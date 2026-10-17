import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { server } from "@/mocks/server";
import { RequireArea } from "./RequireArea";
import { RequireSession } from "./RequireSession";
import { SessionProvider, type SessionView } from "./SessionProvider";

const L = (id: string, role: string) => ({ id, organizationId: "o", organizationName: "Alfa", role, unitIds: [] });
const account = { id: "a", email: "x@x.com", name: "X" };
const TITLE = "Esta área não está disponível para o seu perfil";

function Path() {
  return <span data-testid="path">{useLocation().pathname}</span>;
}
function setup(view: SessionView | Response) {
  server.use(http.get("/api/v1/auth/session", () => (view instanceof Response ? view : HttpResponse.json(view))));
  return render(
    <MemoryRouter initialEntries={["/financeiro"]}>
      <SessionProvider>
        <Path />
        <Routes>
          <Route element={<RequireSession />}>
            <Route element={<RequireArea roles={["COORDINATOR", "ADMINISTRATOR"]} />}>
              <Route path="/financeiro" element={<p>conteudo protegido</p>} />
            </Route>
          </Route>
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  );
}

describe("RequireArea", () => {
  it("CA2: professor vê a tela, a URL não muda e o conteúdo não renderiza", async () => {
    setup({ account, links: [L("l1", "TEACHER")], activeLinkId: "l1" });
    expect(await screen.findByRole("heading", { name: TITLE })).toBeInTheDocument();
    expect(screen.getByTestId("path")).toHaveTextContent("/financeiro");
    expect(screen.queryByText("conteudo protegido")).not.toBeInTheDocument();
  });

  it("CA3: administrador vê o conteúdo", async () => {
    setup({ account, links: [L("l1", "ADMINISTRATOR")], activeLinkId: "l1" });
    expect(await screen.findByText("conteudo protegido")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: TITLE })).not.toBeInTheDocument();
  });

  it("CA4: sem vínculo ativo nega", async () => {
    setup({ account, links: [L("l1", "ADMINISTRATOR")], activeLinkId: null });
    expect(await screen.findByRole("heading", { name: TITLE })).toBeInTheDocument();
    expect(screen.queryByText("conteudo protegido")).not.toBeInTheDocument();
  });

  it("CA8: erro de sessão mostra ErrorState, não a tela", async () => {
    setup(new Response(null, { status: 500 }));
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: TITLE })).not.toBeInTheDocument();
  });
});
