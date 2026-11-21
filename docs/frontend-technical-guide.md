# Sentinel console — frontend technical guide

How the analyst console (`frontend/`) is built, and how to change it safely. For how to *use* the console, see [console-user-guide.md](console-user-guide.md). For the decisions behind it, see [ADR-007](ADR/ADR-007-phase-7-analyst-console-and-edge-limits.md) and [ADR-008](ADR/ADR-008-phase-7b-cases-audit-pii-and-console-redesign.md).

---

## 1. Stack

| | Version | Role |
|---|---|---|
| Next.js (App Router) | 15.5 | Pages, server components, server actions, route handlers, middleware, standalone build |
| React | 19 | UI |
| next-auth | 4 | Keycloak login (authorization code + PKCE) and the encrypted session cookie |
| Redux Toolkit + react-redux | 2.14 / 9.3 | Client-side state: live stream status, notifications (toasts), theme, mobile drawer |
| Tailwind CSS | 4 | Styling, on top of CSS-variable design tokens in `globals.css` |
| TypeScript | 5.9 | Strict types; API types in `src/lib/api.ts` |
| Vitest | 5 | Unit tests (`*.test.ts`) |

---

## 2. Architecture

```
Browser ──(cookie)──▶ Next.js server ──(Bearer token)──▶ API gateway :8080 ──▶ services
   ▲                     │  middleware: refresh token
   │  HTML + RSC         │  server components: fetch data
   │  /api/live (SSE) ◀──┘  server actions: mutations
```

**The rule that shapes everything: the browser never holds a token.** Keycloak tokens live only in next-auth's encrypted, httpOnly cookie. The Next.js server decrypts the cookie and calls the gateway with the user's own token. So:

- **Reads** happen in **Server Components**. Each page is `async` and calls `api<T>(path)` (in `src/lib/api.ts`) while rendering.
- **Writes** happen in **Server Actions** (`actions.ts` next to each page). They call `api(...)`, then report the outcome with `setFlash`/`flashResult` (`lib/flash-server.ts`), a short-lived cookie. Setting a cookie makes Next.js re-render the page and root layout in place with fresh data, and `FlashToasts` shows the message as a notification (§6a). Actions only `redirect()` when the result lives on another page (opening a case).
- **Live updates** go through `/api/live`, a route handler that opens the gateway's SSE stream with the user's token and pipes the events to the browser.
- The browser calls nothing directly. That is also why server data is **not** in Redux: a client store couldn't fetch it without exposing the token. See §5.

---

## 3. Folder map

