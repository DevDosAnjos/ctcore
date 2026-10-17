package br.com.ctcore.identity;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opens the account and invite-code scopes read by the row-level security policies of V6; never both at once. */
@Component
@Lazy
public class AccountTransactions {
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    public AccountTransactions(@Lazy JdbcClient jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    /** Rows of this account (its links, invites and link units). */
    public <T> T forAccount(UUID accountId, Supplier<T> work) {
        return scoped(Objects.requireNonNull(accountId).toString(), "", work);
    }

    /** The single invite that has this SHA-256 code hash, and its link. */
    public <T> T forInvite(String codeHash, Supplier<T> work) {
        if (codeHash == null || codeHash.isBlank()) {
            throw new AccessDeniedException("Invite code scope requires a hash");
        }
        return scoped("", codeHash, work);
    }

    private <T> T scoped(String account, String codeHash, Supplier<T> work) {
        return transactions.execute(status -> {
            var previous = jdbc.sql("select current_setting('app.account_scope_initialized', true) as initialized, "
                    + "coalesce(current_setting('app.account_id', true), '') as account, "
                    + "coalesce(current_setting('app.invite_code_hash', true), '') as code").query().singleRow();
            if ("true".equals(previous.get("initialized"))
                    && (!account.equals(previous.get("account")) || !codeHash.equals(previous.get("code")))) {
                throw new AccessDeniedException("Cannot change account scope inside a transaction");
            }
            jdbc.sql("select set_config('app.account_id', :account, true), set_config('app.invite_code_hash', :code, true), "
                    + "set_config('app.account_scope_initialized', 'true', true)")
                    .param("account", account).param("code", codeHash).query().singleRow();
            return work.get();
        });
    }
}
