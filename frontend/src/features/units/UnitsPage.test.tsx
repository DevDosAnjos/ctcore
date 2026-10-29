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
import type { UnitSummary } from "./useUnits";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const unit = (over: Partial<UnitSummary> = {}): UnitSummary => ({
  id: "u1",
  name: "Unidade Centro",
  shortName: "alfa-centro",
  shortNameAdjusted: false,
  city: "Rio de Janeiro",
  activeStudents: 0,
  administrators: [{ name: "Bruno" }, { name: "Carla" }],
  active: true,
  ...over,
});
const two = [unit(), unit({ id: "u2", name: "Unidade Norte", shortName: "alfa-norte", shortNameAdjusted: true, city: null, administrators: [], active: false })];

let patches: unknown[];

function setup(role: string, read: () => Response | Promise<Response> = () => HttpResponse.json(two)) {
  patches = [];
  let stored = two;
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: false, units: [] })),
    http.get("/api/v1/units", read),
    http.patch("/api/v1/units/:id/short-name", async ({ request, params }) => {
      const body = (await request.json()) as { shortName: string };
      patches.push(body);
      if (body.shortName === "em-uso") return HttpResponse.json({ code: "CONFLICT", fields: [] }, { status: 409 });
      if (body.shortName === "quebrado") return HttpResponse.json({ code: "INTERNAL_ERROR", fields: [] }, { status: 500 });
      const changed = { ...stored.find((u) => u.id === params.id)!, shortName: body.shortName, shortNameAdjusted: true };
      stored = stored.map((u) => (u.id === params.id ? changed : u));
      return HttpResponse.json(changed);
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/configuracoes/unidades"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Unidades", level: 1 });

describe("UnitsPage", () => {
  it("CA1 / CA2: lista cidade, alunos ativos, Administradores, situação e nome curto; só a unidade não ajustada tem o botão", async () => {
    setup("COORDINATOR");
    await ready();
    const rowOf = (name: string) => screen.getAllByRole("row").find((r) => r.querySelector("td")?.textContent === name)!;
    const centro = rowOf("Unidade Centro");
    expect(within(centro).getByText("Rio de Janeiro")).toBeInTheDocument();
    expect(within(centro).getByText("Bruno, Carla")).toBeInTheDocument();
    expect(within(centro).getByText("Ativa")).toBeInTheDocument();
    expect(within(centro).getByText("alfa-centro")).toBeInTheDocument();
    expect(within(centro).getByRole("button", { name: /Ajustar nome curto/ })).toBeInTheDocument();
    const norte = rowOf("Unidade Norte");
    expect(within(norte).getByText("Não informada")).toBeInTheDocument();
    expect(within(norte).getByText("Nenhum Administrador")).toBeInTheDocument();
    expect(within(norte).getByText("Desativada")).toBeInTheDocument();
    expect(within(norte).queryByRole("button")).toBeNull();
  });

  it("CA13: mostra carregando, depois erro com nova tentativa que recarrega, e vazio", async () => {
    const user = userEvent.setup();
    let calls = 0;
    setup("COORDINATOR", () => (++calls === 1 ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json([])));
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByRole("heading", { name: "Nenhuma unidade" })).toBeInTheDocument();
  });

  it("CA13: o 403 do servidor mostra o estado sem permissão", async () => {
    setup("COORDINATOR", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("CA16: o Administrador não vê Unidades no menu e a rota mostra que a área não está disponível", async () => {
    setup("ADMINISTRATOR");
    expect(await screen.findByRole("heading", { name: "Esta área não está disponível para o seu perfil" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Unidades", level: 1 })).toBeNull();
  });

  it("CA14: o Coordenador ajusta o nome curto, o botão some e a lista mostra o valor novo", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.click(screen.getByRole("button", { name: /Ajustar nome curto/ }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/só pode ajustar o nome curto uma vez/)).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText("Novo nome curto"), "centro-novo");
    await user.click(within(dialog).getByRole("button", { name: "Ajustar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(patches).toEqual([{ shortName: "centro-novo" }]);
    expect(screen.getByText("centro-novo")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Ajustar nome curto/ })).toBeNull();
  });

  it("CA14: formato inválido bloqueia o envio; nome em uso (409) e falha do servidor mostram a mensagem e mantêm a janela aberta", async () => {
    const user = userEvent.setup();
    setup("COORDINATOR");
    await ready();
    await user.click(screen.getByRole("button", { name: /Ajustar nome curto/ }));
    const dialog = await screen.findByRole("dialog");
    const input = within(dialog).getByLabelText("Novo nome curto");
    const confirm = within(dialog).getByRole("button", { name: "Ajustar" });
    expect(confirm).toBeDisabled();
    await user.type(input, "Maiuscula");
    expect(within(dialog).getByRole("alert")).toHaveTextContent("Use até 60 letras minúsculas");
    expect(confirm).toBeDisabled();
    await user.clear(input);
    await user.type(input, "em-uso");
    await user.click(confirm);
    expect(await within(dialog).findByText("Este nome curto não está disponível. Escolha outro.")).toBeInTheDocument();
    await user.clear(input);
    await user.type(input, "quebrado");
    await user.click(confirm);
    expect(await within(dialog).findByText("Não foi possível ajustar o nome curto. Tente de novo.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(screen.getByText("alfa-centro")).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA13: a 360 px não tem violações no axe no tema %s, com a lista e com a janela aberta", async (_n, dark) => {
    const user = userEvent.setup();
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup("COORDINATOR");
    await ready();
    expect(await axeViolations(container)).toEqual([]);
    await user.click(screen.getByRole("button", { name: /Ajustar nome curto/ }));
    await screen.findByRole("dialog");
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
