package br.com.ctcore.organization;

import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** RN-ORG-11 (D1): o que impede desativar uma unidade. Roda dentro da transação da organização (RLS). */
public interface UnitDeactivationBlockers {
    int activeStudents(UUID unitId);

    int activeClasses(UUID unitId);

    /** Turmas ativas e alunos distintos com modalidade ativa na unidade (#63, R13). */
    @Component
    class Jdbc implements UnitDeactivationBlockers {
        private final JdbcClient jdbc;

        Jdbc(@Lazy JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public int activeStudents(UUID unitId) {
            return jdbc.sql("select count(distinct student_id) from enrollment_modality where unit_id = :id and status = 'ACTIVE'")
                    .param("id", unitId).query(Integer.class).single();
        }

        @Override
        public int activeClasses(UUID unitId) {
            return jdbc.sql("select count(*) from training_class where unit_id = :id and status = 'ACTIVE'")
                    .param("id", unitId).query(Integer.class).single();
        }
    }
}
