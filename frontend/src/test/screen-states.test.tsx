import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { ExampleScreen } from "./ExampleScreen";

const url = "http://localhost:3000/api/v1/example-items";
const allowed = { canView: true };

describe("quatro estados de tela (CA11)", () => {
  it("carregando enquanto a rota demora", async () => {
    server.use(
      http.get(url, async () => {
        await delay(150);
        return HttpResponse.json(["Item A"]);
      }),
    );
    render(<ExampleScreen profile={allowed} />);
    expect(screen.getByText("Carregando...")).toBeInTheDocument();
    expect(await screen.findByText("Item A")).toBeInTheDocument();
  });

  it("vazio", async () => {
    server.use(http.get(url, () => HttpResponse.json([])));
    render(<ExampleScreen profile={allowed} />);
    expect(await screen.findByText("Nada por aqui ainda")).toBeInTheDocument();
  });

  it("erro 500 com ação de tentar de novo", async () => {
    server.use(http.get(url, () => new HttpResponse(null, { status: 500 })));
    render(<ExampleScreen profile={allowed} />);
    expect(await screen.findByText("Não foi possível carregar")).toBeInTheDocument();
    server.use(http.get(url, () => HttpResponse.json(["Item B"])));
    await userEvent.setup().click(screen.getByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByText("Item B")).toBeInTheDocument();
  });

  it("sem permissão (403)", async () => {
    server.use(http.get(url, () => new HttpResponse(null, { status: 403 })));
    render(<ExampleScreen profile={allowed} />);
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });
});

describe("perfis (CA12)", () => {
  it("conteúdo restrito só aparece para o perfil permitido", async () => {
    server.use(http.get(url, () => HttpResponse.json(["Segredo"])));
    const { unmount } = render(<ExampleScreen profile={allowed} />);
    expect(await screen.findByText("Segredo")).toBeInTheDocument();
    unmount();

    render(<ExampleScreen profile={{ canView: false }} />);
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
    expect(screen.queryByText("Segredo")).not.toBeInTheDocument();
  });
});
