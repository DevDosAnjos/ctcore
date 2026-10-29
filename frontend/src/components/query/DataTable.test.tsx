import { useState } from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import { DataTable } from "./DataTable";
import { Pagination } from "./Pagination";

type Row = { id: string; name: string; total: string };
const rows: Row[] = [
  { id: "1", name: "Ana", total: "R$ 10,00" },
  { id: "2", name: "Bia", total: "R$ 20,00" },
];
const columns = [
  { key: "name", label: "Nome", render: (r: Row) => r.name },
  { key: "total", label: "Total", numeric: true, render: (r: Row) => r.total },
];

describe("DataTable (CA11)", () => {
  it("coluna numérica à direita com números tabulares e linha de 52 px", () => {
    render(<DataTable columns={columns} rows={rows} rowKey={(r) => r.id} order="name" onOrder={() => {}} />);
    expect(screen.getByRole("columnheader", { name: "Total" })).toHaveClass("text-right");
    expect(screen.getByRole("cell", { name: "R$ 10,00" })).toHaveClass("text-right", "tabular-nums");
    expect(screen.getAllByRole("row")[1]).toHaveClass("h-[52px]");
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("seleção: marcar linha e marcar todas entregam as chaves", async () => {
    const u = userEvent.setup();
    const onSel = vi.fn();
    function Demo() {
      const [sel, setSel] = useState<string[]>([]);
      return (
        <DataTable
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id}
          order="name"
          onOrder={() => {}}
          selectable
          selected={sel}
          onSelectionChange={(k) => {
            onSel(k);
            setSel(k);
          }}
        />
      );
    }
    render(<Demo />);
    await u.click(within(screen.getAllByRole("row")[2]).getByRole("checkbox"));
    expect(onSel).toHaveBeenLastCalledWith(["2"]);
    await u.click(screen.getByRole("checkbox", { name: "Selecionar todas as linhas" }));
    expect(onSel).toHaveBeenLastCalledWith(["2", "1"]);
    await u.click(screen.getByRole("checkbox", { name: "Selecionar todas as linhas" }));
    expect(onSel).toHaveBeenLastCalledWith([]);
  });
});

describe("Pagination (CA14)", () => {
  it("mantém as props, anuncia a página e desabilita nos extremos", async () => {
    const onPage = vi.fn();
    const { rerender } = render(<Pagination page={1} pages={3} total={60} size={25} onPage={onPage} onSize={() => {}} />);
    expect(screen.getByText("Página 1 de 3")).toHaveAttribute("aria-live", "polite");
    expect(screen.getByRole("button", { name: "Página anterior" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Próxima página" }));
    expect(onPage).toHaveBeenCalledWith(2);
    rerender(<Pagination page={3} pages={3} total={60} size={25} onPage={onPage} onSize={() => {}} />);
    expect(screen.getByRole("button", { name: "Próxima página" })).toBeDisabled();
  });
});
