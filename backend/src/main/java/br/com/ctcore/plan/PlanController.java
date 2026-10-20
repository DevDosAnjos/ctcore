package br.com.ctcore.plan;

import br.com.ctcore.identity.AccountPrincipal;
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

    @GetMapping("/summary")
    Plans.Summary summary(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) UUID unitId) {
        return plans.summary(who, unitId);
    }
}
