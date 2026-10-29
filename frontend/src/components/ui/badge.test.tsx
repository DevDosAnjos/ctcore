import { render, screen } from "@testing-library/react";
import { Badge, StatusBadge } from "./badge";

describe("Badge (CA5)", () => {
  it("StatusBadge OVERDUE usa danger, o rótulo recebido e 22 px", () => {
    render(<StatusBadge status="OVERDUE" label="Em atraso" />);
    const b = screen.getByText("Em atraso");
    expect(b).toHaveAttribute("data-tone", "danger");
    expect(b).toHaveClass("h-[22px]", "bg-danger");
  });
  it("Badge usa o tom pedido", () => {
    render(<Badge tone="info">Bolsista</Badge>);
    expect(screen.getByText("Bolsista")).toHaveClass("bg-info", "text-info-foreground");
  });
});
