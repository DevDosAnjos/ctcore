package br.com.ctcore.teacher;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import br.com.ctcore.api.error.ApiValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-PRO-01 a RF-PRO-03: a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/teachers")
class TeacherController {
    record AccessRequest(List<UUID> unitIds) {}

    private final Teachers teachers;
    private final TeacherAccess access;
    private final TeacherWrite write;
    private final TeacherDeactivation deactivation;

    TeacherController(Teachers teachers, TeacherAccess access, TeacherWrite write, TeacherDeactivation deactivation) {
        this.deactivation = deactivation;
        this.write = write;
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

    @GetMapping("/options")
    TeacherWrite.Options options(@AuthenticationPrincipal AccountPrincipal who) {
        return write.options(who);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Teachers.Detail create(@AuthenticationPrincipal AccountPrincipal who, @RequestBody TeacherWrite.Input body, HttpServletRequest request) {
        return write.create(who, body, request.getRemoteAddr());
    }

    @PutMapping("/{id}")
    Teachers.Detail update(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody TeacherWrite.Input body,
            HttpServletRequest request) {
        return write.update(who, id, body, request.getRemoteAddr());
    }

    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Teachers.Detail replacePhoto(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id,
            @RequestPart(value = "file", required = false) MultipartFile file, HttpServletRequest request) throws IOException {
        if (file == null) throw new ApiValidationException("file", "obrigatório");
        return write.replacePhoto(who, id, file.getBytes(), request.getRemoteAddr());
    }

    @DeleteMapping("/{id}/photo")
    Teachers.Detail removePhoto(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, HttpServletRequest request) {
        return write.removePhoto(who, id, request.getRemoteAddr());
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

    @GetMapping("/{id}/deactivation")
    TeacherDeactivation.Preview deactivationPreview(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return deactivation.preview(who, id);
    }

    @PostMapping("/{id}/deactivate")
    Teachers.Detail deactivate(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, HttpServletRequest request) {
        return deactivation.deactivate(who, id, request.getRemoteAddr());
    }
}
