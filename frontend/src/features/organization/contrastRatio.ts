// RNF-22: mesma fórmula do servidor (BrandColor.java): razão WCAG da cor contra texto branco; AA pede 4,5.
export const MIN_CONTRAST = 4.5;

export const isHexColor = (value: string) => /^#[0-9a-fA-F]{6}$/.test(value);

function luminance(hex: string): number {
  const channels = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r, g, b] = channels.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Razão de 1 a 21 entre a cor `#RRGGBB` e o branco. */
export function contrastRatio(hex: string): number {
  return 1.05 / (luminance(hex) + 0.05);
}

export const passesContrast = (hex: string) => contrastRatio(hex) >= MIN_CONTRAST;
