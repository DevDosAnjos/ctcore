import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import ptBR from "@/i18n/pt-BR.json";
import { server } from "@/mocks/server";
import { ThemeProvider } from "@/theme/ThemeProvider";

// RF-POR-08, RNF-25: todas as rotas do portal mostram carregando, erro com retry, sem permissão e vazio.
const tr = (key: string) => key.split(".").reduce<unknown>((o, p) => (o as Record<string, unknown>)[p], ptBR) as string;

const session: SessionView = {
  account: { id: "a", email: "x@x.com", name: "Ana Silva" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role: "GUARDIAN", unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
};
const students = [{ id: "s1", name: "Ana Souza", self: true }];

// `empty` é a resposta sem itens; `emptyText` o texto do vazio da tela (nenhum: a tela não tem vazio).
const routes = [
  {
    path: "/",
    url: "/api/v1/portal/home",
    empty: {
      reduced: false,
      students: [{ ...students[0], photoUrl: null, openCents: 0 }],
      familyOpenCents: 0,
      selectedStudentId: "s1",
      charges: { items: [], nextDueOn: null },
      lessons: [],
      announcements: { unreadCount: 0, items: [] },
      belts: [],
    },
  },
  {
    path: "/portal/agenda",
    url: "/api/v1/portal/schedule",
    empty: { reduced: false, students, studentId: "s1", scope: "ALL", weekStart: "2026-10-26", weekEnd: "2026-11-01", today: "2026-10-27", timezone: "America/Sao_Paulo", lessons: [], noClassDays: [] },
    emptyText: tr("portalAgenda.emptyTitle"),
  },
  {
    path: "/portal/financeiro",
    url: "/api/v1/portal/finance",
    empty: { reduced: false, students: [{ ...students[0], photoUrl: null, openCents: 0 }], selectedStudentId: "s1", scope: "STUDENT", year: 2026, years: [2026], familyOpenCents: 0, open: [], history: [] },
    emptyText: tr("portalFinance.open.emptyTitle"),
  },
  {
    path: "/portal/avisos",
    url: "/api/v1/portal/announcements",
    empty: { reduced: false, students, studentId: "s1", filter: "ALL", modalityId: null, modalities: [], unreadCount: 0, items: [], page: 1, size: 20, total: 0 },
    emptyText: tr("portalAnnouncements.emptyTitle"),
  },
  {
    path: "/portal/carteirinha",
    url: "/api/v1/portal/card",
    empty: {
      organization: { name: "Academia Alfa", color: null, logoUrl: null },
      student: { id: "s1", name: "Ana Souza", photoUrl: null },
      students,
      status: "ACTIVE",
      today: "2026-10-27",
      generatedAt: "2026-10-27T15:30:00Z",
      timezone: "America/Sao_Paulo",
      modalities: [],
    },
    emptyText: tr("portalCard.emptyTitle"),
  },
  {
    path: "/portal/minhas-informacoes",
    url: "/api/v1/portal/info",
    empty: {
      reduced: false,
      students,
      selectedStudentId: "s1",
      personal: {
        name: "Ana Souza",
        birthDate: "2010-01-01",
        maskedCpf: null,
        photoUrl: null,
        phone: null,
        email: null,
        address: { postalCode: "01001000", street: "Rua A", number: "1", complement: null, district: "Centro", city: "São Paulo", state: "SP" },
        emergency: { name: null, relationship: null, phone: null },
        canEdit: false,
        editableFields: [],
      },
      unitContacts: [],
      trainings: [],
      plans: [],
      graduation: [],
      emailAnnouncements: true,
      terms: { enrollment: null, imageUse: { granted: false, version: null, at: null } },
    },
    emptyText: tr("portalInfo.emptyTitle"),
  },
  { path: "/portal/pedidos", url: "/api/v1/portal/students", empty: [], emptyText: tr("portalOwn.noStudentTitle") },
  { path: "/portal/termo", url: "/api/v1/portal/term", empty: [], emptyText: tr("portalTerm.emptyTitle") },
];

function setup(path: string, main: (typeof routes)[number], response: () => Response | Promise<Response>) {
  server.use(
    http.get(main.url, response),
    http.get("/api/v1/auth/session", () => HttpResponse.json(session)),
    // O portão do termo e a moldura respondem bem; só a busca principal da rota muda.
    http.get("/api/v1/portal/term", () => HttpResponse.json([])),
    http.get("/api/v1/portal/students", () => HttpResponse.json([])),
    http.get("/api/v1/portal/modality-confirmations", () => HttpResponse.json([])),
    http.get("/api/v1/portal/shell", () => HttpResponse.json({ organization: { name: "Academia Alfa", color: null, logoUrl: null }, reduced: false, unreadAnnouncements: 0 })),
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

describe("portal: estados de tela em todas as rotas (RF-POR-08, R6)", () => {
  for (const route of routes) {
    describe(route.path, () => {
      it("carregando enquanto busca", async () => {
        setup(route.path, route, () => new Promise<Response>(() => {}));
        expect(await screen.findByRole("status")).toHaveTextContent(tr("states.loading"));
      });

      it("erro com Tentar de novo, que refaz a busca", async () => {
        let fail = true;
        setup(route.path, route, () => (fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(route.empty)));
        const retry = await screen.findByRole("button", { name: tr("states.retry") });
        fail = false;
        await userEvent.click(retry);
        await vi.waitFor(() => expect(screen.queryByRole("button", { name: tr("states.retry") })).not.toBeInTheDocument());
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        if (route.emptyText) await vi.waitFor(() => expect(screen.getByText(route.emptyText!)).toBeInTheDocument(), { timeout: 5000 });
      });

      it("sem permissão no 403, sem dado da resposta", async () => {
        setup(route.path, route, () => HttpResponse.json({ message: "SEGREDO-FICTICIO" }, { status: 403 }));
        expect(await screen.findByRole("heading", { name: tr("states.forbiddenTitle") })).toBeInTheDocument();
        expect(screen.queryByText(/SEGREDO-FICTICIO/)).not.toBeInTheDocument();
      });

      if (route.emptyText)
        it("vazio com título próprio", async () => {
          setup(route.path, route, () => HttpResponse.json(route.empty));
          await vi.waitFor(() => expect(screen.getByText(route.emptyText!)).toBeInTheDocument(), { timeout: 5000 });
        });
    });
  }
});
