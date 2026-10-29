import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelham PixInstructions, PixBatchInstructions e PaymentProofSent do OpenAPI (RF-COB-05, RF-POR-05).
export interface PixInstructions {
  pixKey: string;
  amountCents: number;
  unit: string;
  /** Só ao pagar várias juntas: as cobranças que somam `amountCents`. */
  charges?: { chargeId: string; studentName: string; amountCents: number; dueOn: string }[];
}
interface SingleInstructions extends PixInstructions {
  chargeId: string;
  dueOn: string;
}
interface BatchInstructions {
  pixKey: string;
  totalCents: number;
  unit: string;
  charges: NonNullable<PixInstructions["charges"]>;
}
type Sent = { id: string; chargeId: string; status: "PENDING"; sentAt: string };

export type PixInstructionsState =
  | { status: "loading" }
  | { status: "ready"; data: PixInstructions }
  | { status: "noKey" | "badSelection" | "notPayable" | "forbidden" | "notFound" | "error" };

// Chave Pix da unidade para pagar uma cobrança (rota dela) ou várias juntas (D2 da #92). 422 é unidade sem chave (ou seleção inválida); 409, cobrança que não está mais aberta.
export function usePixInstructions(chargeIds: string[]) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<{ key: string; state: PixInstructionsState }>();
  const ids = chargeIds.join(",");
  const key = `${ids}|${tick}`;
  useEffect(() => {
    let alive = true;
    const request: Promise<PixInstructions> =
      chargeIds.length === 1
        ? api<SingleInstructions>(`/portal/charges/${chargeIds[0]}/pix-instructions`)
        : api<BatchInstructions>(`/portal/charges/pix-instructions?ids=${ids}`).then((b) => ({ pixKey: b.pixKey, amountCents: b.totalCents, unit: b.unit, charges: b.charges }));
    request.then(
      (data) => alive && setDone({ key, state: { status: "ready", data } }),
      (e) => {
        const status = e instanceof ApiError ? e.status : 0;
        const selection = e instanceof ApiError && e.fields.some((f) => f.field === "ids" || f.field === "chargeIds");
        const state = status === 422 ? (selection ? "badSelection" : "noKey") : status === 409 ? "notPayable" : status === 403 ? "forbidden" : status === 404 ? "notFound" : "error";
        if (alive) setDone({ key, state: { status: state } });
      },
    );
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `ids` e `key` resumem `chargeIds`
  }, [ids, key]);
  const state: PixInstructionsState = done?.key === key ? done.state : { status: "loading" };
  return { state, reload: () => setTick((n) => n + 1) };
}

// Um arquivo para uma ou várias cobranças; com várias, um comprovante por cobrança (tudo ou nada).
export function sendProof(chargeIds: string[], file: File) {
  const body = new FormData();
  body.append("file", file);
  if (chargeIds.length === 1) return api<Sent>(`/portal/charges/${chargeIds[0]}/proof`, { method: "POST", body });
  for (const id of chargeIds) body.append("chargeIds", id);
  return api<Sent[]>("/portal/charges/proof", { method: "POST", body });
}
