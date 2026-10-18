package br.com.ctcore.organization;

import br.com.ctcore.api.error.ApiValidationException;
import java.util.regex.Pattern;

/** RF-ORG-03: corpo de POST /units. {@link #validated()} normaliza (CEP e telefones só dígitos) ou lança 400 com o campo. */
public record NewUnit(String name, String postalCode, String address, String city, Double latitude, Double longitude, String phone,
        String email, String whatsapp, Administrator administrator) {
    public record Administrator(String name, String email) {}

    static final int MAX_NAME = 120;
    private static final Pattern CEP = Pattern.compile("\\d{5}-?\\d{3}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE = Pattern.compile("[1-9][1-9](9\\d{8}|\\d{8})");

    public NewUnit validated() {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) throw new ApiValidationException("name", "obrigatório");
        if (n.length() > MAX_NAME) throw new ApiValidationException("name", "no máximo 120 caracteres");
        String cep = blank(postalCode);
        if (cep != null) {
            if (!CEP.matcher(cep).matches()) throw new ApiValidationException("postalCode", "CEP inválido");
            cep = cep.replace("-", "");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new ApiValidationException(latitude == null ? "latitude" : "longitude", "informe latitude e longitude juntas");
        }
        if (latitude != null && !(latitude >= -90 && latitude <= 90)) throw new ApiValidationException("latitude", "entre -90 e 90");
        if (longitude != null && !(longitude >= -180 && longitude <= 180)) throw new ApiValidationException("longitude", "entre -180 e 180");
        Administrator admin = null;
        if (administrator != null) {
            String an = blank(administrator.name());
            String ae = blank(administrator.email());
            if (an == null && ae != null) throw new ApiValidationException("administrator.name", "obrigatório com o e-mail");
            if (an != null && ae == null) throw new ApiValidationException("administrator.email", "obrigatório com o nome");
            if (an != null) {
                if (an.length() > MAX_NAME) throw new ApiValidationException("administrator.name", "no máximo 120 caracteres");
                admin = new Administrator(an, email("administrator.email", ae));
            }
        }
        return new NewUnit(n, cep, limited("address", address, 200), limited("city", city, 100), latitude, longitude,
                phone("phone", phone), email("email", blank(email)), phone("whatsapp", whatsapp), admin);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String limited(String field, String value, int max) {
        String v = blank(value);
        if (v != null && v.length() > max) throw new ApiValidationException(field, "no máximo " + max + " caracteres");
        return v;
    }

    private static String email(String field, String value) {
        if (value != null && (value.length() > 254 || !EMAIL.matcher(value).matches())) throw new ApiValidationException(field, "e-mail inválido");
        return value;
    }

    // Mesma regra do front (masks.ts): DDD de 2 dígitos, fixo com 10 ou celular com 11 começando em 9; "55" na frente é descartado.
    private static String phone(String field, String value) {
        String v = blank(value);
        if (v == null) return null;
        String d = v.replaceAll("\\D", "");
        if (d.length() > 11 && d.startsWith("55")) d = d.substring(2);
        if (!PHONE.matcher(d).matches()) throw new ApiValidationException(field, "telefone inválido");
        return d;
    }
}
