import { useState } from "react";

/** Janela de conflito de edição (RNF-15): null = fechada; senão consultando, com erro ou com o estado atual. */
export type ConflictState<V> = { status: "loading" } | { status: "error" } | { status: "ready"; current: V };

// `open` consulta o estado atual; serve também para "Tentar de novo" e para reabrir depois de um novo 409.
export function useEditConflict<V>(fetchCurrent: () => Promise<V>) {
  const [state, setState] = useState<ConflictState<V> | null>(null);
  return {
    state,
    open: () => {
      setState({ status: "loading" });
      fetchCurrent().then(
        (current) => setState({ status: "ready", current }),
        () => setState({ status: "error" }),
      );
    },
    close: () => setState(null),
  } as const;
}
