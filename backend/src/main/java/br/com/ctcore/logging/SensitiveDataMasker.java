package br.com.ctcore.logging;

import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * RNF-11 / D3: troca por {@code [REDACTED]} senha, token, credencial de Authorization, cookie, código de
 * segurança do cartão, CPF e cartão em todo texto do log JSON (mensagem, argumentos, MDC, exceção e pilha).
 * Dado novo e sensível: acrescente a regra e o teste.
 */
public class SensitiveDataMasker implements StructuredLoggingJsonMembersCustomizer<Object> {

    static final String MASK = "[REDACTED]";

    /** Authorization e Proxy-Authorization: esquema e credencial juntos (Basic, Bearer, Digest...); mantém a chave. */
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)((?:proxy-)?authorization[\\\\\"']*\\s*[=:]\\s*[\\\\\"']*)(?:[A-Za-z][\\w-]*\\s+)?[^\\s\\\\\"',;&)]+");
    /** Cookie e Set-Cookie: o valor inteiro, até a aspa ou o fim da linha. */
    private static final Pattern COOKIE = Pattern.compile("(?i)((?:set-)?cookie[\\\\\"']*\\s*[=:]\\s*[\\\\\"']*)[^\\r\\n\\\\\"']+");
    /** chave=valor, chave: valor ou "chave":"valor"; mantém a chave. */
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)((?:password|passwd|senha|secret|token|api[-_]?key|cvv2?|cvc2?|j?session(?:id)?)[\\\\\"']*\\s*[=:]\\s*[\\\\\"']*)[^\\s\\\\\"',;&)]+");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+");
    /** Cartão: 13 a 19 dígitos, com espaço ou hífen. UUID e id curto não casam. */
    private static final Pattern CARD = Pattern.compile("(?<![\\w.-])(?:\\d[ -]?){12,18}\\d(?![\\w-])");
    /** CPF com ou sem pontuação; ponto final de frase depois dele vale, número maior (".5") não. */
    private static final Pattern CPF = Pattern.compile("(?<![\\w.-])\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?![\\w-]|\\.\\d)");

    public static String mask(String text) {
        String out = AUTHORIZATION.matcher(text).replaceAll("$1" + MASK);
        out = COOKIE.matcher(out).replaceAll("$1" + MASK);
        out = KEY_VALUE.matcher(out).replaceAll("$1" + MASK);
        out = BEARER.matcher(out).replaceAll("$1" + MASK);
        out = CARD.matcher(out).replaceAll(MASK);
        return CPF.matcher(out).replaceAll(MASK);
    }

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, SensitiveDataMasker::mask));
    }
}
