export const LOGO_SIZE = 512;
export const LOGO_MAX_BYTES = 5 * 1024 * 1024;
export const LOGO_TYPES = ["image/png", "image/jpeg", "image/webp"];

/** Confere no navegador antes do corte; o servidor confere de novo pelo conteúdo (RF-ORG-01). */
export function checkLogoFile(file: File): "type" | "size" | null {
  if (!LOGO_TYPES.includes(file.type)) return "type";
  if (file.size > LOGO_MAX_BYTES) return "size";
  return null;
}
