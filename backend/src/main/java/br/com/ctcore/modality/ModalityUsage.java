package br.com.ctcore.modality;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** RN-MOD-06: o que impede desativar uma modalidade. A versão real entra junto com as tabelas de turma e matrícula. */
public interface ModalityUsage {
    int activeClasses(UUID modalityId);

    int activeStudents(UUID modalityId);

    /** ponytail: sem tabelas de turma e aluno até as Sprints 05 e 07; trocar por count real. */
    @Component
    class None implements ModalityUsage {
        @Override
        public int activeClasses(UUID modalityId) {
            return 0;
        }

        @Override
        public int activeStudents(UUID modalityId) {
            return 0;
        }
    }
}
