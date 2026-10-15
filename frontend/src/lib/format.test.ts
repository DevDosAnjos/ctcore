import { formatCurrency, formatDate, formatDateTime, formatPhoneDisplay, formatTime, relativeDay } from "./format";

describe("formatadores (RNF-23)", () => {
  it("CA1 RNF-23: data, hora e data e hora em Brasília", () => {
    const i = "2026-10-08T22:30:00Z";
    expect(formatDate(i)).toBe("08/10/2026");
    expect(formatTime(i)).toBe("19h30");
    expect(formatDateTime(i)).toBe("08/10/2026 19h30");
    expect(formatTime("2026-10-08T22:05:00Z")).toBe("19h05");
    expect(formatTime("2026-10-08T22:00:00Z")).toBe("19h00");
  });
  it("CA2 RNF-23: moeda a partir de centavos", () => {
    expect(formatCurrency(125000)).toBe("R$ 1.250,00");
    expect(formatCurrency(0)).toBe("R$ 0,00");
    expect(formatCurrency(5)).toBe("R$ 0,05");
  });
  it("CA3 RNF-23: telefone", () => {
    expect(formatPhoneDisplay("11912345678")).toBe("(11) 91234-5678");
    expect(formatPhoneDisplay("1112345678")).toBe("(11) 1234-5678");
    expect(formatPhoneDisplay("(11) 91234-5678")).toBe("(11) 91234-5678");
    expect(formatPhoneDisplay("abc")).toBe("abc");
  });
  it("CA4 RNF-23: usa o fuso recebido, não o da máquina", () => {
    const i = "2026-10-09T02:30:00Z";
    expect(formatDateTime(i, "America/Sao_Paulo")).toBe("08/10/2026 23h30");
    expect(formatDateTime(i, "America/Manaus")).toBe("08/10/2026 22h30");
    expect(formatDate(i, "Asia/Tokyo")).toBe("09/10/2026");
  });
  it("CA5 RNF-23: meia-noite local não vira 24h nem dia anterior", () => {
    expect(formatDateTime("2026-10-08T03:00:00Z")).toBe("08/10/2026 00h00");
  });
  it("RNF-23: data inválida vira vazio e dia relativo nulo", () => {
    expect(formatDate("x")).toBe("");
    expect(formatTime("x")).toBe("");
    expect(formatDateTime("x")).toBe("");
    expect(relativeDay("x")).toBeNull();
  });
  it("CA6 RNF-23: dia relativo conta dias de calendário", () => {
    const now = "2026-10-08T03:10:00Z"; // 00h10 de 08/10 em Brasília
    expect(relativeDay("2026-10-08T02:00:00Z", now)).toBe(1); // 23h de 07/10
    expect(relativeDay("2026-10-08T03:05:00Z", now)).toBe(0);
  });
});
