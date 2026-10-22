package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CA7: o corte de campos da ficha do Professor (o acesso real só existe com a matrícula, #63). */
class StudentRecordsTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private String teacherJson(boolean contacts) throws Exception {
        var row = new Students.Row(UUID.randomUUID(), "Aluno", null, LocalDate.of(2000, 1, 1), 25, false, null,
                new Students.UnitRef(UUID.randomUUID(), "Unidade"), null, List.of(StudentSeal.EMAIL_PROBLEM), "ACTIVE", "CURRENT", List.of());
        var detail = new Students.Detail(row.id(), UUID.randomUUID(), "Aluno", LocalDate.of(2000, 1, 1), false, "01001000", "Rua Segredo", "1", null,
                "Centro", "Cidade", "SP", "52998224725", "aluno@ficticia.example", "11987654321", null, null, "Emergencia Nome", "Mae", "11911112222", null, false,
                List.of(new Students.GuardianRow(UUID.randomUUID(), "Resp", "resp@ficticia.example", "11933334444", true, "ACTIVE", false)));
        return json.writeValueAsString(StudentRecords.assemble(row, detail, true, contacts));
    }

    @Test
    @DisplayName("Professor: sem CPF, endereço, nascimento, selos, financial nem access; contatos só com a permissão")
    void teacherCut() throws Exception {
        String without = teacherJson(false);
        for (String gone : new String[] {"cpf", "address", "birthDate", "seals", "financial", "access", "52998224725", "Rua Segredo",
                "11987654321", "aluno@ficticia", "Emergencia", "11911112222", "resp@ficticia", "11933334444", "contact", "emergency", "financialStatus"}) {
            assertThat(without).as(gone).doesNotContain(gone);
        }
        assertThat(without).contains("\"age\":25").contains("\"name\":\"Aluno\"");
        String with = teacherJson(true);
        assertThat(with).contains("11987654321", "aluno@ficticia.example", "Emergencia Nome", "11911112222", "resp@ficticia.example", "11933334444");
        for (String gone : new String[] {"cpf", "address", "birthDate", "seals", "financial", "access", "52998224725", "Rua Segredo"}) {
            assertThat(with).as(gone).doesNotContain(gone);
        }
    }
}
