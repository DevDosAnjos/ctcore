import { safeNext } from "./safeNext";

describe("safeNext (CA7)", () => {
  it.each([
    ["/alunos?x=1", "/alunos?x=1"],
    ["//evil.com", "/"],
    ["https://x", "/"],
    ["/\\evil.com", "/"],
    ["", "/"],
    [null, "/"],
  ])("%s vira %s", (entrada, saida) => {
    expect(safeNext(entrada)).toBe(saida);
  });
});
