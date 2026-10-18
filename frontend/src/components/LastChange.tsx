import { useTranslation } from "react-i18next";
import { formatDateTime } from "@/lib/format";

/** Espelha LastChange do OpenAPI (RF-ORG-08): `by` é nulo quando o autor não é uma pessoa. */
export interface LastChangeValue {
  at: string;
  by: string | null;
}

// "Última alteração em [data] por [pessoa]", ou "Ainda sem alterações" (RF-ORG-08).
export function LastChange({ value }: { value: LastChangeValue | null }) {
  const { t } = useTranslation();
  const text = !value
    ? t("lastChange.none")
    : value.by
      ? t("lastChange.line", {
          date: formatDateTime(value.at),
          person: value.by,
        })
      : t("lastChange.lineSystem", { date: formatDateTime(value.at) });
  return <p className="text-sm text-muted-foreground">{text}</p>;
}
