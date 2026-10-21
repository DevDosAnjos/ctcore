import type { ConclusionRequest, Drafts, ModalityDraft } from "./types";

export const MAX_ACTIVE = 10;
export const emptyBenefit = (): ModalityDraft["benefit"] => ({ on: false, type: "PERCENT", percent: "", amount: null, reason: "", decidedBy: "", validUntil: "" });
export const emptyOrigin = (): ModalityDraft["origin"] => ({ on: false, levelId: "", degree: "0", achievedOn: "", proofKey: "", proofName: "", proofRequired: false });
export const newModality = (): ModalityDraft => ({ key: crypto.randomUUID(), unitId: "", modalityId: "", primaryClassId: "", otherClassIds: [], planId: "", benefit: emptyBenefit(), origin: emptyOrigin() });

export const benefitValue = (b: ModalityDraft["benefit"]): number | null => {
  if (b.type === "AMOUNT") return b.amount && b.amount > 0 ? b.amount : null;
  const n = Number(b.percent);
  return /^\d{1,3}$/.test(b.percent) && n >= 1 && n <= 100 ? n : null;
};

// Rascunho completo da modalidade (etapa 2); o servidor reconfere tudo.
export const modalityReady = (m: ModalityDraft): boolean =>
  Boolean(m.unitId && m.modalityId && m.primaryClassId && m.planId) &&
  (!m.benefit.on || (benefitValue(m.benefit) !== null && m.benefit.reason.trim().length >= 2 && m.benefit.decidedBy.trim().length >= 2)) &&
  (!m.origin.on || (Boolean(m.origin.levelId) && (!m.origin.proofRequired || Boolean(m.origin.proofKey))));

// Monta o pedido da prévia e da conclusão: um dia de vencimento por unidade, a taxa e a segunda cobrança só quando preenchidos.
export function buildRequest(modalities: ModalityDraft[], drafts: Drafts): ConclusionRequest {
  const units = [...new Set(modalities.map((m) => m.unitId))];
  return {
    modalities: modalities.map((m) => {
      const due = drafts[m.unitId]?.dueDay;
      const value = benefitValue(m.benefit);
      return {
        unitId: m.unitId,
        modalityId: m.modalityId,
        primaryClassId: m.primaryClassId,
        otherClassIds: m.otherClassIds,
        planId: m.planId,
        ...(due ? { dueDay: Number(due) } : {}),
        ...(m.benefit.on && value !== null
          ? { benefit: { type: m.benefit.type, value, reason: m.benefit.reason.trim(), decidedBy: m.benefit.decidedBy.trim(), validUntil: m.benefit.validUntil || null } }
          : {}),
        ...(m.origin.on && m.origin.levelId
          ? { origin: { levelId: m.origin.levelId, degree: Number(m.origin.degree) || 0, achievedOn: m.origin.achievedOn || null, proofKey: m.origin.proofKey || null } }
          : {}),
      };
    }),
    units: units.map((unitId) => {
      const d = drafts[unitId];
      return {
        unitId,
        ...(d?.feeExempt ? { feeExempt: true, feeExemptReason: d.feeExemptReason.trim() } : {}),
        ...(d?.secondDueOn ? { secondDueOn: d.secondDueOn } : {}),
      };
    }),
  };
}
