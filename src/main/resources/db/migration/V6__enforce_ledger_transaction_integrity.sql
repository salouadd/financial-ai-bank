ALTER TABLE vault_lock_intents
    ADD CONSTRAINT fk_vault_lock_intents_vault
        FOREIGN KEY (vault_id) REFERENCES smart_vaults(id),
    ADD CONSTRAINT uq_vault_lock_intent_transaction_identity
        UNIQUE (id, user_id, vault_id, amount);

ALTER TABLE vault_ledger_entries
    ADD CONSTRAINT fk_vault_ledger_intent_transaction
        FOREIGN KEY (intent_id, user_id, vault_id, amount)
        REFERENCES vault_lock_intents(id, user_id, vault_id, amount),
    ADD CONSTRAINT chk_vault_ledger_idempotency_key_not_blank
        CHECK (length(trim(idempotency_key)) > 0);
