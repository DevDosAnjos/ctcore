import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Field, Input, Select, Textarea } from "./field";
import { Checkbox } from "./checkbox";
import { Switch } from "./switch";
import { RadioCard } from "./radio-card";

describe("Field (CA8)", () => {
  it("liga rótulo, dica, erro, obrigatório e inválido ao controle", () => {
    render(
      <Field label="Nome" required hint="Como no documento" error="Obrigatório">
        {(c) => <Input {...c} />}
      </Field>,
    );
    const input = screen.getByLabelText(/Nome/);
    expect(input).toHaveAttribute("aria-required", "true");
    expect(input).toHaveAttribute("aria-invalid", "true");
    const ids = input.getAttribute("aria-describedby")!.split(" ");
    expect(ids).toHaveLength(2);
    expect(document.getElementById(ids[0])).toHaveTextContent("Como no documento");
    expect(document.getElementById(ids[1])).toHaveTextContent("Obrigatório");
  });

  it("sem dica nem erro não declara describedby nem invalid", () => {
    render(<Field label="Nome">{(c) => <Select {...c} />}</Field>);
    const sel = screen.getByLabelText("Nome");
    expect(sel).not.toHaveAttribute("aria-describedby");
    expect(sel).not.toHaveAttribute("aria-invalid");
  });
});

describe("controles (CA9)", () => {
  it("Textarea conta o uso", async () => {
    render(<Textarea aria-label="Obs" maxLength={10} />);
    expect(screen.getByText("0/10")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Obs"), "abc");
    expect(screen.getByText("3/10")).toBeInTheDocument();
  });

  it("Checkbox alterna por clique e Espaço", async () => {
    const u = userEvent.setup();
    render(<Checkbox label="Aceito" />);
    const box = screen.getByRole("checkbox", { name: "Aceito" });
    await u.click(box);
    expect(box).toBeChecked();
    await u.keyboard(" ");
    expect(box).not.toBeChecked();
  });

  it("Switch tem role e estado e alterna por clique, Espaço e Enter", async () => {
    const u = userEvent.setup();
    function Demo() {
      const [on, setOn] = useState(false);
      return <Switch label="Avisos" checked={on} onCheckedChange={setOn} />;
    }
    render(<Demo />);
    const sw = screen.getByRole("switch", { name: "Avisos" });
    expect(sw).toHaveAttribute("aria-checked", "false");
    await u.click(sw);
    expect(sw).toHaveAttribute("aria-checked", "true");
    await u.keyboard(" ");
    expect(sw).toHaveAttribute("aria-checked", "false");
    await u.keyboard("{Enter}");
    expect(sw).toHaveAttribute("aria-checked", "true");
  });

  it("RadioCard: clique e setas mudam a escolha no grupo", async () => {
    const u = userEvent.setup();
    render(
      <div role="radiogroup" aria-label="Plano">
        <RadioCard name="p" value="a" label="Mensal" description="Todo mês" defaultChecked />
        <RadioCard name="p" value="b" label="Anual" />
      </div>,
    );
    const [a, b] = screen.getAllByRole("radio");
    expect(a).toBeChecked();
    expect(screen.getByText("Todo mês")).toBeInTheDocument();
    await u.click(b);
    expect(b).toBeChecked();
    await u.keyboard("{ArrowLeft}");
    expect(a).toBeChecked();
  });
});
