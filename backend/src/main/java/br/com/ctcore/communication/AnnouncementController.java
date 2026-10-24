package br.com.ctcore.communication;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-COM-01: lista e cancelamento de comunicados. */
@RestController
class AnnouncementController {
    private final Announcements announcements;

    AnnouncementController(Announcements announcements) {
        this.announcements = announcements;
    }

    @GetMapping("/api/v1/announcements")
    Announcements.AnnouncementList list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID classId, @RequestParam(required = false) String type, @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort) {
        return announcements.list(who, new Announcements.Filter(from, to, classId, type, unitId), PageRequest.of(page, size, sort, Announcements.SORT_FIELDS));
    }

    @DeleteMapping("/api/v1/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, HttpServletRequest request) {
        announcements.cancel(who, id, request.getRemoteAddr());
    }
}
