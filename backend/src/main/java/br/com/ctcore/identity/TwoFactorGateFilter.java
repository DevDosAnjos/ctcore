package br.com.ctcore.identity;

import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.api.error.SecurityErrorHandlers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** RN-ACE-17 (D2): a session that must have two-step and does not reaches only the setup, the session read and the exit. */
public class TwoFactorGateFilter extends OncePerRequestFilter {
    private static final String BASE = "/api/v1/auth";

    private final TwoFactorService twoFactor;
    private final SecurityErrorHandlers errors;

    public TwoFactorGateFilter(TwoFactorService twoFactor, SecurityErrorHandlers errors) {
        this.twoFactor = twoFactor;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal && !allowed(request.getRequestURI())
                && twoFactor.setupRequired(principal.accountId())) {
            errors.write(request, response, ErrorCode.TWO_FACTOR_SETUP_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean allowed(String path) {
        return path.equals(BASE + "/session") || path.equals(BASE + "/logout")
                || path.equals(BASE + "/two-factor") || path.startsWith(BASE + "/two-factor/");
    }
}
