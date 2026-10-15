import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import { CpfField, DateField, MoneyField, PhoneField } from "./fields";

describe("campos brasileiros", () => {
  it("CPF formata, entrega dígitos e mostra erro ligado ao campo (CA17)", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<CpfField label="CPF" onChange={onChange} />);
    const campo = screen.getByLabelText("CPF");
    await user.type(campo, "52998224725");
    expect(campo).toHaveValue("529.982.247-25");
    expect(onChange).toHaveBeenLastCalledWith("52998224725");
    await user.tab();
    expect(campo).not.toHaveAttribute("aria-invalid");
    await user.clear(campo);
    await user.type(campo, "11111111111");
    await user.tab();
    expect(campo).toHaveAttribute("aria-invalid", "true");
    const erro = document.getElementById(campo.getAttribute("aria-describedby")!);
    expect(erro).toHaveTextContent("CPF");
  });

  it("moeda entrega centavos e ignora letras (CA18)", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<MoneyField label="Valor" onChange={onChange} />);
    const campo = screen.getByLabelText("Valor");
    await user.type(campo, "12a5000");
    expect(campo).toHaveValue("R$ 1.250,00");
    expect(onChange).toHaveBeenLastCalledWith(125000);
  });

  it("moeda colada é lida em reais e substitui o valor (CA18)", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<MoneyField label="Valor" onChange={onChange} />);
    const campo = screen.getByLabelText("Valor");
    await user.click(campo);
    await user.paste("R$ 1.250");
    expect(campo).toHaveValue("R$ 1.250,00");
    expect(onChange).toHaveBeenLastCalledWith(125000);
    await user.paste("1.250,5");
    expect(campo).toHaveValue("R$ 1.250,50");
    expect(onChange).toHaveBeenLastCalledWith(125050);
  });

  it("telefone colado com +55 entrega DDD e número (CA19)", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<PhoneField label="Telefone" onChange={onChange} />);
    const campo = screen.getByLabelText("Telefone");
    await user.click(campo);
    await user.paste("+55 92 99123-4567");
    expect(campo).toHaveValue("(92) 99123-4567");
    expect(onChange).toHaveBeenLastCalledWith("92991234567");
    await user.tab();
    expect(campo).not.toHaveAttribute("aria-invalid");
  });

  it("telefone celular, fixo e erro (CA19)", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<PhoneField label="Telefone" onChange={onChange} />);
    const campo = screen.getByLabelText("Telefone");
    await user.type(campo, "11912345678");
    expect(campo).toHaveValue("(11) 91234-5678");
    expect(onChange).toHaveBeenLastCalledWith("11912345678");
    await user.clear(campo);
    await user.type(campo, "1112345678");
    expect(campo).toHaveValue("(11) 1234-5678");
    await user.clear(campo);
    await user.type(campo, "0112345678");
    await user.tab();
    expect(campo).toHaveAttribute("aria-invalid", "true");
  });

  it("data entrega ISO e acusa data inexistente (CA20)", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<DateField label="Nascimento" onChange={onChange} />);
    const campo = screen.getByLabelText("Nascimento");
    await user.type(campo, "08102026");
    expect(campo).toHaveValue("08/10/2026");
    expect(onChange).toHaveBeenLastCalledWith("2026-10-08");
    await user.clear(campo);
    await user.type(campo, "31022026");
    await user.tab();
    expect(campo).toHaveAttribute("aria-invalid", "true");
    expect(onChange).toHaveBeenLastCalledWith("");
  });
});
