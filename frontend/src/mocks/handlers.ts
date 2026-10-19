import { http, HttpResponse, type HttpResponseResolver } from "msw";
import { isStrongPassword } from "@/features/auth/PasswordRules";
import { isValidCpfOrCnpj, onlyDigits } from "@/components/fields/masks";
import { isHexColor, passesContrast } from "@/features/organization/contrastRatio";
import { mockPermissions } from "./permissions";

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
  teacherContacts = initialTeacherContacts();
  teacherResends = 0;
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
const defaultUnitSettings = () => ({
  dueDays: [1, 5, 10, 15],
  enrollmentFeeEnabled: false,
  enrollmentFeeCents: 0,
  feesCents: { cash: 0, pix: 0, bankSlip: 0, creditCard: 0, debitCard: 0, automaticDebit: 0, manualPix: 0, terminalCard: 0, transfer: 0 },
  pixKey: null as string | null,
  paymentAccount: "NOT_CONNECTED" as "CONNECTED" | "NOT_CONNECTED",
  reminderDays: [-3, 0, 3, 10],
  teacherPermissions: { announcements: true, agendaEdit: true, studentContacts: true, trialClasses: true },
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
  return { id, name, photoUrl, directoryConsent, status, modalities, units, access, ...teacherContacts[id], classes: [], upcomingClasses: [] };
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
const classFor = (id: string) => {
  if (!loggedIn) return unauthenticated();
  const role = activeRole();
  if (role !== "COORDINATOR" && role !== "ADMINISTRATOR") return error(403, "FORBIDDEN", "Sem permissão.");
  return mockClasses.find((c) => c.id === id && (role === "COORDINATOR" || c.unitId === ADMIN_UNIT)) ?? error(404, "NOT_FOUND", "Não encontrado.");
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
    method: "get",
    path: "/classes/{id}",
    resolver: ({ params }) => {
      const found = classFor(params.id as string);
      return found instanceof HttpResponse ? found : HttpResponse.json(classDetail(found));
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
];

export const handlers = routes.map((r) =>
  http[r.method](`/api/v1${r.path.replace(/\{(\w+)\}/g, ":$1")}`, r.resolver),
);
