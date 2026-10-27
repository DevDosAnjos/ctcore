import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelha PortalCard do OpenAPI (docs/openapi.yaml). Sem presença, frequência, progresso, CPF, telefone nem e-mail (RN-POR-09).
export interface CardBelt {
  levelName: string;
  color: string;
  secondaryColor: string | null;
  degree: number;
}
export interface CardModality {
  modality: { id: string; name: string };
  unit: { id: string; name: string };
  belt: CardBelt | null;
}
export interface PortalCardData {
  organization: { name: string; color: string | null; logoUrl: string | null };
  student: { id: string; name: string; photoUrl: string | null };
  students: { id: string; name: string; self: boolean }[];
  status: "ACTIVE" | "INACTIVE";
  today: string;
  generatedAt: string;
  timezone: string;
  modalities: CardModality[];
}

type Failure = "forbidden" | "notFound" | "error";
type Done = {
  key: string;
  data?: PortalCardData;
  offline?: boolean;
  failure?: Failure;
};

const storageKey = (studentId: string | null) => `ctcore.portalCard.${studentId ?? "first"}`;

function readCopy(studentId: string | null): PortalCardData | undefined {
  try {
    const raw = window.localStorage.getItem(storageKey(studentId));
    return raw ? (JSON.parse(raw) as PortalCardData) : undefined;
  } catch {
    return undefined;
  }
}

function writeCopy(studentId: string | null, data: PortalCardData) {
  try {
    window.localStorage.setItem(storageKey(studentId), JSON.stringify(data));
  } catch {
    // Sem armazenamento: a tela segue só com a leitura online.
  }
}

/**
 * Carteirinha do aluno escolhido (nulo: o primeiro cartão). Cada leitura boa fica guardada por aluno; só a falha de rede (não 4xx/5xx) mostra a cópia, com
 * `offline` (RN-POR-11, D3). Erro de API nunca usa a cópia, para não exibir carteirinha de quem perdeu o acesso.
 */
export function usePortalCard(studentId: string | null) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Done>();
  const key = studentId ?? "first";
  useEffect(() => {
    let alive = true;
    api<PortalCardData>(`/portal/card${studentId ? `?studentId=${studentId}` : ""}`).then(
      (data) => {
        writeCopy(studentId, data);
        writeCopy(data.student.id, data); // a mesma carteirinha é achada pelo id quando o seletor a pedir sem rede
        if (alive) setDone({ key, data });
      },
      (e) => {
        if (!alive) return;
        if (e instanceof ApiError && e.status === 0) {
          const copy = readCopy(studentId);
          setDone(copy ? { key, data: copy, offline: true } : { key, failure: "error" });
        } else
          setDone({
            key,
            failure: e instanceof ApiError && e.status === 403 ? "forbidden" : e instanceof ApiError && e.status === 404 ? "notFound" : "error",
          });
      },
    );
    return () => {
      alive = false;
    };
  }, [studentId, key, tick]);
  const current = done?.key === key ? done : undefined;
  return {
    status: !current ? ("loading" as const) : (current.failure ?? ("ready" as const)),
    data: current?.data,
    offline: current?.offline ?? false,
    reload: () => setTick((n) => n + 1),
  };
}
