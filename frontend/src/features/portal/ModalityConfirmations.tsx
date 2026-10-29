import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import { formatCurrency } from "@/lib/format";

// Espelha ModalityConfirmationItem do OpenAPI.
interface Item {
  modalityRowId: string;
  studentId: string;
  studentName: string;
  modality: string;
  unit: string;
  plan: string;
  planValueCents: number;
}

// RN-MAT-13: confirmação curta (plano e valor) da modalidade nova. Não bloqueia nada; sem pendência, o cartão não aparece.
export function ModalityConfirmations() {
  const { t } = useTranslation();
  const [items, setItems] = useState<Item[]>([]);
  const [failed, setFailed] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let alive = true;
    api<Item[]>("/portal/modality-confirmations").then(
      (data) => {
        if (!alive) return;
        setLoadFailed(false);
        setItems(data);
      },
      () => alive && setLoadFailed(true),
    );
    return () => {
      alive = false;
    };
  }, [tick]);
  const confirm = async (item: Item) => {
    setFailed(false);
    try {
      await api(`/portal/modality-confirmations/${item.modalityRowId}`, { method: "POST" });
      setItems((all) => all.filter((i) => i.modalityRowId !== item.modalityRowId));
    } catch {
      setFailed(true);
    }
  };
  if (loadFailed)
    return (
      <Card heading="h2" title={t("portalConfirm.title")} className="mt-6">
        <ErrorState onRetry={() => setTick((n) => n + 1)} />
      </Card>
    );
  if (items.length === 0) return null;
  return (
    <Card heading="h2" title={t("portalConfirm.title")} className="mt-6">
      <ul className="flex flex-col divide-y">
        {items.map((i) => (
          <li key={i.modalityRowId} className="flex flex-col gap-2 py-3">
            <p className="break-words">
              {t("portalConfirm.text", { student: i.studentName, modality: i.modality, unit: i.unit, plan: i.plan, value: formatCurrency(i.planValueCents) })}
            </p>
            <Button className="min-h-11 self-start" onClick={() => confirm(i)}>
              {t("portalConfirm.confirm", { modality: i.modality })}
            </Button>
          </li>
        ))}
      </ul>
      {failed && (
        <p role="alert" className="mt-2 text-sm text-destructive">
          {t("portalConfirm.error")}
        </p>
      )}
    </Card>
  );
}
