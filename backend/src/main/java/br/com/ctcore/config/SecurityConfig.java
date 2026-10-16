package br.com.ctcore.config;

import br.com.ctcore.api.error.SecurityErrorHandlers;
import br.com.ctcore.isolation.OrganizationScopeFilter;
import br.com.ctcore.isolation.OrganizationTransactions;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Tudo protegido por padrão; rotas públicas são liberadas uma a uma pelos itens que as criam (#18). */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SecurityErrorHandlers errors,
            @Lazy OrganizationTransactions transactions, @Lazy JdbcClient jdbc,
            @Value("${ctcore.security.require-https:true}") boolean requireHttps) throws Exception {
        http.addFilterAfter(new OrganizationScopeFilter(errors, transactions, jdbc), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                // D7: toda resposta garante o cookie XSRF-TOKEN; o SPA o lê e devolve em X-XSRF-TOKEN.
                .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .headers(h -> h.httpStrictTransportSecurity(s -> s.maxAgeInSeconds(31536000).includeSubDomains(true)));
        if (requireHttps) {
            http.requiresChannel(c -> c.requestMatchers(r -> !r.getRequestURI().startsWith("/actuator/")).requiresSecure());
        }
        return http.build();
    }

    /** Único codificador de senha da plataforma: Argon2id. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
