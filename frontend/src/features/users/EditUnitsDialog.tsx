import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { useUnitScope, type ReachableUnit } from "@/features/units/UnitScopeProvider";
import { ApiError } from "@/lib/api";
import { UnitChecks } from "./UnitChecks";
import { changeUnits, type StaffUser } from "./useUsers";

// R7 / D5: o cliente envia as unidades desejadas ao seu alcance; as demais do vínculo o servidor preserva.
export function EditUnitsDialog({ user, onClose, onDone }: { user: StaffUser | null; onClose: () => void; onDone: () => void }) {
  const { units } = useUnitScope();
  return user ? <Body key={user.linkId} user={user} choices={units} onClose={onClose} onDone={onDone} /> : null;
}

function Body({ user, choices, onClose, onDone }: { user: StaffUser; choices: ReachableUnit[]; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [unitIds, setUnitIds] = useState(() => user.units.map((u) => u.id).filter((id) => choices.some((c) => c.id === id)));
  const [error, setError] = useState<string>();
  const [last, setLast] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  async function submit() {
    setLast([]);
    if (unitIds.length === 0) return setError(t("users.invite.unitsRequired"));
    setError(undefined);
    setBusy(true);
    try {
      await changeUnits(user.linkId, unitIds);
    } catch (e) {
      if (e instanceof ApiError && e.code === "LAST_UNIT_ADMINISTRATOR") {
        setLast((e.fields[0]?.message ?? "").split(",").map((id) => choices.find((c) => c.id === id)?.name ?? id));
      } else setError(t("users.editUnits.error"));
      setBusy(false);
      return;
    }
    setBusy(false);
    onClose();
    onDone();
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("users.editUnits.title", { name: user.name })}>
      <form
        noValidate
        className="flex flex-col gap-3"
        aria-busy={busy}
        onSubmit={(e) => {
          e.preventDefault();
          if (!busy) void submit();
        }}
      >
        <UnitChecks units={choices} value={unitIds} onChange={setUnitIds} error={error} />
        {last.length > 0 && (
          <Banner tone="warn">
            <p className="font-medium">{t("users.lastAdmin.explain")}</p>
            <ul className="list-disc pl-5">
              {last.map((n) => (
                <li key={n}>{n}</li>
              ))}
            </ul>
          </Banner>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="secondary" className="min-h-11" onClick={onClose} disabled={busy}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" className="min-h-11" disabled={busy}>
            {t("users.editUnits.save")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
