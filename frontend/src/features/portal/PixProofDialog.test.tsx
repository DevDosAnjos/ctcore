import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { ApiError } from "@/lib/api";
import { PixProofDialog } from "./PixProofDialog";

// FormData e File do jsdom não passam pelo fetch do Node: o envio é trocado por um duplo simples e o resto (instruções, estados) usa o MSW.
const send = vi.hoisted(() => ({ calls: [] as unknown[][], reply: (): Promise<unknown> => Promise.resolve() }));
vi.mock("./usePixProof", async (original) => ({
  ...(await original<typeof import("./usePixProof")>()),
  sendProof: (...args: unknown[]) => {
    send.calls.push(args);
    return send.reply();
  },
}));

const instructions = { chargeId: "c1", pixKey: "pix@alfa.test", amountCents: 12000, dueOn: "2026-03-05", unit: "Centro" };
const pixGet = (res: () => Response) => server.use(http.get("/api/v1/portal/charges/c1/pix-instructions", res));
const view = (onClose = () => {}, onSent?: () => void) =>
  render(
    <ThemeProvider>
      <PixProofDialog chargeIds={["c1"]} onClose={onClose} onSent={onSent} />
    </ThemeProvider>,
  );
const png = () => new File(["x"], "comprovante.png", { type: "image/png" });

describe("PixProofDialog (RF-COB-05)", () => {
  beforeEach(() => {
    send.calls.length = 0;
    send.reply = () => Promise.resolve({ id: "p1", chargeId: "c1", status: "PENDING", sentAt: "2026-03-06T10:00:00Z" });
  });

  it("CA16: mostra a chave, o valor e a unidade, copia a chave e envia o arquivo; no sucesso avisa da conferência", async () => {
    pixGet(() => HttpResponse.json(instructions));
    const onSent = vi.fn();
    const user = userEvent.setup();
    const write = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue();
    view(() => {}, onSent);
    const dialog = within(await screen.findByRole("dialog", { name: "Pix na chave com comprovante" }));
    expect(await dialog.findByText("pix@alfa.test")).toBeInTheDocument();
    expect(dialog.getByText(/120,00/)).toBeInTheDocument();
    expect(dialog.getByText("Centro")).toBeInTheDocument();
    await user.click(dialog.getByRole("button", { name: "Copiar" }));
    expect(write).toHaveBeenCalledWith("pix@alfa.test");
    expect(await dialog.findByText("Chave copiada.")).toBeInTheDocument();
    const submit = dialog.getByRole("button", { name: "Enviar comprovante" });
    expect(submit).toBeDisabled();
    await user.upload(dialog.getByLabelText(/Comprovante \(JPG/), png());
    await user.click(submit);
    expect(await dialog.findByText("Comprovante enviado. A cobrança ficou em conferência.")).toBeInTheDocument();
    expect(send.calls).toEqual([[["c1"], expect.any(File)]]);
    expect(onSent).toHaveBeenCalledOnce();
  });

  it("CA16: o erro do servidor aparece no campo do arquivo e a janela segue aberta", async () => {
    pixGet(() => HttpResponse.json(instructions));
    send.reply = () => Promise.reject(new ApiError(422, "UNPROCESSABLE", [{ field: "file", message: "use JPG, PNG, WebP ou PDF" }]));
    const user = userEvent.setup();
    view();
    const dialog = within(await screen.findByRole("dialog", { name: "Pix na chave com comprovante" }));
    await user.upload(await dialog.findByLabelText(/Comprovante \(JPG/, undefined, { timeout: 3000 }), png());
    await user.click(dialog.getByRole("button", { name: "Enviar comprovante" }));
    expect(await dialog.findByText("use JPG, PNG, WebP ou PDF")).toBeInTheDocument();
    expect(dialog.getByLabelText(/Comprovante \(JPG/)).toBeInvalid();
  });

  it("CA16: unidade sem chave, cobrança que não está aberta, erro com nova tentativa e sem permissão", async () => {
    pixGet(() => HttpResponse.json({ code: "PIX_KEY_MISSING", message: "m", fields: [], traceId: "t" }, { status: 422 }));
    const first = view();
    expect(await screen.findByText("Esta unidade não tem chave Pix cadastrada")).toBeInTheDocument();
    first.unmount();

    pixGet(() => HttpResponse.json({ code: "CHARGE_NOT_PAYABLE", message: "m", fields: [], traceId: "t" }, { status: 409 }));
    const second = view();
    expect(await screen.findByText("Esta cobrança não está aberta")).toBeInTheDocument();
    second.unmount();

    let calls = 0;
    pixGet(() => (calls++ === 0 ? HttpResponse.json({ code: "X", message: "m", fields: [], traceId: "t" }, { status: 500 }) : HttpResponse.json(instructions)));
    const user = userEvent.setup();
    const third = view();
    await user.click(await screen.findByRole("button", { name: "Tentar de novo" }));
    expect(await screen.findByText("pix@alfa.test")).toBeInTheDocument();
    third.unmount();

    pixGet(() => HttpResponse.json({ code: "FORBIDDEN", message: "m", fields: [], traceId: "t" }, { status: 403 }));
    view();
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("CA5 (#92): várias cobranças de unidades diferentes mostram que não podem ser pagas juntas", async () => {
    server.use(http.get("/api/v1/portal/charges/pix-instructions", () => HttpResponse.json({ code: "UNPROCESSABLE", message: "m", fields: [{ field: "ids", message: "mesma unidade" }], traceId: "t" }, { status: 422 })));
    render(
      <ThemeProvider>
        <PixProofDialog chargeIds={["c1", "c2"]} onClose={() => {}} />
      </ThemeProvider>,
    );
    expect(await screen.findByText("Estas cobranças não podem ser pagas juntas")).toBeInTheDocument();
  });

  it("CA16: carregando a chave mostra o estado de carregamento", async () => {
    pixGet(() => HttpResponse.json(instructions));
    view();
    expect(await screen.findByRole("status")).toHaveTextContent("Carregando");
    await waitFor(() => expect(screen.getByText("pix@alfa.test")).toBeInTheDocument());
  });

  it("CA16: o axe não acusa violação nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      pixGet(() => HttpResponse.json(instructions));
      const { baseElement, unmount } = view();
      await screen.findByText("pix@alfa.test");
      expect(await axeViolations(baseElement)).toEqual([]);
      unmount();
    }
  });
});
