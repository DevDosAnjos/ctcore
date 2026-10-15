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

  it.each([
    ["claro", light],
    ["escuro", dark],
  ])("pares texto/fundo têm contraste mínimo de 4,5:1 no tema %s", (_n, t) => {
    for (const [fg, bg] of [
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
