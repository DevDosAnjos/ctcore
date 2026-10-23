package br.com.ctcore.payment;

import br.com.ctcore.identity.AccountPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DailyReceiptsController {
    private final DailyReceipts receipts;

    DailyReceiptsController(DailyReceipts receipts) {
        this.receipts = receipts;
    }

    @GetMapping("/api/v1/billing/daily-receipts")
    DailyReceipts.Day day(@AuthenticationPrincipal AccountPrincipal who, @RequestParam UUID unitId, @RequestParam(required = false) String date) {
        return receipts.day(who, unitId, date);
    }
}
