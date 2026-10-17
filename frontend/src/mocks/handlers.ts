import { http, HttpResponse, type HttpResponseResolver } from "msw";
import { isStrongPassword } from "@/features/auth/PasswordRules";

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
};
let current = session;

// Sessão simulada: abre com um login bem-sucedido. Senha "errada" dá 401, "limite" dá 429.
let loggedIn = false;
export const resetMockSession = () => {
  loggedIn = false;
  current = session;
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
      loggedIn = true;
      current = email === many.account.email ? { ...many } : session;
      return HttpResponse.json(current);
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
];

export const handlers = routes.map((r) =>
  http[r.method](`/api/v1${r.path.replace(/\{(\w+)\}/g, ":$1")}`, r.resolver),
);
