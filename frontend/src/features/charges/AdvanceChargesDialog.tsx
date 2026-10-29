import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { useStudentModalities } from "@/features/benefits/GrantBenefitDialog";
import { ModalityDialog, PickSelect } from "@/features/students/ModalityPickers";
import { StudentSearch } from "./StudentSearch";
import type { ChargeCreated } from "./types";
import { advanceCharges } from "./useChargeCreation";

const month = (iso: string) => `${new Date(`${iso}T12:00:00Z`).toLocaleDateString("pt-BR", { month: "short", timeZone: "UTC" }).replace(".", "")}/${iso.slice(0, 4)}`;

// RN-COB-06: gera adiantado as próximas N mensalidades de uma modalidade ativa; mostra quantas nasceram e de que meses.
export function AdvanceChargesDialog({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const k = "charges.advance.";
  const [query, setQuery] = useState("");
  const [studentId, setStudentId] = useState("");
  const [rowId, setRowId] = useState("");
  const [count, setCount] = useState("");
  const [result, setResult] = useState<ChargeCreated[] | null>(null);
  // O ModalityDialog fecha logo depois de concluir; a ref guarda o resultado para trocar a janela pelo resumo em vez de fechar.
  const created = useRef<ChargeCreated[] | null>(null);
  const { students, enrollment } = useStudentModalities(query, studentId);
  const modalities = (enrollment.data?.modalities ?? []).filter((m) => m.status === "ACTIVE" && m.canEdit);
  const n = /^\d{1,2}$/.test(count) ? Number(count) : 0;

  if (result) {
    const months = result.map((c) => c.referenceMonth).sort();
    return (
      <Modal open onOpenChange={(o) => !o && onClose()} title={t(`${k}doneTitle`)}>
        <div className="flex flex-col gap-3">
          <p role="status">{result.length === 0 ? t(`${k}doneNone`) : t(`${k}done`, { count: result.length, from: month(months[0]), to: month(months[months.length - 1]) })}</p>
          <div className="flex justify-end">
            <Button type="button" className="min-h-11" onClick={onClose}>
              {t(`${k}close`)}
            </Button>
          </div>
        </div>
      </Modal>
    );
  }

  return (
    <ModalityDialog
      title={t(`${k}title`)}
      effect={t(`${k}effect`)}
      state="ready"
      submitLabel={t(`${k}submit`)}
      canSubmit={!!studentId && !!rowId && n >= 1 && n <= 12}
      errorPrefix={`${k}errors.`}
      work={async () => {
        created.current = await advanceCharges(studentId, rowId, n);
      }}
      onClose={() => (created.current ? setResult(created.current) : onClose())}
      onDone={onDone}
    >
      {(errors) => (
        <>
          <StudentSearch
            query={query}
            onQuery={(q) => {
              setQuery(q);
              setStudentId("");
              setRowId("");
            }}
            students={students}
            studentId={studentId}
            onPick={(v) => {
              setStudentId(v);
              setRowId("");
            }}
            error={errors.studentId}
          />
          {studentId && enrollment.status === "loading" && <LoadingState />}
          {studentId && enrollment.status === "forbidden" && <ForbiddenState />}
          {studentId && (enrollment.status === "error" || enrollment.status === "notFound") && <ErrorState onRetry={enrollment.reload} />}
          {studentId && enrollment.status === "ready" && modalities.length === 0 && <EmptyState title={t(`${k}noModalityTitle`)} text={t(`${k}noModalityText`)} />}
          {studentId && enrollment.status === "ready" && modalities.length > 0 && (
            <PickSelect label={t(`${k}modality`)} value={rowId} onChange={setRowId} options={modalities.map((m) => ({ id: m.id, name: `${m.modality.name} · ${m.unit.name}` }))} error={errors.modalityRowId} />
          )}
          <label className="flex flex-col gap-1 text-sm font-medium">
            {t(`${k}count`)}
            <Input
              inputMode="numeric"
              value={count}
              aria-invalid={errors.count ? true : undefined}
              onChange={(e) => setCount(e.target.value.replace(/\D/g, "").slice(0, 2))}
            />
          </label>
          <p className="text-sm text-muted-foreground">{t(`${k}countHint`)}</p>
          {errors.count && <p className="text-sm text-danger-foreground">{errors.count}</p>}
        </>
      )}
    </ModalityDialog>
  );
}