```
frontend/
├── Dockerfile                 # 3-stage node:22-alpine build → standalone server.js, healthcheck on /login
├── .env.example               # all settings (copy to .env.local)
└── src/
    ├── middleware.ts          # auth gate + token refresh for every page and /api/live
    ├── app/                   # routes (App Router)
    │   ├── layout.tsx         # root: fonts, theme cookie → <html data-theme>, StoreProvider, LiveFeed, Sidebar, Topbar
    │   ├── page.tsx           # Overview
    │   ├── alerts/            # queue; [id]/ detail + actions.ts (changeStatus, openCase, addToCase)
    │   ├── cases/             # list; [id]/ detail + actions.ts (status, assign, owner/priority, notes, add alert)
    │   ├── customers/         # lookup, RevealNationalId (client), actions.ts (reveal, changeRating)
    │   ├── audit/             # log + actions.ts (verifyChain), SENIOR_ANALYST+
    │   ├── rules/             # list; [code]/ detail + edit (ADMIN), actions.ts
    │   ├── ops/               # dead letters (ADMIN), actions.ts (replay, delete)
    │   ├── simulate/          # Simulation (ADMIN): ScenarioGrid, CsvUpload (client); actions.ts (runScenario, sendTransaction, uploadCsv, batchStatus, seedCustomers)
    │   ├── login/             # public sign-in page
    │   ├── api/auth/[...nextauth]/  # next-auth endpoints
    │   ├── api/live/          # SSE proxy to the gateway
    │   ├── favicon.ico, icon.svg, apple-icon.png   # from brand/favicon (Next adds the <link> tags)
    │   ├── loading.tsx        # skeletons: one per route (alerts/, alerts/[id]/, cases/, … each has its own)
    │   ├── not-found.tsx      # 404 (alerts/[id]/ and cases/[id]/ have their own)
    │   ├── error.tsx          # a page threw: error page inside the layout, with retry
    │   └── global-error.tsx   # the root layout itself threw
    ├── components/
    │   ├── ui.tsx             # design-system pieces: badges, RiskScore, PageHeader, ErrorPanel (with retry), EmptyState, StatTile, KeyValue, Pager…
    │   ├── Icon.tsx           # inline SVG icon set + Logo
    │   ├── Sidebar.tsx        # navigation, role-aware; mobile drawer state in Redux
    │   ├── Topbar.tsx         # breadcrumbs + LiveStatus
    │   ├── LiveAlerts.tsx     # LiveFeed (SSE → store, notices) and LiveStatus (pill)
    │   ├── Toaster.tsx        # Toaster (renders the toast slice) and FlashToasts (flash cookie → toast)
    │   ├── SubmitButton.tsx   # submit button with pending spinner (useFormStatus); Spinner
    │   ├── RetryButton.tsx    # router.refresh() in a transition; used by ErrorPanel and error pages
    │   ├── Skeleton.tsx       # skeleton building blocks for loading.tsx files
    │   ├── StatusPage.tsx     # animated 404 / error layout
    │   └── ThemeToggle.tsx    # system / light / dark, via Redux
    ├── store/                 # Redux Toolkit (see §5)
    └── lib/
        ├── api.ts             # api<T>() + every API type (AlertSummary, CaseDetail, …)
        ├── auth.ts            # next-auth options (Keycloak provider, callbacks)
        ├── flash.ts           # flash cookie name, type, encode/decode (tested); shared by server and client
        ├── flash-server.ts    # setFlash / flashResult for server actions
        ├── simulation.ts      # demo scenarios (one per rule), demo customers/accounts, CSV writer (tested)
        ├── session.ts         # getSession() for server code, keycloakLogoutUrl()
        ├── tokens.ts          # JWT claims, roles, refresh, cookie chunk handling (tested)
        └── format.ts          # labels, INR/date formatting, risk severity (tested)
```

---

## 4. Authentication and authorisation

1. **Login.** `/login` → **Continue with Keycloak** → `signIn("keycloak")`. next-auth runs authorization code + PKCE against the client `sentinel-console` and stores `{ accessToken, refreshToken, expiresAt, username, roles }` in its encrypted cookie (`lib/tokens.ts#toSessionToken`). The session object that reaches the browser contains no token.
2. **Every request** goes through `middleware.ts`, which matches everything except `/api/auth`, `/login`, static files and the icons:
   - No session: pages redirect to `/login?callbackUrl=…`, and `/api/*` answers 401.
   - Token expiring within 30 s: it refreshes at Keycloak and rewrites the cookie **on both the response and the request**, so server components in this same request already use the new token.
   - Refresh refused, or a cookie that can't be decrypted: it clears the cookie and sends the user to `/login`.
3. **Roles** come from the access token's `realm_access.roles`. Use `hasRole(session, "SENIOR_ANALYST")` from `lib/tokens.ts`. In Keycloak, `ADMIN` is a composite role that includes the others.
4. **The UI only hides features; the API enforces them.** Hiding a button by role is for convenience. The gateway and services check roles on every call, and a 403 becomes "Your role does not allow this." through `api()`.
5. **Logout** calls `signOut({ callbackUrl: keycloakLogoutUrl() })`, which ends the Keycloak session too.

---

## 5. State management (Redux Toolkit)

### What goes where

| State | Where it lives | Why |
|---|---|---|
| Server data (alerts, cases, customers, rules, audit, stats) | **Server Components**, fetched per request | Needs the user's token, which only the server has. Always fresh, with no client cache to invalidate |
| Results of a mutation | **Server Action → flash cookie → toast** | The cookie change re-renders the page in place with fresh data; the layout hands the message to `FlashToasts` |
| Form inputs | The form itself (uncontrolled inputs, `FormData`) | No state to sync |
| Live stream status | **Redux, `live` slice** | Written by `LiveFeed`, read by the pill in the top bar and in the mobile bar |
| Notifications (action results, errors, live alert changes) | **Redux, `toasts` slice** | Raised from several places (`FlashToasts`, `LiveFeed`, any client component), rendered by one `Toaster` |
| Theme, mobile drawer | **Redux, `ui` slice** | App-wide UI state. The theme has side effects (DOM + cookie) that belong in middleware, not in components |
| Short-lived local UI (reveal form open, login busy) | `useState` in the component | Nobody else reads it. Putting it in a global store adds code and no value |

