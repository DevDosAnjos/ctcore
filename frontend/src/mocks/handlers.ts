import { http, HttpResponse, type HttpResponseResolver } from "msw";
import { isStrongPassword } from "@/features/auth/PasswordRules";
import { isValidCpf, isValidCpfOrCnpj, onlyDigits } from "@/components/fields/masks";
import { isHexColor, passesContrast } from "@/features/organization/contrastRatio";
import { nextHolidays, validateDay, type DayBody } from "./noClassDays";
import { mockPermissions } from "./permissions";
import type { StudentSeal, StudentDetail, StudentGuardianInput, StudentGuardianRow, StudentInput, StudentRow } from "@/features/students/types";

export type Method = "get" | "post" | "put" | "patch" | "delete";

export interface Route {
  method: Method;
  /** Caminho como no OpenAPI (sem o prefixo /api/v1), ex.: "/class-groups/{id}". */
  path: string;
  resolver: HttpResponseResolver;
}

const error = (status: number, code: string, message: string, fields: { field: string; message: string }[] = []) =>
  HttpResponse.json({ code, message, changed: false, fields, traceId: "mock" }, { status });

const session = {
  account: { id: "00000000-0000-0000-0000-000000000001", email: "ana@exemplo.com", name: "Ana" },
  links: [
    { id: "00000000-0000-0000-0000-000000000002", organizationId: "00000000-0000-0000-0000-000000000003", organizationName: "Academia Alfa", role: "COORDINATOR", unitIds: [] },
  ],
  activeLinkId: "00000000-0000-0000-0000-000000000002" as string | null,
  twoFactor: { enabled: false, required: false },
};

// D7: "varios@exemplo.com" tem vínculos de Gestão e Portal em duas organizações e entra sem vínculo ativo.
const many = {
  account: { id: "00000000-0000-0000-0000-000000000009", email: "varios@exemplo.com", name: "Vera" },
  links: [
    { id: "00000000-0000-0000-0000-000000000011", organizationId: "00000000-0000-0000-0000-000000000003", organizationName: "Academia Alfa", role: "ADMINISTRATOR", unitIds: [] },
    { id: "00000000-0000-0000-0000-000000000012", organizationId: "00000000-0000-0000-0000-000000000003", organizationName: "Academia Alfa", role: "STUDENT", unitIds: [] },
    { id: "00000000-0000-0000-0000-000000000013", organizationId: "00000000-0000-0000-0000-000000000004", organizationName: "Academia Beta", role: "TEACHER", unitIds: [] },
  ],
  activeLinkId: null as string | null,
  twoFactor: { enabled: false, required: false },
};

// Duas etapas simuladas (RN-ACE-17): "duas@exemplo.com" tem as duas ligadas (login devolve bilhete);
// "vencida@exemplo.com" recebe um bilhete que o segundo passo recusa como vencido;
// "coord@exemplo.com" é Coordenador sem as duas etapas (configuração obrigatória).
// Código TOTP válido: 123456; código de reserva válido: RESERVA001; "limite" dá 429.
const withTwoFactor = (email: string, enabled: boolean, required: boolean) => ({
  ...session,
  account: { ...session.account, email },
  twoFactor: { enabled, required },
});
const accounts: Record<string, ReturnType<typeof withTwoFactor>> = {
  "duas@exemplo.com": withTwoFactor("duas@exemplo.com", true, true),
  "vencida@exemplo.com": withTwoFactor("vencida@exemplo.com", true, true),
  "coord@exemplo.com": withTwoFactor("coord@exemplo.com", false, true),
};
const VALID_CODES = ["123456", "RESERVA001"];
const backupCodes = () => ({ backupCodes: Array.from({ length: 10 }, (_, i) => `RESERVA${String(i + 1).padStart(3, "0")}`) });
let current: typeof session = session;

const initialSessions = () => [
  { id: "00000000-0000-0000-0000-000000000031", device: "Chrome em Windows", city: null as string | null, createdAt: "2026-10-17T12:00:00Z", lastUsedAt: "2026-10-18T09:00:00Z", current: true },
  { id: "00000000-0000-0000-0000-000000000032", device: "Safari em iOS", city: "São Paulo" as string | null, createdAt: "2026-10-10T08:00:00Z", lastUsedAt: "2026-10-16T20:00:00Z", current: false },
];
let sessions = initialSessions();

// Sessão simulada: abre com um login bem-sucedido. Senha "errada" dá 401, "limite" dá 429.
let loggedIn = false;
let ticketAccount: string | null = null;
export const resetMockSession = () => {
  loggedIn = false;
  current = session;
  ticketAccount = null;
  organization = { ...initialOrganization };
  units = initialUnits();
  unitDetails = {};
  staff = initialStaff();
  mockTeachers = initialTeachers();
  mockClasses = initialClasses();
  mockPlans = initialPlans();
  lessonEdits.clear();
  mockAdhoc = [];
  mockNoClassDays = [];
  teacherContacts = initialTeacherContacts();
  teacherResends = 0;
  mockStudents = initialStudents();
  guardianResends = 0;
  studentLog = {};
  for (const k of Object.keys(guardianCpfs)) delete guardianCpfs[k];
  guardianCpfs["00000000-0000-0000-0000-000000000801"] = "98765432100";
  sessions = initialSessions();
  activeModalities.clear();
  unitModalities.clear();
  graduations.clear();
  modalityRequests = [];
  ageRanges = initialAgeRanges();
};

// Organização do vínculo ativo (RF-ORG-01), em memória: sem cor, titular nem logo.
type MockLastChange = { at: string; by: string | null } | null;
const initialOrganization = {
  name: "Academia Alfa",
  color: null as string | null,
  holderDocument: null as string | null,
  logoUrl: null as string | null,
  version: 0,
  lastChange: null as MockLastChange,
};
// RNF-15: If-Match com a versão lida; ausente ou não numérico é 428, versão diferente da atual é 409.
// Sem `version`, só o formato é conferido (a versão é conferida depois da validação do corpo).
const precondition = (request: Request, version?: number) => {
  const header = request.headers.get("If-Match");
  if (header === null || !/^"?[0-9]+"?$/.test(header)) return error(428, "PRECONDITION_REQUIRED", "Recarregue a tela para obter a versão atual dos dados.");
  return version === undefined || Number(header.replace(/"/g, "")) === version ? null : error(409, "EDIT_CONFLICT", "Outra pessoa alterou estes dados.");
};
const changedNow = (): MockLastChange => ({ at: new Date().toISOString(), by: current.account.name });
let organization = { ...initialOrganization };
// PNG de 1 pixel: o mock não guarda o arquivo enviado, só que existe uma logo.
const MOCK_LOGO =
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAHnOcQAAAAABJRU5ErkJggg==";
const activeRole = () => current.links.find((l) => l.id === current.activeLinkId)?.role;
// Leitura: Coordenador e Administrador; escrita: só o Coordenador. Professor e portal: 403 (FORBIDDEN).
const organizationAccess = (write: boolean) => {
  if (!loggedIn) return unauthenticated();
  const role = activeRole();
  const allowed = role === "COORDINATOR" || (!write && role === "ADMINISTRATOR");
  return allowed ? null : error(403, "FORBIDDEN", "Sem permissão.");
};
// Unidades da organização (RF-ORG-02), em memória. Só o Coordenador lê e ajusta; nome "em-uso" simula nome de outra organização.
const initialUnits = () => [
  { id: "00000000-0000-0000-0000-000000000021", name: "Unidade Centro", shortName: "alfa-centro", shortNameAdjusted: false, city: "Rio de Janeiro", activeStudents: 0, administrators: [{ name: "Bruno" }, { name: "Carla" }], active: true },
  { id: "00000000-0000-0000-0000-000000000022", name: "Unidade Norte", shortName: "alfa-norte", shortNameAdjusted: true, city: null as string | null, activeStudents: 0, administrators: [] as { name: string }[], active: false },
];
let units = initialUnits();
const unitsAccess = () => {
  if (!loggedIn) return unauthenticated();
  return activeRole() === "COORDINATOR" ? null : error(403, "FORBIDDEN", "Sem permissão.");
};
// Modalidades (RF-MOD-01), em memória: catálogo fixo de 14, ativas por organização e pedidos enviados.
const MODALITY_NAMES = ["Boxe", "Capoeira", "Defesa pessoal", "Jiu-Jitsu", "Judô", "Karatê", "Kickboxing", "Krav Magá", "Kung Fu", "Luta Livre", "MMA", "Muay Thai", "Taekwondo", "Wrestling"];
const modalityCatalog = MODALITY_NAMES.map((name, i) => ({ id: `00000000-0000-0000-0000-0000000001${String(i).padStart(2, "0")}`, name }));
const activeModalities = new Set<string>();
const unitModalities = new Map<string, Set<string>>();
type MockLevel = { id: string; name: string; color: string; secondaryColor: string | null; maxDegree: number; minAge: number | null; maxAge: number | null; kids: boolean };
type MockGraduation = { enabled: boolean; requireOriginProof: boolean; levels: MockLevel[] };
const graduations = new Map<string, MockGraduation>();
const jiuJitsuDefault = (): MockLevel[] =>
  [["Branca", "#FFFFFF", 4, 16], ["Azul", "#0000CD", 4, 16], ["Roxa", "#800080", 4, 16], ["Marrom", "#8B4513", 4, 18], ["Preta", "#000000", 6, 19]].map(
    ([name, color, maxDegree, minAge]) => ({ id: crypto.randomUUID(), name: name as string, color: color as string, secondaryColor: null, maxDegree: maxDegree as number, minAge: minAge as number, maxAge: null, kids: false }),
  );
const graduationOf = (id: string) => graduations.get(id) ?? { enabled: false, requireOriginProof: false, levels: [] };
let modalityRequests: { id: string; name: string; note: string | null; status: string; createdAt: string }[] = [];
// Faixas etárias (RF-MOD-04), em memória: as três padrão; nome único sem acento nem maiúsculas.
type MockAgeRange = { id: string; name: string; minAge: number; maxAge: number | null };
type AgeRangeInput = { name?: string; minAge?: number | null; maxAge?: number | null };
const initialAgeRanges = (): MockAgeRange[] => [
  { id: crypto.randomUUID(), name: "Infantil", minAge: 4, maxAge: 12 },
  { id: crypto.randomUUID(), name: "Juvenil", minAge: 13, maxAge: 17 },
  { id: crypto.randomUUID(), name: "Adulto", minAge: 18, maxAge: null },
];
let ageRanges = initialAgeRanges();
const sortedAgeRanges = () => [...ageRanges].sort((a, b) => a.minAge - b.minAge || a.name.localeCompare(b.name));
const checkAgeRange = (body: AgeRangeInput, id: string | null): MockAgeRange | Response => {
  const bad = (field: string, message: string) => error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field, message }]);
  const name = body.name?.trim() ?? "";
  if (name.length < 2 || name.length > 40) return bad("name", "de 2 a 40 caracteres");
  const { minAge } = body;
  const maxAge = body.maxAge ?? null;
  if (minAge === undefined || minAge === null) return bad("minAge", "obrigatório");
  if (!Number.isInteger(minAge) || minAge < 0 || minAge > 120) return bad("minAge", "de 0 a 120");
  if (maxAge !== null && (!Number.isInteger(maxAge) || maxAge < minAge || maxAge > 120)) return bad("maxAge", "da idade mínima a 120");
  const key = (s: string) => s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
  if (ageRanges.some((r) => r.id !== id && key(r.name) === key(name))) return error(409, "AGE_RANGE_NAME_TAKEN", "Já existe uma faixa etária com este nome.");
  return { id: id ?? crypto.randomUUID(), name, minAge, maxAge };
};
export const TRANSFER_TARGET = "admin@alfa.test";
const unauthenticated = () => error(401, "UNAUTHENTICATED", "Sem sessão.");

// Configurações da unidade (RF-ORG-04), em memória. Coordenador: qualquer unidade; Administrador: só a primeira (ADMIN_UNIT);
// Professor e portal: 403. Unidade que não existe: 404.
const ADMIN_UNIT = "00000000-0000-0000-0000-000000000021";

// RF-PLA-01/02: planos fictícios (3 ativos e 1 desativado); dois na unidade do Administrador, um em outra.
const initialPlans = () => [
  { id: "00000000-0000-0000-0000-000000000601", unitId: ADMIN_UNIT, unitName: "Unidade Centro", modalityIndex: 4, name: "Judô Mensal", valueCents: 15000, weeklyFrequency: 2 as 2 | 3 | null, validOtherUnits: false as boolean, status: "ACTIVE" as "ACTIVE" | "INACTIVE" },
  { id: "00000000-0000-0000-0000-000000000602", unitId: ADMIN_UNIT, unitName: "Unidade Centro", modalityIndex: 3, name: "Jiu-Jitsu Livre", valueCents: 20000, weeklyFrequency: null, validOtherUnits: false as boolean, status: "ACTIVE" as "ACTIVE" | "INACTIVE" },
  { id: "00000000-0000-0000-0000-000000000603", unitId: "00000000-0000-0000-0000-000000000022", unitName: "Unidade Norte", modalityIndex: 0, name: "Boxe 3x", valueCents: 30000, weeklyFrequency: 3 as 2 | 3 | null, validOtherUnits: false as boolean, status: "ACTIVE" as "ACTIVE" | "INACTIVE" },
  { id: "00000000-0000-0000-0000-000000000604", unitId: ADMIN_UNIT, unitName: "Unidade Centro", modalityIndex: 4, name: "Judô Antigo", valueCents: 12000, weeklyFrequency: 2 as 2 | 3 | null, validOtherUnits: false as boolean, status: "INACTIVE" as "ACTIVE" | "INACTIVE" },
];
let mockPlans = initialPlans();
type MockPlan = ReturnType<typeof initialPlans>[number];
const planRow = ({ modalityIndex, unitName, unitId, ...x }: MockPlan) => ({
  id: x.id,
  unit: { id: unitId, name: unitName },
  modality: modalityCatalog[modalityIndex],
  name: x.name,
  valueCents: x.valueCents,
  weeklyFrequency: x.weeklyFrequency,
  validOtherUnits: x.validOtherUnits,
  studentsUsing: 0,
  status: x.status,
});
// RF-PLA-03: escrita de planos; Coordenador e Administrador (só a unidade dele); demais 403. Unidade e modalidade não mudam ao editar.
type PlanBody = { unitId?: string; modalityId?: string; name?: string; valueCents?: unknown; weeklyFrequency?: unknown; validOtherUnits?: boolean; unpaidCharges?: string; paidFutureDifference?: boolean };
const planBad = (field: string, message = "inválido") => error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field, message }]);
const planAccess = () => {
  if (!loggedIn) return unauthenticated();
  const role = activeRole();
  return role === "COORDINATOR" || role === "ADMINISTRATOR" ? null : error(403, "FORBIDDEN", "Sem permissão.");
};
const planFor = (id: string) => {
  const found = mockPlans.find((p) => p.id === id && (activeRole() === "COORDINATOR" || p.unitId === ADMIN_UNIT));
  return found ?? error(404, "NOT_FOUND", "Não encontrado.");
};
const validCents = (v: unknown): v is number => typeof v === "number" && Number.isInteger(v) && v >= 1 && v <= 99_999_999;
const planTaken = (unitId: string, modalityIndex: number, name: string, self?: string) =>
  mockPlans.some((p) => p.id !== self && p.status === "ACTIVE" && p.unitId === unitId && p.modalityIndex === modalityIndex && teacherKey(p.name.trim()) === teacherKey(name));
