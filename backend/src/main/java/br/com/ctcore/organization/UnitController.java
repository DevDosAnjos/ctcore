package br.com.ctcore.organization;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.organization.UnitList.UnitSummary;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF-ORG-02: a organização é sempre a do vínculo ativo; a URL só traz o id da unidade, conferido contra ela. */
@RestController
@RequestMapping("/api/v1/units")
class UnitController {
    record ShortNameRequest(String shortName) {}

    private final UnitList units;
    private final UnitCreation creation;
    private final UnitSettings settings;
    private final UnitScope scope;
    private final UnitDeactivation deactivation;

    record DeactivationRequest(String confirmation) {}

    UnitController(UnitList units, UnitCreation creation, UnitSettings settings, UnitScope scope, UnitDeactivation deactivation) {
        this.deactivation = deactivation;
        this.scope = scope;
        this.units = units;
        this.creation = creation;
        this.settings = settings;
    }

    // RF-ORG-04: /mine antes de /{id}.
    @GetMapping("/mine")
    List<UnitSettings.UnitChoice> mine(@AuthenticationPrincipal AccountPrincipal who) {
        return settings.mine(who);
    }

    // RF-ORG-05: /reachable antes de /{id}.
    @GetMapping("/reachable")
    UnitScope.Result reachable(@AuthenticationPrincipal AccountPrincipal who) {
        return scope.reachable(who);
    }

    @GetMapping("/{id}")
    UnitSettings.UnitDetail read(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return settings.read(who, id);
    }

    @PutMapping("/{id}")
    UnitSettings.UnitDetail update(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, @RequestBody UnitUpdate body, HttpServletRequest request) {
        return settings.update(who, id, ifMatch, body, request.getRemoteAddr());
    }

    @GetMapping
    List<UnitSummary> list(@AuthenticationPrincipal AccountPrincipal who) {
        return units.list(who);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UnitCreation.Result create(@AuthenticationPrincipal AccountPrincipal who, @RequestBody NewUnit body, HttpServletRequest request) {
        return creation.create(who, body, request.getRemoteAddr());
    }

    @PatchMapping("/{id}/short-name")
    UnitSummary adjustShortName(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody ShortNameRequest body,
            HttpServletRequest request) {
        return units.adjustShortName(who, id, body.shortName(), request.getRemoteAddr());
    }

    @GetMapping("/{id}/deactivation-check")
    UnitDeactivation.Check deactivationCheck(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return deactivation.check(who, id);
    }

    @PostMapping("/{id}/deactivation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody DeactivationRequest body,
            @RequestHeader(value = "X-Reauth-Token", required = false) String token, HttpServletRequest request) {
        deactivation.deactivate(who, id, body.confirmation(), token, request.getRemoteAddr());
    }
}
