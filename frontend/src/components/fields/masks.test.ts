import { formatCpf, isValidCpf, formatPhone, isValidPhone, formatMoney, parseMoney, parsePastedMoney, formatDate, parseDate, isValidCnpj, formatCpfCnpj, isValidCpfOrCnpj } from "./masks";

describe("masks", () => {
  it("CPF", () => {
    expect(formatCpf("52998224725")).toBe("529.982.247-25");
    expect(formatCpf("5299")).toBe("529.9");
    expect(isValidCpf("52998224725")).toBe(true);
    expect(isValidCpf("52998224726")).toBe(false);
    expect(isValidCpf("11111111111")).toBe(false);
    expect(isValidCpf("529")).toBe(false);
  });
  it("telefone", () => {
    expect(formatPhone("11912345678")).toBe("(11) 91234-5678");
    expect(formatPhone("1112345678")).toBe("(11) 1234-5678");
    expect(formatPhone("1")).toBe("(1");
    expect(formatPhone("")).toBe("");
    expect(isValidPhone("11912345678")).toBe(true);
    expect(isValidPhone("1112345678")).toBe(true);
    expect(isValidPhone("01912345678")).toBe(false);
    expect(isValidPhone("11812345678")).toBe(false);
    expect(isValidPhone("111234")).toBe(false);
  });
  it("telefone colado com o código do país perde o 55; DDD 55 fica", () => {
    expect(formatPhone("+55 92 99123-4567")).toBe("(92) 99123-4567");
    expect(isValidPhone("+55 92 99123-4567")).toBe(true);
    expect(formatPhone("+55 (11) 3456-7890")).toBe("(11) 3456-7890");
    expect(formatPhone("(55) 99123-4567")).toBe("(55) 99123-4567");
    expect(isValidPhone("55991234567")).toBe(true);
  });
  it.each([
    ["R$ 1.250", 125000],
    ["1250", 125000],
    ["1.250,5", 125050],
    ["1250,00", 125000],
    ["12.50", 1250],
    ["1.250.000", 125000000],
    ["R$ 0,05", 5],
    ["abc", null],
    ["", null],
    ["-50,00", null],
    ["1,2,3", null],
    ["99999999999", null],
  ])("moeda colada %s vale %s centavos", (text, cents) => {
    expect(parsePastedMoney(text)).toBe(cents);
  });
  it("moeda em centavos", () => {
    expect(parseMoney("R$ 1.250,00abc")).toBe(125000);
    expect(parseMoney("abc")).toBeNull();
    expect(formatMoney(125000)).toBe("R$ 1.250,00");
    expect(formatMoney(5)).toBe("R$ 0,05");
    expect(formatMoney(null)).toBe("");
  });
  it("data", () => {
    expect(formatDate("08102026")).toBe("08/10/2026");
    expect(formatDate("0810")).toBe("08/10");
    expect(parseDate("08/10/2026")).toBe("2026-10-08");
    expect(parseDate("31/02/2026")).toBeNull();
    expect(parseDate("29/02/2024")).toBe("2024-02-29");
    expect(parseDate("08/10")).toBeNull();
  });
});

describe("CNPJ e titular", () => {
  it("valida os dígitos verificadores do CNPJ", () => {
    expect(isValidCnpj("11.222.333/0001-81")).toBe(true);
    expect(isValidCnpj("11222333000182")).toBe(false);
    expect(isValidCnpj("11111111111111")).toBe(false);
    expect(isValidCnpj("123")).toBe(false);
  });

  it("formata CPF até 11 dígitos e CNPJ acima, e aceita os dois", () => {
    expect(formatCpfCnpj("52998224725")).toBe("529.982.247-25");
    expect(formatCpfCnpj("11222333000181")).toBe("11.222.333/0001-81");
    expect(formatCpfCnpj("112223330001")).toBe("11.222.333/0001");
    expect(formatCpfCnpj("1122233300018199")).toBe("11.222.333/0001-81");
    expect(isValidCpfOrCnpj("529.982.247-25")).toBe(true);
    expect(isValidCpfOrCnpj("11.222.333/0001-81")).toBe(true);
    expect(isValidCpfOrCnpj("529.982.247-26")).toBe(false);
  });
});
