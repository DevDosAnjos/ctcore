import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider, type SessionView } from "@/features/auth/SessionProvider";
import { mockPermissions } from "@/mocks/permissions";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import type { PaymentProof } from "./types";

const session = (role: string): SessionView => ({
  account: { id: "a", email: "x@x.com", name: "X" },
  links: [{ id: "l1", organizationId: "o", organizationName: "Alfa", role, unitIds: [] }],
  activeLinkId: "l1",
  twoFactor: { enabled: false, required: false },
});
const IMAGE = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAHnOcQAAAAABJRU5ErkJggg==";
const proof = (over: Partial<PaymentProof> = {}): PaymentProof => ({
  id: "p1", chargeId: "c1", studentId: "s1", studentName: "Ana Souza", studentPhotoUrl: null, unitId: "u1", unit: "Centro", reference: "Jiu-Jitsu · 03/2026", amountCents: 12000,
  dueOn: "2026-03-05", sentAt: "2026-03-06T15:00:00Z", status: "PENDING", contentType: "image/png", imageUrl: IMAGE, refusalReason: null, decidedAt: null, ...over,
});
const two = [proof(), proof({ id: "p2", studentName: "João Pereira", studentId: "s2", reference: "Kimono", amountCents: 9000, contentType: "application/pdf", imageUrl: "https://files.example/p2.pdf" })];

let posts: { url: string; body: unknown }[];

function setup(list?: () => Response | Promise<Response>, role = "COORDINATOR") {
  posts = [];
  window.history.replaceState(null, "", "/cobrancas/conferencia");
  const decide = async ({ request }: { request: Request }) => {
    posts.push({ url: new URL(request.url).pathname, body: await request.json() });
    return HttpResponse.json(proof());
  };
  server.use(
    http.get("/api/v1/auth/session", () => HttpResponse.json(session(role))),
    http.get("/api/v1/permissions", () => HttpResponse.json({ permissions: mockPermissions(role) })),
    http.get("/api/v1/payment-proofs", () => (list ? list() : HttpResponse.json({ items: two, page: 1, size: 25, total: 2 }))),
    http.post("/api/v1/payment-proofs/:id/confirm", decide),
    http.post("/api/v1/payment-proofs/:id/refuse", decide),
  );
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={["/cobrancas/conferencia"]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const ready = () => screen.findByRole("heading", { name: "Comprovantes em conferência", level: 1 });
const tableRow = (name: string) => screen.getAllByText(name).map((e) => e.closest("tr")).find(Boolean)!;

describe("PaymentProofsPage (RF-COB-05)", () => {
  it("CA15: lista imagem, aluno, unidade, referência, valor, vencimento e envio; o PDF vira link", async () => {
    setup();
    await ready();
    await screen.findAllByText("Ana Souza");
    const a = tableRow("Ana Souza");
    expect(within(a).getByText("Centro")).toBeInTheDocument();
    expect(within(a).getByText("Jiu-Jitsu · 03/2026")).toBeInTheDocument();
    expect(within(a).getByText(/120,00/)).toBeInTheDocument();
    expect(within(a).getByText("05/03/2026")).toBeInTheDocument();
    expect(within(a).getByRole("button", { name: "Ampliar comprovante de Ana Souza" })).toBeInTheDocument();
    expect(within(tableRow("João Pereira")).getByRole("link", { name: /Abrir arquivo/ })).toHaveAttribute("href", "https://files.example/p2.pdf");
  });

  it("CA15: a imagem amplia numa janela", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findAllByText("Ana Souza");
    await user.click(within(tableRow("Ana Souza")).getByRole("button", { name: "Ampliar comprovante de Ana Souza" }));
    const dialog = await screen.findByRole("dialog", { name: "Comprovante de Ana Souza" });
    expect(within(dialog).getByRole("img", { name: "Comprovante de Ana Souza" })).toHaveAttribute("src", IMAGE);
  });

  it("CA15: Confirmar oferece o recibo por e-mail (marcado) e recarrega a lista", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findAllByText("Ana Souza");
    await user.click(within(tableRow("Ana Souza")).getByRole("button", { name: "Confirmar comprovante de Ana Souza" }));
    const dialog = await screen.findByRole("dialog", { name: "Confirmar comprovante" });
    const box = within(dialog).getByRole("checkbox", { name: "Enviar o recibo por e-mail" });
    expect(box).toBeChecked();
    await user.click(box);
    await user.click(within(dialog).getByRole("button", { name: "Confirmar pagamento" }));
    await waitFor(() => expect(posts).toEqual([{ url: "/api/v1/payment-proofs/p1/confirm", body: { sendReceipt: false } }]));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("CA15: Recusar exige motivo de 3 caracteres e envia o texto", async () => {
    const user = userEvent.setup();
    setup();
    await screen.findAllByText("Ana Souza");
    await user.click(within(tableRow("Ana Souza")).getByRole("button", { name: "Recusar comprovante de Ana Souza" }));
    const dialog = await screen.findByRole("dialog", { name: "Recusar comprovante" });
    const submit = within(dialog).getByRole("button", { name: "Recusar comprovante" });
    expect(submit).toBeDisabled();
    await user.type(within(dialog).getByLabelText(/Motivo da recusa/), "ab");
    expect(submit).toBeDisabled();
    await user.type(within(dialog).getByLabelText(/Motivo da recusa/), "c  ");
    await user.click(submit);
    await waitFor(() => expect(posts).toEqual([{ url: "/api/v1/payment-proofs/p1/refuse", body: { reason: "abc" } }]));
  });

  it("CA15: erro do servidor na decisão aparece na janela e nada fecha", async () => {
    const user = userEvent.setup();
    setup();
    server.use(http.post("/api/v1/payment-proofs/:id/confirm", () => HttpResponse.json({ code: "PROOF_NOT_PENDING", message: "m", fields: [], traceId: "t" }, { status: 409 })));
    await screen.findAllByText("Ana Souza");
    await user.click(within(tableRow("Ana Souza")).getByRole("button", { name: "Confirmar comprovante de Ana Souza" }));
    const dialog = await screen.findByRole("dialog", { name: "Confirmar comprovante" });
    await user.click(within(dialog).getByRole("button", { name: "Confirmar pagamento" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("já foi decidido");
  });

  it("CA15: vazio, erro com nova tentativa e sem permissão", async () => {
    const user = userEvent.setup();
    let fail = true;
    setup(() => (fail ? HttpResponse.json({ code: "X", message: "m", fields: [], traceId: "t" }, { status: 500 }) : HttpResponse.json({ items: [], page: 1, size: 25, total: 0 })));
    fail = false;
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByText("Nenhum comprovante em conferência")).toBeInTheDocument();
  });

  it("CA15: 403 do servidor mostra sem permissão", async () => {
    setup(() => HttpResponse.json({ code: "FORBIDDEN", message: "m", fields: [], traceId: "t" }, { status: 403 }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA15: a 360 px as linhas viram cartões e o axe não acusa violação nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      const { container, unmount } = setup();
      await ready();
      await screen.findAllByText("Ana Souza");
      const cards = screen.getAllByRole("listitem").filter((li) => li.className.includes("rounded-md border p-3"));
      expect(cards).toHaveLength(2);
      expect(await axeViolations(container)).toEqual([]);
      unmount();
    }
  });
});
