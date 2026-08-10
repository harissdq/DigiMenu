# DigiMenu — Architectural Blueprint

A restaurant system with **one Android app** (the Manager Dashboard) and a
**browser-based customer page** (no customer app, no install). Customers scan a
printed table QR code and the menu opens in their phone browser. The system is
**multi-tenant**: one Firebase project serves many restaurants, and each manager
account is bound to exactly one restaurant. Every restaurant also exposes a
**public Take Away QR** so customers can order from home with a delivery
address.

## 1. System overview

```
┌──────────────────────────────┐
│  Manager Dashboard (:manager)│   Android app (com.digimenu.manager)
│  ┌──────────┬──────────────┐ │   installed on the restaurant's device
│  │ Login    │ Menu CRUD    │ │
│  │ (Auth)   │ + stock flag │ │
│  ├──────────┼──────────────┤ │
│  │ QR code  │ Order tracker│ │
│  │ generator│ (live feed)  │ │
│  │ Tables   │ Reports      │ │
│  └──────────┴──────────────┘ │
└──────────────┬───────────────┘
               │  QR encodes the web page URL: ?restaurant={id}&table={id}
               │  Take Away QR: ?restaurant={id}&takeaway=1
               ▼
┌──────────────────────────────┐
│  Kitchen display (web/)      │   static HTML/JS + Firebase JS SDK,
│  kitchen.html + kitchen.js   │   sign in as a manager, live queue,
│  NEW→ACCEPTED→PREPARING→READY│   audio alert on new orders
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  Customer web page (web/)    │   static HTML/CSS/JS + Firebase JS SDK
│  ┌────────────────────────┐  │   hosted on GitHub Pages
│  │ verify table           │  │
│  │ lead capture (+ addr)  │  │
│  │ live menu + cart       │  │
│  │ place order            │  │
│  │ take away ordering     │  │
│  │ live tracker + alerts  │  │
│  └────────────────────────┘  │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  Firebase Realtime Database  │   single backend, tenant-scoped
│  managers/{uid}/restaurantId │
│  restaurants/{id}/{info,menu,│
│   tables, orders, sessions}  │
└──────────────────────────────┘
```

- **Manager** (Android) pushes writes: menu, stock, tables, order status, sessions.
- **Kitchen display** (web) shows the live queue and advances orders
  (`NEW → ACCEPTED → PREPARING → READY`) — same nodes, same rules, same legal
  transitions as the Android app.
- **Customer** (web) reads menu/tables live and writes orders.
- **Shared Android code** (models, Firebase repositories, QR codec/generator,
  report aggregation) lives once in `:core`. The web app mirrors the same schema
  with the JS SDK.

## 2. Tech stack

| Layer        | Choice                                    | Why |
|--------------|-------------------------------------------|-----|
| Manager UI   | Jetpack Compose (Material 3)              | Fast to build, single toolchain, modern look |
| DI           | Hilt + KSP                                | Standard, compile-safe graph |
| Async        | Kotlin Coroutines + Flows                 | Repos expose `Flow` for real-time data |
| Customer UI  | Plain HTML/CSS/JS + Firebase JS SDK       | Zero install, works in every phone browser |
| Kitchen UI   | Plain HTML/JS + Firebase JS SDK (Auth+DB)  | Runs in any browser/tablet; manager sign-in |
| Backend      | Firebase Realtime Database                | Change events push to all subscribers instantly — the "no refresh" requirement |
| Auth         | Firebase Auth (email/password)            | Manager sign-in; authorisation via `managers/{uid}` node |
| QR *write*   | ZXing core (`QRCodeWriter`)               | Pure-JVM bitmap generation, no camera needed |
| Web hosting  | GitHub Pages (deployed by CI)             | Free static hosting; QR URL is the page URL |
| Build        | AGP 8.7, Kotlin 2.0, Gradle 8.9           | Matches existing project conventions |

### Why Realtime Database over Firestore
The requirement is "manager sees orders instantly **and** menu updates globally
the moment a price changes." Realtime Database delivers whole-node snapshots to
every connected client with a single listener — the simplest way to satisfy both
"instant order" and "global menu update". Firestore is better later if complex
queries are needed, but it is overkill for this schema.

## 3. Module architecture

```
:core      — shared Android library (no UI): models, FirebaseRefs, repositories,
             report aggregation (ReportStats), QR codec + generator, DI module
:manager   — the single Android app (Manager Dashboard)
web/       — customer page (index.html) + kitchen display (kitchen.html);
             static, independent of the Gradle build
```

The customer and kitchen interfaces are deliberately *not* Android modules:
customers should never install anything — the QR opens the web page directly,
and the kitchen display runs on any spare browser/tablet.

## 4. Data model (Firebase Realtime Database)

