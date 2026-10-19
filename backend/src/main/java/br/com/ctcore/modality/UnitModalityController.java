package br.com.ctcore.modality;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF-MOD-03: a organização é a da sessão; a unidade do caminho é conferida contra ela. */
@RestController
@RequestMapping("/api/v1/units/{unitId}/modalities")
class UnitModalityController {
    record OfferRequest(Boolean offered) {}

    private final UnitModalities modalities;

    UnitModalityController(UnitModalities modalities) {
        this.modalities = modalities;
    }

    @GetMapping
    UnitModalities.Listing list(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID unitId) {
        return modalities.list(who, unitId);
    }

    @PutMapping("/{modalityId}")
    UnitModalities.Item offer(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID unitId, @PathVariable UUID modalityId,
            @RequestBody OfferRequest body, HttpServletRequest request) {
        return modalities.setOffered(who, unitId, modalityId, body.offered(), request.getRemoteAddr());
    }
}
