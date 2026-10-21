import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { buttonVariants } from "@/components/ui/button";

// Entrada do assistente "Nova matrícula" (RF-MAT-01); com `studentId` abre já no aluno, a partir da etapa 2.
export function NewEnrollmentLink({ studentId }: { studentId?: string }) {
  const { t } = useTranslation();
  return (
    <Link to={studentId ? `/alunos/nova-matricula?aluno=${studentId}` : "/alunos/nova-matricula"} className={`${buttonVariants({ variant: "default" })} min-h-11 self-start`}>
      {t("enrollmentWizard.open")}
    </Link>
  );
}
