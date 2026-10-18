package br.com.ctcore.organization;

import br.com.ctcore.api.error.ApiValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * RF-ORG-04: corpo de PUT /units/{id}, completo (campo omitido apaga). {@link #validated()} normaliza ou lança 400 com o campo.
 * Nome curto, organização e situação do Mercado Pago não existem aqui: o que o cliente mandar a mais é ignorado.
 */
public record UnitUpdate(String name, String postalCode, String address, String city, Double latitude, Double longitude, String phone,
        String email, String whatsapp, String cnpj, Settings settings) {
    public record TeacherPermissions(Boolean announcements, Boolean agendaEdit, Boolean studentContacts, Boolean trialClasses) {}

    public record Settings(List<Integer> dueDays, Boolean enrollmentFeeEnabled, Long enrollmentFeeCents, Map<String, Long> feesCents,
            String pixKey, List<Integer> reminderDays, TeacherPermissions teacherPermissions, Boolean directoryEnabled,
            Boolean trialLinkEnabled, Boolean selfRegistrationLinkEnabled) {

        Settings validated() {
            List<Integer> due = days("settings.dueDays", dueDays, 1, 28, 28, false);
            boolean feeOn = required("settings.enrollmentFeeEnabled", enrollmentFeeEnabled);
            long enrollment = 0;
            if (feeOn) {
                enrollment = required("settings.enrollmentFeeCents", enrollmentFeeCents);
                if (enrollment < 0 || enrollment > MAX_ENROLLMENT_CENTS) throw new ApiValidationException("settings.enrollmentFeeCents", "valor inválido");
            }
            Map<String, Long> sent = required("settings.feesCents", feesCents);
            Map<String, Long> fees = new LinkedHashMap<>();
            for (String key : FEE_COLUMNS.keySet()) {
                String path = "settings.feesCents." + key;
                Long cents = required(path, sent.get(key));
                if (cents < 0 || cents > MAX_FEE_CENTS) throw new ApiValidationException(path, "entre 0 e R$ 1.000,00");
                fees.put(key, cents);
            }
            String pix = field("settings.pixKey", () -> PixKey.normalize(pixKey));
            List<Integer> reminders = days("settings.reminderDays", reminderDays, -30, 60, MAX_REMINDERS, true);
            TeacherPermissions t = required("settings.teacherPermissions", teacherPermissions);
            TeacherPermissions teacher = new TeacherPermissions(required("settings.teacherPermissions.announcements", t.announcements()),
                    required("settings.teacherPermissions.agendaEdit", t.agendaEdit()),
                    required("settings.teacherPermissions.studentContacts", t.studentContacts()),
                    required("settings.teacherPermissions.trialClasses", t.trialClasses()));
            return new Settings(due, feeOn, enrollment, fees, pix, reminders, teacher, required("settings.directoryEnabled", directoryEnabled),
                    required("settings.trialLinkEnabled", trialLinkEnabled),
                    required("settings.selfRegistrationLinkEnabled", selfRegistrationLinkEnabled));
        }
    }

    /** Chave do contrato para a coluna de taxa de cada meio de pagamento (RN-PAG-05). */
    static final Map<String, String> FEE_COLUMNS = new LinkedHashMap<>();

    static {
        FEE_COLUMNS.put("cash", "cash_fee_cents");
        FEE_COLUMNS.put("pix", "pix_fee_cents");
        FEE_COLUMNS.put("bankSlip", "bank_slip_fee_cents");
        FEE_COLUMNS.put("creditCard", "credit_card_fee_cents");
        FEE_COLUMNS.put("debitCard", "debit_card_fee_cents");
        FEE_COLUMNS.put("automaticDebit", "automatic_debit_fee_cents");
        FEE_COLUMNS.put("manualPix", "manual_pix_fee_cents");
        FEE_COLUMNS.put("terminalCard", "terminal_card_fee_cents");
        FEE_COLUMNS.put("transfer", "transfer_fee_cents");
    }

    static final long MAX_FEE_CENTS = 100_000; // D5: teto de R$ 1.000,00 por meio
    static final long MAX_ENROLLMENT_CENTS = 100_000_000; // R$ 1.000.000,00, só para barrar erro de digitação
    static final int MAX_REMINDERS = 8;

    public UnitUpdate validated() {
        if (settings == null) throw new ApiValidationException("settings", "obrigatório");
        NewUnit data = new NewUnit(name, postalCode, address, city, latitude, longitude, phone, email, whatsapp, null).validated();
        String cnpjDigits = field("cnpj", () -> HolderDocument.normalizeCnpj(cnpj));
        return new UnitUpdate(data.name(), data.postalCode(), data.address(), data.city(), data.latitude(), data.longitude(), data.phone(),
                data.email(), data.whatsapp(), cnpjDigits, settings.validated());
    }

    private static <T> T field(String field, Supplier<T> parse) {
        try {
            return parse.get();
        } catch (IllegalArgumentException e) {
            throw new ApiValidationException(field, e.getMessage());
        }
    }

    private static <T> T required(String field, T value) {
        if (value == null) throw new ApiValidationException(field, "obrigatório");
        return value;
    }

    /** Lista de inteiros sem repetição dentro da faixa, em ordem crescente. */
    private static List<Integer> days(String field, List<Integer> raw, int min, int max, int maxSize, boolean allowEmpty) {
        if (raw == null) throw new ApiValidationException(field, "obrigatório");
        if (raw.isEmpty() && !allowEmpty) throw new ApiValidationException(field, "informe ao menos um dia");
        if (raw.size() > maxSize) throw new ApiValidationException(field, "no máximo " + maxSize + " itens");
        List<Integer> sorted = new ArrayList<>();
        for (Integer d : raw) {
            if (d == null || d < min || d > max) throw new ApiValidationException(field, "valores entre " + min + " e " + max);
            if (sorted.contains(d)) throw new ApiValidationException(field, "sem repetição");
            sorted.add(d);
        }
        Collections.sort(sorted);
        return sorted;
    }
}
