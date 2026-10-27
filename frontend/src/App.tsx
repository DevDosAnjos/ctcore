import { useTranslation } from "react-i18next";
import { Link, Route, Routes } from "react-router";
import { LoginPage } from "@/features/auth/LoginPage";
import { ForgotPasswordPage } from "@/features/auth/ForgotPasswordPage";
import { AreaUnavailablePage } from "@/features/auth/AreaUnavailablePage";
import { InvitePage } from "@/features/auth/InvitePage";
import { PublicReceiptPage } from "@/features/payments/PublicReceiptPage";
import { ResetPasswordPage } from "@/features/auth/ResetPasswordPage";
import { RequireSession } from "@/features/auth/RequireSession";
import { RequireTwoFactor, TWO_FACTOR_REQUIRED_PATH } from "@/features/auth/RequireTwoFactor";
import { TwoFactorRequiredPage } from "@/features/auth/TwoFactorRequiredPage";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { OrganizationSettingsPage } from "@/features/organization/OrganizationSettingsPage";
import { AgeRangesPage } from "@/features/age-ranges/AgeRangesPage";
import { TermsPage } from "@/features/terms/TermsPage";
import { TextsPage } from "@/features/texts/TextsPage";
import { ModalitiesPage } from "@/features/modalities/ModalitiesPage";
import { NewUnitPage } from "@/features/units/NewUnitPage";
import { ScopeLine } from "@/features/units/UnitSwitcher";
import { UnitSettingsPage } from "@/features/units/UnitSettingsPage";
import { UnitsPage } from "@/features/units/UnitsPage";
import { BirthdaysCard } from "@/features/students/BirthdaysCard";
import { EnrollmentWizardPage } from "@/features/students/enrollment-wizard/EnrollmentWizardPage";
import { PortalRequestsPage } from "@/features/portal-requests/PortalRequestsPage";
import { StudentsPage } from "@/features/students/StudentsPage";
import { ChargesPage } from "@/features/charges/ChargesPage";
import { ChargePreviewPage } from "@/features/charges/ChargePreviewPage";
import { DailyReceiptsPage } from "@/features/charges/DailyReceiptsPage";
import { OverduePage } from "@/features/charges/OverduePage";
import { PaymentProofsPage } from "@/features/payment-proofs/PaymentProofsPage";
import { AnnouncementsPage } from "@/features/announcements/AnnouncementsPage";
import { NewAnnouncementPage } from "@/features/announcements/NewAnnouncementPage";
import { TeachersPage } from "@/features/teachers/TeachersPage";
import { TeacherPanelPage } from "@/features/teachers/TeacherPanelPage";
import { ClassesPage } from "@/features/classes/ClassesPage";
import { ClassPanelPage } from "@/features/classes/ClassPanelPage";
import { PlansPage } from "@/features/plans/PlansPage";
import { PlansTablePage } from "@/features/plans/PlansTablePage";
import { BenefitsPage } from "@/features/benefits/BenefitsPage";
import { AgendaPage } from "@/features/agenda/AgendaPage";
import { LessonPanelPage } from "@/features/agenda/LessonPanelPage";
import { NoClassDaysPage } from "@/features/noClassDays/NoClassDaysPage";
import { UsersPage } from "@/features/users/UsersPage";
import { AccountPage } from "@/features/account/AccountPage";
import { ConfirmEmailPage } from "@/features/account/ConfirmEmailPage";
import { ModalityConfirmations } from "@/features/portal/ModalityConfirmations";
import { PortalRequestsPage as PortalOwnRequestsPage } from "@/features/portal/PortalRequestsPage";
import { PortalAgendaPage } from "@/features/portal/PortalAgendaPage";
import { PortalCardPage } from "@/features/portal/PortalCardPage";
import { PortalHomePage } from "@/features/portal/PortalHomePage";
import { PortalLayout } from "@/features/portal/PortalLayout";
import { PortalSectionPage } from "@/features/portal/PortalSectionPage";
import { RequireFullPortal } from "@/features/portal/RequireFullPortal";
import { PortalTermPage } from "@/features/portal/PortalTermPage";
import { RequirePortalTerm } from "@/features/portal/RequirePortalTerm";
import { RequireArea } from "@/features/auth/RequireArea";
import { useSession } from "@/features/auth/SessionProvider";

function Welcome() {
  const { t } = useTranslation();
  return (
    <>
      <h1 className="text-2xl font-semibold">{t("app.welcomeTitle")}</h1>
      <p className="mt-2 text-muted-foreground">{t("app.welcomeText")}</p>
    </>
  );
}

