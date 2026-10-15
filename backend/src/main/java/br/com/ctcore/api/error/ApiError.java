package br.com.ctcore.api.error;

import java.util.List;
import java.util.Locale;
import org.springframework.context.support.ResourceBundleMessageSource;

/** Corpo único de todo erro da API (RNF-24). Nunca leva pilha, SQL ou nome de classe. */
public record ApiError(String code, String message, boolean changed, List<Field> fields, String traceId) {

    public record Field(String field, String message) {}

    private static final ResourceBundleMessageSource MESSAGES = new ResourceBundleMessageSource();

    static {
        MESSAGES.setBasename("messages");
        MESSAGES.setDefaultEncoding("UTF-8");
    }

    /** {@code changed} é sempre false aqui: nenhuma operação altera dado antes de falhar (lotes gravam tudo ou nada). */
    public static ApiError of(ErrorCode code, String traceId, List<Field> fields) {
        String message = MESSAGES.getMessage("error." + code.name(), null, Locale.forLanguageTag("pt-BR"));
        return new ApiError(code.name(), message, false, fields, traceId);
    }
}
