import { useState } from "react";
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { PortalBelts } from "./PortalBelts";
import { PortalChargeCard } from "./PortalChargeCard";
import { PortalFamilyStrip } from "./PortalFamilyStrip";
import { PortalNextLessons } from "./PortalNextLessons";
import { PortalNoticesCard } from "./PortalNoticesCard";
import { usePortalHome, type PortalHomeData } from "./usePortalHome";

const ALL = "all";
const key = (modality: string, unit: string) => `${modality}|${unit}`;

// RF-POR-02: Início do portal. Cobrança, próximas aulas, avisos e faixa, nesta ordem; o portal reduzido só tem a cobrança (RN-INA-08).
export function PortalHomePage() {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<string | null>(null);
  const [filter, setFilter] = useState(ALL);
  const { status, data, reload } = usePortalHome(selected);
  // A faixa de cartões fica na tela enquanto o cartão novo carrega.
  const [last, setLast] = useState<PortalHomeData>();
  if (data && data !== last) setLast(data);
  const strip = data ?? last;

  const choose = (id: string) => {
    setSelected(id);
    setFilter(ALL);
  };

  return (
    <>
      <h1 className="text-2xl font-semibold">{t("app.welcomeTitle")}</h1>
      <Link
        to={`/portal/carteirinha${(selected ?? strip?.selectedStudentId) ? `?aluno=${selected ?? strip?.selectedStudentId}` : ""}`}
        className="mt-2 inline-flex min-h-11 items-center underline"
      >
        {t("portalCard.link")}
      </Link>
      <div className="mt-4">
        {strip && strip.students.length > 1 && (
          <PortalFamilyStrip students={strip.students} selected={selected ?? strip.selectedStudentId} familyOpenCents={strip.familyOpenCents} onSelect={choose} />
        )}
        {status === "loading" || status === "idle" ? (
          <LoadingState />
        ) : status === "forbidden" ? (
          <ForbiddenState />
        ) : status === "ready" && data ? (
          <Sections data={data} filter={filter} onFilter={setFilter} reload={reload} />
        ) : (
          <ErrorState onRetry={reload} />
        )}
      </div>
    </>
  );
}

function Sections({ data, filter, onFilter, reload }: { data: PortalHomeData; filter: string; onFilter: (v: string) => void; reload: () => void }) {
  const { t } = useTranslation();
  // RN-POR-04: etiqueta e filtro só com mais de uma combinação de modalidade e unidade (a faixa filtra pela modalidade).
  const option = (modality: string, unit: string) => t("portalHome.filter.option", { modality, unit });
  const combos = new Map<string, string>();
  for (const c of data.charges.items) if (c.modality) combos.set(key(c.modality.id, c.unit.id), option(c.modality.name, c.unit.name));
  for (const l of data.lessons) combos.set(key(l.modality.id, l.unit.id), option(l.modality.name, l.unit.name));
  const many = combos.size > 1;
  const active = many && combos.has(filter) ? filter : ALL;
  const fits = (modality: string | undefined, unit: string) => active === ALL || modality === undefined || key(modality, unit) === active;

  return (
    <div className="flex flex-col gap-4">
      {many && (
        <label className="flex flex-col gap-1">
          <span className="text-sm font-medium">{t("portalHome.filter.label")}</span>
          <select className="min-h-11 rounded-md border bg-background px-2" value={active} onChange={(e) => onFilter(e.target.value)}>
            <option value={ALL}>{t("portalHome.filter.all")}</option>
            {[...combos].map(([k, text]) => (
              <option key={k} value={k}>
                {text}
              </option>
            ))}
          </select>
        </label>
      )}
      <PortalChargeCard
        items={data.charges.items.filter((c) => fits(c.modality?.id, c.unit.id))}
        nextDueOn={data.charges.nextDueOn}
        label={(c) => (many ? (c.modality ? option(c.modality.name, c.unit.name) : c.unit.name) : null)}
        onChanged={reload}
      />
      {!data.reduced && (
        <>
          <PortalNextLessons items={data.lessons.filter((l) => fits(l.modality.id, l.unit.id))} label={(l) => (many ? option(l.modality.name, l.unit.name) : null)} />
          <PortalNoticesCard data={data.announcements} />
          <PortalBelts items={data.belts.filter((b) => active === ALL || b.modality.id === active.split("|")[0])} showLabel={many} />
        </>
      )}
    </div>
  );
}
