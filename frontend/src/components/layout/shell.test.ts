import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const walk = (dir: string): string[] =>
  readdirSync(dir).flatMap((n) => {
    const p = join(dir, n);
    return statSync(p).isDirectory() ? walk(p) : [p];
  });

describe("moldura da gestão (CA8)", () => {
  const pages = walk("src/features").filter((f) => /Page\.tsx$/.test(f) && readFileSync(f, "utf8").includes("<ManagementLayout"));

  it("encontra as páginas da gestão", () => {
    expect(pages.length).toBeGreaterThanOrEqual(29);
  });

  it.each(pages)("%s usa PageHeader e nenhum <h1 próprio", (f) => {
    const src = readFileSync(f, "utf8");
    expect(src).not.toContain("<h1");
    expect(src).toContain("<PageHeader");
  });
});
