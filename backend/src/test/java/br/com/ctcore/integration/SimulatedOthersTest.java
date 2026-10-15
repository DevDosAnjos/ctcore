package br.com.ctcore.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.integration.simulated.SimulatedChallengeVerifier;
import br.com.ctcore.integration.simulated.SimulatedMapLocator;
import br.com.ctcore.integration.simulated.SimulatedUsageMetrics;
import org.junit.jupiter.api.Test;

class SimulatedOthersTest {

    @Test
    void challengeAlwaysApproves() {
        var v = new SimulatedChallengeVerifier();
        assertThat(v.verify("abc")).isTrue();
        assertThat(v.verify("")).isTrue();
        assertThat(v.verify(null)).isTrue();
    }

    @Test
    void mapKnowsOnlyTheFixedCeps() {
        var m = new SimulatedMapLocator();
        assertThat(m.locate("01001000")).contains(new MapLocator.Coordinates(-23.5505, -46.6333));
        assertThat(m.locate("01001-000")).isPresent();
        assertThat(m.locate("99999999")).isEmpty();
        assertThat(m.locate("abc")).isEmpty();
        assertThat(m.locate("")).isEmpty();
        assertThat(m.locate(null)).isEmpty();
    }

    @Test
    void metricsAcceptsEventsAndKeepsNothing() {
        new SimulatedUsageMetrics().record("page_view");
        new SimulatedUsageMetrics().record(null);
    }
}
