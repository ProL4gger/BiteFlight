# Free Spark delivery flow — Kotlin and Realtime Database

This replaces the Cloud Functions flow. No Node.js installation, server,
service-account key, Cloud Functions deployment or paid plan is needed.

## Required setup

1. Publish the complete contents of `database-v2.rules.json` in Firebase Console
   → Realtime Database → Rules. Local edits alone do not publish rules.
   These rules target the grouped v2 data already used by the apps.
2. Build and install both updated Android apps when execution is allowed.
3. Approve the rider by setting `users/riders/{uid}/role` to `rider` in the console.
4. Open the rider dashboard, grant location permission and tap Go Online. Keep
   the dashboard visible for automatic claims. GPS tracking can still continue
   in its foreground service while the dashboard is hidden, but new order
   claiming is intentionally foreground-only.
5. Place a NEW order with the updated buyer app. Checkout writes the canonical
   order, buyer index and `deliveryQueue/{orderId}` together. Old orders without
   a queue entry are not automatically migrated or assigned.
6. The buyer's MainActivity must stay visible to receive local stage alerts.
   Allow notifications to also display them in the notification tray. If denied,
   the app still shows a toast and the live Orders progress tracker.

No commands or app/build/test execution were performed by the assistant.

## How claiming works

`RiderDeliveryRepository.kt` watches a queue containing restaurant/buyer IDs,
pickup coordinates and a timestamp, without customer addresses or order items.
Each online rider selects their nearest queued pickup (straight-line distance).
This is rider-side claiming, not a globally optimal nearest-rider dispatcher:
when several riders compete, the first successful claim wins.

The rider first reserves `delivery/riders/{uid}` using a transaction. Rules allow
only one reservation and require fresh online presence. It then claims the
canonical order's `riderId` using another transaction. Rules allow only the first
claim on a PLACED order; order contents cannot be changed by riders.

The two transactions are intentionally separate so clients never need root-level
read/write access. During a brief reservation window, several riders can reserve
the same candidate, but only one can become its assigned rider. Losers release
their reservation and retry. A stopped app can leave a reservation; reopening
the rider dashboard resumes or releases it. Reservations are not automatically
reassigned from riders whose app remains closed.

After claiming, the rider sets status ASSIGNED and removes the queue entry.
The assigned dashboard reads the canonical order at
`orders/{sellerId}/{orderId}`, using the seller ID stored with the reservation.
There is no new write to the old `delivery/jobs` path.

Stage sequence:

`PLACED → ASSIGNED → WAITING_AT_RESTAURANT → PICKED_UP → IN_AREA → DELIVERED`

Only the approved assigned rider with the matching reservation can advance a
stage, one step at a time. The rules protect prices, items, customer IDs and
addresses by granting rider writes only to riderId, status and updatedAt.
DELIVERED is saved before the reservation is released; if interrupted, the
dashboard releases it on reopening. Seller completion controls remain hidden.

Going offline pauses presence writes, publishes offline, then checks the
reservation. New reservations are denied once presence is offline; an existing
reservation blocks logout/ending the shift. Use one active device per rider UID;
the shared presence record is not a multi-device session coordinator.

## Buyer alerts

`BuyerOrderAlerts.kt` subscribes while MainActivity is visible and stops when it
is hidden. The initial snapshots establish a baseline without replaying old
notifications. Later forward status changes show a toast and, when permitted,
a local Android notification. Tapping it opens Orders.

There is NO background push delivery in this version. When the app is hidden,
force-stopped or closed, new alerts are not generated. Reopening shows the
current order status. Updates while another buyer activity covers MainActivity
are also outside the alert listener's lifetime.

The FCM service/registration and Cloud Functions client dependency are removed.
The old `functions` folder is retained only as retired source; do not deploy it.
`firebase.delivery.json` now contains database rules configuration only.

## Existing data and limits

Old assignments from the Cloud Functions design may have currentOrderId without
sellerId. Review those manually before using this flow: add the correct sellerId
for an active canonical order, or clear a confirmed obsolete reservation. Never
clear a rider's reservation while they own an unfinished order.

This is a small-project demo implementation. It reads the waiting queue on
active rider devices, chooses by pickup distance, and retries interrupted claims.
It does not offer an always-on dispatcher, exactly-once alerts, automatic
reassignment, or guaranteed global nearest-rider selection. Firebase free quotas
still apply. Use a trusted backend for a public production delivery service.

## Checks to perform later

Source was reviewed only; rules behavior and Android runtime behavior have not
been validated. Before wider use, verify two riders competing for one order,
one rider on two devices, interrupted reservation/claim/assignment, app reopening,
offline/claim races, skipped or unauthorized stage writes, buyer progress and
foreground alerts with notification permission both allowed and denied.
