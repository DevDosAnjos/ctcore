package br.com.ctcore.student;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-MAT-04, RN-POR-01: pedidos do portal (aluno adulto e responsável financeiro), depois do portão do termo. */
@RestController
@RequestMapping("/api/v1/portal/students/{id}")
class PortalRequestController {
    private final PortalRequests requests;

    PortalRequestController(PortalRequests requests) {
        this.requests = requests;
    }

    @GetMapping("/request-options")
    PortalRequests.Options options(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return requests.options(who, id);
    }

    @GetMapping("/requests")
    List<PortalRequests.Request> list(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return requests.list(who, id);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    PortalRequests.Request create(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody PortalRequests.Input body, HttpServletRequest request) {
        return requests.create(who, id, body, request.getRemoteAddr());
    }
}
