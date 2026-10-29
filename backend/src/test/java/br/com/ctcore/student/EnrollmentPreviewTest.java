package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.storage.FileKind;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-MAT-05 e RN-MAT-07 (R2, R3, R7): regras de data, taxa e tipo de comprovante, sem banco. */
class EnrollmentPreviewTest {
    @Test
    @DisplayName("CA4: a segunda cobrança é a primeira data com o dia de vencimento a pelo menos 15 dias da matrícula")
    void secondDueDate() {
        assertThat(EnrollmentConclusion.suggestSecond(LocalDate.of(2026, 10, 20), 10)).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(EnrollmentConclusion.suggestSecond(LocalDate.of(2026, 10, 25), 10)).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(EnrollmentConclusion.suggestSecond(LocalDate.of(2026, 10, 28), 10)).isEqualTo(LocalDate.of(2026, 12, 10));
        // exatamente 15 dias depois já vale
        assertThat(EnrollmentConclusion.suggestSecond(LocalDate.of(2026, 10, 26), 10)).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(EnrollmentConclusion.suggestSecond(LocalDate.of(2026, 10, 25), 9)).isEqualTo(LocalDate.of(2026, 11, 9));
        // vira o ano
        assertThat(EnrollmentConclusion.suggestSecond(LocalDate.of(2026, 12, 20), 5)).isEqualTo(LocalDate.of(2027, 1, 5));
        // dia 28 existe em todo mês
        assertThat(EnrollmentConclusion.suggestSecond(LocalDate.of(2027, 1, 20), 28)).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    @DisplayName("CA2: a taxa entra se a unidade cobra e o aluno nunca teve modalidade ali, ou no retorno de ex-aluno")
    void fee() {
        assertThat(EnrollmentConclusion.fee(true, 5000, false, false)).isEqualTo(5000);
        assertThat(EnrollmentConclusion.fee(true, 5000, true, false)).isZero();
        assertThat(EnrollmentConclusion.fee(true, 5000, true, true)).isEqualTo(5000);
        assertThat(EnrollmentConclusion.fee(false, 5000, false, false)).isZero();
    }

    @Test
    @DisplayName("CA11: o tipo do comprovante vem do conteúdo (JPG, PNG, WebP, PDF); o resto é recusado")
    void proofKind() {
        assertThat(FileKind.of(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}).extension()).isEqualTo("jpg");
        assertThat(FileKind.of(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0}).extension()).isEqualTo("png");
        assertThat(FileKind.of("RIFF....WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)).extension()).isEqualTo("webp");
        assertThat(FileKind.of("%PDF-1.7".getBytes(StandardCharsets.ISO_8859_1)).extension()).isEqualTo("pdf");
        assertThat(FileKind.of("GIF89a".getBytes(StandardCharsets.ISO_8859_1))).isNull();
        assertThat(FileKind.of(new byte[] {1, 2})).isNull();
    }
}
