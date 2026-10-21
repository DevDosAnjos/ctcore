package br.com.ctcore.modality;

import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** RN-MOD-06: o que impede desativar uma modalidade. Roda dentro da transação da organização (RLS). */
public interface ModalityUsage {
    int activeClasses(UUID modalityId);

    int activeStudents(UUID modalityId);

    /** Turmas ativas e alunos distintos com a modalidade ativa na matrícula (#63, R12). */
    @Component
    class Jdbc implements ModalityUsage {
        private final JdbcClient jdbc;

        Jdbc(@Lazy JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public int activeClasses(UUID modalityId) {
            return jdbc.sql("select count(*) from training_class where modality_id = :id and status = 'ACTIVE'")
                    .param("id", modalityId).query(Integer.class).single();
        }

        @Override
        public int activeStudents(UUID modalityId) {
            return jdbc.sql("select count(distinct student_id) from enrollment_modality where modality_id = :id and status = 'ACTIVE'")
                    .param("id", modalityId).query(Integer.class).single();
        }
    }
}
