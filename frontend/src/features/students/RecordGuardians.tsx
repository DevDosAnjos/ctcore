import { useState } from "react";
import { useTranslation } from "react-i18next";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { formatPhone } from "@/components/fields/masks";
import { Button } from "@/components/ui/button";
import { api, ApiError } from "@/lib/api";
import { GuardianLinkDialog } from "./GuardianLinkDialog";
import type { StudentRecord, StudentRecordGuardian } from "./types";

// RN-RSP-02, RN-RSP-05: lista os responsáveis e usa as rotas de #54. Recusa do servidor aparece aqui, sem fechar o painel; ao concluir, a ficha é recarregada.
export function RecordGuardians({ record, onChanged }: { record: StudentRecord; onChanged: () => void }) {
  const { t } = useTranslation();
  const k = "students.record.guardians.";
  const [notice, setNotice] = useState<string | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [unlinking, setUnlinking] = useState<StudentRecordGuardian | null>(null);
  const [linking, setLinking] = useState(false);
  const base = `/students/${record.id}/guardians`;

  async function run(work: () => Promise<unknown>, done: string) {
    setNotice(null);
    setProblem(null);
    try {
      await work();
      setNotice(done);
      onChanged();
    } catch (e) {
      const code = e instanceof ApiError ? e.code : "generic";
      setProblem(t(`${k}errors.${code}`, { defaultValue: t(`${k}errors.generic`) }));
    }
  }

  return (
    <div className="flex flex-col gap-3">
      {notice && (
        <p role="status" className="text-sm">
          {notice}
        </p>
      )}
      {problem && (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      )}
      {record.guardians.length === 0 ? (
        <p className="text-muted-foreground">{t(`${k}empty`)}</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {record.guardians.map((g) => (
            <li key={g.guardianId} className="flex flex-col gap-2 rounded-md border p-3">
              <p className="break-words font-medium">{g.name}</p>
              <p className="break-words text-sm text-muted-foreground">{[g.phone && formatPhone(g.phone), g.email].filter(Boolean).join(" · ")}</p>
              <div className="flex flex-wrap gap-1">
                {g.financial && <span className="rounded-md border px-2 py-0.5 text-sm">{t(`${k}financial`)}</span>}
                {g.access && <span className="rounded-md border px-2 py-0.5 text-sm">{t(g.access === "ACTIVE" ? `${k}accessActive` : `${k}accessPending`)}</span>}
              </div>
              <div className="flex flex-wrap gap-2">
                {g.access === "PENDING" && (
                  <Button variant="outline" className="min-h-11" onClick={() => run(() => api(`${base}/${g.guardianId}/invite`, { method: "POST" }), t(`${k}resent`, { name: g.name }))}>
                    {t(`${k}resend`)}
                  </Button>
                )}
                {!g.financial && (
                  <Button variant="outline" className="min-h-11" onClick={() => run(() => api(`${base}/${g.guardianId}`, { method: "PUT", body: { financial: true } }), t(`${k}madeFinancial`, { name: g.name }))}>
                    {t(`${k}makeFinancial`)}
                  </Button>
                )}
                <Button variant="outline" className="min-h-11" onClick={() => setUnlinking(g)}>
                  {t(`${k}unlink`)}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
      <div>
        <Button className="min-h-11" onClick={() => setLinking(true)}>
          {t(`${k}link`)}
        </Button>
      </div>
      {unlinking && (
        <ConfirmDialog
          open
          onOpenChange={(o) => !o && setUnlinking(null)}
          title={t(`${k}unlinkTitle`, { name: unlinking.name })}
          summary={t(`${k}unlinkSummary`)}
          reason="required"
          confirmLabel={t(`${k}unlink`)}
          onConfirm={(reason) => run(() => api(`${base}/${unlinking.guardianId}/unlink`, { method: "POST", body: { reason } }), t(`${k}unlinked`, { name: unlinking.name }))}
        />
      )}
      <GuardianLinkDialog
        studentId={record.id}
        open={linking}
        onOpenChange={setLinking}
        onLinked={(name) => {
          setProblem(null);
          setNotice(t(`${k}linked`, { name }));
          onChanged();
        }}
      />
    </div>
  );
}