// Gestão (Coordenador, Administrador, Professor) ganha o menu pelas permissões do servidor; portal segue sem menu.
function Home() {
  const { t } = useTranslation();
  const { state } = useSession();
  const session = state.status === "ready" ? state.session : undefined;
  const role = session?.links.find((l) => l.id === session.activeLinkId)?.role;
  if (role === "COORDINATOR" || role === "ADMINISTRATOR" || role === "TEACHER") {
    return (
      <ManagementLayout>
        <Welcome />
        <ScopeLine />
        {(role === "ADMINISTRATOR" || role === "TEACHER") && <BirthdaysCard />}
      </ManagementLayout>
    );
  }
  return (
    <PortalLayout>
      <PortalHomePage />
      <ModalityConfirmations />
      <Link to="/portal/pedidos" className="mt-6 inline-flex min-h-11 items-center underline">
        {t("portalOwn.link")}
      </Link>
    </PortalLayout>
  );
}

// Precisa estar dentro de um roteador e do SessionProvider (main.tsx).
export function App() {
  return (
    <Routes>
      <Route path="/entrar" element={<LoginPage />} />
      <Route path="/esqueci-senha" element={<ForgotPasswordPage />} />
      <Route path="/redefinir-senha/:code" element={<ResetPasswordPage />} />
      <Route path="/convite/:code" element={<InvitePage />} />
      <Route path="/recibo/:codigo" element={<PublicReceiptPage />} />
      <Route element={<RequireSession />}>
        <Route element={<RequireTwoFactor />}>
          <Route path={TWO_FACTOR_REQUIRED_PATH} element={<TwoFactorRequiredPage />} />
          <Route path="/portal/termo" element={<PortalTermPage />} />
          <Route element={<RequirePortalTerm />}>
            <Route path="/" element={<Home />} />
            <Route path="/conta" element={<AccountPage />} />
            <Route path="/portal/pedidos" element={<PortalOwnRequestsPage />} />
            <Route path="/portal/carteirinha" element={<PortalCardPage />} />
            <Route element={<RequireFullPortal />}>
              <Route path="/portal/agenda" element={<PortalAgendaPage />} />
              <Route path="/portal/avisos" element={<PortalSectionPage tab="notices" />} />
            </Route>
            <Route path="/portal/financeiro" element={<PortalSectionPage tab="finance" />} />
            <Route path="/portal/minhas-informacoes" element={<PortalSectionPage tab="info" />} />
          </Route>
          <Route path="/confirmar-email/:code" element={<ConfirmEmailPage />} />
          <Route path="/sem-acesso" element={<AreaUnavailablePage />} />
          <Route element={<RequireArea roles={["COORDINATOR", "ADMINISTRATOR", "TEACHER"]} />}>
            <Route path="/agenda" element={<AgendaPage />} />
            <Route path="/agenda/aulas/:id" element={<LessonPanelPage />} />
            <Route path="/comunicados" element={<AnnouncementsPage />} />
            <Route path="/comunicados/novo" element={<NewAnnouncementPage />} />
          </Route>
          <Route element={<RequireArea roles={["COORDINATOR", "ADMINISTRATOR"]} />}>
            <Route path="/dias-sem-aula" element={<NoClassDaysPage />} />
            <Route path="/turmas" element={<ClassesPage />} />
            <Route path="/turmas/:id" element={<ClassPanelPage />} />
            <Route path="/planos" element={<PlansPage />} />
            <Route path="/planos/lista" element={<PlansTablePage />} />
            <Route path="/beneficios" element={<BenefitsPage />} />
            <Route path="/alunos/nova-matricula" element={<EnrollmentWizardPage />} />
            <Route path="/alunos/:id?" element={<StudentsPage />} />
            <Route path="/pedidos" element={<PortalRequestsPage />} />
            <Route path="/cobrancas" element={<ChargesPage />} />
            <Route path="/cobrancas/previa" element={<ChargePreviewPage />} />
            <Route path="/cobrancas/recebimentos" element={<DailyReceiptsPage />} />
            <Route path="/cobrancas/atrasados" element={<OverduePage />} />
            <Route path="/cobrancas/conferencia" element={<PaymentProofsPage />} />
            <Route path="/professores" element={<TeachersPage />} />
            <Route path="/professores/:id" element={<TeacherPanelPage />} />
            <Route path="/configuracoes/organizacao" element={<OrganizationSettingsPage />} />
            <Route path="/configuracoes/unidade" element={<UnitSettingsPage />} />
            <Route path="/configuracoes/modalidades" element={<ModalitiesPage />} />
            <Route path="/configuracoes/faixas-etarias" element={<AgeRangesPage />} />
            <Route path="/configuracoes/termos" element={<TermsPage />} />
            <Route path="/configuracoes/textos" element={<TextsPage />} />
            <Route path="/configuracoes/usuarios" element={<UsersPage />} />
          </Route>
          <Route element={<RequireArea roles={["COORDINATOR"]} />}>
            <Route path="/configuracoes/unidades" element={<UnitsPage />} />
            <Route path="/configuracoes/unidades/nova" element={<NewUnitPage />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  );
}
