import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useSearchParams } from "react-router";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { ApiError } from "@/lib/api";
import { useEnrollment, useResource } from "../useStudentRecord";
import { concludeEnrollment } from "./api";
import { buildRequest, modalityReady, newModality } from "./request";
import { StepBilling } from "./StepBilling";
import { StepDone } from "./StepDone";
import { StepModalities } from "./StepModalities";
import { StepStudent } from "./StepStudent";
import { StepSummary } from "./StepSummary";
import type { EnrollmentConcluded, ModalityDraft, UnitDraft, WizardStudent } from "./types";

const STEPS = ["student", "modalities", "billing", "summary", "done"] as const;
const BILLING_FIELDS = new Set(["dueDay", "feeExemptReason", "secondDueOn", "units"]);
const emptyUnit = (): UnitDraft => ({ dueDay: "", feeExempt: false, feeExemptReason: "", secondDueOn: "" });

function Progress({ step }: { step: number }) {
  const { t } = useTranslation();
  return (
    <nav aria-label={t("enrollmentWizard.progress")}>
      <p role="status" className="text-sm text-muted-foreground">
        {t("stepper.step", { current: step + 1, total: STEPS.length })}
      </p>
      <ol className="flex flex-wrap gap-2">
        {STEPS.map((id, i) => (
          <li key={id} aria-current={i === step ? "step" : undefined} className={`rounded-md border px-2 py-1 text-sm ${i === step ? "font-semibold" : "text-muted-foreground"}`}>
            {t(`enrollmentWizard.steps.${id}`)}
          </li>
        ))}
      </ol>
    </nav>
  );
}

function Wizard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const presetId = params.get("aluno");
  const preset = useResource<{ id: string; name: string; photoUrl: string | null }>(presetId ? `/students/${presetId}/record` : null);
  const [chosen, setChosen] = useState<WizardStudent>();
  const [step, setStep] = useState(presetId ? 1 : 0);
  const [modalities, setModalities] = useState<ModalityDraft[]>(() => [{ ...newModality(), unitId: params.get("unidade") ?? "", modalityId: params.get("modalidade") ?? "" }]); // R18: o pedido de nova modalidade chega com unidade e modalidade
  const [drafts, setDrafts] = useState<Record<string, UnitDraft>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [network, setNetwork] = useState(false);
  const [result, setResult] = useState<EnrollmentConcluded>();
  const student: WizardStudent | undefined = chosen ?? (preset.data ? { id: preset.data.id, name: preset.data.name, photoUrl: preset.data.photoUrl, enrollmentStatus: "NONE" } : undefined);
  const enrollment = useEnrollment(student?.id ?? "", Boolean(student));
  const reachable = useResource<{ units: { id: string; name: string }[] }>("/units/reachable");

  if (presetId && !chosen) {
    if (preset.status === "loading" || preset.status === "idle") return <LoadingState />;
    if (preset.status === "forbidden") return <ForbiddenState />;
    if (preset.status === "error" || preset.status === "notFound") return <ErrorState onRetry={preset.reload} />;
  }
  if (step > 0 && student) {
    if (enrollment.status === "loading" || enrollment.status === "idle" || reachable.status === "loading") return <LoadingState />;
    if (enrollment.status === "forbidden" || reachable.status === "forbidden") return <ForbiddenState />;
    if (enrollment.status === "error" || enrollment.status === "notFound" || reachable.status === "error") {
      return <ErrorState onRetry={() => { enrollment.reload(); reachable.reload(); }} />;
    }
  }

  const active = (enrollment.data?.modalities ?? []).filter((m) => m.status === "ACTIVE");
  const inherited: Record<string, number> = Object.fromEntries(active.map((m) => [m.unit.id, m.dueDay]));
  const unitIds = [...new Set(modalities.map((m) => m.unitId).filter(Boolean))];
  const unitName = (id: string) => reachable.data?.units.find((u) => u.id === id)?.name ?? "";
  const full: Record<string, UnitDraft> = Object.fromEntries(unitIds.map((id) => [id, { ...emptyUnit(), ...drafts[id], ...(inherited[id] ? { dueDay: String(inherited[id]) } : {}) }]));
  const request = buildRequest(modalities, full);

  const valid = [
    true,
    modalities.length > 0 && modalities.every(modalityReady),
    unitIds.every((id) => Boolean(full[id].dueDay) && (!full[id].feeExempt || full[id].feeExemptReason.trim().length >= 3)),
    true,
    true,
  ];

  // Erro de regra do servidor: volta à etapa do campo, com a mensagem, sem perder o que foi digitado.
  function fail(e: ApiError) {
    const fields = Object.fromEntries(e.fields.map((f) => [f.field.split(".").pop()!, f.message]));
    setErrors(fields);
    setStep(e.fields.some((f) => BILLING_FIELDS.has(f.field)) ? 2 : 1);
  }

  async function conclude() {
    if (busy || !student) return;
    setBusy(true);
    setNetwork(false);
    setErrors({});
    try {
      setResult(await concludeEnrollment(student.id, request));
      setStep(4);
    } catch (e) {
      if (e instanceof ApiError && [400, 409, 422].includes(e.status)) fail(e);
      else setNetwork(true);
    } finally {
      setBusy(false);
    }
  }

  const go = (n: number) => {
    setErrors({});
    setStep(n);
  };

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("enrollmentWizard.title")}</h1>
      {student && step > 0 && <p className="break-words">{t("enrollmentWizard.forStudent", { name: student.name })}</p>}
      <Progress step={step} />
      {step === 0 && (
        <StepStudent
          onConfirm={(s) => {
            setChosen(s);
            go(1);
          }}
        />
      )}
      {step === 1 && student && (
        <StepModalities
          studentId={student.id}
          activeCount={active.length}
          takenModalityIds={new Set(active.map((m) => m.modality.id))}
          modalities={modalities}
          errors={errors}
          onChange={setModalities}
        />
      )}
      {step === 2 && (
        <StepBilling units={unitIds.map((id) => ({ id, name: unitName(id) }))} drafts={full} inherited={inherited} errors={errors} onChange={(id, d) => setDrafts((p) => ({ ...p, [id]: d }))} />
      )}
      {step === 3 && student && !network && (
        <>
          {Object.keys(errors).length > 0 && (
            <p role="alert" className="text-sm text-destructive">
              {Object.values(errors).join(" ")}
            </p>
          )}
          <StepSummary studentId={student.id} request={request} onInvalid={fail} onReady={() => undefined} />
        </>
      )}
      {step === 3 && network && <ErrorState onRetry={() => void conclude()} />}
      {step === 4 && student && result && <StepDone name={student.name} result={result} onPortal={() => navigate(`/alunos/${student.id}`)} />}
      {step > 0 && step < 4 && !network && (
        <div className="flex justify-between gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={() => go(step - 1)} disabled={busy || (step === 1 && Boolean(presetId))}>
            {t("common.back")}
          </Button>
          {step < 3 ? (
            <Button type="button" className="min-h-11" disabled={!valid[step]} onClick={() => go(step + 1)}>
              {t("common.next")}
            </Button>
          ) : (
            <Button type="button" className="min-h-11" disabled={busy} onClick={() => void conclude()}>
              {t("enrollmentWizard.summary.conclude")}
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

// RF-MAT-01: "Nova matrícula" em cinco etapas. A barreira de dados e de perfil é o servidor; a rota já é só da gestão.
export function EnrollmentWizardPage() {
  return (
    <ManagementLayout activeId="students">
      <Wizard />
    </ManagementLayout>
  );
}
