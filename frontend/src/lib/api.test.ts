import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { ApiError, api } from "./api";

describe("api", () => {
  afterEach(() => {
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  });

  it("202 sem corpo é sucesso", async () => {
    server.use(http.post("/api/v1/x", () => new HttpResponse(null, { status: 202 })));
    expect(await api("/x", { method: "POST" })).toBeUndefined();
  });

  it("manda X-XSRF-TOKEN em POST e não em GET", async () => {
    document.cookie = "XSRF-TOKEN=abc%3D";
    const seen: Record<string, string | null> = {};
    server.use(
      http.post("/api/v1/x", ({ request }) => {
        seen.post = request.headers.get("X-XSRF-TOKEN");
        return HttpResponse.json({ ok: 1 });
      }),
      http.get("/api/v1/x", ({ request }) => {
        seen.get = request.headers.get("X-XSRF-TOKEN");
        return new HttpResponse(null, { status: 204 });
      }),
    );
    expect(await api("/x", { method: "POST", body: { a: 1 } })).toEqual({ ok: 1 });
    expect(await api("/x")).toBeUndefined();
    expect(seen).toEqual({ post: "abc=", get: null });
  });

  it("erro da API vira ApiError com código e campos; corpo ilegível vira INTERNAL_ERROR", async () => {
    server.use(
      http.get("/api/v1/e", () => HttpResponse.json({ code: "VALIDATION_ERROR", fields: [{ field: "a", message: "m" }] }, { status: 400 })),
      http.get("/api/v1/h", () => new HttpResponse("<html>", { status: 502 })),
    );
    await expect(api("/e")).rejects.toMatchObject({ status: 400, code: "VALIDATION_ERROR", fields: [{ field: "a", message: "m" }] });
    await expect(api("/h")).rejects.toMatchObject({ status: 502, code: "INTERNAL_ERROR" });
  });

  it("rede fora vira status 0", async () => {
    server.use(http.get("/api/v1/n", () => HttpResponse.error()));
    const err = (await api("/n").catch((e) => e)) as ApiError;
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(0);
  });
});
