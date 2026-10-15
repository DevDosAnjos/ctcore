package br.com.ctcore.api.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Dá um identificador a cada requisição; vai no corpo do erro e no cabeçalho X-Trace-Id. Logs correlacionados: #5. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    static final String ATTRIBUTE = "ctcore.traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader("X-Trace-Id", id);
        chain.doFilter(request, response);
    }

    public static String of(HttpServletRequest request) {
        Object id = request.getAttribute(ATTRIBUTE);
        return id != null ? id.toString() : UUID.randomUUID().toString();
    }
}
