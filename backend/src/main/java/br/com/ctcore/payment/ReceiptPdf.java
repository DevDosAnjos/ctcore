package br.com.ctcore.payment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** RN-COB-16: recibo em PDF (A4, uma página). Fonte padrão WinAnsi cobre o pt-BR; o que ela não cobre vira "?". Não guarda nada: só monta os bytes. */
final class ReceiptPdf {
    /** Linha da função {@code receipt_pdf_data}. */
    record Data(String logo, String unitName, String cnpj, String address, String timezone, int number, String code, long amountCents, long feePassedCents,
            String method, OffsetDateTime paidAt, String studentName, String payerName, String payerCpf, String reference) {}

    static final String NOT_INVOICE = "Este recibo não é nota fiscal.";
    private static final Map<String, String> METHODS = Map.of("CASH", "Dinheiro", "PIX_KEY", "Pix na chave", "CARD_MACHINE", "Cartão na maquininha", "TRANSFER", "Transferência");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.of("pt", "BR"));
    private static final float LEFT = 50;
    private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * LEFT;

    private ReceiptPdf() {}

    static String money(long cents) {
        return "R$ " + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR"))).format(cents / 100.0);
    }

    static String cpf(String digits) {
        return digits.length() == 11 ? digits.substring(0, 3) + "." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-" + digits.substring(9) : digits;
    }

    /** CPF do PDF aberto pelo link público (RN-COB-16): só os dígitos do meio. */
    static String maskedCpf(String digits) {
        return digits.length() == 11 ? "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**" : "***";
    }

    static String cnpj(String digits) {
        return digits.matches("[0-9]{14}") ? digits.substring(0, 2) + "." + digits.substring(2, 5) + "." + digits.substring(5, 8) + "/" + digits.substring(8, 12) + "-" + digits.substring(12) : digits;
    }

    /** {@code logo} pode ser nulo; imagem que o PDF não lê (ex.: WebP) é omitida sem erro. {@code fullCpf} só com sessão; sem ele o CPF sai mascarado. */
    static byte[] render(Data d, byte[] logo, boolean fullCpf) throws IOException {
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream out = new PDPageContentStream(doc, page)) {
                float y = PDRectangle.A4.getHeight() - 60;
                PDImageXObject image = image(doc, logo);
                if (image != null) {
                    out.drawImage(image, LEFT, y - 60, 60, 60);
                    y -= 75;
                }
                y = line(out, bold, 16, y, d.unitName());
                if (d.cnpj() != null && !d.cnpj().isBlank()) y = line(out, regular, 10, y, "CNPJ: " + cnpj(d.cnpj()));
                if (d.address() != null && !d.address().isBlank()) y = line(out, regular, 10, y, d.address());
                y -= 18;
                y = line(out, bold, 14, y, "Recibo nº " + d.number());
                y -= 8;
                String payerLine = d.payerCpf() == null ? d.payerName() : d.payerName() + " · CPF " + (fullCpf ? cpf(d.payerCpf()) : maskedCpf(d.payerCpf()));
                String[][] rows = {{"Pago por", payerLine}, {"Aluno", d.studentName()}, {"Referente a", d.reference()}, {"Valor", money(d.amountCents())},
                    {"Taxa repassada", money(d.feePassedCents())}, {"Meio", METHODS.getOrDefault(d.method(), d.method())},
                    {"Data e hora", WHEN.format(d.paidAt().atZoneSameInstant(ZoneId.of(d.timezone())))}};
                for (String[] r : rows) y = line(out, regular, 11, y, r[0] + ": " + r[1]);
                y -= 18;
                y = line(out, bold, 11, y, NOT_INVOICE);
                y = line(out, regular, 10, y, "Código " + d.code() + " · ctcore.com.br/recibo/" + d.code());
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            doc.save(bytes);
            return bytes.toByteArray();
        }
    }

    private static PDImageXObject image(PDDocument doc, byte[] logo) {
        if (logo == null) return null;
        try {
            return PDImageXObject.createFromByteArray(doc, logo, "logo");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Escreve o texto quebrando por largura; devolve o próximo y. */
    private static float line(PDPageContentStream out, PDType1Font font, float size, float y, String raw) throws IOException {
        for (String text : wrap(font, size, safe(font, raw))) {
            out.beginText();
            out.setFont(font, size);
            out.newLineAtOffset(LEFT, y);
            out.showText(text);
            out.endText();
            y -= size + 5;
        }
        return y;
    }

    private static List<String> wrap(PDType1Font font, float size, String text) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.getStringWidth(next) / 1000 * size > WIDTH) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(next);
            }
        }
        lines.add(current.toString());
        return lines;
    }

    private static String safe(PDType1Font font, String text) {
        StringBuilder sb = new StringBuilder();
        text.codePoints().forEach(cp -> {
            try {
                font.encode(new String(Character.toChars(cp)));
                sb.appendCodePoint(cp);
            } catch (IOException | IllegalArgumentException e) {
                sb.append('?');
            }
        });
        return sb.toString();
    }
}
