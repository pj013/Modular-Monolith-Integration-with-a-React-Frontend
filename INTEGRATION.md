# LegacySupply Integration

## Product Mapping

Inventory seed products are mapped to matching items from this partner's authenticated `/catalog` response.

| Product ID | Inventory product | Catalog description | SupplierSku | PackSize |
|---|---|---|---|---:|
| P100 | Wireless Mouse | WIRELESS MOUSE 2.4GHZ | SAV-7593 | 6 |
| P200 | Mechanical Keyboard | KEYBOARD MECH TKL | SAV-4340 | 20 |
| P300 | USB-C Hub | USB HUB 4-PORT | SAV-5005 | 10 |

These values are defaults in `application.properties`; the corresponding `LS_Pxxx_SKU` and `LS_Pxxx_PACK_SIZE` environment variables can override them.

## Session Behavior

The adapter posts XML credentials to `/auth/token`, then sends the returned token in `X-LS-Session`. It caches the token and obtains a new one when the supplier rejects the current session. The self-check marked expired-session renewal as met, with 35 sign-ins and 15 requests receiving an expired-session response. The manual does not state a duration, and the exact session lifetime has not been measured.

## Observed Errors

| Code | Observed cause |
|---|---|
| E-AUTH-01 | An earlier PowerShell authentication probe sent only one character from the API key, so LegacySupply rejected those credentials. A later authenticated catalog response supplied the mappings above. |

The follow-on null-token error came from the PowerShell probe continuing after authentication failed; it was not a supplier error code.

## Quantity and Unit of Measure

`Qty` is the whole-number count in the supplier's ordering unit, not the number of individual inventory units. `Uom` identifies that unit; the manual's acknowledgement example uses `CS` for cases. The adapter computes `cases = ceil(unitsNeeded / PackSize)`. For example, if Inventory needs 13 units and the P100 PackSize is 6, request Qty 3 in CS and restock 18 units when delivered.

## Status Mapping and Unknown Values

Supplier status codes 10, 20, 30, and 40 map to the adapter's own `ACCEPTED`, `PICKING`, `SHIPPED`, and `DELIVERED` statuses. Status code 90 was observed on PO-100356 in this partner's order record and is mapped to the adapter's own `CANCELLED` status. Explicit `CANCELLED`/`CANCELED` status text also maps to `CANCELLED`. Other unrecognized or missing codes map to `UNKNOWN`; those purchase orders remain open and are polled again. A delivered event carries the case-rounded unit count back to Inventory for restocking. The manual lists 10/20/30/40 but omits the observed cancellation code 90.

## Resilience and Persistence

The additive migration is `db/supplier_orders.sql`; apply it to the existing database before starting the updated backend. Each reorder is first stored as `PENDING`. Its generated ID determines a stable, unique `BuyerRef` and `X-Request-Id`; retries and restarts reuse both. HTTP requests have a three-second timeout and at most three attempts with backoff. Scheduled dispatch retries pending rows, while scheduled tracking polls open purchase orders. Supplier credentials are read from `LS_API_KEY`, never committed.

For the marketplace lab, also apply the additive `db/marketplace_channel.sql` migration before
starting the backend. It adds a durable feed cursor, processed event IDs, order correlation, a
stock-update outbox, and backorder tracking; it does not reset existing shop data. Both outbound
adapters attach the same per-process `X-Client-Instance` UUID. The marketplace channel reads its
client ID from `TIANGGE_CLIENT_ID` (falling back to `LS_CLIENT_ID`) and its API key from `LS_API_KEY`.
Its listing supplier SKUs are read from the existing supplier product mappings.

The `edu.cit.aaron.channel` package keeps its HTTP client, JSON handling, feed poller, and persistence
adapter package-private. Order and Inventory receive only generic order references and inventory
events; neither module depends on Tiangge. Marketplace orders use the normal Order/Inventory
reservation path, and their generic source reference makes feed retries idempotent. Stock changes
are queued after the inventory transaction commits and retried until Tiangge accepts the update.
Do not make real partner API calls from Postman, curl, a browser, or an external script after the
first heartbeat/listing has made the shop live.

The supplier limits each request to 99 cases. A permanently invalid local request is retained with the system-owned `FAILED` status for correction; transient transport, rate-limit, and service errors remain `PENDING` with backoff.

The supplier's numeric quota and session lifetime have not been measured. Tracking is throttled to one scheduled polling pass per minute; confirm the provider's quota before increasing frequency.

## Verification Snapshot

Latest self-check results shared during implementation (2026-10-01; counters are cumulative): at least three purchase orders and three delivered orders were met. The duplicate-order check reported 3 duplicates, outage replay reported 0 of 1 blocked references placed later, cancellation reported 0 cancelled orders seen, and the request-ID check reported 140 of 147 order requests carrying `X-Request-Id`. Repeated manual Postman `BuyerRef` values such as `your reference` are a likely source of duplicate counts, based on the visible order list; this is not confirmed by LegacySupply. Historical counters cannot be corrected by changing local database rows.

The application-generated orders `RO-1` through `RO-4` later had PO numbers and reached local `DELIVERED` status. `RO-4` recorded a transport failure before succeeding with the same persisted request ID. PO-100356 showed observed status code 90 in the supplier order list, but it used the manual Postman BuyerRef `your reference` and was not tracked in the application's `supplier_orders` table. The adapter maps observed code 90 to `CANCELLED` for purchase orders it polls.