### Files

```
src/store/
├── store.ts          # makeStore(preloaded): combineSlices + listener middleware; RootState, AppStore, AppDispatch
├── liveSlice.ts      # { connected }: streamOpened, streamLost; selectConnected
├── toastSlice.ts     # { items[] }: toastShown, toastDismissed (starts exit), toastRemoved; selectToasts; DEFAULT_MS per tone
├── uiSlice.ts        # { theme, navOpen }: themeChosen, navOpened, navClosed; selectors
├── hooks.ts          # useAppDispatch, useAppSelector (typed)
├── StoreProvider.tsx # client provider: one store per tab, preloaded with the theme cookie
└── store.test.ts     # reducer tests
```

### Data flow

```
EventSource("/api/live")           flash cookie after a server action    Sidebar ☰ / route change   ThemeToggle
   │ connected / error / alert              │                                   │                          │
   ▼                                        ▼                                   ▼                          ▼
streamOpened | streamLost | toastShown  FlashToasts: toastShown, del cookie  navOpened | navClosed      themeChosen
   │               │                        │                                   │                          │
   ▼               ▼                        ▼                                   ▼                          ▼
live ─▶ LiveStatus  toasts ─▶ Toaster ◀─────┘                                 ui.navOpen ─▶ drawer       ui.theme ─▶ ThemeToggle
                    countdown ends (CSS) → toastDismissed → exit ends → toastRemoved                     listener: data-theme + cookie
LiveFeed: router.refresh() 500 ms after the last change (debounced), so server data re-renders
```

### Conventions

- **Store per tab, never a module singleton.** `makeStore()` is called once in `StoreProvider` (in a `useRef`). Next.js renders client components on the server too, and a module-level store would be shared between users' requests.
- **Preload from the server.** The root layout reads the `sentinel-theme` cookie and passes it to `StoreProvider`, so the first render already has the right theme and the page doesn't flash.
- **Reducers are pure.** Generated values (the toast `id` from `nanoid`) are made in the action's `prepare` callback. DOM and cookie side effects live in listeners in `store.ts`. A toast's timer is a CSS animation (so hover can pause it), and its `animationend` dispatches the next action.
- **Use the typed hooks** (`useAppSelector`, `useAppDispatch`) and the slice selectors (`selectToasts`, `selectTheme`, …), not raw `useSelector` or inline state paths.
- **Name actions as events** (`alertChanged`, `themeChosen`), not setters.
- **Adding state:** add it only if several components share it or it has side effects. Otherwise keep it in `useState`. A new slice goes into `combineSlices(...)` in `store.ts`, and a test goes in `store.test.ts`.
- **Don't add RTK Query for gateway data.** The browser can't call the gateway (no token). If client-side fetching is ever needed, add a Next.js route handler that proxies with the session (like `/api/live`) and point RTK Query at that.

---

## 6. Live alerts (SSE)

- alert-case-service publishes `AlertChangedEvent` after each commit. Every pod streams it from `GET /api/v1/alerts/stream`, with a 25 s heartbeat.
- `app/api/live/route.ts` opens that stream with the user's token and pipes it through unchanged. When the browser closes, `req.signal` aborts the upstream connection.
- `LiveFeed` (mounted once in `layout.tsx`) listens for `connected`, `alert` and `error` events. `EventSource` reconnects by itself. A reconnect passes through middleware, which refreshes an expiring token.
- Each event dispatches `alertChanged` and schedules `router.refresh()`, debounced to 500 ms so a CSV batch causes one refresh.
- The notices render at layout level, so they work on every screen size. The pill (`LiveStatus`) appears in the desktop top bar and in the mobile bar.

---

## 6a. Notifications, loading and error states

**Notifications.** Use `dispatch(toastShown({ tone, title, message?, href?, duration? }))` from any client component.
- `tone` is `success | danger | warn | info | brand`. The default duration is 5 s for success and up to 10 s for danger; `duration: 0` keeps it until closed.
- At most 4 are shown, newest first: top right on desktop, a bottom snackbar stack on phones. `danger` uses `role="alert"`, the rest `role="status"`.
- The countdown bar is `.toast-timer`, a CSS animation (`--toast-ms`) that pauses on hover or focus. With reduced motion it still counts down, but invisibly.
- Server actions don't dispatch. They call `setFlash(tone, text)` or `flashResult(err, successText)`, which writes the `sentinel-flash` cookie (base64url JSON with a unique `id`, 60 s). Next.js re-renders the page and root layout. The layout decodes the cookie (`lib/flash.ts`, which ignores anything malformed) and passes it to `FlashToasts`, which dispatches once per `id` and deletes the cookie.
- **Why not `?msg=` in the URL** (the previous design): redirecting a page to itself with new search parameters re-mounts its `loading.tsx` boundary. That flashed the whole skeleton after every click, and with Next 15.5 / React 19.3 the redirect could leave the action stuck pending. With the cookie, the URL never changes and the page updates in place.

