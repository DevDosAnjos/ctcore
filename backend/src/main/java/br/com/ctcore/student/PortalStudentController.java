package br.com.ctcore.student;

import br.com.ctcore.identity.AccountPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Portal do responsável e do aluno (RN-RSP-03): só leitura dos próprios cartões, depois do aceite do termo (RN-POR-03). */
@RestController
@RequestMapping("/api/v1/portal")
class PortalStudentController {
    private final PortalStudents portal;
    private final PortalTerms terms;
    private final PortalShell shell;
    private final PortalHome home;

    private final PortalCard card;
    private final PortalSchedule schedule;
    private final PortalFinance finance;

    PortalStudentController(PortalStudents portal, PortalTerms terms, PortalShell shell, PortalHome home, PortalCard card, PortalSchedule schedule,
            PortalFinance finance) {
        this.finance = finance;
        this.schedule = schedule;
        this.card = card;
        this.home = home;
        this.terms = terms;
        this.shell = shell;
        this.portal = portal;
    }

    @GetMapping("/students")
    List<PortalStudents.Card> students(@AuthenticationPrincipal AccountPrincipal who) {
        terms.requireAccepted(who);
        return portal.cards(who);
    }

    @GetMapping("/shell")
    PortalShell.View shell(@AuthenticationPrincipal AccountPrincipal who) {
        terms.requireAccepted(who);
        return shell.view(who);
    }

    @GetMapping("/home")
    PortalHome.View home(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) UUID studentId) {
        terms.requireAccepted(who);
        return home.view(who, studentId);
    }

    @GetMapping("/card")
    PortalCard.View card(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) UUID studentId) {
        terms.requireAccepted(who);
        return card.view(who, studentId);
    }

    @GetMapping("/finance")
    PortalFinance.View finance(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) PortalFinance.Scope scope, @RequestParam(required = false) Integer year) {
        terms.requireAccepted(who);
        return finance.view(who, studentId, scope, year);
    }

    @GetMapping("/schedule")
    PortalSchedule.View schedule(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week, @RequestParam(required = false) PortalSchedule.Scope scope) {
        terms.requireAccepted(who);
        return schedule.view(who, studentId, week, scope);
    }
}
