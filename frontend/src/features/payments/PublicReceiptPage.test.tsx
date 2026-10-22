import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { App } from "@/App";
import { SessionProvider } from "@/features/auth/SessionProvider";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";

const open = (code: string) =>
  render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/recibo/${code}`]}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
const receipt = { unit: "Centro", number: 7, reference: "Jiu-Jitsu · nov/2026", amountCents: 12000, paidAt: "2026-11-04T15:00:00Z", payerInitials: "A. S." };

describe("PublicReceiptPage (RN-COB-16)", () => {
  it("CA11: mostra só unidade, número, referência, valor, data e iniciais, com o botão do PDF, sem sessão e sem violações no axe nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      server.use(http.get("/api/v1/public/receipts/:code", () => HttpResponse.json(receipt)), http.get("/api/v1/auth/session", () => HttpResponse.json({}, { status: 401 })));
      const { unmount } = open("ABCDEF0123456789");
      expect(await screen.findByRole("heading", { name: "Recibo número 7" })).toBeInTheDocument();
      for (const text of ["Centro", "Jiu-Jitsu · nov/2026", "R$ 120,00", "A. S."]) expect(screen.getByText(text)).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "Baixar PDF" })).toHaveAttribute("href", "/api/v1/public/receipts/ABCDEF0123456789/pdf");
      expect(await axeViolations(document.body)).toEqual([]);
      unmount();
    }
  });

  it("CA11: carregando, código inválido (404) e erro com nova tentativa", async () => {
    server.use(http.get("/api/v1/public/receipts/:code", async () => (await delay(50), HttpResponse.json({ code: "NOT_FOUND", message: "x", changed: false, fields: [], traceId: "t" }, { status: 404 }))));
    const first = open("XXXX");
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(await screen.findByText("Recibo não encontrado")).toBeInTheDocument();
    expect(await axeViolations(document.body)).toEqual([]);
    first.unmount();

    let calls = 0;
    server.use(http.get("/api/v1/public/receipts/:code", () => (++calls === 1 ? HttpResponse.error() : HttpResponse.json(receipt))));
    open("ABCDEF0123456789");
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: /Tentar de novo|Tentar novamente/ }));
    expect(await screen.findByRole("heading", { name: "Recibo número 7" })).toBeInTheDocument();
  });
});
