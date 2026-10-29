import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { Banner } from "@/components/ui/banner";
import { Input, Textarea } from "@/components/ui/field";
import { CpfField, PhoneField } from "@/components/fields/fields";
import { isValidCpf, isValidPhone } from "@/components/fields/masks";
import { api, ApiError } from "@/lib/api";
import type { StudentGuardianInput } from "./types";

const empty = { name: "", cpf: "", email: "", phone: "", financial: false, reason: "" };

// RN-RSP-02: vincula um responsável (nome, CPF, e-mail, celular, financeiro e o motivo que o servidor exige). Erro do servidor aparece junto do campo ou no alto, sem fechar.
export function GuardianLinkDialog({ studentId, open, onOpenChange, onLinked }: { studentId: string; open: boolean; onOpenChange: (o: boolean) => void; onLinked: (name: string) => void }) {
  const { t } = useTranslation();
  const [form, setForm] = useState(empty);
  const [shown, setShown] = useState(false);
  const [busy, setBusy] = useState(false);
  const [server, setServer] = useState<{ fields: Record<string, string>; code: string } | null>(null);
  const set = <K extends keyof typeof empty>(key: K, value: (typeof empty)[K]) => setForm((f) => ({ ...f, [key]: value }));

  const local: Record<string, boolean> = {
    name: form.name.trim().length < 2,
    cpf: !isValidCpf(form.cpf),
    email: !/^\S+@\S+\.\S+$/.test(form.email.trim()),
    phone: !isValidPhone(form.phone),
    reason: form.reason.trim().length < 3,
  };
  const error = (field: string) => (server?.fields[field] ? t("students.record.guardians.errors.VALIDATION_ERROR") : shown && local[field] ? t("students.record.guardians.required") : undefined);
  const errs = { name: error("name"), cpf: error("cpf"), email: error("email"), phone: error("phone"), reason: error("reason") };

  function close(o: boolean) {
    if (!o) {
      setForm(empty);
      setShown(false);
      setServer(null);
    }
    onOpenChange(o);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setShown(true);
    if (busy || Object.values(local).some(Boolean)) return;
    setBusy(true);
    setServer(null);
    const body: StudentGuardianInput = { name: form.name.trim(), cpf: form.cpf, email: form.email.trim(), phone: form.phone, financial: form.financial, reason: form.reason.trim() };
    try {
      await api(`/students/${studentId}/guardians`, { method: "POST", body });
      onLinked(body.name);
      close(false);
    } catch (err) {
      const failure = err instanceof ApiError ? err : null;
      setServer({
        code: failure?.code ?? "generic",
        fields: Object.fromEntries((failure?.fields ?? []).map((f) => [f.field.split(".").pop()!, f.message])),
      });
    } finally {
      setBusy(false);
    }
  }

  
  const serverText = server && !Object.keys(server.fields).length ? t(`students.record.guardians.errors.${server.code}`, { defaultValue: t("students.record.guardians.errors.generic") }) : null;
  return (
    <Modal open={open} onOpenChange={close} title={t("students.record.guardians.linkTitle")}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-3">
        {serverText && (
          <Banner tone="danger">
            {serverText}
          </Banner>
        )}
        <div>
          <label htmlFor="guardian-name" className="block text-sm font-medium">
            {t("students.record.guardians.name")}
          </label>
          <Input id="guardian-name" value={form.name} onChange={(e) => set("name", e.target.value)} autoComplete="off" aria-invalid={Boolean(errs.name) || undefined} />
          {errs.name && <p className="text-sm text-danger-foreground">{errs.name}</p>}
        </div>
        <CpfField label={t("students.record.guardians.cpf")} onChange={(v) => set("cpf", v)} />
        {errs.cpf && <p className="text-sm text-danger-foreground">{errs.cpf}</p>}
        <div>
          <label htmlFor="guardian-email" className="block text-sm font-medium">
            {t("students.record.guardians.email")}
          </label>
          <Input id="guardian-email" type="email" value={form.email} onChange={(e) => set("email", e.target.value)} autoComplete="off" aria-invalid={Boolean(errs.email) || undefined} />
          {errs.email && <p className="text-sm text-danger-foreground">{errs.email}</p>}
        </div>
        <PhoneField label={t("students.record.guardians.phone")} onChange={(v) => set("phone", v)} />
        {errs.phone && <p className="text-sm text-danger-foreground">{errs.phone}</p>}
        <label className="flex min-h-11 items-center gap-2">
          <input type="checkbox" className="size-5" checked={form.financial} onChange={(e) => set("financial", e.target.checked)} />
          {t("students.record.guardians.makeItFinancial")}
        </label>
        <div>
          <label htmlFor="guardian-reason" className="block text-sm font-medium">
            {t("students.record.guardians.reason")}
          </label>
          <Textarea id="guardian-reason" value={form.reason} onChange={(e) => set("reason", e.target.value)} aria-invalid={Boolean(errs.reason) || undefined} />
          {errs.reason && <p className="text-sm text-danger-foreground">{errs.reason}</p>}
        </div>
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={() => close(false)}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" className="min-h-11" disabled={busy}>
            {t("students.record.guardians.submit")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
