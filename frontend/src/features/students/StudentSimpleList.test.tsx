import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { StudentSimpleList } from "./StudentSimpleList";
import type { StudentSimple } from "./types";

const ana: StudentSimple = { id: "s1", name: "Ana Souza", photoUrl: null, belt: "Azul", blocked: false };
const joao: StudentSimple = { id: "s2", name: "João Pereira", photoUrl: "https://files.example/joao.png", belt: null, blocked: true };

let query: URLSearchParams;
function setup(list: () => Response | Promise<Response>, props: { lessonId?: string; classId?: string } = { classId: "k1" }) {
  server.use(
    http.get("/api/v1/students/simple", ({ request }) => {
      query = new URL(request.url).searchParams;
      return list();
    }),
  );
  return render(
    <ThemeProvider>
      <StudentSimpleList {...props} />
    </ThemeProvider>,
  );
}

describe("lista simples da turma e da aula (R22, CA31, CA32)", () => {
  it("mostra só nome, foto, faixa e bloqueio; iniciais sem foto; pede pela turma", async () => {
    setup(() => HttpResponse.json([ana, joao]));
    expect(await screen.findByText("Ana Souza")).toBeInTheDocument();
    expect(query.get("classId")).toBe("k1");
    expect(screen.getByText("AS")).toBeInTheDocument();
    expect(screen.getByText("Azul")).toBeInTheDocument();
    expect(screen.getByText("Bloqueado")).toBeInTheDocument();
    expect(document.querySelector("img[src='https://files.example/joao.png']")).not.toBeNull();
  });

  it("a lista da aula pede pelo id da aula", async () => {
    setup(() => HttpResponse.json([ana]), { lessonId: "les1" });
    await screen.findByText("Ana Souza");
    expect(query.get("lessonId")).toBe("les1");
    expect(query.get("classId")).toBeNull();
  });

  it("carregando, vazio, erro com nova tentativa e sem permissão", async () => {
    const user = userEvent.setup();
    let fail = true;
    const first = setup(() => (fail ? HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 }) : HttpResponse.json([])));
    expect(screen.getByRole("status")).toHaveTextContent("Carregando");
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    fail = false;
    await user.click(retry);
    expect(await screen.findByText("Nenhum aluno ativo")).toBeInTheDocument();
    first.unmount();
    setup(() => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("a 360 px não tem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = setup(() => HttpResponse.json([ana, joao]));
    await screen.findByText("Ana Souza");
    expect(await axeViolations(container)).toEqual([]);
  });
});
