ALTER TABLE vault_ledger_entries
    DROP CONSTRAINT fk_vault_ledger_intent_transaction,
    ADD CONSTRAINT fk_vault_ledger_intent_transaction
        FOREIGN KEY (intent_id, user_id, vault_id, amount)
        REFERENCES vault_lock_intents(id, user_id, vault_id, amount)
        DEFERRABLE INITIALLY DEFERRED;
