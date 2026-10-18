import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import { axeViolations } from "@/test/axe";
import { LogoCropDialog } from "./LogoCropDialog";

// jsdom não decodifica imagem nem desenha em canvas: Image, URL e canvas são trocados por simulações.
const drawImage = vi.fn();
let loadFails = false;

class FakeImage {
  width = 800;
  height = 600;
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  set src(_: string) {
    queueMicrotask(() => (loadFails ? this.onerror?.() : this.onload?.()));
  }
}

beforeEach(() => {
  loadFails = false;
  drawImage.mockClear();
  vi.stubGlobal("Image", FakeImage);
  URL.createObjectURL = vi.fn(() => "blob:logo");
  URL.revokeObjectURL = vi.fn();
  HTMLCanvasElement.prototype.getContext = vi.fn(() => ({ drawImage })) as never;
  HTMLCanvasElement.prototype.toBlob = vi.fn(function (this: HTMLCanvasElement, cb: BlobCallback) {
    cb(new Blob([`${this.width}x${this.height}`], { type: "image/png" }));
  }) as never;
});
afterEach(() => vi.unstubAllGlobals());

const file = new File(["x"], "logo.png", { type: "image/png" });
const lastDraw = () => drawImage.mock.calls.at(-1)!;

describe("LogoCropDialog (CA16)", () => {
  it("ao confirmar gera 512 x 512 com o quadrado central da imagem e entrega o PNG", async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    render(<LogoCropDialog file={file} onCancel={() => {}} onConfirm={onConfirm} />);
    const confirm = await screen.findByRole("button", { name: "Usar este corte" });
    await waitFor(() => expect(confirm).toBeEnabled());
    await userEvent.click(confirm);
    // imagem 800 x 600, ampliação 1: lado 600, começando em x = 100
    expect(lastDraw().slice(1)).toEqual([100, 0, 600, 600, 0, 0, 512, 512]);
    const sent = onConfirm.mock.calls[0][0] as Blob;
    expect(sent.type).toBe("image/png");
    expect(await sent.text()).toBe("512x512");
  });

  it("ampliação e posição mudam o quadrado de origem", async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    render(<LogoCropDialog file={file} onCancel={() => {}} onConfirm={onConfirm} />);
    const confirm = await screen.findByRole("button", { name: "Usar este corte" });
    await waitFor(() => expect(confirm).toBeEnabled());
    fireEvent.change(screen.getByLabelText("Ampliação"), { target: { value: "2" } });
    fireEvent.change(screen.getByLabelText("Posição horizontal"), { target: { value: "0" } });
    fireEvent.change(screen.getByLabelText("Posição vertical"), { target: { value: "100" } });
    await userEvent.click(confirm);
    // lado 300; x = 0; y = (600 - 300) * 100%
    expect(lastDraw().slice(1)).toEqual([0, 300, 300, 300, 0, 0, 512, 512]);
  });

  it("arrastar a prévia move o quadrado e o limite é a borda da imagem", async () => {
    render(<LogoCropDialog file={file} onCancel={() => {}} onConfirm={vi.fn().mockResolvedValue(undefined)} />);
    const preview = await screen.findByRole("img", { name: "Prévia do corte da logo" });
    await waitFor(() => expect(screen.getByRole("button", { name: "Usar este corte" })).toBeEnabled());
    fireEvent.pointerDown(preview, { clientX: 100, clientY: 100, pointerId: 1 });
    fireEvent.pointerMove(preview, { clientX: 1000, clientY: 100, pointerId: 1 });
    fireEvent.pointerUp(preview, { pointerId: 1 });
    await waitFor(() => expect((screen.getByLabelText("Posição horizontal") as HTMLInputElement).value).toBe("0"));
  });

  it("imagem que não abre mostra o erro e não deixa confirmar; Cancelar fecha", async () => {
    loadFails = true;
    const onCancel = vi.fn();
    render(<LogoCropDialog file={file} onCancel={onCancel} onConfirm={vi.fn()} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível abrir esta imagem");
    expect(screen.getByRole("button", { name: "Usar este corte" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(onCancel).toHaveBeenCalled();
  });

  it("falha no envio mantém a janela aberta com o erro", async () => {
    render(<LogoCropDialog file={file} onCancel={() => {}} onConfirm={vi.fn().mockRejectedValue(new Error("x"))} />);
    const confirm = await screen.findByRole("button", { name: "Usar este corte" });
    await waitFor(() => expect(confirm).toBeEnabled());
    await userEvent.click(confirm);
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível atualizar a logo");
  });

  it("sem violações no axe", async () => {
    render(<LogoCropDialog file={file} onCancel={() => {}} onConfirm={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("button", { name: "Usar este corte" })).toBeEnabled());
    expect(await axeViolations(document.body)).toEqual([]);
  });
});
