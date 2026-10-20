package br.com.ctcore.permission;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionTableTest {
    private static List<String[]> csv() throws IOException {
        try (var in = PermissionTableTest.class.getResourceAsStream("/permission-table.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().skip(1).map(l -> l.split(",")).toList();
        }
    }

    private static Grant parse(String cell) {
        String[] part = cell.split("#")[0].split("/");
        return part.length == 1 ? Grant.NONE : new Grant(Access.valueOf(part[0]), Scope.valueOf(part[1]));
    }

    @Test
    @DisplayName("CA1 / RNF-05: as 29 linhas x 3 perfis do código batem com a cópia literal da especificação")
    void everyCellMatchesTheSpecification() throws IOException {
        var rows = csv();
        assertThat(rows).hasSize(29).hasSize(Permission.values().length);
        for (String[] row : rows) {
            Permission p = Permission.valueOf(row[0]);
            assertThat(PermissionTable.grant(Role.COORDINATOR, p)).as(row[0] + " COORDINATOR").isEqualTo(parse(row[1]));
            assertThat(PermissionTable.grant(Role.ADMINISTRATOR, p)).as(row[0] + " ADMINISTRATOR").isEqualTo(parse(row[2]));
            assertThat(PermissionTable.grant(Role.TEACHER, p)).as(row[0] + " TEACHER").isEqualTo(parse(row[3]));
            int flag = row[3].contains("#") ? Integer.parseInt(row[3].split("#")[1]) : -1;
            assertThat(PermissionTable.teacherFlag(p)).as(row[0] + " flag").isEqualTo(flag);
        }
    }

    @Test
    @DisplayName("Portal e sem papel não têm nenhuma permissão da gestão")
    void portalHasNothing() {
        for (Role role : new Role[] {Role.STUDENT, Role.GUARDIAN, null}) {
            for (Permission p : Permission.values()) {
                assertThat(PermissionTable.grant(role, p)).isEqualTo(Grant.NONE);
            }
        }
    }
}