**Loading.**
- Every route has a `loading.tsx` built from `components/Skeleton.tsx` in the shape of its page. It fades in after 120 ms (`.loading-in`), so fast pages don't flash it.
- Action buttons are `<SubmitButton>` (`useFormStatus`): while the action runs, all submit buttons in the form are disabled and the clicked one shows a spinner. Pass `icon` so the spinner replaces it.

**Errors.**
- An expected API failure (gateway down, 403, 429) is caught by the page and shown inline with `<ErrorPanel>`, which has a **Try again** button (`RetryButton` → `router.refresh()`).
- A missing record calls `notFound()`: the alert and case pages do this on API 404 or 400, and render `alerts/[id]/not-found.tsx` or `cases/[id]/not-found.tsx`. Any other unknown URL gets `app/not-found.tsx`.
- An unexpected exception while rendering goes to `app/error.tsx`, inside the layout, with retry (`reset()` + refresh in one transition) and the error `digest` as a support reference. If the root layout itself fails, `app/global-error.tsx` takes over.
- All of them use `<StatusPage>`: an animated transaction signal (a gap for "missing", a flagged spike for "error") with a scanning sweep.

**Motion** (`globals.css`, *motion* section): `rise` (page content, staggered), `toast-in`, `toast-in-up` and `toast-out`, `countdown`, `spin`, `shimmer` (skeletons), and `draw`, `sweep` and `float` (illustrations). `prefers-reduced-motion` collapses all of them.

## 6b. Simulation page

`/simulate` (ADMIN) stands in for the bank's systems, so demos don't need Postman. It calls only the public API, with the admin's token: customer-service to create customers and accounts, ingestion-service for transactions (`Idempotency-Key`: a new UUID per transaction) and CSV batches, and detection-service's `baseline/recompute`.

- **Scenarios** live in `lib/simulation.ts` as pure functions: `build(accountId, now)` returns `{ history, live }`. `simulation.test.ts` checks each one against its rule's *default* settings (detection-service `V4__rule_params…`), and checks that it trips **only** its own rule. If you change a rule's defaults in a migration, update both.
- `runScenario` returns its result to the card through `useActionState` (shown inline, not as a toast): the new account, with links to its alerts and customer. The behavioural scenario sends backdated history, waits 4 s for detection to record it, recomputes baselines, then sends today's spike.
- **CSV upload** goes through a server action. `api()` leaves the `Content-Type` to `fetch` when the body is `FormData`, and `next.config.ts` raises `serverActions.bodySizeLimit` to 50 MB to match the gateway. `CsvUpload` then polls the `batchStatus` server action every 1.5 s until the batch isn't `PROCESSING` (for at most 5 minutes). The sample file is generated in the browser from the same scenario builders.

## 7. Optimistic concurrency

Alerts and cases carry a `version`. Forms send it as `expectedVersion`. If someone else changed the record first, the API answers **409**, the action shows the error as a notification, and the page re-renders the current state. Never drop `expectedVersion` from a mutation form.

---

## 8. Styling and design system

