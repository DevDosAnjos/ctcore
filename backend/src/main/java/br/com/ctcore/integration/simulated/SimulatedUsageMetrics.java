package br.com.ctcore.integration.simulated;

import br.com.ctcore.integration.UsageMetrics;

/** Não guarda nem envia nada. */
public class SimulatedUsageMetrics implements UsageMetrics {
    @Override
    public void record(String event) { }
}
