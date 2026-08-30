CREATE TABLE IF NOT EXISTS events (global_position BIGSERIAL PRIMARY KEY, stream_id TEXT NOT NULL, version INTEGER NOT NULL, type TEXT NOT NULL, data JSONB NOT NULL, metadata JSONB NOT NULL, timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(), UNIQUE(stream_id, version));
CREATE INDEX IF NOT EXISTS idx_events_stream ON events(stream_id, version);
CREATE TABLE IF NOT EXISTS account_balances (account_id TEXT PRIMARY KEY, owner_name TEXT NOT NULL, balance NUMERIC(19,4) NOT NULL DEFAULT 0, currency TEXT NOT NULL DEFAULT 'USD', status TEXT NOT NULL DEFAULT 'active', opened_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL);
CREATE TABLE IF NOT EXISTS transaction_history (id BIGSERIAL PRIMARY KEY, account_id TEXT NOT NULL, type TEXT NOT NULL, amount NUMERIC(19,4) NOT NULL, balance_after NUMERIC(19,4) NOT NULL, description TEXT, counterparty_account_id TEXT, transfer_id TEXT, created_at TIMESTAMPTZ NOT NULL);
CREATE INDEX IF NOT EXISTS idx_txhist_account ON transaction_history(account_id);
CREATE TABLE IF NOT EXISTS projection_checkpoints (projection_name TEXT PRIMARY KEY, last_processed_position BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
