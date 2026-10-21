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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import br.com.ctcore.api.error.ApiValidationException;
import org.springframework.http.MediaType;
import java.io.IOException;
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
    private final StudentEdit edit;
    private final Enrollments enrollments;
    private final StudentSimple simple;

    record FinancialBody(Boolean financial) {}

    record ReasonBody(String reason) {}

    StudentController(Students students, StudentRegistration registration, StudentGuardians guardians, StudentRecords records,
            StudentEdit edit, Enrollments enrollments, StudentSimple simple) {
        this.simple = simple;
        this.enrollments = enrollments;
        this.edit = edit;
        this.records = records;
        this.guardians = guardians;
        this.students = students;
        this.registration = registration;
    }

    @GetMapping
    Students.StudentList list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID unitId, @RequestParam(required = false) UUID ageRangeId, @RequestParam(required = false) String seal,
            @RequestParam(required = false) UUID modalityId, @RequestParam(required = false) UUID classId, @RequestParam(required = false) UUID levelId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort) {
        String field = "name".equals(sort) ? "name,asc" : sort;
        return students.list(who, new Students.Filter(q, unitId, ageRangeId, seal, modalityId, classId, levelId, status),
                PageRequest.of(page, size, field, Set.of("name")));
    }

    /** R22: lista simples de uma turma ou aula, para o Professor e para a gestão. */
    @GetMapping("/simple")
    java.util.List<StudentSimple.Item> simple(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID lessonId) {
        return simple.list(who, classId, lessonId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Students.Detail create(@AuthenticationPrincipal AccountPrincipal who, @RequestBody StudentRegistration.Input body, HttpServletRequest request) {
        return registration.create(who, body, request.getRemoteAddr());
    }

    @GetMapping("/birthdays")
    java.util.List<Students.Birthday> birthdays(@AuthenticationPrincipal AccountPrincipal who) {
        return records.birthdays(who);
    }

    @GetMapping("/lookup")
    Students.Lookup lookup(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String cpf, @RequestParam(required = false) String email) {
        return students.lookup(who, cpf, email);
    }

    @GetMapping("/{id}")
    Students.Detail get(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return students.get(who, id);
    }


    @PutMapping("/{id}")
    Students.Detail update(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody StudentEdit.Update body,
            HttpServletRequest request) {
        return edit.update(who, id, body, request.getRemoteAddr());
    }

    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Students.Detail replacePhoto(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id,
            @RequestPart(value = "file", required = false) MultipartFile file, HttpServletRequest request) throws IOException {
        if (file == null) throw new ApiValidationException("file", "obrigatório");
        return edit.replacePhoto(who, id, file.getBytes(), request.getRemoteAddr());
    }

    @DeleteMapping("/{id}/photo")
    Students.Detail removePhoto(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, HttpServletRequest request) {
        return edit.removePhoto(who, id, request.getRemoteAddr());
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

    @PostMapping("/{id}/modalities")
    @ResponseStatus(HttpStatus.CREATED)
    Enrollments.Enrollment addModalities(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody Enrollments.AddInput body,
            HttpServletRequest request) {
        return enrollments.add(who, id, body, request.getRemoteAddr());
    }

    @PutMapping("/{id}/modalities/{modalityRowId}/class")
    Enrollments.Enrollment changeClass(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @PathVariable("modalityRowId") UUID rowId,
            @RequestBody Enrollments.ClassInput body, HttpServletRequest request) {
        return enrollments.changeClass(who, id, rowId, body, request.getRemoteAddr());
    }

    @PutMapping("/{id}/modalities/{modalityRowId}/plan")
    Enrollments.Enrollment changePlan(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @PathVariable("modalityRowId") UUID rowId,
            @RequestBody Enrollments.PlanInput body, HttpServletRequest request) {
        return enrollments.changePlan(who, id, rowId, body, request.getRemoteAddr());
    }

    @PostMapping("/{id}/modalities/{modalityRowId}/transfer")
    Enrollments.Enrollment transfer(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @PathVariable("modalityRowId") UUID rowId,
            @RequestBody Enrollments.TransferInput body, HttpServletRequest request) {
        return enrollments.transfer(who, id, rowId, body, request.getRemoteAddr());
    }

    @GetMapping("/{id}/enrollment")
    Enrollments.Enrollment enrollment(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return enrollments.get(who, id);
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
