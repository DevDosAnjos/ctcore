package br.com.ctcore.identity;

/** Erro de regra de identidade; o mapeamento para ErrorCode/HTTP entra com as rotas (T11). */
public class IdentityException extends RuntimeException {
    public enum Code { MINOR_HAS_NO_ACCOUNT, INVITE_INVALID, TOO_MANY_REQUESTS, INVALID_CREDENTIALS,
        PASSWORD_WEAK, PASSWORD_LEAKED, ALREADY_LINKED,
        CHALLENGE_FAILED, RESET_INVALID, LINK_NOT_FOUND,
        INVALID_TWO_FACTOR_CODE, TWO_FACTOR_TICKET_INVALID, REAUTH_REQUIRED, TWO_FACTOR_STATE,
        EMAIL_CHANGE_INVALID, SESSION_CURRENT, SESSION_NOT_FOUND }

    private final Code code;
    private final String detail;

    public IdentityException(Code code, String detail) {
        super(code + (detail == null ? "" : ":" + detail));
        this.code = code;
        this.detail = detail;
    }

    public Code code() { return code; }

    /** USED, EXPIRED, CANCELED, UNKNOWN or DEACTIVATED for INVITE_INVALID; null otherwise. */
    public String detail() { return detail; }
}
