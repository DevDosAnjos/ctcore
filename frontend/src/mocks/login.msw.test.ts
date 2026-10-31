const login = (email: string, password: string) =>
  fetch("/api/v1/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password }) });

describe("login travado x MSW (#445, CA7)", () => {
  it("limite@exemplo.com devolve 429 com retryAt no futuro", async () => {
    const res = await login("limite@exemplo.com", "qualquer");
    expect(res.status).toBe(429);
    const body = await res.json();
    expect(body.fields[0].field).toBe("retryAt");
    expect(new Date(body.fields[0].message).getTime()).toBeGreaterThan(Date.now());
  });

  it("senha limite com outro e-mail devolve 429 sem retryAt", async () => {
    const res = await login("ana@exemplo.com", "limite");
    expect(res.status).toBe(429);
    expect((await res.json()).fields).toEqual([]);
  });
});
