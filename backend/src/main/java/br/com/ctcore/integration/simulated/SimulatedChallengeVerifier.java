package br.com.ctcore.integration.simulated;

import br.com.ctcore.integration.ChallengeVerifier;

public class SimulatedChallengeVerifier implements ChallengeVerifier {
    @Override
    public boolean verify(String token) { return true; }
}
