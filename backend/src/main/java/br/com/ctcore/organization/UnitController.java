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

    UnitController(UnitList units, UnitCreation creation, UnitSettings settings) {
        this.units = units;
        this.creation = creation;
        this.settings = settings;
    }

    // RF-ORG-04: /mine antes de /{id}.
    @GetMapping("/mine")
    List<UnitSettings.UnitChoice> mine(@AuthenticationPrincipal AccountPrincipal who) {
        return settings.mine(who);
    }

    @GetMapping("/{id}")
    UnitSettings.UnitDetail read(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return settings.read(who, id);
    }

    @PutMapping("/{id}")
    UnitSettings.UnitDetail update(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody UnitUpdate body,
            HttpServletRequest request) {
        return settings.update(who, id, body, request.getRemoteAddr());
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
}
