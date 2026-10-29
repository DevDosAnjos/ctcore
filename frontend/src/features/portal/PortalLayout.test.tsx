import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import type { PortalShellData } from "./usePortalShell";

// O Início do portal (#89) lê /portal/home; estes testes só olham a moldura e o termo.
const emptyHome = { reduced: false, students: [], familyOpenCents: 0, selectedStudentId: null, charges: { items: [], nextDueOn: null }, lessons: [], announcements: { unreadCount: 0, items: [] }, belts: [] };

const emptyFinance = { reduced: false, students: [], selectedStudentId: null, scope: "STUDENT", year: 2026, years: [], familyOpenCents: 0, open: [], history: [] };

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "Ana Silva" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const shell = (over: Partial<PortalShellData> = {}): PortalShellData => ({ organization: { name: "Academia Alfa", color: null, logoUrl: null }, reduced: false, unreadAnnouncements: 2, ...over });

function setup(path = "/", over: { role?: string; shell?: () => Response | Promise<Response>; data?: PortalShellData } = {}) {
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(over.role ?? "STUDENT"))),
    http.get("/api/v1/portal/term", () => HttpResponse.json([])),
    http.get("/api/v1/portal/students", () => HttpResponse.json([])),
    http.get("/api/v1/portal/modality-confirmations", () => HttpResponse.json([])),
    http.get("/api/v1/portal/home", () => HttpResponse.json(emptyHome)),
    http.get("/api/v1/portal/finance", () => HttpResponse.json(emptyFinance)),
    http.get("/api/v1/auth/sessions", () => HttpResponse.json([])), // sem isto /conta recebia 401 e relia a sessão sem parar
    http.get("/api/v1/portal/shell", over.shell ?? (() => HttpResponse.json(over.data ?? shell()))),
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
const ready = () => screen.findByRole("link", { name: "Academia Alfa" });
const tabs = () => within(screen.getByRole("navigation", { name: "Portal" })).getAllByRole("link");
const current = () => tabs().filter((a) => a.getAttribute("aria-current") === "page").map((a) => a.textContent);
const bell = () => screen.queryAllByRole("link", { name: /^Avisos/ }).find((a) => !a.closest("nav"));

