// Exemplo mínimo usado só pelo teste-modelo dos quatro estados, com os componentes da #8.
import { useEffect, useState } from "react";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";

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

  if (!profile.canView || result?.state === "forbidden") return <ForbiddenState />;
  if (!result) return <LoadingState />;
  if (result.state === "empty") return <EmptyState />;
  if (result.state === "error")
    return (
      <ErrorState
        onRetry={() => {
          setResult(null);
          setAttempt((a) => a + 1);
        }}
      />
    );
  return (
    <ul>
      {result.items.map((i) => (
        <li key={i}>{i}</li>
      ))}
    </ul>
  );
}
