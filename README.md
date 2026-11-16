# Navan Transaction & Invoice Service

HTTP service for Navan flight, hotel, rail, and fee transactions, plus invoices built from immutable snapshots of those transactions.

The API stores transaction headers, tax lines, fee lines, and type-specific metadata in a file-backed SQLite database. Invoice reads come from copied snapshot rows, so a later change to a live transaction cannot change an invoice that already exists.

## Prerequisites

- Java 17 or newer. The Maven build compiles with release 17. The automated suite in this repository was executed on OpenJDK 21, which runs that bytecode.
- Maven 3.8 or newer.

## Run the API

```bash
mvn spring-boot:run
```

The service listens on `http://localhost:8080`.

## Run the tests

```bash
mvn test
```

## Database

The running API uses `./data/navan.db`. The application creates the `data` directory and the SQLite file on startup. `schema.sql` runs with `CREATE TABLE IF NOT EXISTS`, so rows survive a restart. Tests do not use this file; they use `target/test-navan.db` and delete the rows before each test.

SQLite is opened with foreign keys, WAL journal mode, and a 10 second busy timeout. Monetary amounts are stored as decimal text, not floating-point numbers. Amounts use scale 2 and `HALF_UP` rounding. Tax rates keep up to 6 decimal places.

## Seed order and expected invoice

POST the example files to `/transactions` in this order:

1. `examples/flight.json` — EUR 220.00
2. `examples/hotel.json` — EUR 331.70
3. `examples/rail.json` — EUR 94.00
4. `examples/navan-trip-fee.json` — EUR 25.00
5. `examples/navan-agent-call-fee.json` — EUR 15.00

Create one invoice with all five returned ids. The grand total is the sum of the transaction totals: **EUR 685.70**. Tax and fee rollups are not added on top of that total.

| Rollup | Expected |
| --- | --- |
| Airport tax | 15.00, no rate |
| VAT | 21.70 at rate 0.07 |
| YQ | 25.00 |
| Booking fee | 4.00 |
| Subtotal | 620.00 (`total - tax_total - fee_total`) |
| Tax total | 36.70 |
| Fee total | 29.00 |
| Grand total | 685.70 |

`examples/duplicate-flight.json` uses the same `external_id` as the flight. The second POST returns 409 and does not insert another row.

## Curl examples

Create the flight:

```bash
curl -s -D - -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  --data @examples/flight.json
```

Create the hotel, rail, and both Navan fees:

```bash
curl -s -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' --data @examples/hotel.json
curl -s -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' --data @examples/rail.json
curl -s -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' --data @examples/navan-trip-fee.json
curl -s -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' --data @examples/navan-agent-call-fee.json
```

Duplicate create. Replace nothing: the file already reuses `flight-TXL-LHR-2026-03-11`. The response is 409 and includes the original id:

```bash
curl -s -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  --data @examples/duplicate-flight.json
```

```json
{
  "status": 409,
  "error": "DUPLICATE_EXTERNAL_ID",
  "message": "A transaction with this external_id already exists",
  "existing_transaction_id": "txn_..."
}
```

Read one transaction and list transactions. The list defaults to `limit=50` and `offset=0`. `limit` must be from 1 to 100; `limit=1000` returns 400 `INVALID_PAGINATION`.

```bash
curl -s http://localhost:8080/transactions/txn_REPLACE
curl -s 'http://localhost:8080/transactions?limit=50&offset=0'
```

Update a transaction that is not on an invoice. `type`, `external_id`, and `currency` cannot be changed. Child lists, when sent, replace the stored list.

```bash
curl -s -X PATCH http://localhost:8080/transactions/txn_REPLACE \
  -H 'Content-Type: application/json' \
  -d '{"total":210.00}'
```

After that transaction is included on an invoice, the same PATCH and a DELETE both return 409 `TRANSACTION_INVOICED` and leave the row unchanged:

```bash
curl -s -X PATCH http://localhost:8080/transactions/txn_REPLACE \
  -H 'Content-Type: application/json' \
  -d '{"total":999.99}'

curl -s -D - -X DELETE http://localhost:8080/transactions/txn_REPLACE
```

Create an invoice from one id, or from several:

```bash
curl -s -X POST http://localhost:8080/invoices \
  -H 'Content-Type: application/json' \
  -d '{"transaction_id":"txn_REPLACE"}'

curl -s -X POST http://localhost:8080/invoices \
  -H 'Content-Type: application/json' \
  -d '{"transaction_ids":["txn_FLIGHT","txn_HOTEL","txn_RAIL","txn_TRIP_FEE","txn_AGENT_FEE"]}'
```