```
managers/{uid}/
  restaurantId                  -> tenant the account manages
restaurants/{restaurantId}/
  info/name                     -> RestaurantInfo
  menu/{itemId}/
    { id, name, description, price, category, available, photo, updatedAt }
  tables/{tableId}/
    { id, label, createdAt }
  sessions/{sessionId}/
    { id, tableId, status: OPEN|CLOSED, openedAt, closedAt,
      orders: { orderId: true }, paid, total }
  orders/{orderId}/
    { id, orderType, tableId, tableLabel, customerName, customerPhone,
      address, items: { itemId: { name, price, qty, category } },
      total, status, createdAt, statusChangedAt, declineReason? }
  managers/{uid} = true         -> legacy uid map (also seeded)
```

`orderType` is `dine-in` or `takeaway`. Take-away orders use the literal
`tableId` value `"TAKEAWAY"` and carry the customer's `address`.

**Table sessions & billing.** Every dine-in order is linked to its table's
session (`sessions/{sessionId}`, push-keyed so each table keeps its full
history), which the manager app opens automatically when the first order for the
table arrives (`SessionRepository.ensureOpen`) and closes when the guests pay. A
closed-but-unpaid session is an open bill (the "Tables" screen shows its live
total and lets the manager settle it); marking it paid archives it. The bill
total is the sum of the session's orders excluding `CANCELLED`/`REJECTED`.
Take-away orders never get sessions.

Real-time propagation:
- **Menu**: manager's `MenuRepository.observeMenu()` and the web page's
  `db.ref('.../menu').on('value')` both subscribe to `menu/`. A
  `setAvailability(false)` write by the manager updates every open browser menu
  immediately.
- **Orders**: web checkout writes to `orders/`; the manager's `observeOrders()`
  gets the child the instant it lands.
- **Status**: the manager app and the kitchen display advance an order through a
  validated state machine (`OrderStatus` in `core/.../model/OrderStatus.kt`):
  `NEW → ACCEPTED → PREPARING → READY → DONE`, or off-path to
  `REJECTED` (with `declineReason`) / `CANCELLED`. Every transition is a single
  atomic write of `status` + `statusChangedAt`. The customer's confirmation page
  subscribes to `orders/{orderId}` and renders the same timeline live.

## 4b. Kitchen display (P5)

`web/kitchen.html` is a browser page for the cooking line. Because orders are
manager/tenant-scoped in the rules, a staff member signs in with the
restaurant's **manager account** (email/password via the Firebase JS SDK), the
page resolves `managers/{uid}/restaurantId`, and subscribes to
`restaurants/{id}/orders`. It renders three queues — **To prepare**
(`NEW`/`ACCEPTED`), **Preparing**, **Ready** — oldest first, plays a beep when a
new order arrives, and offers one legal action per state:
`Accept order` (NEW→ACCEPTED) → `Start preparing` (ACCEPTED→PREPARING) →
`Ready` (PREPARING→READY). Terminal orders are dropped from the queue. This is
purely a client; no new backend nodes or rules are required.

## 4c. Customer notifications (P6)

The web app has no push backend, so the customer is alerted **in-page** while
the confirmation screen is open: when the live tracker observes a status change
it plays a beep, updates `document.title`, and shows a dismissible banner for the
action states (READY / DONE / REJECTED / CANCELLED), plus a *Place another
order* button to start over. "Push" (FCM) or WhatsApp/SMS alerts would require a
server/notification provider and are documented as a future extension — the
schema and tracker already expose everything needed to trigger them.

## 4d. Reports (P7)

`ReportsScreen` (a new manager tab) aggregates the live orders stream per
period — **Today / 7 days / 30 days / All time** — using the pure
`ReportStats.aggregate()` function in `core` (no new writes). It shows revenue,
average order value, order/dine-in/take-away counts, cancelled vs completed,
sales by category and top items, and can copy a **CSV** export to the
clipboard. Reporting relies on `OrderLine.category`, which the customer page now
writes when placing an order.

## 5. QR → restaurant & table mapping

Payload format (canonical): the web page URL with the restaurant id and the
table id as query parameters:

```
https://harissdq.github.io/DigiMenu/?restaurant=demo-restaurant&table=Table_1
```

Take-away QR (restaurant-wide, no table):

```
https://harissdq.github.io/DigiMenu/?restaurant=demo-restaurant&takeaway=1
```

`TableQrCode.decode()` also accepts a deep link (`digimenu://table/Table_1`) or a
raw id, so legacy codes keep working. See `core/qr/TableQrCode.kt`.

Pipeline:
1. **Generate** (`QrCodeGenerator`) — manager app renders `TableQrCode.encode(id)`
   to a bitmap; the same id is persisted to `tables/` via `TableRepository.ensureTable`.
2. **Open** — the customer's phone camera reads the QR and opens the URL.
3. **Verify** (`web/app.js`) — the page reads `tables/{id}`; only real tables can
   start a dine-in order (shows a friendly error otherwise). The take-away QR
   skips table verification and asks for a delivery address instead.

## 6. Key flows

