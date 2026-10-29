import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/field";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { ALL, useUnitScope } from "@/features/units/UnitScopeProvider";
import { formatCurrency, formatTime } from "@/lib/format";
import { todayIso } from "./ChangeDueDialog";
import type { DailyReceipts } from "./types";
import { useDailyReceipts } from "./useDailyReceipts";
import { PageHeader } from "@/components/ui/page-header";

const cell = "px-3 py-2 text-left align-top";

function Table({ title, head, children }: { title: string; head: string[]; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-lg font-semibold">{title}</h2>
      <div className="overflow-x-auto rounded-md border">
        <table className="w-full min-w-[28rem] text-sm">
          <thead className="bg-muted/50">
            <tr>
              {head.map((h) => (
                <th key={h} scope="col" className={`${cell} font-medium`}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y">{children}</tbody>
        </table>
      </div>
    </section>
  );
}

function Summary({ data }: { data: DailyReceipts }) {
  const { t } = useTranslation();
  const method = (m: string) => t(`payments.method.${m}`);
  const who = (p: { name: string } | null) => (p ? p.name : t("charges.daily.online"));
  const c = (k: string) => t(`charges.daily.columns.${k}`);
  return (
    <>
      <dl className="grid gap-3 sm:grid-cols-2">
        <div className="rounded-card border bg-card p-4">
          <dt className="text-sm text-muted-foreground">{t("charges.daily.total")}</dt>
          <dd className="text-xl font-semibold">{formatCurrency(data.totalCents)}</dd>
        </div>
        <div className="rounded-card border bg-card p-4">
          <dt className="text-sm text-muted-foreground">{t("charges.daily.count")}</dt>
          <dd className="text-xl font-semibold">{data.count}</dd>
        </div>
      </dl>
      <Table title={t("charges.daily.byMethod")} head={[c("method"), c("count"), c("total")]}>
        {data.byMethod.map((m) => (
          <tr key={m.method}>
            <td className={cell}>{method(m.method)}</td>
            <td className={cell}>{m.count}</td>
            <td className={cell}>{formatCurrency(m.totalCents)}</td>
          </tr>
        ))}
      </Table>
      <Table title={t("charges.daily.byRegistrar")} head={[c("registrar"), c("count"), c("total")]}>
        {data.byRegistrar.map((r) => (
          <tr key={r.registeredBy?.id ?? "online"}>
            <td className={cell}>{who(r.registeredBy)}</td>
            <td className={cell}>{r.count}</td>
            <td className={cell}>{formatCurrency(r.totalCents)}</td>
          </tr>
        ))}
      </Table>
      <Table title={t("charges.daily.list")} head={[c("time"), c("student"), c("reference"), c("method"), c("registrar"), c("amount"), c("receipt")]}>
        {data.payments.map((p) => (
          <tr key={p.id}>
            <td className={cell}>{formatTime(p.paidAt)}</td>
            <td className={cell}>{p.student.name}</td>
            <td className={cell}>{p.reference}</td>
            <td className={cell}>{method(p.method)}</td>
            <td className={cell}>{who(p.registeredBy)}</td>
            <td className={cell}>{formatCurrency(p.amountCents)}</td>
            <td className={cell}>{p.receiptNumber}</td>
          </tr>
        ))}
      </Table>
    </>
  );
}

function Content() {
  const { t } = useTranslation();
  const scope = useUnitScope();
  const [date, setDate] = useState(todayIso);
  const [picked, setPicked] = useState("");
  // Com "todas as unidades" a rota não tem o que consultar; a pessoa escolhe uma da lista ao alcance.
  const all = scope.selected === ALL;
  const unitId = all ? picked || null : scope.selected;
  const { status, data, reload } = useDailyReceipts(unitId, date || todayIso());

  let body;
  if (!unitId) body = <EmptyState title={t("charges.daily.pickUnit")} text={t("charges.daily.pickUnitText")} />;
  else if (status === "loading" || status === "idle") body = <LoadingState />;
  else if (status === "forbidden") body = <ForbiddenState />;
  else if (status === "error" || status === "notFound" || !data) body = <ErrorState onRetry={reload} />;
  else if (data.count === 0) body = <EmptyState title={t("charges.daily.empty")} />;
  else body = <Summary data={data} />;

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("charges.daily.title")} />
      <p className="text-muted-foreground">{t("charges.daily.intro")}</p>
      <div className="flex flex-wrap items-end gap-3">
        {all && (
          <label className="flex flex-col gap-1 text-sm font-medium">
            {t("charges.daily.unit")}
            <Select className="w-auto" value={picked} onChange={(e) => setPicked(e.target.value)}>
              <option value="">{t("charges.daily.chooseUnit")}</option>
              {scope.units.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name}
                </option>
              ))}
            </Select>
          </label>
        )}
        <label className="flex flex-col gap-1 text-sm font-medium">
          {t("charges.daily.date")}
          <Input type="date" className="w-auto" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <Button asChild variant="outline" className="min-h-11">
          <Link to="/cobrancas">{t("charges.daily.back")}</Link>
        </Button>
      </div>
      {body}
    </div>
  );
}

// RF-COB-08: recebimentos do dia da unidade, sem caixa (RN-COB-12); a barreira de dados é o servidor.
export function DailyReceiptsPage() {
  return (
    <ManagementLayout activeId="collections">
      <Content />
    </ManagementLayout>
  );
}