Repeated ids return 409 `DUPLICATE_TRANSACTION_IDS`. Different currencies return 409 `MIXED_CURRENCY`. A missing id returns 404 `TRANSACTION_NOT_FOUND`. In every failed create, no invoice row is written.

Read and list invoices. List pagination matches transactions: default 50, maximum 100, and `limit=1000` is rejected with 400.

```bash
curl -s http://localhost:8080/invoices/inv_REPLACE
curl -s 'http://localhost:8080/invoices?limit=50&offset=0'
```

Replace an invoice's transactions. A failed PATCH leaves the previous snapshots in place. A successful PATCH rebuilds snapshots and rollups from the current stored transactions.

```bash
curl -s -X PATCH http://localhost:8080/invoices/inv_REPLACE \
  -H 'Content-Type: application/json' \
  -d '{"transaction_ids":["txn_HOTEL"]}'
```

Delete an invoice. Underlying transactions stay. A transaction that is no longer on any invoice can be updated or deleted.

```bash
curl -s -D - -X DELETE http://localhost:8080/invoices/inv_REPLACE
```

## Postman

Import `postman/Navan-Transaction-Invoice.postman_collection.json`. The collection variable `base_url` is `http://localhost:8080`. Run the folder **Seed and invoice** with the Postman Collection Runner, in order. Create requests save `flight_id`, `hotel_id`, `rail_id`, `trip_fee_id`, `agent_fee_id`, and `invoice_id` for the later requests. The invoice request asserts the grand total `685.70`.

## Test results

Command: `mvn test`

Run: 16 Nov 2026, 5:44 PM IST

```text
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.841 s
Test set: com.navan.NavanApiIntegrationTest
```

The suite uses the HTTP API and a real file-backed SQLite database.

| Result | Test | What it checks |
| --- | --- | --- |
| PASS | `createsAllSeedTransactionsAndReadsTypeSpecificMetadata` | Flight, hotel, rail, trip fee, and agent-call fee create and round-trip coupons, address, legs, and `fee_kind` |
| PASS | `duplicateExternalIdReturns409AndDoesNotInsertOrOverwrite` | Second POST with the same `external_id` returns 409 with the existing id and keeps the original coupons |
| PASS | `concurrentDuplicateCreateLeavesExactlyOneRow` | Eight overlapping POSTs leave exactly one transaction row |
| PASS | `invoiceOfFiveSeedsTotals68570AndFreezesSnapshots` | Five-transaction invoice total is EUR 685.70; a direct database change to the live flight does not change the snapshot or invoice GET |
| PASS | `mixedCurrencyAndDuplicateIdsReturn409WithoutCreatingAnInvoice` | EUR + USD returns 409; repeated ids return 409; a single `transaction_id` creates an invoice |
| PASS | `invoicedTransactionPatchAndDeleteReturn409AndChangeNothing` | PATCH and DELETE of an invoiced transaction return 409 and keep the stored total |
| PASS | `deleteInvoiceRemovesSnapshotsAndReleasesTransactions` | Invoice DELETE removes snapshots and rollups, leaves transactions, and then allows PATCH and DELETE |
| PASS | `transactionPaginationDefaultsTo50AndRejectsLimitOver100` | Default limit is 50, database pages honor `limit` and `offset`, and `limit=1000` returns 400 |
| PASS | `invoicePaginationDefaultsTo50AndRejectsLimitOver100` | The same pagination rules apply to `/invoices` |
| PASS | `missingTransactionAndInvoiceIdsReturn404` | Unknown transaction and invoice ids return 404, including an invoice create that names a missing transaction |
| PASS | `invoicePatchReplacesSnapshotsAtomicallyAndKeepsTheOldInvoiceOnFailure` | A bad PATCH leaves the original snapshot; a valid PATCH replaces it |
| PASS | `taxAndFeeRollupsGroupByNameAndRate` | Tax lines group by name and rate, with a separate null-rate group; fee lines group by name |
| PASS | `invalidMetadataAndUnknownTypesDoNotPersist` | Unknown types, unknown fee kinds, and missing metadata return 400 and insert nothing |
| PASS | `patchReplacesChildLinesAtomically` | PATCH replaces tax lines, fee lines, total, time, and coupons together |
| PASS | `requiredIndexesAndForeignKeysExist` | Foreign keys are enabled and the required indexes exist |

## Further reading

`ARCHITECTURE.md` describes the tables, the unique `external_id` rule, snapshot immutability, rollup grouping, and the high-volume limits.
