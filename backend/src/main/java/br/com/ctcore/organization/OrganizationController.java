package br.com.ctcore.organization;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.organization.OrganizationProfile.OrganizationView;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** RF-ORG-01 (D1): sem identificador na URL; a organização é a do vínculo ativo da sessão. */
@RestController
@RequestMapping("/api/v1/organization")
class OrganizationController {
    record UpdateRequest(String name, String color, String holderDocument) {}

    private final OrganizationProfile profile;

    OrganizationController(OrganizationProfile profile) {
        this.profile = profile;
    }

    @GetMapping
    OrganizationView read(@AuthenticationPrincipal AccountPrincipal who) {
        return profile.read(who);
    }

    @PatchMapping
    OrganizationView update(@AuthenticationPrincipal AccountPrincipal who, @RequestBody UpdateRequest body, HttpServletRequest request) {
        return profile.update(who, body.name(), body.color(), body.holderDocument(), request.getRemoteAddr());
    }

    @PutMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    OrganizationView replaceLogo(@AuthenticationPrincipal AccountPrincipal who, @RequestPart(value = "file", required = false) MultipartFile file,
            HttpServletRequest request) throws IOException {
        if (file == null) throw new ApiValidationException("file", "obrigatório");
        return profile.replaceLogo(who, file.getBytes(), request.getRemoteAddr());
    }

    @DeleteMapping("/logo")
    OrganizationView removeLogo(@AuthenticationPrincipal AccountPrincipal who, HttpServletRequest request) {
        return profile.removeLogo(who, request.getRemoteAddr());
    }
}
