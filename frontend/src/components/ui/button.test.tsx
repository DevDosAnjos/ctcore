import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Mail } from "lucide-react";
import { Button } from "./button";
import { axeViolations } from "@/test/axe";

const VARIANTS = ["primary", "secondary", "ghost", "danger", "dangerSolid", "accent", "whatsapp", "default", "outline"] as const;
const SIZES = { sm: "h-8", md: "h-10", lg: "h-12" } as const;

describe("Button", () => {
  it.each(Object.entries(SIZES))("CA1: tamanho %s tem %s e alvo de 44 px (CA3)", (size, h) => {
    render(<Button size={size as keyof typeof SIZES}>Salvar</Button>);
    const b = screen.getByRole("button", { name: "Salvar" });
    expect(b).toHaveClass(h, "after:min-h-11", "after:min-w-11");
  });

  it.each(VARIANTS)("CA1: variante %s só usa tokens", async (variant) => {
    const { container } = render(<Button variant={variant}>Ok</Button>);
    expect(screen.getByRole("button").className).not.toMatch(/#[0-9a-f]{3,8}|-(red|gray|green|blue)-\d/i);
    expect(await axeViolations(container)).toEqual([]);
  });

  it("CA1: ícone antes do texto", () => {
    render(<Button icon={<Mail data-testid="i" />}>Enviar</Button>);
    expect(screen.getByRole("button").firstElementChild).toBe(screen.getByTestId("i"));
  });

  it("CA2: carregando não clica, anuncia e mostra indicador", async () => {
    const onClick = vi.fn();
    render(<Button loading onClick={onClick}>Salvar</Button>);
    const b = screen.getByRole("button");
    await userEvent.click(b);
    expect(onClick).not.toHaveBeenCalled();
    expect(b).toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("Carregando")).toBeInTheDocument();
  });

  it("CA2: desabilitado com motivo fica aria-disabled, com dica e sem clique", async () => {
    const onClick = vi.fn();
    render(<Button disabled disabledReason="Falta o CPF" onClick={onClick}>Salvar</Button>);
    const b = screen.getByRole("button");
    await userEvent.click(b);
    expect(onClick).not.toHaveBeenCalled();
    expect(b).toHaveAttribute("aria-disabled", "true");
    expect(b).toHaveAttribute("title", "Falta o CPF");
    expect(b).toHaveAccessibleDescription("Falta o CPF");
  });

  it("clique normal e disabled simples seguem como antes", async () => {
    const onClick = vi.fn();
    const { rerender } = render(<Button onClick={onClick}>Ir</Button>);
    await userEvent.click(screen.getByRole("button"));
    expect(onClick).toHaveBeenCalledTimes(1);
    rerender(<Button disabled>Ir</Button>);
    expect(screen.getByRole("button")).toBeDisabled();
  });
});
