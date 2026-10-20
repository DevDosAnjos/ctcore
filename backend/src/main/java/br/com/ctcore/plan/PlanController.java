package br.com.ctcore.plan;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-PLA-01: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/plans")
class PlanController {
    private final Plans plans;
    private final PlanWrite write;

    PlanController(Plans plans, PlanWrite write) {
        this.plans = plans;
        this.write = write;
    }

    @GetMapping
    PageResponse<Plans.Row> list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID modalityId, @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return plans.list(who, new Plans.Filter(q, modalityId, unitId, status), PageRequest.of(page, size, null, Set.of()));
    }

    @GetMapping("/summary")
    Plans.Summary summary(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) UUID unitId) {
        return plans.summary(who, unitId);
    }

    @GetMapping("/options")
    PlanWrite.Options options(@AuthenticationPrincipal AccountPrincipal who) {
        return write.options(who);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Plans.Row create(@AuthenticationPrincipal AccountPrincipal who, @RequestBody PlanWrite.Input body, HttpServletRequest request) {
        return write.create(who, body, request.getRemoteAddr());
    }

    @PutMapping("/{id}")
    Plans.Row update(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody PlanWrite.Input body, HttpServletRequest request) {
        return write.update(who, id, body, request.getRemoteAddr());
    }

    @GetMapping("/{id}/value-impact")
    PlanWrite.ValueImpact impact(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestParam(required = false) String valueCents) {
        return write.impact(who, id, valueCents);
    }

    @PostMapping("/{id}/deactivate")
    Plans.Row deactivate(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, HttpServletRequest request) {
        return write.setActive(who, id, false, request.getRemoteAddr());
    }

    @PostMapping("/{id}/activate")
    Plans.Row activate(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, HttpServletRequest request) {
        return write.setActive(who, id, true, request.getRemoteAddr());
    }
}
