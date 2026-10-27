import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { EmptyState } from "@/components/states";
import { formatPhoneDisplay } from "@/lib/format";
import { PortalTag } from "./PortalTag";
import type { InfoClass, InfoTraining, InfoUnit } from "./usePortalInfo";

export function PortalUnitContact({ unit }: { unit: InfoUnit }) {
  const { t } = useTranslation();
  const k = "portalInfo.units.";
  const items = [
    [t(`${k}phone`), unit.phone && formatPhoneDisplay(unit.phone)],
    [t(`${k}whatsapp`), unit.whatsapp && formatPhoneDisplay(unit.whatsapp)],
    [t(`${k}email`), unit.email],
  ].filter((i): i is [string, string] => !!i[1]);
  return (
    <div className="flex flex-col gap-1 text-sm">
      <p className="break-words font-medium">{unit.name}</p>
      {items.map(([label, value]) => (
        <p key={label} className="break-words">
          <span className="text-muted-foreground">{label}: </span>
          {value}
        </p>
      ))}
    </div>
  );
}

// RF-POR-07, RN-POR-06: contato da academia (da unidade, nunca do professor). No portal reduzido é o que resta no lugar dos treinos.
export function PortalUnitContacts({ units }: { units: InfoUnit[] }) {
  const { t } = useTranslation();
  if (units.length === 0) return null;
  return (
    <Card heading="h2" title={t("portalInfo.units.title")}>
      <ul className="flex flex-col divide-y">
        {units.map((u) => (
          <li key={u.id} className="py-3 first:pt-0 last:pb-0">
            <PortalUnitContact unit={u} />
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Slots({ item }: { item: InfoClass }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-1">
      <p className="break-words">{t("portalInfo.trainings.classLine", { name: item.clazz.name, teacher: item.teacherName })}</p>
      {item.schedule.length === 0 ? (
        <p className="text-muted-foreground">{t("portalInfo.trainings.noSchedule")}</p>
      ) : (
        <ul className="flex flex-col text-muted-foreground">
          {item.schedule.map((s, i) => (
            <li key={i}>{t("portalInfo.trainings.slot", { days: t(`agenda.weekdaysLong.${s.weekday}`), from: s.startsAt, to: s.endsAt })}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

// RF-POR-07, RN-POR-07, RN-POR-09: por modalidade, unidade, turma principal e outros horários; sem presença, frequência nem progresso.
export function PortalTrainings({ items }: { items: InfoTraining[] }) {
  const { t } = useTranslation();
  const k = "portalInfo.trainings.";
  return (
    <Card heading="h2" title={t(`${k}title`)}>
      {items.length === 0 ? (
        <EmptyState title={t("portalInfo.emptyTitle")} text={t("portalInfo.emptyText")} />
      ) : (
        <ul className="flex flex-col divide-y">
          {items.map((tr) => (
            <li key={tr.modality.id + tr.unit.id} className="flex flex-col gap-2 py-3 text-sm first:pt-0 last:pb-0">
              <div className="flex flex-wrap items-center gap-2">
                <PortalTag>{tr.modality.name}</PortalTag>
                <span className="text-muted-foreground">{t(`${k}unit`)}:</span>
                <span className="break-words">{tr.unit.name}</span>
              </div>
              <div>
                <p className="font-medium">{t(`${k}main`)}</p>
                <Slots item={tr.mainClass} />
              </div>
              {tr.otherClasses.length > 0 && (
                <div>
                  <p className="font-medium">{t(`${k}others`)}</p>
                  <ul className="flex flex-col gap-2">
                    {tr.otherClasses.map((c) => (
                      <li key={c.clazz.id}>
                        <Slots item={c} />
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <PortalUnitContact unit={tr.unit} />
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
