import { useTranslation } from "react-i18next";
import { Input, Select } from "@/components/ui/field";
import { useStudentFilterOptions } from "@/features/students/useStudents";
import { ANNOUNCEMENT_TYPES } from "./types";

type Props = { get: (k: string) => string; set: (k: string, v: string) => void };

// Filtros de Comunicação › Comunicados (RF-COM-01): período, turma e tipo; a unidade vem do alcance do perfil.
export function AnnouncementFilters({ get, set }: Props) {
  const { t } = useTranslation();
  const { classes } = useStudentFilterOptions();
  return (
    <>
      <label className="flex min-w-0 flex-col gap-1 text-sm">
        {t("announcements.filters.from")}
        <Input type="date" value={get("from")} onChange={(e) => set("from", e.target.value)} />
      </label>
      <label className="flex min-w-0 flex-col gap-1 text-sm">
        {t("announcements.filters.to")}
        <Input type="date" value={get("to")} onChange={(e) => set("to", e.target.value)} />
      </label>
      <label className="flex min-w-0 flex-col gap-1 text-sm">
        {t("announcements.filters.class")}
        <Select value={get("classId")} onChange={(e) => set("classId", e.target.value)}>
          <option value="">{t("announcements.filters.allClasses")}</option>
          {classes.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </Select>
      </label>
      <label className="flex min-w-0 flex-col gap-1 text-sm">
        {t("announcements.filters.type")}
        <Select value={get("type")} onChange={(e) => set("type", e.target.value)}>
          <option value="">{t("announcements.filters.allTypes")}</option>
          {ANNOUNCEMENT_TYPES.map((id) => (
            <option key={id} value={id}>
              {t(`announcements.types.${id}`)}
            </option>
          ))}
        </Select>
      </label>
    </>
  );
}
