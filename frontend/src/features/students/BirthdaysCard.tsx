import { useTranslation } from "react-i18next";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { WhatsAppButton } from "@/components/whatsapp";
import { useBirthdays } from "./useBirthdays";
import type { StudentBirthday } from "./types";

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

// RF-ALU-06: Início do Administrador e do Professor. O servidor já entrega a lista com o corte do perfil.
export function BirthdaysCard() {
  const { t } = useTranslation();
  const { status, data, reload } = useBirthdays();
  const message = (b: StudentBirthday) =>
    b.contactIsGuardian
      ? t("birthdays.messageGuardian", { contact: b.contactName, name: b.name, unit: b.registeredUnit.name })
      : t("birthdays.messageStudent", { name: b.name, unit: b.registeredUnit.name });
  return (
    <section aria-labelledby="birthdays-title" className="mt-6 rounded-lg border p-4">
      <h2 id="birthdays-title" className="text-lg font-semibold">
        {t("birthdays.title")}
      </h2>
      {status === "loading" && <LoadingState />}
      {status === "forbidden" && <ForbiddenState />}
      {(status === "error" || status === "notFound") && <ErrorState onRetry={reload} />}
      {status === "ready" && data?.length === 0 && <EmptyState title={t("birthdays.empty")} text={t("birthdays.emptyText")} />}
      {status === "ready" && !!data?.length && (
        <ul className="mt-3 flex flex-col gap-3">
          {data.map((b) => (
            <li key={b.id} className="flex flex-wrap items-center gap-3">
              {b.photoUrl ? (
                <img src={b.photoUrl} alt="" className="size-10 shrink-0 rounded-full object-cover" />
              ) : (
                <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-muted font-semibold">
                  {initials(b.name)}
                </span>
              )}
              <div className="min-w-0 flex-1">
                <p className="break-words font-medium">{b.name}</p>
                <p className="text-sm text-muted-foreground">
                  {t("students.age", { count: b.age })}
                  {b.ageRange ? ` · ${b.ageRange.name}` : ""}
                </p>
              </div>
              <WhatsAppButton phone={b.contactPhone ?? undefined} message={message(b)} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
