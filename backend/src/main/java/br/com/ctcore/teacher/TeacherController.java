package br.com.ctcore.teacher;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.identity.AccountPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-PRO-01: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/teachers")
class TeacherController {
    private final Teachers teachers;

    TeacherController(Teachers teachers) {
        this.teachers = teachers;
    }

    @GetMapping
    PageResponse<Teachers.Row> list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID modalityId, @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort) {
        String field = "name".equals(sort) ? "name,asc" : sort;
        return teachers.list(who, q, modalityId, unitId, status, PageRequest.of(page, size, field, Teachers.SORT));
    }
}
