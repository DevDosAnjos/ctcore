package br.com.ctcore.trainingclass;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * RN-AGE-02: ponto único chamado quando o conjunto de horários da turma muda. Aulas só existem a partir de #49, que o implementa
 * para refazer só as aulas futuras não alteradas à mão; até lá não faz nada.
 */
@Component
public class ClassScheduleChange {
    public void changed(UUID classId, List<TrainingClasses.Slot> previous, List<TrainingClasses.Slot> next) {
        // Sem aulas para refazer até #49.
    }
}
