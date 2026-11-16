# Rider assignment and buyer delivery updates

> Superseded by **SPARK_DELIVERY_SETUP.md**. The active apps now use Kotlin and
> Realtime Database directly. The Cloud Functions deployment instructions below
> describe the retired implementation and must not be followed for the Spark flow.

## Implemented flow

Checkout saves only the canonical Realtime Database order and buyer index.
The server assigns the nearest eligible rider immediately after order placement;
the seller does not need to mark anything ready. Eligible riders have an approved
role in `users/riders`, online presence with coordinates updated within two
minutes, and no current assignment. Distance is straight-line distance to the
restaurant's saved `lat`/`lng`, with no maximum radius. Orders wait in `PLACED`
when no rider is available and are reconsidered on online presence updates or
when a rider finishes another delivery. Oldest waiting orders are considered first.
Restaurant coordinates must be saved correctly before using dispatch.

| Database status | Rider action | Buyer sees |
| --- | --- | --- |
| PLACED | None | Finding your rider |
| ASSIGNED | Automatic assignment | Rider heading to restaurant |
| WAITING_AT_RESTAURANT | Waiting at Restaurant | Preparing your order |
| PICKED_UP | Confirm Pickup | On the way |
| IN_AREA | Arrived in Customer Area | Rider is in your area |
| DELIVERED | Confirm Delivered, then confirm handoff | Delivered |

Only the assigned approved rider can advance stages, one step at a time, through
the authenticated `advanceDelivery` callable. Server transactions update the
canonical order, delivery job, timestamps and rider assignment together. The
server clears the rider lock on delivery. `endRiderShift` checks the lock and
sets presence offline in one transaction, preventing concurrent assignment at
logout. Both apps observe the same order status. Seller completion controls are
removed so that they cannot bypass delivery stages.

The buyer's Orders tab displays a bordered tracker with connected, checked boxes.
Each reached phase stays checked; the current phase is highlighted. Buyer and
seller order lists subscribe to live updates and detach listeners when stopped.

## Buyer notifications

`notifyBuyerDeliveryStage` sends high-priority data messages for assignment,
restaurant waiting/preparation, pickup, arrival in the area, and delivery.
The Android messaging service displays these both while the app is open and
while it is in the background, subject to Android restrictions and notification
permission. Tapping a notification opens Orders. Data messages are checked
against the currently signed-in buyer so an old account's messages are ignored.
Multiple device tokens are stored under `buyerPushTokens/{buyerUid}/{tokenHash}`;
invalid tokens are removed by the server. Android uses a stable notification ID
per order and stage to replace repeated deliveries of the same update.

FCM delivery timing is best effort. Force-stopped apps, denied notifications,
offline devices and device power restrictions can prevent or delay alerts.
The live order record remains authoritative. Trigger retries can resend alerts;
this is not an exactly-once notification system. No rider push notification or
automatic reassignment of an offline busy rider is implemented in this change.

## Deployment required — not performed

No app, build, dependency installation, emulator or tests were run. The changes
were reviewed as source only. Existing external Cloud Functions were not inspected.
Review them for assignment/status writers before deploying this codebase to avoid
two independent dispatch systems. Existing Firestore delivery records are not
migrated; new checkout stops creating them. The old Firestore delivery screen is
legacy and is not used by the separate rider app.

Deployment preparation uses `firebase.delivery.json` rather than overwriting an
existing Firebase config. The functions codebase is named `delivery`. Functions
use the default `us-central1` region, matching the rider callable client and the
existing default Realtime Database URL. If changing regions, update trigger and
callable configuration and both rider callable clients together.

Before deployment, review existing PLACED orders: bringing a rider online will
also attempt to assign historical orders still in that state. Review the full
`database-v2.rules.json` against the currently deployed database schema. It
contains the prior v2 migration rules as well as the new buyer token permissions
and disables direct seller status writes. It must not be published against an
unmigrated database.

After source review and authorization, the setup commands are:

```powershell
cd C:\BiteFlight\functions
npm install
cd C:\BiteFlight
firebase deploy --project biteflight-e6426 --config firebase.delivery.json --only functions:delivery,database
```

Cloud Functions deployment requires a Firebase project with the appropriate
billing plan and deployment permissions. Updated buyer and rider APKs must also
be built and installed. Deploy the backend before switching clients to this flow.

## Verification to perform when execution is allowed

Use a separate Firebase project/emulator before live deployment. Check no-rider
queueing, nearest fresh approved rider selection, simultaneous orders competing
for one rider, concurrent offline/assignment, unauthorized/skipped stage requests,
callable retries, live buyer progress, foreground/background notifications,
permission denial, account switching and delivery releasing the rider lock.

The server transactions currently span the database root so that orders, jobs,
presence and assignments commit together. This reads the entire database and
contends with unrelated writes, including GPS updates. It is suitable only for
this small prototype; move dispatch/order state into a shared dedicated subtree
and use targeted transactions before scaling. No load or runtime validation has
been performed.

Firebase references:
- https://firebase.google.com/docs/database/extend-with-functions
- https://firebase.google.com/docs/cloud-messaging/android/receive
