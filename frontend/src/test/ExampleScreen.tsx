// Exemplo mínimo usado só pelo teste-modelo dos quatro estados (D7). O padrão
// compartilhado (componentes de estado) é da #8.
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

type Result = { state: "empty" | "error" | "forbidden" | "ready"; items: string[] };

export type Profile = { canView: boolean };

async function fetchItems(): Promise<Result> {
  try {
    const res = await fetch(`${window.location.origin}/api/v1/example-items`);
    if (res.status === 403) return { state: "forbidden", items: [] };
    if (!res.ok) return { state: "error", items: [] };
    const items: string[] = await res.json();
    return { state: items.length === 0 ? "empty" : "ready", items };
  } catch {
    return { state: "error", items: [] };
  }
}

export function ExampleScreen({ profile }: { profile: Profile }) {
  const { t } = useTranslation();
  const [result, setResult] = useState<Result | null>(null); // null = carregando
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!profile.canView) return;
    let ignore = false;
    void fetchItems().then((r) => !ignore && setResult(r));
    return () => {
      ignore = true;
    };
  }, [profile.canView, attempt]);

  if (!profile.canView || result?.state === "forbidden")
    return (
      <section>
        <h2>{t("states.forbiddenTitle")}</h2>
        <p>{t("states.forbiddenText")}</p>
      </section>
    );
  if (!result) return <p role="status">{t("states.loading")}</p>;
  if (result.state === "empty")
    return (
      <section>
        <h2>{t("states.emptyTitle")}</h2>
        <p>{t("states.emptyText")}</p>
      </section>
    );
  if (result.state === "error")
    return (
      <section role="alert">
        <h2>{t("errors.generic.title")}</h2>
        <p>{t("errors.generic.aconteceu")}</p>
        <p>{t("errors.generic.alterado")}</p>
        <p>{t("errors.generic.fazer")}</p>
        <button
          onClick={() => {
            setResult(null);
            setAttempt((a) => a + 1);
          }}
        >
          {t("states.retry")}
        </button>
      </section>
    );
  return (
    <ul>
      {result.items.map((i) => (
        <li key={i}>{i}</li>
      ))}
    </ul>
  );
}