**Manager — add menu item / mark out of stock**
`MenuScreen` → `MenuViewModel` → `MenuRepository.addItem/setAvailability` → `menu/` → every open customer page live-updates.

**Manager — table QR**
`QrCodesScreen` → `TableRepository.ensureTable(label)` + `QrCodeGenerator.generate(TableQrCode.encode(id))` → show/save bitmap.

**Manager — incoming order**
`OrdersScreen` → `OrderRepository.observeOrders()` → new `status=NEW` order card appears with items, qty, table. The manager **accepts** (→ `ACCEPTED`) or **rejects** (→ `REJECTED` with a reason), then moves it `PREPARING → READY → DONE`, or cancels it.

**Manager — table session & bill**
`OrdersViewModel` links each active dine-in order to its table's session
(`SessionRepository.ensureOpen`). `TablesScreen` combines `tables` + `sessions` +
`orders` live: a table shows *Free*, *Seated since HH:mm*, or a settled bill.
**Close & bill** snapshots the total into the session; **Mark paid** archives it.

**Manager — reports**
`ReportsScreen` → `ReportsViewModel` → `ReportStats.aggregate(orders, from)`
period-filtered over the live orders stream → revenue / counts / category & item
breakdowns + CSV export.

**Kitchen — display & advance the queue**
Open `https://harissdq.github.io/DigiMenu/kitchen.html`, sign in with the
restaurant's manager account → live queue (`To prepare` / `Preparing` / `Ready`)
with an audio alert on new orders and one legal action per card (Accept / Start
preparing / Ready).

**Customer — scan → order**
Phone camera opens the QR URL → `web/app.js` resolves the restaurant id, verifies
the table (or enters take-away mode) → lead capture (name + phone, plus a
delivery address in take-away mode) → live menu + cart →
`db.ref(.../orders).push()` (key retained) → `.set(order)`. The order appears on
the manager dashboard and the kitchen display instantly, and the customer's
confirmation page subscribes to `orders/{orderId}` to show the live status
timeline (`Placed → Accepted → Preparing → Ready → Completed`, or the rejection
reason) — with an audio/visual alert when the status changes (P6).

**Manager — tenant resolution**
`ManagerViewModel.login()` → `RestaurantSession.refresh()` →
`AuthRepository.currentRestaurantId()` reads `managers/{uid}/restaurantId` (with a
legacy fallback to `restaurants/demo-restaurant/managers/{uid} == true`). All
repositories are then scoped to that id via `RestaurantSession.restaurantId`, so
a manager only ever sees their own restaurant's data. The top bar shows the
resolved restaurant name (`restaurants/{id}/info/name`).

## 7. Security notes (production hardening)

- Realtime Database rules: `managers/{uid}` readable only by that user; manager
  writes to `info`/`menu`/`tables`/`sessions`/`orders` gated on
  `managers/{uid}/restaurantId == $restaurantId`; `orders/` readable only by
  managers, writable by **anyone only to create** (`!data.exists()`), with the
  payload required to carry a `tableId`; `menu`/`tables`/`info` readable by all.
  `sessions` is manager-only on both read and write (customers never touch it).
  Only the tiny `status` / `statusChangedAt` / `declineReason` subfields of an
  order are publicly readable (so a customer can follow their own order by its
  unguessable push key) — the rest of an order stays manager-only.
- The kitchen display authenticates with the restaurant's **manager account**,
  so it inherits the exact tenant-scoped read/write rights of the Android app.
  It shares the account with the manager device — fine for a single-restaurant
  setup; for separate kitchen staff, create a second manager account for the
  same `restaurantId`.
- The QR payload id is only the *key* — never embed prices or customer data in a QR.
- The web page never authenticates a customer; the **rules** are the security
  boundary for public writes.
- The Firebase web SDK config in `web/config.js` is public by design — it only
  identifies the project; access control lives in the rules.
- The restaurant id is derived from the signed-in manager's
  `managers/{uid}/restaurantId` mapping (never from the QR payload), so a
  scanned QR cannot escalate a manager across tenants.

## 8. Build & run

1. Create a Firebase project, enable **Realtime Database** and **Email/Password**
   sign-in, and download `google-services.json` into `manager/` (the file is
   gitignored; CI falls back to a placeholder so the APK always compiles).
2. Add the Firebase **web** config to `web/config.js` (Project settings → Your
   apps → Web). This is required for the customer page to connect.
3. Paste the Rules sample from `docs/FIREBASE_SETUP.md`.
4. `./gradlew :manager:assembleDebug` (needs JDK 17 + Android SDK 35), or push
   and download the APK artifact from Actions.
5. The web page is deployed to `https://harissdq.github.io/DigiMenu/` on every
   push to `main` — `index.html` (customer) and `kitchen.html` (kitchen
   display) are served side by side.
6. Seed `managers/{uid}/restaurantId` + `restaurants/demo-restaurant` (info,
   tables, menu, manager uid) as described in `docs/FIREBASE_SETUP.md`, then
   print the table and Take Away QRs from the app.
