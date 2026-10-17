import { http, HttpResponse, type HttpResponseResolver } from "msw";

export type Method = "get" | "post" | "put" | "patch" | "delete";

export interface Route {
  method: Method;
  /** Caminho como no OpenAPI (sem o prefixo /api/v1), ex.: "/class-groups/{id}". */
  path: string;
  resolver: HttpResponseResolver;
}

const error = (status: number, code: string, message: string) =>
  HttpResponse.json({ code, message, changed: false, fields: [], traceId: "mock" }, { status });

const session = {
  account: { id: "00000000-0000-0000-0000-000000000001", email: "ana@exemplo.com", name: "Ana" },
  links: [{ id: "00000000-0000-0000-0000-000000000002", organizationId: "00000000-0000-0000-0000-000000000003", role: "COORDINATOR", unitIds: [] }],
  activeLinkId: "00000000-0000-0000-0000-000000000002",
};

// Sessão simulada: abre com um login bem-sucedido. Senha "errada" dá 401, "limite" dá 429.
let loggedIn = false;
export const resetMockSession = () => {
  loggedIn = false;
};

// Cada item que declarar rota acrescenta aqui o handler dela; o teste msw.test.ts confere contra o OpenAPI.
export const routes: Route[] = [
  {
    method: "post",
    path: "/auth/login",
    resolver: async ({ request }) => {
      const { password } = (await request.json()) as { password?: string };
      if (password === "limite") return error(429, "TOO_MANY_REQUESTS", "Muitas tentativas.");
      if (password === "errada") return error(401, "INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
      loggedIn = true;
      return HttpResponse.json(session);
    },
  },
  {
    method: "get",
    path: "/auth/session",
    resolver: () => (loggedIn ? HttpResponse.json(session) : error(401, "UNAUTHENTICATED", "Sem sessão.")),
  },
];

export const handlers = routes.map((r) =>
  http[r.method](`/api/v1${r.path.replace(/\{(\w+)\}/g, ":$1")}`, r.resolver),
);
