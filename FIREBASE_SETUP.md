# Firebase setup — free Spark plan

This version uses Firebase Authentication and Realtime Database. It does not use
Cloud Functions or require enabling Blaze billing. Keep the Firebase project on
Spark and stay within its no-cost quotas. Existing photo uploads still use AWS S3;
AWS billing is separate and has not been changed or verified here.

No builds, tests, emulator sessions, dependency installations, migration scripts or
deployments were run. Source changes and rules still need verification before release.

## Correct project

The Android configuration points to **biteflight-e6426**. The email names
**my-application-4fc23-default-rtdb**, which is a different project/database.
These edits do not change the Android Firebase connection. Use the project the app
actually connects to. Updating this project will not resolve warnings for the other one.

## What to do next

1. Back up existing Realtime Database data and rules in Firebase Console.
2. Publish the complete `database.rules.json` under Realtime Database → Rules in
   the correct project, coordinated with installing the updated Android app.
   Older app versions read all private profiles and are incompatible with the new rules.
   There is no Cloud Functions deployment or billing upgrade step.
3. Sign in to each existing seller account and open its dashboard once. The app
   creates the restaurant's public record if missing. New seller registrations
   create both records automatically. Until an existing seller does this (or you
   import its record), its restaurant will not appear on the buyer dashboard.
4. Alternatively, populate all existing restaurants using the optional offline
   migration below. Existing menu items and orders do not need migration.

If you independently deployed the previous version's `syncRestaurant` or
`placeOrder` functions, stop/remove them in Firebase Console when switching to
this version. The assistant did not deploy either function or enable billing.

## Optional bulk migration

This alternative requires Node locally but no installed packages, service account,
Cloud Functions, billing account or network connection for the conversion.

- Export only the `/users` node to a private JSON file outside this repository.
- Run yourself when ready:
  `node scripts/migrate-restaurants.js <absolute-users-export-path> <absolute-new-output-path>`
- Review the generated restaurant records, especially restaurant addresses.
- For a first-time migration, import that object at `/restaurants`, never at the
  database root. Pause seller edits during migration. If `/restaurants` already
  contains active records, do not replace them with an old export; use the seller
  dashboard to create missing records instead.
- The converter only writes a new local output file and refuses to overwrite it.
  It does not upload anything. Keep private profile exports out of Git.

## Data access

- `/users/{uid}` is readable/writable only by its owner. Roles are immutable after
  registration. Buyer/seller self-registration remains supported.
- `/restaurants/{uid}` contains restaurant identity, name, address, image, hours
  and availability. Only that seller can write it. Authenticated buyers can read it.
  Email, phone and structured delivery-address fields cannot be stored here by the app.
  A seller's `address` is their public restaurant address; do not put a private
  home address there unless it is intended to be visible to buyers.
- Profile and availability saves update both records atomically. Public records
  are created if missing without overwriting already-published details.
- Buyer/seller order queries are restricted to their own IDs. Reading all orders
  is denied. Sellers may change only status, following the transitions in the rules.
- Sellers can create, edit or delete only their own menu items.

## Order validation without paid functions

Checkout writes directly to Realtime Database. Rules require an authenticated
buyer, the correct seller, a non-busy public restaurant record, a server timestamp,
initial PLACED status, 1–100 item slots and integer quantities from 1–99 per slot.
Each item's name, price and seller must match the current stored menu. Changes
between viewing the menu and ordering can therefore cause checkout to be rejected;
the buyer should refresh the cart. Existing orders cannot have their items edited.

New orders omit `total`. The buyer and seller screens calculate totals from the
validated item prices and quantities, rounded to two decimal places, instead of
trusting an aggregate supplied by a buyer. Legacy totals remain in the database
but the updated screens derive totals from their original item details too.
Old orders were created under older rules; publishing new rules does not retroactively
validate those old prices or quantities.

Opening/closing hours are checked by the Android app. The rules enforce the busy
switch but do not enforce timezone-aware daily hours against a modified client.
This is a limitation of this implementation. No payment processing, scheduled
server tasks or automatic refunds are added.

A checkout retains the same order ID when retried or restored from saved state.
A transaction cannot overwrite an existing order. If you leave checkout after an
uncertain connection result, check My Orders before starting a new checkout, which
has a different ID. A pending offline operation may finish later.

## References

- Spark plan: https://firebase.google.com/docs/projects/billing/firebase-pricing-plans
- Database quota/billing: https://firebase.google.com/docs/database/usage/billing
- Rules reference: https://firebase.google.com/docs/reference/security/database
