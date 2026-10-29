import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { useSession } from "@/features/auth/SessionProvider";
import { useUnitScope } from "@/features/units/UnitScopeProvider";
import { ApiError } from "@/lib/api";
import { appointMyself, deactivateUser, type StaffUser } from "./useUsers";

// RF-ACE-06 / RN-ORG-07: desativa o vínculo; se deixaria uma unidade sem Administrador, explica e, para o Coordenador, oferece "Nomear a mim mesmo".
export function DeactivateUserDialog({ user, onClose, onDone }: { user: StaffUser | null; onClose: () => void; onDone: () => void }) {
  return user ? <Body key={user.linkId} user={user} onClose={onClose} onDone={onDone} /> : null;
}

function Body({ user, onClose, onDone }: { user: StaffUser; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const { state } = useSession();
  const { units } = useUnitScope();
  const coordinator = state.status === "ready" && state.session.links.find((l) => l.id === state.session.activeLinkId)?.role === "COORDINATOR";
  const [affected, setAffected] = useState<string[]>([]);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  // Com `appoint`, o Coordenador entra como Administrador nas unidades afetadas e a desativação é repetida.
  async function run(appoint: boolean) {
    setBusy(true);
    setError(undefined);
    try {
      if (appoint) await appointMyself(affected);
      await deactivateUser(user.linkId);
    } catch (e) {
      if (!appoint && e instanceof ApiError && e.code === "LAST_UNIT_ADMINISTRATOR") setAffected((e.fields[0]?.message ?? "").split(",").filter(Boolean));
      else setError(t("users.deactivate.error"));
      setBusy(false);
      return;
    }
    setBusy(false);
    onClose();
    onDone();
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("users.deactivate.title", { name: user.name })}>
      <div className="flex flex-col gap-3">
        <p>{t("users.deactivate.summary")}</p>
        {affected.length > 0 && (
          <Banner tone="warn">
            <p className="font-medium">{t("users.lastAdmin.explain")}</p>
            <ul className="list-disc pl-5">
              {affected.map((id) => (
                <li key={id}>{units.find((u) => u.id === id)?.name ?? id}</li>
              ))}
            </ul>
            <p className="mt-1">{t(coordinator ? "users.lastAdmin.coordinatorHelp" : "users.lastAdmin.adminHelp")}</p>
          </Banner>
        )}
        {error && (
          <Banner tone="danger">{error}</Banner>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="secondary" className="min-h-11" onClick={onClose} disabled={busy}>
            {t("common.cancel")}
          </Button>
          {affected.length > 0 && coordinator && (
            <Button type="button" className="min-h-11" onClick={() => void run(true)} disabled={busy}>
              {t("users.lastAdmin.appoint")}
            </Button>
          )}
          {affected.length === 0 && (
            <Button type="button" className="min-h-11" onClick={() => void run(false)} disabled={busy}>
              {t("users.deactivate.confirm")}
            </Button>
          )}
        </div>
      </div>
    </Modal>
  );
}
