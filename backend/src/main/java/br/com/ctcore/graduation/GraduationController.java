package br.com.ctcore.graduation;

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

/** RF-MOD-02: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/modalities/{id}/graduation")
class GraduationController {
    private final Graduation graduation;

    GraduationController(Graduation graduation) {
        this.graduation = graduation;
    }

    @GetMapping
    Graduation.View get(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return graduation.get(who, id);
    }

    @PutMapping
    Graduation.View save(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody Graduation.Update body, HttpServletRequest request) {
        return graduation.save(who, id, body, request.getRemoteAddr());
    }
}
