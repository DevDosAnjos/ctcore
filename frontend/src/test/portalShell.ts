import { http, HttpResponse } from "msw";

/** Moldura do portal (#88) para os testes que montam o portal sem fazer login no mock: organização sem logo, portal completo, 2 avisos. */
export const portalShellOk = http.get("/api/v1/portal/shell", () =>
  HttpResponse.json({ organization: { name: "Academia Alfa", color: null, logoUrl: null }, reduced: false, unreadAnnouncements: 2 }),
);
