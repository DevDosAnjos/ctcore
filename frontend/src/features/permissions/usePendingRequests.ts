import { useEffect, useState } from "react";
import { api } from "@/lib/api";

// Contador do item Pedidos: pede só 1 linha e lê `total`. Só consulta se o item está no menu; 403 ou falha: sem contador.
export function usePendingRequests(enabled: boolean) {
  const [total, setTotal] = useState(0);
  useEffect(() => {
    if (!enabled) return;
    let alive = true;
    api<{ total: number }>("/portal-requests?page=1&size=1").then(
      (v) => alive && setTotal(v.total),
      () => alive && setTotal(0),
    );
    return () => {
      alive = false;
    };
  }, [enabled]);
  return enabled ? total : 0;
}
