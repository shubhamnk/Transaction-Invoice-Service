# Architecture

The service is a single Spring Boot process. HTTP controllers bind requests and status codes. `TransactionService` and `InvoiceService` own validation, money math, and transaction boundaries. Repositories persist rows in SQLite through Spring Data JPA. Jackson uses snake_case JSON.

```text
HTTP clients
    |
    v
TransactionController / InvoiceController
    |
    v
TransactionService / InvoiceService
    |
    v
Repositories and SQLite (./data/navan.db)
```

## Why the tables are split

A transaction has one header and two child collections that must stay queryable on their own: tax lines and fee lines. Those lines are rows in `transaction_tax_lines` and `transaction_fee_lines`, not fields buried in metadata. The header stores `type`, `external_id`, `currency`, `occurred_at`, `total`, and a JSON `metadata` document.

Metadata is validated by type and stored whole, so GET returns the same structure that was accepted:

| Type | Required metadata |
| --- | --- |
| `flight` | `origin`, `destination`, `fare`, and `coupons`. Each coupon has `flight_number`, `from`, `to`, `departure`, and `arrival`. `carrier` is optional. An empty coupon list is valid. |
| `hotel` | `name`, `address.street`, `address.city`, `address.country`, `check_in`, `check_out`, `nights`, and `room_rate_per_night`. Extra fields such as `extras` are kept. |
| `rail` | `legs` with at least one leg. Each leg has `origin`, `destination`, `departure`, and `arrival`. `train_number` and `class` are optional. |
| `navan_fee` | `fee_kind` of `trip_fee` or `agent_call_fee`. `description` and `related_external_id` are optional. |

Unknown types, unknown fee kinds, and missing required metadata return 400. The service does not insert a header when validation fails.

Header, tax lines, fee lines, and metadata are saved in one database transaction. Child foreign keys use `ON DELETE CASCADE`, so deleting a transaction removes its lines. Invoice snapshots reference `transactions.id` with `ON DELETE RESTRICT`, which is a second guard behind the service check that refuses to delete an invoiced transaction.

## Duplicate `external_id`

`ux_transactions_external_id` is a unique index on `transactions.external_id`. The create path inserts and flushes. It does not check-then-insert, and it does not take an in-process lock. If SQLite reports a unique violation on `external_id`, the failed write is rolled back, the existing row is loaded, and the API returns 409:

```json
{
  "status": 409,
  "error": "DUPLICATE_EXTERNAL_ID",
  "message": "A transaction with this external_id already exists",
  "existing_transaction_id": "txn_..."
}
```

Overlapping POSTs for the same `external_id` compete on that index. WAL mode and `busy_timeout=10000` let the loser wait for the winner's lock and then fail the unique check. A `SQLITE_BUSY` result is retried a few times. Exactly one row remains. Ids are random `txn_` / `inv_` values, not row counts.

## Invoice snapshots

`POST /invoices` accepts `transaction_ids`, `transaction_id`, or both. The ids are trimmed, blanks are rejected, and a repeated id returns 409 before any write. Missing ids return 404. Mixed currencies return 409. Those failures leave the database unchanged.

On success the service, in one database transaction:

1. Copies each transaction header, metadata JSON, tax line, and fee line into `invoice_transaction_snapshots`, `invoice_snapshot_tax_lines`, and `invoice_snapshot_fee_lines`.
2. Builds `invoice_tax_rollups` and `invoice_fee_rollups`.
3. Stores invoice `subtotal`, `tax_total`, `fee_total`, and `total`.

`GET /invoices/{id}` maps those snapshot and rollup tables. It does not join back to `transactions` for amounts, metadata, taxes, or fees. The copied `transaction_id` is only an identity pointer so the service can tell whether a live transaction is on an invoice.

`PATCH /invoices/{id}` validates the replacement set before it deletes the old children. If validation fails, the old snapshots remain. If validation passes, the service clears snapshots and rollups, flushes, and inserts the new copy in the same transaction. A persistence failure rolls the whole replacement back.

`DELETE /invoices/{id}` removes the invoice and, through foreign-key cascade, its snapshots and rollups. It does not delete transactions. A transaction with no remaining snapshot row can be patched or deleted again.

## Totals and rollup grouping

Every money amount is a `BigDecimal`. Input amounts are normalized to scale 2 with `RoundingMode.HALF_UP`. The invoice grand total is the sum of the included transaction totals. Tax and fee amounts are already inside those gross totals, so the rollups are not added again.

`tax_total` is the sum of tax rollup amounts. `fee_total` is the sum of fee rollup amounts. `subtotal` is `total - tax_total - fee_total`. For the five seed transactions that is EUR 685.70, tax 36.70, fees 29.00, and subtotal 620.00.

Tax lines group by name and rate. A null rate is its own group, so "VAT" with rate 0.07 does not merge with "VAT" that has no rate. Rates compare numerically after trailing zeros are removed, so 0.07 and 0.070 are one group. Fee lines group by name only. Group order follows the first time that key appears in the request's transaction order.

## Pagination, indexes, and contention

`GET /transactions` and `GET /invoices` take `limit` and `offset`. The default limit is 50 and the default offset is 0. A limit outside 1..100, or a negative offset, returns 400 `INVALID_PAGINATION`. The chosen behavior is to reject `limit=1000` rather than silently cap it. The repository query uses Hibernate `setFirstResult` and `setMaxResults`, which SQLite executes as `LIMIT` / `OFFSET`. The service does not load the table and slice it in memory. Transactions are ordered by `occurred_at` descending, then id. Invoices are ordered by `created_at` descending, then id.

Indexes:

| Index | Purpose |
| --- | --- |
| `ux_transactions_external_id` | One row per client reference; decides concurrent POST winners |
| `ix_transactions_occurred_at` | Transaction list order |
| `ix_invoices_created_at` | Invoice list order |
| `ix_transaction_tax_lines_transaction_id` | Load tax lines for one transaction |
| `ix_transaction_fee_lines_transaction_id` | Load fee lines for one transaction |
| `ix_invoice_snapshots_transaction_id` | "Is this transaction on an invoice?" |
| `ix_invoice_snapshots_invoice_id` | Load one invoice's snapshots |
| `ix_invoice_snapshot_tax_lines_snapshot_id` | Snapshot tax lines |
| `ix_invoice_snapshot_fee_lines_snapshot_id` | Snapshot fee lines |
| `ix_invoice_tax_rollups_invoice_id` | Tax rollups |
| `ix_invoice_fee_rollups_invoice_id` | Fee rollups |

Primary keys cover id lookups. Child tables are also indexed on the parent foreign key.

SQLite serializes writers. Concurrent PATCH and DELETE use a database transaction and a `@Version` column. A stale write returns 409 `CONCURRENT_MODIFICATION`. Replacing tax or fee lines clears the collection and inserts the new lines in that same transaction, so a reader does not see a half-updated list. The invoiced-transaction check runs before any mutation. The snapshot foreign key is a backstop if a delete races an invoice insert.

The first limits under load are the single-writer SQLite lock, large invoice copies (bounded by the ids in one request), and unbounded list scans. Page limits and the indexes above are the controls in this service. Redis, Kafka, distributed locks, and extra infrastructure are intentionally absent.
