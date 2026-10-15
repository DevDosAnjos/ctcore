import axe from "axe-core";

// jsdom não mede contraste; a regra color-contrast fica desligada aqui e o contraste
// dos tokens é coberto por theme.test.tsx (D8).
export async function axeViolations(container: Element) {
  const result = await axe.run(container, { rules: { "color-contrast": { enabled: false } } });
  return result.violations;
}
