package br.com.ctcore.modality;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-MOD-01: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/modalities")
class ModalityController {
    record ActivationRequest(Boolean active) {}

    record NewRequest(String name, String note) {}

    private final Modalities modalities;

    ModalityController(Modalities modalities) {
        this.modalities = modalities;
    }

    @GetMapping
    List<Modalities.Item> list(@AuthenticationPrincipal AccountPrincipal who) {
        return modalities.list(who);
    }

    @PutMapping("/{id}/activation")
    Modalities.Item activation(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody ActivationRequest body,
            HttpServletRequest request) {
        return modalities.setActive(who, id, body.active(), request.getRemoteAddr());
    }

    @GetMapping("/requests")
    List<Modalities.Request> requests(@AuthenticationPrincipal AccountPrincipal who) {
        return modalities.requests(who);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    Modalities.Request request(@AuthenticationPrincipal AccountPrincipal who, @RequestBody NewRequest body, HttpServletRequest request) {
        return modalities.request(who, body.name(), body.note(), request.getRemoteAddr());
    }
}
