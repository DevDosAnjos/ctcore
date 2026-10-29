import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Button } from "@/components/ui/button";
import { Banner } from "@/components/ui/banner";
import { Select as Control } from "@/components/ui/field";
import { QueryPage, type Page, type Query } from "@/components/query/QueryPage";
import type { Column } from "@/components/query/DataTable";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { useStudentFilterOptions } from "@/features/students/useStudents";
import { formatCurrency, formatDate } from "@/lib/format";
import { DaysToBlock, LastReminder, OverdueCard, Select, Student, Whatsapp, useOverdueReference } from "./OverdueRows";
import { SendReminderDialog } from "./SendReminderDialog";
import type { OverdueCharge } from "./types";
import { fetchOverdue } from "./useOverdue";
import { PageHeader } from "@/components/ui/page-header";

const MAX_SEND = 100;

function Content() {
  const { t } = useTranslation();
  const reference = useOverdueReference();
  const { units } = useStudentFilterOptions();
  // A seleção fica na tela (id -> linha, para o resultado citar o aluno) e vale entre páginas; recarregar a lista a zera.
  const [selected, setSelected] = useState<Map<string, OverdueCharge>>(new Map());
  const [pageRows, setPageRows] = useState<OverdueCharge[]>([]);
  const [version, setVersion] = useState(0);
  const [sending, setSending] = useState(false);
  const fetchPage = useCallback(async (q: Query): Promise<Page<OverdueCharge>> => {
    const page = await fetchOverdue(q);
    setPageRows(page.rows);
    return page;
  }, []);

  const toggle = (row: OverdueCharge) =>
    setSelected((s) => {
      const next = new Map(s);
      if (!next.delete(row.id)) next.set(row.id, row);
      return next;
    });
  const allOnPage = pageRows.length > 0 && pageRows.every((r) => selected.has(r.id));
  const toggleAll = () =>
    setSelected((s) => {
      const next = new Map(s);
      for (const r of pageRows) {
        if (allOnPage) next.delete(r.id);
        else next.set(r.id, r);
      }
      return next;
    });
  const tooMany = selected.size > MAX_SEND;
  const targets = [...selected.values()].map((r) => ({ id: r.id, label: `${r.student.name} · ${reference(r)}` }));

  const columns: Column<OverdueCharge>[] = [
    { key: "select", label: t("charges.overdue.columns.select"), render: (r) => <Select row={r} checked={selected.has(r.id)} onToggle={toggle} /> },
    { key: "student", label: t("charges.overdue.columns.student"), sortable: true, render: (r) => <Student row={r} /> },
    { key: "unit", label: t("charges.overdue.columns.unit"), render: (r) => r.unit.name },
    { key: "reference", label: t("charges.overdue.columns.reference"), render: (r) => reference(r) },
    { key: "dueOn", label: t("charges.overdue.columns.dueOn"), render: (r) => formatDate(r.dueOn) },
    { key: "amount", label: t("charges.overdue.columns.amount"), sortable: true, render: (r) => formatCurrency(r.amountCents) },
    { key: "daysLate", label: t("charges.overdue.columns.daysLate"), sortable: true, render: (r) => <span className="font-medium text-danger-foreground">{t("charges.overdue.daysLate", { count: r.daysLate })}</span> },
    { key: "lastReminder", label: t("charges.overdue.columns.lastReminder"), render: (r) => <LastReminder row={r} /> },
    { key: "daysToBlock", label: t("charges.overdue.columns.daysToBlock"), render: (r) => <DaysToBlock row={r} /> },
    { key: "whatsapp", label: t("charges.overdue.columns.whatsapp"), render: (r) => <Whatsapp row={r} /> },
  ];

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("charges.overdue.title")} />
      <p className="text-muted-foreground">{t("charges.overdue.intro")}</p>
      <div className="flex flex-wrap items-center gap-3">
        <Button asChild variant="outline" className="min-h-11">
          <Link to="/cobrancas">{t("charges.overdue.back")}</Link>
        </Button>
        <label className="flex min-h-11 items-center gap-2 text-sm">
          <input type="checkbox" className="size-6" checked={allOnPage} disabled={pageRows.length === 0} onChange={toggleAll} />
          {t("charges.overdue.selectAll")}
        </label>
        <span className="text-sm" aria-live="polite">
          {t("charges.overdue.selected", { count: selected.size })}
        </span>
        <Button type="button" className="min-h-11" disabled={selected.size === 0 || tooMany} onClick={() => setSending(true)}>
          {t("charges.overdue.send")}
        </Button>
        {tooMany && (
          <Banner tone="danger">{t("charges.overdue.tooMany")}</Banner>
        )}
      </div>
      <QueryPage
        key={version}
        columns={columns}
        fetchPage={fetchPage}
        rowKey={(r) => r.id}
        filterKeys={["unitId"]}
        filters={({ get, set }) => (
          <label className="flex min-w-0 flex-col gap-1 text-sm">
            {t("charges.overdue.unit")}
            <Control value={get("unitId")} onChange={(e) => set("unitId", e.target.value)}>
              <option value="">{t("charges.overdue.allUnits")}</option>
              {units.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name}
                </option>
              ))}
            </Control>
          </label>
        )}
        cards={(r) => <OverdueCard row={r} checked={selected.has(r.id)} onToggle={toggle} />}
      />
      {sending && (
        <SendReminderDialog
          targets={targets}
          onClose={() => setSending(false)}
          onDone={() => {
            setSelected(new Map());
            setVersion((v) => v + 1);
          }}
        />
      )}
    </div>
  );
}

// RF-COB-06: cobranças em atraso da gestão, com envio de lembrete em lote; a barreira de dados é o servidor (403 vira "sem permissão" na lista).
export function OverduePage() {
  return (
    <ManagementLayout activeId="collections">
      <Content />
    </ManagementLayout>
  );
}
