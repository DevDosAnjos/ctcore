package br.com.ctcore.isolation;

import br.com.ctcore.api.error.SecurityErrorHandlers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class OrganizationScopeFilter extends OncePerRequestFilter {
    private final SecurityErrorHandlers errors;
    private final OrganizationTransactions transactions;
    private final JdbcClient jdbc;

    public OrganizationScopeFilter(SecurityErrorHandlers errors, OrganizationTransactions transactions, JdbcClient jdbc) {
        this.errors = errors;
        this.transactions = transactions;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/v1/") || authentication == null
                || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        // Rotas de conta (/auth) valem para a conta da sessão e conferem a própria conta; não abrem contexto de organização.
        // /permissions também: quem não tem vínculo ativo ou é de portal recebe tudo NONE (200), não 403; a rota decide pelo principal.
        if (path.startsWith("/api/v1/auth/") || path.equals("/api/v1/permissions")) {
            chain.doFilter(request, response);
            return;
        }
        if (!(authentication.getPrincipal() instanceof OrganizationPrincipal principal)) {
            errors.handle(request, response, new AccessDeniedException("Organization scope required"));
            return;
        }
        OrganizationContext context;
        try {
            context = OrganizationContext.open(principal);
        } catch (AccessDeniedException denied) {
            errors.handle(request, response, denied);
            return;
        }
        try (context) {
            if (principal.unitId() != null && !transactions.execute(() -> jdbc.sql(
                    "select exists(select 1 from unit where organization_id = :org and id = :unit)")
                    .param("org", OrganizationContext.organizationId()).param("unit", principal.unitId())
                    .query(Boolean.class).single())) {
                errors.handle(request, response, new AccessDeniedException("Invalid unit scope"));
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
