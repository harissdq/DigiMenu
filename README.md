# DigiMenu

Restaurant system with **one Android app** (for the restaurant) and a **browser
web page** (for customers). Customers scan a printed table QR code, which opens
the restaurant's live menu in their phone browser and lets them order directly.
Everything is powered by one Firebase Realtime Database.

```
┌─────────────────────────┐
│  DigiMenu (Android)     │   com.digimenu.manager — installed on the
│  operated by restaurant │   restaurant's device/tablet
│                         │
│  • login                │
│  • menu CRUD            │
│  • out-of-stock         │
│  • generate table QR    │
│  • live orders          │
└────────────┬────────────┘
             │  QR encodes menu page URL: ?table=Table_1
             ▼
┌─────────────────────────┐
│  Customer web page      │   https://harissdq.github.io/DigiMenu/
│  (static site, GitHub   │   hosted on GitHub Pages, served to the
│   Pages + Firebase JS)  │   customer's phone browser after scanning
│                         │
│  • verify table         │
│  • lead capture         │
│  • live menu + cart     │
│  • place order          │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│ Firebase Realtime DB    │
│  restaurants/{id}/      │
│    menu/{itemId}        │
│    tables/{tableId}     │
│    orders/{orderId}     │
│    managers/{uid}->true │
└─────────────────────────┘
```

## Repo layout

| Path                        | What it is                                            |
|-----------------------------|-------------------------------------------------------|
| `manager/`                  | The Android app (single app, restaurant-facing).      |
| `core/`                     | Shared Android library (models, Firebase repos, QR).  |
| `web/`                      | Customer web app: `index.html`, `app.js`, `config.js`.|
| `.github/workflows/`        | CI: builds the APK + deploys `web/` to GitHub Pages.  |
| `docs/ARCHITECTURE.md`      | Blueprint and data model.                             |
| `docs/FIREBASE_SETUP.md`    | Firebase project, rules and seed instructions.        |

## How customers order (no app install)

1. The restaurant prints the QR code for each physical table (Manager app →
   QR Codes tab). The code encodes `https://harissdq.github.io/DigiMenu/?table=Table_1`.
2. A customer points their phone camera at the QR → the menu page opens in their
   browser with the table pre-selected.
3. They enter name + phone, browse the live menu, add items, place the order.
4. The order lands in Firebase instantly and the Manager app's Orders tab
   updates live (no refresh, no polling).

## QR payload format

`TableQrCode.encode()` produces the menu page URL with the canonical table id:

```
https://harissdq.github.io/DigiMenu/?table=Table_1
```

`TableQrCode.decode()` can also read `digimenu://table/<id>` or a raw id.

## Tech stack

- Android: Kotlin 2.0.20, Compose (BOM 2024.09.02, Material 3), Hilt, AGP 8.7.3
- Web: plain HTML/CSS/JS + Firebase JS SDK (compat), no build step
- Backend: Firebase Realtime Database + Firebase Auth (BOM 33.1.2)
- Hosting: GitHub Pages (deployed by CI) · QR rendering: ZXing

## Build

1. Add `manager/google-services.json` (see `docs/FIREBASE_SETUP.md`).
2. `./gradlew :manager:assembleDebug`, or push to GitHub and download the APK
   artifact from Actions.
3. The customer page is deployed automatically to
   `https://harissdq.github.io/DigiMenu/` on every push.

## Security notes

- Customers never authenticate; they write only to `orders` under their table
  from the web page. The Firebase **rules** are the security boundary.
- Managers sign in with Firebase Auth and are checked against `managers/{uid}`.
- Production rules: `menu`/`tables` writes → manager-only; `orders` writes →
  any payload carrying a `tableId`; reads of `orders` → manager-only. Starter
  rules are in `docs/FIREBASE_SETUP.md`.
