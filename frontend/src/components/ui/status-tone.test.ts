import ptBR from "@/i18n/pt-BR.json";
import { hasTone, toneOf } from "./status-tone";

type Tree = { [k: string]: string | Tree };

// Chaves em maiúsculas dentro dos objetos status/statuses/situation do pt-BR.json.
function statusKeys(node: Tree, inStatus = false, out = new Set<string>()): Set<string> {
  for (const [k, v] of Object.entries(node)) {
    if (typeof v === "string") {
      if (inStatus && /^[A-Z][A-Z_]+$/.test(k)) out.add(k);
    } else statusKeys(v, ["status", "statuses", "situation"].includes(k), out);
  }
  return out;
}

// Tipos do código (StoredStatus, ProofStatus, PortalRequestStatus, EnrollmentStatus, InviteStatus, cobranças).
const FROM_TYPES = [
  "OPEN", "IN_REVIEW", "PAID", "CANCELED", "RENEGOTIATED", "REFUNDED", "PENDING", "CONFIRMED", "REFUSED",
  "APPROVED", "EXPIRED", "CLOSED", "NONE", "ACTIVE", "FORMER", "SENT", "FAILED", "MINOR", "EXISTING_ACCOUNT",
  "NO_EMAIL", "DUE_TODAY", "DUE_SOON", "OVERDUE", "PAID_LATE", "BLOCKED", "CLOSURE_REQUEST", "EMAIL_PROBLEM", "BIRTHDAY_TODAY",
];

describe("statusTone (CA4)", () => {
  it("toda situação do pt-BR.json e dos tipos tem tom", () => {
    const all = [...new Set([...statusKeys(ptBR as Tree), ...FROM_TYPES])];
    expect(all.filter((k) => !hasTone(k))).toEqual([]);
  });
  it("tons da decisão D3 e chave desconhecida", () => {
    expect(toneOf("OVERDUE")).toBe("danger");
    expect(toneOf("PAID")).toBe("ok");
    expect(toneOf("DUE_SOON")).toBe("warn");
    expect(toneOf("IN_REVIEW")).toBe("info");
    expect(toneOf("BIRTHDAY_TODAY")).toBe("acc");
    expect(toneOf("XYZ")).toBe("neutral");
  });
});