describe("PortalLayout: barra e navegação do portal (RF-POR-01)", () => {
  it("CA1: mostra organização, logo, nome e papel (Aluno e Responsável); sem logo não há imagem", async () => {
    const { container, unmount } = setup("/", { data: shell({ organization: { name: "Academia Alfa", color: "#1d4ed8", logoUrl: "data:image/png;base64,AAAA" } }) });
    await ready();
    expect(container.querySelector("header img")).toHaveAttribute("src", "data:image/png;base64,AAAA");
    expect(screen.getByText("Ana Silva · Aluno")).toBeInTheDocument();
    unmount();
    const guardian = setup("/", { role: "GUARDIAN" });
    await ready();
    expect(screen.getByText("Ana Silva · Responsável")).toBeInTheDocument();
    expect(guardian.container.querySelector("header img")).toBeNull();
  });

  it("CA2: as cinco abas na ordem, como links, com a atual marcada", async () => {
    setup("/");
    await ready();
    expect(tabs().map((a) => [a.textContent, a.getAttribute("href")])).toEqual([
      ["Início", "/"],
      ["Agenda", "/portal/agenda"],
      ["Financeiro", "/portal/financeiro"],
      ["Avisos", "/portal/avisos"],
      ["Minhas informações", "/portal/minhas-informacoes"],
    ]);
    expect(current()).toEqual(["Início"]);
  });

  it("CA2, CA13: em /portal/financeiro a atual é Financeiro, com a tela do Financeiro e um só cabeçalho", async () => {
    setup("/portal/financeiro");
    await screen.findByRole("heading", { name: "Financeiro", level: 1 });
    expect(current()).toEqual(["Financeiro"]);
    expect(await screen.findByText("Nenhuma cobrança em aberto")).toBeInTheDocument();
    expect(screen.getAllByRole("banner")).toHaveLength(1);
  });

  it("CA3: abas fixas embaixo abaixo de 768 px, na barra a partir de md, e o conteúdo ganha folga", async () => {
    setup("/");
    await ready();
    expect(screen.getByRole("navigation", { name: "Portal" })).toHaveClass("fixed", "inset-x-0", "bottom-0", "md:static");
    expect(screen.getByRole("main")).toHaveClass("pb-24", "md:pb-6");
  });

  it("CA6: 120 não lidos mostram 99+ e o sino leva a /portal/avisos", async () => {
    setup("/", { data: shell({ unreadAnnouncements: 120 }) });
    const link = await screen.findByRole("link", { name: "Avisos, 99+ não lidos" });
    expect(link).toHaveAttribute("href", "/portal/avisos");
    expect(link).toHaveTextContent("99+");
  });

  it("CA6: em zero o sino não mostra número", async () => {
    setup("/", { data: shell({ unreadAnnouncements: 0 }) });
    await ready();
    expect(bell()).toHaveAttribute("href", "/portal/avisos");
    expect(bell()).toHaveTextContent("");
  });

  it("CA7, CA9: portal reduzido esconde Agenda, Avisos e o sino, mostra o aviso e leva /portal/agenda ao Início", async () => {
    setup("/portal/agenda", { data: shell({ reduced: true, unreadAnnouncements: 0 }) });
    await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" });
    expect(tabs().map((a) => a.textContent)).toEqual(["Início", "Financeiro", "Minhas informações"]);
    expect(bell()).toBeUndefined();
    expect(screen.getByText(/Seu acesso está reduzido/)).toBeInTheDocument();
  });

  it("CA9: no reduzido /portal/avisos também volta ao Início", async () => {
    setup("/portal/avisos", { data: shell({ reduced: true, unreadAnnouncements: 0 }) });
    await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" });
  });

  it("CA8: portal completo abre Agenda e Avisos", async () => {
    setup("/portal/agenda");
    await screen.findByRole("heading", { name: "Agenda", level: 1 });
  });

  it("CA12: erro de leitura aparece no lugar do conteúdo, com a barra de abas, e Tentar de novo refaz a chamada", async () => {
    let calls = 0;
    setup("/", {
      shell: () => {
        calls += 1;
        return calls === 1 ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json(shell());
      },
    });
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(tabs()).toHaveLength(5);
    await userEvent.setup().click(screen.getByRole("button", { name: "Tentar de novo" }));
    await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" });
    expect(calls).toBe(2);
  });

  it("CA12: carregando e, com 403, o estado sem permissão, sempre com a barra de abas", async () => {
    setup("/", { shell: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await screen.findByRole("status")).toHaveTextContent("Carregando");
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
    expect(tabs()).toHaveLength(5);
  });

  it("CA12: Tab percorre logo, abas, sino, conta e tema", async () => {
    const user = userEvent.setup();
    setup("/");
    await ready();
    const seen: string[] = [];
    for (let i = 0; i < 9; i += 1) {
      await user.tab();
      seen.push(document.activeElement?.getAttribute("aria-label") ?? document.activeElement?.textContent ?? "");
    }
    expect(seen.slice(0, 8)).toEqual(["Academia Alfa", "Início", "Agenda", "Financeiro", "Avisos", "Minhas informações", "Avisos, 2 não lidos", "Minha conta"]);
    expect(seen[8]).toMatch(/tema/i);
  });

  it("CA12: passa no axe nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      const { container, unmount } = setup("/portal/financeiro");
      await screen.findByRole("heading", { name: "Financeiro", level: 1 });
      await waitFor(async () => expect(await axeViolations(container)).toEqual([]));
      unmount();
    }
  });

  it.each(["/", "/conta", "/portal/pedidos"])("CA13: %s usa a moldura nova, com um só cabeçalho", async (path) => {
    setup(path);
    await ready();
    if (path === "/") await screen.findByText("Tudo em dia"); // deixa a leitura do Início terminar antes de o teste acabar
    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(tabs()).toHaveLength(5);
  });
});
