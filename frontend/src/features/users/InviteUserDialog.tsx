import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { Text } from "@/features/units/Text";
import { useUnitScope } from "@/features/units/UnitScopeProvider";
import { ApiError } from "@/lib/api";
import { UnitChecks } from "./UnitChecks";
import { inviteUser } from "./useUsers";

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const KNOWN = ["ALREADY_LINKED", "STAFF_LIMIT_REACHED", "FORBIDDEN"];
type Errors = { email?: string; name?: string; units?: string; form?: string };

// RF-ACE-06: convida; o perfil vem do servidor (Coordenador convida Administrador, Administrador convida Professor).
export function InviteUserDialog({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const { units } = useUnitScope();
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [unitIds, setUnitIds] = useState<string[]>([]);
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);

  function close() {
    setEmail("");
    setName("");
    setUnitIds([]);
    setErrors({});
    onClose();
  }

  async function submit() {
    const found: Errors = {};
    if (!EMAIL.test(email.trim())) found.email = t("users.invite.emailInvalid");
    if (!name.trim()) found.name = t("users.invite.nameRequired");
    if (unitIds.length === 0) found.units = t("users.invite.unitsRequired");
    setErrors(found);
    if (Object.keys(found).length) return;
    setBusy(true);
    try {
      await inviteUser({ email: email.trim(), name: name.trim(), unitIds });
    } catch (e) {
      const code = e instanceof ApiError && KNOWN.includes(e.code) ? e.code : "generic";
      setErrors({ form: t(`users.invite.errors.${code}`) });
      setBusy(false);
      return;
    }
    setBusy(false);
    close();
    onDone();
  }

  return (
    <Modal open={open} onOpenChange={(o) => !o && !busy && close()} title={t("users.invite.title")}>
      <form
        noValidate
        className="flex flex-col gap-3"
        aria-busy={busy}
        onSubmit={(e) => {
          e.preventDefault();
          if (!busy) void submit();
        }}
      >
        <Text label={t("users.invite.name")} value={name} onChange={setName} error={errors.name} autoComplete="off" />
        <Text label={t("users.invite.email")} type="email" value={email} onChange={setEmail} error={errors.email} autoComplete="off" />
        <UnitChecks units={units} value={unitIds} onChange={setUnitIds} error={errors.units} />
        {errors.form && (
          <Banner tone="danger">{errors.form}</Banner>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="secondary" className="min-h-11" onClick={close} disabled={busy}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" className="min-h-11" disabled={busy}>
            {t("users.invite.send")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
