export type Theme = "light" | "dark";

// Mesma chave e mesma regra do script de index.html (que evita o flash).
export const THEME_KEY = "ctcore-theme";

export function readStoredTheme(): Theme | null {
  try {
    const v = window.localStorage.getItem(THEME_KEY);
    return v === "light" || v === "dark" ? v : null;
  } catch {
    return null;
  }
}

export function saveTheme(theme: Theme): void {
  try {
    window.localStorage.setItem(THEME_KEY, theme);
  } catch {
    // Sem armazenamento: a escolha vale só nesta visita.
  }
}

export function deviceTheme(): Theme {
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function initialTheme(): Theme {
  return readStoredTheme() ?? deviceTheme();
}

export function applyTheme(theme: Theme): void {
  document.documentElement.classList.toggle("dark", theme === "dark");
}
