import { formatCpf, isValidCpf, formatPhone, isValidPhone, formatMoney, parseMoney, formatDate, parseDate } from "./masks";

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
