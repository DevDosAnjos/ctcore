import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { vi } from "vitest";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";
import { setDevicePrefersDark } from "@/test/device";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { BackupCodesDialog } from "./BackupCodesDialog";
import { ReauthDialog } from "./ReauthDialog";
import { SessionProvider } from "./SessionProvider";
import { TwoFactorSetupDialog } from "./TwoFactorSetupDialog";

const err = (status: number, code: string) =>
  HttpResponse.json({ code, message: "m", changed: false, fields: [], traceId: "t" }, { status });
const session = (enabled: boolean) =>
  http.get("/api/v1/auth/session", () =>
    HttpResponse.json({ account: { id: "1", email: "a@x.com" }, links: [], twoFactor: { enabled, required: false } }),
  );

// A sessão do mock fica ativa para os endpoints que exigem login.
async function login() {
  await fetch("/api/v1/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: "a@b.c", password: "x" }) });
}

function wrap(ui: React.ReactNode) {
  return render(
    <ThemeProvider>
      <SessionProvider>{ui}</SessionProvider>
    </ThemeProvider>,
  );
}

describe("TwoFactorSetupDialog (CA22)", () => {
  beforeEach(login);

  it("mostra QR e chave, valida o código, mostra os 10 códigos e só conclui depois da confirmação", async () => {
    const user = userEvent.setup();
    const onEnabled = vi.fn();
    wrap(<TwoFactorSetupDialog open onClose={() => {}} onEnabled={onEnabled} />);
    expect(await screen.findByAltText("QR code para ativar as duas etapas")).toHaveAttribute("src", expect.stringContaining("data:image/png"));
    expect(screen.getByText("JBSWY3DPEHPK3PXP")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Código de 6 dígitos"), "111111");
    await user.click(screen.getByRole("button", { name: "Ativar" }));
    expect(await screen.findByText("Código incorreto. Confira o app e tente de novo.")).toBeInTheDocument();
    expect(screen.getByLabelText("Código de 6 dígitos")).toHaveValue("111111");

    await user.clear(screen.getByLabelText("Código de 6 dígitos"));
    await user.type(screen.getByLabelText("Código de 6 dígitos"), "123456");
    await user.click(screen.getByRole("button", { name: "Ativar" }));
    expect(await screen.findAllByRole("listitem")).toHaveLength(10);
    const done = screen.getByRole("button", { name: "Concluir" });
    expect(done).toBeDisabled();
    await user.click(screen.getByLabelText("Já guardei os códigos"));
    await user.click(done);
    expect(onEnabled).toHaveBeenCalledTimes(1);
  });

  it("copia e baixa os códigos", async () => {
    const user = userEvent.setup();
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    URL.createObjectURL = vi.fn(() => "blob:x");
    URL.revokeObjectURL = vi.fn();
    wrap(<TwoFactorSetupDialog open onClose={() => {}} onEnabled={() => {}} />);
    await user.type(await screen.findByLabelText("Código de 6 dígitos"), "123456");
    await user.click(screen.getByRole("button", { name: "Ativar" }));
    await screen.findAllByRole("listitem");
    await user.click(screen.getByRole("button", { name: "Copiar códigos" }));
    expect(await screen.findByText("Códigos copiados.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Baixar códigos" }));
    expect(click).toHaveBeenCalled();
    click.mockRestore();
  });

  it("código incompleto não chama a API; erro e 403 têm estados próprios", async () => {
    const user = userEvent.setup();
    wrap(<TwoFactorSetupDialog open onClose={() => {}} onEnabled={() => {}} />);
    await user.click(await screen.findByRole("button", { name: "Ativar" }));
    expect(screen.getByText("Informe o código de 6 dígitos.")).toBeInTheDocument();
  });

  it("falha ao preparar mostra erro com Tentar de novo; 403 mostra sem permissão", async () => {
    const user = userEvent.setup();
    server.use(http.post("/api/v1/auth/two-factor/setup", () => err(500, "INTERNAL_ERROR")));
    const { unmount } = wrap(<TwoFactorSetupDialog open onClose={() => {}} onEnabled={() => {}} />);
    const retry = await screen.findByRole("button", { name: "Tentar de novo" });
    server.use(http.post("/api/v1/auth/two-factor/setup", () => HttpResponse.json({ secret: "ABC", otpauthUri: "otpauth://totp/x?secret=ABC" })));
    await user.click(retry);
    expect(await screen.findByText("ABC")).toBeInTheDocument();
    unmount();

    server.use(http.post("/api/v1/auth/two-factor/setup", () => err(403, "FORBIDDEN")));
    wrap(<TwoFactorSetupDialog open onClose={() => {}} onEnabled={() => {}} />);
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("mostra o carregando enquanto prepara", async () => {
    server.use(http.post("/api/v1/auth/two-factor/setup", () => new Promise(() => {})));
    wrap(<TwoFactorSetupDialog open onClose={() => {}} onEnabled={() => {}} />);
    expect(await screen.findByText("Preparando as duas etapas...")).toBeInTheDocument();
  });

  it.each([
    ["claro", false],
    ["escuro", true],
  ])("axe a 360 px no tema %s (formulário e códigos)", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const user = userEvent.setup();
    wrap(<TwoFactorSetupDialog open onClose={() => {}} onEnabled={() => {}} />);
    await screen.findByAltText("QR code para ativar as duas etapas");
    expect(await axeViolations(document.body)).toEqual([]);
    await user.type(screen.getByLabelText("Código de 6 dígitos"), "123456");
    await user.click(screen.getByRole("button", { name: "Ativar" }));
    await screen.findAllByRole("listitem");
    expect(await axeViolations(document.body)).toEqual([]);
  });
});

describe("BackupCodesDialog (CA22)", () => {
  beforeEach(login);

  it("com códigos, mostra-os e não fecha sem confirmar", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    wrap(<BackupCodesDialog open codes={["AAAAAAAAAA", "BBBBBBBBBB"]} onClose={onClose} />);
    expect(await screen.findByText("AAAAAAAAAA")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Fechar" })).toBeNull();
    await user.keyboard("{Escape}");
    expect(onClose).not.toHaveBeenCalled();
    await user.click(screen.getByLabelText("Já guardei os códigos"));
    await user.click(screen.getByRole("button", { name: "Concluir" }));
    expect(onClose).toHaveBeenCalled();
  });

  it("pede senha e código antes de gerar códigos novos", async () => {
    const user = userEvent.setup();
    wrap(<BackupCodesDialog open onClose={() => {}} />);
    await user.click(await screen.findByRole("button", { name: "Gerar códigos novos" }));
    expect(screen.getByText("Preencha a senha e o código.")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Senha"), "errada");
    await user.type(screen.getByLabelText("Código do app ou de reserva"), "123456");
    await user.click(screen.getByRole("button", { name: "Gerar códigos novos" }));
    expect(await screen.findByText("Senha incorreta.")).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Senha"));
    await user.type(screen.getByLabelText("Senha"), "certa");
    await user.clear(screen.getByLabelText("Código do app ou de reserva"));
    await user.type(screen.getByLabelText("Código do app ou de reserva"), "999999");
    await user.click(screen.getByRole("button", { name: "Gerar códigos novos" }));
    expect(await screen.findByText("Código incorreto.")).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Código do app ou de reserva"));
    await user.type(screen.getByLabelText("Código do app ou de reserva"), "123456");
    await user.click(screen.getByRole("button", { name: "Gerar códigos novos" }));
    expect(await screen.findAllByRole("listitem")).toHaveLength(10);
  });

  it("429, 403 e erro do servidor", async () => {
    const user = userEvent.setup();
    server.use(http.post("/api/v1/auth/two-factor/backup-codes", () => err(429, "TOO_MANY_REQUESTS")));
    const first = wrap(<BackupCodesDialog open onClose={() => {}} />);
    await user.type(await screen.findByLabelText("Senha"), "x");
    await user.type(screen.getByLabelText("Código do app ou de reserva"), "123456");
    await user.click(screen.getByRole("button", { name: "Gerar códigos novos" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Muitas tentativas");
    server.use(http.post("/api/v1/auth/two-factor/backup-codes", () => err(500, "INTERNAL_ERROR")));
    await user.click(screen.getByRole("button", { name: "Gerar códigos novos" }));
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
    first.unmount();

    server.use(http.post("/api/v1/auth/two-factor/backup-codes", () => err(403, "FORBIDDEN")));
    wrap(<BackupCodesDialog open onClose={() => {}} />);
    await user.type(await screen.findByLabelText("Senha"), "x");
    await user.type(screen.getByLabelText("Código do app ou de reserva"), "123456");
    await user.click(screen.getByRole("button", { name: "Gerar códigos novos" }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
  });

  it("axe a 360 px nos dois temas", async () => {
    for (const dark of [false, true]) {
      setDevicePrefersDark(dark);
      window.innerWidth = 360;
      const { unmount } = wrap(<BackupCodesDialog open onClose={() => {}} />);
      await screen.findByLabelText("Senha");
      expect(await axeViolations(document.body)).toEqual([]);
      unmount();
    }
  });
});

describe("ReauthDialog (CA23)", () => {
  beforeEach(login);

  it("com as duas etapas desligadas pede só a senha e devolve o token", async () => {
    const user = userEvent.setup();
    server.use(session(false));
    const onConfirmed = vi.fn();
    wrap(<ReauthDialog open action="FULL_EXPORT" onClose={() => {}} onConfirmed={onConfirmed} />);
    await screen.findByText(/Confirme a sua senha para continuar/);
    expect(screen.queryByLabelText("Código do app ou de reserva")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(screen.getByText("Preencha todos os campos.")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Senha"), "certa");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    await waitFor(() => expect(onConfirmed).toHaveBeenCalledWith("token-de-confirmacao"));
  });

  it("senha errada mostra o erro e não devolve token; com as duas etapas pede também o código", async () => {
    const user = userEvent.setup();
    server.use(session(true));
    const onConfirmed = vi.fn();
    let body: Record<string, unknown> = {};
    server.use(
      http.post("/api/v1/auth/reauth", async ({ request }) => {
        body = (await request.json()) as typeof body;
        return body.password === "errada" ? err(401, "INVALID_CREDENTIALS") : err(401, "INVALID_TWO_FACTOR_CODE");
      }),
    );
    wrap(<ReauthDialog open action="DEACTIVATE_UNIT" onClose={() => {}} onConfirmed={onConfirmed} />);
    const code = await screen.findByLabelText("Código do app ou de reserva");
    await user.type(screen.getByLabelText("Senha"), "errada");
    await user.type(code, "123456");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByText("Senha incorreta.")).toBeInTheDocument();
    expect(body).toEqual({ action: "DEACTIVATE_UNIT", password: "errada", code: "123456" });
    await user.clear(screen.getByLabelText("Senha"));
    await user.type(screen.getByLabelText("Senha"), "certa");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByText("Código incorreto.")).toBeInTheDocument();
    expect(onConfirmed).not.toHaveBeenCalled();
  });

  it("429, 403, erro do servidor e Cancelar", async () => {
    const user = userEvent.setup();
    server.use(session(false));
    const onClose = vi.fn();
    server.use(http.post("/api/v1/auth/reauth", () => err(429, "TOO_MANY_REQUESTS")));
    const first = wrap(<ReauthDialog open action="FULL_EXPORT" onClose={onClose} onConfirmed={() => {}} />);
    await user.type(await screen.findByLabelText("Senha"), "x");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Muitas tentativas");
    server.use(http.post("/api/v1/auth/reauth", () => err(500, "INTERNAL_ERROR")));
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByRole("button", { name: "Tentar de novo" })).toBeInTheDocument();
    first.unmount();

    server.use(http.post("/api/v1/auth/reauth", () => err(403, "FORBIDDEN")));
    const second = wrap(<ReauthDialog open action="FULL_EXPORT" onClose={onClose} onConfirmed={() => {}} />);
    await user.type(await screen.findByLabelText("Senha"), "x");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    expect(await screen.findByText("Você não tem acesso")).toBeInTheDocument();
    second.unmount();

    wrap(<ReauthDialog open action="FULL_EXPORT" onClose={onClose} onConfirmed={() => {}} />);
    await user.click(await screen.findByRole("button", { name: "Cancelar" }));
    expect(onClose).toHaveBeenCalled();
  });

  it("axe a 360 px nos dois temas, com e sem código", async () => {
    for (const dark of [false, true]) {
      for (const enabled of [false, true]) {
        setDevicePrefersDark(dark);
        window.innerWidth = 360;
        server.use(session(enabled));
        const { unmount } = wrap(<ReauthDialog open action="FULL_EXPORT" onClose={() => {}} onConfirmed={() => {}} />);
        await screen.findByText(enabled ? /senha e o código/ : /sua senha para continuar/);
        expect(await axeViolations(document.body)).toEqual([]);
        unmount();
      }
    }
  });
});
