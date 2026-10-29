import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { useUnitScope } from "@/features/units/UnitScopeProvider";
import { UnitChecks } from "@/features/users/UnitChecks";
import { ApiError } from "@/lib/api";
import { grantAccess, type TeacherDetail } from "./useTeachers";
import { Banner } from "@/components/ui/banner";

const KNOWN = ["ALREADY_LINKED", "STAFF_LIMIT_REACHED", "FORBIDDEN", "CONFLICT"];
type Choice = { id: string; name: string; shortName: string };

// RN-PRO-02: escolhe, entre as unidades do professor ao alcance de quem chama, onde o vínculo de Professor nasce; o servidor reconfere tudo.
export function GrantAccessDialog({ teacher, onClose, onDone }: { teacher: TeacherDetail | null; onClose: () => void; onDone: () => void }) {
  const { units } = useUnitScope();
  if (!teacher) return null;
  const choices = teacher.units.filter((u) => units.some((r) => r.id === u.id)).map((u) => ({ ...u, shortName: "" }));
  return <Body key={teacher.id} teacher={teacher} choices={choices} onClose={onClose} onDone={onDone} />;
}

function Body({ teacher, choices, onClose, onDone }: { teacher: TeacherDetail; choices: Choice[]; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [unitIds, setUnitIds] = useState(() => choices.map((c) => c.id));
  const [error, setError] = useState<string>();
  const [form, setForm] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function submit() {
    setForm(undefined);
    if (unitIds.length === 0) return setError(t("users.invite.unitsRequired"));
    setError(undefined);
    setBusy(true);
    try {
      await grantAccess(teacher.id, unitIds);
    } catch (e) {
      setForm(t(`teachers.panel.grant.errors.${e instanceof ApiError && KNOWN.includes(e.code) ? e.code : "generic"}`));
      setBusy(false);
      return;
    }
    setBusy(false);
    onClose();
    onDone();
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("teachers.panel.grant.title", { name: teacher.name })}>
      <form
        noValidate
        className="flex flex-col gap-3"
        aria-busy={busy}
        onSubmit={(e) => {
          e.preventDefault();
          if (!busy) void submit();
        }}
      >
        <p className="text-sm text-muted-foreground">{t("teachers.panel.grant.explain", { email: teacher.email })}</p>
        <UnitChecks units={choices} value={unitIds} onChange={setUnitIds} error={error} />
        {form && (
          <Banner tone="danger">
            {form}
          </Banner>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" className="min-h-11" disabled={busy}>
            {t("teachers.panel.grant.send")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
