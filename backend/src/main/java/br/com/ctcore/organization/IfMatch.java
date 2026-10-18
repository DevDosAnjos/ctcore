package br.com.ctcore.organization;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;

/** RNF-15: cabeçalho {@code If-Match} com a versão inteira lida; ausente ou não numérico responde 428. */
final class IfMatch {
    private IfMatch() {}

    static int version(String header) {
        if (header != null) {
            String value = header.trim();
            if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            if (value.matches("[0-9]{1,9}")) return Integer.parseInt(value);
        }
        throw new ApiStatusException(ErrorCode.PRECONDITION_REQUIRED);
    }
}
