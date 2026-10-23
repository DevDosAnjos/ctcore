package br.com.ctcore.billing;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-COB-03, RN-COB-06: a organização vem da sessão; a unidade da modalidade é conferida na permissão. */
@RestController
class ChargeController {
    private final MonthlyGeneration generation;
    private final Charges charges;
    private final ChargeActions actions;

    private final OneOffCharges oneOff;

    ChargeController(MonthlyGeneration generation, Charges charges, ChargeActions actions, OneOffCharges oneOff) {
        this.oneOff = oneOff;
        this.actions = actions;
        this.generation = generation;
        this.charges = charges;
    }

    /** RF-COB-01: a lista de cobranças; só leitura. */
    @GetMapping("/api/v1/charges")
    Charges.ChargeList list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String q, @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String quick, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String status, @RequestParam(required = false) String type, @RequestParam(required = false) UUID modalityId,
            @RequestParam(required = false) UUID classId, @RequestParam(required = false) UUID studentId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return charges.list(who, new Charges.Filter(q, unitId, quick, from, to, status, type, modalityId, classId, studentId),
                PageRequest.of(page, size, sort, Charges.SORT_FIELDS));
    }

    /** RF-COB-02: o painel da cobrança. */
    @GetMapping("/api/v1/charges/{id}")
    ChargeActions.Detail detail(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return actions.detail(who, id);
    }

    public record CancelInput(String reason) {}

    @PostMapping("/api/v1/charges/{id}/cancel")
    ChargeActions.Detail cancel(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody CancelInput input, HttpServletRequest request) {
        return actions.cancel(who, id, input.reason(), request.getRemoteAddr());
    }

    @PostMapping("/api/v1/charges/renegotiate")
    @ResponseStatus(HttpStatus.CREATED)
    List<Charges.Row> renegotiate(@AuthenticationPrincipal AccountPrincipal who, @RequestBody ChargeActions.RenegotiateInput input, HttpServletRequest request) {
        return actions.renegotiate(who, input, request.getRemoteAddr());
    }

    @GetMapping("/api/v1/billing/generation-preview")
    MonthlyGeneration.Preview preview(@AuthenticationPrincipal AccountPrincipal who, @RequestParam UUID unitId, @RequestParam(required = false) Integer days) {
        return generation.preview(who, unitId, days);
    }

    @PostMapping("/api/v1/students/{id}/modalities/{modalityRowId}/charges/advance")
    @ResponseStatus(HttpStatus.CREATED)
    List<MonthlyGeneration.Created> advance(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @PathVariable UUID modalityRowId,
            @RequestBody MonthlyGeneration.Advance input, HttpServletRequest request) {
        return generation.advance(who, id, modalityRowId, input, request.getRemoteAddr());
    }

    /** RN-COB-11: cobrança avulsa; 201 ao criar, 200 na repetição do mesmo requestId. */
    @PostMapping("/api/v1/students/{id}/charges")
    ResponseEntity<Charges.Row> createOneOff(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody OneOffCharges.Input input,
            HttpServletRequest request) {
        var result = oneOff.create(who, id, input, request.getRemoteAddr());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.row());
    }

    @PatchMapping("/api/v1/students/{id}/modalities/{modalityRowId}/next-charge")
    MonthlyGeneration.Adjusted changeNextDue(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @PathVariable UUID modalityRowId,
            @RequestBody MonthlyGeneration.ChangeDue input, HttpServletRequest request) {
        return generation.changeNextDue(who, id, modalityRowId, input, request.getRemoteAddr());
    }

    @PostMapping("/api/v1/students/{id}/modalities/{modalityRowId}/next-charge/cancel")
    MonthlyGeneration.Canceled cancelNext(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @PathVariable UUID modalityRowId,
            @RequestBody MonthlyGeneration.CancelNext input, HttpServletRequest request) {
        return generation.cancelNext(who, id, modalityRowId, input, actions, request.getRemoteAddr());
    }
}
