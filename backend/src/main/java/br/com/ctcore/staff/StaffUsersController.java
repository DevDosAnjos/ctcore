package br.com.ctcore.staff;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-ACE-06: o perfil gerido é o do vínculo da sessão; o corpo nunca traz perfil, organização nem permissão. */
@RestController
@RequestMapping("/api/v1/users")
class StaffUsersController {
    record InviteRequest(String email, String name, List<UUID> unitIds) {}

    record UnitsRequest(List<UUID> unitIds) {}

    private final StaffUsers users;

    StaffUsersController(StaffUsers users) {
        this.users = users;
    }

    @GetMapping
    List<StaffUsers.Row> list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String status) {
        return users.list(who, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StaffUsers.Row invite(@AuthenticationPrincipal AccountPrincipal who, @RequestBody InviteRequest body) {
        return users.invite(who, body.email(), body.name(), body.unitIds());
    }

    // O literal vence a variável {linkId}.
    @PostMapping("/coordinator-appointment")
    StaffUsers.Row appoint(@AuthenticationPrincipal AccountPrincipal who, @RequestBody UnitsRequest body, HttpServletRequest request) {
        return users.appointCoordinator(who, body.unitIds(), request.getRemoteAddr());
    }

    @PostMapping("/{linkId}/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resend(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID linkId) {
        users.resend(who, linkId);
    }

    @PutMapping("/{linkId}/units")
    StaffUsers.Row units(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID linkId, @RequestBody UnitsRequest body,
            HttpServletRequest request) {
        return users.changeUnits(who, linkId, body.unitIds(), request.getRemoteAddr());
    }

    @DeleteMapping("/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID linkId) {
        users.deactivate(who, linkId);
    }
}
