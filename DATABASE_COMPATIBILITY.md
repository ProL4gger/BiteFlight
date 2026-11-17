# Database compatibility

The Android buyer/seller app uses these paths from the supplied Realtime Database rules:

- Private profiles: users/buyers/{uid} and users/sellers/{uid}.
- Public restaurant profiles: restaurants/{sellerId}.
- Menus: menuItems/{sellerId}/{mealId}.
- Orders: orders/{sellerId}/{orderId}.
- Buyer history index: buyerOrders/{buyerId}/{orderId}, containing the seller ID.

Checkout reserves stock in a transaction on the seller menu, then atomically creates the order and its buyer history index. Failed order writes attempt stock compensation. New orders contain an empty riderId and updatedAt = 0 as required by the creation validators. This remains a client checkout with compensation, not a server-side atomic stock/order transaction.

Seller registration and login publish the public restaurant profile. Profile and availability edits synchronize private and public fields. Buyers never read another user's private profile. Login looks up only the signed-in user's individual role profiles; missing profiles are reported rather than silently creating a buyer account.

The supplied rules do not authorize seller status updates. Order details remain available, with the status action disabled; authorized riders manage delivery transitions.

Existing flat records require migration by a trusted administrator: move profiles into their role buckets, group menus/orders under seller IDs, populate buyerOrders, and publish restaurants. The client cannot read or migrate legacy paths with these rules. No remote data or deployed rules were changed.

This repository has no rider implementation and no restaurant pickup-coordinate fields. It does not create deliveryQueue entries or rider assignments. Integrating rider dispatch requires real pickup coordinates and the rider app/backend; do not substitute fabricated coordinates. delivery/jobs is read-only under the supplied rules.

Verification: git diff --check passes. Compilation/unit tests could not run because Gradle 9.6.0 was not cached, network access failed, and the download permission request was declined. Firebase emulator integration was not run.
