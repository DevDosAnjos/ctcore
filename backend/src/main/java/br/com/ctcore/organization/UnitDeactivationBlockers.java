package br.com.ctcore.organization;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** RN-ORG-11 (D1): o que impede desativar uma unidade. A versão real entra junto com as tabelas de matrícula e turma. */
public interface UnitDeactivationBlockers {
    int activeStudents(UUID unitId);

    int activeClasses(UUID unitId);

    /** ponytail: sem tabelas de aluno e turma até as sprints de matrícula e turmas; trocar por count real. */
    @Component
    class None implements UnitDeactivationBlockers {
        @Override
        public int activeStudents(UUID unitId) {
            return 0;
        }

        @Override
        public int activeClasses(UUID unitId) {
            return 0;
        }
    }
}
