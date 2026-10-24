package br.com.ctcore.communication;

import br.com.ctcore.api.error.ApiValidationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RN-COM-05, D6: texto do comunicado com o formato mínimo {@code **negrito**}, {@code - lista} e {@code [texto](https://...)}, guardado como veio.
 * O servidor valida o tamanho e que todo link seja http ou https; o e-mail usa a forma simples ({@link #plain}).
 */
final class AnnouncementText {
    static final int MAX = 2000;
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\(([^)\\s]*)\\)");

    private AnnouncementText() {}

    /** Lança 400 no campo {@code body}; devolve o texto como veio. */
    static String validate(String body) {
        if (body == null || body.isBlank()) throw new ApiValidationException("body", "obrigatório");
        if (body.length() > MAX) throw new ApiValidationException("body", "no máximo " + MAX + " caracteres");
        Matcher m = LINK.matcher(body);
        while (m.find()) {
            String url = m.group(2).toLowerCase();
            if (!url.startsWith("http://") && !url.startsWith("https://")) throw new ApiValidationException("body", "o link precisa começar com http:// ou https://");
        }
        return body;
    }

    /** Sem os sinais: o link vira {@code texto (endereço)}. O escape de HTML é do {@code EmailService}. */
    static String plain(String body) {
        return LINK.matcher(body).replaceAll(r -> Matcher.quoteReplacement(r.group(1) + " (" + r.group(2) + ")")).replace("**", "");
    }
}