const planConflict = () => error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.", [{ field: "name", message: "já existe um plano ativo com este nome na unidade e modalidade" }]);
// Ao criar e ao editar: nome, valor e frequência; devolve os campos limpos ou a resposta 400.
const planFields = (b: PlanBody) => {
  const name = (b.name ?? "").trim();
  if (name.length < 2 || name.length > 60) return planBad("name", "de 2 a 60 caracteres");
  if (!validCents(b.valueCents)) return planBad("valueCents", "inteiro de 1 a 99999999 centavos");
  if (b.weeklyFrequency !== null && b.weeklyFrequency !== undefined && b.weeklyFrequency !== 2 && b.weeklyFrequency !== 3) return planBad("weeklyFrequency", "2, 3 ou vazio (livre)");
  if (b.unpaidCharges !== undefined && b.unpaidCharges !== "KEEP" && b.unpaidCharges !== "ADJUST") return planBad("unpaidCharges", "KEEP ou ADJUST");
  return { name, valueCents: b.valueCents, weeklyFrequency: (b.weeklyFrequency ?? null) as 2 | 3 | null, validOtherUnits: b.validOtherUnits === true };
};
const defaultUnitSettings = () => ({
  dueDays: [1, 5, 10, 15],
  enrollmentFeeEnabled: false,
  enrollmentFeeCents: 0,
  feesCents: { cash: 0, pix: 0, bankSlip: 0, creditCard: 0, debitCard: 0, automaticDebit: 0, manualPix: 0, terminalCard: 0, transfer: 0 },
  pixKey: null as string | null,
  paymentAccount: "NOT_CONNECTED" as "CONNECTED" | "NOT_CONNECTED",
  reminderDays: [-3, 0, 3, 10],
  teacherPermissions: { announcements: true, agendaEdit: true, studentContacts: true, trialClasses: true, studentRecord: false },
  directoryEnabled: false,
  trialLinkEnabled: true,
  selfRegistrationLinkEnabled: true,
});
type UnitDetailMock = ReturnType<typeof makeUnitDetail>;
const makeUnitDetail = (u: (typeof units)[number]) => ({
  id: u.id,
  name: u.name,
  shortName: u.shortName,
  postalCode: null as string | null,
  address: null as string | null,
  city: u.city,
  latitude: null as number | null,
  longitude: null as number | null,
  phone: null as string | null,
  email: null as string | null,
  whatsapp: null as string | null,
  cnpj: null as string | null,
  active: u.active,
  settings: defaultUnitSettings(),
  version: 0,
  lastChange: null as MockLastChange,
});
let unitDetails: Record<string, UnitDetailMock> = {};
const unitDetail = (id: string) => {
  const unit = units.find((u) => u.id === id)!;
  // nome curto, situação e cidade vêm da lista, que o ajuste do nome curto (#29) atualiza
  return (unitDetails[id] = { ...(unitDetails[id] ?? makeUnitDetail(unit)), shortName: unit.shortName, active: unit.active });
};
const unitSettingsRole = () => {
  if (!loggedIn) return unauthenticated();
  const role = activeRole();
  return role === "COORDINATOR" || role === "ADMINISTRATOR" ? null : error(403, "FORBIDDEN", "Sem permissão.");
};
// Equipe da gestão (RF-ACE-06), em memória. Bruno é o único Administrador ativo da Unidade Centro (RN-ORG-07);
// Carla está pendente; Diego é Professor só do Centro e Elisa também da Unidade Norte (fora do alcance do Administrador).
const CENTRO = "00000000-0000-0000-0000-000000000021";
const NORTE = "00000000-0000-0000-0000-000000000022";
const initialStaff = () => [
  { linkId: "00000000-0000-0000-0000-000000000041", name: "Bruno", email: "bruno@alfa.test", role: "ADMINISTRATOR", unitIds: [CENTRO], lastAccessAt: "2026-10-17T10:00:00Z" as string | null, status: "ACTIVE", self: false, resends: 0 },
  { linkId: "00000000-0000-0000-0000-000000000042", name: "Carla", email: "carla@alfa.test", role: "ADMINISTRATOR", unitIds: [CENTRO], lastAccessAt: null as string | null, status: "PENDING", self: false, resends: 0 },
  { linkId: "00000000-0000-0000-0000-000000000043", name: "Diego", email: "diego@alfa.test", role: "TEACHER", unitIds: [CENTRO], lastAccessAt: "2026-10-16T08:00:00Z" as string | null, status: "ACTIVE", self: false, resends: 0 },
  { linkId: "00000000-0000-0000-0000-000000000044", name: "Elisa", email: "elisa@alfa.test", role: "TEACHER", unitIds: [CENTRO, NORTE], lastAccessAt: null as string | null, status: "ACTIVE", self: false, resends: 0 },
];
type MockStaff = ReturnType<typeof initialStaff>[number];
let staff = initialStaff();
const managedRole = () => (activeRole() === "COORDINATOR" ? "ADMINISTRATOR" : "TEACHER");
const reaches = (s: MockStaff) => s.role === managedRole() && (activeRole() === "COORDINATOR" || s.unitIds.includes(ADMIN_UNIT));
const manageable = (s: MockStaff) => activeRole() === "COORDINATOR" || s.unitIds.every((id) => id === ADMIN_UNIT);
const staffRow = (s: MockStaff) => ({
  linkId: s.linkId,
  name: s.name,
  email: s.email,
  role: s.role,
  units: s.unitIds.map((id) => ({ id, name: units.find((u) => u.id === id)?.name ?? "" })),
  lastAccessAt: s.lastAccessAt,
  status: s.status,
  canManage: manageable(s),
});
// RN-ORG-07: unidades ativas, entre as que o Administrador perde, sem outro Administrador ativo.
const orphans = (row: MockStaff, losing: string[]) =>
  losing.filter((id) => units.some((u) => u.id === id && u.active) && !staff.some((s) => s !== row && s.role === "ADMINISTRATOR" && s.status === "ACTIVE" && s.unitIds.includes(id)));
const lastAdministrator = (ids: string[]) =>
  error(409, "LAST_UNIT_ADMINISTRATOR", "Uma unidade ficaria sem Administrador ativo.", [{ field: "unitIds", message: ids.join(",") }]);
const unitSettingsAccess = (id: string) => {
  const denied = unitSettingsRole();
  if (denied) return denied;
  if (!units.some((u) => u.id === id)) return error(404, "NOT_FOUND", "Não encontrado.");
  return activeRole() === "ADMINISTRATOR" && id !== ADMIN_UNIT ? error(403, "FORBIDDEN", "Sem permissão.") : null;
};

// Convites simulados: "valido" (conta nova), "existente" (já tem senha), "vencido", "usado", "cancelado"; outro código é desconhecido.
const invites: Record<string, { email: string; name: string | null; existingAccount: boolean }> = {
  valido: { email: "novo@exemplo.com", name: "Nova Pessoa", existingAccount: false },
  existente: { email: "ana@exemplo.com", name: null, existingAccount: true },
};
const reasons: Record<string, string> = { vencido: "EXPIRED", usado: "USED", cancelado: "CANCELED" };
const gone = (code: string) =>
  error(410, "INVITE_INVALID", "Convite inválido.", reasons[code] ? [{ field: "reason", message: reasons[code] }] : []);

// Cada item que declarar rota acrescenta aqui o handler dela; o teste msw.test.ts confere contra o OpenAPI.
// Professores (RF-PRO-01), fixos: o Administrador (ADMIN_UNIT) só vê os que têm a unidade dele; telefone e e-mail nunca saem (RN-PRO-04).
const UNIT_NORTE = "00000000-0000-0000-0000-000000000022";
type MockModality = { id: string; name: string; levelId: string | null; level: string | null; degree: number | null };
type MockTeacher = { id: string; name: string; photoUrl: string | null; modalities: MockModality[]; units: { id: string; name: string }[]; classCount: number; status: string; directoryConsent: boolean; access: string };
const initialTeachers = (): MockTeacher[] => [
  { id: "00000000-0000-0000-0000-000000000501", name: "Ana Souza", photoUrl: null as string | null, modalities: [{ id: modalityCatalog[3].id, name: "Jiu-Jitsu", levelId: null as string | null, level: "Roxa", degree: 2 }], units: [{ id: ADMIN_UNIT, name: "Unidade Centro" }], classCount: 0, status: "ACTIVE", directoryConsent: true, access: "ACTIVE" },
  { id: "00000000-0000-0000-0000-000000000502", name: "João Pereira", photoUrl: null, modalities: [{ id: modalityCatalog[3].id, name: "Jiu-Jitsu", levelId: null as string | null, level: "Azul", degree: 0 }, { id: modalityCatalog[4].id, name: "Judô", levelId: null as string | null, level: null, degree: null }], units: [{ id: ADMIN_UNIT, name: "Unidade Centro" }, { id: UNIT_NORTE, name: "Unidade Norte" }], classCount: 0, status: "ACTIVE", directoryConsent: false, access: "NONE" },
  { id: "00000000-0000-0000-0000-000000000503", name: "Marcos Lima", photoUrl: null, modalities: [{ id: modalityCatalog[0].id, name: "Boxe", levelId: null as string | null, level: null, degree: null }], units: [{ id: UNIT_NORTE, name: "Unidade Norte" }], classCount: 0, status: "INACTIVE", directoryConsent: false, access: "ENDED" },
];
let mockTeachers = initialTeachers();
// Contato e apresentação só saem no painel (RF-PRO-02, D2); a lista nunca os traz. João tem e-mail e ainda sem acesso; Marcos não tem e-mail.
type TeacherContact = { bio: string | null; email: string | null; phone: string | null; consentDeclaredByManagement: boolean };
const initialTeacherContacts = (): Record<string, TeacherContact> => ({
  "00000000-0000-0000-0000-000000000501": { bio: "Faixa preta, 15 anos de tatame.", email: "ana.souza@alfa.test", phone: "+55 11 98888-0001", consentDeclaredByManagement: true },
  "00000000-0000-0000-0000-000000000502": { bio: null, email: "joao.pereira@alfa.test", phone: "+55 11 98888-0002", consentDeclaredByManagement: false },
  "00000000-0000-0000-0000-000000000503": { bio: null, email: null, phone: null, consentDeclaredByManagement: false },
});
let teacherContacts = initialTeacherContacts();
let teacherResends = 0;
const teacherDetail = (t: MockTeacher) => {
  const { id, name, photoUrl, directoryConsent, status, modalities, units, access } = t;
  // Turmas ativas como titular ou auxiliar (as do classCount); o simulado não gera aulas, então as próximas vêm vazias.
  const classes = mockClasses
    .filter((c) => c.status === "ACTIVE" && (c.head === id || c.assistants.includes(id)))
    .map((c) => ({ id: c.id, name: c.name, role: c.head === id ? "HEAD" : "ASSISTANT" }));
  return { id, name, photoUrl, directoryConsent, status, modalities, units, access, ...teacherContacts[id], classes, upcomingClasses: [] };
};
// Quem chama: 401 sem sessão, 403 sem TEACHERS, 404 fora do alcance (Administrador sem a unidade dele) ou inexistente.
const teacherFor = (id: string) => {
  if (!loggedIn) return unauthenticated();
  const role = activeRole();
  if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
  const found = mockTeachers.find((t) => t.id === id && (role === "COORDINATOR" || t.units.some((u) => u.id === ADMIN_UNIT)));
  return found ?? error(404, "NOT_FOUND", "Não encontrado.");
};
// D3: o Coordenador inativa qualquer um; o Administrador só quem não tem unidade fora do alcance dele.
const teacherInReach = (t: MockTeacher) => activeRole() === "COORDINATOR" || t.units.every((u) => u.id === ADMIN_UNIT);
const teacherKey = (s: string) => s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
// Turmas (RF-TUR-01), fixas: a faixa etária é resolvida pelo nome na hora, porque as faixas do mock têm id novo a cada carga.
const TEACHER_ANA = "00000000-0000-0000-0000-000000000501";
const TEACHER_JOAO = "00000000-0000-0000-0000-000000000502";
const TEACHER_MARCOS = "00000000-0000-0000-0000-000000000503";
type MockClass = { id: string; name: string; unitId: string; modalityId: string; ageRange: string | null; schedule: [number, string, string][]; head: string; assistants: string[]; capacity: number | null; status: string; groupLink?: string; blocking?: number };
// groupLink só no detalhe; `blocking` (alunos com a turma como principal) é só do simulado: o servidor só o traz com a matrícula (Sprint 07).
const initialClasses = (): MockClass[] => [
  { id: "00000000-0000-0000-0000-000000000601", name: "Jiu-Jitsu Adulto", unitId: ADMIN_UNIT, modalityId: modalityCatalog[3].id, ageRange: "Adulto", schedule: [[1, "19:00", "20:00"], [3, "19:00", "20:00"]], head: TEACHER_ANA, assistants: [TEACHER_JOAO], capacity: 30, status: "ACTIVE", groupLink: "https://chat.example.com/jiu-jitsu-adulto" },
  { id: "00000000-0000-0000-0000-000000000602", name: "Judô Infantil", unitId: ADMIN_UNIT, modalityId: modalityCatalog[4].id, ageRange: "Infantil", schedule: [[2, "18:00", "19:00"], [4, "18:00", "19:00"]], head: TEACHER_JOAO, assistants: [], capacity: 20, status: "ACTIVE", blocking: 2 },
  { id: "00000000-0000-0000-0000-000000000603", name: "Boxe Noturno", unitId: UNIT_NORTE, modalityId: modalityCatalog[0].id, ageRange: null, schedule: [[5, "20:00", "21:30"]], head: TEACHER_MARCOS, assistants: [], capacity: null, status: "INACTIVE" },
];
let mockClasses = initialClasses();
const classRow = (c: MockClass) => {
  const unit = units.find((u) => u.id === c.unitId)!;
  const range = ageRanges.find((r) => r.name === c.ageRange);
  const person = (id: string) => ({ id, name: mockTeachers.find((t) => t.id === id)!.name });
  return {
    id: c.id,
    name: c.name,
    unit: { id: unit.id, name: unit.name },
    modality: { id: c.modalityId, name: modalityCatalog.find((m) => m.id === c.modalityId)!.name },
    ageRange: range ?? null,
    schedule: c.schedule.map(([weekday, startsAt, endsAt]) => ({ weekday, startsAt, endsAt })),
    head: person(c.head),
    assistants: c.assistants.map(person),
    activeStudents: 0,
    capacity: c.capacity,
    overCapacity: false,
    status: c.status,
  };
};
// RF-TUR-02: painel; alunos e próxima aula vazios até a matrícula e a agenda (D1).
const classDetail = (c: MockClass) => ({
  ...classRow(c),
  groupLink: c.groupLink ?? null,
  nextClass: null,
  students: [],
  deactivation: { allowed: c.status === "ACTIVE" && !c.blocking, blockingStudents: c.blocking ?? 0 },
});
// Quem chama: 401 sem sessão, 403 sem CLASSES_MANAGE, 404 fora do alcance (Administrador) ou inexistente.
// RF-AGE-01: a aula de uma turma numa data (o id junta turma, data e início). Hoje é dia sem aula; a de quarta do Jiu-Jitsu é substituição.
// RF-AGE-03: o que a edição e o cancelamento mudaram, por id de aula (as aulas do mock nascem das turmas e não têm linha própria).
type LessonEdit = { date?: string; startsAt?: string; endsAt?: string; teacherId?: string; substitute?: boolean; cancelCode?: string; cancelText?: string | null };
const lessonEdits = new Map<string, LessonEdit>();
// RF-AGE-04: aulas avulsas criadas no simulado (têm linha própria, ao contrário das aulas de turma).
type MockAdhoc = { id: string; unitId: string; title: string; modalityId: string; date: string; startsAt: string; endsAt: string; teacherId: string; visibilityClassId: string | null };
let mockAdhoc: MockAdhoc[] = [];
// RF-AGE-05: dias sem aula lançados no simulado. As aulas deles não têm linha própria: nascem canceladas pelo dia que as cobre (como o gerador do servidor).
type MockDay = { id: string; unitId: string; startsOn: string; endsOn: string; reason: string; origin: "HOLIDAY" | "COORDINATOR" | "UNIT"; classIds: string[]; noticeMode: string; noticeAt: string | null };
let mockNoClassDays: MockDay[] = [];
const coveringDay = (unitId: string, date: string, classId: string | null, today: string) =>
  mockNoClassDays.find((d) => d.unitId === unitId && date >= today && date >= d.startsOn && date <= d.endsOn && (d.classIds.length === 0 || (classId !== null && d.classIds.includes(classId))));
