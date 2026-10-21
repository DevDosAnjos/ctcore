package br.com.ctcore.terms;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-MAT-05: a organização é sempre a do vínculo ativo da sessão; não há rota de alterar nem apagar versão. */
@RestController
@RequestMapping("/api/v1/organization/terms/{kind}")
class TermsController {
    record Body(String body, Boolean reaccept) {}

    private final Terms terms;

    TermsController(Terms terms) {
        this.terms = terms;
    }

    @GetMapping
    Terms.View view(@AuthenticationPrincipal AccountPrincipal who, @PathVariable String kind) {
        return terms.view(who, kind);
    }

    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    Terms.Version publish(@AuthenticationPrincipal AccountPrincipal who, @PathVariable String kind, @RequestBody Body body, HttpServletRequest request) {
        return terms.publish(who, kind, body.body(), body.reaccept(), request.getRemoteAddr());
    }
}
