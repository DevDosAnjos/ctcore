package br.com.ctcore.student;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-MAT-04: lista de pedidos da gestão com Aprovar e Recusar (STUDENT_MANAGE e alcance de unidade). */
@RestController
@RequestMapping("/api/v1/portal-requests")
class PortalRequestAdminController {
    record DecisionBody(String message) {}

    private final PortalRequests requests;

    PortalRequestAdminController(PortalRequests requests) {
        this.requests = requests;
    }

    @GetMapping
    PortalRequests.RequestList list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String status, @RequestParam(required = false) String kind,
            @RequestParam(required = false) UUID unitId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return requests.list(who, status, kind, unitId, PageRequest.of(page, size, null, Set.of()));
    }

    @GetMapping("/{id}")
    PortalRequests.Request get(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return requests.get(who, id);
    }

    @PostMapping("/{id}/approve")
    PortalRequests.Request approve(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody(required = false) DecisionBody body,
            HttpServletRequest request) {
        return requests.approve(who, id, body == null ? null : body.message(), request.getRemoteAddr());
    }

    @PostMapping("/{id}/refuse")
    PortalRequests.Request refuse(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody(required = false) DecisionBody body,
            HttpServletRequest request) {
        return requests.refuse(who, id, body == null ? null : body.message(), request.getRemoteAddr());
    }
}
