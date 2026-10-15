import { useCallback, useEffect, useState } from "react";

// D3: estado da consulta no endereço, sem roteador. Valores inválidos viram o padrão na leitura.
export function useQueryParams() {
  const [search, setSearch] = useState(() => window.location.search);
  useEffect(() => {
    const onPop = () => setSearch(window.location.search);
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);

  const params = new URLSearchParams(search);
  const get = (k: string) => params.get(k) ?? "";

  // Mudar qualquer filtro volta para a página 1, salvo quando a própria página é mudada.
  const set = useCallback((changes: Record<string, string>, replace = false) => {
    const next = new URLSearchParams(window.location.search);
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (!("pagina" in changes)) next.delete("pagina");
    const qs = next.toString();
    const url = window.location.pathname + (qs ? `?${qs}` : "");
    if (replace) window.history.replaceState(null, "", url);
    else window.history.pushState(null, "", url);
    setSearch(window.location.search);
  }, []);

  const clear = useCallback(() => {
    window.history.pushState(null, "", window.location.pathname);
    setSearch("");
  }, []);

  return { get, set, clear };
}
