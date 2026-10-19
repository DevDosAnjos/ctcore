package br.com.ctcore.trainingclass;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-TUR-01: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/classes")
class TrainingClassController {
    private final TrainingClasses classes;
    private final TrainingClassDeactivation deactivation;
    private final TrainingClassWrite write;

    TrainingClassController(TrainingClasses classes, TrainingClassDeactivation deactivation, TrainingClassWrite write) {
        this.write = write;
        this.deactivation = deactivation;
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

    @GetMapping("/options")
    TrainingClassWrite.Options options(@AuthenticationPrincipal AccountPrincipal who) {
        return write.options(who);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TrainingClasses.Detail create(@AuthenticationPrincipal AccountPrincipal who, @RequestBody TrainingClassWrite.Input body, HttpServletRequest request) {
        return write.create(who, body, request.getRemoteAddr());
    }

    @PutMapping("/{id}")
    TrainingClasses.Detail update(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody TrainingClassWrite.Input body,
            HttpServletRequest request) {
        return write.update(who, id, body, request.getRemoteAddr());
    }

    @GetMapping("/{id}")
    TrainingClasses.Detail get(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return classes.get(who, id);
    }

    @PostMapping("/{id}/deactivate")
    TrainingClasses.Detail deactivate(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, HttpServletRequest request) {
        return deactivation.deactivate(who, id, request.getRemoteAddr());
    }
}
