CREATE TABLE smart_vaults (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              user_id UUID NOT NULL,
                              vault_name VARCHAR(64) NOT NULL,
                              vault_type VARCHAR(32) NOT NULL,
                              target_amount NUMERIC(12, 2) DEFAULT NULL,
                              current_balance NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
                              auto_transfer_date INT DEFAULT NULL,
                              has_card_linked BOOLEAN NOT NULL DEFAULT FALSE,
                              version BIGINT NOT NULL DEFAULT 0,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                              updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                              CONSTRAINT chk_vault_type CHECK (vault_type IN ('FIXED_EXPENSES', 'SAVINGS', 'GOAL')),
                              CONSTRAINT chk_positive_target CHECK (target_amount IS NULL OR target_amount > 0),
                              CONSTRAINT chk_positive_balance CHECK (current_balance >= 0),
                              CONSTRAINT chk_transfer_day CHECK (auto_transfer_date IS NULL OR (auto_transfer_date >= 1 AND auto_transfer_date <= 31)),
    -- Règle bancaire : Le coffre d'épargne ne peut en aucun cas avoir de carte rattachée
                              CONSTRAINT chk_savings_no_card CHECK (
                                  vault_type != 'SAVINGS' OR has_card_linked = FALSE
)
    );

CREATE INDEX idx_smart_vaults_user_id ON smart_vaults(user_id);