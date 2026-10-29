import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Card } from "@/components/Card";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ChangeEmailForm } from "@/features/account/ChangeEmailForm";
import { ChangePasswordForm } from "@/features/account/ChangePasswordForm";
import { SessionsSection } from "@/features/account/SessionsSection";
import { TwoFactorSection } from "@/features/account/TwoFactorSection";
import { useAccountSessions } from "@/features/account/useAccount";
import { useMyRequests } from "./usePortalRequests";
import { formatDate, formatDateTime } from "@/lib/format";
import { setEmailAnnouncements, setPortalImageUse, type PortalInfoData } from "./usePortalInfo";

// RF-POR-07, RN-COM-13: comunicados por e-mail (vale para a conta na organização).
export function PortalPreferences({ initial }: { initial: boolean }) {
  const { t } = useTranslation();
  const [on, setOn] = useState(initial);
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  async function change(next: boolean) {
    setBusy(true);
    setFailed(false);
    try {
      setOn((await setEmailAnnouncements(next)).announcements);
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Card heading="h2" title={t("portalInfo.preferences.title")}>
      <label className="flex min-h-11 items-center gap-2 text-sm">
        <input type="checkbox" className="size-5" checked={on} disabled={busy} onChange={(e) => void change(e.target.checked)} />
        <span>{t("portalInfo.preferences.emailAnnouncements")}</span>
      </label>
      {failed && (
        <p role="alert" className="text-sm text-danger-foreground">
          {t("portalInfo.preferences.failed")}
        </p>
      )}
    </Card>
  );
}

// RF-POR-07, RN-LGP-01, RN-MAT-12: termo de matrícula aceito e uso de imagem, que o aluno ou responsável revoga ou concede aqui.
export function PortalTermsAccepted({ studentId, terms, onChanged }: { studentId: string; terms: PortalInfoData["terms"]; onChanged: () => void }) {
  const { t } = useTranslation();
  const k = "portalInfo.terms.";
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const { enrollment, imageUse } = terms;
  async function toggle() {
    setBusy(true);
    setFailed(false);
    try {
      await setPortalImageUse(studentId, !imageUse.granted);
      onChanged();
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Card heading="h2" title={t(`${k}title`)}>
      <dl className="flex flex-col gap-3 text-sm">
        <div>
          <dt className="font-medium">{t(`${k}enrollment`)}</dt>
          <dd>{enrollment ? t(`${k}enrollmentAccepted`, { version: enrollment.version, date: formatDateTime(enrollment.acceptedAt) }) : t(`${k}enrollmentNone`)}</dd>
        </div>
        <div>
          <dt className="font-medium">{t(`${k}imageUse`)}</dt>
          <dd className="flex flex-col items-start gap-2">
            <span>{imageUse.granted ? t(`${k}imageGranted`, { version: imageUse.version ?? "", date: imageUse.at ? formatDate(imageUse.at) : "" }) : t(`${k}imageNotGranted`)}</span>
            <Button variant="outline" className="min-h-11" disabled={busy} onClick={() => void toggle()}>
              {imageUse.granted ? t(`${k}revoke`) : t(`${k}grant`)}
            </Button>
          </dd>
        </div>
      </dl>
      {failed && (
        <p role="alert" className="text-sm text-danger-foreground">
          {t(`${k}failed`)}
        </p>
      )}
    </Card>
  );
}

// RF-POR-07, RN-POR-07: os últimos pedidos do aluno; o pedido em si é feito em /portal/pedidos.
export function PortalRequestsSummary({ studentId }: { studentId: string }) {
  const { t } = useTranslation();
  const k = "portalInfo.requests.";
  const mine = useMyRequests(studentId);
  return (
    <Card heading="h2" title={t(`${k}title`)}>
      {mine.status === "loading" && <LoadingState />}
      {mine.status === "forbidden" && <ForbiddenState />}
      {mine.status === "error" && <ErrorState onRetry={mine.reload} />}
      {mine.data && mine.data.length === 0 && <p className="text-sm">{t(`${k}empty`)}</p>}
      {mine.data && mine.data.length > 0 && (
        <ul className="flex flex-col divide-y text-sm">
          {mine.data.slice(0, 5).map((r) => (
            <li key={r.id} className="py-2">
              {t(`${k}line`, { kind: t(`portalRequests.kinds.${r.kind}`), status: t(`portalRequests.statuses.${r.status}`), date: formatDate(r.createdAt) })}
            </li>
          ))}
        </ul>
      )}
      <Link to="/portal/pedidos" className="mt-3 inline-flex min-h-11 items-center underline">
        {t(`${k}make`)}
      </Link>
    </Card>
  );
}

// RF-ACE-04: os mesmos formulários de Minha conta (senha, e-mail, duas etapas e sessões), sem rota nova.
export function PortalAccount() {
  const { t } = useTranslation();
  const sessions = useAccountSessions();
  return (
    <section aria-labelledby="portal-account" className="flex flex-col gap-4">
      <h2 id="portal-account" className="text-xl font-semibold">
        {t("portalInfo.account.title")}
      </h2>
      <ChangePasswordForm onChanged={sessions.reload} />
      <ChangeEmailForm />
      <TwoFactorSection />
      <SessionsSection {...sessions} />
    </section>
  );
}

// D4: aparecem e ficam desabilitados até a Sprint 13 (RN-LGP-04, RN-LGP-05); sem rota nem chamada.
export function PortalDataActions() {
  const { t } = useTranslation();
  const k = "portalInfo.data.";
  return (
    <Card heading="h2" title={t(`${k}title`)}>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" className="min-h-11" disabled>
          {t(`${k}download`)}
        </Button>
        <Button variant="outline" className="min-h-11" disabled>
          {t(`${k}delete`)}
        </Button>
      </div>
      <p className="mt-2 text-sm text-muted-foreground">{t(`${k}soon`)}</p>
    </Card>
  );
}
