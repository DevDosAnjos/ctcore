import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { PortalHomePage } from "./PortalHomePage";
import type { HomeBelt, HomeCharge, HomeLesson, PortalHomeData } from "./usePortalHome";

const jiu = { id: "m1", name: "Jiu-Jitsu" };
const judo = { id: "m2", name: "Judô" };
const centro = { id: "u1", name: "Centro" };
const norte = { id: "u2", name: "Norte" };
const charge = (over: Partial<HomeCharge> = {}): HomeCharge => ({
  id: "c1", studentId: "s1", description: "Kimono", referenceMonth: null, dueOn: "2026-10-20", amountCents: 15000, situation: "OVERDUE", canPay: true, modality: jiu, unit: centro, ...over,
});
const lesson = (over: Partial<HomeLesson> = {}): HomeLesson => ({
  id: "l1", date: "2026-10-28", startsAt: "19:00", endsAt: "20:00", title: "Jiu-Jitsu Adulto", status: "SCHEDULED", cancelReason: null, changed: false, adhoc: false, modality: jiu, unit: centro, ...over,
});
const belt = (over: Partial<HomeBelt> = {}): HomeBelt => ({ modality: jiu, levelName: "Azul", color: "#1E40AF", secondaryColor: null, degree: 2, achievedOn: "2026-10-01", isNew: false, ...over });
const home = (over: Partial<PortalHomeData> = {}): PortalHomeData => ({
  reduced: false,
  students: [{ id: "s1", name: "Ana Silva", photoUrl: null, self: true, openCents: 15000 }],
  familyOpenCents: 15000,
  selectedStudentId: "s1",
  charges: { items: [charge()], nextDueOn: null },
  lessons: [lesson()],
  announcements: { unreadCount: 1, items: [{ id: "a1", title: "Aula de sábado", sentAt: "2026-10-25T12:00:00Z", pinned: true, unread: true, authorName: null }] },
  belts: [belt({ isNew: true })],
  ...over,
});
const serve = (data: PortalHomeData | (() => Response)) => server.use(http.get("/api/v1/portal/home", typeof data === "function" ? data : () => HttpResponse.json(data)));
const view = () =>
  render(
    <ThemeProvider>
      <MemoryRouter>
        <PortalHomePage />
      </MemoryRouter>
    </ThemeProvider>,
  );

