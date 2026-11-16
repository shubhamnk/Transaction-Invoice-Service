CREATE TABLE IF NOT EXISTS transactions (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL CHECK (type IN ('flight', 'hotel', 'rail', 'navan_fee')),
    external_id TEXT NOT NULL,
    currency TEXT NOT NULL CHECK (length(currency) = 3),
    occurred_at TEXT NOT NULL,
    total TEXT NOT NULL,
    metadata TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    version INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS transaction_tax_lines (
    id TEXT PRIMARY KEY,
    transaction_id TEXT NOT NULL,
    name TEXT NOT NULL,
    rate TEXT,
    amount TEXT NOT NULL,
    line_order INTEGER NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS transaction_fee_lines (
    id TEXT PRIMARY KEY,
    transaction_id TEXT NOT NULL,
    name TEXT NOT NULL,
    amount TEXT NOT NULL,
    line_order INTEGER NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS invoices (
    id TEXT PRIMARY KEY,
    currency TEXT NOT NULL CHECK (length(currency) = 3),
    subtotal TEXT NOT NULL,
    tax_total TEXT NOT NULL,
    fee_total TEXT NOT NULL,
    total TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    version INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS invoice_transaction_snapshots (
    id TEXT PRIMARY KEY,
    invoice_id TEXT NOT NULL,
    transaction_id TEXT NOT NULL,
    type TEXT NOT NULL,
    external_id TEXT NOT NULL,
    currency TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    total TEXT NOT NULL,
    metadata TEXT NOT NULL,
    snapshot_order INTEGER NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS invoice_snapshot_tax_lines (
    id TEXT PRIMARY KEY,
    snapshot_id TEXT NOT NULL,
    name TEXT NOT NULL,
    rate TEXT,
    amount TEXT NOT NULL,
    line_order INTEGER NOT NULL,
    FOREIGN KEY (snapshot_id) REFERENCES invoice_transaction_snapshots (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS invoice_snapshot_fee_lines (
    id TEXT PRIMARY KEY,
    snapshot_id TEXT NOT NULL,
    name TEXT NOT NULL,
    amount TEXT NOT NULL,
    line_order INTEGER NOT NULL,
    FOREIGN KEY (snapshot_id) REFERENCES invoice_transaction_snapshots (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS invoice_tax_rollups (
    id TEXT PRIMARY KEY,
    invoice_id TEXT NOT NULL,
    name TEXT NOT NULL,
    rate TEXT,
    amount TEXT NOT NULL,
    line_order INTEGER NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS invoice_fee_rollups (
    id TEXT PRIMARY KEY,
    invoice_id TEXT NOT NULL,
    name TEXT NOT NULL,
    amount TEXT NOT NULL,
    line_order INTEGER NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_external_id ON transactions (external_id);
CREATE INDEX IF NOT EXISTS ix_transactions_occurred_at ON transactions (occurred_at, id);
CREATE INDEX IF NOT EXISTS ix_transaction_tax_lines_transaction_id ON transaction_tax_lines (transaction_id);
CREATE INDEX IF NOT EXISTS ix_transaction_fee_lines_transaction_id ON transaction_fee_lines (transaction_id);
CREATE INDEX IF NOT EXISTS ix_invoices_created_at ON invoices (created_at, id);
CREATE INDEX IF NOT EXISTS ix_invoice_snapshots_transaction_id ON invoice_transaction_snapshots (transaction_id);
CREATE INDEX IF NOT EXISTS ix_invoice_snapshots_invoice_id ON invoice_transaction_snapshots (invoice_id);
CREATE INDEX IF NOT EXISTS ix_invoice_snapshot_tax_lines_snapshot_id ON invoice_snapshot_tax_lines (snapshot_id);
CREATE INDEX IF NOT EXISTS ix_invoice_snapshot_fee_lines_snapshot_id ON invoice_snapshot_fee_lines (snapshot_id);
CREATE INDEX IF NOT EXISTS ix_invoice_tax_rollups_invoice_id ON invoice_tax_rollups (invoice_id);
CREATE INDEX IF NOT EXISTS ix_invoice_fee_rollups_invoice_id ON invoice_fee_rollups (invoice_id);
