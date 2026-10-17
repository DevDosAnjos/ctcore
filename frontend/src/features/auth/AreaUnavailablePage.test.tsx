import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { SessionProvider, type SessionView } from "./SessionProvider";

const L = (id: string, role: string) => ({ id, organizationId: "o", organizationName: "Alfa", role, unitIds: [] });
const account = { id: "a", email: "x@x.com", name: "X" };
const off = { enabled: false, required: false };
const TITLE = "Esta área não está disponível para o seu perfil";

function setup(view: SessionView | null, path = "/sem-acesso") {
  server.use(
    http.get("/api/v1/auth/session", () =>
      view ? HttpResponse.json(view) : HttpResponse.json({ code: "UNAUTHENTICATED", message: "m", changed: false, fields: [], traceId: "t" }, { status: 401 }),
    ),
    http.get("/api/v1/auth/login", () => HttpResponse.json({})),
    http.get("/api/v1/permissions", () =>
      HttpResponse.json({ permissions: mockPermissions(view?.links.find((l) => l.id === view.activeLinkId)?.role) }),
    ),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}
const one: SessionView = { account, links: [L("l1", "TEACHER")], activeLinkId: "l1", twoFactor: off };
const two: SessionView = { account, links: [L("l1", "TEACHER"), L("l2", "STUDENT")], activeLinkId: "l1", twoFactor: off };

describe("AreaUnavailablePage", () => {
  it("CA1, CA9: título, texto e link para o Início, sem citar recurso", async () => {
    setup(one);
    expect(await screen.findByRole("heading", { level: 1, name: TITLE })).toBeInTheDocument();
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByText(/não permite abrir este endereço/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ir para o Início" })).toHaveAttribute("href", "/");
  });

  it("CA5: sem sessão vai ao login com next", async () => {
    setup(null);
    await waitFor(() => expect(screen.queryByRole("heading", { name: TITLE })).not.toBeInTheDocument());
    expect(await screen.findByRole("button", { name: "Entrar" })).toBeInTheDocument();
  });

  it("CA6: Ir para o Início leva a /; troca só com mais de um vínculo", async () => {
    const user = userEvent.setup();
    setup(two);
    await screen.findByRole("heading", { name: TITLE });
    expect(screen.getByRole("button", { name: "Trocar organização ou área" })).toBeInTheDocument();
    await user.click(screen.getByRole("link", { name: "Ir para o Início" }));
    expect(await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" })).toBeInTheDocument();
  });

  it("CA6: com um vínculo não há troca", async () => {
    setup(one);
    await screen.findByRole("heading", { name: TITLE });
    expect(screen.queryByRole("button", { name: "Trocar organização ou área" })).not.toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA7: axe a 360 px no tema %s e foco no título", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    setup(two);
    const h1 = await screen.findByRole("heading", { name: TITLE });
    await waitFor(() => expect(h1).toHaveFocus());
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
