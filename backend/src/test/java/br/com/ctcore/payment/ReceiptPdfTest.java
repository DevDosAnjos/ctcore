package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-COB-16: conteúdo do recibo em PDF, com cada ramo (logo, CNPJ, CPF, responsável, avulsa, caracteres e endereço longos). */
class ReceiptPdfTest {
    private static final OffsetDateTime PAID = OffsetDateTime.parse("2026-11-05T13:30:00Z");

    private static ReceiptPdf.Data data(String cnpj, String address, String payer, String cpf, String student, String reference) {
        return new ReceiptPdf.Data(null, "Academia Centro", cnpj, address, "America/Sao_Paulo", 7, "ABCDEFGHJKLMNPQR", 15000, 0, "PIX_KEY", PAID, student, payer, cpf, reference);
    }

    /** Com sessão: CPF completo. */
    private static byte[] render(ReceiptPdf.Data d, byte[] logo) throws Exception {
        return ReceiptPdf.render(d, logo, true);
    }

    private static String text(byte[] pdf) throws Exception {
        try (var doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc).replaceAll("[ \\r\\n]+", " ");
        }
    }

    private static byte[] png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("CA5: mensalidade de adulto traz unidade, CNPJ, endereço, número, pagador com CPF, aluno, referência, valor, taxa, meio, data e hora no fuso, aviso e código")
    void adult() throws Exception {
        String t = text(render(data("12345678000190", "Rua das Flores, 10, Centro, São Paulo/SP", "João Conceição", "12345678901", "João Conceição", "Jiu-Jitsu · nov/2026"), png()));
        assertThat(t).contains("Academia Centro", "CNPJ: 12.345.678/0001-90", "Rua das Flores, 10, Centro, São Paulo/SP", "Recibo nº 7",
                "Pago por: João Conceição · CPF 123.456.789-01", "Aluno: João Conceição", "Referente a: Jiu-Jitsu · nov/2026", "Valor: R$ 150,00",
                "Taxa repassada: R$ 0,00", "Meio: Pix na chave", "Data e hora: 05/11/2026 às 10:30", ReceiptPdf.NOT_INVOICE, "Código ABCDEFGHJKLMNPQR · ctcore.com.br/recibo/ABCDEFGHJKLMNPQR");
    }

    @Test
    @DisplayName("RN-COB-16: pelo link público o CPF sai mascarado, só com os seis dígitos do meio; CPF fora do padrão vira só ***; sem CPF não há linha")
    void maskedCpf() throws Exception {
        String masked = text(ReceiptPdf.render(data(null, null, "Ana Lima", "98765432100", "Pedro Lima", "Kimono"), null, false));
        assertThat(masked).contains("Pago por: Ana Lima · CPF ***.654.321-**").doesNotContain("98765432100", "987.654.321-00", "987");
        assertThat(text(ReceiptPdf.render(data(null, null, "Ana Lima", "123", "Pedro Lima", "Kimono"), null, false))).contains("CPF ***").doesNotContain("123");
        assertThat(text(ReceiptPdf.render(data(null, null, "Maria", null, "Maria", "Taxa"), null, false))).doesNotContain("CPF");
        assertThat(ReceiptPdf.maskedCpf("12345678901")).isEqualTo("***.456.789-**");
    }

    @Test
    @DisplayName("CA6: responsável financeiro aparece como pagador e o aluno à parte; avulsa mostra a descrição; sem CNPJ, sem CPF e sem logo o PDF sai sem essas linhas")
    void guardianAndBlanks() throws Exception {
        String t = text(render(data(null, null, "Ana Lima", "98765432100", "Pedro Lima", "Kimono"), null));
        assertThat(t).contains("Pago por: Ana Lima · CPF 987.654.321-00", "Aluno: Pedro Lima", "Referente a: Kimono").doesNotContain("CNPJ");
        String bare = text(render(data("  ", "", "Maria", null, "Maria", "Taxa"), null));
        assertThat(bare).contains("Pago por: Maria Aluno").doesNotContain("CPF", "CNPJ");
    }

    @Test
    @DisplayName("Logo ilegível é omitida sem erro; caractere fora da fonte vira '?'; endereço longo quebra em linhas; CPF fora do padrão sai como está; meio desconhecido sai pelo código")
    void edges() throws Exception {
        assertThat(render(data(null, null, "A", null, "A", "x"), "não é imagem".getBytes())).startsWith("%PDF".getBytes());
        assertThat(text(render(data(null, "Rua " + "longa ".repeat(40), "Zoë 漢", null, "Zoë", "Linha\nnova"), null))).contains("Zoë ?", "Linha?nova");
        assertThat(ReceiptPdf.cpf("123")).isEqualTo("123");
        assertThat(ReceiptPdf.cnpj("12")).isEqualTo("12");
        assertThat(ReceiptPdf.money(123456)).isEqualTo("R$ 1.234,56");
        var d = new ReceiptPdf.Data(null, "U", null, null, "UTC", 1, "C", 1, 0, "OUTRO", PAID, "A", "A", null, "r");
        assertThat(text(render(d, null))).contains("Meio: OUTRO");
    }
}
