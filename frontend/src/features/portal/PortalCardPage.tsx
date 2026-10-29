import { PageHeader } from "@/components/ui/page-header";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { PortalLayout } from "./PortalLayout";
import { PortalMemberCard } from "./PortalMemberCard";
import { usePortalCard, type PortalCardData } from "./usePortalCard";

// RF-POR-03: carteirinha do aluno escolhido em `?aluno=` (padrão o primeiro cartão). Fica fora de RequireFullPortal: o inativo e o ex-aluno também a veem (RN-INA-08).
export function PortalCardPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const student = params.get("aluno");
  const { status, data, offline, reload } = usePortalCard(student);
  // O seletor fica na tela enquanto a carteirinha nova carrega.
  const [last, setLast] = useState<PortalCardData>();
  if (data && data !== last) setLast(data);
  const list = data ?? last;
  return (
    <PortalLayout>
      <PageHeader title={t("portalCard.title")} className="mb-4" />
      {list && list.students.length > 1 && (
        <label className="mb-4 flex flex-col gap-1">
          <span className="text-sm font-medium">{t("portalCard.selector")}</span>
          <select
            className="min-h-11 rounded-md border bg-background px-2"
            value={student ?? list.student.id}
            onChange={(e) => setParams({ aluno: e.target.value }, { replace: true })}
          >
            {list.students.map((s) => (
              <option key={s.id} value={s.id}>
                {s.self ? t("portalCard.you") : s.name}
              </option>
            ))}
          </select>
        </label>
      )}
      {status === "loading" ? (
        <LoadingState />
      ) : status === "forbidden" ? (
        <ForbiddenState />
      ) : status === "ready" && data ? (
        <PortalMemberCard data={data} offline={offline} />
      ) : (
        <ErrorState onRetry={reload} />
      )}
    </PortalLayout>
  );
}
