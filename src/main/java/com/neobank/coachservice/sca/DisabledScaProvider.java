package com.neobank.coachservice.sca;

public class DisabledScaProvider implements ScaProvider {

    @Override
    public String issueChallenge(ChallengeRequest request) {
        throw new ScaProviderUnavailableException();
    }

    @Override
    public boolean verifyChallenge(VerificationRequest request) {
        throw new ScaProviderUnavailableException();
    }
}
