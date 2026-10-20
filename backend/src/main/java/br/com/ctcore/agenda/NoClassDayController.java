package br.com.ctcore.agenda;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-AGE-05: dias sem aula. A organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/no-class-days")
class NoClassDayController {
    private final NoClassDays days;
    private final NoClassDayWrite write;

    NoClassDayController(NoClassDays days, NoClassDayWrite write) {
        this.days = days;
        this.write = write;
    }

    @GetMapping
    NoClassDays.View get(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) UUID unitId) {
        return days.view(who, unitId);
    }

    @GetMapping("/preview")
    NoClassDays.Preview preview(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) List<UUID> unitIds,
            @RequestParam(required = false) String startsOn, @RequestParam(required = false) String endsOn,
            @RequestParam(required = false) List<UUID> classIds) {
        return days.preview(who, new NoClassDays.Input(unitIds, startsOn, endsOn, null, null, classIds, null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    List<NoClassDays.Item> create(@AuthenticationPrincipal AccountPrincipal who, @RequestBody NoClassDays.Input body, HttpServletRequest request) {
        return write.create(who, body, request.getRemoteAddr());
    }

    @PutMapping("/{id}")
    NoClassDays.Item update(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody NoClassDays.Input body,
            HttpServletRequest request) {
        return write.update(who, id, body, request.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean notify,
            HttpServletRequest request) {
        write.delete(who, id, notify, request.getRemoteAddr());
    }
}
