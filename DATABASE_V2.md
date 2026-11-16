# BiteFlight database layout v2

The Android source in this checkout now targets these paths in the existing
Realtime Database instance. Firebase Authentication remains unchanged.

| Path | Contents |
| --- | --- |
| `users/buyers/{uid}` | Private buyer profile, including delivery address |
| `users/sellers/{uid}` | Private seller profile |
| `users/riders/{uid}` | Private rider or pending rider profile |
| `restaurants/{sellerUid}` | Public restaurant discovery and availability fields |
| `menuItems/{sellerUid}/{mealId}` | Restaurant menu items and stock |
| `orders/{sellerUid}/{orderId}` | Restaurant orders; each is the canonical order record |
| `buyerOrders/{buyerUid}/{orderId}` | Restaurant UID used to find the canonical order |

Restaurant keys use the seller's Firebase Authentication UID, not the restaurant
name, so names can change without changing record paths. This app currently has
one restaurant per seller account.

## Local implementation

- Registration writes to the matching account group. Seller registration also
  publishes the public restaurant record in the same atomic database update.
- Profile loading reads only the signed-in UID in each account group. Missing
  profiles produce an explicit error; a seller or rider is never guessed to be a
  buyer. Profiles missing from the original database must be restored manually.
- Private profile updates, availability settings, and public restaurant fields
  use the new paths.
- The active checkout and login screens in `C:\BiteFlight` use the grouped
  paths. Restaurant latitude and longitude are published with public restaurant
  details for delivery-fee calculation and included in the offline conversion.
- The rider module in `C:\BiteFlight\rider` registers profiles with `uid` and
  `pending_rider` under `users/riders/{uid}` and verifies roles there on login.
  Failed profile saves attempt to roll back the newly created Auth account and
  show a recovery message if rollback fails. Presence and delivery paths stay
  unchanged.
- Menu creation, editing, deletion, cart stock checks, and checkout use a
  restaurant-specific menu branch.
- Checkout writes the order and buyer index together with `updateChildren`.
- Buyer order history resolves the index to the canonical restaurant orders, so
  status updates do not require copying entire orders.
- Seller order lists and completion updates use the restaurant order branch.

## Files to use

`database-v2.rules.json` is the complete replacement rules file. It is not
published automatically. The rider presence role lookup targets
`users/riders/{uid}`. Delivery job and FCM token paths retain their original layout.

The menu branch retains the existing authenticated-client write access because
checkout reserves stock using client transactions. This is not a complete
production security redesign: clients can still alter menu data, and order
prices are not authoritatively calculated on a server. Move checkout and stock
reservation to a trusted backend before relying on this for real payments.

## Coordinated migration

Do not publish these rules against the old data and expect existing clients to
continue working. Old clients use incompatible paths. Coordinate the data import,
rules publication, and release of every affected client during a maintenance
window with writes paused.

1. Export the ENTIRE Realtime Database from its root, including unrelated nodes.
   Keep the original export unchanged as your backup. Also retain a copy of the
   currently published rules. Keep exports outside the repository.
2. In PowerShell 7, convert the export offline, using fresh output filenames:

   ```powershell
   pwsh -File .\scripts\migrate-database-v2.ps1 -InputPath C:\Backups\biteflight-before.json -OutputPath C:\Backups\biteflight-v2.json
   ```

   The script has no network access code. It does not upload anything or modify
   the source export. It refuses existing output files, unknown roles, duplicate
   accounts, missing restaurant owners, and conflicting record IDs. It accepts
   flat or already grouped account/menu/order branches. Unknown-role accounts
   (such as an admin profile) need an explicit migration design before proceeding.
3. Review the generated JSON. Check each account group, restaurant menu, order,
   and buyer index. Preserve all rider profile fields. Existing grouped menus
   and orders must have seller IDs matching their parent restaurant IDs.
4. Validate the migration and rules in a separate Firebase project before the
   maintenance window. No emulator, build, app, or tests were run by the assistant
   because the user requested no execution.
5. Update any admin app, Cloud Functions, scripts,
   or other services before the cutover:
   - rider profile reads/writes: `users/riders/{uid}`;
   - rider registration includes `uid` equal to the authenticated UID and role
     `pending_rider`;
   - buyer/seller lookups use their account groups;
   - orders use `orders/{sellerUid}/{orderId}`;
   - any service creating orders must also maintain `buyerOrders`.
   The local rider module is updated; external clients/services have not been
   modified. Existing rider approval must occur through a trusted admin
   service or Firebase Console; clients cannot change their own roles.
6. During the write pause, import the reviewed full export at the database root,
   publish the COMPLETE contents of `database-v2.rules.json`, and release the
   updated clients together. Do not delete Authentication accounts to migrate
   profiles: the original UIDs are preserved.
7. Verify buyer and seller registration, sign-in, restaurant discovery, menu
   edits, stock reservation, buyer/seller history, order completion, and rider
   registration/presence before allowing normal use. For rollback, restore the
   original data, rules, and client versions together while writes are paused.

Publishing rules alone does not rearrange data. Android source edits alone do
not change the currently installed APK or the live database.

Reference: Firebase's Android read/write documentation describes atomic
multi-location updates:
https://firebase.google.com/docs/database/android/read-and-write
