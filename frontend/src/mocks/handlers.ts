import { http, type HttpResponseResolver } from "msw";

export type Method = "get" | "post" | "put" | "patch" | "delete";

export interface Route {
  method: Method;
  /** Caminho como no OpenAPI (sem o prefixo /api/v1), ex.: "/class-groups/{id}". */
  path: string;
  resolver: HttpResponseResolver;
}

// Vazia: `paths` do docs/openapi.yaml ainda não tem rotas. Cada item que declarar rota
// acrescenta aqui o handler dela; o teste msw.test.ts confere contra o OpenAPI.
export const routes: Route[] = [];

export const handlers = routes.map((r) =>
  http[r.method](`/api/v1${r.path.replace(/\{(\w+)\}/g, ":$1")}`, r.resolver),
);
