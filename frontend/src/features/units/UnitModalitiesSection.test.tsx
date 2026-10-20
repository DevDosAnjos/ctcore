import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { UnitModalitiesSection } from "./UnitModalitiesSection";

const A = "00000000-0000-0000-0000-00000000000a";
const B = "00000000-0000-0000-0000-00000000000b";
const item = (id: string, name: string, offered: boolean) => ({ id, name, offered });

const ui = (unitId: string) => (
  <ThemeProvider>
    <MemoryRouter>
      <UnitModalitiesSection unitId={unitId} />
    </MemoryRouter>
  </ThemeProvider>
);
const listing = (unit: Record<string, unknown>, canManage = true) =>
  http.get("/api/v1/units/:id/modalities", ({ params }) => HttpResponse.json({ canManage, items: unit[String(params.id)] ?? [] }));

describe("UnitModalitiesSection (RF-MOD-03)", () => {
  it("CA7: lista as modalidades, marca e desmarca com efeito imediato e a lista reflete sem recarregar", async () => {
    const user = userEvent.setup();
    const puts: unknown[] = [];
    server.use(
      listing({ [A]: [item("m1", "Boxe", false), item("m2", "Judô", true)] }),
      http.put("/api/v1/units/:id/modalities/:mid", async ({ request, params }) => {
        puts.push([params.id, params.mid, await request.json()]);
        return HttpResponse.json(item(String(params.mid), "x", true));
      }),
    );
    render(ui(A));
    const boxe = await screen.findByRole("switch", { name: "Boxe" });
    expect(boxe).not.toBeChecked();
    expect(screen.getByRole("switch", { name: "Judô" })).toBeChecked();
    await user.click(boxe);
    expect(screen.getByRole("switch", { name: "Boxe" })).toBeChecked();
    await user.click(screen.getByRole("switch", { name: "Judô" }));
    expect(screen.getByRole("switch", { name: "Judô" })).not.toBeChecked();
    expect(puts).toEqual([[A, "m1", { offered: true }], [A, "m2", { offered: false }]]);
  });

  it("CA7: erro do servidor volta o interruptor ao valor anterior e mostra a mensagem", async () => {
    const user = userEvent.setup();
    server.use(listing({ [A]: [item("m1", "Boxe", false)] }), http.put("/api/v1/units/:id/modalities/:mid", () => HttpResponse.json({ code: "BOOM" }, { status: 500 })));
    render(ui(A));
    await user.click(await screen.findByRole("switch", { name: "Boxe" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível alterar Boxe. Nada foi alterado. Tente de novo.");
    expect(screen.getByRole("switch", { name: "Boxe" })).not.toBeChecked();
  });

  it("RN-MOD-06: turma ativa impede desmarcar, o interruptor volta e a tela lista as turmas", async () => {
    const user = userEvent.setup();
    server.use(
      listing({ [A]: [item("m1", "Judô", true)] }),
      http.put("/api/v1/units/:id/modalities/:mid", () =>
        HttpResponse.json(
          { code: "UNIT_MODALITY_IN_USE", message: "x", changed: false, fields: [{ field: "class", message: "Judô Adulto" }, { field: "class", message: "Judô Kids" }] },
          { status: 409 },
        ),
      ),
    );
    render(ui(A));
    await user.click(await screen.findByRole("switch", { name: "Judô" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("A unidade não pode deixar de oferecer Judô enquanto houver turmas ativas dela");
    expect(alert).toHaveTextContent("Judô Adulto");
    expect(alert).toHaveTextContent("Judô Kids");
    expect(screen.getByRole("switch", { name: "Judô" })).toBeChecked();
    expect(await axeViolations(document.body)).toEqual([]);
  });

  it("CA7: sem canManage o interruptor é só leitura", async () => {
    server.use(listing({ [A]: [item("m1", "Boxe", true)] }, false));
    render(ui(A));
    const boxe = await screen.findByRole("switch", { name: "Boxe" });
    expect(boxe).toBeDisabled();
    expect(boxe).toBeChecked();
  });

  it("CA8: trocar a unidade mostra as marcações da unidade escolhida", async () => {
    server.use(listing({ [A]: [item("m1", "Boxe", true)], [B]: [item("m1", "Boxe", false)] }));
    const { rerender } = render(ui(A));
    expect(await screen.findByRole("switch", { name: "Boxe" })).toBeChecked();
    rerender(ui(B));
    await waitFor(() => expect(screen.getByRole("switch", { name: "Boxe" })).not.toBeChecked());
  });

  it("CA7: vazio orienta a ativar modalidades, com link; erro tem 'Tentar de novo'; 403 mostra sem permissão; carregando", async () => {
    const user = userEvent.setup();
    server.use(listing({}));
    const first = render(ui(A));
    expect(screen.getByText("Carregando...")).toBeInTheDocument();
    expect(await screen.findByText("Nenhuma modalidade ativa")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ir para Configurações › Modalidades" })).toHaveAttribute("href", "/configuracoes/modalidades");
    first.unmount();

    let fail = true;
    server.use(http.get("/api/v1/units/:id/modalities", () => (fail ? HttpResponse.error() : HttpResponse.json({ canManage: true, items: [item("m1", "Boxe", false)] }))));
    render(ui(A));
    fail = true;
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    fail = false;
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByRole("switch", { name: "Boxe" })).toBeInTheDocument();

    server.use(http.get("/api/v1/units/:id/modalities", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 })));
    render(ui(B));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA7: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    server.use(listing({ [A]: [item("m1", "Boxe", false), item("m2", "Judô", true)] }));
    const { container } = render(ui(A));
    await screen.findByRole("switch", { name: "Boxe" });
    expect(await axeViolations(container)).toEqual([]);
  });
});
