package br.com.ctcore.organization;

import br.com.ctcore.isolation.OrganizationTransactions;
import java.util.Optional;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-ORG-15: nome antigo resolve para o nome atual da mesma unidade (D5).
 * Roda no escopo da organização do chamador; a rota pública que o consome (sprints futuras) define como obter esse escopo.
 */
@Service
public class UnitShortNames {
    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;

    public UnitShortNames(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public Optional<String> resolve(String shortName) {
        if (shortName == null) return Optional.empty();
        return transactions.execute(() -> jdbc.sql("""
                select u.short_name from unit u
                where u.short_name = :name
                   or u.id = (select h.unit_id from unit_short_name_history h where h.short_name = :name)
                """).param("name", shortName).query(String.class).optional());
    }
}
