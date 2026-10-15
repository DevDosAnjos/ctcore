import { render, screen } from "@testing-library/react";
import { Button } from "@/components/ui/button";
import { Card, CardGrid } from "./Card";

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
});
