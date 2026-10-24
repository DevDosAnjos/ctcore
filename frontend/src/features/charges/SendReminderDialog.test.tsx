import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { gate } from "@/test/gate";
import { SendReminderDialog } from "./SendReminderDialog";

const targets = [
  { id: "c1", label: "Ana Souza · Mensalidade" },
  { id: "c2", label: "João Pereira · Kimono" },
];

function setup(handler: () => Response | Promise<Response>) {
  const onClose = vi.fn();
  const onDone = vi.fn();
  server.use(http.post("/api/v1/charges/reminders", handler));
  render(<SendReminderDialog targets={targets} onClose={onClose} onDone={onDone} />);
  return { onClose, onDone };
}

describe("SendReminderDialog (RF-COB-06)", () => {
  it("cada resultado do servidor aparece em texto, ao lado da cobrança; fechar depois do envio avisa a tela", async () => {
    const user = userEvent.setup();
    const { onClose, onDone } = setup(() => HttpResponse.json({ results: [{ chargeId: "c1", result: "EMAIL_PROBLEM" }, { chargeId: "c2", result: "NOT_OPEN" }] }));
    const dialog = await screen.findByRole("dialog", { name: "Enviar lembrete" });
    await user.click(within(dialog).getByRole("button", { name: "Enviar" }));
    expect(await within(dialog).findByText("E-mail com problema")).toBeInTheDocument();
    expect(within(dialog).getByText("Não está aberta")).toBeInTheDocument();
    expect(within(dialog).getByText("João Pereira · Kimono")).toBeInTheDocument();
    await user.click(within(dialog).getAllByRole("button", { name: "Fechar" })[0]);
    expect(onDone).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("cancelar antes de enviar não avisa a tela; duplo clique envia uma vez só", async () => {
    const user = userEvent.setup();
    const release = gate();
    let sent = 0;
    const { onClose, onDone } = setup(async () => {
      sent++;
      await release.opened;
      return HttpResponse.json({ results: [] });
    });
    const dialog = await screen.findByRole("dialog", { name: "Enviar lembrete" });
    await user.dblClick(within(dialog).getByRole("button", { name: "Enviar" }));
    release.release();
    await waitFor(() => expect(sent).toBe(1));
    await waitFor(() => expect(within(dialog).queryByRole("button", { name: "Enviar" })).toBeNull());
    await user.click(within(dialog).getAllByRole("button", { name: "Fechar" })[0]);
    expect(onDone).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("o 404 mostra que a cobrança saiu do alcance e a janela segue aberta, sem avisar a tela", async () => {
    const user = userEvent.setup();
    const { onClose, onDone } = setup(() => HttpResponse.json({ code: "NOT_FOUND", fields: [] }, { status: 404 }));
    const dialog = await screen.findByRole("dialog", { name: "Enviar lembrete" });
    await user.click(within(dialog).getByRole("button", { name: "Enviar" }));
    expect(await within(dialog).findByRole("alert")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));
    expect(onDone).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
