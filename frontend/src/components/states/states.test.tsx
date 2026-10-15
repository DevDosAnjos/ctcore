import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from ".";

describe("componentes de estado (CA3)", () => {
  it("carregando tem role=status", () => {
    render(<LoadingState />);
    expect(screen.getByRole("status")).toHaveTextContent("Carregando...");
  });

  it("vazio mostra uma frase e a ação principal", () => {
    render(<EmptyState action={<button>Criar</button>} />);
    expect(screen.getByText("Nada por aqui ainda")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Criar" })).toBeInTheDocument();
  });

  it("erro mostra as três partes e chama Tentar de novo", async () => {
    const onRetry = vi.fn();
    render(<ErrorState onRetry={onRetry} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Nada foi alterado nos seus dados.");
    expect(screen.getByText("Não conseguimos carregar as informações agora.")).toBeInTheDocument();
    expect(screen.getByText(/Tente de novo em instantes/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Tentar de novo" }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("sem permissão não mostra conteúdo", () => {
    render(<ForbiddenState />);
    expect(screen.getByText("Você não tem acesso")).toBeInTheDocument();
  });
});
