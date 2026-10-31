import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { PageHeader } from "@/components/ui/page-header";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { StudentRecordPanel } from "./StudentRecordPanel";

// RF-ALU-03: ficha "Visão do Professor", aberta pela aula; fechar volta ao painel da aula. A barreira é o servidor.
export function TeacherRecordPage() {
  const { t } = useTranslation();
  const { id = "", studentId = "" } = useParams();
  const navigate = useNavigate();
  return (
    <ManagementLayout activeId="agenda">
      <PageHeader title={t("agenda.title")} />
      <StudentRecordPanel id={studentId} onClose={() => navigate(`/agenda/aulas/${id}`)} />
    </ManagementLayout>
  );
}
