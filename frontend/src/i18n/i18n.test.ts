import i18n from "./index";

describe("i18n (CA9)", () => {
  it("chave existente traduz", () => {
    expect(i18n.t("theme.toDark")).toBe("Usar tema escuro");
  });

  it("chave inexistente falha", () => {
    expect(() => i18n.t("nao.existe")).toThrow(/ausente/);
  });
});
