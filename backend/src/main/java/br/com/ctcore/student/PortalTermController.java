package br.com.ctcore.student;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RN-MAT-11 a RN-MAT-13, RN-POR-03: aceite do termo e uso de imagem no portal (funcionam com o termo pendente) e confirmação de modalidade (com o termo em dia). */
@RestController
@RequestMapping("/api/v1/portal")
class PortalTermController {
    record AcceptBody(Integer version, Boolean imageUse) {}

    record ImageUseBody(Boolean granted, Integer version) {}

    private final PortalTerms terms;
    private final ModalityConfirmations confirmations;

    PortalTermController(PortalTerms terms, ModalityConfirmations confirmations) {
        this.terms = terms;
        this.confirmations = confirmations;
    }

    @GetMapping("/term")
    List<PortalTerms.Pending> pending(@AuthenticationPrincipal AccountPrincipal who) {
        return terms.pending(who);
    }

    @PostMapping("/students/{id}/term-acceptance")
    ResponseEntity<PortalTerms.Acceptance> accept(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody AcceptBody body,
            HttpServletRequest request) {
        var outcome = terms.accept(who, id, body.version(), body.imageUse(), request.getRemoteAddr());
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(outcome.body());
    }

    @GetMapping("/students/{id}/image-use")
    PortalTerms.ImageUse imageUse(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return terms.imageUse(who, id);
    }

    @PutMapping("/students/{id}/image-use")
    PortalTerms.ImageUse setImageUse(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody ImageUseBody body,
            HttpServletRequest request) {
        return terms.setImageUse(who, id, body.granted(), body.version(), request.getRemoteAddr());
    }

    @GetMapping("/modality-confirmations")
    List<ModalityConfirmations.Item> modalityConfirmations(@AuthenticationPrincipal AccountPrincipal who) {
        return confirmations.pending(who);
    }

    @PostMapping("/modality-confirmations/{modalityRowId}")
    ResponseEntity<ModalityConfirmations.Confirmed> confirm(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID modalityRowId, HttpServletRequest request) {
        var outcome = confirmations.confirm(who, modalityRowId, request.getRemoteAddr());
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(outcome.body());
    }
}
