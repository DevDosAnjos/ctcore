import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import type { PortalTermPending } from "./usePortalTerm";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const pending: PortalTermPending = {
  id: "s1",
  name: "Aluno Fictício",
  term: { version: 1, body: "Texto do termo.\nSegunda linha." },
  imageUse: { version: 1, body: "Texto do uso de imagem.", granted: false },
};

let list: PortalTermPending[];
let posts: { id: string; body: { version: number; imageUse: boolean } }[];

function setup(over: { role?: string; initial?: PortalTermPending[]; get?: () => Response; post?: () => Response | undefined; path?: string } = {}) {
  list = over.initial ?? [pending];
  posts = [];
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(over.role ?? "STUDENT"))),
    http.get("/api/v1/portal/term", over.get ?? (() => HttpResponse.json(list))),
    http.get("/api/v1/portal/modality-confirmations", () => HttpResponse.json([])),
    http.post("/api/v1/portal/students/:id/term-acceptance", async ({ request, params }) => {
      const failure = over.post?.();
      if (failure) return failure;
      posts.push({ id: params.id as string, body: (await request.json()) as { version: number; imageUse: boolean } });
      list = list.filter((p) => p.id !== params.id);
      return HttpResponse.json({ studentId: params.id, version: 1, imageUse: false }, { status: 201 });
    }),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[over.path ?? "/portal/termo"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}
const heading = () => screen.findByRole("heading", { name: "Termo de matrícula", level: 1 });

describe("PortalTermPage (RN-MAT-11, RN-POR-03)", () => {
  it("mostra o termo, aceita com a caixa de imagem separada e segue para o início", async () => {
    setup();
    await heading();
    expect(await screen.findByText(/Texto do termo\./)).toBeInTheDocument();
    const read = screen.getByLabelText("Li e aceito o termo de matrícula");
    const image = screen.getByLabelText(/Autorizo o uso de imagem/);
    expect(image).not.toBeChecked();
    const submit = screen.getByRole("button", { name: "Aceitar e continuar" });
    expect(submit).toBeDisabled();
    await userEvent.click(read);
    await userEvent.click(image);
    await userEvent.click(submit);
    await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" });
    expect(posts).toEqual([{ id: "s1", body: { version: 1, imageUse: true } }]);
  });

  it("sem aceite do uso de imagem, imageUse segue falso", async () => {
    setup();
    await heading();
    await userEvent.click(await screen.findByLabelText("Li e aceito o termo de matrícula"));
    await userEvent.click(screen.getByRole("button", { name: "Aceitar e continuar" }));
    await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" });
    expect(posts[0].body.imageUse).toBe(false);
  });

  it("portal com termo pendente leva de qualquer tela ao aceite", async () => {
    setup({ path: "/" });
    await heading();
  });

  it("sem pendência a tela inicial abre; a tela de aceite mostra o vazio", async () => {
    setup({ initial: [], path: "/" });
    await screen.findByRole("heading", { name: "Bem-vindo ao ctcore" });
  });

  it("vazio, erro com nova tentativa e sem permissão", async () => {
    const empty = setup({ initial: [] });
    expect(await screen.findByRole("heading", { name: "Nenhum termo para aceitar" })).toBeInTheDocument();
    empty.unmount();

    let calls = 0;
    const failing = setup({ get: () => (++calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([pending])) });
    await userEvent.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    await screen.findByText(/Texto do termo\./);
    failing.unmount();

    setup({ role: "GUARDIAN", get: () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 }) });
    expect(await screen.findByRole("heading", { name: "Você não tem acesso" })).toBeInTheDocument();
  });

  it("termo que mudou (409) avisa e mantém a tela", async () => {
    setup({ post: () => HttpResponse.json({ code: "TERMS_VERSION_CONFLICT" }, { status: 409 }) });
    await heading();
    await userEvent.click(await screen.findByLabelText("Li e aceito o termo de matrícula"));
    await userEvent.click(screen.getByRole("button", { name: "Aceitar e continuar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("O termo mudou");
    await waitFor(() => expect(screen.getByRole("button", { name: "Aceitar e continuar" })).toBeEnabled());
  });

  it.each([false, true])("passa no axe (escuro: %s)", async (dark) => {
    setDevicePrefersDark(dark);
    const { container } = setup();
    await screen.findByText(/Texto do termo\./);
    expect(await axeViolations(container)).toEqual([]);
  });
});
