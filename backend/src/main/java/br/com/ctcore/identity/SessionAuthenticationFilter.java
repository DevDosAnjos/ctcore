package br.com.ctcore.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/** Reads the SESSION cookie, looks up its hash in account_session and puts an {@link AccountPrincipal} in the context. */
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final SessionService sessions;

    public SessionAuthenticationFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, SessionCookie.NAME);
        if (cookie != null && !cookie.getValue().isBlank()) {
            sessions.authenticate(cookie.getValue()).ifPresent(principal -> {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        principal.role() == null ? AuthorityUtils.NO_AUTHORITIES
                                : AuthorityUtils.createAuthorityList("ROLE_" + principal.role().name())));
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
