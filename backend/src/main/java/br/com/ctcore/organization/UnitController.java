package br.com.ctcore.organization;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.organization.UnitList.UnitSummary;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF-ORG-02: a organização é sempre a do vínculo ativo; a URL só traz o id da unidade, conferido contra ela. */
@RestController
@RequestMapping("/api/v1/units")
class UnitController {
    record ShortNameRequest(String shortName) {}

    private final UnitList units;

    UnitController(UnitList units) {
        this.units = units;
    }

    @GetMapping
    List<UnitSummary> list(@AuthenticationPrincipal AccountPrincipal who) {
        return units.list(who);
    }

    @PatchMapping("/{id}/short-name")
    UnitSummary adjustShortName(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody ShortNameRequest body,
            HttpServletRequest request) {
        return units.adjustShortName(who, id, body.shortName(), request.getRemoteAddr());
    }
}
