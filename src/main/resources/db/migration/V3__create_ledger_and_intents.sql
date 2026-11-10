-- Table des intentions de transfert avec défi SCA
CREATE TABLE vault_lock_intents (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    user_id UUID NOT NULL,
                                    vault_id UUID NOT NULL,
                                    amount NUMERIC(12, 2) NOT NULL,
                                    sca_challenge VARCHAR(64) NOT NULL,
                                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                                    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                                    CONSTRAINT chk_intent_amount CHECK (amount > 0),
                                    CONSTRAINT chk_intent_status CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'FAILED'))
);

-- Table de journalisation comptable en partie double (Ledger immuable)
CREATE TABLE vault_ledger_entries (
                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      user_id UUID NOT NULL,
                                      vault_id UUID NOT NULL,
                                      intent_id UUID NOT NULL UNIQUE,
                                      idempotency_key VARCHAR(128) NOT NULL,
                                      amount NUMERIC(12, 2) NOT NULL,
                                      entry_type VARCHAR(16) NOT NULL, -- 'LOCK_INFLOW' (virement entrant) ou 'UNLOCK_OUTFLOW'
                                      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                                      CONSTRAINT chk_ledger_amount CHECK (amount > 0),
                                      CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN ('LOCK_INFLOW', 'UNLOCK_OUTFLOW'))
);

-- Clé unique pour garantir l'idempotence par utilisateur
CREATE UNIQUE INDEX idx_ledger_idempotency ON vault_ledger_entries(user_id, idempotency_key);
CREATE INDEX idx_intents_user_vault ON vault_lock_intents(user_id, vault_id);