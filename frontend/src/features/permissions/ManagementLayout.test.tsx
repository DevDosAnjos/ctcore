import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";

const link = (id: string, role: string) => ({ id, organizationId: "o", organizationName: "Alfa", role, unitIds: [] });
const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [link("l1", role)],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});

function setup(role: string, permissions?: () => Response | Promise<Response>) {
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", permissions ?? (() => HttpResponse.json({ permissions: mockPermissions(role) }))),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe("ManagementLayout", () => {
  it("CA13: carregando mostra o estado de carregamento", async () => {
    setup("COORDINATOR", () => new Promise<Response>(() => {}));
    expect(await screen.findByRole("status")).toHaveTextContent(/carregando/i);
  });

  it("CA13: erro mostra Tentar de novo e a nova tentativa monta o menu", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup("COORDINATOR", () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json({ permissions: mockPermissions("COORDINATOR") })));
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect((await screen.findAllByRole("link", { name: "Início", hidden: true })).length).toBeGreaterThan(0);
  });

  it("CA13: perfil sem nenhuma área mostra a tela de área indisponível", async () => {
    setup("TEACHER", () => HttpResponse.json({ permissions: mockPermissions("STUDENT") }));
    expect(await screen.findByRole("heading", { name: "Esta área não está disponível para o seu perfil" })).toBeInTheDocument();
  });

  it("R7: Professor e Coordenador veem o menu com o Início e o conteúdo da tela", async () => {
    setup("TEACHER");
    const nav = await screen.findByRole("navigation", { name: "Menu principal", hidden: true });
    expect(within(nav).getByRole("link", { name: "Início", hidden: true })).toHaveAttribute("aria-current", "page");
    expect(within(nav).queryByText("Financeiro")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
  });

  it("CA1/CA2/CA6: organização, área, contador de Pedidos e conta no desktop", async () => {
    server.use(http.get("/api/v1/portal-requests", () => HttpResponse.json({ items: [], total: 3 })));
    window.innerWidth = 1440;
    setup("COORDINATOR");
    expect(await screen.findByText("Gestão · Coordenação")).toBeInTheDocument();
    expect(screen.getByText("Alfa")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Pessoas" }));
    await waitFor(() => expect(screen.getByRole("link", { name: "Pedidos" })).toHaveTextContent("3"));
    expect(screen.getByRole("link", { name: /^Minha conta/ })).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA14: sem violações no axe a 1440 px no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 1440;
    const { container } = setup("COORDINATOR");
    await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" });
    expect(await axeViolations(container)).toEqual([]);
  });

  it("CA13: a 360 px o menu abre pelo botão", async () => {
    const user = userEvent.setup();
    window.innerWidth = 360;
    setup("COORDINATOR");
    await user.click(await screen.findByRole("button", { name: "Menu" }));
    expect(screen.getByRole("dialog", { name: "Menu" })).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA13: sem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup("COORDINATOR");
    await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" });
    expect(await axeViolations(container)).toEqual([]);
  });
});
