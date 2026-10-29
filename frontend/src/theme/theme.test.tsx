import { readFileSync } from "node:fs";
import { join } from "node:path";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import { ThemeProvider } from "./ThemeProvider";
import { ThemeToggle } from "./ThemeToggle";
import { THEME_KEY } from "./theme";
import { setDevicePrefersDark } from "@/test/device";

const css = readFileSync(join(process.cwd(), "src/index.css"), "utf8");

function tokens(selector: string): Record<string, string> {
  const block = css.match(new RegExp(`${selector.replace(".", "\\.")}\\s*\\{([^}]*)\\}`))![1];
  return Object.fromEntries([...block.matchAll(/--([\w-]+):\s*([^;]+);/g)].map((m) => [m[1], m[2].trim()]));
}

function luminance(hex: string) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) =>
    c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4,
  );
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
function contrast(a: string, b: string) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

function app() {
  return render(
    <ThemeProvider>
      <ThemeToggle />
    </ThemeProvider>,
  );
}

describe("tokens (CA4)", () => {
  const light = tokens(":root");
  const dark = tokens(".dark");

  it("claro e escuro definem os mesmos tokens, incluindo cor e tipografia", () => {
    expect(Object.keys(dark).sort()).toEqual(Object.keys(light).sort());
    for (const k of ["background", "foreground", "primary", "border", "destructive", "ring", "font-sans", "text-base"]) {
      expect(light).toHaveProperty(k);
    }
  });

  // [token, claro, escuro] — tabela de docs/design/README.md (e D1/D2 do item 414)
  const table: [string, string, string][] = [
    ["background", "#F4F2ED", "#141416"],
    ["card", "#FFFFFF", "#1D1D21"],
    ["popover", "#FFFFFF", "#1D1D21"],
    ["surface-soft", "#FAF8F4", "#19191C"],
    ["secondary", "#FAF8F4", "#19191C"],
    ["muted", "#FAF8F4", "#19191C"],
    ["accent", "#FAF8F4", "#19191C"],
    ["foreground", "#1B1B1F", "#ECE9E2"],
    ["card-foreground", "#1B1B1F", "#ECE9E2"],
    ["muted-foreground", "#6B675F", "#A8A398"],
    ["subtle", "#8C877D", "#8F8A80"],
    ["border", "#E3DFD6", "#34343A"],
    ["divider", "#EEEAE2", "#2A2A2F"],
    ["input", "#D9D4C9", "#45454C"],
    ["brand", "#C8462C", "#C8462C"],
    ["ring", "#B23C24", "#F08A70"],
    ["primary", "#1B1B1F", "#ECE9E2"],
    ["primary-foreground", "#FFFFFF", "#1D1D21"],
    ["sidebar", "#17171A", "#17171A"],
    ["sidebar-foreground", "#D8D4CB", "#D8D4CB"],
    ["sidebar-accent", "#2B2B30", "#2B2B30"],
    ["ok", "#E2EFE5", "#1E3626"],
    ["ok-foreground", "#1D5E34", "#8FD3A3"],
    ["warn", "#FAEBC8", "#3D3018"],
    ["warn-foreground", "#7A4E00", "#F0C674"],
    ["danger", "#F8DED8", "#45231D"],
    ["danger-foreground", "#9C2516", "#F4A08F"],
    ["info", "#E1E8F4", "#1E2A40"],
    ["info-foreground", "#23427A", "#A9C1EA"],
    ["neutral", "#ECE9E2", "#2C2C31"],
    ["neutral-foreground", "#4A4740", "#CFCAC0"],
    ["acc", "#FBE7E1", "#43241C"],
    ["acc-foreground", "#9C2F18", "#F2A48F"],
  ];

  it.each(table)("%s tem o valor da tabela do design nos dois temas (CA1 a CA3)", (k, l, d) => {
    expect(light[k].toUpperCase()).toBe(l);
    expect(dark[k].toUpperCase()).toBe(d);
  });

  it("raios, tamanho base, sombras e fontes (CA4, CA5)", () => {
    for (const t of [light, dark]) {
      expect(t).toMatchObject({
        radius: "0.5rem",
        "radius-card": "0.75rem",
        "radius-window": "1rem",
        "text-base": "0.875rem",
      });
      expect(t["shadow-window"]).toBeTruthy();
      expect(t["shadow-panel"]).toBeTruthy();
      expect(t["shadow-menu"]).toBeTruthy();
      expect(t["font-sans"]).toMatch(/^"IBM Plex Sans"/);
      expect(t["font-heading"]).toMatch(/^"Barlow Condensed"/);
    }
  });

  it("tons e menu estão expostos ao Tailwind (CA2)", () => {
    for (const n of ["ok", "warn", "danger", "info", "neutral", "acc"]) {
      expect(css).toContain(`--color-${n}: var(--${n});`);
      expect(css).toContain(`--color-${n}-foreground: var(--${n}-foreground);`);
    }
  });

  it.each([
    ["claro", light],
    ["escuro", dark],
  ])("pares texto/fundo têm contraste mínimo de 4,5:1 no tema %s", (_n, t) => {
    for (const [fg, bg] of [
      ...["ok", "warn", "danger", "info", "neutral", "acc"].map((n) => [`${n}-foreground`, n]),
      ["foreground", "card"],
      ["muted-foreground", "card"],
      ["sidebar-foreground", "sidebar"],
      ["ring", "background"],
      ["foreground", "background"],
      ["card-foreground", "card"],
      ["popover-foreground", "popover"],
      ["primary-foreground", "primary"],
      ["secondary-foreground", "secondary"],
      ["muted-foreground", "background"],
      ["muted-foreground", "muted"],
      ["accent-foreground", "accent"],
      ["destructive", "background"],
      ["destructive-foreground", "destructive"],
    ]) {
      expect(contrast(t[fg], t[bg]), `${fg} sobre ${bg}`).toBeGreaterThanOrEqual(4.5);
    }
  });
});

