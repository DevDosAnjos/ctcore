package br.com.ctcore.identity;

/** "Navegador em Sistema" from a User-Agent, by known fragments; order matters (Edge and Opera also say Chrome). */
final class DeviceLabel {
    static final String UNKNOWN = "Aparelho desconhecido";

    private DeviceLabel() {}

    static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String browser = first(userAgent, "Edg/", "Edge", "OPR/", "Opera", "Firefox/", "Chrome/", "CriOS", "Safari/");
        String system = first(userAgent, "Windows", "Android", "iPhone", "iPad", "Mac OS X", "Macintosh", "CrOS", "Linux");
        if (browser == null || system == null) {
            return UNKNOWN;
        }
        return browser + " em " + system;
    }

    private static String first(String agent, String... keys) {
        for (String key : keys) {
            if (agent.contains(key)) {
                return switch (key) {
                    case "Edg/", "Edge" -> "Edge";
                    case "OPR/", "Opera" -> "Opera";
                    case "Firefox/" -> "Firefox";
                    case "Chrome/", "CriOS" -> "Chrome";
                    case "Safari/" -> "Safari";
                    case "iPhone", "iPad" -> "iOS";
                    case "Mac OS X", "Macintosh" -> "macOS";
                    case "CrOS" -> "ChromeOS";
                    default -> key;
                };
            }
        }
        return null;
    }
}
