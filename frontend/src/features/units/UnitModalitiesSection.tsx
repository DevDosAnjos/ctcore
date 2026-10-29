import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { ApiError } from "@/lib/api";
import { Banner } from "@/components/ui/banner";
import { Card } from "@/components/Card";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { useUnitModalities } from "./useUnitModalities";

// RF-MOD-03: modalidades que a unidade oferece. Cada interruptor grava na hora, fora do formulário e do "Salvar" da tela.
export function UnitModalitiesSection({ unitId }: { unitId: string }) {
  const { t } = useTranslation();
  const api = useUnitModalities(unitId);
  const [failed, setFailed] = useState<{ name: string; classes: string[] }>();

  function body() {
    if (api.status === "loading") return <LoadingState />;
    if (api.status === "forbidden") return <ForbiddenState />;
    if (!api.listing) return <ErrorState onRetry={api.retry} />;
    const { canManage, items } = api.listing;
    if (items.length === 0) {
      return (
        <EmptyState
          title={t("unitModalities.emptyTitle")}
          text={t("unitModalities.emptyText")}
          action={
            <Link className="min-h-11 py-2 underline" to="/configuracoes/modalidades">
              {t("unitModalities.emptyLink")}
            </Link>
          }
        />
      );
    }
    return (
      <>
        {failed && (
          <Banner tone="danger" className="mb-2">
            <p>{t(failed.classes.length > 0 ? "unitModalities.inUse" : "unitModalities.changeError", { name: failed.name })}</p>
            {failed.classes.length > 0 && (
              <ul className="list-disc pl-5">
                {failed.classes.map((c) => (
                  <li key={c}>{c}</li>
                ))}
              </ul>
            )}
          </Banner>
        )}
        <ul className="flex flex-col divide-y">
          {items.map((m) => (
            <li key={m.id}>
              <label className="flex min-h-11 items-center justify-between gap-2 py-2">
                <span className="min-w-0 break-words">{m.name}</span>
                <input
                  type="checkbox"
                  role="switch"
                  className="size-5 shrink-0"
                  checked={m.offered}
                  disabled={!canManage}
                  onChange={(e) => {
                    setFailed(undefined);
                    api.setOffered(m.id, e.target.checked).catch((err) =>
                      setFailed({
                        name: m.name,
                        classes: err instanceof ApiError && err.code === "UNIT_MODALITY_IN_USE" ? err.fields.filter((f) => f.field === "class").map((f) => f.message) : [],
                      }),
                    );
                  }}
                />
              </label>
            </li>
          ))}
        </ul>
        {!canManage && <p className="mt-2 text-sm text-muted-foreground">{t("unitModalities.readOnly")}</p>}
      </>
    );
  }

  return (
    <Card heading="h2" title={t("unitModalities.title")}>
      <p className="mb-2 text-sm text-muted-foreground">{t("unitModalities.help")}</p>
      {body()}
    </Card>
  );
}
