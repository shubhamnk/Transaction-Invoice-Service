package com.navan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NavanApiIntegrationTest {

    private static final List<String> SEED_FILES = List.of(
            "flight.json",
            "hotel.json",
            "rail.json",
            "navan-trip-fee.json",
            "navan-agent-call-fee.json");

    @LocalServerPort
    private int port;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void cleanDatabase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM invoice_snapshot_tax_lines");
        jdbc.update("DELETE FROM invoice_snapshot_fee_lines");
        jdbc.update("DELETE FROM invoice_tax_rollups");
        jdbc.update("DELETE FROM invoice_fee_rollups");
        jdbc.update("DELETE FROM invoice_transaction_snapshots");
        jdbc.update("DELETE FROM invoices");
        jdbc.update("DELETE FROM transaction_tax_lines");
        jdbc.update("DELETE FROM transaction_fee_lines");
        jdbc.update("DELETE FROM transactions");
    }

    @Test
    void createsAllSeedTransactionsAndReadsTypeSpecificMetadata() throws Exception {
        Map<String, String> ids = createSeeds();

        JsonNode flight = readOk("/transactions/" + ids.get("flight-TXL-LHR-2026-03-11"));
        assertEquals("flight", flight.get("type").asText());
        assertEquals("BA982", flight.get("metadata").get("coupons").get(0).get("flight_number").asText());
        assertEquals("BA", flight.get("metadata").get("coupons").get(0).get("carrier").asText());
        assertMoney(flight.get("total"), "220.00");
        assertEquals("Airport tax", flight.get("tax_lines").get(0).get("name").asText());
        assertFalse(flight.get("tax_lines").get(0).has("rate"));
        assertMoney(flight.get("tax_lines").get(0).get("amount"), "15.00");
        assertEquals("YQ", flight.get("fee_lines").get(0).get("name").asText());

        JsonNode hotel = readOk("/transactions/" + ids.get("hotel-LON-2026-03-11"));
        assertEquals("Hotel Mitte London", hotel.get("metadata").get("name").asText());
        assertEquals("12 Kingsway", hotel.get("metadata").get("address").get("street").asText());
        assertEquals("London", hotel.get("metadata").get("address").get("city").asText());
        assertEquals("GB", hotel.get("metadata").get("address").get("country").asText());
        assertEquals("Breakfast", hotel.get("metadata").get("extras").get(0).get("name").asText());
        assertMoney(hotel.get("total"), "331.70");
        assertMoney(hotel.get("tax_lines").get(0).get("rate"), "0.07");

        JsonNode rail = readOk("/transactions/" + ids.get("rail-STP-PAR-2026-03-13"));
        assertEquals(2, rail.get("metadata").get("legs").size());
        assertEquals("EST9108", rail.get("metadata").get("legs").get(0).get("train_number").asText());
        assertEquals("Paris Nord", rail.get("metadata").get("legs").get(1).get("destination").asText());

        JsonNode tripFee = readOk("/transactions/" + ids.get("navan-trip-fee-2026-03-11"));
        assertEquals("trip_fee", tripFee.get("metadata").get("fee_kind").asText());
        JsonNode agentFee = readOk("/transactions/" + ids.get("navan-agent-call-2026-03-11"));
        assertEquals("agent_call_fee", agentFee.get("metadata").get("fee_kind").asText());

        assertEquals(1, count("transactions", "external_id = 'flight-TXL-LHR-2026-03-11'"));
        assertTrue(count("transaction_tax_lines", "1 = 1") >= 2);
        assertTrue(count("transaction_fee_lines", "1 = 1") >= 2);
    }

    @Test
    void duplicateExternalIdReturns409AndDoesNotInsertOrOverwrite() throws Exception {
        JsonNode created = postJson("/transactions", fixture("flight.json"), 201);
        JsonNode duplicate = postJson("/transactions", fixture("duplicate-flight.json"), 409);

        assertEquals(409, duplicate.get("status").asInt());
        assertEquals("DUPLICATE_EXTERNAL_ID", duplicate.get("error").asText());
        assertEquals(created.get("id").asText(), duplicate.get("existing_transaction_id").asText());
        assertEquals(1, count("transactions", "external_id = 'flight-TXL-LHR-2026-03-11'"));

        JsonNode stored = readOk("/transactions/" + created.get("id").asText());
        assertEquals(1, stored.get("metadata").get("coupons").size());
        assertEquals("BA982", stored.get("metadata").get("coupons").get(0).get("flight_number").asText());
    }

    @Test
    void concurrentDuplicateCreateLeavesExactlyOneRow() throws Exception {
        String payload = fixture("flight.json");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/transactions"))
                            .timeout(Duration.ofSeconds(20))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();
                    return client.send(request, HttpResponse.BodyHandlers.ofString());
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int created = 0;
            int conflicts = 0;
            String winnerId = null;
            for (Future<HttpResponse<String>> future : futures) {
                HttpResponse<String> response = future.get(30, TimeUnit.SECONDS);
                JsonNode body = objectMapper.readTree(response.body());
                if (response.statusCode() == 201) {
                    created++;
                    winnerId = body.get("id").asText();
                } else if (response.statusCode() == 409) {
                    conflicts++;
                    assertEquals("DUPLICATE_EXTERNAL_ID", body.get("error").asText());
                    assertNotNull(body.get("existing_transaction_id"));
                    if (winnerId == null) {
                        winnerId = body.get("existing_transaction_id").asText();
                    }
                } else {
                    throw new AssertionError("Unexpected status " + response.statusCode() + " body " + response.body());
                }
            }
            assertEquals(1, created);
            assertEquals(threads - 1, conflicts);
            assertEquals(1, count("transactions", "external_id = 'flight-TXL-LHR-2026-03-11'"));
            for (Future<HttpResponse<String>> future : futures) {
                // already consumed
            }
            JsonNode stored = readOk("/transactions/" + winnerId);
            assertEquals("BA982", stored.get("metadata").get("coupons").get(0).get("flight_number").asText());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void invoiceOfFiveSeedsTotals68570AndFreezesSnapshots() throws Exception {
        Map<String, String> ids = createSeeds();
        JsonNode invoice = postJson("/invoices", invoiceBody(ids.values().stream().toList()), 201);

        assertMoney(invoice.get("total"), "685.70");
        assertMoney(invoice.get("tax_total"), "36.70");
        assertMoney(invoice.get("fee_total"), "29.00");
        assertMoney(invoice.get("subtotal"), "620.00");
        assertEquals("EUR", invoice.get("currency").asText());
        assertEquals(5, invoice.get("transactions").size());

        JsonNode flight = snapshot(invoice, "flight-TXL-LHR-2026-03-11");
        assertMoney(flight.get("total"), "220.00");
        assertEquals("BA982", flight.get("metadata").get("coupons").get(0).get("flight_number").asText());
        assertEquals(1, flight.get("tax_lines").size());
        assertEquals(1, flight.get("fee_lines").size());

        JsonNode hotel = snapshot(invoice, "hotel-LON-2026-03-11");
        assertEquals("Hotel Mitte London", hotel.get("metadata").get("name").asText());
        assertEquals("12 Kingsway", hotel.get("metadata").get("address").get("street").asText());
        assertMoney(hotel.get("total"), "331.70");

        JsonNode rail = snapshot(invoice, "rail-STP-PAR-2026-03-13");
        assertEquals(2, rail.get("metadata").get("legs").size());
        assertEquals("trip_fee", snapshot(invoice, "navan-trip-fee-2026-03-11").get("metadata").get("fee_kind").asText());
        assertEquals("agent_call_fee", snapshot(invoice, "navan-agent-call-2026-03-11").get("metadata").get("fee_kind").asText());
        assertEquals(0, snapshot(invoice, "navan-trip-fee-2026-03-11").get("fee_lines").size());

        assertMoney(rollup(invoice.get("tax_rollups"), "Airport tax").get("amount"), "15.00");
        assertFalse(rollup(invoice.get("tax_rollups"), "Airport tax").has("rate"));
        assertMoney(rollup(invoice.get("tax_rollups"), "VAT").get("amount"), "21.70");
        assertMoney(rollup(invoice.get("tax_rollups"), "VAT").get("rate"), "0.07");
        assertMoney(rollup(invoice.get("fee_rollups"), "YQ").get("amount"), "25.00");
        assertMoney(rollup(invoice.get("fee_rollups"), "Booking fee").get("amount"), "4.00");

        String flightId = ids.get("flight-TXL-LHR-2026-03-11");
        String invoiceId = invoice.get("id").asText();
        jdbc.update("UPDATE transactions SET total = ? WHERE id = ?", "999.99", flightId);
        jdbc.update("UPDATE transactions SET metadata = ? WHERE id = ?", "{\"origin\":\"CHANGED\"}", flightId);
        jdbc.update("UPDATE transaction_tax_lines SET amount = ? WHERE transaction_id = ?", "1.00", flightId);

        JsonNode live = readOk("/transactions/" + flightId);
        assertMoney(live.get("total"), "999.99");

        JsonNode reread = readOk("/invoices/" + invoiceId);
        assertMoney(reread.get("total"), "685.70");
        assertMoney(snapshot(reread, "flight-TXL-LHR-2026-03-11").get("total"), "220.00");
        assertEquals("BA982", snapshot(reread, "flight-TXL-LHR-2026-03-11").get("metadata").get("coupons").get(0).get("flight_number").asText());
        assertMoney(snapshot(reread, "flight-TXL-LHR-2026-03-11").get("tax_lines").get(0).get("amount"), "15.00");
        assertEquals("220.00", jdbc.queryForObject(
                "SELECT total FROM invoice_transaction_snapshots WHERE invoice_id = ? AND transaction_id = ?",
                String.class,
                invoiceId,
                flightId));
    }

    @Test
    void mixedCurrencyAndDuplicateIdsReturn409WithoutCreatingAnInvoice() throws Exception {
        Map<String, String> ids = createSeeds();
        JsonNode usd = postJson("/transactions", fixture("usd-flight.json"), 201);
        String flightId = ids.get("flight-TXL-LHR-2026-03-11");

        JsonNode mixed = postJson("/invoices", invoiceBody(List.of(flightId, usd.get("id").asText())), 409);
        assertEquals("MIXED_CURRENCY", mixed.get("error").asText());
        assertEquals(0, count("invoices", "1 = 1"));

        JsonNode duplicates = postJson("/invoices", invoiceBody(List.of(flightId, flightId)), 409);
        assertEquals("DUPLICATE_TRANSACTION_IDS", duplicates.get("error").asText());
        assertEquals(0, count("invoices", "1 = 1"));

        JsonNode single = postJson("/invoices", "{\"transaction_id\":\"" + flightId + "\"}", 201);
        assertMoney(single.get("total"), "220.00");
        assertEquals(1, single.get("transactions").size());
    }

    @Test
    void invoicedTransactionPatchAndDeleteReturn409AndChangeNothing() throws Exception {
        Map<String, String> ids = createSeeds();
        String flightId = ids.get("flight-TXL-LHR-2026-03-11");
        postJson("/invoices", invoiceBody(List.of(flightId)), 201);

        JsonNode patched = patchJson("/transactions/" + flightId, "{\"total\":999.99}", 409);
        assertEquals("TRANSACTION_INVOICED", patched.get("error").asText());
        assertMoney(readOk("/transactions/" + flightId).get("total"), "220.00");
        assertEquals("220.00", jdbc.queryForObject("SELECT total FROM transactions WHERE id = ?", String.class, flightId));

        JsonNode deleted = deleteJson("/transactions/" + flightId, 409);
        assertEquals("TRANSACTION_INVOICED", deleted.get("error").asText());
        assertEquals(1, count("transactions", "id = '" + flightId + "'"));
    }

    @Test
    void deleteInvoiceRemovesSnapshotsAndReleasesTransactions() throws Exception {
        Map<String, String> ids = createSeeds();
        String flightId = ids.get("flight-TXL-LHR-2026-03-11");
        String hotelId = ids.get("hotel-LON-2026-03-11");
        JsonNode invoice = postJson("/invoices", invoiceBody(List.of(flightId, hotelId)), 201);
        String invoiceId = invoice.get("id").asText();

        deleteJson("/invoices/" + invoiceId, 204);
        deleteJson("/invoices/" + invoiceId, 404);
        assertEquals(0, count("invoices", "1 = 1"));
        assertEquals(0, count("invoice_transaction_snapshots", "1 = 1"));
        assertEquals(0, count("invoice_snapshot_tax_lines", "1 = 1"));
        assertEquals(0, count("invoice_tax_rollups", "1 = 1"));
        assertEquals(1, count("transactions", "id = '" + flightId + "'"));
        assertEquals(1, count("transactions", "id = '" + hotelId + "'"));

        JsonNode updated = patchJson("/transactions/" + flightId, "{\"total\":210.00}", 200);
        assertMoney(updated.get("total"), "210.00");
        deleteJson("/transactions/" + flightId, 204);
        assertEquals(0, count("transactions", "id = '" + flightId + "'"));
        assertEquals(0, count("transaction_tax_lines", "transaction_id = '" + flightId + "'"));
        read("/transactions/" + flightId, 404);
    }

    @Test
    void transactionPaginationDefaultsTo50AndRejectsLimitOver100() throws Exception {
        createTransaction("page-a", "2026-03-01T00:00:00Z", "10.00");
        createTransaction("page-b", "2026-03-02T00:00:00Z", "20.00");
        createTransaction("page-c", "2026-03-03T00:00:00Z", "30.00");

        JsonNode defaults = readOk("/transactions");
        assertEquals(50, defaults.get("limit").asInt());
        assertEquals(0, defaults.get("offset").asInt());
        assertEquals(3, defaults.get("total_count").asInt());
        assertEquals(3, defaults.get("items").size());
        assertEquals("page-c", defaults.get("items").get(0).get("external_id").asText());

        JsonNode page = readOk("/transactions?limit=2&offset=0");
        assertEquals(2, page.get("items").size());
        assertEquals(2, page.get("limit").asInt());
        assertEquals("page-c", page.get("items").get(0).get("external_id").asText());
        assertEquals("page-b", page.get("items").get(1).get("external_id").asText());

        JsonNode next = readOk("/transactions?limit=2&offset=2");
        assertEquals(1, next.get("items").size());
        assertEquals("page-a", next.get("items").get(0).get("external_id").asText());
        assertEquals(3, next.get("total_count").asInt());

        JsonNode capped = read("/transactions?limit=100", 200);
        assertEquals(100, capped.get("limit").asInt());
        JsonNode rejected = read("/transactions?limit=1000", 400);
        assertEquals("INVALID_PAGINATION", rejected.get("error").asText());
        read("/transactions?limit=101", 400);
        read("/transactions?limit=0", 400);
        read("/transactions?offset=-1", 400);
    }

    @Test
    void invoicePaginationDefaultsTo50AndRejectsLimitOver100() throws Exception {
        String first = createTransaction("inv-page-a", "2026-03-01T00:00:00Z", "10.00").get("id").asText();
        String second = createTransaction("inv-page-b", "2026-03-02T00:00:00Z", "11.00").get("id").asText();
        String third = createTransaction("inv-page-c", "2026-03-03T00:00:00Z", "12.00").get("id").asText();
        postJson("/invoices", invoiceBody(List.of(first)), 201);
        postJson("/invoices", invoiceBody(List.of(second)), 201);
        JsonNode newest = postJson("/invoices", invoiceBody(List.of(third)), 201);

        JsonNode defaults = readOk("/invoices");
        assertEquals(50, defaults.get("limit").asInt());
        assertEquals(0, defaults.get("offset").asInt());
        assertEquals(3, defaults.get("total_count").asInt());
        assertEquals(newest.get("id").asText(), defaults.get("items").get(0).get("id").asText());

        JsonNode page = readOk("/invoices?limit=2&offset=2");
        assertEquals(1, page.get("items").size());
        assertEquals(3, page.get("total_count").asInt());

        JsonNode rejected = read("/invoices?limit=1000", 400);
        assertEquals("INVALID_PAGINATION", rejected.get("error").asText());
        read("/invoices?limit=100", 200);
        read("/invoices?limit=101", 400);
    }

    @Test
    void missingTransactionAndInvoiceIdsReturn404() throws Exception {
        JsonNode missingTransaction = read("/transactions/txn_missing", 404);
        assertEquals("TRANSACTION_NOT_FOUND", missingTransaction.get("error").asText());
        JsonNode missingInvoice = read("/invoices/inv_missing", 404);
        assertEquals("INVOICE_NOT_FOUND", missingInvoice.get("error").asText());

        JsonNode missingCreate = postJson("/invoices", "{\"transaction_ids\":[\"txn_missing\"]}", 404);
        assertEquals("TRANSACTION_NOT_FOUND", missingCreate.get("error").asText());
        assertEquals(0, count("invoices", "1 = 1"));
    }

    @Test
    void invoicePatchReplacesSnapshotsAtomicallyAndKeepsTheOldInvoiceOnFailure() throws Exception {
        Map<String, String> ids = createSeeds();
        String flightId = ids.get("flight-TXL-LHR-2026-03-11");
        String hotelId = ids.get("hotel-LON-2026-03-11");
        JsonNode usd = postJson("/transactions", fixture("usd-flight.json"), 201);
        JsonNode invoice = postJson("/invoices", invoiceBody(List.of(flightId)), 201);
        String invoiceId = invoice.get("id").asText();

        JsonNode missing = patchJson("/invoices/" + invoiceId, "{\"transaction_ids\":[\"" + hotelId + "\",\"txn_missing\"]}", 404);
        assertEquals("TRANSACTION_NOT_FOUND", missing.get("error").asText());
        JsonNode unchanged = readOk("/invoices/" + invoiceId);
        assertMoney(unchanged.get("total"), "220.00");
        assertEquals(flightId, unchanged.get("transactions").get(0).get("transaction_id").asText());

        JsonNode mixed = patchJson("/invoices/" + invoiceId, invoiceBody(List.of(flightId, usd.get("id").asText())), 409);
        assertEquals("MIXED_CURRENCY", mixed.get("error").asText());
        assertMoney(readOk("/invoices/" + invoiceId).get("total"), "220.00");
        assertEquals(1, count("invoice_transaction_snapshots", "invoice_id = '" + invoiceId + "'"));

        JsonNode duplicate = patchJson("/invoices/" + invoiceId, invoiceBody(List.of(hotelId, hotelId)), 409);
        assertEquals("DUPLICATE_TRANSACTION_IDS", duplicate.get("error").asText());
        assertEquals(flightId, readOk("/invoices/" + invoiceId).get("transactions").get(0).get("transaction_id").asText());

        JsonNode replaced = patchJson("/invoices/" + invoiceId, invoiceBody(List.of(hotelId)), 200);
        assertMoney(replaced.get("total"), "331.70");
        assertEquals(1, replaced.get("transactions").size());
        assertEquals(hotelId, replaced.get("transactions").get(0).get("transaction_id").asText());
        assertEquals("Hotel Mitte London", replaced.get("transactions").get(0).get("metadata").get("name").asText());
        assertEquals(0, count("invoice_transaction_snapshots", "invoice_id = '" + invoiceId + "' AND transaction_id = '" + flightId + "'"));

        JsonNode patchedFlight = patchJson("/transactions/" + flightId, "{\"total\":111.00}", 200);
        assertMoney(patchedFlight.get("total"), "111.00");
    }

    @Test
    void taxAndFeeRollupsGroupByNameAndRate() throws Exception {
        String first = createRaw("""
                {
                  "type": "flight",
                  "external_id": "group-a",
                  "currency": "EUR",
                  "occurred_at": "2026-04-01T00:00:00Z",
                  "total": 10.00,
                  "tax_lines": [
                    {"name": "VAT", "rate": 0.07, "amount": 1.00},
                    {"name": "VAT", "amount": 2.00}
                  ],
                  "fee_lines": [{"name": "Booking fee", "amount": 1.00}],
                  "metadata": {
                    "origin": "AAA",
                    "destination": "BBB",
                    "fare": 7.00,
                    "coupons": []
                  }
                }
                """).get("id").asText();
        String second = createRaw("""
                {
                  "type": "flight",
                  "external_id": "group-b",
                  "currency": "EUR",
                  "occurred_at": "2026-04-02T00:00:00Z",
                  "total": 20.00,
                  "tax_lines": [
                    {"name": "VAT", "rate": 0.07, "amount": 3.00},
                    {"name": "VAT", "rate": 0.0700, "amount": 4.00}
                  ],
                  "fee_lines": [{"name": "Booking fee", "amount": 2.50}],
                  "metadata": {
                    "origin": "CCC",
                    "destination": "DDD",
                    "fare": 10.50,
                    "coupons": []
                  }
                }
                """).get("id").asText();

        JsonNode invoice = postJson("/invoices", invoiceBody(List.of(first, second)), 201);
        assertEquals(2, invoice.get("tax_rollups").size());
        assertMoney(rollup(invoice.get("tax_rollups"), "VAT", "0.07").get("amount"), "8.00");
        assertMoney(rollup(invoice.get("tax_rollups"), "VAT", null).get("amount"), "2.00");
        assertEquals(1, invoice.get("fee_rollups").size());
        assertMoney(invoice.get("fee_rollups").get(0).get("amount"), "3.50");
    }

    @Test
    void invalidMetadataAndUnknownTypesDoNotPersist() throws Exception {
        JsonNode unknown = postJson("/transactions", fixture("flight.json").replace("\"flight\"", "\"boat\""), 400);
        assertEquals("VALIDATION_ERROR", unknown.get("error").asText());

        JsonNode badFee = postJson("/transactions", fixture("navan-trip-fee.json").replace("trip_fee", "support_fee"), 400);
        assertTrue(badFee.get("message").asText().contains("fee_kind"));

        String missingAddress = fixture("hotel.json").replace("\"address\": {", "\"address_removed\": {");
        postJson("/transactions", missingAddress, 400);

        postJson("/transactions", fixture("flight.json").replace("\"coupons\"", "\"coupon_list\""), 400);
        assertEquals(0, count("transactions", "1 = 1"));
        assertEquals(0, count("transaction_tax_lines", "1 = 1"));
    }

    @Test
    void patchReplacesChildLinesAtomically() throws Exception {
        JsonNode created = postJson("/transactions", fixture("flight.json"), 201);
        String id = created.get("id").asText();
        JsonNode updated = patchJson("/transactions/" + id, """
                {
                  "total": 230.00,
                  "occurred_at": "2026-03-12T06:40:00Z",
                  "tax_lines": [{"name": "Replacement tax", "rate": 0.1, "amount": 10.00}],
                  "fee_lines": [],
                  "metadata": {
                    "origin": "TXL",
                    "destination": "LHR",
                    "fare": 220.00,
                    "coupons": [
                      {
                        "flight_number": "BA100",
                        "from": "TXL",
                        "to": "LHR",
                        "departure": "2026-03-12T07:00:00Z",
                        "arrival": "2026-03-12T08:20:00Z"
                      },
                      {
                        "carrier": "BA",
                        "flight_number": "BA200",
                        "from": "LHR",
                        "to": "TXL",
                        "departure": "2026-03-20T18:00:00Z",
                        "arrival": "2026-03-20T21:00:00Z"
                      }
                    ]
                  }
                }
                """, 200);
        assertMoney(updated.get("total"), "230.00");
        assertEquals(1, updated.get("tax_lines").size());
        assertEquals("Replacement tax", updated.get("tax_lines").get(0).get("name").asText());
        assertEquals(0, updated.get("fee_lines").size());
        assertEquals(2, updated.get("metadata").get("coupons").size());
        assertEquals(1, count("transaction_tax_lines", "transaction_id = '" + id + "'"));
        assertEquals(0, count("transaction_fee_lines", "transaction_id = '" + id + "'"));
    }

    @Test
    void requiredIndexesAndForeignKeysExist() {
        Integer foreignKeys = jdbc.queryForObject("PRAGMA foreign_keys", Integer.class);
        assertEquals(1, foreignKeys);
        List<String> indexes = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'index' ORDER BY name",
                String.class);
        assertTrue(indexes.contains("ux_transactions_external_id"));
        assertTrue(indexes.contains("ix_transactions_occurred_at"));
        assertTrue(indexes.contains("ix_transaction_tax_lines_transaction_id"));
        assertTrue(indexes.contains("ix_transaction_fee_lines_transaction_id"));
        assertTrue(indexes.contains("ix_invoices_created_at"));
        assertTrue(indexes.contains("ix_invoice_snapshots_transaction_id"));
        assertTrue(indexes.contains("ix_invoice_snapshots_invoice_id"));
        assertTrue(indexes.contains("ix_invoice_snapshot_tax_lines_snapshot_id"));
        assertTrue(indexes.contains("ix_invoice_snapshot_fee_lines_snapshot_id"));
        assertTrue(indexes.contains("ix_invoice_tax_rollups_invoice_id"));
        assertTrue(indexes.contains("ix_invoice_fee_rollups_invoice_id"));
    }

    private Map<String, String> createSeeds() throws Exception {
        Map<String, String> ids = new LinkedHashMap<>();
        for (String file : SEED_FILES) {
            JsonNode created = postJson("/transactions", fixture(file), 201);
            ids.put(created.get("external_id").asText(), created.get("id").asText());
        }
        return ids;
    }

    private JsonNode createTransaction(String externalId, String occurredAt, String total) throws Exception {
        return createRaw("""
                {
                  "type": "navan_fee",
                  "external_id": "%s",
                  "currency": "EUR",
                  "occurred_at": "%s",
                  "total": %s,
                  "tax_lines": [],
                  "fee_lines": [],
                  "metadata": {"fee_kind": "trip_fee"}
                }
                """.formatted(externalId, occurredAt, total));
    }

    private JsonNode createRaw(String json) throws Exception {
        return postJson("/transactions", json, 201);
    }

    private String invoiceBody(List<String> ids) throws Exception {
        return objectMapper.writeValueAsString(Map.of("transaction_ids", ids));
    }

    private String fixture(String name) throws Exception {
        return Files.readString(Path.of("examples", name));
    }

    private JsonNode postJson(String path, String json, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return read(result);
    }

    private JsonNode patchJson(String path, String json, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(patch(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return read(result);
    }

    private JsonNode readOk(String path) throws Exception {
        return read(path, 200);
    }

    private JsonNode read(String path, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andExpect(status().is(expectedStatus)).andReturn();
        return read(result);
    }

    private JsonNode deleteJson(String path, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(delete(path)).andExpect(status().is(expectedStatus)).andReturn();
        String body = result.getResponse().getContentAsString();
        if (body.isBlank()) {
            assertEquals(204, expectedStatus);
            return null;
        }
        return objectMapper.readTree(body);
    }

    private JsonNode read(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        if (result.getResponse().getStatus() >= 400 && (body == null || body.isBlank())) {
            throw new AssertionError("Empty error body for status " + result.getResponse().getStatus());
        }
        return body == null || body.isBlank() ? null : objectMapper.readTree(body);
    }

    private int count(String table, String where) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class);
        return count == null ? 0 : count;
    }

    private static void assertMoney(JsonNode node, String expected) {
        assertNotNull(node, "missing money value");
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(node.asText())), node.asText() + " != " + expected);
    }

    private static JsonNode snapshot(JsonNode invoice, String externalId) {
        for (JsonNode item : invoice.get("transactions")) {
            if (externalId.equals(item.get("external_id").asText())) {
                return item;
            }
        }
        throw new AssertionError("Missing snapshot " + externalId + " in " + invoice);
    }

    private static JsonNode rollup(JsonNode rollups, String name) {
        for (JsonNode item : rollups) {
            if (name.equals(item.get("name").asText())) {
                return item;
            }
        }
        throw new AssertionError("Missing rollup " + name + " in " + rollups);
    }

    private static JsonNode rollup(JsonNode rollups, String name, String rate) {
        for (JsonNode item : rollups) {
            if (!name.equals(item.get("name").asText())) {
                continue;
            }
            if (rate == null && !item.has("rate")) {
                return item;
            }
            if (rate != null && item.has("rate") && new BigDecimal(rate).compareTo(new BigDecimal(item.get("rate").asText())) == 0) {
                return item;
            }
        }
        throw new AssertionError("Missing rollup " + name + " rate " + rate + " in " + rollups);
    }
}
