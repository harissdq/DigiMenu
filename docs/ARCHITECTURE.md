# DigiMenu — Architectural Blueprint

A full-stack restaurant management system with **two Android apps** sharing one
backend: a **Manager Dashboard** (menu, QR codes, live orders) and a **Customer
Ordering Portal** (scan → lead → menu → order).

## 1. System overview

```
┌──────────────────────────────┐         ┌──────────────────────────────┐
│  Manager Dashboard (:manager)│         │  Customer Portal (:customer) │
│  ┌──────────┬──────────────┐ │  real-  │  ┌────────────────────────┐  │
│  │ Login    │ Menu CRUD    │ │  time   │  │ QR scanner  (CameraX + │  │
│  │ (Auth)   │ + stock flag │ │◄────────►│  │  ML Kit barcode)      │  │
│  ├──────────┼──────────────┤ │ Firebase│  ├────────────────────────┤  │
│  │ QR code  │ Order tracker│ │Realtime │  │ Lead capture           │  │
│  │ generator│ (live feed)  │ │Database │  │ (name + phone)         │  │
│  └──────────┴──────────────┘ │         │  ├────────────────────────┤  │
└──────────────────────────────┘         │  │ Digital menu (live     │  │
                                         │  │  prices/stock)         │  │
                                         │  ├────────────────────────┤  │
                                         │  │ Cart → place order     │  │
                                         │  └────────────────────────┘  │
└────────────────────────────────────────┴──────────────────────────────┘
              shared :core module (models, repos, QR mapping)
```

- **Manager** pushes writes (menu, stock, tables, order status).
- **Customer** pushes writes (orders) and reads (menu) — every node it reads is
  subscribed live, so nothing needs a manual refresh.
- **Shared code** (data model, Firebase repositories, QR codec/resolver, QR
  bitmap generator) lives once in `:core`.

## 2. Tech stack

| Layer        | Choice                                    | Why |
|--------------|-------------------------------------------|-----|
| UI           | Jetpack Compose (Material 3)              | Fast to build, single toolchain, modern look |
| DI           | Hilt + KSP                                | Standard, compile-safe graph for both apps |
| Async        | Kotlin Coroutines + Flows                 | Repos expose `Flow` for real-time data |
| Backend      | Firebase Realtime Database                | Change events push to all subscribers instantly — the "no refresh" requirement |
| Auth         | Firebase Auth (email/password)            | Manager sign-in; authorisation via `managers/{uid}` node |
| QR *write*   | ZXing core (`QRCodeWriter`)               | Pure-JVM bitmap generation, no camera |
| QR *read*    | ML Kit barcode scanning + CameraX         | Fast, on-device, modern API |
| Build        | AGP 8.7, Kotlin 2.0, Gradle 8.9           | Matches existing project conventions |

### Why Realtime Database over Firestore
The requirement is "manager sees orders instantly **and** menu updates globally
the moment a price changes." Realtime Database delivers whole-node snapshots to
every connected client with a single `ValueEventListener` — the simplest way to
satisfy both "instant order" and "global menu update" with one subscription per
screen. Firestore is a better choice later if you need complex queries, but it
is overkill for this schema.

## 3. Module architecture

```
:core      — pure shared layer (no UI): models, FirebaseRefs, repositories,
             QR codec + resolver + generator, DI module
:manager   — Manager Dashboard app
:customer  — Customer Ordering Portal app
```

Each app is a separate `applicationId` (`com.digimenu.manager`, `com.digimenu.customer`)
so the two interfaces can be installed on different phones simultaneously.

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
- **Menu**: `MenuRepository.observeMenu()` → customer menu + manager editor both
  subscribe to `menu/`. A manager's `setAvailability(false)` writes one field;
  every open customer menu updates immediately.
- **Orders**: customer checkout writes to `orders/`; manager's `observeOrders()`
  gets the child event the instant it lands.
- **Status**: manager flips `status` (`NEW → PREPARING → DONE/CANCELLED`).

## 5. QR → table mapping

Payload format (canonical): `digimenu://table/<TABLE_ID>` (e.g. `digimenu://table/Table_1`).

The same id resolves from any of these equivalent encodings — see
`core/qr/TableQrCode.kt`:

| Form | Example |
|------|---------|
| Deep link | `digimenu://table/Table_1` |
| Web URL (path) | `https://digimenu.app/t/Table_1` |
| Web URL (query) | `https://digimenu.app/?table=Table_1` |
| Raw id | `Table_1` |

Pipeline:
1. **Decode** (`TableQrCode.decode`) — pure parsing, no I/O, testable offline.
2. **Verify** (`QrTableResolver.resolve`) — checks the id against the `tables/`
   registry so only real tables can order; returns `Valid / UnknownTable /
   NotATable / Offline`.
3. **Generate** (`QrCodeGenerator`) — manager app renders `encode(tableId)` to a
   bitmap; the same id is persisted to `tables/` via `TableRepository.ensureTable`.

## 6. Key flows

**Manager — add menu item / mark out of stock**
`MenuScreen` → `MenuViewModel` → `MenuRepository.addItem/setAvailability` → `menu/` → all customers live-update.

**Manager — table QR**
`QrCodesScreen` → `TableRepository.ensureTable(label)` + `QrCodeGenerator.generate(TableQrCode.encode(id))` → show/save bitmap.

**Manager — incoming order**
`OrdersScreen` → `OrderRepository.observeOrders()` → new `status=NEW` order card appears with items, qty, table.

**Customer — scan → order**
`QrScannerScreen` (CameraX + ML Kit) → `CustomerViewModel.onQrScanned(raw)` → `QrTableResolver.resolve()` → `LeadScreen` (name+phone) → `MenuScreen` (live menu + cart) → `OrderRepository.placeOrder()` → `Order` appears on manager dashboard instantly.

## 7. Security notes (production hardening)

- Realtime Database rules: `managers/` readable only by that restaurant's
  authorised uids; `orders/` writeable by anyone (anonymous customers), readable
  only by managers; `menu/` readable by all, writeable by managers only.
- The QR payload id is only the *key* — never embed prices or customer data in a QR.
- The customer app is anonymous; it never needs an account, only a verified table.
- Bake the restaurant id from the manager's profile rather than the
  `DEFAULT_RESTAURANT` constant for a multi-tenant deployment.

## 8. Build & run

1. Create a Firebase project, enable **Realtime Database** and **Email/Password**
   sign-in, and download `google-services.json` into BOTH `manager/` and `customer/`
   (the file is gitignored).
2. Paste the `Rules` sample from `docs/` (optional but recommended).
3. `./gradlew :manager:assembleDebug :customer:assembleDebug` (needs JDK 17 +
   Android SDK 35).
4. In Firebase console: add an owner user, then put its uid in
   `restaurants/demo-restaurant/managers/{uid} = true`, or register that user via
   the app — the dashboard lets an authorised manager sign in.
