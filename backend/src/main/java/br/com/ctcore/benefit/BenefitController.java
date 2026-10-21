package br.com.ctcore.benefit;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-BEN-01: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/benefits")
class BenefitController {
    private final Benefits benefits;
    private final BenefitWrite write;

    BenefitController(Benefits benefits, BenefitWrite write) {
        this.write = write;
        this.benefits = benefits;
    }

    @GetMapping
    PageResponse<Benefits.Row> list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String q,
            @RequestParam(required = false) String type, @RequestParam(required = false) UUID modalityId, @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Boolean high, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return benefits.list(who, new Benefits.Filter(q, type, modalityId, unitId, status, high), PageRequest.of(page, size, null, Set.of()));
    }

    @PostMapping("/preview")
    BenefitWrite.Preview preview(@AuthenticationPrincipal AccountPrincipal who, @RequestBody BenefitWrite.PreviewInput body) {
        return write.preview(who, body);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Benefits.Row grant(@AuthenticationPrincipal AccountPrincipal who, @RequestBody BenefitWrite.Input body, HttpServletRequest request) {
        return write.grant(who, body, request.getRemoteAddr());
    }

    @PostMapping("/{id}/end")
    Benefits.Row end(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody(required = false) BenefitWrite.EndInput body,
            HttpServletRequest request) {
        return write.end(who, id, body, request.getRemoteAddr());
    }
}
