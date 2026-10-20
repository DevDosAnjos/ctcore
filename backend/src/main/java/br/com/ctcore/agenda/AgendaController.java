package br.com.ctcore.agenda;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
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

/** RF-AGE-01/02: leitura; RF-AGE-03: editar e cancelar. A organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/agenda")
class AgendaController {
    private final Agenda agenda;
    private final LessonWrite write;

    AgendaController(Agenda agenda, LessonWrite write) {
        this.agenda = agenda;
        this.write = write;
    }

    @GetMapping
    Agenda.View get(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) UUID teacherId, @RequestParam(required = false) UUID modalityId) {
        return agenda.view(who, new Agenda.Filter(from, to, unitId, teacherId, modalityId));
    }

    /** RF-AGE-02: painel da aula. */
    @GetMapping("/lessons/{id}")
    Agenda.LessonDetail lesson(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return agenda.lesson(who, id);
    }

    /** RF-AGE-04: aula avulsa. */
    @PostMapping("/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    Agenda.LessonDetail create(@AuthenticationPrincipal AccountPrincipal who, @RequestBody LessonWrite.CreateInput body, HttpServletRequest request) {
        return write.create(who, body, request.getRemoteAddr());
    }

    @PutMapping("/lessons/{id}")
    Agenda.LessonDetail update(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody LessonWrite.EditInput body,
            HttpServletRequest request) {
        return write.update(who, id, body, request.getRemoteAddr());
    }

    @PostMapping("/lessons/{id}/cancel")
    Agenda.LessonDetail cancel(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody LessonWrite.CancelInput body,
            HttpServletRequest request) {
        return write.cancel(who, id, body, request.getRemoteAddr());
    }
}
