package br.com.ctcore.communication;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** RF-COM-01 e RF-COM-02: lista, cancelamento, destinos, prévia, criação e imagem de comunicados. */
@RestController
class AnnouncementController {
    private final Announcements announcements;
    private final AnnouncementTargets targets;
    private final AnnouncementWrite write;

    AnnouncementController(Announcements announcements, AnnouncementTargets targets, AnnouncementWrite write) {
        this.announcements = announcements;
        this.targets = targets;
        this.write = write;
    }

    @GetMapping("/api/v1/announcements")
    Announcements.AnnouncementList list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID classId, @RequestParam(required = false) String type, @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort) {
        return announcements.list(who, new Announcements.Filter(from, to, classId, type, unitId), PageRequest.of(page, size, sort, Announcements.SORT_FIELDS));
    }

    @GetMapping("/api/v1/announcements/targets")
    AnnouncementTargets.Targets targets(@AuthenticationPrincipal AccountPrincipal who) {
        return targets.targets(who);
    }

    @PostMapping("/api/v1/announcements/preview")
    AnnouncementWrite.Preview preview(@AuthenticationPrincipal AccountPrincipal who, @RequestBody AnnouncementWrite.Draft body) {
        return write.preview(who, body);
    }

    @PostMapping("/api/v1/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    AnnouncementWrite.Created create(@AuthenticationPrincipal AccountPrincipal who, @RequestBody AnnouncementWrite.Draft body, HttpServletRequest request) {
        return write.create(who, body, request.getRemoteAddr());
    }

    @PostMapping(value = "/api/v1/announcements/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AnnouncementWrite.Image image(@AuthenticationPrincipal AccountPrincipal who, @RequestPart(value = "file", required = false) MultipartFile file,
            HttpServletRequest request) throws IOException {
        if (file == null) throw new ApiValidationException("file", "obrigatório");
        return write.uploadImage(who, file.getBytes(), request.getRemoteAddr());
    }

    @DeleteMapping("/api/v1/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, HttpServletRequest request) {
        announcements.cancel(who, id, request.getRemoteAddr());
    }
}