describe("tema (CA5 a CA7)", () => {
  it("segue o aparelho escuro sem escolha salva", () => {
    setDevicePrefersDark(true);
    app();
    expect(document.documentElement).toHaveClass("dark");
  });

  it("segue o aparelho claro sem escolha salva", () => {
    app();
    expect(document.documentElement).not.toHaveClass("dark");
  });

  it("botão alterna, tem nome acessível e salva; o salvo vence o aparelho ao recarregar", async () => {
    const user = userEvent.setup();
    const { unmount } = app();
    await user.click(screen.getByRole("button", { name: "Usar tema escuro" }));
    expect(document.documentElement).toHaveClass("dark");
    expect(window.localStorage.getItem(THEME_KEY)).toBe("dark");
    await user.click(screen.getByRole("button", { name: "Usar tema claro" }));
    expect(document.documentElement).not.toHaveClass("dark");
    expect(window.localStorage.getItem(THEME_KEY)).toBe("light");

    unmount();
    document.documentElement.classList.remove("dark");
    setDevicePrefersDark(true); // aparelho escuro, mas a escolha salva é clara
    app();
    expect(document.documentElement).not.toHaveClass("dark");
  });

  it("armazenamento com falha ou vazio segue o aparelho, sem erro", async () => {
    setDevicePrefersDark(true);
    const get = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("bloqueado");
    });
    const set = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("bloqueado");
    });
    app();
    expect(document.documentElement).toHaveClass("dark");
    await userEvent.setup().click(screen.getByRole("button", { name: "Usar tema claro" }));
    expect(document.documentElement).not.toHaveClass("dark");
    get.mockRestore();
    set.mockRestore();
  });

  it("index.html aplica o tema antes da pintura", () => {
    const html = readFileSync(join(process.cwd(), "index.html"), "utf8");
    expect(html).toContain(THEME_KEY);
    expect(html.indexOf("classList.toggle")).toBeLessThan(html.indexOf('type="module"'));
  });
});
