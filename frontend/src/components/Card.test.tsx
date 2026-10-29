import { render, screen } from "@testing-library/react";
import { Button } from "@/components/ui/button";
import { Card, CardGrid, Section } from "./Card";

describe("Card (CA15)", () => {
  it("grade começa em uma coluna e o cartão tem título como cabeçalho", () => {
    const { container } = render(
      <CardGrid>
        <Card title="Alunos" actions={<Button>Ver</Button>}>
          12
        </Card>
      </CardGrid>,
    );
    expect(container.firstChild).toHaveClass("grid-cols-1", "sm:grid-cols-2", "lg:grid-cols-3");
    expect(screen.getByRole("heading", { name: "Alunos" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Alunos" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Ver" })).toBeInTheDocument();
  });

  it("CA7: Section nomeia a região e põe a ação; Card sem título é só o contêiner", () => {
    const { container } = render(
      <>
        <Section title="Dados" action={<Button>Editar</Button>}>x</Section>
        <Card>solto</Card>
      </>,
    );
    expect(screen.getByRole("region", { name: "Dados" })).toContainElement(screen.getByRole("button", { name: "Editar" }));
    expect(screen.getAllByRole("region")).toHaveLength(1);
    expect(container.lastElementChild).toHaveTextContent("solto");
    expect(container.lastElementChild).toHaveClass("rounded-card");
  });
});
