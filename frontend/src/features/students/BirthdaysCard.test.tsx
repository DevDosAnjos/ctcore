import { render, screen, waitFor } from "@testing-library/react";
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
import type { StudentBirthday } from "./types";

type Role = SessionView["links"][number]["role"];
const session = (role: Role): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const adult: StudentBirthday = {
  id: "s1", name: "Ana Souza", photoUrl: null, age: 35, ageRange: { id: "r1", name: "Adulto" }, registeredUnit: { id: "u1", name: "Centro" },
  contactName: "Ana Souza", contactPhone: "21912345678", contactIsGuardian: false,
};
const minor: StudentBirthday = {
  id: "s2", name: "Pedro Lima", photoUrl: null, age: 10, ageRange: { id: "r2", name: "Infantil" }, registeredUnit: { id: "u1", name: "Centro" },
  contactName: "Marta Lima", contactPhone: "21987654321", contactIsGuardian: true,
};
const noPhone: StudentBirthday = { ...adult, id: "s3", name: "Zeca Sem Fone", contactName: null, contactPhone: null };

function setup(role: Role, birthdays: () => Response | Promise<Response>) {
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role as "COORDINATOR") })),
    http.get("/api/v1/units/reachable", () => HttpResponse.json({ allUnits: true, units: [] })),
    http.get("/api/v1/students/birthdays", birthdays),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe("cartão Aniversariantes de hoje (RF-ALU-06)", () => {
  beforeEach(() => localStorage.clear());

  it("CA7: nome, idade, faixa e WhatsApp com a mensagem do aluno ou do responsável; sem telefone não há botão", async () => {
    setup("ADMINISTRATOR", () => HttpResponse.json([adult, minor, noPhone]));
    expect(await screen.findByText("Ana Souza")).toBeInTheDocument();
    expect(screen.getAllByText("35 anos · Adulto")).toHaveLength(2);
    expect(screen.getAllByText("AS")).toHaveLength(1);
    const links = screen.getAllByRole("link", { name: /WhatsApp/ });
    expect(links).toHaveLength(2);
    expect(links[0].getAttribute("href")).toBe(
      `https://wa.me/5521912345678?text=${encodeURIComponent("Parabéns, Ana Souza! A equipe da unidade Centro deseja a você um feliz aniversário!")}`,
    );
    expect(links[1].getAttribute("href")).toBe(
      `https://wa.me/5521987654321?text=${encodeURIComponent("Olá, Marta Lima! Hoje é aniversário de Pedro Lima. A equipe da unidade Centro deseja a ele(a) um feliz aniversário!")}`,
    );
  });

  it("CA8: Professor vê o cartão; Coordenador não", async () => {
    const { unmount } = setup("TEACHER", () => HttpResponse.json([]));
    expect(await screen.findByRole("heading", { name: "Aniversariantes de hoje" })).toBeInTheDocument();
    unmount();
    setup("COORDINATOR", () => HttpResponse.json([]));
    await screen.findByRole("heading", { level: 1 });
    expect(screen.queryByRole("heading", { name: "Aniversariantes de hoje" })).toBeNull();
  });

  it("CA9: erro com Tentar de novo, vazio e sem permissão", async () => {
    const user = userEvent.setup();
    let fail = true;
    const { unmount } = setup("ADMINISTRATOR", () => (fail ? HttpResponse.json({ code: "X" }, { status: 500 }) : HttpResponse.json([])));
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
    fail = false;
    await user.click(screen.getByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByText("Ninguém faz aniversário hoje")).toBeInTheDocument();
    unmount();
    setup("ADMINISTRATOR", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA9: mostra o carregamento enquanto espera", async () => {
    setup("ADMINISTRATOR", () => new Promise<Response>(() => undefined));
    await waitFor(() => expect(screen.getAllByText("Carregando...").length).toBeGreaterThan(0));
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("CA9: a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    setup("ADMINISTRATOR", () => HttpResponse.json([adult, minor]));
    await screen.findByText("Pedro Lima");
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
