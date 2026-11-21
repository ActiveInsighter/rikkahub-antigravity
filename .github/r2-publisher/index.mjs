const prefix = "antigravity-updates/";
const assetPath = /^builds\/[0-9]{1,10}\/([a-f0-9]{64})\/(?:arm64-v8a|x86_64|universal)(?:\.apk|-from-[a-f0-9]{64}-([a-f0-9]{64})\.hdiff)$/;
const encoder = new TextEncoder();
const noCache = { "Cache-Control": "no-store" };

export default {
  async fetch(request, env) {
    if (!env.PUBLISH_TOKEN) return new Response("Publisher unavailable", { status: 503 });
    const [provided, expected] = await Promise.all([
      crypto.subtle.digest("SHA-256", encoder.encode(request.headers.get("Authorization") ?? "")),
      crypto.subtle.digest("SHA-256", encoder.encode(`Bearer ${env.PUBLISH_TOKEN}`)),
    ]);
    if (!crypto.subtle.timingSafeEqual(provided, expected)) {
      return new Response("Unauthorized", { status: 401, headers: noCache });
    }

    const url = new URL(request.url);
    const path = url.pathname.slice(1);
    const catalog = path === "catalog.json";
    const asset = assetPath.exec(path);
    const key = prefix + path;
    const bucket = env.UPDATES;
    try {
      if (request.method === "GET" && path === "builds/") {
        const cursor = url.searchParams.get("cursor") ?? undefined;
        const listed = await bucket.list({ prefix: key, cursor });
        return Response.json({
          keys: listed.objects.map((object) => object.key),
          cursor: listed.truncated ? listed.cursor : null,
        }, { headers: noCache });
      }
      if (!catalog && !asset) return new Response("Not found", { status: 404 });

      if (request.method === "GET") {
        const object = await bucket.get(key);
        if (!object) return new Response("Not found", { status: 404 });
        return new Response(object.body, {
          headers: { ...noCache, ETag: object.httpEtag, "Content-Length": String(object.size) },
        });
      }
      if (request.method === "PUT") {
        const length = Number(request.headers.get("Content-Length"));
        if (!request.body || !Number.isSafeInteger(length) || length <= 0) {
          return new Response("A body with Content-Length is required", { status: 411 });
        }
        const condition = new Headers();
        if (catalog) {
          const etag = request.headers.get("If-Match");
          if (length > 1024 * 1024) return new Response("Catalog too large", { status: 413 });
          if (etag && /^"[^"]+"$/.test(etag)) {
            condition.set("If-Match", etag);
          } else if (request.headers.get("If-None-Match") === "*") {
            condition.set("If-None-Match", "*");
          } else {
            return new Response("Catalog write requires a precondition", { status: 428 });
          }
        }
        const checksum = asset && (asset[2] ?? asset[1]);
        const sha256 = checksum
          ? Uint8Array.from(checksum.match(/../g), (byte) => Number.parseInt(byte, 16)).buffer
          : undefined;
        const written = await bucket.put(key, request.body, {
          onlyIf: catalog ? condition : undefined,
          sha256,
          httpMetadata: {
            contentType: catalog ? "application/json"
              : path.endsWith(".apk") ? "application/vnd.android.package-archive" : "application/octet-stream",
            cacheControl: catalog ? "no-store" : "public, max-age=3600, immutable",
          },
        });
        return new Response(null, { status: written ? 204 : 412 });
      }
      if (request.method === "DELETE" && asset) {
        await bucket.delete(key);
        return new Response(null, { status: 204 });
      }
      return new Response("Method not allowed", {
        status: 405, headers: { Allow: catalog ? "GET, PUT" : "GET, PUT, DELETE" },
      });
    } catch (error) {
      console.error(JSON.stringify({ event: "apk_update_storage_failed", method: request.method, key, error: String(error) }));
      return new Response("R2 operation failed", { status: 502 });
    }
  },
};
