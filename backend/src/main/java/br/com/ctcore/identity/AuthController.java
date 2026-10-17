package br.com.ctcore.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account routes (RN-ACE-04: none of them creates an account). Public ones are listed in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
    record LoginRequest(@NotBlank String email, @NotBlank String password, String challengeToken) {}
    record TwoFactorLoginRequest(@NotBlank String ticket, @NotBlank String code) {}
    record AcceptRequest(@NotBlank String password) {}
    record ResetRequest(@NotBlank String email, String challengeToken) {}
    record ResetBody(@NotBlank String password) {}
    record ChangePassword(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    record LinkRequest(@NotNull UUID linkId) {}

    private final AuthService auth;
    private final SessionService sessions;
    private final InviteService invites;
    private final MessageSource messages;

    AuthController(AuthService auth, SessionService sessions, InviteService invites, MessageSource messages) {
        this.auth = auth;
        this.sessions = sessions;
        this.invites = invites;
        this.messages = messages;
    }

    private static String device(HttpServletRequest request) {
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        return agent == null || agent.length() <= 200 ? agent : agent.substring(0, 200);
    }

    private static AccountPrincipal require(AccountPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Account session required");
        }
        return principal;
    }

    /** 200 with the session view and the SESSION cookie. */
    private ResponseEntity<SessionService.SessionView> started(SessionService.Created created) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, SessionCookie.create(created.token()).toString())
                .body(sessions.view(created.accountId(), created.activeLinkId()));
    }

    /** 200 with the session, or 200 {twoFactorRequired, ticket} when the account has two-step on (RN-ACE-17). */
    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest r, HttpServletRequest request) {
        var outcome = auth.login(r.email(), r.password(), r.challengeToken(), request.getRemoteAddr(), device(request));
        if (outcome.ticket() != null) {
            return ResponseEntity.ok(Map.of("twoFactorRequired", true, "ticket", outcome.ticket()));
        }
        return started(outcome.session());
    }

    @PostMapping("/login/two-factor")
    ResponseEntity<SessionService.SessionView> loginTwoFactor(@Valid @RequestBody TwoFactorLoginRequest r, HttpServletRequest request) {
        return started(auth.loginSecondFactor(r.ticket(), r.code(), request.getRemoteAddr(), device(request)));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal AccountPrincipal principal) {
        sessions.end(require(principal).sessionId());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, SessionCookie.clear().toString()).build();
    }

    @GetMapping("/session")
    SessionService.SessionView session(@AuthenticationPrincipal AccountPrincipal principal) {
        return sessions.view(require(principal).accountId(), principal.activeLinkId());
    }

    @PutMapping("/session/link")
    SessionService.SessionView selectLink(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody LinkRequest r) {
        sessions.selectLink(require(principal).accountId(), principal.sessionId(), r.linkId());
        return sessions.view(principal.accountId(), r.linkId());
    }

    @GetMapping("/invitations/{code}")
    InviteService.Info invitation(@PathVariable String code) {
        return invites.describe(code);
    }

    @PostMapping("/invitations/{code}/accept")
    ResponseEntity<SessionService.SessionView> accept(@PathVariable String code, @Valid @RequestBody AcceptRequest r,
            HttpServletRequest request) {
        var accepted = invites.accept(code, r.password(), request.getRemoteAddr());
        return started(sessions.create(accepted.accountId(), request.getRemoteAddr(), device(request)));
    }

    @PostMapping("/password-reset")
    Map<String, String> requestReset(@Valid @RequestBody ResetRequest r, HttpServletRequest request) {
        auth.requestReset(r.email(), r.challengeToken(), request.getRemoteAddr());
        return Map.of("message", messages.getMessage("auth.reset.requested", null, Locale.forLanguageTag("pt-BR")));
    }

    @PostMapping("/password-reset/{code}")
    ResponseEntity<SessionService.SessionView> reset(@PathVariable String code, @Valid @RequestBody ResetBody r,
            HttpServletRequest request) {
        var created = auth.reset(code, r.password(), request.getRemoteAddr(), device(request));
        return started(created);
    }

    @PostMapping("/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody ChangePassword r,
            HttpServletRequest request) {
        auth.change(require(principal).accountId(), principal.sessionId(), r.currentPassword(), r.newPassword(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