describe("PortalHomePage (RF-POR-02)", () => {
  it("CA15: as seções ficam na ordem cobrança, aulas, avisos, faixa", async () => {
    serve(home());
    view();
    expect(await screen.findByRole("heading", { name: "Bem-vindo ao ctcore", level: 1 })).toBeInTheDocument();
    await screen.findByText("Kimono");
    expect(screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent)).toEqual(["Cobrança", "Próximas aulas", "Avisos", "Faixa"]);
    expect(screen.getByText("Vencida")).toBeInTheDocument();
    expect(screen.getByText("Nova")).toBeInTheDocument();
    expect(screen.getByText("Fixado")).toBeInTheDocument();
    expect(screen.getByText("1 aviso não lido")).toBeInTheDocument();
  });

  it("CA8: o link Ver carteirinha leva ao aluno escolhido", async () => {
    serve(home({ students: [{ id: "s1", name: "Ana Silva", photoUrl: null, self: true, openCents: 0 }, { id: "s2", name: "Beto Silva", photoUrl: null, self: false, openCents: 0 }], selectedStudentId: "s1" }));
    const user = userEvent.setup();
    view();
    await waitFor(() => expect(screen.getByRole("link", { name: "Ver carteirinha" })).toHaveAttribute("href", "/portal/carteirinha?aluno=s1"));
    await user.click(await screen.findByRole("button", { name: /Beto Silva/ }));
    await waitFor(() => expect(screen.getByRole("link", { name: "Ver carteirinha" })).toHaveAttribute("href", "/portal/carteirinha?aluno=s2"));
  });

  it("CA2: sem cobrança que peça ação mostra Tudo em dia, com a data do próximo vencimento quando houver", async () => {
    serve(home({ charges: { items: [], nextDueOn: "2026-11-10" } }));
    const { unmount } = view();
    expect(await screen.findByText("Tudo em dia")).toBeInTheDocument();
    expect(screen.getByText("Próximo vencimento em 10/11/2026.")).toBeInTheDocument();
    unmount();
    serve(home({ charges: { items: [], nextDueOn: null } }));
    view();
    expect(await screen.findByText("Tudo em dia")).toBeInTheDocument();
    expect(screen.queryByText(/Próximo vencimento/)).not.toBeInTheDocument();
  });

  it("CA3: em conferência não tem Pagar; Pagar numa aberta abre o diálogo do Pix", async () => {
    serve(home({ charges: { items: [charge({ id: "c1" }), charge({ id: "c2", description: "Mensalidade", situation: "IN_REVIEW", canPay: false })], nextDueOn: null } }));
    server.use(http.get("/api/v1/portal/charges/c1/pix-instructions", () => HttpResponse.json({ chargeId: "c1", pixKey: "pix@alfa.test", amountCents: 15000, dueOn: "2026-10-20", unit: "Centro" })));
    const user = userEvent.setup();
    view();
    await screen.findByText("Em conferência");
    expect(screen.getAllByRole("button", { name: /^Pagar/ })).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: "Pagar Kimono" }));
    expect(await screen.findByText("pix@alfa.test")).toBeInTheDocument();
  });

  it("CA4: aula cancelada com o motivo e aula alterada em destaque", async () => {
    serve(
      home({
        lessons: [lesson({ id: "l1", status: "CANCELLED", cancelReason: "Feriado" }), lesson({ id: "l2", changed: true, title: "Judô Adulto" }), lesson({ id: "l3", adhoc: true, title: "Seminário" })],
      }),
    );
    view();
    expect(await screen.findByText("Cancelada")).toBeInTheDocument();
    expect(screen.getByText("Motivo: Feriado")).toBeInTheDocument();
    expect(screen.getByText("Alterada")).toBeInTheDocument();
    expect(screen.getByText("Avulsa")).toBeInTheDocument();
    expect(screen.getAllByText("28/10/2026 · 19:00 às 20:00")).toHaveLength(3);
  });

  it("CA8: com duas combinações de modalidade e unidade há etiqueta e filtro; com uma só, nenhum dos dois", async () => {
    serve(
      home({
        charges: { items: [charge(), charge({ id: "c2", description: "Faixa nova", modality: judo, unit: norte })], nextDueOn: null },
        lessons: [lesson(), lesson({ id: "l2", title: "Judô Adulto", modality: judo, unit: norte })],
        belts: [belt(), belt({ modality: judo, levelName: "Amarela", degree: 0 })],
      }),
    );
    const user = userEvent.setup();
    const { unmount } = view();
    const filter = await screen.findByLabelText("Filtrar por modalidade e unidade");
    expect(screen.getAllByText("Judô · Norte")).toHaveLength(3); // a opção do filtro, a cobrança e a aula
    expect(screen.getByText("Amarela, grau 0")).toBeInTheDocument();
    await user.selectOptions(filter, "m1|u1");
    expect(screen.queryByText("Faixa nova")).not.toBeInTheDocument();
    expect(screen.queryByText("Judô Adulto")).not.toBeInTheDocument();
    expect(screen.queryByText("Amarela, grau 0")).not.toBeInTheDocument();
    expect(screen.getByText("Kimono")).toBeInTheDocument();
    expect(screen.getByText("Azul, grau 2")).toBeInTheDocument();
    unmount();

    serve(home());
    view();
    await screen.findByText("Kimono");
    expect(screen.queryByLabelText("Filtrar por modalidade e unidade")).not.toBeInTheDocument();
    expect(screen.queryByText("Jiu-Jitsu · Centro")).not.toBeInTheDocument();
  });

  it("CA9: nada de presença, frequência nem progresso na tela", async () => {
    serve(home());
    const { container } = view();
    await screen.findByText("Kimono");
    expect(container.textContent).not.toMatch(/presen|frequ|progress/i);
  });

  it("CA10, CA11: responsável vê os cartões e o total da família; trocar de cartão refaz a leitura; adulto sem filhos não vê a faixa", async () => {
    const seen: (string | null)[] = [];
    const family = {
      students: [
        { id: "s1", name: "Ana Silva", photoUrl: null, self: true, openCents: 10000 },
        { id: "s2", name: "Bia Silva", photoUrl: null, self: false, openCents: 5000 },
      ],
      familyOpenCents: 15000,
    };
    server.use(
      http.get("/api/v1/portal/home", ({ request }) => {
        const id = new URL(request.url).searchParams.get("studentId");
        seen.push(id);
        return HttpResponse.json(id === "s2" ? home({ ...family, selectedStudentId: "s2", charges: { items: [charge({ id: "c9", description: "Mensalidade da Bia", amountCents: 5000 })], nextDueOn: null } }) : home({ ...family }));
      }),
    );
    const user = userEvent.setup();
    const { unmount } = view();
    const strip = await screen.findByRole("region", { name: "Alunos da família" });
    expect(within(strip).getByRole("button", { name: /^Você/ })).toHaveAttribute("aria-pressed", "true");
    expect(within(strip).getByText("Total da família em aberto: R$ 150,00")).toBeInTheDocument();
    await user.click(within(strip).getByRole("button", { name: /^Bia Silva/ }));
    expect(await screen.findByText("Mensalidade da Bia")).toBeInTheDocument();
    expect(seen).toEqual([null, "s2"]);
    expect(screen.getByRole("button", { name: /^Bia Silva/ })).toHaveAttribute("aria-pressed", "true");
    unmount();

    serve(home());
    view();
    await screen.findByText("Kimono");
    expect(screen.queryByRole("region", { name: "Alunos da família" })).not.toBeInTheDocument();
  });

  it("CA12: portal reduzido mostra só a cobrança", async () => {
    serve(home({ reduced: true, lessons: [], belts: [], announcements: { unreadCount: 0, items: [] } }));
    view();
    await screen.findByText("Kimono");
    expect(screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent)).toEqual(["Cobrança"]);
  });

  it("CA15: carregando, erro com Tentar de novo, sem permissão e seções vazias", async () => {
    let calls = 0;
    serve(() => (++calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(home({ lessons: [], belts: [], announcements: { unreadCount: 0, items: [] } }))));
    const user = userEvent.setup();
    const { unmount } = view();
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByText("Nenhuma aula por enquanto")).toBeInTheDocument();
    expect(screen.getByText("Nenhum aviso novo")).toBeInTheDocument();
    expect(screen.getByText("Nenhuma faixa registrada")).toBeInTheDocument();
    expect(calls).toBe(2);
    unmount();

    serve(() => new HttpResponse(null, { status: 403 }));
    view();
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  for (const dark of [false, true]) {
    it(`CA15: sem violações de acessibilidade no tema ${dark ? "escuro" : "claro"}`, async () => {
      setDevicePrefersDark(dark);
      serve(home({ charges: { items: [charge(), charge({ id: "c2", situation: "IN_REVIEW", canPay: false, modality: judo, unit: norte })], nextDueOn: null }, lessons: [lesson({ status: "CANCELLED", cancelReason: "Feriado" }), lesson({ id: "l2", changed: true, modality: judo, unit: norte })] }));
      const { container } = view();
      await screen.findByText("Em conferência");
      await waitFor(async () => expect(await axeViolations(container)).toEqual([]));
    });
  }
});
