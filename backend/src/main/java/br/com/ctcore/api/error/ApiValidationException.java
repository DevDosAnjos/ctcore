package br.com.ctcore.api.error;

import java.util.List;

/** Entrada inválida detectada no código: vira 400 VALIDATION_ERROR com os campos. */
public class ApiValidationException extends RuntimeException {

    public final transient List<ApiError.Field> fields;

    public ApiValidationException(String field, String message) {
        super(field + ": " + message);
        this.fields = List.of(new ApiError.Field(field, message));
    }
}
