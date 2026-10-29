import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import { QuickFilters } from "./quick-filters";

describe("QuickFilters (CA12)", () => {
  it("marca a opção ativa, mostra o contador e chama onChange com a chave", async () => {
    const onChange = vi.fn();
    render(
      <QuickFilters
        value="all"
        onChange={onChange}
        options={[
          { key: "all", label: "Todos", count: 12 },
          { key: "late", label: "Em atraso", count: 3 },
        ]}
      />,
    );
    expect(screen.getByRole("button", { name: "Todos, 12" })).toHaveAttribute("aria-pressed", "true");
    const late = screen.getByRole("button", { name: "Em atraso, 3" });
    expect(late).toHaveAttribute("aria-pressed", "false");
    await userEvent.click(late);
    expect(onChange).toHaveBeenCalledWith("late");
  });
});
