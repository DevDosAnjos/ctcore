import { useState } from "react";
import { initials } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import { isHexColor, passesContrast } from "@/features/organization/contrastRatio";

/** D3: a cor da organização só vale no formato #RRGGBB e com contraste AA contra o texto branco; senão, vale o `--brand`. */
export const orgColor = (color: string | null | undefined): string | undefined => (color && isHexColor(color) && passesContrast(color) ? color : undefined);

/** Estilo que entrega a cor aprovada às classes `bg-[var(--org,var(--brand))]`. */
export const orgStyle = (color: string | null | undefined) => {
  const ok = orgColor(color);
  return ok ? ({ "--org": ok } as React.CSSProperties) : undefined;
};

/** Logo da organização; sem imagem (ou com imagem quebrada), quadrado com as iniciais. */
export function OrgMark({ name, logoUrl, className }: { name: string; logoUrl?: string | null; className?: string }) {
  const [broken, setBroken] = useState(false);
  if (logoUrl && !broken) return <img src={logoUrl} alt="" className={cn("size-9 shrink-0 rounded-md object-contain", className)} onError={() => setBroken(true)} />;
  return (
    <span aria-hidden="true" data-initials={initials(name).slice(0, 2)} className={cn("inline-flex size-9 shrink-0 before:content-[attr(data-initials)] items-center justify-center rounded-md bg-[var(--org,var(--brand))] text-sm font-semibold text-brand-foreground", className)} />
  );
}
