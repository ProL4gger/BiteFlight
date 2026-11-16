const {initializeApp} = require('firebase-admin/app');
const {getDatabase} = require('firebase-admin/database');
const {getMessaging} = require('firebase-admin/messaging');
const {onCall, HttpsError} = require('firebase-functions/v2/https');
const {onValueWritten} = require('firebase-functions/v2/database');
initializeApp();

const stages = ['ASSIGNED', 'WAITING_AT_RESTAURANT', 'PICKED_UP', 'IN_AREA', 'DELIVERED'];
const validKey = value => typeof value === 'string' && value.length > 0 &&
  value.length <= 128 && !/[.#$\[\]\/]/.test(value);
const approved = (root, uid) =>
  String(root.users?.riders?.[uid]?.role).toLowerCase() === 'rider';
const coordinates = point => point && Number.isFinite(point.latitude) &&
  Number.isFinite(point.longitude) && Math.abs(point.latitude) <= 90 &&
  Math.abs(point.longitude) <= 180;
function distance(a, b) {
  const rad = n => n * Math.PI / 180;
  const dLat = rad(b.latitude - a.latitude), dLng = rad(b.longitude - a.longitude);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.latitude)) *
    Math.cos(rad(b.latitude)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
}

async function atomicUpdate(update) {
  const ref = getDatabase().ref();
  const keepCached = () => {};
  ref.on('value', keepCached);
  try {
    // RTDB transactions can initially receive an empty local cache. Keep a live
    // snapshot until commit so an empty-cache abort cannot lose a dispatch.
    await ref.once('value');
    return await ref.transaction(update);
  } finally {
    ref.off('value', keepCached);
  }
}

// One server transaction keeps the canonical order, job and rider lock consistent.
// Root scope is intentional for this small database; see DELIVERY_SETUP.md for scale limits.
async function dispatch() {
  await atomicUpdate(root => {
    if (!root) return;
    const now = Date.now();
    root.delivery ||= {};
    root.delivery.riders ||= {};
    root.delivery.jobs ||= {};
    const waiting = Object.entries(root.orders || {}).flatMap(([sellerId, orders]) =>
      Object.entries(orders).filter(([, order]) => order.status === 'PLACED')
        .map(([orderId, order]) => ({sellerId, orderId, order})))
      .sort((a, b) => (a.order.createdAt || 0) - (b.order.createdAt || 0));
    let changed = false;
    for (const {sellerId, orderId, order} of waiting) {
      const restaurant = root.restaurants?.[sellerId];
      const pickup = {latitude: restaurant?.lat, longitude: restaurant?.lng};
      if (!coordinates(pickup) || root.delivery.jobs[orderId]) continue;
      const available = Object.entries(root.riderPresence || {}).filter(([uid, p]) =>
        approved(root, uid) && p.online === true && coordinates(p) &&
        Number.isFinite(p.updatedAt) && now - p.updatedAt <= 120000 &&
        p.updatedAt <= now + 30000 && !root.delivery.riders[uid]?.currentOrderId)
        .sort((a, b) => distance(a[1], pickup) - distance(b[1], pickup) || a[0].localeCompare(b[0]));
      if (!available.length) continue;
      const riderId = available[0][0];
      order.status = 'ASSIGNED';
      order.riderId = riderId;
      order.updatedAt = now;
      root.delivery.riders[riderId] = {...root.delivery.riders[riderId], currentOrderId: orderId};
      root.delivery.jobs[orderId] = {
        orderId, sellerId, buyerId: order.buyerId, riderId, status: 'ASSIGNED',
        restaurantName: order.restaurantName || restaurant.name || 'Restaurant',
        pickupAddress: restaurant.address || '', pickupLatitude: pickup.latitude,
        pickupLongitude: pickup.longitude, deliveryAddress: order.deliveryAddress || '',
        items: order.items || [], total: order.total || 0, updatedAt: now,
        stageTimes: {ASSIGNED: now}
      };
      changed = true;
    }
    return changed ? root : undefined;
  });
}

