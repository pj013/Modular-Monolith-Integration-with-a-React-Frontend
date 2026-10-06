# LegacySupply Reflection

## 1. LegacySupply holds more than one order for BuyerRef "your reference": PO-100354 (22:40:09) and PO-100355 (22:40:37) and PO-100356 (22:42:40) and PO-100357 (22:42:57). Reconstruct the sequence of events that produced the duplicate, and describe the change you made (or would make) so it cannot happen again.

At 22:40:09 and 22:40:37, I sent separate Postman `POST /purchase-orders` requests using the literal BuyerRef `your reference`, creating PO-100354 and PO-100355. At 22:42:40, another request using that same reference received a 503, even though the order list later showed PO-100356; PO-100357 at 22:42:57 also used that reference, but had a different SKU, so the order list alone does not prove it was a retry of PO-100356. The manual says LegacySupply stores BuyerRef but does not enforce its uniqueness, so repeating it did not deduplicate those requests. In my adapter, each reorder gets a persisted `RO-<id>` BuyerRef and matching stable request ID, and the database constrains both references to be unique.

## 2. At 22:42:40 your request for BuyerRef "your reference" received a 503, but LegacySupply had already created PO-100356. Walk through exactly what your adapter did next, and explain why that did or did not result in a second order.

The 22:42:40 request was sent manually from Postman, not through my Spring adapter, so the adapter did not handle that 503 or control what Postman did next. The order list shows LegacySupply created PO-100356 despite returning 503, and the verifier later reported duplicate references from my repeated manual requests. For requests sent by my adapter, a 503 is retryable and `LegacySupplyClient` resends the POST at most three times with the same persisted `X-Request-Id`, allowing LegacySupply to replay the result rather than create another order. My local RO-4 record later showed one transport failure and then a single PO-100361 with the same RO-4 reference, which is the adapter path behaving as intended.

## 3. PO-100356 (BuyerRef "your reference") ended with StatusCode 90, which is not in the documentation. How did you work out what it means, and what does your system now do with the stock that will never arrive?

The manual documents status codes 10, 20, 30, and 40, but not 90; I observed 90 on PO-100356 in LegacySupply's order list and correlated that record with the cancelled-order scenario in the verifier. I added a mapping from 90 to my own `CANCELLED` status in `LegacySupplyClient`, without exposing the supplier code outside the adapter. Inventory is only restocked after a PO reaches `DELIVERED`, so a cancelled PO never adds its expected units to on-hand stock; the cancelled order is not treated as received. The current implementation does not automatically create a replacement PO after a supplier cancellation, so any replacement replenishment must be initiated as a new reorder.

### Marketplace reflection questions

## 1. Duplicate feed event

`ChannelFeedPoller` checks whether an `eventId` was processed before handling it. `ChannelRepository` stores processed IDs in `channel_events`, where `event_id` is the primary key, so the second delivery of `evt_b8cb1cc571eaed55` at seq 155 was skipped. If the app restarted between deliveries, that table and the feed cursor in `channel_state` would still be in the database, so the repeated event would still be recognized.

## 2. Backorder filled after delivery

When PO-104114 was marked delivered, the supplier adapter published a delivery event and `SupplierDeliveryListener` added the received units to Inventory. `ChannelBackorderResolver` listens for that event, retries the waiting order through `OrderService`, reserves the now-available stock, and asks Tiangge to resolve the backorder as accepted.

## 3. Restart recovery

The app resumes polling with the saved cursor from `channel_state`, so after restarting it fetched the feed events that arrived while it was down. It does not start from zero, and `channel_events` plus the order's unique external reference prevent already-processed events or orders from being handled twice.
