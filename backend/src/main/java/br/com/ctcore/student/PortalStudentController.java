package br.com.ctcore.student;

import br.com.ctcore.identity.AccountPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Portal do responsável e do aluno (RN-RSP-03): só leitura dos próprios cartões. */
@RestController
@RequestMapping("/api/v1/portal")
class PortalStudentController {
    private final PortalStudents portal;

    PortalStudentController(PortalStudents portal) {
        this.portal = portal;
    }

    @GetMapping("/students")
    List<PortalStudents.Card> students(@AuthenticationPrincipal AccountPrincipal who) {
        return portal.cards(who);
    }
}
