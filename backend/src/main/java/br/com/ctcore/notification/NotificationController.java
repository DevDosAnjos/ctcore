package br.com.ctcore.notification;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-NOT-01: o vínculo é sempre o ativo da sessão; não há rota de marcar uma nem de apagar (D5, D7). */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {
    private final Notifications notifications;

    NotificationController(Notifications notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    Notifications.Listing list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) String filter) {
        return notifications.list(who, filter);
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount(@AuthenticationPrincipal AccountPrincipal who) {
        return Map.of("count", notifications.unreadCount(who));
    }

    @PostMapping("/read-all")
    Map<String, Integer> readAll(@AuthenticationPrincipal AccountPrincipal who, HttpServletRequest request) {
        return Map.of("changed", notifications.readAll(who, request.getRemoteAddr()));
    }

    @GetMapping("/preferences")
    Notifications.Preferences preferences(@AuthenticationPrincipal AccountPrincipal who) {
        return notifications.preferences(who);
    }

    @PutMapping("/preferences")
    Notifications.Preferences setPreferences(@AuthenticationPrincipal AccountPrincipal who, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return notifications.setReceiveAll(who, body.get("receiveAll"), request.getRemoteAddr());
    }
}
