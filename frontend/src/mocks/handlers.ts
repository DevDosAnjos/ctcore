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
  sessions = initialSessions();
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
