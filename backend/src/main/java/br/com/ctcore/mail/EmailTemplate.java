package br.com.ctcore.mail;

/** Message key prefix ({@code .subject} and {@code .body} in messages.properties) and whether the text carries a secret code. */
public enum EmailTemplate {
    INVITE("mail.invite", true),
    PASSWORD_RESET("mail.reset", true),
    PASSWORD_CHANGED("mail.password-changed", false),
    NEW_ACCESS("mail.new-access", false);

    final String key;
    final boolean secret;

    EmailTemplate(String key, boolean secret) {
        this.key = key;
        this.secret = secret;
    }
}
