import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { formatPhone } from "@/components/fields/masks";
import { formatDate } from "@/lib/format";
import type { EnrollmentModality, StudentRecord } from "./types";

function Line({ label, value }: { label: string; value: string | null | undefined }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-wrap gap-x-2">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-words">{value || t("students.record.summary.none")}</dd>
    </div>
  );
}

// Resumo: o que existe hoje (contato, responsáveis, emergência); situação, modalidades e pendências chegam com a matrícula e as cobranças (D1).
export function RecordSummary({ record, modalities }: { record: StudentRecord; modalities?: EnrollmentModality[] }) {
  const active = (modalities ?? []).filter((m) => m.status === "ACTIVE");
  const { t } = useTranslation();
  const k = "students.record.summary.";
  return (
    <div className="flex flex-col gap-3">
      <dl className="flex flex-col gap-1">
        <Line label={t(`${k}unit`)} value={record.registeredUnit.name} />
        <Line label={t(`${k}ageRange`)} value={record.ageRange?.name} />
        {record.birthDate && <Line label={t(`${k}birthDate`)} value={formatDate(record.birthDate)} />}
      </dl>
      {modalities && (
        <Card title={t(`${k}modalities`)}>
          {active.length === 0 ? (
            <p className="text-muted-foreground">{t(`${k}noModalities`)}</p>
          ) : (
            <ul className="flex flex-col gap-1">
              {active.map((m) => (
                <li key={m.id} className="break-words">
                  {t("students.modalityLine", { modality: m.modality.name, unit: m.unit.name, class: m.primaryClass.name })}
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
      {record.contact && (
        <Card title={t(`${k}contact`)}>
          <dl className="flex flex-col gap-1">
            <Line label={t(`${k}phone`)} value={record.contact.phone ? formatPhone(record.contact.phone) : null} />
            <Line label={t(`${k}email`)} value={record.contact.email} />
          </dl>
          {record.contact.emailProblem && <p className="mt-1 text-sm text-danger-foreground">{t(`${k}emailProblem`)}</p>}
        </Card>
      )}
      <Card title={t(`${k}guardians`)}>
        {record.guardians.length === 0 ? (
          <p className="text-muted-foreground">{t(`${k}noGuardians`)}</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {record.guardians.map((g) => (
              <li key={g.guardianId} className="break-words">
                <span className="font-medium">{g.name}</span>
                {g.phone && <span className="text-muted-foreground"> · {formatPhone(g.phone)}</span>}
              </li>
            ))}
          </ul>
        )}
      </Card>
      {record.emergency && (
        <Card title={t(`${k}emergency`)}>
          <dl className="flex flex-col gap-1">
            <Line label={t(`${k}emergencyName`)} value={record.emergency.name} />
            <Line label={t(`${k}emergencyRelationship`)} value={record.emergency.relationship} />
            <Line label={t(`${k}emergencyPhone`)} value={record.emergency.phone ? formatPhone(record.emergency.phone) : null} />
          </dl>
        </Card>
      )}
    </div>
  );
}
