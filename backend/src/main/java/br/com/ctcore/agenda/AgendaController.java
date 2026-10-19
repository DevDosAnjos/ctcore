package br.com.ctcore.agenda;

import br.com.ctcore.identity.AccountPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-AGE-01: só leitura; a organização é sempre a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/agenda")
class AgendaController {
    private final Agenda agenda;

    AgendaController(Agenda agenda) {
        this.agenda = agenda;
    }

    @GetMapping
    Agenda.View get(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) UUID teacherId, @RequestParam(required = false) UUID modalityId) {
        return agenda.view(who, new Agenda.Filter(from, to, unitId, teacherId, modalityId));
    }
}
