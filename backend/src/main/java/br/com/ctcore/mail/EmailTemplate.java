package br.com.ctcore.mail;

/** Message key prefix ({@code .subject} and {@code .body} in messages.properties) and whether the text carries a secret code. */
public enum EmailTemplate {
    INVITE("mail.invite", true, true),
    PASSWORD_RESET("mail.reset", true, true),
    PASSWORD_CHANGED("mail.password-changed", false, false),
    NEW_ACCESS("mail.new-access", false, false);

    final String key;
    final boolean secret;
    /** The first argument is a link, shown also as a full-width button. */
    final boolean link;

    EmailTemplate(String key, boolean secret, boolean link) {
        this.key = key;
        this.secret = secret;
        this.link = link;
    }
}
