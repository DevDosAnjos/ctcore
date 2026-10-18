package br.com.ctcore.permission;

public record Grant(Access access, Scope scope) {
    public static final Grant NONE = new Grant(Access.NONE, null);
}
