package br.com.ctcore.billing;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-COB-03, RN-COB-06: a organização vem da sessão; a unidade da modalidade é conferida na permissão. */
@RestController
class ChargeController {
    private final MonthlyGeneration generation;

    ChargeController(MonthlyGeneration generation) {
        this.generation = generation;
    }

    @GetMapping("/api/v1/billing/generation-preview")
    MonthlyGeneration.Preview preview(@AuthenticationPrincipal AccountPrincipal who, @RequestParam UUID unitId, @RequestParam(required = false) Integer days) {
        return generation.preview(who, unitId, days);
    }

    @PostMapping("/api/v1/students/{id}/modalities/{modalityRowId}/charges/advance")
    @ResponseStatus(HttpStatus.CREATED)
    List<MonthlyGeneration.Created> advance(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @PathVariable UUID modalityRowId,
            @RequestBody MonthlyGeneration.Advance input, HttpServletRequest request) {
        return generation.advance(who, id, modalityRowId, input, request.getRemoteAddr());
    }
}
