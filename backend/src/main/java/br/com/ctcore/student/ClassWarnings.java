package br.com.ctcore.student;

import br.com.ctcore.trainingclass.TrainingClasses.AgeRangeRef;

/** RF-MAT-02: avisos da turma escolhida na matrícula. Só informam; nada impede a matrícula (RN-MOD-07, RN-TUR-02). */
public record ClassWarnings(int studentAge, AgeRangeRef ageRange, boolean ageOutOfRange, Integer capacity, int activeStudents, boolean overCapacity) {
    /** RN-MOD-07: fora da faixa quando a turma tem faixa e a idade é menor que a mínima ou maior que a máxima. */
    static boolean outOfRange(int age, AgeRangeRef range) {
        return range != null && (age < range.minAge() || (range.maxAge() != null && age > range.maxAge()));
    }

    /** D1: avisa quando esta matrícula faria a turma passar da capacidade; quem já está ativo nela não conta de novo. */
    static boolean over(int active, boolean alreadyIn, Integer capacity) {
        return capacity != null && active + (alreadyIn ? 0 : 1) > capacity;
    }
}
