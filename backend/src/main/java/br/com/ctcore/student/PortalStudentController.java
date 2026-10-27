package br.com.ctcore.student;

import br.com.ctcore.identity.AccountPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Portal do responsável e do aluno (RN-RSP-03): só leitura dos próprios cartões, depois do aceite do termo (RN-POR-03). */
@RestController
@RequestMapping("/api/v1/portal")
class PortalStudentController {
    private final PortalStudents portal;
    private final PortalTerms terms;
    private final PortalShell shell;

    PortalStudentController(PortalStudents portal, PortalTerms terms, PortalShell shell) {
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
}
