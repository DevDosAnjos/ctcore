import { render, screen } from "@testing-library/react";
import { PageHeader } from "./page-header";

it("CA20: h1 em caixa alta por CSS, subtítulo e ações", () => {
  render(<PageHeader title="Alunos" subtitle="Todos" actions={<button>Novo</button>} />);
  const h = screen.getByRole("heading", { level: 1, name: "Alunos" });
  expect(h).toHaveClass("uppercase");
  expect(h.textContent).toBe("Alunos");
  expect(screen.getByText("Todos")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Novo" })).toBeInTheDocument();
});
