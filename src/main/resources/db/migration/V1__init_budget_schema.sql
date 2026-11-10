-- Table du profil budgétaire client
CREATE TABLE budget_profiles (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 user_id UUID NOT NULL UNIQUE,
                                 net_monthly_income NUMERIC(12, 2) NOT NULL,
                                 needs_ratio INT NOT NULL DEFAULT 50,
                                 wants_ratio INT NOT NULL DEFAULT 30,
                                 savings_ratio INT NOT NULL DEFAULT 20,
                                 version BIGINT NOT NULL DEFAULT 0,
                                 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                                 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                                 CONSTRAINT chk_positive_income CHECK (net_monthly_income > 0),
                                 CONSTRAINT chk_ratios_bounds CHECK (
                                     needs_ratio >= 0 AND needs_ratio <= 100 AND
                                     wants_ratio >= 0 AND wants_ratio <= 100 AND
                                     savings_ratio >= 0 AND savings_ratio <= 100
                                     ),
                                 CONSTRAINT chk_ratios_total CHECK (needs_ratio + wants_ratio + savings_ratio = 100)
);

-- Table du journal d'audit immuable
CREATE TABLE audit_logs (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            user_id UUID NOT NULL,
                            action VARCHAR(64) NOT NULL,
                            resource_type VARCHAR(64) NOT NULL,
                            resource_id VARCHAR(64),
                            details JSONB,
                            ip_address VARCHAR(45),
                            timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index pour optimiser les requêtes indexées par utilisateur
CREATE INDEX idx_budget_profiles_user ON budget_profiles(user_id);
CREATE INDEX idx_audit_logs_user_timestamp ON audit_logs(user_id, timestamp DESC);