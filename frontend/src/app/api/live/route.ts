import { getSession } from "@/lib/session";

export const dynamic = "force-dynamic";

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";

/**
 * Same-origin SSE endpoint for the browser. The Next.js server opens the gateway stream with the
 * user's token (which never reaches the browser) and pipes the events through unchanged.
 * The token is checked when the stream opens; the connection is closed after 30 minutes at the latest
 * (server timeout), and the browser's reconnect goes through middleware, which refreshes the token.
 */
export async function GET(req: Request) {
  const session = await getSession();
  if (!session) return new Response("Not signed in", { status: 401 });

  let upstream: Response;
  try {
    upstream = await fetch(`${GATEWAY}/api/v1/alerts/stream`, {
      headers: { Accept: "text/event-stream", Authorization: `Bearer ${session.accessToken}` },
      signal: req.signal, // browser closes the tab -> close the gateway connection too
      cache: "no-store",
    });
  } catch {
    return new Response("Gateway unreachable", { status: 502 });
  }
  if (!upstream.ok || !upstream.body) {
    return new Response(`Gateway answered ${upstream.status}`, { status: upstream.status === 200 ? 502 : upstream.status });
  }
  return new Response(upstream.body, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
    },
  });
}
