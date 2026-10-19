package br.com.ctcore.agerange;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-MOD-04: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/age-ranges")
class AgeRangeController {
    record Body(String name, Integer minAge, Integer maxAge) {}

    private final AgeRanges ageRanges;

    AgeRangeController(AgeRanges ageRanges) {
        this.ageRanges = ageRanges;
    }

    @GetMapping
    AgeRanges.Listing list(@AuthenticationPrincipal AccountPrincipal who) {
        return ageRanges.list(who);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AgeRanges.Item create(@AuthenticationPrincipal AccountPrincipal who, @RequestBody Body body, HttpServletRequest request) {
        return ageRanges.create(who, body.name(), body.minAge(), body.maxAge(), request.getRemoteAddr());
    }

    @PutMapping("/{id}")
    AgeRanges.Item update(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody Body body, HttpServletRequest request) {
        return ageRanges.update(who, id, body.name(), body.minAge(), body.maxAge(), request.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, HttpServletRequest request) {
        ageRanges.delete(who, id, request.getRemoteAddr());
    }
}
