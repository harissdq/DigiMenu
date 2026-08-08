# DigiMenu — Architectural Blueprint

A restaurant system with **one Android app** (the Manager Dashboard) and a
**browser-based customer page** (no customer app, no install). Customers scan a
printed table QR code and the menu opens in their phone browser.

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
│  └──────────┴──────────────┘ │
└──────────────┬───────────────┘
               │  QR encodes the web page URL: ?table=Table_1
               ▼
┌──────────────────────────────┐
│  Customer web page (web/)    │   static HTML/CSS/JS + Firebase JS SDK
│  ┌────────────────────────┐  │   hosted on GitHub Pages
│  │ verify table           │  │
│  │ lead capture           │  │
│  │ live menu + cart       │  │
│  │ place order            │  │
│  └────────────────────────┘  │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  Firebase Realtime Database  │   single backend, tenant-scoped
│  restaurants/{id}/{menu,     │
│   tables, orders, managers}  │
└──────────────────────────────┘
```

- **Manager** (Android) pushes writes: menu, stock, tables, order status.
- **Customer** (web) reads menu/tables live and writes orders.
- **Shared Android code** (models, Firebase repositories, QR codec/generator)
  lives once in `:core`. The web app mirrors the same schema with the JS SDK.

## 2. Tech stack

| Layer        | Choice                                    | Why |
|--------------|-------------------------------------------|-----|
| Manager UI   | Jetpack Compose (Material 3)              | Fast to build, single toolchain, modern look |
| DI           | Hilt + KSP                                | Standard, compile-safe graph |
| Async        | Kotlin Coroutines + Flows                 | Repos expose `Flow` for real-time data |
| Customer UI  | Plain HTML/CSS/JS + Firebase JS SDK       | Zero install, works in every phone browser |
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
             QR codec + generator, DI module
:manager   — the single Android app (Manager Dashboard)
web/       — customer-facing static site (independent of the Gradle build)
```

The customer interface is deliberately *not* an Android module: customers should
never install anything — the QR opens the web page directly.

## 4. Data model (Firebase Realtime Database)

```
restaurants/{restaurantId}/
  menu/{itemId}/
    { id, name, description, price, category, available, updatedAt }
  tables/{tableId}/
    { id, label, createdAt }
  orders/{orderId}/
    { id, tableId, tableLabel, customerName, customerPhone,
      items: { itemId: { name, price, qty } },
      total, status, createdAt }
  managers/{uid} = true
```

Real-time propagation:
- **Menu**: manager's `MenuRepository.observeMenu()` and the web page's
  `db.ref('.../menu').on('value')` both subscribe to `menu/`. A
  `setAvailability(false)` write by the manager updates every open browser menu
  immediately.
- **Orders**: web checkout writes to `orders/`; the manager's `observeOrders()`
  gets the child the instant it lands.
- **Status**: manager flips `status` (`NEW → PREPARING → DONE/CANCELLED`).

## 5. QR → table mapping

Payload format (canonical): the web page URL with the table id as a query
parameter:

```
https://harissdq.github.io/DigiMenu/?table=Table_1
```

`TableQrCode.decode()` also accepts a deep link (`digimenu://table/Table_1`) or a
raw id, so legacy codes keep working. See `core/qr/TableQrCode.kt`.

Pipeline:
1. **Generate** (`QrCodeGenerator`) — manager app renders `TableQrCode.encode(id)`
   to a bitmap; the same id is persisted to `tables/` via `TableRepository.ensureTable`.
2. **Open** — the customer's phone camera reads the QR and opens the URL.
3. **Verify** (`web/app.js`) — the page reads `tables/{id}`; only real tables can
   start an order (shows a friendly error otherwise).

## 6. Key flows

**Manager — add menu item / mark out of stock**
`MenuScreen` → `MenuViewModel` → `MenuRepository.addItem/setAvailability` → `menu/` → every open customer page live-updates.

**Manager — table QR**
`QrCodesScreen` → `TableRepository.ensureTable(label)` + `QrCodeGenerator.generate(TableQrCode.encode(id))` → show/save bitmap.

**Manager — incoming order**
`OrdersScreen` → `OrderRepository.observeOrders()` → new `status=NEW` order card appears with items, qty, table.

**Customer — scan → order**
Phone camera opens the QR URL → `web/app.js` verifies the table → lead capture
(name + phone) → live menu + cart → `db.ref(.../orders).push().set(order)` → the
order appears on the manager dashboard instantly.

## 7. Security notes (production hardening)

- Realtime Database rules: `managers/` readable only by that restaurant's
  authorised uids; `orders/` writeable by anyone (anonymous customers), readable
  only by managers; `menu/` readable by all, writeable by managers only.
- The QR payload id is only the *key* — never embed prices or customer data in a QR.
- The web page never authenticates a customer; the **rules** are the security
  boundary for public writes.
- The Firebase web SDK config in `web/config.js` is public by design — it only
  identifies the project; access control lives in the rules.
- Bake the restaurant id from the manager's profile rather than the
  `DEFAULT_RESTAURANT` constant for a multi-tenant deployment.

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
   push to `main`.
6. Seed `restaurants/demo-restaurant` (tables + menu + manager uid) as described
   in `docs/FIREBASE_SETUP.md`, then print the table QRs from the app.
