import { render, screen } from "@testing-library/react";
import { SummaryBox } from "./summary-box";

it("CA19: itens em lista com seta", () => {
  render(<SummaryBox items={["Cancela a matrícula", "Mantém o histórico"]} />);
  expect(screen.getAllByRole("listitem")).toHaveLength(2);
  expect(screen.getByRole("list").querySelectorAll("svg")).toHaveLength(2);
});
