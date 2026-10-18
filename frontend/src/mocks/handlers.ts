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
};

// Organização do vínculo ativo (RF-ORG-01), em memória: sem cor, titular nem logo.
const initialOrganization = { name: "Academia Alfa", color: null as string | null, holderDocument: null as string | null, logoUrl: null as string | null };
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
      const body = (await request.json()) as { name?: string; color?: string | null; holderDocument?: string | null };
      const name = (body.name ?? "").trim();
      const color = body.color ? body.color : null;
      const holder = body.holderDocument ? body.holderDocument : null;
      const fields: { field: string; message: string }[] = [];
      if (!name || name.length > 120) fields.push({ field: "name", message: "entre 1 e 120 caracteres" });
      if (color !== null && !(isHexColor(color) && passesContrast(color))) fields.push({ field: "color", message: "use #RRGGBB com contraste mínimo de 4,5:1" });
      if (holder !== null && !isValidCpfOrCnpj(holder)) fields.push({ field: "holderDocument", message: "CPF ou CNPJ inválido" });
      if (fields.length) return error(400, "VALIDATION_ERROR", "Dados inválidos.", fields);
      organization = { ...organization, name, color: color?.toLowerCase() ?? null, holderDocument: holder ? onlyDigits(holder) : null };
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
      organization = { ...organization, logoUrl: MOCK_LOGO };
      return HttpResponse.json(organization);
    },
  },
  {
    method: "delete",
    path: "/organization/logo",
    resolver: () => {
      const denied = organizationAccess(true);
      if (denied) return denied;
      organization = { ...organization, logoUrl: null };
      return HttpResponse.json(organization);
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
      unitDetails[id] = next;
      Object.assign(units.find((u) => u.id === id)!, { name, city: next.city });
      return HttpResponse.json(next);
    },
  },
];

export const handlers = routes.map((r) =>
  http[r.method](`/api/v1${r.path.replace(/\{(\w+)\}/g, ":$1")}`, r.resolver),
);
