import { render, screen } from "@testing-library/react";
import { Stat } from "./stat";

it("CA6: valor na fonte de títulos e nota com tom e texto", () => {
  render(<Stat label="Em dia" value="92%" note="Subiu 3 pontos" tone="ok" />);
  expect(screen.getByText("92%")).toHaveClass("font-heading");
  expect(screen.getByText("Subiu 3 pontos")).toHaveClass("text-ok-foreground");
});
