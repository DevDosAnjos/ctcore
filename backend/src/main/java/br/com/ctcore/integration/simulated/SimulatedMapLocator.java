package br.com.ctcore.integration.simulated;

import br.com.ctcore.integration.MapLocator;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Tabela fixa e pequena (D6); a #12 a alinha aos CEPs do script de dados. */
public class SimulatedMapLocator implements MapLocator {

    private static final Pattern CEP = Pattern.compile("\\d{5}-?\\d{3}");
    private static final Map<String, Coordinates> TABLE = Map.of(
            "01001000", new Coordinates(-23.5505, -46.6333),
            "20040020", new Coordinates(-22.9068, -43.1729),
            "30130010", new Coordinates(-19.9167, -43.9345),
            "80010000", new Coordinates(-25.4284, -49.2733),
            "90010000", new Coordinates(-30.0346, -51.2177));

    @Override
    public Optional<Coordinates> locate(String cep) {
        if (cep == null || !CEP.matcher(cep).matches()) return Optional.empty();
        return Optional.ofNullable(TABLE.get(cep.replace("-", "")));
    }
}
