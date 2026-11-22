const keyPattern = /^[A-Za-z0-9_-]{1,128}$/;

function error(status, stage) {
  return new Response(null, {
    status,
    headers: stage ? { "X-BiteFlight-Relay-Error": stage } : undefined,
  });
}

async function fetchWithRetry(url) {
  for (const timeout of [4000, 7000]) {
    try {
      const response = await fetch(url, {
        redirect: "manual",
        signal: AbortSignal.timeout(timeout),
      });
      if (response.status < 500 || timeout === 7000) return response;
      await response.body?.cancel();
    } catch (failure) {
      if (timeout === 7000) throw failure;
    }
  }
}

export default {
  async fetch(request, env) {
    if (request.method !== "POST") return error(405);
    const authorization = request.headers.get("Authorization");
    if (!authorization?.startsWith("Bearer ")) return error(401);

    let recipient, callId;
    try {
      ({ recipient, callId } = await request.json());
    } catch {
      return error(400);
    }
    if (typeof recipient !== "string" || typeof callId !== "string" ||
        !keyPattern.test(recipient) || !keyPattern.test(callId)) return error(400);

    const database = env.FIREBASE_DATABASE_URL?.trim();
    const app = env.METERED_APP?.trim();
    const apiKey = env.METERED_TURN_API_KEY?.trim();
    if (!database || !/^https:\/\/[a-z0-9-]+\.(?:firebaseio\.com|[a-z0-9-]+\.firebasedatabase\.app)\/?$/.test(database) ||
        !app || !/^[a-z0-9-]+$/.test(app) || !apiKey) return error(503, "configuration");

    // Firebase validates the ID token and applies the call's participant read rules.
    const url = new URL(`/internetCalls/${recipient}/${callId}.json`, database);
    url.searchParams.set("auth", authorization.slice("Bearer ".length));
    let call;
    try {
      const record = await fetchWithRetry(url);
      if (record.status >= 300 && record.status < 400) return error(502, "firebase");
      if (!record.ok) return record.status >= 500 ? error(502, "firebase") : error(403, "call-access");
      call = await record.json();
    } catch {
      return error(502, "firebase");
    }
    if (!call || !["ringing", "accepted"].includes(call.state) ||
        typeof call.createdAt !== "number" || Date.now() - call.createdAt > 90000) return error(403, "call-access");

    const metered = new URL(`https://${app}.metered.live/api/v1/turn/credentials`);
    metered.searchParams.set("apiKey", apiKey);
    try {
      const relay = await fetchWithRetry(metered);
      if (!relay.ok) return error(502, "metered");
      const iceServers = await relay.json();
      if (!Array.isArray(iceServers) || !iceServers.some(server => {
        const urls = Array.isArray(server?.urls) ? server.urls : [server?.urls];
        return server?.username && server?.credential &&
          urls.some(url => typeof url === "string" && /^turns?:/.test(url));
      })) return error(502, "metered");
      return Response.json({ iceServers }, { headers: { "Cache-Control": "no-store" } });
    } catch {
      return error(502, "metered");
    }
  },
};
