import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import { Stepper } from "./Stepper";

function Demo({ onFinish = () => {} }: { onFinish?: () => void }) {
  const [nome, setNome] = useState("");
  return (
    <Stepper
      onFinish={onFinish}
      steps={[
        {
          id: "1",
          title: "Dados",
          valid: nome !== "",
          content: <input aria-label="Nome" value={nome} onChange={(e) => setNome(e.target.value)} />,
        },
        { id: "2", title: "Revisão", valid: true, content: <p>{nome}</p> },
        { id: "3", title: "Fim", valid: true, content: <p>ok</p> },
      ]}
    />
  );
}

describe("Stepper", () => {
  it("CA14: anuncia a etapa, só avança se válida e voltar mantém o preenchido", async () => {
    const onFinish = vi.fn();
    render(<Demo onFinish={onFinish} />);
    expect(screen.getByText("Etapa 1 de 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Avançar" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Voltar" })).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Nome"), "Ana");
    await userEvent.click(screen.getByRole("button", { name: "Avançar" }));
    expect(screen.getByText("Etapa 2 de 3")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Voltar" }));
    expect(screen.getByLabelText("Nome")).toHaveValue("Ana");
    await userEvent.click(screen.getByRole("button", { name: "Avançar" }));
    await userEvent.click(screen.getByRole("button", { name: "Avançar" }));
    await userEvent.click(screen.getByRole("button", { name: "Concluir" }));
    expect(onFinish).toHaveBeenCalledTimes(1);
  });
});
