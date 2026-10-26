import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelham Notification, NotificationList e NotificationPreferences do OpenAPI (docs/openapi.yaml).
export type NotificationType =
  | "PAYMENT_RECEIVED"
  | "HIGH_BENEFIT"
  | "STUDENT_MAJORITY"
  | "EMAIL_PROBLEM"
  | "INVITE_PENDING"
  | "CLOSURE_REQUEST"
  | "MP_DISCONNECTED"
  | "SUBSCRIPTION_OVERDUE"
  | "PORTAL_REQUEST";
export interface AppNotification {
  id: string;
  type: NotificationType;
  group: "TODAY" | "WEEK";
  createdAt: string;
  readAt: string | null;
  refType: string | null;
  refId: string | null;
  params: Record<string, unknown>;
}
export interface NotificationList {
  items: AppNotification[];
  unreadCount: number;
}
export interface NotificationPreferences {
  receiveAll: boolean;
  canReceiveAll: boolean;
}

/** D6: o contador é consultado a cada 60 s. */
export const POLL_MS = 60_000;

// Contador do sino. 403 (perfil sem sino) esconde o sino; qualquer outra falha só deixa o contador como estava.
export function useUnreadCount() {
  const [count, setCount] = useState(0);
  const [forbidden, setForbidden] = useState(false);
  const refresh = useCallback(() => {
    api<{ count: number }>("/notifications/unread-count").then(
      (v) => setCount(v.count),
      (e) => e instanceof ApiError && e.status === 403 && setForbidden(true),
    );
  }, []);
  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, POLL_MS);
    return () => clearInterval(timer);
  }, [refresh]);
  return { count, forbidden, refresh, setCount } as const;
}

type Loaded = { tick: number; data?: NotificationList; forbidden?: boolean };

// Painel: lista do vínculo ativo e preferência "receber tudo". Só consulta com o painel aberto.
export function useNotificationPanel(open: boolean) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();
  const [prefs, setPrefs] = useState<NotificationPreferences>();

  useEffect(() => {
    if (!open) return;
    let alive = true;
    api<NotificationList>("/notifications").then(
      (data) => alive && setDone({ tick, data }),
      (e) => alive && setDone({ tick, forbidden: e instanceof ApiError && e.status === 403 }),
    );
    api<NotificationPreferences>("/notifications/preferences").then(
      (p) => alive && setPrefs(p),
      () => undefined,
    );
    return () => {
      alive = false;
    };
  }, [open, tick]);

  const current = done?.tick === tick ? done : undefined;
  const status = !current ? "loading" : current.data ? "ready" : current.forbidden ? "forbidden" : "error";
  return {
    status,
    data: current?.data,
    prefs,
    retry: () => setTick((n) => n + 1),
    // Erros sobem (ApiError) para a tela.
    readAll: async () => {
      await api<{ changed: number }>("/notifications/read-all", { method: "POST" });
      const now = new Date().toISOString();
      setDone((d) => (d?.data ? { ...d, data: { items: d.data.items.map((n) => ({ ...n, readAt: n.readAt ?? now })), unreadCount: 0 } } : d));
    },
    setReceiveAll: async (receiveAll: boolean) => setPrefs(await api<NotificationPreferences>("/notifications/preferences", { method: "PUT", body: { receiveAll } })),
  } as const;
}
