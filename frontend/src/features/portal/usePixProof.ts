import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelham PixInstructions e PaymentProofSent do OpenAPI (RF-COB-05).
export interface PixInstructions {
  chargeId: string;
  pixKey: string;
  amountCents: number;
  dueOn: string;
  unit: string;
}

export type PixInstructionsState =
  | { status: "loading" }
  | { status: "ready"; data: PixInstructions }
  | { status: "noKey" | "notPayable" | "forbidden" | "notFound" | "error" };

// Chave Pix da unidade para pagar a cobrança. 422 é unidade sem chave; 409, cobrança que não está mais aberta.
export function usePixInstructions(chargeId: string) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<{ key: string; state: PixInstructionsState }>();
  const key = `${chargeId}|${tick}`;
  useEffect(() => {
    let alive = true;
    api<PixInstructions>(`/portal/charges/${chargeId}/pix-instructions`).then(
      (data) => alive && setDone({ key, state: { status: "ready", data } }),
      (e) => {
        const status = e instanceof ApiError ? e.status : 0;
        const state = status === 422 ? "noKey" : status === 409 ? "notPayable" : status === 403 ? "forbidden" : status === 404 ? "notFound" : "error";
        if (alive) setDone({ key, state: { status: state } });
      },
    );
    return () => {
      alive = false;
    };
  }, [chargeId, key]);
  const state: PixInstructionsState = done?.key === key ? done.state : { status: "loading" };
  return { state, reload: () => setTick((n) => n + 1) };
}

export function sendProof(chargeId: string, file: File) {
  const body = new FormData();
  body.append("file", file);
  return api<{ id: string; chargeId: string; status: "PENDING"; sentAt: string }>(`/portal/charges/${chargeId}/proof`, { method: "POST", body });
}
