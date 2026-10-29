import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { RegisterPaymentDialog } from "./RegisterPaymentDialog";

describe("RegisterPaymentDialog (RF-COB-04)", () => {
  it("CA9 (#422): a janela não tem violações no axe nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      const { unmount } = render(
        <ThemeProvider>
          <RegisterPaymentDialog chargeId="c1" label="Mensalidade" amountCents={5000} onClose={() => {}} onPaid={() => {}} />
        </ThemeProvider>,
      );
      await screen.findByRole("dialog", { name: "Registrar pagamento" });
      expect(await axeViolations(document.body)).toEqual([]);
      unmount();
    }
  });

  it("data passada vai como paidAt, sem e-mail não pede envio, e o aviso do e-mail aparece no sucesso", async () => {
    let body: Record<string, unknown> = {};
    server.use(
      http.post("/api/v1/charges/c1/payments", async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: "p", chargeId: "c1", receipt: { number: 3, code: "ABCDEF0123456789" }, receiptEmail: "FAILED" }, { status: 201 });
      }),
    );
    const onPaid = vi.fn();
    render(<RegisterPaymentDialog chargeId="c1" label="Mensalidade" amountCents={5000} onClose={() => {}} onPaid={onPaid} />);
    const dialog = within(await screen.findByRole("dialog", { name: "Registrar pagamento" }));
    const user = userEvent.setup();
    fireEvent.change(dialog.getByLabelText("Data do pagamento"), { target: { value: "2026-01-02" } });
    await user.click(dialog.getByLabelText("Enviar recibo por e-mail"));
    await user.click(dialog.getByRole("button", { name: "Confirmar pagamento" }));
    expect(await dialog.findByText("Recibo número 3")).toBeInTheDocument();
    expect(dialog.getByText(/e-mail do recibo não saiu/)).toBeInTheDocument();
    expect(body).toMatchObject({ method: "CASH", amountCents: 5000, sendReceipt: false });
    expect(new Date(body.paidAt as string).getFullYear()).toBe(2026);
    expect(onPaid).toHaveBeenCalledOnce();
  });
});
