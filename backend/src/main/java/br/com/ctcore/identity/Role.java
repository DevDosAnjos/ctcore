package br.com.ctcore.identity;

public enum Role {
    COORDINATOR, ADMINISTRATOR, TEACHER, STUDENT, GUARDIAN;

    /** Gestão e painel (RN-ACE-19, D3): tudo que não é portal. */
    public boolean management() {
        return this != STUDENT && this != GUARDIAN;
    }
}
