import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter, useLocation } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import type { AnnouncementDetail, AnnouncementItem, PortalAnnouncementsData } from "./usePortalAnnouncements";

const session: SessionView = {
  account: { id: "a", email: "x@x.com", name: "Ana Silva" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role: "GUARDIAN", unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
};
const item = (over: Partial<AnnouncementItem>): AnnouncementItem => ({
  id: "n0",
  title: "Aviso",
  type: "GENERAL",
  sentAt: "2026-10-25T12:00:00Z",
  pinned: false,
  unread: false,
  destination: { kind: "ORGANIZATION", names: [] },
  author: "Coordenação",
  ...over,
});
const data = (over: Partial<PortalAnnouncementsData> = {}): PortalAnnouncementsData => ({
  reduced: false,
  students: [{ id: "s1", name: "Ana Souza", self: true }],
  studentId: "s1",
  filter: "ALL",
  modalityId: null,
  modalities: [{ id: "m1", name: "Jiu-Jitsu" }],
  unreadCount: 2,
  items: [
    item({ id: "n1", title: "Mudança de <b>horário</b>", pinned: true, unread: true, destination: { kind: "UNIT", names: ["Unidade Centro"] } }),
    item({ id: "n2", title: "Exame de faixa", unread: true, destination: { kind: "CLASSES", names: ["Turma A", "Turma B"] }, author: "Prof. Beto" }),
    item({ id: "n3", title: "Recesso", sentAt: "2026-10-20T12:00:00Z" }),
  ],
  page: 1,
  size: 20,
  total: 3,
  ...over,
});
const detail = (over: Partial<AnnouncementDetail> = {}): AnnouncementDetail => ({ ...item({ id: "n1", title: "Mudança de <b>horário</b>" }), body: "Linha um\nLinha <i>dois</i>", imageUrl: null, ...over });
const serve = (view: PortalAnnouncementsData | ((info: { request: Request }) => Response | Promise<Response>)) =>
  server.use(http.get("/api/v1/portal/announcements", typeof view === "function" ? view : () => HttpResponse.json(view)));

function Probe() {
  const l = useLocation();
  return <span data-testid="where" hidden>{l.pathname + l.search}</span>;
}
const where = () => screen.getByTestId("where").textContent;

function setup(path = "/portal/avisos") {
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session)),
    http.get("/api/v1/portal/term", () => HttpResponse.json([])),
    http.get("/api/v1/portal/students", () => HttpResponse.json([])),
    http.get("/api/v1/portal/shell", () => HttpResponse.json({ organization: { name: "Academia Alfa", color: null, logoUrl: null }, reduced: false, unreadAnnouncements: 2 })),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <Probe />
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe("PortalAnnouncementsPage (RF-POR-06)", () => {
  it("CA12: lista com Fixado, Não lido, título como texto, data, destino e autor; lido sem etiqueta", async () => {
    serve(data());
    setup();
    const list = await screen.findByRole("list", { name: "Lista de avisos" });
    const first = within(list).getAllByRole("listitem")[0];
    expect(within(first).getByText("Fixado")).toBeInTheDocument();
    expect(within(first).getByText("Não lido")).toBeInTheDocument();
    expect(within(first).getByText("Mudança de <b>horário</b>")).toBeInTheDocument();
    expect(within(first).getByText("Enviado em 25/10/2026")).toBeInTheDocument();
    expect(within(first).getByText("Para: Unidade Centro")).toBeInTheDocument();
    expect(within(first).getByText("Por Coordenação")).toBeInTheDocument();
    expect(within(list).getByText("Para: turmas Turma A, Turma B")).toBeInTheDocument();
    const last = within(list).getAllByRole("listitem")[2];
    expect(within(last).queryByText("Não lido")).not.toBeInTheDocument();
    expect(within(last).getByText("Para: toda a academia")).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/presen|frequ|progress|R\$/i);
  });

  it("CA12: clicar abre o diálogo com o texto completo, grava a leitura e relê a lista e o sino; fechar tira ?aviso=", async () => {
    let opens = 0;
    let reads = 0;
    let shells = 0;
    serve(() => {
      reads += 1;
      return HttpResponse.json(opens ? data({ unreadCount: 1, items: data().items.map((i) => (i.id === "n1" ? { ...i, unread: false } : i)) }) : data());
    });
    server.use(
      http.post("/api/v1/portal/announcements/n1/open", () => {
        opens += 1;
        return HttpResponse.json(detail({ imageUrl: "https://files.example/aviso.png" }));
      }),
    );
    const user = userEvent.setup();
    setup();
    server.use(
      http.get("/api/v1/portal/shell", () => {
        shells += 1;
        return HttpResponse.json({ organization: { name: "Academia Alfa", color: null, logoUrl: null }, reduced: false, unreadAnnouncements: 1 });
      }),
    );
    await user.click(await screen.findByRole("button", { name: /Mudança de <b>horário<\/b>/ }));
    const dialog = await screen.findByRole("dialog", { name: "Mudança de <b>horário</b>" });
    expect(where()).toBe("/portal/avisos?aviso=n1");
    expect(await within(dialog).findByText(/Linha um/)).toHaveTextContent("Linha <i>dois</i>");
    expect(within(dialog).getByRole("img", { name: "Imagem do aviso" })).toHaveAttribute("src", "https://files.example/aviso.png");
    await waitFor(() => expect(reads).toBe(2));
    await waitFor(() => expect(shells).toBeGreaterThan(0));
    expect(opens).toBe(1);
    await waitFor(() => expect(within(screen.getAllByRole("listitem", { hidden: true })[0]).queryByText("Não lido")).not.toBeInTheDocument());

    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(where()).toBe("/portal/avisos");
  });

  it("CA12: ?aviso= abre o diálogo direto, com o aluno da URL", async () => {
    const asked: string[] = [];
    serve(data());
    server.use(
      http.post("/api/v1/portal/announcements/n2/open", ({ request }) => {
        asked.push(new URL(request.url).search);
        return HttpResponse.json(detail({ id: "n2", title: "Exame de faixa" }));
      }),
    );
    setup("/portal/avisos?aluno=s1&aviso=n2");
    expect(await screen.findByRole("dialog", { name: "Exame de faixa" })).toBeInTheDocument();
    expect(asked).toEqual(["?studentId=s1"]);
  });

  it("aviso que deixou de existir mostra o estado indisponível na janela", async () => {
    serve(data());
    server.use(http.post("/api/v1/portal/announcements/n9/open", () => new HttpResponse(null, { status: 404 })));
    setup("/portal/avisos?aviso=n9");
    expect(await screen.findByText("Aviso indisponível")).toBeInTheDocument();
  });

  it("CA13: Não lidos, Por modalidade (escolhendo a modalidade) e Todos refazem a leitura e mudam ?filtro=; trocar de aluno volta à página 1", async () => {
    const asked: string[] = [];
    serve(({ request }) => {
      asked.push(new URL(request.url).search);
      return HttpResponse.json(
        data({
          students: [
            { id: "s1", name: "Ana Souza", self: true },
            { id: "s2", name: "Beto Souza", self: false },
          ],
          modalities: [
            { id: "m1", name: "Jiu-Jitsu" },
            { id: "m2", name: "Judô" },
          ],
        }),
      );
    });
    const user = userEvent.setup();
    setup("/portal/avisos?pagina=2");
    await screen.findByRole("list", { name: "Lista de avisos" });
    expect(asked.at(-1)).toBe("?page=2");
    await user.click(screen.getByRole("button", { name: "Não lidos" }));
    await waitFor(() => expect(asked.at(-1)).toBe("?filter=UNREAD"));
    expect(where()).toBe("/portal/avisos?filtro=nao-lidos");
    expect(screen.getByRole("button", { name: "Não lidos" })).toHaveAttribute("aria-pressed", "true");

    await user.click(screen.getByRole("button", { name: "Por modalidade" }));
    await waitFor(() => expect(asked.at(-1)).toBe("?filter=MODALITY&modalityId=m1"));
    await user.selectOptions(screen.getByLabelText("Modalidade"), "m2");
    await waitFor(() => expect(asked.at(-1)).toBe("?filter=MODALITY&modalityId=m2"));
    expect(where()).toBe("/portal/avisos?filtro=modalidade&modalidade=m2");

    await user.selectOptions(screen.getByLabelText("Aluno"), "s2");
    await waitFor(() => expect(asked.at(-1)).toBe("?studentId=s2"));
    expect(where()).toBe("/portal/avisos?aluno=s2");

    await user.click(screen.getByRole("button", { name: "Não lidos" }));
    await user.click(screen.getByRole("button", { name: "Todos" }));
    await waitFor(() => expect(asked.at(-1)).toBe("?studentId=s2"));
    expect(screen.getByRole("button", { name: "Todos" })).toHaveAttribute("aria-pressed", "true");
  });

  it("CA13: Marcar todos como lidos limpa, relê e fica desligado sem não lidos", async () => {
    let cleared = 0;
    serve(() => HttpResponse.json(cleared ? data({ unreadCount: 0, items: data().items.map((i) => ({ ...i, unread: false })) }) : data()));
    server.use(
      http.post("/api/v1/portal/announcements/read-all", () => {
        cleared += 1;
        return HttpResponse.json({ dismissed: 2 });
      }),
    );
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole("button", { name: "Marcar todos como lidos" }));
    expect(await screen.findByText("2 avisos marcados como lidos.")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("button", { name: "Marcar todos como lidos" })).toBeDisabled());
    expect(screen.queryByText("Não lido")).not.toBeInTheDocument();
    expect(cleared).toBe(1);
  });

  it("Marcar todos que falha mostra o erro", async () => {
    serve(data());
    server.use(http.post("/api/v1/portal/announcements/read-all", () => new HttpResponse(null, { status: 500 })));
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole("button", { name: "Marcar todos como lidos" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível marcar os avisos como lidos");
  });

  it("paginação: Próxima página e Página anterior mudam ?pagina=", async () => {
    const asked: string[] = [];
    serve(({ request }) => {
      asked.push(new URL(request.url).search);
      return HttpResponse.json(data({ total: 45 }));
    });
    const user = userEvent.setup();
    setup();
    await screen.findByText("Página 1 de 3");
    expect(screen.getByRole("button", { name: "Página anterior" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Próxima página" }));
    await waitFor(() => expect(asked.at(-1)).toBe("?page=2"));
    expect(where()).toBe("/portal/avisos?pagina=2");
    await user.click(await screen.findByRole("button", { name: "Página anterior" }));
    await waitFor(() => expect(where()).toBe("/portal/avisos"));
  });

  it("CA15: vazio (sem avisos e sem não lidos), carregando, erro com Tentar de novo e sem permissão", async () => {
    serve(data({ items: [], total: 0, unreadCount: 0 }));
    const empty = setup();
    expect(await screen.findByText("Nenhum aviso")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Marcar todos como lidos" })).toBeDisabled();
    empty.unmount();

    serve(data({ items: [], total: 0, unreadCount: 0, filter: "UNREAD" }));
    const unread = setup("/portal/avisos?filtro=nao-lidos");
    expect(await screen.findByText("Nenhum aviso não lido")).toBeInTheDocument();
    unread.unmount();

    serve(() => new Promise<Response>(() => {}));
    const loading = setup();
    expect((await screen.findAllByRole("status")).length).toBeGreaterThan(0);
    loading.unmount();

    let calls = 0;
    serve(() => (++calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(data())));
    const user = userEvent.setup();
    const failed = setup();
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByRole("list", { name: "Lista de avisos" })).toBeInTheDocument();
    expect(calls).toBe(2);
    failed.unmount();

    serve(() => new HttpResponse(null, { status: 403 }));
    setup();
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  for (const dark of [false, true]) {
    it(`CA15: sem violações de acessibilidade no tema ${dark ? "escuro" : "claro"}, com a lista e com o aviso aberto`, async () => {
      setDevicePrefersDark(dark);
      serve(data());
      server.use(http.post("/api/v1/portal/announcements/n1/open", () => HttpResponse.json(detail())));
      const { container } = setup();
      await screen.findByRole("list", { name: "Lista de avisos" });
      await waitFor(async () => expect(await axeViolations(container)).toEqual([]));
      const user = userEvent.setup();
      await user.click(screen.getAllByRole("button", { name: /Mudança de/ })[0]);
      const dialog = await screen.findByRole("dialog");
      await within(dialog).findByText(/Linha um/);
      await waitFor(async () => expect(await axeViolations(document.body)).toEqual([]));
    });
  }
});
