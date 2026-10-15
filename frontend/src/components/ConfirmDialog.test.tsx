import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import { ConfirmDialog } from "./ConfirmDialog";

// Janela controlada pela tela, que continua montada entre uma abertura e outra.
function Demo() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)}>Abrir</button>
      <ConfirmDialog open={open} onOpenChange={setOpen} title="Desativar" summary="Vai desativar a unidade." reason="required" typedText="DESATIVAR" onConfirm={vi.fn()} />
    </>
  );
}

function setup(props: Partial<React.ComponentProps<typeof ConfirmDialog>> = {}) {
  const onConfirm = props.onConfirm ?? vi.fn();
  const onOpenChange = vi.fn();
  render(
    <ConfirmDialog open onOpenChange={onOpenChange} title="Cancelar" summary="Vai cancelar a cobrança." {...props} onConfirm={onConfirm} />,
  );
  return { onConfirm, onOpenChange };
}

describe("ConfirmDialog", () => {
  it("CA11: motivo obrigatório vazio ou só com espaços desabilita e diz o que falta", async () => {
    setup({ reason: "required" });
    const ok = screen.getByRole("button", { name: "Confirmar" });
    expect(ok).toBeDisabled();
    expect(screen.getByText("Informe o motivo para continuar.")).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText("Motivo")); await userEvent.paste("   ");
    expect(ok).toBeDisabled();
    await userEvent.paste("erro de lançamento");
    expect(ok).toBeEnabled();
  });

  it("CA11: cliques repetidos confirmam uma única vez", async () => {
    let done!: () => void;
    const onConfirm = vi.fn(() => new Promise<void>((r) => (done = r)));
    setup({ onConfirm });
    const ok = screen.getByRole("button", { name: "Confirmar" });
    await userEvent.dblClick(ok);
    await userEvent.click(ok);
    expect(onConfirm).toHaveBeenCalledTimes(1);
    done();
  });

  it("CA12: mostra o resumo e Cancelar fecha sem chamar a ação", async () => {
    const { onConfirm, onOpenChange } = setup();
    expect(screen.getByText("Vai cancelar a cobrança.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it.each([
    ["Cancelar", () => userEvent.click(screen.getByRole("button", { name: "Cancelar" }))],
    ["Esc", () => userEvent.keyboard("{Escape}")],
  ])("CA12 RNF-26: depois de %s, a janela reabre limpa e com Confirmar desabilitado", async (_n, fechar) => {
    render(<Demo />);
    await userEvent.click(screen.getByRole("button", { name: "Abrir" }));
    await userEvent.type(screen.getByLabelText("Motivo"), "erro de lançamento");
    await userEvent.type(screen.getByLabelText("Digite DESATIVAR para confirmar"), "DESATIVAR");
    expect(screen.getByRole("button", { name: "Confirmar" })).toBeEnabled();

    await fechar();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Abrir" }));
    expect(screen.getByLabelText("Motivo")).toHaveValue("");
    expect(screen.getByLabelText("Digite DESATIVAR para confirmar")).toHaveValue("");
    expect(screen.getByRole("button", { name: "Confirmar" })).toBeDisabled();
  });

  it("CA13: texto digitado exato, diferenciando maiúsculas", async () => {
    const { onConfirm } = setup({ typedText: "DESATIVAR" });
    const ok = screen.getByRole("button", { name: "Confirmar" });
    const campo = screen.getByLabelText("Digite DESATIVAR para confirmar");
    await userEvent.type(campo, "desativar");
    expect(ok).toBeDisabled();
    await userEvent.clear(campo);
    await userEvent.type(campo, "DESATIV");
    expect(ok).toBeDisabled();
    await userEvent.type(campo, "AR");
    expect(ok).toBeEnabled();
    await userEvent.click(ok);
    expect(onConfirm).toHaveBeenCalledWith("");
  });

  it("falha na ação mantém a janela aberta", async () => {
    const { onOpenChange } = setup({ onConfirm: vi.fn().mockRejectedValue(new Error("x")) });
    await userEvent.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(onOpenChange).not.toHaveBeenCalled();
  });
});