- **Tokens** are CSS variables in `src/app/globals.css` for light and dark: `--brand` (deep teal `#0f6b62`), `--ink`, `--muted`, `--line`, `--surface`, `--danger`, `--warn`, `--ok`, `--info` and so on, exposed to Tailwind as `bg-brand`, `text-muted`, etc. Colour carries meaning: red for high risk or destructive actions, amber for medium or warning, green for OK.
- **Theme:** `<html data-theme="light|dark">`, or no attribute to follow the OS through `prefers-color-scheme`.
- **Component classes** are in `globals.css`: `card`, `card-head`, `btn`, `btn-primary`, `btn-danger`, `btn-ghost`, `input`, `label`, `table`, `th`, `td`, `row`, `chip`, `eyebrow`.
- **Fonts:** IBM Plex Sans and IBM Plex Mono through `next/font` (self-hosted at build time). IDs, amounts and hashes use the mono font.
- **Reuse `components/ui.tsx`** before writing new markup: `PageHeader`, `StatusBadge`, `CaseStatusBadge`, `PriorityBadge`, `RatingBadge`, `RiskScore`, `ErrorPanel`, `EmptyState`, `StatTile`, `KeyValue`, `Pager`, `BackLink`.
- **Logo and favicons:** the `Logo` in `Icon.tsx` mirrors `brand/svg/sentinel-mark.svg`. The favicons are copied from `brand/favicon/`. See [brand/README.md](../brand/README.md).
- **Accessibility:** every input has a `<label>` (`sr-only` where it is visually implied), the live notices are `aria-live="polite"`, the current nav item has `aria-current="page"`, and the theme switch is a `radiogroup`.

---

## 9. Configuration

All settings are environment variables (see `frontend/.env.example`):

| Variable | Used by | Meaning |
|---|---|---|
| `NEXTAUTH_URL` | next-auth | Public URL of the console |
| `NEXTAUTH_SECRET` | next-auth, middleware | Encrypts the session cookie. Changing it signs everyone out |
| `KEYCLOAK_ISSUER` | auth, middleware | Public realm URL, which must match the token's `iss` |
| `KEYCLOAK_INTERNAL_ISSUER` | auth, middleware | Docker only: how the container reaches Keycloak |
| `KEYCLOAK_CLIENT_ID` / `KEYCLOAK_CLIENT_SECRET` | auth | The `sentinel-console` client |
| `GATEWAY_URL` | `api()`, `/api/live` | Gateway as seen by the Next.js server |
| `GATEWAY_PUBLIC_URL` | Sidebar | Gateway as seen by the browser (API reference link) |

---

## 10. Run, test, build

```bash
cd frontend && cp .env.example .env.local   # once; set NEXTAUTH_SECRET
```

```bash
npm run dev          # http://localhost:3000
```

```bash
npm run typecheck && npm test && npm run build
```

- **Docker:** `docker compose up -d --build` (from the repo root) starts the console with the rest of the stack, built from `frontend/Dockerfile` into a standalone `node server.js`. In the container it reaches the gateway as `http://gateway:8080` and Keycloak as `http://keycloak:8080` (`KEYCLOAK_INTERNAL_ISSUER`), while the browser keeps using `localhost`. Rebuild just the console with `docker compose up -d --build console`.
- **CI** (`.github/workflows/ci.yml`, job `console`): `npm ci` → typecheck → tests → production build → Docker image.
- **Tests** live next to the code: `lib/tokens.test.ts` (claims, roles, refresh, cookie chunks), `lib/format.test.ts`, `lib/flash.test.ts` (cookie round trip, tampered values), `lib/simulation.test.ts` (each scenario trips only its rule; IDs; CSV), `store/store.test.ts` (slices). Pages are covered by the manual guide and Postman/newman on the API side.

---

## 11. Recipes

**Add a page.**
1. Create `src/app/<name>/page.tsx` as an `async` Server Component that calls `api<T>()` in `try/catch` and shows `<ErrorPanel>` on error. Call `notFound()` when the record doesn't exist.
2. Add a `loading.tsx` next to it, built from `components/Skeleton.tsx` in the page's shape.
3. Add the response type to `lib/api.ts`.
4. Add a nav item in `Sidebar.tsx`, with a `show` condition by role, and a `SECTION` label in `Topbar.tsx`.
5. If the page is role-restricted, check `hasRole` at the top of the page as well. The API is still the real guard.

**Add a mutation.**
1. In `<page>/actions.ts` (`"use server"`), read `FormData`, call `api(path, { method, body })` inside `try/catch`, then `await flashResult(err, "Done.")`. Don't redirect back to the same page, and don't put messages in the URL. To go to another page, call `setFlash` first, then `redirect()` outside the `try` (it throws).
2. Use `<SubmitButton>` for the form's buttons, not a plain `<button>`.
3. Bind per-button arguments with `.bind(null, value)` and pass them as `formAction`, because a clicked button's value isn't reliably in the `FormData`.
4. Pass `expectedVersion` for versioned records.

**Add shared client state.** See §5 *Conventions*: add a slice, register it in `combineSlices`, put side effects in a listener, export selectors, and add a test.
