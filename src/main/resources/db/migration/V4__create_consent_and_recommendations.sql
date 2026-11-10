-- Table du suivi des consentements (Loi 09-08 / RGPD)
CREATE TABLE user_consents (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id UUID NOT NULL UNIQUE,
                               ai_processing_consented BOOLEAN NOT NULL DEFAULT FALSE,
                               policy_version VARCHAR(32) NOT NULL,
                               consented_at TIMESTAMP WITH TIME ZONE,
                               revoked_at TIMESTAMP WITH TIME ZONE,
                               ip_address VARCHAR(45) NOT NULL,
                               updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Table d'historisation des recommandations générées par l'IA
CREATE TABLE budget_recommendations (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        user_id UUID NOT NULL,
                                        summary TEXT NOT NULL,
                                        recommendations_payload JSONB NOT NULL,
                                        potential_monthly_savings NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
                                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                                        expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_user_consents_user ON user_consents(user_id);
CREATE INDEX idx_recommendations_user_created ON budget_recommendations(user_id, created_at DESC);
CREATE INDEX idx_recommendations_expiry ON budget_recommendations(expires_at);