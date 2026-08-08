# DigiMenu

Full-stack restaurant system in two Android apps sharing one Firebase Realtime
Database backend. The **manager** app runs the menu, QR codes and live orders;
the **customer** app is what diners see after scanning a table's QR code.

```
┌──────────────────────┐     ┌─────────────────────────────────┐
│  DigiMenu Manager    │     │  DigiMenu Customer              │
│  (com.digimenu.manager)     │  (com.digimenu.customer)        │
│                      │     │                                 │
│  • login             │     │  • scan table QR  (CameraX +    │
│  • menu CRUD         │     │    ML Kit barcode)              │
│  • out-of-stock      │     │  • lead capture (name/phone)    │
│  • generate table QR │     │  • live menu                    │
│  • live orders       │     │  • cart + place order           │
└──────────┬───────────┘     └──────────┬──────────────────────┘
           │                            │
           └──────────────┬─────────────┘
                          ▼
        ┌─────────────────────────────────┐
        │ Firebase Realtime Database       │
        │  restaurants/{id}/               │
        │    menu/{itemId}                 │
        │    tables/{tableId}              │
        │    orders/{orderId}              │
        │    managers/{uid} -> true        │
        └─────────────────────────────────┘
```

## Modules

| Module   | Type               | Description                                                   |
|----------|--------------------|---------------------------------------------------------------|
| `:core`  | Android library    | Shared models, Firebase repositories, QR encode/decode, DI.   |
| `:manager`| Application       | Restaurant dashboard (Compose + Hilt).                        |
| `:customer`| Application     | Diner app that scans QRs and places orders.                   |

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full blueprint and
[`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md) for backend setup.

## QR payload format

A table QR always encodes the canonical table id in a versionable payload
(`TableQrCode.encode`). All of these resolve to the same table id when scanned:

```
digimenu://table/Table_1        deep link (canonical, what the manager generates)
https://digimenu.app/t/Table_1  web URL form
https://digimenu.app/?table=Table_1
Table_1                         raw id (plain-text QR)
```

The customer app verifies the scanned table against the database registry
(`QrTableResolver`) before allowing an order, so only real tables can order.

## Tech stack

- Kotlin 2.0.20, AGP 8.7.3, Gradle 8.9 (wrapper included)
- Jetpack Compose (BOM 2024.09.02, Material 3), ViewModel + StateFlow
- Hilt 2.52 (KSP)
- Firebase Realtime Database + Firebase Auth (BOM 33.1.2)
- CameraX 1.3.4 + ML Kit barcode scanning (customer)
- ZXing core (QR rendering in the manager)

## Build

1. Add a `google-services.json` for each application id to the corresponding
   module (see `docs/FIREBASE_SETUP.md`). The file is gitignored.
2. Open the project in Android Studio (or run `./gradlew assembleDebug`).
3. Install the manager app, log in, add menu items, add a table and print its QR.
4. Install the customer app, scan the printed QR, enter a name/phone, order.

## Security notes

- Customers never authenticate; they write only to `orders` under their table.
- Managers authenticate with Firebase Auth and are additionally checked against
  the `managers/{uid}` node.
- Production Realtime Database rules must restrict `menu`/`tables` writes to
  authenticated managers and `orders` writes to table-bound sessions. Starter
  rules are provided in `docs/FIREBASE_SETUP.md`.
