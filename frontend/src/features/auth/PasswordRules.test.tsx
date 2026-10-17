import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { PasswordFields } from "./PasswordFields";
import { checkPassword, isStrongPassword } from "./PasswordRules";

function Harness() {
  const [p, setP] = useState("");
  const [c, setC] = useState("");
  return <PasswordFields password={p} confirm={c} onPassword={setP} onConfirm={setC} />;
}

describe("checkPassword (RN-ACE-08)", () => {
  it.each([
    ["", { length: false, number: false, upper: false }],
    ["abcdefgh", { length: true, number: false, upper: false }],
    ["Abc1", { length: false, number: true, upper: true }],
    ["Senha123", { length: true, number: true, upper: true }],
    ["Ágata123", { length: true, number: true, upper: true }],
  ])("%s", (p, expected) => {
    expect(checkPassword(p)).toEqual(expected);
  });

  it("isStrongPassword exige as três", () => {
    expect(isStrongPassword("Senha123")).toBe(true);
    expect(isStrongPassword("senha123")).toBe(false);
  });
});

describe("PasswordFields (CA3, CA4)", () => {
  it("marca cada exigência a cada tecla, com texto", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const rule = (name: string) => screen.getByText(new RegExp(name));
    expect(rule("8 ou mais")).toHaveTextContent("pendente");
    await user.type(screen.getByLabelText("Senha"), "Abc");
    expect(rule("1 letra maiúscula")).toHaveTextContent("atendida");
    expect(rule("1 número")).toHaveTextContent("pendente");
    await user.type(screen.getByLabelText("Senha"), "12345");
    expect(rule("8 ou mais")).toHaveTextContent("atendida");
    expect(rule("1 número")).toHaveTextContent("atendida");
  });

  it("confirmação diferente mostra o aviso; igual some", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.type(screen.getByLabelText("Senha"), "Senha123");
    await user.type(screen.getByLabelText("Confirmar senha"), "Senha12");
    expect(screen.getByText("As senhas não são iguais.")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Confirmar senha"), "3");
    expect(screen.queryByText("As senhas não são iguais.")).toBeNull();
  });

  it("Mostrar/Ocultar troca os dois campos e usa new-password", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const a = screen.getByLabelText("Senha");
    const b = screen.getByLabelText("Confirmar senha");
    expect(a).toHaveAttribute("autocomplete", "new-password");
    await user.click(screen.getByRole("button", { name: "Mostrar" }));
    expect([a, b].map((x) => x.getAttribute("type"))).toEqual(["text", "text"]);
    await user.click(screen.getByRole("button", { name: "Ocultar" }));
    expect([a, b].map((x) => x.getAttribute("type"))).toEqual(["password", "password"]);
  });
});