exports.advanceDelivery = onCall(async request => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Sign in first.');
  const {orderId, status} = request.data || {};
  if (!validKey(orderId) || !stages.slice(1).includes(status))
    throw new HttpsError('invalid-argument', 'Invalid delivery stage.');
  const result = await atomicUpdate(root => {
    if (!root || !approved(root, uid)) return;
    const job = root.delivery?.jobs?.[orderId];
    if (!job || job.riderId !== uid) return;
    // Safe retry after a lost callable response, including after completion.
    if (job.status === status) return root;
    const order = root.orders?.[job.sellerId]?.[orderId];
    if (!order || order.riderId !== uid || order.status !== job.status ||
        root.delivery?.riders?.[uid]?.currentOrderId !== orderId ||
        stages.indexOf(status) !== stages.indexOf(job.status) + 1) return;
    const now = Date.now();
    job.status = order.status = status;
    job.updatedAt = order.updatedAt = now;
    job.stageTimes ||= {};
    job.stageTimes[status] = now;
    if (status === 'DELIVERED') delete root.delivery.riders[uid].currentOrderId;
    return root;
  });
  if (!result.committed)
    throw new HttpsError('failed-precondition', 'Assignment or stage changed. Refresh the delivery.');
  await dispatch();
  return {ok: true};
});

exports.dispatchPlacedOrders = onValueWritten({ref: '/orders/{sellerId}/{orderId}/status', retry: true}, event => {
  if (event.data.after.val() === 'PLACED') return dispatch();
  return null;
});
exports.dispatchAvailableRiders = onValueWritten({ref: '/riderPresence/{riderId}', retry: true}, event => {
  if (event.data.after.val()?.online === true) return dispatch();
  return null;
});

exports.endRiderShift = onCall(async request => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Sign in first.');
  const result = await atomicUpdate(root => {
    if (!root || !approved(root, uid) || root.delivery?.riders?.[uid]?.currentOrderId) return;
    if (root.riderPresence?.[uid]) {
      root.riderPresence[uid].online = false;
      root.riderPresence[uid].updatedAt = Date.now();
    }
    return root;
  });
  if (!result.committed) throw new HttpsError('failed-precondition',
    'Finish your assigned delivery before going offline.');
  return {ok: true};
});

const notificationText = {
  ASSIGNED: ['Rider assigned', 'Your rider is heading to the restaurant.'],
  WAITING_AT_RESTAURANT: ['Preparing your order', 'Your rider is waiting at the restaurant while your order is prepared.'],
  PICKED_UP: ['Your order is on the way', 'Your rider has picked up your order and is on the way to you.'],
  IN_AREA: ['Your rider is in your area', 'Your order is nearby. Please be ready to receive it.'],
  DELIVERED: ['Order delivered', 'Your rider has marked your order as delivered. Enjoy your meal!']
};
exports.notifyBuyerDeliveryStage = onValueWritten({
  ref: '/delivery/jobs/{orderId}/status', retry: true
}, async event => {
  const status = event.data.after.val();
  if (event.data.before.val() === status || !notificationText[status]) return;
  const database = getDatabase();
  const job = (await database.ref(`delivery/jobs/${event.params.orderId}`).get()).val();
  if (!job?.buyerId) return;
  // A delayed trigger must not announce preparation after the order arrived.
  if (stages.indexOf(job.status) > stages.indexOf(status)) return;
  const ref = database.ref(`buyerPushTokens/${job.buyerId}`);
  const tokens = Object.entries((await ref.get()).val() || {});
  const [title, body] = notificationText[status];
  let retry = false;
  for (let i = 0; i < tokens.length; i += 500) {
    const batch = tokens.slice(i, i + 500);
    const result = await getMessaging().sendEachForMulticast({
      tokens: batch.map(([, token]) => token),
      data: {buyerId: job.buyerId, orderId: event.params.orderId, status, title, body},
      android: {priority: 'high', ttl: 60 * 60 * 1000}
    });
    await Promise.all(result.responses.map(async (response, index) => {
      if (response.success) return;
      if (['messaging/registration-token-not-registered', 'messaging/invalid-registration-token']
        .includes(response.error?.code)) await ref.child(batch[index][0]).remove();
      else retry = true;
    }));
  }
  if (retry) throw new Error('Some buyer notifications need retrying');
});