const todayLocal = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
};
const adhocLesson = (a: MockAdhoc, today: string) => {
  const unit = units.find((u) => u.id === a.unitId)!;
  const teacher = mockTeachers.find((t) => t.id === a.teacherId)!;
  const future = a.date >= today;
  const day = coveringDay(a.unitId, a.date, null, today);
  return {
    id: a.id,
    date: a.date,
    startsAt: a.startsAt,
    endsAt: a.endsAt,
    status: day ? "CANCELLED" : future ? "SCHEDULED" : "COMPLETED",
    cancelReason: (day?.reason ?? null) as string | null,
    cancelReasonCode: (day ? (day.origin === "HOLIDAY" ? "HOLIDAY" : "OTHER") : null) as string | null,
    class: null as { id: string; name: string } | null,
    title: a.title as string | null,
    unit: { id: unit.id, name: unit.name },
    modality: { id: a.modalityId, name: modalityCatalog.find((m) => m.id === a.modalityId)!.name },
    teacher: { id: teacher.id, name: teacher.name },
    substitute: false,
    canEdit: !day,
    canCancel: future && !day,
  };
};
const applyEdit = <T extends { id: string; date: string; startsAt: string; endsAt: string; teacher: { id: string; name: string }; substitute: boolean; status: string; cancelReason: string | null; cancelReasonCode: string | null; canEdit: boolean; canCancel: boolean }>(l: T): T => {
  const e = lessonEdits.get(l.id);
  if (!e) return l;
  const teacher = e.teacherId ? mockTeachers.find((x) => x.id === e.teacherId) : undefined;
  return {
    ...l,
    date: e.date ?? l.date,
    startsAt: e.startsAt ?? l.startsAt,
    endsAt: e.endsAt ?? l.endsAt,
    teacher: teacher ? { id: teacher.id, name: teacher.name } : l.teacher,
    substitute: e.substitute ?? l.substitute,
    ...(e.cancelCode ? { status: "CANCELLED", cancelReason: e.cancelText ?? null, cancelReasonCode: e.cancelCode, canEdit: false, canCancel: false } : {}),
  };
};
const mockLesson = (c: MockClass, unit: { id: string; name: string }, date: string, wd: number, startsAt: string, endsAt: string, today: string) => {
  const row = classRow(c);
  const substitute = c.id.endsWith("601") && wd === 3;
  const teacher = substitute ? { id: TEACHER_JOAO, name: mockTeachers.find((x) => x.id === TEACHER_JOAO)!.name } : row.head;
  const day = coveringDay(unit.id, date, c.id, today);
  const cancelled = date === today || !!day;
  const future = date > today;
  return {
    id: `${c.id}-${date}-${startsAt}`,
    date,
    startsAt,
    endsAt,
    status: cancelled ? "CANCELLED" : future ? "SCHEDULED" : "COMPLETED",
    cancelReason: cancelled ? (day?.reason ?? "Feriado sintético") : null,
    cancelReasonCode: (day ? (day.origin === "HOLIDAY" ? "HOLIDAY" : "OTHER") : null) as string | null,
    class: { id: c.id, name: c.name } as { id: string; name: string } | null,
    title: null as string | null,
    unit: { id: unit.id, name: unit.name },
    modality: row.modality,
    teacher,
    substitute,
    canEdit: !cancelled,
    canCancel: !cancelled && future,
  };
};
const classFor = (id: string) => {
  if (!loggedIn) return unauthenticated();
  const role = activeRole();
  if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
  return mockClasses.find((c) => c.id === id && (role === "COORDINATOR" || c.unitId === ADMIN_UNIT)) ?? error(404, "NOT_FOUND", "Não encontrado.");
};
// RF-AGE-02/03: a aula pelo id (turma, data e início), com as edições aplicadas; 401, 403 e 404 como no servidor.
const lessonView = (id: string) => {
  if (!loggedIn) return unauthenticated();
  const role = activeRole();
  if (role !== "COORDINATOR" && role !== "ADMINISTRATOR" && role !== "TEACHER") return error(403, "FORBIDDEN", "Sem permissão.");
  const adhoc = mockAdhoc.find((a) => a.id === id && (role !== "ADMINISTRATOR" || a.unitId === ADMIN_UNIT));
  if (adhoc) {
    const today = todayLocal();
    const { canEdit, canCancel, ...lesson } = applyEdit(adhocLesson(adhoc, today));
    const visible = adhoc.visibilityClassId ? mockClasses.find((k) => k.id === adhoc.visibilityClassId) : undefined;
    const detail = {
      ...lesson,
      visibilityClass: visible ? { id: visible.id, name: visible.name } : null,
      head: null,
      activeStudents: 0,
      trialClasses: 0,
      groupLink: null,
      canEdit: role === "TEACHER" ? false : canEdit,
      canCancel: role === "TEACHER" ? false : canCancel,
      canViewClass: false,
      followingEdited: 0,
    };
    return { detail, klass: null, today };
  }
  const m = /^(.+)-(\d{4}-\d{2}-\d{2})-(\d{2}:\d{2})$/.exec(id);
  const c = m && mockClasses.find((k) => k.id === m[1] && k.status === "ACTIVE" && (role !== "ADMINISTRATOR" || k.unitId === ADMIN_UNIT));
  const unit = c && units.find((u) => u.id === c.unitId);
  const slot = c && m && c.schedule.find(([d, s]) => s === m[3] && d === ((new Date(`${m[2]}T12:00:00Z`).getUTCDay() + 6) % 7) + 1);
  if (!m || !c || !unit || !slot) return error(404, "NOT_FOUND", "Não encontrado.");
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  const { canEdit, canCancel, ...lesson } = applyEdit(mockLesson(c, unit, m[2], slot[0], slot[1], slot[2], today));
  const detail = {
    ...lesson,
    visibilityClass: null,
    head: lesson.substitute ? classRow(c).head : null,
    activeStudents: 0,
    trialClasses: 0,
    groupLink: c.groupLink ?? null,
    canEdit: role === "TEACHER" ? false : canEdit,
    canCancel: role === "TEACHER" ? false : canCancel,
    canViewClass: role !== "TEACHER",
    // RN-AGE-04: edições à mão (ainda agendadas) das aulas seguintes da turma, que "esta e as seguintes" substituiria.
    followingEdited: [...lessonEdits.entries()].filter(([key, e]) => key.startsWith(`${c.id}-`) && !e.cancelCode && key.slice(c.id.length + 1, c.id.length + 11) > m[2]).length,
  };
  return { detail, klass: c, today };
};
// RF-AGE-05: Coordenador e Administrador; Professor e portal 403. O Administrador só alcança a unidade dele (ADMIN_UNIT); fora do alcance, 404.
const dayAccess = () => {
  if (!loggedIn) return unauthenticated();
  const role = activeRole();
  return role === "COORDINATOR" || role === "ADMINISTRATOR" ? null : error(403, "FORBIDDEN", "Sem permissão.");
};
const dayReach = () => units.filter((u) => u.active && (activeRole() === "COORDINATOR" || u.id === ADMIN_UNIT));
// Aulas da unidade no período que o dia cancelaria: de turma ativa (as escolhidas, ou todas) e, sem turma escolhida, as avulsas; sem cancelamento manual.
const dayLessons = (unitId: string, start: string, end: string, classIds: string[], today: string) => {
  const out: { id: string; date: string; startsAt: string; endsAt: string; class: { id: string; name: string } | null; title: string | null }[] = [];
  for (let t = Date.parse(`${start}T12:00:00Z`); t <= Date.parse(`${end}T12:00:00Z`); t += 86400000) {
    const date = new Date(t).toISOString().slice(0, 10);
    if (date <= today) continue;
    const weekday = ((new Date(t).getUTCDay() + 6) % 7) + 1;
    for (const c of mockClasses.filter((k) => k.status === "ACTIVE" && k.unitId === unitId && (classIds.length === 0 || classIds.includes(k.id)))) {
      for (const [, startsAt, endsAt] of c.schedule.filter(([d]) => d === weekday)) {
        const id = `${c.id}-${date}-${startsAt}`;
        if (!lessonEdits.get(id)?.cancelCode) out.push({ id, date, startsAt, endsAt, class: { id: c.id, name: c.name }, title: null });
      }
    }
    if (classIds.length === 0) {
      for (const a of mockAdhoc.filter((x) => x.unitId === unitId && x.date === date && !lessonEdits.get(x.id)?.cancelCode)) {
        out.push({ id: a.id, date, startsAt: a.startsAt, endsAt: a.endsAt, class: null, title: a.title });
      }
    }
  }
  return out.sort((a, b) => `${a.date}${a.startsAt}`.localeCompare(`${b.date}${b.startsAt}`));
};
const dayItem = (d: MockDay, today: string) => ({
  id: d.id,
  unit: { id: d.unitId, name: units.find((u) => u.id === d.unitId)!.name },
  startsOn: d.startsOn,
  endsOn: d.endsOn,
  reason: d.reason,
  origin: d.origin,
  allClasses: d.classIds.length === 0,
  classes: d.classIds.map((id) => mockClasses.find((c) => c.id === id)!).map((c) => ({ id: c.id, name: c.name })),
  notice: { mode: d.noticeMode, sendAt: d.noticeAt, status: d.noticeMode === "SCHEDULED" ? "SCHEDULED" : "NOT_NOTIFIED" },
  cancelledLessons: dayLessons(d.unitId, d.startsOn, d.endsOn, d.classIds, today).length,
  canEdit: d.startsOn > today,
});
// Valida como o servidor (400, 404 de unidade, 422/404 de turma) e devolve as unidades já resolvidas ou a resposta de erro.
const dayPlan = (b: DayBody, unitIds: string[], full: boolean) => {
  const today = todayLocal();
  if (unitIds.length === 0) return classBad("unitIds", "obrigatório");
  if (activeRole() !== "COORDINATOR" && unitIds.length > 1) return classBad("unitIds", "informe uma só unidade");
  const bad = validateDay(b, today, full);
  if (bad) return classBad(bad.field, bad.message);
  const found = unitIds.map((id) => dayReach().find((u) => u.id === id));
  if (found.some((u) => !u)) return error(404, "NOT_FOUND", "Não encontrado.");
  const classIds = [...new Set(b.classIds ?? [])];
  if (classIds.length > 0) {
    if (unitIds.length > 1) return error(422, "UNPROCESSABLE", "Os dados estão corretos, mas a regra não permite esta operação.", [{ field: "classIds", message: "turmas só com uma unidade" }]);
    for (const id of classIds) {
      const k = mockClasses.find((c) => c.id === id);
      if (!k) return error(404, "NOT_FOUND", "Não encontrado.");
      if (k.status !== "ACTIVE" || k.unitId !== unitIds[0]) return error(422, "UNPROCESSABLE", "Os dados estão corretos, mas a regra não permite esta operação.", [{ field: "classIds", message: "escolha turmas ativas da unidade" }]);
    }
  }
  return { today, units: unitIds, classIds };
};
const dayClash = (unitId: string, start: string, end: string, self?: string) => mockNoClassDays.some((d) => d.unitId === unitId && d.id !== self && d.startsOn <= end && d.endsOn >= start);
const dayConflict = () => error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.", [{ field: "startsOn", message: "já existe dia sem aula neste período" }]);
// RF-TUR-03: validação como no servidor; devolve o erro 400/409 ou a turma já no formato interno.
type ClassInput = { unitId?: string; name?: string; modalityId?: string; ageRangeId?: string | null; schedule?: { weekday?: number; startsAt?: string; endsAt?: string }[]; capacity?: number | null; headTeacherId?: string; assistantIds?: string[]; groupLink?: string | null; notifyClass?: boolean };
const classBad = (field: string, message = "inválido") => error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field, message }]);
const classFromInput = (b: ClassInput, unitId: string, self: MockClass | null): Omit<MockClass, "id" | "status"> | Response => {
  const name = (b.name ?? "").trim();
  if (name.length < 2 || name.length > 80) return classBad("name", "de 2 a 80 caracteres");
  if (!b.modalityId || !teacherOptions().some((m) => m.id === b.modalityId)) return classBad("modalityId");
  const range = b.ageRangeId ? ageRanges.find((r) => r.id === b.ageRangeId) : null;
  if (b.ageRangeId && !range) return classBad("ageRangeId");
  const time = /^([01]\d|2[0-3]):[0-5]\d$/;
  const slots = b.schedule ?? [];
  if (slots.length === 0 || new Set(slots.map((s) => s.weekday)).size !== slots.length || slots.some((s) => !Number.isInteger(s.weekday) || s.weekday! < 1 || s.weekday! > 7 || !time.test(s.startsAt ?? "") || !time.test(s.endsAt ?? "") || s.endsAt! <= s.startsAt!)) return classBad("schedule");
  if (b.capacity != null && (!Number.isInteger(b.capacity) || b.capacity < 1 || b.capacity > 200)) return classBad("capacity", "de 1 a 200");
  const link = (b.groupLink ?? "").trim() === "" ? undefined : b.groupLink!;
  if (link !== undefined && (link.length > 300 || link !== link.trim() || !/^https?:\/\/\S+$/.test(link))) return classBad("groupLink", "http ou https");
  // RN-TUR-05: ativo, da unidade e que ensina a modalidade da turma.
  const usable = (id: string) => mockTeachers.some((t) => t.id === id && t.status === "ACTIVE" && t.units.some((u) => u.id === unitId) && t.modalities.some((m) => m.id === b.modalityId));
  if (!b.headTeacherId || !usable(b.headTeacherId)) return classBad("headTeacherId");
  const assistants = b.assistantIds ?? [];
  if (new Set(assistants).size !== assistants.length || assistants.includes(b.headTeacherId) || !assistants.every(usable)) return classBad("assistantIds");
  if (mockClasses.some((c) => c !== self && c.unitId === unitId && c.status === "ACTIVE" && teacherKey(c.name) === teacherKey(name))) {
    return error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.", [{ field: "name", message: "já existe uma turma ativa com este nome na unidade" }]);
  }
  const sorted = [...slots].sort((a, c) => a.weekday! - c.weekday!);
  return { name, unitId, modalityId: b.modalityId, ageRange: range?.name ?? null, schedule: sorted.map((s) => [s.weekday!, s.startsAt!, s.endsAt!]), head: b.headTeacherId, assistants, capacity: b.capacity ?? null, groupLink: link };
};
// RF-PRO-03: opções do formulário (modalidades ativas; as dos professores fixos contam como ativas) e validação como no servidor.
const SEEDED_MODALITIES = [3, 4, 0].map((i) => modalityCatalog[i].id);
const teacherOptions = () =>
  modalityCatalog
    .filter((m) => activeModalities.has(m.id) || SEEDED_MODALITIES.includes(m.id))
    .map((m) => {
      const g = graduationOf(m.id);
      return { id: m.id, name: m.name, graduationEnabled: g.enabled, levels: g.enabled ? g.levels.map(({ id, name, maxDegree }) => ({ id, name, maxDegree })) : [] };
    });
type TeacherInput = { name?: string; email?: string | null; phone?: string | null; bio?: string | null; directoryConsent?: boolean; consentDeclaredByManagement?: boolean; unitIds?: string[]; modalities?: { modalityId?: string; levelId?: string | null; degree?: number | null }[] };
const teacherBad = (field: string, message: string) => error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field, message }]);
// Devolve o cadastro validado ou a resposta de erro. `current` é o professor editado (null na criação): o Administrador mantém as unidades de fora do alcance dele.
const teacherFromInput = (b: TeacherInput, current: MockTeacher | null) => {
  const name = (b.name ?? "").trim();
  if (name.length < 2 || name.length > 80) return teacherBad("name", "de 2 a 80 caracteres");
  const email = b.email?.trim() || null;
  if (email && (email.length > 254 || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email))) return teacherBad("email", "e-mail inválido");
  let phone: string | null = null;
  if (b.phone?.trim()) {
    phone = b.phone.replace(/\D/g, "");
    if (phone.length > 11 && phone.startsWith("55")) phone = phone.slice(2);
    if (!/^[1-9][1-9](9\d{8}|\d{8})$/.test(phone)) return teacherBad("phone", "telefone inválido");
  }
  const bio = b.bio?.trim() || null;
  if (bio && bio.length > 300) return teacherBad("bio", "no máximo 300 caracteres");
  const consent = b.directoryConsent === true;
  const declared = b.consentDeclaredByManagement === true;
  if (consent && !declared) return teacherBad("consentDeclaredByManagement", "declare que o professor autorizou");
  const asked = [...new Set(b.unitIds ?? [])];
  if (asked.length === 0) return teacherBad("unitIds", "escolha ao menos uma unidade");
  if (activeRole() === "ADMINISTRATOR" && asked.some((id) => id !== ADMIN_UNIT)) return error(403, "FORBIDDEN", "Sem permissão.");
  if (asked.some((id) => !units.some((u) => u.id === id && u.active))) return teacherBad("unitIds", "escolha unidades ativas da organização");
  const options = teacherOptions();
  const seen = new Set<string>();
  const modalities: MockModality[] = [];
  for (const m of b.modalities ?? []) {
    const option = options.find((o) => o.id === m.modalityId);
    if (!option || seen.has(option.id)) return teacherBad("modalities", option ? "modalidade repetida" : "modalidade inativa ou inexistente");
    seen.add(option.id);
    if (!option.graduationEnabled) {
      if (m.levelId || (m.degree ?? 0) !== 0) return teacherBad("modalities", "a modalidade não tem graduação");
      modalities.push({ id: option.id, name: option.name, levelId: null, level: null, degree: null });
      continue;
    }
    const level = option.levels.find((l) => l.id === m.levelId);
    if (!level) return teacherBad("modalities", m.levelId ? "nível inválido para a modalidade" : "escolha o nível");
    if (!Number.isInteger(m.degree) || m.degree! < 0 || m.degree! > level.maxDegree) return teacherBad("modalities", `grau de 0 a ${level.maxDegree}`);
    modalities.push({ id: option.id, name: option.name, levelId: level.id, level: level.name, degree: m.degree! });
  }
  const kept = activeRole() === "ADMINISTRATOR" ? (current?.units ?? []).filter((u) => u.id !== ADMIN_UNIT) : [];
  const chosen = [...kept, ...asked.map((id) => ({ id, name: units.find((u) => u.id === id)!.name }))].sort((a, c) => a.name.localeCompare(c.name));
  return { name, directoryConsent: consent, modalities: modalities.sort((a, c) => a.name.localeCompare(c.name)), units: chosen, contact: { bio, email, phone, consentDeclaredByManagement: declared } };
};

// Alunos e responsáveis (RF-ALU-01), em memória: o Administrador (ADMIN_UNIT) só alcança os cadastrados na unidade dele (404 fora).
const STUDENT_ADULT = "00000000-0000-0000-0000-000000000701";
const STUDENT_MINOR = "00000000-0000-0000-0000-000000000702";
const initialStudents = (): StudentDetail[] => [
  { id: STUDENT_ADULT, registeredUnitId: ADMIN_UNIT, name: "Aluno Adulto Fictício", birthDate: "1990-03-10", minor: false, postalCode: "01001000", street: "Rua Fictícia", number: "1", complement: null, district: "Centro", city: "Rio de Janeiro", state: "RJ", cpf: "52998224725", email: null, phone: "21900000001", gender: null, referral: null, emergencyName: null, emergencyRelationship: null, emergencyPhone: null, photoUrl: null, emailProblem: false, guardians: [] },
  { id: STUDENT_MINOR, registeredUnitId: ADMIN_UNIT, name: "Aluno Menor Fictício", birthDate: "2014-05-02", minor: true, postalCode: "01001000", street: "Rua Fictícia", number: "2", complement: null, district: "Centro", city: "Rio de Janeiro", state: "RJ", cpf: null, email: null, phone: null, gender: null, referral: null, emergencyName: null, emergencyRelationship: null, emergencyPhone: null, photoUrl: null, emailProblem: false, guardians: [{ guardianId: "00000000-0000-0000-0000-000000000801", name: "Responsável Um Fictício", email: "responsavel1@alfa.test", phone: "21900000011", financial: true, access: "PENDING", emailProblem: false }] },
];
let mockStudents = initialStudents();
let guardianResends = 0;
// Histórico do aluno (RF-ALU-03), em memória: o servidor lê a auditoria; aqui as rotas de responsável acrescentam a linha.
let studentLog: Record<string, { action: string; at: string; actor: string | null; actorProfile: string | null; reason: string | null }[]> = {};
const logStudent = (id: string, action: string, reason: string | null) => {
  (studentLog[id] ??= []).push({ action, at: new Date().toISOString(), actor: "Ana Coordenadora", actorProfile: activeRole() ?? null, reason });
};
const guardianCpfs: Record<string, string> = { "00000000-0000-0000-0000-000000000801": "98765432100" };
const studentError = (status: number, code: string, existing?: StudentDetail) =>
  error(status, code, "Dados inválidos.", existing ? [{ field: "id", message: existing.id }, { field: "name", message: existing.name }, { field: "photoUrl", message: existing.photoUrl ?? "" }] : []);
