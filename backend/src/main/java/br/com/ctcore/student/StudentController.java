package br.com.ctcore.student;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-ALU-01: a organização é sempre a do vínculo ativo da sessão; não há rota de exclusão (RN-ALU-08). */
@RestController
@RequestMapping("/api/v1/students")
class StudentController {
    private final Students students;
    private final StudentRegistration registration;
    private final StudentGuardians guardians;
    private final StudentRecords records;

    record FinancialBody(Boolean financial) {}

    record ReasonBody(String reason) {}

    StudentController(Students students, StudentRegistration registration, StudentGuardians guardians, StudentRecords records) {
        this.records = records;
        this.guardians = guardians;
        this.students = students;
        this.registration = registration;
    }

    @GetMapping
    PageResponse<Students.Row> list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID unitId, @RequestParam(required = false) UUID ageRangeId, @RequestParam(required = false) String seal,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort) {
        String field = "name".equals(sort) ? "name,asc" : sort;
        return students.list(who, q, unitId, ageRangeId, seal, PageRequest.of(page, size, field, Set.of("name")));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Students.Detail create(@AuthenticationPrincipal AccountPrincipal who, @RequestBody StudentRegistration.Input body, HttpServletRequest request) {
        return registration.create(who, body, request.getRemoteAddr());
    }

    @GetMapping("/lookup")
    Students.Ref lookup(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String cpf) {
        return students.lookup(who, cpf);
    }

    @GetMapping("/{id}")
    Students.Detail get(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return students.get(who, id);
    }

    @GetMapping("/{id}/record")
    StudentRecords.RecordView record(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return records.record(who, id);
    }

    @GetMapping("/{id}/history")
    PageResponse<StudentRecords.HistoryEntry> history(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return records.history(who, id, PageRequest.of(page, size, null, Set.of()));
    }

    @PostMapping("/{id}/guardians")
    Students.Detail link(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody StudentGuardians.Input body,
            HttpServletRequest request) {
        return guardians.link(who, id, body, request.getRemoteAddr());
    }

    @PutMapping("/{id}/guardians/{guardianId}")
    Students.Detail financial(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @PathVariable UUID guardianId,
            @RequestBody FinancialBody body, HttpServletRequest request) {
        return guardians.setFinancial(who, id, guardianId, body == null ? null : body.financial(), request.getRemoteAddr());
    }

    @PostMapping("/{id}/guardians/{guardianId}/unlink")
    Students.Detail unlink(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @PathVariable UUID guardianId,
            @RequestBody ReasonBody body, HttpServletRequest request) {
        return guardians.unlink(who, id, guardianId, body == null ? null : body.reason(), request.getRemoteAddr());
    }

    @PostMapping("/{id}/guardians/{guardianId}/invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void invite(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @PathVariable UUID guardianId, HttpServletRequest request) {
        guardians.resendInvite(who, id, guardianId, request.getRemoteAddr());
    }
}
