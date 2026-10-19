package br.com.ctcore.teacher;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-PRO-01 e RF-PRO-02: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/teachers")
class TeacherController {
    record AccessRequest(List<UUID> unitIds) {}

    private final Teachers teachers;
    private final TeacherAccess access;

    TeacherController(Teachers teachers, TeacherAccess access) {
        this.teachers = teachers;
        this.access = access;
    }

    @GetMapping
    PageResponse<Teachers.Row> list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID modalityId, @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort) {
        String field = "name".equals(sort) ? "name,asc" : sort;
        return teachers.list(who, q, modalityId, unitId, status, PageRequest.of(page, size, field, Teachers.SORT));
    }

    @GetMapping("/{id}")
    Teachers.Detail get(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return teachers.get(who, id);
    }

    @PostMapping("/{id}/access")
    Teachers.Detail grant(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id,
            @RequestBody(required = false) AccessRequest body, HttpServletRequest request) {
        return access.grant(who, id, body == null ? null : body.unitIds(), request.getRemoteAddr());
    }

    @PostMapping("/{id}/access/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resend(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, HttpServletRequest request) {
        access.resend(who, id, request.getRemoteAddr());
    }
}
