package br.com.ctcore.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Two-step setup, backup codes and critical-action confirmation. Always on the caller's own account (RN-ACE-17, 18, 21). */
@RestController
@RequestMapping("/api/v1/auth")
class TwoFactorController {
    record CodeRequest(@NotBlank String code) {}
    record PasswordCodeRequest(@NotBlank String password, String code) {}
    record ReauthRequest(@NotBlank String action, @NotBlank String password, String code) {}

    private final TwoFactorService twoFactor;
    private final ReauthService reauth;

    TwoFactorController(TwoFactorService twoFactor, ReauthService reauth) {
        this.twoFactor = twoFactor;
        this.reauth = reauth;
    }

    private static AccountPrincipal require(AccountPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Account session required");
        }
        return principal;
    }

    @PostMapping("/two-factor/setup")
    TwoFactorService.Setup setup(@AuthenticationPrincipal AccountPrincipal principal) {
        return twoFactor.setup(require(principal).accountId());
    }

    @PostMapping("/two-factor/enable")
    Map<String, List<String>> enable(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CodeRequest r,
            HttpServletRequest request) {
        return Map.of("backupCodes", twoFactor.enable(require(principal).accountId(), r.code(), request.getRemoteAddr()));
    }

    @DeleteMapping("/two-factor")
    ResponseEntity<Void> disable(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody PasswordCodeRequest r,
            HttpServletRequest request) {
        twoFactor.disable(require(principal).accountId(), r.password(), r.code(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/two-factor/backup-codes")
    Map<String, List<String>> backupCodes(@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request) {
        return Map.of("backupCodes", twoFactor.regenerate(require(principal).accountId(), request.getRemoteAddr()));
    }

    @PostMapping("/reauth")
    ReauthService.Token reauth(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody ReauthRequest r,
            HttpServletRequest request) {
        return reauth.confirm(require(principal), r.action(), r.password(), r.code(), request.getRemoteAddr());
    }
}
