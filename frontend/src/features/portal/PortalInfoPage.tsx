import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { PortalGraduation } from "./PortalGraduation";
import { PortalLayout } from "./PortalLayout";
import { PortalPersonalData } from "./PortalPersonalData";
import { PortalPlans } from "./PortalPlans";
import { PortalAccount, PortalDataActions, PortalPreferences, PortalRequestsSummary, PortalTermsAccepted } from "./PortalSettings";
import { PortalTrainings, PortalUnitContacts } from "./PortalTrainings";
import { usePortalInfo, type PortalInfoData } from "./usePortalInfo";

function Sections({ info, reload }: { info: PortalInfoData; reload: () => void }) {
  const id = info.selectedStudentId;
  const hasModalities = !info.reduced && info.trainings.length > 0;
  return (
    <div className="flex flex-col gap-4">
      <PortalPersonalData studentId={id} personal={info.personal} onChanged={reload} />
      {info.reduced ? <PortalUnitContacts units={info.unitContacts} /> : <PortalTrainings items={info.trainings} />}
      {hasModalities && <PortalPlans items={info.plans} />}
      {hasModalities && <PortalGraduation items={info.graduation} />}
      <PortalPreferences initial={info.emailAnnouncements} />
      <PortalTermsAccepted studentId={id} terms={info.terms} onChanged={reload} />
      {!info.reduced && <PortalRequestsSummary studentId={id} />}
      <PortalAccount />
      <PortalDataActions />
    </div>
  );
}

// RF-POR-07: aba Minhas informações do aluno escolhido em `?aluno=` (padrão o primeiro cartão). Fora de RequireFullPortal: o portal reduzido também a vê (RN-INA-08).
export function PortalInfoPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const student = params.get("aluno");
  const { status, data, reload } = usePortalInfo(student);
  // O seletor fica na tela enquanto os dados do aluno novo carregam.
  const [last, setLast] = useState<PortalInfoData>();
  if (data && data !== last) setLast(data);
  const list = data ?? last;
  return (
    <PortalLayout>
      <h1 className="mb-4 text-2xl font-semibold">{t("portalInfo.title")}</h1>
      <Link to="/portal/carteirinha" className="mb-4 inline-flex min-h-11 items-center underline">
        {t("portalInfo.cardLink")}
      </Link>
      {list && list.students.length > 1 && (
        <label className="mb-4 flex flex-col gap-1">
          <span className="text-sm font-medium">{t("portalInfo.selector")}</span>
          <select className="min-h-11 rounded-md border bg-background px-2" value={student ?? list.selectedStudentId} onChange={(e) => setParams({ aluno: e.target.value }, { replace: true })}>
            {list.students.map((s) => (
              <option key={s.id} value={s.id}>
                {s.self ? t("portalInfo.you") : s.name}
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
        <Sections key={data.selectedStudentId} info={data} reload={reload} />
      ) : (
        <ErrorState onRetry={reload} />
      )}
    </PortalLayout>
  );
}
