import { render, screen } from "@testing-library/react";
import { Banner } from "./banner";
import { axeViolations } from "@/test/axe";

it.each([["info", "status"], ["ok", "status"], ["warn", "alert"], ["danger", "alert"]] as const)("CA19: tom %s tem role %s, ícone e texto", async (tone, role) => {
  const { container } = render(<Banner tone={tone}>Mensagem</Banner>);
  const el = screen.getByRole(role);
  expect(el).toHaveTextContent("Mensagem");
  expect(el.querySelector("svg")).not.toBeNull();
  expect(await axeViolations(container)).toEqual([]);
});
