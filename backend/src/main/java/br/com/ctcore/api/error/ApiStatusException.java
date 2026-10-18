package br.com.ctcore.api.error;

/** Recusa de regra com status próprio (404 ou 409), sem campos. */
public class ApiStatusException extends RuntimeException {

    public final transient ErrorCode code;

    public final transient java.util.List<ApiError.Field> fields;

    public ApiStatusException(ErrorCode code) {
        this(code, java.util.List.of());
    }

    public ApiStatusException(ErrorCode code, java.util.List<ApiError.Field> fields) {
        super(code.name());
        this.code = code;
        this.fields = fields;
    }
}
