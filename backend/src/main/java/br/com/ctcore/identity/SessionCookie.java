package br.com.ctcore.identity;

import org.springframework.http.ResponseCookie;

/** Cookie SESSION: HttpOnly, Secure, SameSite=Lax, no Max-Age (there is no "stay signed in", RN-ACE-19). */
public final class SessionCookie {
    public static final String NAME = "SESSION";

    private SessionCookie() {}

    public static ResponseCookie create(String token) {
        return base(token).build();
    }

    public static ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(true).sameSite("Lax").path("/");
    }
}
