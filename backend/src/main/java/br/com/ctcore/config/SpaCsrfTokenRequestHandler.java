package br.com.ctcore.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * D7 / RNF-07: CSRF para o SPA (padrão da documentação do Spring Security 6; o {@code csrf.spa()} não existe na 6.5).
 * O token é carregado em toda requisição, então o cookie XSRF-TOKEN chega ao navegador já na primeira resposta,
 * mesmo sem sessão. O SPA lê o cookie e devolve o valor puro em X-XSRF-TOKEN; valor vindo de parâmetro
 * (formulário renderizado pelo servidor) passa pela proteção BREACH do Xor.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        // Sem carregar o token adiado, o CookieCsrfTokenRepository nunca grava o cookie.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String header = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(header) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
