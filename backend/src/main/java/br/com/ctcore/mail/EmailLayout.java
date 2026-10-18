package br.com.ctcore.mail;

/** RNF-28: one column up to 600 px, 18 px text and a full-width button when there is a link. RNF-22: brand color on the button, "feito com ctcore" in the footer. Arguments are escaped by the caller. */
final class EmailLayout {
    static final String DEFAULT_COLOR = "#1a56db";
    static final String FOOTER = "feito com ctcore";

    private EmailLayout() {}

    static String html(String text, String link, String button) {
        return html(text, link, button, null);
    }

    /** @param text already escaped message text; @param link already escaped link or null; @param button its label; @param color organization color (#RRGGBB) or null for the platform color */
    static String html(String text, String link, String button, String color) {
        // only a plain #rrggbb reaches the style attribute; anything else falls back to the platform color
        String background = color != null && color.matches("#[0-9a-fA-F]{6}") ? color : DEFAULT_COLOR;
        String btn = link == null ? "" : "<p><a href=\"" + link + "\" style=\"display:block;width:100%;box-sizing:border-box;padding:16px;"
                + "background:" + background + ";color:#ffffff;font-size:18px;text-align:center;text-decoration:none;border-radius:8px\">"
                + button + "</a></p>";
        // the text keeps the link and ends with a line break so tools that read the link as plain text stop at it
        return "<!doctype html><html><body style=\"margin:0;padding:16px;font-family:Arial,sans-serif;font-size:18px;line-height:1.5\">"
                + "<table role=\"presentation\" width=\"100%\" style=\"max-width:600px;margin:0 auto\"><tr><td style=\"font-size:18px\">"
                + "<p style=\"font-size:18px\">" + text + "\n</p>" + btn
                + "<p style=\"font-size:14px;color:#595959\">" + FOOTER + "</p></td></tr></table></body></html>";
    }
}
