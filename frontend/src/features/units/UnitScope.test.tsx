import { render, screen } from "@testing-library/react";
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

const A = "00000000-0000-0000-0000-00000000000a";
const B = "00000000-0000-0000-0000-00000000000b";
const unit = (id: string, name: string) => ({ id, name, shortName: name.toLowerCase().replace(" ", "-") });
const KEY = "ctcore.unitScope.l1";

function setup(role: string, reachable: () => Response | Promise<Response>) {
  const session: SessionView = {
    account: { id: "a", email: "x@x.com", name: "X" },
    links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
    activeLinkId: "l1",
    twoFactor: { enabled: false, required: false },
  };
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session)),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", reachable),
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
const two = (allUnits: boolean) => () => HttpResponse.json({ allUnits, units: [unit(A, "Unidade A"), unit(B, "Unidade B")] });
const switcher = () => screen.findByRole("combobox", { name: "Unidade ativa" });

describe("seletor de unidade (RF-ORG-05)", () => {
  it("RN-ORG-09 / CA5: com uma unidade não há seletor; com duas há", async () => {
    const one = setup("ADMINISTRATOR", () => HttpResponse.json({ allUnits: false, units: [unit(A, "Unidade A")] }));
    expect(await screen.findByText("Unidade: Unidade A")).toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Unidade ativa" })).not.toBeInTheDocument();
    one.unmount();
    setup("ADMINISTRATOR", two(false));
    expect(await switcher()).toBeInTheDocument();
  });

  it("RN-ORG-09 / CA6: Coordenador lista Todas as unidades e as duas, começando em Todas; Administrador só as duas", async () => {
    const coordinator = setup("COORDINATOR", two(true));
    const select = await switcher();
    expect([...select.querySelectorAll("option")].map((o) => o.textContent)).toEqual(["Todas as unidades", "Unidade A", "Unidade B"]);
    expect(select).toHaveValue("all");
    expect(screen.getByText(/todas as unidades, só para consulta/)).toBeInTheDocument();
    coordinator.unmount();
    setup("ADMINISTRATOR", two(false));
    const admin = await switcher();
    expect([...admin.querySelectorAll("option")].map((o) => o.textContent)).toEqual(["Unidade A", "Unidade B"]);
    expect(admin).toHaveValue(A);
  });

  it("CA8: a escolha válida é guardada e mantida; a que saiu do alcance volta ao padrão", async () => {
    const user = userEvent.setup();
    const first = setup("COORDINATOR", two(true));
    await user.selectOptions(await switcher(), B);
    expect(localStorage.getItem(KEY)).toBe(B);
    first.unmount();
    const second = setup("COORDINATOR", two(true));
    expect(await switcher()).toHaveValue(B);
    second.unmount();
    localStorage.setItem(KEY, "00000000-0000-0000-0000-0000000000ff");
    const third = setup("COORDINATOR", two(true));
    expect(await switcher()).toHaveValue("all");
    third.unmount();
    // Administrador nunca herda "all" guardado
    localStorage.setItem(KEY, "all");
    setup("ADMINISTRATOR", two(false));
    expect(await switcher()).toHaveValue(A);
  });

  it("CA9: carregando e erro com Tentar de novo sem derrubar a página; 403 sem seletor", async () => {
    const user = userEvent.setup();
    const loading = setup("COORDINATOR", () => new Promise<Response>(() => {}));
    expect(await screen.findByText("Carregando unidades...")).toBeInTheDocument();
    loading.unmount();
    let fail = true;
    const failing = setup("COORDINATOR", () => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : two(true)()));
    expect(await screen.findByText("Não foi possível carregar as unidades.")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Início", hidden: true })[0]).toBeInTheDocument();
    fail = false;
    await user.click(screen.getByRole("button", { name: "Tentar de novo" }));
    expect(await switcher()).toBeInTheDocument();
    failing.unmount();
    setup("COORDINATOR", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    await screen.findAllByRole("link", { name: "Início", hidden: true });
    expect(screen.queryByRole("combobox", { name: "Unidade ativa" })).not.toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA9: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup("COORDINATOR", two(true));
    await switcher();
    expect(await axeViolations(container)).toEqual([]);
  });
});
