package br.com.ctcore.permission;

import br.com.ctcore.identity.AccountPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permissões efetivas do vínculo ativo, para o menu. Só lê: sem auditoria (D6). Portal ou sem vínculo: tudo NONE. */
@RestController
class PermissionController {
    record PermissionsView(Map<Permission, Access> permissions) {}

    private final Permissions permissions;

    PermissionController(Permissions permissions) {
        this.permissions = permissions;
    }

    @GetMapping("/api/v1/permissions")
    PermissionsView read(@AuthenticationPrincipal AccountPrincipal principal) {
        return new PermissionsView(permissions.effective(principal));
    }
}
