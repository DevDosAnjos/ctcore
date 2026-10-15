import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CharCounter } from "./CharCounter";

function Wrap() {
  const [v, setV] = useState("");
  return <CharCounter aria-label="Observação" value={v} onChange={setV} max={100} />;
}

describe("CharCounter (CA16)", () => {
  it("mantém 100, mostra 100/100 e avisa ao passar de 90%", async () => {
    const user = userEvent.setup();
    render(<Wrap />);
    const campo = screen.getByRole("textbox", { name: "Observação" });
    await user.type(campo, "a".repeat(50));
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
    await user.type(campo, "a".repeat(41));
    expect(screen.getByRole("status")).toHaveTextContent("poucos caracteres");
    await user.type(campo, "b".repeat(30));
    expect((campo as HTMLTextAreaElement).value).toHaveLength(100);
    expect(screen.getByText("100/100")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("chegou ao limite");
  });
});
