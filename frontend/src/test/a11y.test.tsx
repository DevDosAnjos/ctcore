import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { SessionProvider } from "@/features/auth/SessionProvider";
import { App } from "@/App";
import { buttonVariants } from "@/components/ui/button";
import { ThemeProvider } from "@/theme/ThemeProvider";
import { setDevicePrefersDark } from "./device";
import { axeViolations } from "./axe";
import { Showcase } from "./Showcase";

function shell() {
  return render(
    <ThemeProvider>
      <MemoryRouter>
        <SessionProvider>
          <App />
        </SessionProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe("casca (CA13)", () => {
  it.each([
    ["claro", false],
    ["escuro", true],
  ])("sem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = shell();
    await screen.findByRole("heading", { name: "Entrar" });
    expect(document.documentElement.classList.contains("dark")).toBe(dark);
    expect(await axeViolations(container)).toEqual([]);
  });

  // jsdom não faz layout: confere a ausência de larguras fixas acima de 360 px e o viewport (D8).
  it("sem largura fixa maior que 360 px", () => {
    const files = (dir: string): string[] =>
      readdirSync(dir).flatMap((f) => {
        const p = join(dir, f);
        return statSync(p).isDirectory() ? files(p) : /\.(tsx|css)$/.test(f) && !f.includes(".test.") ? [p] : [];
      });
    const src = join(process.cwd(), "src");
    for (const f of files(src)) {
      const text = readFileSync(f, "utf8");
      for (const m of text.matchAll(/\b(?:min-w|w)-\[(\d+)px\]/g)) {
        expect(Number(m[1]), `${f}: ${m[0]}`).toBeLessThanOrEqual(360);
      }
    }
    expect(readFileSync(join(process.cwd(), "index.html"), "utf8")).toContain("width=device-width");
  });
});

describe("componentes (CA21, CA22)", () => {
  it.each([
    ["claro", false],
    ["escuro", true],
  ])("Showcase sem violações no axe no tema %s", async (_n, dark) => {
    setDevicePrefersDark(dark);
    window.innerWidth = 360;
    const { container } = render(
      <ThemeProvider>
        <Showcase />
      </ThemeProvider>,
    );
    expect(await axeViolations(container)).toEqual([]);
  });

  it.each([["modal"], ["panel"], ["confirm"]])("janela aberta (%s) sem violações no axe", async (which) => {
    setDevicePrefersDark(false);
    render(
      <ThemeProvider>
        <Showcase modal={which === "modal"} panel={which === "panel"} confirm={which === "confirm"} />
      </ThemeProvider>,
    );
    expect(await axeViolations(document.body)).toEqual([]);
  });

  it.each([["default"], ["sm"], ["icon"]] as const)("RNF-20: botão de tamanho %s tem alvo de 44 px", (size) => {
    expect(buttonVariants({ size }).split(" ")).toEqual(expect.arrayContaining(["after:min-h-11", "after:min-w-11"])); // D2: alvo por área invisível
  });

  it("RNF-20: botão só de ícone é quadrado de 44 px; links do menu e paginação também", () => {
    expect(buttonVariants({ size: "icon" }).split(" ")).toEqual(expect.arrayContaining(["after:min-h-11", "after:min-w-11"]));
    const dir = join(process.cwd(), "src/components");
    for (const f of ["layout/AppLayout.tsx", "query/Pagination.tsx"]) {
      expect(readFileSync(join(dir, f), "utf8"), f).toMatch(/min-h-11/);
    }
  });
});
