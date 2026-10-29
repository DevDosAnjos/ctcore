import { useState } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Modal } from "./Modal";
import { SidePanel } from "./SidePanel";

function Demo({ Comp }: { Comp: typeof Modal }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)}>Abrir</button>
      <Comp open={open} onOpenChange={setOpen} title="Detalhes">
        <button>Dentro</button>
      </Comp>
    </>
  );
}

describe.each([
  ["Modal", Modal],
  ["SidePanel", SidePanel],
])("%s", (_n, Comp) => {
  it("CA9/CA10: título é o nome, foco preso, Esc fecha e devolve o foco", async () => {
    render(<Demo Comp={Comp as typeof Modal} />);
    const origem = screen.getByRole("button", { name: "Abrir" });
    await userEvent.click(origem);
    const dlg = screen.getByRole("dialog", { name: "Detalhes" });
    for (let i = 0; i < 5; i++) {
      await userEvent.tab();
      expect(dlg).toContainElement(document.activeElement as HTMLElement);
    }
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await waitFor(() => expect(origem).toHaveFocus());
  });
});

describe("SidePanel", () => {
  it("CA9: ocupa a largura toda no celular", async () => {
    render(<Demo Comp={SidePanel as typeof Modal} />);
    await userEvent.click(screen.getByRole("button", { name: "Abrir" }));
    expect(screen.getByRole("dialog")).toHaveClass("w-full", "md:w-[min(680px,100%)]");
  });
});

describe("CA15/CA17: visual", () => {
  it("Modal: título em fonte de títulos e rodapé com fundo suave", () => {
    render(
      <Modal open onOpenChange={() => {}} title="Detalhes" footer={<button>Salvar</button>}>
        <p>Corpo</p>
      </Modal>,
    );
    expect(screen.getByText("Detalhes")).toHaveClass("font-heading");
    expect(screen.getByRole("button", { name: "Salvar" }).parentElement).toHaveClass("bg-surface-soft");
  });

  it("dismissible=false: sem X", () => {
    render(<Modal open onOpenChange={() => {}} title="Detalhes" dismissible={false}>x</Modal>);
    expect(screen.queryByRole("button", { name: "Fechar" })).not.toBeInTheDocument();
  });

  it("SidePanel com abas: navega por teclado", async () => {
    render(
      <SidePanel
        open
        onOpenChange={() => {}}
        title="Ficha"
        tabs={[
          { id: "a", label: "Dados", content: <p>Painel A</p> },
          { id: "b", label: "Histórico", content: <p>Painel B</p> },
        ]}
      />,
    );
    await userEvent.click(screen.getByRole("tab", { name: "Dados" }));
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: "Histórico" })).toHaveFocus();
    expect(screen.getByText("Painel B")).toBeInTheDocument();
  });
});
