import type { AppNotification, NotificationType } from "./useNotifications";

// D5: a ação de cada notificação é o botão que leva à tela do objeto; sem tela própria do objeto, a lista da área.
export function notificationTarget(n: Pick<AppNotification, "type" | "refType" | "refId">): string {
  const student = n.refType === "student" && n.refId ? `/alunos/${n.refId}` : "/alunos";
  const routes: Record<NotificationType, string> = {
    PAYMENT_RECEIVED: "/cobrancas",
    HIGH_BENEFIT: "/beneficios",
    STUDENT_MAJORITY: student,
    EMAIL_PROBLEM: student,
    INVITE_PENDING: "/configuracoes/usuarios",
    CLOSURE_REQUEST: "/configuracoes/organizacao",
    MP_DISCONNECTED: "/configuracoes/organizacao",
    SUBSCRIPTION_OVERDUE: "/configuracoes/organizacao",
  };
  return routes[n.type];
}

/** Tempo relativo em minutos, horas ou dias (a data exata vai no `title`). */
export function relativeTime(createdAt: string, now: Date = new Date()): { key: "now" | "minutes" | "hours" | "days"; count: number } {
  const minutes = Math.floor((now.getTime() - new Date(createdAt).getTime()) / 60_000);
  if (minutes < 1) return { key: "now", count: 0 };
  if (minutes < 60) return { key: "minutes", count: minutes };
  if (minutes < 1440) return { key: "hours", count: Math.floor(minutes / 60) };
  return { key: "days", count: Math.floor(minutes / 1440) };
}
