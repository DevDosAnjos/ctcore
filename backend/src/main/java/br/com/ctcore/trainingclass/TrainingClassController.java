package br.com.ctcore.trainingclass;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.identity.AccountPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-TUR-01: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/classes")
class TrainingClassController {
    private final TrainingClasses classes;

    TrainingClassController(TrainingClasses classes) {
        this.classes = classes;
    }

    @GetMapping
    PageResponse<TrainingClasses.Row> list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID modalityId, @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) UUID ageRangeId, @RequestParam(required = false) Integer weekday,
            @RequestParam(required = false) UUID unitId, @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        String field = "name".equals(sort) ? "name,asc" : sort;
        return classes.list(who, new TrainingClasses.Filter(q, modalityId, teacherId, ageRangeId, weekday, unitId, status),
                PageRequest.of(page, size, field, TrainingClasses.SORT));
    }
}
