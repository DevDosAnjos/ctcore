import { useResource } from "@/features/students/useStudentRecord";
import type { DailyReceipts } from "./types";

// Sem unidade escolhida não há pedido (a rota exige `unitId`).
export const useDailyReceipts = (unitId: string | null, date: string) =>
  useResource<DailyReceipts>(unitId ? `/billing/daily-receipts?unitId=${unitId}&date=${date}` : null);