const studentBad = (field: string, message: string) => error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field, message }]);
// 401 sem sessão, 403 sem STUDENT_MANAGE (só Coordenador e Administrador).
const studentAccess = () => {
  if (!loggedIn) return unauthenticated();
  const role = activeRole();
  return role === "COORDINATOR" || role === "ADMINISTRATOR" ? null : error(403, "FORBIDDEN", "Sem permissão.");
};
const studentFor = (id: string) => {
  const denied = studentAccess();
  if (denied) return denied;
  const found = mockStudents.find((s) => s.id === id && (activeRole() === "COORDINATOR" || s.registeredUnitId === ADMIN_UNIT));
  return found ?? error(404, "NOT_FOUND", "Não encontrado.");
};
// RF-ALU-02: selos pelos mesmos critérios do servidor; paga o próprio adulto ou o responsável financeiro do menor (RN-ALU-09, RN-ALU-10).
const SEALS: StudentSeal[] = ["INCOMPLETE_REGISTRATION", "EMAIL_PROBLEM", "BIRTHDAY_TODAY"];
const sealsOf = (s: StudentDetail): StudentSeal[] => {
  const guardian = s.minor ? s.guardians.find((g) => g.financial) : undefined;
  const hasCpf = s.minor ? Boolean(guardian && guardianCpfs[guardian.guardianId]) : Boolean(s.cpf);
  const email = s.minor ? guardian?.email : s.email;
  const problem = s.minor ? Boolean(guardian?.emailProblem) : s.emailProblem;
  const now = new Date();
  const birth = new Date(s.birthDate);
  const leapDay = birth.getUTCMonth() === 1 && birth.getUTCDate() === 29 && now.getMonth() === 1 && now.getDate() === 28 && now.getFullYear() % 4 !== 0;
  const birthday = (birth.getUTCMonth() === now.getMonth() && birth.getUTCDate() === now.getDate()) || leapDay;
  return SEALS.filter((seal) => (seal === "INCOMPLETE_REGISTRATION" ? !hasCpf || !email : seal === "EMAIL_PROBLEM" ? problem : birthday));
};
const ageOf = (birth: string) => {
  const b = new Date(birth);
  const now = new Date();
  return now.getFullYear() - b.getFullYear() - (now < new Date(now.getFullYear(), b.getMonth(), b.getDate()) ? 1 : 0);
};
const covers = (r: MockAgeRange, age: number) => age >= r.minAge && (r.maxAge === null || age <= r.maxAge);
const studentRowOf = (s: StudentDetail): StudentRow => {
  const age = ageOf(s.birthDate);
  const own = [...ageRanges].filter((r) => covers(r, age)).sort((a, b) => b.minAge - a.minAge)[0];
  return {
    id: s.id, name: s.name, photoUrl: s.photoUrl, birthDate: s.birthDate, age, minor: s.minor,
    ageRange: own ? { id: own.id, name: own.name } : null,
    registeredUnit: { id: s.registeredUnitId, name: units.find((u) => u.id === s.registeredUnitId)?.name ?? "" },
    createdAt: "2026-01-10T12:00:00Z", seals: sealsOf(s),
  };
};
const isMinor = (birthDate: string) => {
  const limit = new Date(birthDate);
  limit.setFullYear(limit.getFullYear() + 18);
  return limit.getTime() > Date.now();
};
const studentReason = (reason: unknown) => (typeof reason === "string" && reason.trim().length >= 3 && reason.trim().length <= 200 ? null : studentBad("reason", "de 3 a 200 caracteres"));
// Valida o responsável e o devolve como linha; CPF e e-mail de outro responsável dão 409 GUARDIAN_CONFLICT.
const guardianRow = (g: Partial<StudentGuardianInput> | undefined, financial: boolean): StudentGuardianRow | Response => {
  const name = g?.name?.trim() ?? "";
  if (name.length < 2 || name.length > 80) return studentBad("guardians.name", "de 2 a 80 caracteres");
  if (!isValidCpf(g?.cpf ?? "")) return studentBad("guardians.cpf", "CPF inválido");
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(g?.email?.trim() ?? "")) return studentBad("guardians.email", "e-mail inválido");
  if (!/^\d{10,11}$/.test(onlyDigits(g?.phone ?? ""))) return studentBad("guardians.phone", "celular com DDD");
  const email = g!.email!.trim();
  const known = mockStudents.flatMap((s) => s.guardians).find((x) => x.email.toLowerCase() === email.toLowerCase());
  const cpf = onlyDigits(g!.cpf!);
  const cpfOwner = Object.entries(guardianCpfs).find(([, c]) => c === cpf)?.[0];
  if ((known && guardianCpfs[known.guardianId] !== cpf) || (cpfOwner && cpfOwner !== known?.guardianId)) {
    return error(409, "GUARDIAN_CONFLICT", "Este e-mail ou CPF já pertence a outro responsável.");
  }
  const guardianId = known?.guardianId ?? crypto.randomUUID();
  guardianCpfs[guardianId] = cpf;
  return { guardianId, name: known?.name ?? name, email: known?.email ?? email, phone: known?.phone ?? onlyDigits(g!.phone!), financial, access: known?.access ?? "PENDING", emailProblem: false };
};
const sortedGuardians = (list: StudentGuardianRow[]) => [...list].sort((a, b) => Number(b.financial) - Number(a.financial) || a.name.localeCompare(b.name));

