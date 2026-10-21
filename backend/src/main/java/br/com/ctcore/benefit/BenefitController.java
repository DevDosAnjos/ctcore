package br.com.ctcore.benefit;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.identity.AccountPrincipal;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-BEN-01: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/benefits")
class BenefitController {
    private final Benefits benefits;

    BenefitController(Benefits benefits) {
        this.benefits = benefits;
    }

    @GetMapping
    PageResponse<Benefits.Row> list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String q,
            @RequestParam(required = false) String type, @RequestParam(required = false) UUID modalityId, @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Boolean high, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return benefits.list(who, new Benefits.Filter(q, type, modalityId, unitId, status, high), PageRequest.of(page, size, null, Set.of()));
    }
}
