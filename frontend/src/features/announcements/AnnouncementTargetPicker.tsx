import { useTranslation } from "react-i18next";
import { Select } from "@/components/ui/field";
import type { AnnouncementDraft, AnnouncementTargets } from "./types";

// Valor do campo de destino: CLASS:<turma>, MODALITY:<modalidade>:<unidade>, UNIT:<unidade> ou ORGANIZATION.
export function draftTarget(value: string): Pick<AnnouncementDraft, "targetKind" | "classId" | "modalityId" | "unitId"> | null {
  const [kind, a, b] = value.split(":");
  if (kind === "CLASS" && a) return { targetKind: "CLASS", classId: a };
  if (kind === "MODALITY" && a && b) return { targetKind: "MODALITY", modalityId: a, unitId: b };
  if (kind === "UNIT" && a) return { targetKind: "UNIT", unitId: a };
  return kind === "ORGANIZATION" ? { targetKind: "ORGANIZATION" } : null;
}

export function targetName(targets: AnnouncementTargets, value: string): string {
  const [kind, a] = value.split(":");
  if (kind === "CLASS") return targets.classes.find((c) => c.id === a)?.name ?? "";
  if (kind === "MODALITY") return targets.modalities.find((m) => m.id === a)?.name ?? "";
  if (kind === "UNIT") return targets.units.find((u) => u.id === a)?.name ?? "";
  return "";
}

// RN-COM-01: só aparece o que o servidor liberou para o perfil (o servidor confere de novo ao enviar).
export function AnnouncementTargetPicker({ targets, value, onChange }: { targets: AnnouncementTargets; value: string; onChange: (v: string) => void }) {
  const { t } = useTranslation();
  const unitName = (id: string) => targets.units.find((u) => u.id === id)?.name ?? "";
  return (
    <div>
      <label htmlFor="ann-target" className="block text-sm font-medium">
        {t("announcements.new.target")}
      </label>
      <Select id="ann-target" value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{t("announcements.new.targetPlaceholder")}</option>
        {targets.organization && <option value="ORGANIZATION">{t("announcements.new.organization")}</option>}
        {targets.units.length > 0 && (
          <optgroup label={t("announcements.new.targetGroups.units")}>
            {targets.units.map((u) => (
              <option key={u.id} value={`UNIT:${u.id}`}>
                {u.name}
              </option>
            ))}
          </optgroup>
        )}
        {targets.modalities.length > 0 && (
          <optgroup label={t("announcements.new.targetGroups.modalities")}>
            {targets.modalities.map((m) => (
              <option key={`${m.id}:${m.unitId}`} value={`MODALITY:${m.id}:${m.unitId}`}>
                {t("announcements.new.modalityOption", { modality: m.name, unit: unitName(m.unitId) })}
              </option>
            ))}
          </optgroup>
        )}
        {targets.classes.length > 0 && (
          <optgroup label={t("announcements.new.targetGroups.classes")}>
            {targets.classes.map((c) => (
              <option key={c.id} value={`CLASS:${c.id}`}>
                {c.name}
              </option>
            ))}
          </optgroup>
        )}
      </Select>
    </div>
  );
}
