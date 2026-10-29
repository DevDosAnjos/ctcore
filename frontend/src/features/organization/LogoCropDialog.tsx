import { useEffect, useRef, useState, type PointerEvent } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { LOGO_SIZE } from "./logoFile";

const PREVIEW = 256;
const clamp = (n: number) => Math.min(100, Math.max(0, n));

// Corte quadrado da logo (RF-ORG-01, D6): feito no navegador, saída 512 x 512 em PNG; o servidor só confere o resultado.
export function LogoCropDialog({
  file,
  onCancel,
  onConfirm,
  labels,
}: {
  file: File;
  onCancel: () => void;
  onConfirm: (logo: Blob) => Promise<void>;
  /** Textos próprios quando o corte serve a outra imagem (ex.: foto do professor); o padrão é o da logo. */
  labels?: { title: string; hint: string; preview: string; error: string };
}) {
  const { t } = useTranslation();
  const canvas = useRef<HTMLCanvasElement>(null);
  const drag = useRef<{ x: number; y: number } | null>(null);
  const [image, setImage] = useState<HTMLImageElement | null>(null);
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [zoom, setZoom] = useState(1);
  const [px, setPx] = useState(50);
  const [py, setPy] = useState(50);

  useEffect(() => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => setImage(img);
    img.onerror = () => setFailed(true);
    img.src = url;
    return () => URL.revokeObjectURL(url);
  }, [file]);

  // Quadrado de origem: lado = menor dimensão / ampliação; a posição (0 a 100) anda dentro do que sobra.
  function draw(target: HTMLCanvasElement, size: number) {
    const ctx = target.getContext("2d");
    if (!ctx || !image) return;
    const side = Math.min(image.width, image.height) / zoom;
    target.width = size;
    target.height = size;
    ctx.drawImage(image, ((image.width - side) * px) / 100, ((image.height - side) * py) / 100, side, side, 0, 0, size, size);
  }

  useEffect(() => {
    if (canvas.current) draw(canvas.current, PREVIEW);
  });

  function move(e: PointerEvent<HTMLCanvasElement>) {
    if (!drag.current || !image) return;
    const side = Math.min(image.width, image.height) / zoom;
    const dx = e.clientX - drag.current.x;
    const dy = e.clientY - drag.current.y;
    drag.current = { x: e.clientX, y: e.clientY };
    // Arrastar a imagem para a direita leva o quadrado para a esquerda.
    setPx((p) => clamp(p - (dx / PREVIEW) * 100 * (side / Math.max(image.width - side, 1))));
    setPy((p) => clamp(p - (dy / PREVIEW) * 100 * (side / Math.max(image.height - side, 1))));
  }

  async function confirm() {
    const out = document.createElement("canvas");
    draw(out, LOGO_SIZE);
    const blob = await new Promise<Blob | null>((resolve) => out.toBlob(resolve, "image/png"));
    setBusy(true);
    try {
      if (!blob) throw new Error("crop");
      await onConfirm(blob);
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }

  const slider = (label: string, value: number, set: (n: number) => void, min: number, max: number, step: number) => (
    <label className="flex flex-col gap-1 text-sm font-medium">
      {label}
      <input type="range" min={min} max={max} step={step} value={value} onChange={(e) => set(Number(e.target.value))} className="min-h-11" />
    </label>
  );

  return (
    <Modal open onOpenChange={(open) => !open && onCancel()} title={labels?.title ?? t("organization.crop.title")}>
      <div className="flex flex-col gap-3">
        <p className="text-sm text-muted-foreground">{labels?.hint ?? t("organization.crop.hint")}</p>
        {failed && (
          <Banner tone="danger">{image ? (labels?.error ?? t("organization.logoError")) : t("organization.crop.loadError")}</Banner>
        )}
        <canvas
          ref={canvas}
          role="img"
          aria-label={labels?.preview ?? t("organization.crop.preview")}
          width={PREVIEW}
          height={PREVIEW}
          className="mx-auto size-64 max-w-full touch-none rounded-md border"
          onPointerDown={(e) => {
            drag.current = { x: e.clientX, y: e.clientY };
            e.currentTarget.setPointerCapture?.(e.pointerId);
          }}
          onPointerMove={move}
          onPointerUp={() => (drag.current = null)}
          onPointerCancel={() => (drag.current = null)}
        />
        {slider(t("organization.crop.zoom"), zoom, setZoom, 1, 4, 0.05)}
        {slider(t("organization.crop.horizontal"), px, setPx, 0, 100, 1)}
        {slider(t("organization.crop.vertical"), py, setPy, 0, 100, 1)}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel}>
            {t("common.cancel")}
          </Button>
          <Button disabled={!image || busy} onClick={confirm}>
            {busy ? t("organization.crop.sending") : t("organization.crop.confirm")}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