export const routes: Route[] = [
  {
    method: "post",
    path: "/auth/login",
    resolver: async ({ request }) => {
      const { email, password } = (await request.json()) as { email?: string; password?: string };
      if (password === "limite") return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      if (password === "errada") return error(401, "INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
      const account = accounts[email ?? ""];
      if (account?.twoFactor.enabled) {
        ticketAccount = account.account.email;
        return HttpResponse.json({ twoFactorRequired: true, ticket: email === "vencida@exemplo.com" ? "vencido" : "bilhete" });
      }
      loggedIn = true;
      current = account ?? (email === many.account.email ? { ...many } : session);
      return HttpResponse.json(current);
    },
  },
  {
    method: "post",
    path: "/auth/login/two-factor",
    resolver: async ({ request }) => {
      const { ticket, code } = (await request.json()) as { ticket?: string; code?: string };
      if (!ticketAccount || ticket !== "bilhete") return error(401, "TWO_FACTOR_TICKET_INVALID", "Verificação vencida.");
      if (code === "limite") return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      if (!code || !VALID_CODES.includes(code)) return error(401, "INVALID_TWO_FACTOR_CODE", "Código inválido.");
      loggedIn = true;
      current = accounts[ticketAccount] ?? session;
      ticketAccount = null;
      return HttpResponse.json(current);
    },
  },
  {
    method: "post",
    path: "/auth/logout",
    resolver: () => {
      loggedIn = false;
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    method: "post",
    path: "/auth/two-factor/setup",
    resolver: () =>
      !loggedIn
        ? unauthenticated()
        : current.twoFactor.enabled
          ? error(409, "CONFLICT", "As duas etapas já estão ligadas.")
          : HttpResponse.json({ secret: "JBSWY3DPEHPK3PXP", otpauthUri: "otpauth://totp/ctcore:ana%40exemplo.com?secret=JBSWY3DPEHPK3PXP&issuer=ctcore" }),
  },
  {
    method: "post",
    path: "/auth/two-factor/enable",
    resolver: async ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const { code } = (await request.json()) as { code?: string };
      if (code !== "123456") return error(401, "INVALID_TWO_FACTOR_CODE", "Código inválido.");
      current = { ...current, twoFactor: { ...current.twoFactor, enabled: true } };
      return HttpResponse.json(backupCodes());
    },
  },
  {
    method: "delete",
    path: "/auth/two-factor",
    resolver: async ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const { password, code } = (await request.json()) as { password?: string; code?: string };
      if (password === "limite") return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      if (password === "errada") return error(401, "INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
      if (!code || !VALID_CODES.includes(code)) return error(401, "INVALID_TWO_FACTOR_CODE", "Código inválido.");
      if (current.twoFactor.required) return error(403, "FORBIDDEN", "Sem permissão.");
      current = { ...current, twoFactor: { ...current.twoFactor, enabled: false } };
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    method: "post",
    path: "/auth/two-factor/backup-codes",
    resolver: async ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const { password, code } = (await request.json()) as { password?: string; code?: string };
      if (password === "limite") return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      if (password === "errada") return error(401, "INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
      if (!code || !VALID_CODES.includes(code)) return error(401, "INVALID_TWO_FACTOR_CODE", "Código inválido.");
      return HttpResponse.json(backupCodes());
    },
  },
  {
    method: "post",
    path: "/auth/reauth",
    resolver: async ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const { action, password, code } = (await request.json()) as { action?: string; password?: string; code?: string };
      if (!action) return error(400, "VALIDATION_ERROR", "Dados inválidos.");
      if (password === "limite") return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      if (password === "errada") return error(401, "INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
      if (current.twoFactor.enabled && (!code || !VALID_CODES.includes(code))) return error(401, "INVALID_TWO_FACTOR_CODE", "Código inválido.");
      return HttpResponse.json({ reauthToken: "token-de-confirmacao", expiresAt: new Date(Date.now() + 5 * 60_000).toISOString() });
    },
  },
  {
    method: "post",
    path: "/auth/password",
    resolver: async ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const { currentPassword, newPassword } = (await request.json()) as { currentPassword?: string; newPassword?: string };
      if (currentPassword === "limite") return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      if (currentPassword === "errada") return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "currentPassword", message: "A senha atual está incorreta." }]);
      if (newPassword === "Password1") return error(400, "PASSWORD_LEAKED", "Senha conhecida demais.");
      if (!newPassword || !isStrongPassword(newPassword)) return error(400, "PASSWORD_WEAK", "Senha fraca.");
      sessions = sessions.filter((s) => s.current);
      return new HttpResponse(null, { status: 204 });
    },
  },
  // Troca de e-mail (RN-ACE-15): senha "errada" 401, "limite" 429; com as duas etapas, código válido 123456; o link "valido" confirma, outro é 410.
  {
    method: "post",
    path: "/auth/email-change",
    resolver: async ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const { newEmail, password, code } = (await request.json()) as { newEmail?: string; password?: string; code?: string };
      if (!newEmail || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(newEmail) || newEmail.toLowerCase() === current.account.email) {
        return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "newEmail", message: "Informe outro e-mail válido." }]);
      }
      if (password === "limite") return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      if (password === "errada") return error(401, "INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
      if (current.twoFactor.enabled && (!code || !VALID_CODES.includes(code))) return error(401, "INVALID_TWO_FACTOR_CODE", "Código inválido.");
      return new HttpResponse(null, { status: 202 });
    },
  },
  {
    method: "post",
    path: "/auth/email-change/{code}/confirm",
    resolver: ({ params }) => {
      if (!loggedIn) return unauthenticated();
      if (params.code !== "valido") return error(410, "EMAIL_CHANGE_INVALID", "Link inválido.");
      current = { ...current, account: { ...current.account, email: "novo@exemplo.com" } };
      return new HttpResponse(null, { status: 204 });
    },
  },
  // Sessões da conta (RF-ACE-04): a atual e mais uma, em outro aparelho.
  {
    method: "get",
    path: "/auth/sessions",
    resolver: () => (loggedIn ? HttpResponse.json(sessions) : unauthenticated()),
  },
  {
    method: "delete",
    path: "/auth/sessions",
    resolver: () => {
      if (!loggedIn) return unauthenticated();
      sessions = sessions.filter((s) => s.current);
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    method: "delete",
    path: "/auth/sessions/{id}",
    resolver: ({ params }) => {
      if (!loggedIn) return unauthenticated();
      const found = sessions.find((s) => s.id === params.id);
      if (!found) return error(404, "NOT_FOUND", "Não encontrado.");
      if (found.current) return error(409, "SESSION_CURRENT", "Esta é a sessão em uso.");
      sessions = sessions.filter((s) => s.id !== params.id);
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    method: "get",
    path: "/auth/invitations/{code}",
    resolver: ({ params }) => {
      const invite = invites[String(params.code)];
      if (!invite) return gone(String(params.code));
      return HttpResponse.json({ ...invite, role: "ADMINISTRATOR", organizationId: "00000000-0000-0000-0000-000000000003", expiresAt: "2026-12-31T15:30:00Z" });
    },
  },
  {
    method: "post",
    path: "/auth/invitations/{code}/accept",
    resolver: async ({ params, request }) => {
      const code = String(params.code);
      const invite = invites[code];
      if (!invite) return gone(code);
      const { password } = (await request.json()) as { password?: string };
      if (invite.existingAccount) {
        if (password === "limite") return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
        if (password === "errada") return error(401, "INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
      } else {
        if (password === "Password1") return error(400, "PASSWORD_LEAKED", "Senha conhecida demais.");
        if (!password || !isStrongPassword(password)) return error(400, "PASSWORD_WEAK", "Senha fraca.");
      }
      loggedIn = true;
      current = session;
      return HttpResponse.json(session);
    },
  },
  {
    method: "post",
    path: "/auth/password-reset",
    resolver: async ({ request }) => {
      const { email } = (await request.json()) as { email?: string };
      if (email === "limite@exemplo.com") return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      return HttpResponse.json({ message: "Se esse e-mail tiver uma conta, você vai receber um link em instantes." });
    },
  },
  // Links de recuperação: "valido" aceita senha forte; "fraca" e "vazada" sempre dão 400; outro código (ex.: "vencido") é 410.
  {
    method: "post",
    path: "/auth/password-reset/{code}",
    resolver: async ({ params, request }) => {
      const code = String(params.code);
      if (!["valido", "fraca", "vazada"].includes(code)) return error(410, "RESET_LINK_INVALID", "Link inválido.");
      const { password } = (await request.json()) as { password?: string };
      if (code === "vazada" || password === "Password1") return error(400, "PASSWORD_LEAKED", "Senha conhecida demais.");
      if (code === "fraca" || !password || !isStrongPassword(password)) return error(400, "PASSWORD_WEAK", "Senha fraca.");
      loggedIn = true;
      current = session;
      return HttpResponse.json(session);
    },
  },
  {
    method: "get",
    path: "/auth/session",
    resolver: () => (loggedIn ? HttpResponse.json(current) : error(401, "UNAUTHENTICATED", "Sem sessão.")),
  },
  {
    method: "put",
    path: "/auth/session/link",
    resolver: async ({ request }) => {
      if (!loggedIn) return error(401, "UNAUTHENTICATED", "Sem sessão.");
      const { linkId } = (await request.json()) as { linkId?: string };
      if (!current.links.some((l) => l.id === linkId)) return error(404, "LINK_NOT_FOUND", "Vínculo não encontrado.");
      current = { ...current, activeLinkId: linkId ?? null };
      return HttpResponse.json(current);
    },
  },
  {
    method: "get",
    path: "/permissions",
    resolver: () =>
      loggedIn
        ? HttpResponse.json({ permissions: mockPermissions(current.links.find((l) => l.id === current.activeLinkId)?.role) })
        : unauthenticated(),
  },
  {
    method: "get",
    path: "/organization",
    resolver: () => organizationAccess(false) ?? HttpResponse.json(organization),
  },
  {
    method: "patch",
    path: "/organization",
    resolver: async ({ request }) => {
      const denied = organizationAccess(true);
      if (denied) return denied;
      const missing = precondition(request);
      if (missing) return missing;
      const body = (await request.json()) as { name?: string; color?: string | null; holderDocument?: string | null };
      const name = (body.name ?? "").trim();
      const color = body.color ? body.color : null;
      const holder = body.holderDocument ? body.holderDocument : null;
      const fields: { field: string; message: string }[] = [];
      if (!name || name.length > 120) fields.push({ field: "name", message: "entre 1 e 120 caracteres" });
      if (color !== null && !(isHexColor(color) && passesContrast(color))) fields.push({ field: "color", message: "use #RRGGBB com contraste mínimo de 4,5:1" });
      if (holder !== null && !isValidCpfOrCnpj(holder)) fields.push({ field: "holderDocument", message: "CPF ou CNPJ inválido" });
      if (fields.length) return error(400, "VALIDATION_ERROR", "Dados inválidos.", fields);
      const stale = precondition(request, organization.version);
      if (stale) return stale;
      const next = { ...organization, name, color: color?.toLowerCase() ?? null, holderDocument: holder ? onlyDigits(holder) : null };
      const changed = next.name !== organization.name || next.color !== organization.color || next.holderDocument !== organization.holderDocument;
      organization = changed ? { ...next, version: organization.version + 1, lastChange: changedNow() } : organization;
      return HttpResponse.json(organization);
    },
  },
  {
    method: "put",
    path: "/organization/logo",
    resolver: async ({ request }) => {
      const denied = organizationAccess(true);
      if (denied) return denied;
      // O corpo multipart é lido como texto: o parser do Node não aceita o File do jsdom nos testes.
      const raw = await request.text();
      if (!raw.includes('name="file"') || raw.length < 20 || raw.length > 5 * 1024 * 1024) {
        return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "file", message: "arquivo inválido" }]);
      }
      organization = { ...organization, logoUrl: MOCK_LOGO, lastChange: changedNow() };
      return HttpResponse.json(organization);
    },
  },
  {
    method: "delete",
    path: "/organization/logo",
    resolver: () => {
      const denied = organizationAccess(true);
      if (denied) return denied;
      organization = { ...organization, logoUrl: null, lastChange: changedNow() };
      return HttpResponse.json(organization);
    },
  },
  {
    // RF-ORG-06: só o Coordenador; "admin@alfa.test" é o único Administrador elegível. Com sucesso, o vínculo ativo vira Administrador.
    method: "post",
    path: "/organization/coordination-transfer",
    resolver: async ({ request }) => {
      const denied = organizationAccess(true);
      if (denied) return denied;
      if (!request.headers.get("X-Reauth-Token")) return error(403, "REAUTH_REQUIRED", "Confirme sua senha para continuar.");
      const { email, confirmation } = (await request.json()) as { email?: string; confirmation?: string };
      if ((confirmation ?? "").trim().toUpperCase() !== "TRANSFERIR") {
        return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "confirmation", message: "digite TRANSFERIR" }]);
      }
      if ((email ?? "").trim().toLowerCase() !== TRANSFER_TARGET) {
        return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "email", message: "informe um Administrador ativo da organização" }]);
      }
      current = {
        ...current,
        links: current.links.map((l) => (l.id === current.activeLinkId ? { ...l, role: "ADMINISTRATOR" as const } : l)),
      };
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    method: "get",
    path: "/modalities",
    resolver: () => {
      const denied = organizationAccess(false);
      if (denied) return denied;
      const canManage = activeRole() === "COORDINATOR";
      return HttpResponse.json(modalityCatalog.map((m) => ({ ...m, active: activeModalities.has(m.id), canManage, graduationEnabled: graduationOf(m.id).enabled })));
    },
  },
  {
    // "Boxe" simula modalidade em uso (2 turmas, 3 alunos): desativar responde 409 MODALITY_IN_USE.
    method: "put",
    path: "/modalities/{id}/activation",
    resolver: async ({ request, params }) => {
      const denied = organizationAccess(true);
      if (denied) return denied;
      const found = modalityCatalog.find((m) => m.id === params.id);
      if (!found) return error(404, "NOT_FOUND", "Não encontrado.");
      const { active } = (await request.json()) as { active?: boolean };
      if (typeof active !== "boolean") return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "active", message: "obrigatório" }]);
      if (!active && found.name === "Boxe" && activeModalities.has(found.id)) {
        return error(409, "MODALITY_IN_USE", "Em uso.", [
          { field: "activeClasses", message: "2" },
          { field: "activeStudents", message: "3" },
        ]);
      }
      if (active) {
        activeModalities.add(found.id);
        if (!graduations.has(found.id)) graduations.set(found.id, { enabled: false, requireOriginProof: false, levels: found.name === "Jiu-Jitsu" ? jiuJitsuDefault() : [] });
      } else activeModalities.delete(found.id);
      return HttpResponse.json({ ...found, active, canManage: true, graduationEnabled: graduationOf(found.id).enabled });
    },
  },
  {
    // RF-MOD-03: só as modalidades ativas da organização; a marcação fica guardada se a organização desativar e voltar.
    method: "get",
    path: "/units/{unitId}/modalities",
    resolver: ({ params }) => {
      const denied = unitSettingsAccess(String(params.unitId));
      if (denied) return denied;
      const offered = unitModalities.get(String(params.unitId)) ?? new Set<string>();
      const items = modalityCatalog.filter((m) => activeModalities.has(m.id)).map((m) => ({ ...m, offered: offered.has(m.id) }));
      return HttpResponse.json({ canManage: true, items });
    },
  },
  {
    method: "put",
    path: "/units/{unitId}/modalities/{modalityId}",
    resolver: async ({ request, params }) => {
      const unitId = String(params.unitId);
      const denied = unitSettingsAccess(unitId);
      if (denied) return denied;
      const found = modalityCatalog.find((m) => m.id === params.modalityId && activeModalities.has(m.id));
      if (!found) return error(404, "NOT_FOUND", "Não encontrado.");
      const { offered } = (await request.json()) as { offered?: boolean };
      if (typeof offered !== "boolean") return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "offered", message: "obrigatório" }]);
      // RN-MOD-06: desmarcar com turma ativa da modalidade na unidade é recusado e lista as turmas.
      const blocking = mockClasses.filter((c) => c.unitId === unitId && c.modalityId === found.id && c.status === "ACTIVE").map((c) => c.name).sort((a, b) => a.localeCompare(b));
      if (!offered && unitModalities.get(unitId)?.has(found.id) && blocking.length > 0) return error(409, "UNIT_MODALITY_IN_USE", "Há turmas ativas.", blocking.map((name) => ({ field: "class", message: name })));
      const set = unitModalities.get(unitId) ?? new Set<string>();
      if (offered) set.add(found.id);
      else set.delete(found.id);
      unitModalities.set(unitId, set);
      return HttpResponse.json({ ...found, offered });
    },
  },
  {
    method: "get",
    path: "/modalities/{id}/graduation",
    resolver: ({ params }) => {
      const denied = organizationAccess(false);
      if (denied) return denied;
      if (!modalityCatalog.some((m) => m.id === params.id)) return error(404, "NOT_FOUND", "Não encontrado.");
      if (!activeModalities.has(params.id as string)) return error(409, "MODALITY_NOT_ACTIVE", "A modalidade não está ativa.");
      return HttpResponse.json({ ...graduationOf(params.id as string), canManage: activeRole() === "COORDINATOR" });
    },
  },
  {
    method: "put",
    path: "/modalities/{id}/graduation",
    resolver: async ({ request, params }) => {
      const denied = organizationAccess(true);
      if (denied) return denied;
      const id = params.id as string;
      if (!modalityCatalog.some((m) => m.id === id)) return error(404, "NOT_FOUND", "Não encontrado.");
      if (!activeModalities.has(id)) return error(409, "MODALITY_NOT_ACTIVE", "A modalidade não está ativa.");
      const body = (await request.json()) as { enabled?: boolean; requireOriginProof?: boolean; levels?: Partial<MockLevel>[] };
      const bad = (field: string, message: string) => error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field, message }]);
      if (typeof body.enabled !== "boolean") return bad("enabled", "obrigatório");
      if (!Array.isArray(body.levels) || body.levels.length > 30) return bad("levels", "no máximo 30 níveis");
      if (body.enabled && body.levels.length === 0) return bad("levels", "inclua ao menos um nível para ligar a graduação");
      const key = (s: string) => s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
      const seen = new Set<string>();
      for (const [i, l] of body.levels.entries()) {
        const name = l.name?.trim() ?? "";
        if (name.length < 1 || name.length > 40) return bad(`levels[${i}].name`, "de 1 a 40 caracteres");
        if (seen.has(key(name))) return bad(`levels[${i}].name`, "nome repetido");
        seen.add(key(name));
        if (!/^#[0-9A-Fa-f]{6}$/.test(l.color ?? "")) return bad(`levels[${i}].color`, "use o formato #RRGGBB");
      }
      const saved: MockGraduation = {
        enabled: body.enabled,
        requireOriginProof: body.requireOriginProof === true,
        levels: body.levels.map((l) => ({ id: l.id ?? crypto.randomUUID(), name: l.name!.trim(), color: l.color!, secondaryColor: l.secondaryColor ?? null, maxDegree: l.maxDegree ?? 0, minAge: l.minAge ?? null, maxAge: l.maxAge ?? null, kids: l.kids === true })),
      };
      graduations.set(id, saved);
      return HttpResponse.json({ ...saved, canManage: true });
    },
  },
  {
    method: "get",
    path: "/modalities/requests",
    resolver: () => organizationAccess(false) ?? HttpResponse.json(modalityRequests),
  },
  {
    method: "post",
    path: "/modalities/requests",
    resolver: async ({ request }) => {
      const denied = organizationAccess(true);
      if (denied) return denied;
      const body = (await request.json()) as { name?: string; note?: string | null };
      const name = body.name?.trim() ?? "";
      const note = body.note?.trim() || null;
      if (name.length < 2 || name.length > 60) return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "name", message: "de 2 a 60 caracteres" }]);
      if (note && note.length > 300) return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "note", message: "no máximo 300 caracteres" }]);
      const key = (s: string) => s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
      if (modalityCatalog.some((m) => key(m.name) === key(name))) return error(409, "ALREADY_IN_CATALOG", "Esta modalidade já existe no catálogo. Ative-a na lista.");
      if (modalityRequests.some((r) => r.status === "OPEN" && key(r.name) === key(name))) {
        return error(409, "REQUEST_ALREADY_OPEN", "Você já pediu esta modalidade e o pedido ainda está aberto.");
      }
      const created = { id: crypto.randomUUID(), name, note, status: "OPEN", createdAt: new Date().toISOString() };
      modalityRequests = [created, ...modalityRequests];
      return HttpResponse.json(created, { status: 201 });
    },
  },
  {
    method: "get",
    path: "/age-ranges",
    resolver: () => organizationAccess(false) ?? HttpResponse.json({ canManage: activeRole() === "COORDINATOR", items: sortedAgeRanges() }),
  },
  {
    method: "post",
    path: "/age-ranges",
    resolver: async ({ request }) => {
      const denied = organizationAccess(true);
      if (denied) return denied;
      const checked = checkAgeRange((await request.json()) as AgeRangeInput, null);
      if (checked instanceof Response) return checked;
      ageRanges = [...ageRanges, checked];
      return HttpResponse.json(checked, { status: 201 });
    },
  },
  {
    method: "put",
    path: "/age-ranges/{id}",
    resolver: async ({ request, params }) => {
      const denied = organizationAccess(true);
      if (denied) return denied;
      if (!ageRanges.some((r) => r.id === params.id)) return error(404, "NOT_FOUND", "Não encontrado.");
      const checked = checkAgeRange((await request.json()) as AgeRangeInput, params.id as string);
      if (checked instanceof Response) return checked;
      ageRanges = ageRanges.map((r) => (r.id === params.id ? checked : r));
      return HttpResponse.json(checked);
    },
  },
  {
    method: "delete",
    path: "/age-ranges/{id}",
    resolver: ({ params }) => {
      const denied = organizationAccess(true);
      if (denied) return denied;
      if (!ageRanges.some((r) => r.id === params.id)) return error(404, "NOT_FOUND", "Não encontrado.");
      ageRanges = ageRanges.filter((r) => r.id !== params.id);
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    method: "get",
    path: "/units",
    resolver: () => unitsAccess() ?? HttpResponse.json(units),
  },
  {
    // Limite de 10 (409 UNIT_LIMIT_REACHED); convite para "existente@exemplo.com" falha (invite FAILED); nome vazio ou longo é 400.
    method: "post",
    path: "/units",
    resolver: async ({ request }) => {
      const denied = unitsAccess();
      if (denied) return denied;
      const body = (await request.json()) as { name?: string; administrator?: { name?: string; email?: string } | null };
      const name = body.name?.trim() ?? "";
      if (name === "" || name.length > 120) {
        return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "name", message: "inválido" }]);
      }
      if (units.length >= 10) return error(409, "UNIT_LIMIT_REACHED", "Limite de unidades.");
      const base = name.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "unidade";
      let shortName = base;
      for (let n = 2; units.some((u) => u.shortName === shortName); n++) shortName = `${base}-${n}`;
      const unit = { id: crypto.randomUUID(), name, shortName, shortNameAdjusted: false, city: null as string | null, activeStudents: 0, administrators: [] as { name: string }[], active: true };
      units = [...units, unit];
      const admin = body.administrator;
      const invite = !admin?.email ? "NONE" : admin.email === "existente@exemplo.com" ? "FAILED" : "SENT";
      return HttpResponse.json({ unit, invite }, { status: 201 });
    },
  },
  {
    method: "patch",
    path: "/units/{id}/short-name",
    resolver: async ({ request, params }) => {
      const denied = unitsAccess();
      if (denied) return denied;
      const unit = units.find((u) => u.id === params.id);
      if (!unit) return error(404, "NOT_FOUND", "Não encontrado.");
      const { shortName } = (await request.json()) as { shortName?: string | null };
      if (!shortName || shortName.length > 60 || !/^[a-z0-9]+(-[a-z0-9]+)*$/.test(shortName)) {
        return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "shortName", message: "formato inválido" }]);
      }
      if (unit.shortNameAdjusted || units.some((u) => u.shortName === shortName) || shortName === "em-uso") {
        return error(409, "CONFLICT", "Conflito.");
      }
      Object.assign(unit, { shortName, shortNameAdjusted: true });
      return HttpResponse.json(unit);
    },
  },
  {
    // RF-ORG-07: sem alunos nem turmas no mock; a única unidade ativa não pode ser desativada.
    method: "get",
    path: "/units/{id}/deactivation-check",
    resolver: ({ params }) => {
      const denied = unitsAccess();
      if (denied) return denied;
      const unit = units.find((u) => u.id === params.id);
      if (!unit) return error(404, "NOT_FOUND", "Não encontrado.");
      const last = !units.some((u) => u.active && u.id !== unit.id);
      return HttpResponse.json({ activeStudents: 0, activeClasses: 0, lastActiveUnit: last, canDeactivate: !last });
    },
  },
  {
    method: "post",
    path: "/units/{id}/deactivation",
    resolver: async ({ request, params }) => {
      const denied = unitsAccess();
      if (denied) return denied;
      const unit = units.find((u) => u.id === params.id);
      if (!unit) return error(404, "NOT_FOUND", "Não encontrado.");
      if (!unit.active) return error(409, "CONFLICT", "Conflito.");
      const { confirmation } = (await request.json()) as { confirmation?: string };
      if ((confirmation ?? "").trim().toLowerCase() !== unit.name.toLowerCase()) {
        return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "confirmation", message: "digite o nome da unidade" }]);
      }
      if (!units.some((u) => u.active && u.id !== unit.id)) {
        return error(409, "UNIT_HAS_BLOCKERS", "Única unidade ativa.", [
          { field: "activeStudents", message: "0" },
          { field: "activeClasses", message: "0" },
          { field: "lastActiveUnit", message: "true" },
        ]);
      }
      if (!request.headers.get("X-Reauth-Token")) return error(403, "REAUTH_REQUIRED", "Confirme sua senha para continuar.");
      unit.active = false;
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    method: "get",
    path: "/units/mine",
    resolver: () => {
      const denied = unitSettingsRole();
      if (denied) return denied;
      const own = activeRole() === "COORDINATOR" ? units : units.filter((u) => u.id === ADMIN_UNIT);
      return HttpResponse.json(own.map(({ id, name, shortName, city, active }) => ({ id, name, shortName, city, active })));
    },
  },
  {
    // RF-ORG-05: unidades ativas ao alcance; Coordenador todas e allUnits, os demais só a ADMIN_UNIT.
    method: "get",
    path: "/units/reachable",
    resolver: () => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "COORDINATOR" && role !== "ADMINISTRATOR" && role !== "TEACHER") return error(403, "FORBIDDEN", "Sem permissão.");
      const coordinator = role === "COORDINATOR";
      const own = units.filter((u) => u.active && (coordinator || u.id === ADMIN_UNIT));
      return HttpResponse.json({ allUnits: coordinator, units: own.map(({ id, name, shortName }) => ({ id, name, shortName })) });
    },
  },
  {
    method: "get",
    path: "/units/{id}",
    resolver: ({ params }) => {
      const denied = unitSettingsAccess(String(params.id));
      return denied ?? HttpResponse.json(unitDetail(String(params.id)));
    },
  },
  {
    // Substituição completa (D10). Valida o essencial como o servidor; o nome curto e a conexão do Mercado Pago não mudam.
    method: "put",
    path: "/units/{id}",
    resolver: async ({ request, params }) => {
      const id = String(params.id);
      const denied = unitSettingsAccess(id);
      if (denied) return denied;
      const missing = precondition(request);
      if (missing) return missing;
      const b = (await request.json()) as Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any
      const s = (b.settings ?? {}) as Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any
      const bad: { field: string; message: string }[] = [];
      const name = String(b.name ?? "").trim();
      if (!name || name.length > 120) bad.push({ field: "name", message: "entre 1 e 120 caracteres" });
      if (b.cnpj && !isValidCpfOrCnpj(String(b.cnpj))) bad.push({ field: "cnpj", message: "CNPJ inválido" });
      const days: number[] = s.dueDays ?? [];
      if (days.length === 0 || new Set(days).size !== days.length || days.some((d) => d < 1 || d > 28)) bad.push({ field: "settings.dueDays", message: "valores entre 1 e 28" });
      if (s.pixKey && !/^([^@\s]+@[^@\s]+\.[^@\s]+|\+55\d{10,11}|\d{11}|\d{14}|[0-9a-f-]{36})$/i.test(String(s.pixKey))) bad.push({ field: "settings.pixKey", message: "chave Pix inválida" });
      for (const [key, cents] of Object.entries((s.feesCents ?? {}) as Record<string, number>)) {
        if (!(cents >= 0 && cents <= 100000)) bad.push({ field: `settings.feesCents.${key}`, message: "entre 0 e R$ 1.000,00" });
      }
      const reminders: number[] = s.reminderDays ?? [];
      if (new Set(reminders).size !== reminders.length || reminders.length > 8 || reminders.some((d) => d < -30 || d > 60)) bad.push({ field: "settings.reminderDays", message: "valores entre -30 e 60" });
      if (bad.length) return error(400, "VALIDATION_ERROR", "Dados inválidos.", bad);
      const stale = precondition(request, unitDetail(id).version);
      if (stale) return stale;
      const current = unitDetail(id);
      const next = {
        ...current,
        name,
        postalCode: b.postalCode ? String(b.postalCode).replace(/\D/g, "") : null,
        address: b.address || null,
        city: b.city || null,
        // CEP novo sem coordenadas: o simulador da porta devolve um ponto fixo.
        latitude: b.latitude ?? (b.postalCode && b.postalCode !== current.postalCode ? -23.5505 : null),
        longitude: b.longitude ?? (b.postalCode && b.postalCode !== current.postalCode ? -46.6333 : null),
        phone: b.phone || null,
        email: b.email || null,
        whatsapp: b.whatsapp || null,
        cnpj: b.cnpj ? String(b.cnpj).replace(/\D/g, "") : null,
        settings: {
          ...current.settings,
          ...s,
          dueDays: [...days].sort((x, y) => x - y),
          enrollmentFeeCents: s.enrollmentFeeEnabled ? s.enrollmentFeeCents : 0,
          pixKey: s.pixKey || null,
          reminderDays: [...reminders].sort((x, y) => x - y),
          paymentAccount: current.settings.paymentAccount,
        },
      };
      const { version, lastChange, ...rest } = next;
      const { version: oldVersion, lastChange: oldLast, ...old } = current;
      void oldVersion;
      void oldLast;
      void lastChange;
      const saved = JSON.stringify(rest) !== JSON.stringify(old) ? { ...next, version: version + 1, lastChange: changedNow() } : current;
      unitDetails[id] = saved;
      Object.assign(units.find((u) => u.id === id)!, { name, city: saved.city });
      return HttpResponse.json(saved);
    },
  },
  {
    // RF-PRO-01: Coordenador e Administrador; Professor e portal 403. Sem `status`, Ativos e Inativos; filtros combinam; size 25/50/100.
    method: "get",
    path: "/teachers",
    resolver: ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
      const p = new URL(request.url).searchParams;
      const bad: { field: string; message: string }[] = [];
      const status = p.get("status");
      const page = Number(p.get("page") ?? 1);
      const size = Number(p.get("size") ?? 25);
      if (status !== null && !["ACTIVE", "INACTIVE"].includes(status)) bad.push({ field: "status", message: "valor inválido" });
      if (!Number.isInteger(page) || page < 1) bad.push({ field: "page", message: "deve ser 1 ou maior" });
      if (![25, 50, 100].includes(size)) bad.push({ field: "size", message: "deve ser 25, 50 ou 100" });
      if (bad.length) return error(400, "VALIDATION_ERROR", "Dados inválidos.", bad);
      const q = teacherKey((p.get("q") ?? "").trim());
      const modalityId = p.get("modalityId");
      const unitId = p.get("unitId");
      const found = mockTeachers
        .filter((t) => role === "COORDINATOR" || t.units.some((u) => u.id === ADMIN_UNIT))
        .filter((t) => teacherKey(t.name).includes(q) && (!status || t.status === status))
        .filter((t) => !modalityId || t.modalities.some((m) => m.id === modalityId))
        .filter((t) => !unitId || (role === "COORDINATOR" || unitId === ADMIN_UNIT) && t.units.some((u) => u.id === unitId))
        .sort((a, b) => a.name.localeCompare(b.name) * (p.get("sort") === "name,desc" ? -1 : 1));
      return HttpResponse.json({ items: found.slice((page - 1) * size, page * size), page, size, total: found.length });
    },
  },
  {
    // RF-TUR-01: Coordenador e Administrador (só as turmas da unidade dele); Professor e portal 403. Sem `status`, Ativas e Desativadas; filtros combinam.
    method: "get",
    path: "/classes",
    resolver: ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
      const p = new URL(request.url).searchParams;
      const bad: { field: string; message: string }[] = [];
      const status = p.get("status");
      const weekday = p.get("weekday");
      const page = Number(p.get("page") ?? 1);
      const size = Number(p.get("size") ?? 25);
      if (status !== null && !["ACTIVE", "INACTIVE"].includes(status)) bad.push({ field: "status", message: "valor inválido" });
      if (weekday !== null && !(Number.isInteger(Number(weekday)) && Number(weekday) >= 1 && Number(weekday) <= 7)) bad.push({ field: "weekday", message: "deve ser de 1 a 7" });
      if (!Number.isInteger(page) || page < 1) bad.push({ field: "page", message: "deve ser 1 ou maior" });
      if (![25, 50, 100].includes(size)) bad.push({ field: "size", message: "deve ser 25, 50 ou 100" });
      if (bad.length) return error(400, "VALIDATION_ERROR", "Dados inválidos.", bad);
      const q = teacherKey((p.get("q") ?? "").trim());
      const modalityId = p.get("modalityId");
      const teacherId = p.get("teacherId");
      const ageRangeId = p.get("ageRangeId");
      const unitId = p.get("unitId");
      const found = mockClasses
        .filter((c) => role === "COORDINATOR" || c.unitId === ADMIN_UNIT)
        .map((c) => classRow(c))
        .filter((c) => teacherKey(c.name).includes(q) && (!status || c.status === status))
        .filter((c) => !modalityId || c.modality.id === modalityId)
        .filter((c) => !teacherId || c.head.id === teacherId || c.assistants.some((a) => a.id === teacherId))
        .filter((c) => !ageRangeId || c.ageRange?.id === ageRangeId)
        .filter((c) => !weekday || c.schedule.some((s) => s.weekday === Number(weekday)))
        .filter((c) => !unitId || c.unit.id === unitId)
        .sort((a, b) => a.name.localeCompare(b.name) * (p.get("sort") === "name,desc" ? -1 : 1));
      return HttpResponse.json({ items: found.slice((page - 1) * size, page * size), page, size, total: found.length });
    },
  },
  {
    // RF-PLA-02: tabela de planos paginada; Coordenador (todas as unidades) e Administrador (só a dele); demais 403.
    method: "get",
    path: "/plans",
    resolver: ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
      const p = new URL(request.url).searchParams;
      const status = p.get("status");
      const page = Number(p.get("page") ?? 1);
      const size = Number(p.get("size") ?? 25);
      const bad: { field: string; message: string }[] = [];
      if (status !== null && !["ACTIVE", "INACTIVE"].includes(status)) bad.push({ field: "status", message: "valor inválido" });
      if (!Number.isInteger(page) || page < 1) bad.push({ field: "page", message: "deve ser 1 ou maior" });
      if (![25, 50, 100].includes(size)) bad.push({ field: "size", message: "deve ser 25, 50 ou 100" });
      if (bad.length) return error(400, "VALIDATION_ERROR", "Dados inválidos.", bad);
      const q = teacherKey((p.get("q") ?? "").trim());
      const modalityId = p.get("modalityId");
      const unitId = p.get("unitId");
      const found = mockPlans
        .filter((x) => role === "COORDINATOR" || x.unitId === ADMIN_UNIT)
        .map(planRow)
        .filter((x) => teacherKey(x.name).includes(q) && (!status || x.status === status))
        .filter((x) => !modalityId || x.modality.id === modalityId)
        .filter((x) => !unitId || x.unit.id === unitId)
        .sort((a, b) => a.modality.name.localeCompare(b.modality.name) || a.name.localeCompare(b.name));
      return HttpResponse.json({ items: found.slice((page - 1) * size, page * size), page, size, total: found.length });
    },
  },
  {
    // RF-PLA-01: resumo de planos e benefícios; Coordenador (todas as unidades ou a filtrada) e Administrador (só a dele; fora do alcance, zerado); demais 403.
    method: "get",
    path: "/plans/summary",
    resolver: ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
      const unitId = new URL(request.url).searchParams.get("unitId");
      const active = mockPlans
        .filter((p) => role === "COORDINATOR" || p.unitId === ADMIN_UNIT)
        .filter((p) => !unitId || p.unitId === unitId);
      const values = active.filter((p) => p.status === "ACTIVE").map((p) => p.valueCents);
      return HttpResponse.json({
        plans: {
          active: values.length,
          inactive: active.length - values.length,
          studentsUsing: 0,
          minValueCents: values.length ? Math.min(...values) : null,
          maxValueCents: values.length ? Math.max(...values) : null,
        },
        benefits: { active: 0, high: 0 },
      });
    },
  },
  {
    // RF-PLA-03: opções da janela; unidades ativas do alcance com as modalidades ativas de cada uma e a configuração "Treinar em outra unidade".
    method: "get",
    path: "/plans/options",
    resolver: () => {
      const denied = planAccess();
      if (denied) return denied;
      const role = activeRole();
      return HttpResponse.json({
        crossUnitTraining: "plan",
        units: units
          .filter((u) => u.active && (role === "COORDINATOR" || u.id === ADMIN_UNIT))
          .map((u) => ({ id: u.id, name: u.name, modalities: modalityCatalog.slice(0, 5) })),
      });
    },
  },
  {
    // RF-PLA-03: cria plano ACTIVE; o Administrador só na unidade dele (403); homônimo ativo 409.
    method: "post",
    path: "/plans",
    resolver: async ({ request }) => {
      const denied = planAccess();
      if (denied) return denied;
      const b = (await request.json().catch(() => ({}))) as PlanBody;
      const fields = planFields(b);
      if (fields instanceof Response) return fields;
      if (!b.unitId) return planBad("unitId", "obrigatório");
      if (activeRole() === "ADMINISTRATOR" && b.unitId !== ADMIN_UNIT) return error(403, "FORBIDDEN", "Sem permissão.");
      const unit = units.find((u) => u.id === b.unitId && u.active);
      if (!unit) return planBad("unitId", "escolha uma unidade ativa da organização");
      const modalityIndex = modalityCatalog.findIndex((m) => m.id === b.modalityId);
      if (modalityIndex < 0 || modalityIndex > 4) return planBad("modalityId", "escolha uma modalidade ativa oferecida pela unidade");
      if (planTaken(unit.id, modalityIndex, fields.name)) return planConflict();
      const created: MockPlan = { id: crypto.randomUUID(), unitId: unit.id, unitName: unit.name, modalityIndex, status: "ACTIVE", ...fields };
      mockPlans = [...mockPlans, created];
      return HttpResponse.json(planRow(created), { status: 201 });
    },
  },
  {
    // RF-PLA-03: troca nome, valor, frequência e "vale em outras unidades"; unidade e modalidade não mudam (400); desativado 409.
    method: "put",
    path: "/plans/{id}",
    resolver: async ({ request, params }) => {
      const denied = planAccess();
      if (denied) return denied;
      const found = planFor(params.id as string);
      if (found instanceof Response) return found;
      const b = (await request.json().catch(() => ({}))) as PlanBody;
      const fields = planFields(b);
      if (fields instanceof Response) return fields;
      if (b.unitId && b.unitId !== found.unitId) return planBad("unitId", "a unidade não muda");
      if (b.modalityId && b.modalityId !== modalityCatalog[found.modalityIndex].id) return planBad("modalityId", "a modalidade não muda");
      if (found.status === "INACTIVE") return error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.");
      if (planTaken(found.unitId, found.modalityIndex, fields.name, found.id)) return planConflict();
      Object.assign(found, fields);
      return HttpResponse.json(planRow(found));
    },
  },
  {
    // RN-PLA-04: impacto de mudar o valor; sem aluno nem cobrança ainda, 0 alunos e a partir da próxima cobrança gerada.
    method: "get",
    path: "/plans/{id}/value-impact",
    resolver: ({ request, params }) => {
      const denied = planAccess();
      if (denied) return denied;
      const found = planFor(params.id as string);
      if (found instanceof Response) return found;
      const raw = new URL(request.url).searchParams.get("valueCents");
      if (!raw || !validCents(Number(raw))) return planBad("valueCents", "inteiro de 1 a 99999999 centavos");
      return HttpResponse.json({ studentsAffected: 0, effectiveFrom: "NEXT_CHARGE" });
    },
  },
  {
    // RN-PLA-06: desativar e ativar são idempotentes; reativar com homônimo ativo 409; nada é apagado.
    method: "post",
    path: "/plans/{id}/deactivate",
    resolver: ({ params }) => {
      const denied = planAccess();
      if (denied) return denied;
      const found = planFor(params.id as string);
      if (found instanceof Response) return found;
      found.status = "INACTIVE";
      return HttpResponse.json(planRow(found));
    },
  },
  {
    method: "post",
    path: "/plans/{id}/activate",
    resolver: ({ params }) => {
      const denied = planAccess();
      if (denied) return denied;
      const found = planFor(params.id as string);
      if (found instanceof Response) return found;
      if (found.status === "INACTIVE" && planTaken(found.unitId, found.modalityIndex, found.name, found.id)) return planConflict();
      found.status = "ACTIVE";
      return HttpResponse.json(planRow(found));
    },
  },
  {
    // RF-TUR-03 (D8): opções do formulário; unidades ativas do alcance, com modalidades e professores ativos de cada uma.
    method: "get",
    path: "/classes/options",
    resolver: () => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
      return HttpResponse.json({
        units: units
          .filter((u) => u.active && (role === "COORDINATOR" || u.id === ADMIN_UNIT))
          .map((u) => ({
            id: u.id,
            name: u.name,
            modalities: teacherOptions().map(({ id, name }) => ({ id, name })),
            teachers: mockTeachers.filter((t) => t.status === "ACTIVE" && t.units.some((x) => x.id === u.id)).map(({ id, name, modalities }) => ({ id, name, modalityIds: modalities.map((m) => m.id) })),
          })),
        ageRanges: ageRanges.map(({ id, name, minAge, maxAge }) => ({ id, name, minAge, maxAge })),
      });
    },
  },
  {
    // RF-TUR-03: cria turma ACTIVE; o Administrador só na unidade dele (403).
    method: "post",
    path: "/classes",
    resolver: async ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
      const body = (await request.json().catch(() => ({}))) as ClassInput;
      if (!body.unitId) return classBad("unitId", "obrigatório");
      if (role === "ADMINISTRATOR" && body.unitId !== ADMIN_UNIT) return error(403, "FORBIDDEN", "Sem permissão.");
      if (!units.some((u) => u.id === body.unitId && u.active)) return classBad("unitId");
      const made = classFromInput(body, body.unitId, null);
      if (made instanceof Response) return made;
      const created: MockClass = { id: crypto.randomUUID(), status: "ACTIVE", ...made };
      mockClasses = [...mockClasses, created];
      return HttpResponse.json(classDetail(created), { status: 201 });
    },
  },
  {
    // RF-TUR-03: troca os campos; a unidade não muda; desativada 409; scheduleChanged quando o conjunto de horários muda.
    method: "put",
    path: "/classes/{id}",
    resolver: async ({ request, params }) => {
      const found = classFor(params.id as string);
      if (found instanceof HttpResponse) return found;
      const body = (await request.json().catch(() => ({}))) as ClassInput;
      if (found.status === "INACTIVE") return error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.");
      const made = classFromInput(body, found.unitId, found);
      if (made instanceof Response) return made;
      const changed = JSON.stringify(found.schedule) !== JSON.stringify(made.schedule);
      Object.assign(found, made);
      return HttpResponse.json({ ...classDetail(found), scheduleChanged: changed });
    },
  },
  {
    method: "get",
    path: "/classes/{id}",
    resolver: ({ params }) => {
      const found = classFor(params.id as string);
      return found instanceof HttpResponse ? found : HttpResponse.json(classDetail(found));
    },
  },
  {
    // RF-AGE-01: Coordenador (todas as unidades), Administrador e Professor (a unidade dele); portal 403. As aulas saem das turmas ativas pelos
    // horários semanais, como a tarefa diária do servidor; hoje é dia sem aula (as aulas de hoje nascem canceladas) e a de quarta do Jiu-Jitsu é substituição.
    method: "get",
    path: "/agenda",
    resolver: ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "COORDINATOR" && role !== "ADMINISTRATOR" && role !== "TEACHER") return error(403, "FORBIDDEN", "Sem permissão.");
      const p = new URL(request.url).searchParams;
      const from = p.get("from");
      const to = p.get("to");
      const isDate = (v: string | null): v is string => !!v && /^\d{4}-\d{2}-\d{2}$/.test(v) && !Number.isNaN(Date.parse(`${v}T12:00:00Z`));
      if (!isDate(from)) return classBad("from", "data inválida, use yyyy-MM-dd");
      if (!isDate(to)) return classBad("to", "data inválida, use yyyy-MM-dd");
      const day = 86400000;
      const span = (Date.parse(`${to}T12:00:00Z`) - Date.parse(`${from}T12:00:00Z`)) / day;
      if (span < 0) return classBad("to", "não pode ser anterior a from");
      if (span + 1 > 31) return classBad("to", "período de no máximo 31 dias");
      const reach = units.filter((u) => u.active && (role === "COORDINATOR" || u.id === ADMIN_UNIT));
      const sorted = [...reach].sort((a, b) => a.name.localeCompare(b.name));
      const unitId = p.get("unitId");
      const unit = unitId ? sorted.find((u) => u.id === unitId) : sorted[0];
      if (!unit) return error(404, "NOT_FOUND", "Não encontrado.");
      const now = new Date();
      const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
      const teacherId = p.get("teacherId");
      const modalityId = p.get("modalityId");
      const lessons: unknown[] = [];
      for (let t = Date.parse(`${from}T12:00:00Z`); t <= Date.parse(`${to}T12:00:00Z`); t += day) {
        const date = new Date(t).toISOString().slice(0, 10);
        const weekday = ((new Date(t).getUTCDay() + 6) % 7) + 1;
        for (const c of mockClasses.filter((k) => k.status === "ACTIVE" && k.unitId === unit.id)) {
          for (const [wd, startsAt, endsAt] of c.schedule.filter(([d]) => d === weekday)) {
            const lesson = applyEdit(mockLesson(c, unit, date, wd, startsAt, endsAt, today));
            if ((teacherId && lesson.teacher.id !== teacherId) || (modalityId && lesson.modality.id !== modalityId)) continue;
            lessons.push(lesson);
          }
        }
      }
      for (const a of mockAdhoc.filter((x) => x.unitId === unit.id && x.date >= from && x.date <= to)) {
        const lesson = applyEdit(adhocLesson(a, today));
        if ((teacherId && lesson.teacher.id !== teacherId) || (modalityId && lesson.modality.id !== modalityId)) continue;
        lessons.push(lesson);
      }
      const noClassDays = [
        ...(today >= from && today <= to ? [{ startsOn: today, endsOn: today, reason: "Feriado sintético" }] : []),
        ...mockNoClassDays.filter((d) => d.unitId === unit.id && d.classIds.length === 0 && d.startsOn <= to && d.endsOn >= from).map((d) => ({ startsOn: d.startsOn, endsOn: d.endsOn, reason: d.reason })),
      ];
      return HttpResponse.json({
        units: sorted.map(({ id, name }) => ({ id, name })),
        unit: { id: unit.id, name: unit.name },
        teachers: mockTeachers.filter((t) => t.status === "ACTIVE" && t.units.some((x) => x.id === unit.id)).map(({ id, name }) => ({ id, name })),
        modalities: teacherOptions().map(({ id, name }) => ({ id, name })),
        lessons: lessons.sort((a, b) => `${(a as { date: string; startsAt: string }).date}${(a as { startsAt: string }).startsAt}`.localeCompare(`${(b as { date: string }).date}${(b as { startsAt: string }).startsAt}`)),
        noClassDays,
      });
    },
  },
  {
    // RF-AGE-02: painel da aula; o id é o da lista (turma, data e início). Administrador só da unidade dele (404); Professor sem acesso a turmas.
    method: "get",
    path: "/agenda/lessons/{id}",
    resolver: ({ params }) => {
      const found = lessonView(params.id as string);
      return found instanceof Response ? found : HttpResponse.json(found.detail);
    },
  },
  {
    // RF-AGE-04: cria aula avulsa (sem turma); validação como no servidor. Só Coordenador e Administrador (o Professor do mock não edita a agenda).
    method: "post",
    path: "/agenda/lessons",
    resolver: async ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
      const b = (await request.json().catch(() => ({}))) as { unitId?: string; title?: string; modalityId?: string; date?: string; startsAt?: string; endsAt?: string; teacherId?: string; visibilityClassId?: string | null };
      const unit = units.find((u) => u.id === b.unitId && u.active && (role === "COORDINATOR" || u.id === ADMIN_UNIT));
      if (!unit) return error(404, "NOT_FOUND", "Não encontrado.");
      const title = (b.title ?? "").trim();
      if (title.length < 1 || title.length > 80) return classBad("title", "de 1 a 80 caracteres");
      if (!b.modalityId) return classBad("modalityId", "obrigatório");
      if (!b.teacherId) return classBad("teacherId", "obrigatório");
      if (!b.date || !/^\d{4}-\d{2}-\d{2}$/.test(b.date) || Number.isNaN(Date.parse(`${b.date}T12:00:00Z`))) return classBad("date", "data inválida, use yyyy-MM-dd");
      const time = /^([01]\d|2[0-3]):[0-5]\d$/;
      if (!b.startsAt || !time.test(b.startsAt)) return classBad("startsAt", "horário HH:mm");
      if (!b.endsAt || !time.test(b.endsAt) || b.endsAt <= b.startsAt) return classBad("endsAt", "o término precisa ser depois do início");
      if (b.date < todayLocal()) return classBad("date", "o início não pode ficar no passado");
      const unprocessable = (field: string, message: string) => error(422, "UNPROCESSABLE", "Os dados estão corretos, mas a regra não permite esta operação.", [{ field, message }]);
      if (!teacherOptions().some((m) => m.id === b.modalityId)) return unprocessable("modalityId", "escolha uma modalidade ativa oferecida pela unidade");
      const teacher = mockTeachers.find((t) => t.id === b.teacherId && t.status === "ACTIVE" && t.modalities.some((m) => m.id === b.modalityId));
      if (!teacher) return unprocessable("teacherId", "escolha um professor ativo que ensine a modalidade da aula");
      if (b.visibilityClassId) {
        const k = mockClasses.find((c) => c.id === b.visibilityClassId);
        if (!k) return error(404, "NOT_FOUND", "Não encontrado.");
        if (k.status !== "ACTIVE" || k.unitId !== unit.id) return unprocessable("visibilityClassId", "escolha uma turma ativa da unidade");
      }
      const made: MockAdhoc = { id: crypto.randomUUID(), unitId: unit.id, title, modalityId: b.modalityId, date: b.date, startsAt: b.startsAt, endsAt: b.endsAt, teacherId: teacher.id, visibilityClassId: b.visibilityClassId ?? null };
      mockAdhoc = [...mockAdhoc, made];
      return HttpResponse.json((lessonView(made.id) as { detail: object }).detail, { status: 201 });
    },
  },
  {
    // RF-AGE-03: edita a aula (as seguintes de FOLLOWING são geradas pelo mock e não mudam). Concluída: só o professor, com motivo.
    method: "put",
    path: "/agenda/lessons/{id}",
    resolver: async ({ request, params }) => {
      const found = lessonView(params.id as string);
      if (found instanceof Response) return found;
      const { detail } = found;
      if (!detail.canEdit) return detail.status === "CANCELLED" || detail.status === "COMPLETED" ? error(409, "CONFLICT", "Não é possível editar a aula.") : error(403, "FORBIDDEN", "Sem permissão.");
      const b = (await request.json().catch(() => ({}))) as { scope?: string; date?: string; startsAt?: string; endsAt?: string; teacherId?: string; reason?: string };
      if (!found.klass && b.scope === "FOLLOWING") return classBad("scope", "aula avulsa: use THIS");
      const time = /^([01]\d|2[0-3]):[0-5]\d$/;
      if (b.scope !== "THIS" && b.scope !== "FOLLOWING") return classBad("scope", "use THIS ou FOLLOWING");
      if (!b.date || !/^\d{4}-\d{2}-\d{2}$/.test(b.date)) return classBad("date", "data inválida, use yyyy-MM-dd");
      if (!b.startsAt || !time.test(b.startsAt)) return classBad("startsAt", "horário HH:mm");
      if (!b.endsAt || !time.test(b.endsAt) || b.endsAt <= b.startsAt) return classBad("endsAt", "o término precisa ser depois do início");
      if (!b.teacherId) return classBad("teacherId", "obrigatório");
      if (detail.status === "COMPLETED") {
        if (b.date !== detail.date || b.startsAt !== detail.startsAt || b.endsAt !== detail.endsAt) return classBad("date", "aula concluída: só o professor pode ser corrigido");
        if (!b.reason?.trim()) return classBad("reason", "obrigatório para aula concluída");
      } else if (b.date < found.today) return classBad("date", "o novo início não pode ficar no passado");
      const teacher = mockTeachers.find((x) => x.id === b.teacherId && x.status === "ACTIVE");
      if (!teacher) return error(422, "UNPROCESSABLE", "Os dados estão corretos, mas a regra não permite esta operação.", [{ field: "teacherId", message: "escolha um professor ativo que ensine a modalidade da turma" }]);
      lessonEdits.set(detail.id, { ...lessonEdits.get(detail.id), date: b.date, startsAt: b.startsAt, endsAt: b.endsAt, teacherId: teacher.id, substitute: found.klass ? teacher.id !== classRow(found.klass).head.id : false });
      return HttpResponse.json((lessonView(detail.id) as { detail: object }).detail);
    },
  },
  {
    // RF-AGE-03: cancela a aula; o portal é sempre canal (o mock não tem experimentais, então não pede a decisão).
    method: "post",
    path: "/agenda/lessons/{id}/cancel",
    resolver: async ({ request, params }) => {
      const found = lessonView(params.id as string);
      if (found instanceof Response) return found;
      const { detail } = found;
      if (!detail.canCancel) return detail.status === "SCHEDULED" || detail.status === "IN_PROGRESS" ? error(403, "FORBIDDEN", "Sem permissão.") : error(409, "CONFLICT", "Não é possível cancelar a aula.");
      const b = (await request.json().catch(() => ({}))) as { reason?: string; reasonText?: string; channels?: string[]; trialDecision?: string };
      if (!b.reason || !["TEACHER_UNAVAILABLE", "HOLIDAY", "ACADEMY_EVENT", "OTHER"].includes(b.reason)) return classBad("reason", "motivo inválido");
      const text = b.reason === "OTHER" ? (b.reasonText ?? "").trim() : null;
      if (text !== null && (text.length < 1 || text.length > 200)) return classBad("reasonText", "de 1 a 200 caracteres");
      if ((b.channels ?? []).some((c) => c !== "EMAIL" && c !== "WHATSAPP")) return classBad("channels", "use EMAIL ou WHATSAPP");
      lessonEdits.set(detail.id, { ...lessonEdits.get(detail.id), cancelCode: b.reason, cancelText: text });
      return HttpResponse.json((lessonView(detail.id) as { detail: object }).detail);
    },
  },
  {
    // RF-AGE-05: dias da unidade que ainda não terminaram e feriados nacionais sugeridos (ainda não confirmados na unidade); nada é gravado.
    method: "get",
    path: "/no-class-days",
    resolver: ({ request }) => {
      const denied = dayAccess();
      if (denied) return denied;
      const reach = [...dayReach()].sort((a, b) => a.name.localeCompare(b.name));
      const unitId = new URL(request.url).searchParams.get("unitId");
      const unit = unitId ? reach.find((u) => u.id === unitId) : reach[0];
      if (unitId && !unit) return error(404, "NOT_FOUND", "Não encontrado.");
      const refs = reach.map(({ id, name }) => ({ id, name }));
      if (!unit) return HttpResponse.json({ units: refs, unit: null, days: [], suggestions: [] });
      const today = todayLocal();
      const mine = mockNoClassDays.filter((d) => d.unitId === unit.id && d.endsOn >= today).sort((a, b) => a.startsOn.localeCompare(b.startsOn));
      const taken = new Set(mine.map((d) => d.startsOn));
      return HttpResponse.json({
        units: refs,
        unit: { id: unit.id, name: unit.name },
        days: mine.map((d) => dayItem(d, today)),
        suggestions: nextHolidays(today).filter((h) => !taken.has(h.date)),
      });
    },
  },
  {
    // RF-AGE-05: prévia das aulas que seriam canceladas, por unidade; mesmas validações do lançamento, menos o conflito.
    method: "get",
    path: "/no-class-days/preview",
    resolver: ({ request }) => {
      const denied = dayAccess();
      if (denied) return denied;
      const p = new URL(request.url).searchParams;
      const body: DayBody = { startsOn: p.get("startsOn") ?? undefined, endsOn: p.get("endsOn") ?? undefined, classIds: p.getAll("classIds") };
      const plan = dayPlan(body, p.getAll("unitIds"), false);
      if (plan instanceof Response) return plan;
      return HttpResponse.json({
        units: plan.units.map((id) => ({ unit: { id, name: units.find((u) => u.id === id)!.name }, lessons: dayLessons(id, body.startsOn!, body.endsOn!, plan.classIds, plan.today) })),
      });
    },
  },
  {
    // RF-AGE-05: um dia por unidade, tudo ou nada; 409 se sobrepõe outro da unidade. Origem: feriado, Coordenador ou unidade.
    method: "post",
    path: "/no-class-days",
    resolver: async ({ request }) => {
      const denied = dayAccess();
      if (denied) return denied;
      const b = (await request.json().catch(() => ({}))) as DayBody;
      const plan = dayPlan(b, b.unitIds ?? [], true);
      if (plan instanceof Response) return plan;
      if (plan.units.some((id) => dayClash(id, b.startsOn!, b.endsOn!))) return dayConflict();
      const mode = b.notice?.mode ?? "NONE";
      const made: MockDay[] = plan.units.map((unitId) => ({
        id: crypto.randomUUID(),
        unitId,
        startsOn: b.startsOn!,
        endsOn: b.endsOn!,
        reason: (b.reason ?? "").trim(),
        origin: b.holiday ? "HOLIDAY" : activeRole() === "COORDINATOR" ? "COORDINATOR" : "UNIT",
        classIds: plan.classIds,
        noticeMode: mode,
        noticeAt: mode === "SCHEDULED" ? new Date(b.notice!.sendAt!).toISOString() : null,
      }));
      mockNoClassDays = [...mockNoClassDays, ...made];
      return HttpResponse.json(made.map((d) => dayItem(d, plan.today)), { status: 201 });
    },
  },
  {
    // RF-AGE-05: só dia futuro (início depois de hoje, senão 409); troca período, motivo, turmas e aviso; a unidade e a origem não mudam.
    method: "put",
    path: "/no-class-days/{id}",
    resolver: async ({ request, params }) => {
      const denied = dayAccess();
      if (denied) return denied;
      const found = mockNoClassDays.find((d) => d.id === params.id && dayReach().some((u) => u.id === d.unitId));
      if (!found) return error(404, "NOT_FOUND", "Não encontrado.");
      if (found.startsOn <= todayLocal()) return error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.");
      const b = (await request.json().catch(() => ({}))) as DayBody;
      const plan = dayPlan(b, [found.unitId], true);
      if (plan instanceof Response) return plan;
      if (dayClash(found.unitId, b.startsOn!, b.endsOn!, found.id)) return dayConflict();
      const mode = b.notice?.mode ?? "NONE";
      Object.assign(found, {
        startsOn: b.startsOn!,
        endsOn: b.endsOn!,
        reason: (b.reason ?? "").trim(),
        classIds: plan.classIds,
        noticeMode: mode,
        noticeAt: mode === "SCHEDULED" ? new Date(b.notice!.sendAt!).toISOString() : null,
      });
      return HttpResponse.json(dayItem(found, plan.today));
    },
  },
  {
    // RF-AGE-05: desfaz um dia futuro; `notify` só fica registrado até a Sprint 09. As aulas voltam porque o simulado as deriva do dia.
    method: "delete",
    path: "/no-class-days/{id}",
    resolver: ({ params }) => {
      const denied = dayAccess();
      if (denied) return denied;
      const found = mockNoClassDays.find((d) => d.id === params.id && dayReach().some((u) => u.id === d.unitId));
      if (!found) return error(404, "NOT_FOUND", "Não encontrado.");
      if (found.startsOn <= todayLocal()) return error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.");
      mockNoClassDays = mockNoClassDays.filter((d) => d.id !== found.id);
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    // RN-TUR-03: com aluno principal 409 HAS_PRIMARY_STUDENTS (contagem em fields); desativada 409; nada é apagado.
    method: "post",
    path: "/classes/{id}/deactivate",
    resolver: ({ params }) => {
      const found = classFor(params.id as string);
      if (found instanceof HttpResponse) return found;
      if (found.status === "INACTIVE") return error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.");
      if (found.blocking) {
        return error(409, "HAS_PRIMARY_STUDENTS", "A turma não pode ser desativada enquanto houver alunos com ela como turma principal.", [
          { field: "primaryStudents", message: String(found.blocking) },
        ]);
      }
      found.status = "INACTIVE";
      return HttpResponse.json(classDetail(found));
    },
  },
  {
    // RF-PRO-03 (D7): opções do formulário; só Coordenador e Administrador.
    method: "get",
    path: "/teachers/options",
    resolver: () => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
      return HttpResponse.json({ modalities: teacherOptions() });
    },
  },
  {
    // RF-PRO-03: cadastro; nasce ACTIVE, sem conta.
    method: "post",
    path: "/teachers",
    resolver: async ({ request }) => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
      const made = teacherFromInput((await request.json().catch(() => ({}))) as TeacherInput, null);
      if (made instanceof Response) return made;
      const { contact, ...fields } = made;
      const created: MockTeacher = { id: crypto.randomUUID(), photoUrl: null, classCount: 0, status: "ACTIVE", access: "NONE", ...fields };
      mockTeachers = [...mockTeachers, created];
      teacherContacts[created.id] = contact;
      return HttpResponse.json(teacherDetail(created), { status: 201 });
    },
  },
  {
    // RF-PRO-03: troca o cadastro inteiro; situação e acesso não mudam.
    method: "put",
    path: "/teachers/{id}",
    resolver: async ({ request, params }) => {
      const found = teacherFor(params.id as string);
      if (found instanceof HttpResponse) return found;
      const made = teacherFromInput((await request.json().catch(() => ({}))) as TeacherInput, found);
      if (made instanceof Response) return made;
      // RN-PRO-09: tirar uma modalidade em que é titular ou auxiliar de turma ativa é recusado, listando as turmas.
      const kept = new Set(made.modalities.map((m) => m.id));
      const blocking = mockClasses
        .filter((c) => c.status === "ACTIVE" && (c.head === found.id || c.assistants.includes(found.id)) && !kept.has(c.modalityId))
        .map((c) => c.name)
        .sort((a, b) => a.localeCompare(b));
      if (blocking.length > 0) return error(409, "TEACHER_MODALITY_IN_USE", "Há turmas ativas.", blocking.map((name) => ({ field: "class", message: name })));
      const { contact, ...fields } = made;
      Object.assign(found, fields);
      teacherContacts[found.id] = contact;
      return HttpResponse.json(teacherDetail(found));
    },
  },
  {
    // O corpo multipart é lido como texto (como na logo); o mock só guarda que existe uma foto.
    method: "put",
    path: "/teachers/{id}/photo",
    resolver: async ({ request, params }) => {
      const found = teacherFor(params.id as string);
      if (found instanceof HttpResponse) return found;
      const raw = await request.text();
      if (!raw.includes('name="file"') || raw.length < 20 || raw.length > 5 * 1024 * 1024) return teacherBad("file", "arquivo inválido");
      found.photoUrl = MOCK_LOGO;
      return HttpResponse.json(teacherDetail(found));
    },
  },
  {
    method: "delete",
    path: "/teachers/{id}/photo",
    resolver: ({ params }) => {
      const found = teacherFor(params.id as string);
      if (found instanceof HttpResponse) return found;
      found.photoUrl = null;
      return HttpResponse.json(teacherDetail(found));
    },
  },
  {
    // RF-PRO-02: painel; Administrador só abre professor com a unidade dele (senão 404); contato só aqui.
    method: "get",
    path: "/teachers/{id}",
    resolver: ({ params }) => {
      const found = teacherFor(params.id as string);
      if (found instanceof HttpResponse) return found;
      return HttpResponse.json(teacherDetail(found));
    },
  },
  {
    // RN-PRO-02: dá acesso (convite + vínculo pendente). Sem unitIds, usa as unidades do professor ao alcance de quem chama.
    method: "post",
    path: "/teachers/{id}/access",
    resolver: async ({ request, params }) => {
      const found = teacherFor(params.id as string);
      if (found instanceof HttpResponse) return found;
      const body = (await request.json().catch(() => ({}))) as { unitIds?: string[] };
      const mine = found.units.map((u) => u.id).filter((id) => activeRole() === "COORDINATOR" || id === ADMIN_UNIT);
      const asked = body.unitIds ?? [];
      if (activeRole() === "ADMINISTRATOR" && asked.some((id) => id !== ADMIN_UNIT)) return error(403, "FORBIDDEN", "Sem permissão.");
      if (asked.some((id) => !found.units.some((u) => u.id === id))) {
        return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "unitIds", message: "escolha unidades ativas do professor" }]);
      }
      if (!teacherContacts[found.id].email) return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "email", message: "o professor não tem e-mail cadastrado" }]);
      if (found.status === "INACTIVE" || found.access === "PENDING" || found.access === "ACTIVE") return error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.");
      if ((asked.length ? asked : mine).length === 0) return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "unitIds", message: "escolha ao menos uma unidade" }]);
      found.access = "PENDING";
      return HttpResponse.json(teacherDetail(found));
    },
  },
  {
    // RN-PRO-02: só com acesso PENDING (senão 409); no máximo 3 reenvios por dia (429).
    method: "post",
    path: "/teachers/{id}/access/resend",
    resolver: ({ params }) => {
      const found = teacherFor(params.id as string);
      if (found instanceof HttpResponse) return found;
      if (found.access !== "PENDING") return error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.");
      if (teacherResends >= 3) return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      teacherResends += 1;
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    // RF-PRO-04: pré-visualização (turmas sem titular: vazia até a Sprint 05). Administrador só inativa quem tem só a unidade dele (D3).
    method: "get",
    path: "/teachers/{id}/deactivation",
    resolver: ({ params }) => {
      const found = teacherFor(params.id as string);
      if (found instanceof HttpResponse) return found;
      return HttpResponse.json({ headClasses: [], canDeactivate: found.status === "ACTIVE" && teacherInReach(found) });
    },
  },
  {
    // RN-PRO-05: inativa e encerra o vínculo (access ENDED se havia conta); já inativo 409; fora do alcance 403.
    method: "post",
    path: "/teachers/{id}/deactivate",
    resolver: ({ params }) => {
      const found = teacherFor(params.id as string);
      if (found instanceof HttpResponse) return found;
      if (!teacherInReach(found)) return error(403, "FORBIDDEN", "Sem permissão.");
      if (found.status === "INACTIVE") return error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.");
      found.status = "INACTIVE";
      if (found.access === "PENDING" || found.access === "ACTIVE") found.access = "ENDED";
      return HttpResponse.json(teacherDetail(found));
    },
  },
  {
    // RF-ACE-06: Coordenador vê os Administradores; Administrador vê os Professores com a unidade dele. Sem filtro, PENDING e ACTIVE.
    method: "get",
    path: "/users",
    resolver: ({ request }) => {
      const denied = unitSettingsRole();
      if (denied) return denied;
      const status = new URL(request.url).searchParams.get("status");
      if (status !== null && !["PENDING", "ACTIVE", "ENDED"].includes(status)) {
        return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "status", message: "valor inválido" }]);
      }
      return HttpResponse.json(staff.filter((s) => reaches(s) && (status ? s.status === status : s.status !== "ENDED")).map(staffRow));
    },
  },
  {
    // Perfil implícito; e-mail "existente@exemplo.com" já está ligado (409 ALREADY_LINKED); 200 vínculos é o limite (STAFF_LIMIT_REACHED).
    method: "post",
    path: "/users",
    resolver: async ({ request }) => {
      const denied = unitSettingsRole();
      if (denied) return denied;
      const body = (await request.json()) as { email?: string; name?: string; unitIds?: string[] };
      const bad: { field: string; message: string }[] = [];
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(body.email?.trim() ?? "")) bad.push({ field: "email", message: "informe um e-mail válido" });
      if (!body.name?.trim()) bad.push({ field: "name", message: "informe o nome" });
      if (!body.unitIds?.length) bad.push({ field: "unitIds", message: "escolha ao menos uma unidade" });
      else if (body.unitIds.some((id) => !units.some((u) => u.id === id && u.active))) bad.push({ field: "unitIds", message: "escolha unidades ativas da organização" });
      if (bad.length) return error(400, "VALIDATION_ERROR", "Dados inválidos.", bad);
      if (activeRole() === "ADMINISTRATOR" && body.unitIds!.some((id) => id !== ADMIN_UNIT)) return error(403, "FORBIDDEN", "Sem permissão.");
      const email = body.email!.trim();
      if (email === "existente@exemplo.com" || staff.some((s) => s.email === email && s.role === managedRole() && s.status !== "ENDED")) {
        return error(409, "ALREADY_LINKED", "Esta pessoa já está ligada a esta organização neste perfil.");
      }
      const row = { linkId: crypto.randomUUID(), name: body.name!.trim(), email, role: managedRole(), unitIds: body.unitIds!, lastAccessAt: null as string | null, status: "PENDING", self: false, resends: 0 };
      staff = [...staff, row];
      return HttpResponse.json(staffRow(row), { status: 201 });
    },
  },
  {
    // RN-ORG-07 (D7): só o Coordenador; cria ou reativa o próprio vínculo de Administrador e soma as unidades.
    method: "post",
    path: "/users/coordinator-appointment",
    resolver: async ({ request }) => {
      if (!loggedIn) return unauthenticated();
      if (activeRole() !== "COORDINATOR") return error(403, "FORBIDDEN", "Sem permissão.");
      const body = (await request.json()) as { unitIds?: string[] };
      if (!body.unitIds?.length || body.unitIds.some((id) => !units.some((u) => u.id === id && u.active))) {
        return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "unitIds", message: "escolha unidades ativas da organização" }]);
      }
      let own = staff.find((s) => s.self);
      if (!own) {
        own = { linkId: crypto.randomUUID(), name: current.account.name, email: current.account.email, role: "ADMINISTRATOR", unitIds: [], lastAccessAt: null, status: "ACTIVE", self: true, resends: 0 };
        staff = [...staff, own];
      }
      own.status = "ACTIVE";
      own.unitIds = [...new Set([...own.unitIds, ...body.unitIds])];
      return HttpResponse.json(staffRow(own));
    },
  },
  {
    // No máximo 3 reenvios por dia (429); só vínculo PENDING que a pessoa gere, senão 404.
    method: "post",
    path: "/users/{linkId}/resend",
    resolver: ({ params }) => {
      const denied = unitSettingsRole();
      if (denied) return denied;
      const row = staff.find((s) => s.linkId === params.linkId);
      if (!row || row.status !== "PENDING" || !reaches(row) || !manageable(row)) return error(404, "NOT_FOUND", "Não encontrado.");
      if (row.resends >= 3) return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      row.resends += 1;
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    // D5: o Coordenador define a lista; o Administrador mexe só na unidade dele. RN-ORG-07 em 409 LAST_UNIT_ADMINISTRATOR.
    method: "put",
    path: "/users/{linkId}/units",
    resolver: async ({ request, params }) => {
      const denied = unitSettingsRole();
      if (denied) return denied;
      const row = staff.find((s) => s.linkId === params.linkId);
      if (!row || row.status === "ENDED" || !reaches(row)) return error(404, "NOT_FOUND", "Não encontrado.");
      const body = (await request.json()) as { unitIds?: string[] };
      const desired = body.unitIds ?? [];
      if (activeRole() === "ADMINISTRATOR" && desired.some((id) => id !== ADMIN_UNIT)) return error(403, "FORBIDDEN", "Sem permissão.");
      if (desired.length === 0 || desired.some((id) => !units.some((u) => u.id === id && u.active))) {
        return error(400, "VALIDATION_ERROR", "Dados inválidos.", [{ field: "unitIds", message: "escolha unidades ativas da organização" }]);
      }
      const next = activeRole() === "COORDINATOR" ? desired : [...row.unitIds.filter((id) => id !== ADMIN_UNIT), ...desired];
      const left = row.role === "ADMINISTRATOR" && row.status === "ACTIVE" ? orphans(row, row.unitIds.filter((id) => !next.includes(id))) : [];
      if (left.length) return lastAdministrator(left);
      row.unitIds = next;
      return HttpResponse.json(staffRow(row));
    },
  },
  {
    method: "delete",
    path: "/users/{linkId}",
    resolver: ({ params }) => {
      const denied = unitSettingsRole();
      if (denied) return denied;
      const row = staff.find((s) => s.linkId === params.linkId);
      if (!row || row.status === "ENDED" || !reaches(row)) return error(404, "NOT_FOUND", "Não encontrado.");
      if (row.self) return error(409, "CONFLICT", "Ninguém desativa a si mesmo.");
      if (!manageable(row)) return error(403, "FORBIDDEN", "Sem permissão.");
      const left = row.role === "ADMINISTRATOR" && row.status === "ACTIVE" ? orphans(row, row.unitIds) : [];
      if (left.length) return lastAdministrator(left);
      row.status = "ENDED";
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    // RF-ALU-01: lista. Coordenador e Administrador (só a unidade de cadastro dele); Professor e portal 403. Ordem por nome; filtros combinam; size 25/50/100.
    method: "get",
    path: "/students",
    resolver: ({ request }) => {
      const denied = studentAccess();
      if (denied) return denied;
      const role = activeRole();
      const p = new URL(request.url).searchParams;
      const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
      const page = Number(p.get("page") ?? 1);
      const size = Number(p.get("size") ?? 25);
      const bad: { field: string; message: string }[] = [];
      if (!Number.isInteger(page) || page < 1) bad.push({ field: "page", message: "deve ser 1 ou maior" });
      if (![25, 50, 100].includes(size)) bad.push({ field: "size", message: "deve ser 25, 50 ou 100" });
      if (p.get("sort") !== null && p.get("sort") !== "name") bad.push({ field: "sort", message: "use campo,asc ou campo,desc com um campo permitido" });
      for (const k of ["unitId", "ageRangeId"]) if (p.get(k) !== null && !uuid.test(p.get(k)!)) bad.push({ field: k, message: "id inválido" });
      if (bad.length) return error(400, "VALIDATION_ERROR", "Dados inválidos.", bad);
      const seal = p.get("seal");
      if (seal !== null && !SEALS.includes(seal as StudentSeal)) return studentBad("seal", "selo inválido");
      const rangeId = p.get("ageRangeId");
      const range = rangeId ? ageRanges.find((r) => r.id === rangeId) : null;
      if (rangeId && !range) return error(404, "NOT_FOUND", "Não encontrado.");
      const unitId = p.get("unitId");
      const q = teacherKey((p.get("q") ?? "").trim());
      const found = mockStudents
        .filter((s) => role === "COORDINATOR" || s.registeredUnitId === ADMIN_UNIT)
        .filter((s) => teacherKey(s.name).includes(q) && (!unitId || s.registeredUnitId === unitId))
        .filter((s) => !range || covers(range, ageOf(s.birthDate)))
        .filter((s) => !seal || sealsOf(s).includes(seal as StudentSeal))
        .sort((a, b) => teacherKey(a.name).localeCompare(teacherKey(b.name)) || a.id.localeCompare(b.id));
      const items = found.slice((page - 1) * size, page * size).map(studentRowOf);
      return HttpResponse.json({ items, page, size, total: found.length });
    },
  },
  {
    // RF-ALU-01: cadastro. Adulto traz CPF, e-mail e celular; menor traz de 1 a 2 responsáveis (exatamente um financeiro).
    method: "post",
    path: "/students",
    resolver: async ({ request }) => {
      const denied = studentAccess();
      if (denied) return denied;
      const b = ((await request.json().catch(() => ({}))) as Partial<StudentInput>) ?? {};
      const name = b.name?.trim() ?? "";
      if (name.length < 2 || name.length > 80) return studentBad("name", "de 2 a 80 caracteres");
      if (!b.birthDate || new Date(b.birthDate).getTime() >= Date.now()) return studentBad("birthDate", "informe uma data no passado");
      const unit = units.find((u) => u.id === b.registeredUnitId);
      if (!unit?.active) return studentBad("registeredUnitId", "unidade inválida");
      if (activeRole() === "ADMINISTRATOR" && b.registeredUnitId !== ADMIN_UNIT) return error(403, "FORBIDDEN", "Sem permissão.");
      const cpf = b.cpf ? onlyDigits(b.cpf) : null;
      if (cpf && !isValidCpf(cpf)) return studentBad("cpf", "CPF inválido");
      const phone = b.phone ? onlyDigits(b.phone) : null;
      if (phone && !/^\d{10,11}$/.test(phone)) return studentBad("phone", "celular com DDD");
      const minor = isMinor(b.birthDate);
      if (!minor) {
        if (!cpf) return studentBad("cpf", "obrigatório");
        if (!b.email) return studentBad("email", "obrigatório");
        if (!phone) return studentBad("phone", "obrigatório");
      }
      const asked = b.guardians ?? [];
      if (asked.length > 2 || (minor && asked.length === 0)) return studentBad("guardians", "aluno menor precisa de um ou dois responsáveis");
      if (asked.length === 2 && asked.filter((g) => g.financial).length !== 1) return studentBad("guardians", "informe exatamente um responsável financeiro");
      if (cpf) {
        const same = mockStudents.find((s) => s.cpf === cpf);
        if (same) return studentError(409, "STUDENT_CPF_EXISTS", same);
      } else if (!b.confirmDuplicate) {
        const key = teacherKey(name);
        const same = mockStudents.find((s) => teacherKey(s.name) === key && s.birthDate === b.birthDate);
        if (same) return studentError(409, "STUDENT_POSSIBLE_DUPLICATE", same);
      }
      const rows: StudentGuardianRow[] = [];
      for (const g of asked) {
        const row = guardianRow(g, asked.length === 1 || g.financial === true);
        if (row instanceof Response) return row;
        rows.push(row);
      }
      const created: StudentDetail = {
        id: crypto.randomUUID(), registeredUnitId: b.registeredUnitId!, name, birthDate: b.birthDate, minor, postalCode: onlyDigits(b.postalCode ?? ""), street: b.street ?? "", number: b.number ?? "",
        complement: b.complement ?? null, district: b.district ?? "", city: b.city ?? "", state: (b.state ?? "").toUpperCase(), cpf, email: b.email ?? null, phone, gender: b.gender ?? null,
        referral: b.referral ?? null, emergencyName: b.emergencyName ?? null, emergencyRelationship: b.emergencyRelationship ?? null, emergencyPhone: b.emergencyPhone ? onlyDigits(b.emergencyPhone) : null,
        photoUrl: null, emailProblem: false, guardians: sortedGuardians(rows),
      };
      mockStudents = [...mockStudents, created];
      return HttpResponse.json(created, { status: 201 });
    },
  },
  {
    // RN-ALU-06: só id, nome e foto do aluno com esse CPF, de qualquer unidade.
    method: "get",
    path: "/students/lookup",
    resolver: ({ request }) => {
      const denied = studentAccess();
      if (denied) return denied;
      const cpf = onlyDigits(new URL(request.url).searchParams.get("cpf") ?? "");
      if (!isValidCpf(cpf)) return studentBad("cpf", "CPF inválido");
      const found = mockStudents.find((s) => s.cpf === cpf);
      return found ? HttpResponse.json({ id: found.id, name: found.name, photoUrl: found.photoUrl }) : error(404, "NOT_FOUND", "Não encontrado.");
    },
  },
  {
    method: "get",
    path: "/students/{id}",
    resolver: ({ params }) => {
      const found = studentFor(params.id as string);
      return found instanceof Response ? found : HttpResponse.json(found);
    },
  },
  {
    // RF-ALU-03: ficha. Coordenador e Administrador (alcance como no detalhe); Professor e portal 403.
    method: "get",
    path: "/students/{id}/record",
    resolver: ({ params }) => {
      const s = studentFor(params.id as string);
      if (s instanceof Response) return s;
      const row = studentRowOf(s);
      return HttpResponse.json({
        id: s.id, name: s.name, photoUrl: s.photoUrl, birthDate: s.birthDate, age: row.age, minor: s.minor, ageRange: row.ageRange,
        registeredUnit: row.registeredUnit, seals: row.seals, contact: { phone: s.phone, email: s.email, emailProblem: s.emailProblem }, cpf: s.cpf,
        address: { postalCode: s.postalCode, street: s.street, number: s.number, complement: s.complement, district: s.district, city: s.city, state: s.state },
        emergency: { name: s.emergencyName, relationship: s.emergencyRelationship, phone: s.emergencyPhone },
        guardians: s.guardians,
      });
    },
  },
  {
    // RF-ALU-03: histórico, da ação mais recente para a mais antiga, sem valores antes e depois.
    method: "get",
    path: "/students/{id}/history",
    resolver: ({ request, params }) => {
      const s = studentFor(params.id as string);
      if (s instanceof Response) return s;
      const p = new URL(request.url).searchParams;
      const page = Number(p.get("page") ?? 1);
      const size = Number(p.get("size") ?? 25);
      if (!Number.isInteger(page) || page < 1) return studentBad("page", "deve ser 1 ou maior");
      if (![25, 50, 100].includes(size)) return studentBad("size", "deve ser 25, 50 ou 100");
      const all = [{ action: "student.created", at: "2026-01-10T12:00:00Z", actor: "Ana Coordenadora", actorProfile: "COORDINATOR", reason: null }, ...(studentLog[s.id] ?? [])].reverse();
      return HttpResponse.json({ items: all.slice((page - 1) * size, page * size), page, size, total: all.length });
    },
  },
  {
    // RN-RSP-02, RN-RSP-06: liga com motivo; o primeiro é o financeiro; o terceiro dá GUARDIAN_LIMIT.
    method: "post",
    path: "/students/{id}/guardians",
    resolver: async ({ request, params }) => {
      const found = studentFor(params.id as string);
      if (found instanceof Response) return found;
      const body = ((await request.json().catch(() => ({}))) as Partial<StudentGuardianInput>) ?? {};
      const badReason = studentReason(body.reason);
      if (badReason) return badReason;
      if (found.guardians.length >= 2) return error(409, "GUARDIAN_LIMIT", "Um aluno tem no máximo dois responsáveis.");
      const row = guardianRow(body, found.guardians.length === 0 || body.financial === true);
      if (row instanceof Response) return row;
      if (found.guardians.some((g) => g.guardianId === row.guardianId)) return error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.");
      const others = row.financial ? found.guardians.map((g) => ({ ...g, financial: false })) : found.guardians;
      found.guardians = sortedGuardians([...others, row]);
      logStudent(found.id, "student.guardian_linked", body.reason?.trim() ?? null);
      return HttpResponse.json(found);
    },
  },
  {
    // Só `financial = true` existe (D4); `false` sozinho dá 400.
    method: "put",
    path: "/students/{id}/guardians/{guardianId}",
    resolver: async ({ request, params }) => {
      const found = studentFor(params.id as string);
      if (found instanceof Response) return found;
      const body = ((await request.json().catch(() => ({}))) as { financial?: boolean }) ?? {};
      if (body.financial !== true) return studentBad("financial", "só é possível indicar quem passa a ser o financeiro");
      if (!found.guardians.some((g) => g.guardianId === params.guardianId)) return error(404, "NOT_FOUND", "Não encontrado.");
      found.guardians = sortedGuardians(found.guardians.map((g) => ({ ...g, financial: g.guardianId === params.guardianId })));
      logStudent(found.id, "student.guardian_financial_changed", null);
      return HttpResponse.json(found);
    },
  },
  {
    // RN-RSP-01, RN-RSP-05: motivo de 3 a 200; o último responsável de menor dá MINOR_NEEDS_GUARDIAN; o financeiro passa ao que fica.
    method: "post",
    path: "/students/{id}/guardians/{guardianId}/unlink",
    resolver: async ({ request, params }) => {
      const found = studentFor(params.id as string);
      if (found instanceof Response) return found;
      const body = ((await request.json().catch(() => ({}))) as { reason?: string }) ?? {};
      const badReason = studentReason(body.reason);
      if (badReason) return badReason;
      const leaving = found.guardians.find((g) => g.guardianId === params.guardianId);
      if (!leaving) return error(404, "NOT_FOUND", "Não encontrado.");
      if (found.minor && found.guardians.length === 1) return error(409, "MINOR_NEEDS_GUARDIAN", "Aluno menor de 18 anos precisa de ao menos um responsável.");
      const left = found.guardians.filter((g) => g.guardianId !== params.guardianId);
      found.guardians = left.map((g) => ({ ...g, financial: leaving.financial ? true : g.financial }));
      logStudent(found.id, "student.guardian_unlinked", body.reason?.trim() ?? null);
      return HttpResponse.json(found);
    },
  },
  {
    // RN-ACE-13: no máximo 3 reenvios por dia (429); acesso ativo dá 409.
    method: "post",
    path: "/students/{id}/guardians/{guardianId}/invite",
    resolver: ({ params }) => {
      const found = studentFor(params.id as string);
      if (found instanceof Response) return found;
      const guardian = found.guardians.find((g) => g.guardianId === params.guardianId);
      if (!guardian) return error(404, "NOT_FOUND", "Não encontrado.");
      if (guardian.access === "ACTIVE") return error(409, "CONFLICT", "Os dados entram em conflito com algo que já existe.");
      if (guardianResends >= 3) return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      guardianResends += 1;
      logStudent(found.id, "student.guardian_invite_resent", null);
      return new HttpResponse(null, { status: 204 });
    },
  },
  {
    // RN-RSP-03: cartões do portal (Aluno e Responsável); no mock, o Aluno é o adulto e também responsável do menor.
    method: "get",
    path: "/portal/students",
    resolver: () => {
      if (!loggedIn) return unauthenticated();
      const role = activeRole();
      if (role !== "STUDENT" && role !== "GUARDIAN") return error(403, "FORBIDDEN", "Sem permissão.");
      const card = (s: StudentDetail, self: boolean) => ({ id: s.id, name: s.name, photoUrl: s.photoUrl, self });
      const adult = mockStudents.find((s) => s.id === STUDENT_ADULT)!;
      const minor = mockStudents.find((s) => s.id === STUDENT_MINOR)!;
      return HttpResponse.json(role === "STUDENT" ? [card(adult, true), card(minor, false)] : [card(minor, false)]);
    },
  },
];

export const handlers = routes.map((r) =>
  http[r.method](`/api/v1${r.path.replace(/\{(\w+)\}/g, ":$1")}`, r.resolver),
);
