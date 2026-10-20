package br.com.ctcore.plan;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-PLA-01: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/plans")
class PlanController {
    private final Plans plans;

    PlanController(Plans plans) {
        this.plans = plans;
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
}
