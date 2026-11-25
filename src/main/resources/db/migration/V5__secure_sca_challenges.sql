ALTER TABLE vault_lock_intents
    ALTER COLUMN sca_challenge TYPE VARCHAR(256),
    ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE vault_lock_intents
    ADD CONSTRAINT chk_vault_lock_intent_failed_attempts CHECK (failed_attempts >= 0);
