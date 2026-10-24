package br.com.ctcore.mail;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-COM-03: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/organization/texts")
class TextModelController {
    record Body(String subject, String body) {}

    private final TextModels texts;

    TextModelController(TextModels texts) {
        this.texts = texts;
    }

    @GetMapping
    TextModels.View view(@AuthenticationPrincipal AccountPrincipal who) {
        return texts.view(who);
    }

    @PutMapping("/{key}")
    TextModels.Item save(@AuthenticationPrincipal AccountPrincipal who, @PathVariable String key, @RequestBody Body body, HttpServletRequest request) {
        return texts.save(who, key, body.subject(), body.body(), request.getRemoteAddr());
    }

    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@AuthenticationPrincipal AccountPrincipal who, @PathVariable String key, HttpServletRequest request) {
        texts.reset(who, key, request.getRemoteAddr());
    }
}
