import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

export type TextGroup = "REMINDERS" | "WELCOME" | "ANNOUNCEMENTS";

// Espelham TextModel, TextModelList e TextModelInput do OpenAPI (docs/openapi.yaml).
export interface TextPair {
  subject: string;
  body: string;
}
export interface TextModel {
  key: string;
  group: TextGroup;
  fields: string[];
  platform: TextPair;
  custom: TextPair | null;
  updatedAt: string | null;
  updatedBy: string | null;
}
export interface TextModelList {
  canManage: boolean;
  models: TextModel[];
}

type Loaded = { tick: number; data?: TextModelList; forbidden?: boolean };

// Configurações › Textos (RF-COM-03). O servidor decide quem acessa (403) e quem altera (canManage).
export function useTexts() {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();

  useEffect(() => {
    let alive = true;
    api<TextModelList>("/organization/texts").then(
      (data) => alive && setDone({ tick, data }),
      (e) => alive && setDone({ tick, forbidden: e instanceof ApiError && e.status === 403 }),
    );
    return () => {
      alive = false;
    };
  }, [tick]);

  const current = done?.tick === tick ? done : undefined;
  const status = !current ? "loading" : current.data ? "ready" : current.forbidden ? "forbidden" : "error";
  const replace = (model: TextModel) =>
    setDone((d) => (d?.data ? { ...d, data: { ...d.data, models: d.data.models.map((m) => (m.key === model.key ? model : m)) } } : d));

  return {
    status,
    data: current?.data,
    retry: () => setTick((n) => n + 1),
    // Erros sobem (ApiError) para a tela mostrar junto do campo.
    save: async (key: string, input: TextPair) => replace(await api<TextModel>(`/organization/texts/${key}`, { method: "PUT", body: input })),
    reset: async (key: string) => {
      await api<void>(`/organization/texts/${key}`, { method: "DELETE" });
      setDone((d) =>
        d?.data ? { ...d, data: { ...d.data, models: d.data.models.map((m) => (m.key === key ? { ...m, custom: null, updatedAt: null, updatedBy: null } : m)) } } : d,
      );
    },
  } as const;
}
