import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import { QueryPage, type Page, type Query } from "./QueryPage";

type Row = { id: string; nome: string };
const columns = [
  { key: "nome", label: "Nome", sortable: true, render: (r: Row) => r.nome },
];
const all: Row[] = Array.from({ length: 45 }, (_, i) => ({ id: String(i), nome: `Aluno ${i}` }));

function setup(fetchPage: (q: Query) => Promise<Page<Row>>, url = "/") {
  window.history.replaceState(null, "", url);
  return render(<QueryPage columns={columns} fetchPage={fetchPage} rowKey={(r) => r.id} pageSize={20} />);
}
const pager = (q: Query) => Promise.resolve({ rows: all.slice((q.pagina - 1) * q.tamanho, q.pagina * q.tamanho), total: 45 });

describe("QueryPage", () => {
  it("CA5/CA7: lê o endereço, pede só a página atual e atualiza o endereço ao filtrar", async () => {
    const fetchPage = vi.fn(pager);
    setup(fetchPage, "/?q=ana&pagina=2&ordem=nome");
    expect(await screen.findByText("Aluno 20")).toBeInTheDocument();
    expect(fetchPage).toHaveBeenLastCalledWith({ q: "ana", pagina: 2, ordem: "nome", filtros: {}, tamanho: 20 });
    expect(screen.getAllByRole("row")).toHaveLength(21);
    expect(screen.getByText("Página 2 de 3")).toBeInTheDocument();
    expect(screen.getByRole("searchbox", { name: "Buscar" })).toHaveValue("ana");

    await userEvent.type(screen.getByRole("searchbox", { name: "Buscar" }), "x");
    await waitFor(() => expect(window.location.search).toBe("?q=anax&ordem=nome"));
    expect(fetchPage).toHaveBeenLastCalledWith(expect.objectContaining({ q: "anax", pagina: 1 }));
  });

  it("CA7: botões desabilitados nos extremos", async () => {
    setup(pager);
    await screen.findByText("Aluno 0");
    expect(screen.getByRole("button", { name: "Página anterior" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Próxima página" }));
    expect(await screen.findByText("Página 2 de 3")).toBeInTheDocument();
    expect(window.location.search).toBe("?pagina=2");
  });

  it("ordenação vai para o endereço", async () => {
    setup(pager);
    await userEvent.click(await screen.findByRole("button", { name: "Ordenar por Nome" }));
    expect(window.location.search).toBe("?ordem=nome");
    await userEvent.click(await screen.findByRole("button", { name: "Ordenar por Nome" }));
    expect(window.location.search).toBe("?ordem=-nome");
  });

  it("CA6: parâmetros inválidos usam os padrões; página fora do intervalo volta para a 1", async () => {
    const fetchPage = vi.fn((q: Query) => (q.pagina > 3 ? Promise.resolve({ rows: [], total: 45 }) : pager(q)));
    setup(fetchPage, "/?pagina=abc&ordem=zzz");
    expect(await screen.findByText("Aluno 0")).toBeInTheDocument();
    expect(fetchPage).toHaveBeenLastCalledWith(expect.objectContaining({ pagina: 1, ordem: "" }));

    window.history.replaceState(null, "", "/");
    setup(fetchPage, "/?pagina=99");
    await waitFor(() => expect(window.location.search).toBe(""));
    expect((await screen.findAllByText("Aluno 0")).length).toBeGreaterThan(0);
  });

  it("CA8: busca sem resultado com Limpar filtros; sem filtro mostra o vazio", async () => {
    setup(() => Promise.resolve({ rows: [], total: 0 }), "/?q=zzz");
    expect(await screen.findByText("Nenhum resultado")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Limpar filtros" }));
    expect(window.location.search).toBe("");
    expect(await screen.findByText("Nada por aqui ainda")).toBeInTheDocument();
  });

  it("erro com Tentar de novo e 403 sem permissão", async () => {
    const fetchPage = vi.fn().mockRejectedValueOnce(new Error("x")).mockImplementation(pager);
    setup(fetchPage);
    await userEvent.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByText("Aluno 0")).toBeInTheDocument();

    setup(() => Promise.reject({ status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });
});
