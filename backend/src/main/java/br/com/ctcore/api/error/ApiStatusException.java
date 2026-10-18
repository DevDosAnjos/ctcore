package br.com.ctcore.api.error;

/** Recusa de regra com status próprio (404 ou 409), sem campos. */
public class ApiStatusException extends RuntimeException {

    public final transient ErrorCode code;

    public ApiStatusException(ErrorCode code) {
        super(code.name());
        this.code = code;
    }
}
