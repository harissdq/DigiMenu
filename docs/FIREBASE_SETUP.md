# Firebase setup

The backend is **one Firebase project** shared by the Android app and the
customer web page. This document covers the things that are **not** committed to
the repo: the `google-services.json` for the Android app, the Realtime Database
rules, and the demo seed data.

## 1. Create the project

1. Go to the [Firebase Console](https://console.firebase.google.com) and create
   a project.
2. Add an **Android app** to it:
   - Package name `com.digimenu.manager`
3. Download the generated `google-services.json` and place it at:
   - `manager/google-services.json`
4. Add a **Web app** to the same project (this gives you the config for
   `web/config.js`).
5. Enable **Email/Password** in *Authentication → Sign-in method*.
6. Create the manager account (e.g. `owner@example.com` / a strong password).

## 2. Realtime Database

Enable **Realtime Database** in test mode first, then replace the rules with
the production set below.

> **Region gotcha:** if you create the database in a region other than
> `us-central1` (the default), the database URL becomes region-specific, e.g.
> `https://<project>-default-rtdb.<region>.firebasedatabase.app`. You must use
> that exact URL in two places:
> `FirebaseRefs.DATABASE_URL` in `core/.../firebase/FirebaseRefs.kt` (Android)
> and `databaseURL` in `web/config.js` (web). The console shows the correct URL
> at the top of the Realtime Database → Data tab.

### Rules

The admin account is `haris.sdq@gmail.com` (see `FirebaseRefs.ADMIN_EMAIL`);
it can create restaurants and link managers from the app's **Admin** tab. To
change the admin, update this file's rules and `FirebaseRefs.ADMIN_EMAIL`
together.

```json
{
  "rules": {
    "managers": {
      "$uid": {
        ".read": "auth != null && (auth.uid == $uid || auth.token.email == 'haris.sdq@gmail.com')",
        ".write": "auth != null && auth.token.email == 'haris.sdq@gmail.com'"
      }
    },
    "restaurants": {
      ".read": "auth.token.email == 'haris.sdq@gmail.com'",
      "$restaurantId": {
        ".read": "auth != null && (root.child('managers').child(auth.uid).child('restaurantId').val() == $restaurantId || root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true || auth.token.email == 'haris.sdq@gmail.com')",
        ".write": "auth.token.email == 'haris.sdq@gmail.com'",
        "info": {
          ".read": true,
          ".write": "auth != null && (root.child('managers').child(auth.uid).child('restaurantId').val() == $restaurantId || root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true || auth.token.email == 'haris.sdq@gmail.com')"
        },
        "menu": {
          ".read": true,
          ".write": "auth != null && (root.child('managers').child(auth.uid).child('restaurantId').val() == $restaurantId || root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true || auth.token.email == 'haris.sdq@gmail.com')"
        },
        "tables": {
          ".read": true,
          ".write": "auth != null && (root.child('managers').child(auth.uid).child('restaurantId').val() == $restaurantId || root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true || auth.token.email == 'haris.sdq@gmail.com')"
        },
        "orders": {
          "$orderId": {
            ".read": "auth != null && (root.child('managers').child(auth.uid).child('restaurantId').val() == $restaurantId || root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true || auth.token.email == 'haris.sdq@gmail.com')",
            ".write": "(!data.exists() && newData.exists() && newData.child('tableId').val() != null) || (auth != null && (root.child('managers').child(auth.uid).child('restaurantId').val() == $restaurantId || root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true || auth.token.email == 'haris.sdq@gmail.com'))",
            "status": {
              ".read": true
            },
            "statusChangedAt": {
              ".read": true
            },
            "declineReason": {
              ".read": true
            }
          }
        },
        "managers": {
          ".read": true,
          ".write": "auth != null && (root.child('managers').child(auth.uid).child('restaurantId').val() == $restaurantId || root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true || auth.token.email == 'haris.sdq@gmail.com')"
        }
      }
    }
  }
}
```

> A manager's tenant is stored at `managers/{uid}/restaurantId`; the legacy
> `restaurants/{id}/managers/{uid} == true` map is also accepted, so databases
> seeded before the multi-tenant change keep working without re-importing.
> Manager-only writes are gated on one of those matching `$restaurantId`, plus
> the admin email, so each account only ever manages its own restaurant.
>
> `orders` is writable by **anyone** placing an order (dine-in or take-away)
> without an account — but only to **create** one (`!data.exists()`), only if the
> payload carries a `tableId` (take-away orders use the literal `"TAKEAWAY"`).
> Existing orders can never be modified by anonymous clients (previously a
> customer could overwrite an order as long as it kept a `tableId`). The
> restaurant's own managers can also write an order (e.g. to update its status,
> optionally adding `statusChangedAt`/`declineReason`), gated by the same tenant
> check as the other nodes. Reads are manager/admin-only, so customers never see
> other people's orders. For stricter control, generate a short-lived signed token
> per scan and validate it server-side (out of scope for the boilerplate).

**Publishing the rules.** The exact JSON above is also committed as
[`docs/database.rules.json`](database.rules.json) — use that file, not copy-paste.

- *Console:* Realtime Database → **Rules** tab. Paste the JSON, then click the
  blue **Publish** button at the top of the editor (it only appears after the
  editor detects a change). If no button shows, toggle the editor by deleting
  and re-pasting the content, or click away so the editor marks the rules as
  modified.
- *CLI:* publish from the terminal with a service account (no console needed):

  ```bash
  node tools/create-tenant.mjs publish-rules <service-account.json> \
      docs/database.rules.json \
      --database-url https://<project>-default-rtdb.<region>.firebasedatabase.app
  ```

  Run with `--dry-run` to preview. Rules take effect immediately and read back
  through the REST endpoint `/.settings/rules.json`.

## 3. Wire the customer web page

The page lives in `web/` and is deployed automatically to
`https://harissdq.github.io/DigiMenu/`. Before it can talk to Firebase:

1. In the Firebase Console open the **Web app** you registered in step 1 and
   copy its SDK config (apiKey, authDomain, databaseURL, projectId, ...).
2. Paste those values into `web/config.js` (replace the `YOUR_*` placeholders).
3. Commit and push — the CI deploys the updated page.

> **No config yet?** The page runs in **demo mode**: it uses a sample menu and
> saves orders to `localStorage`, so the whole scan → order flow can be tested
> immediately at `https://harissdq.github.io/DigiMenu/?table=Table_1`. A banner
> tells you demo mode is active, and it switches off automatically once real
> values are present in `web/config.js`.

> The web SDK config is public by design. It only identifies your project;
> access control comes entirely from the Realtime Database rules above.

## 4. Seed the demo tenant

Each manager account maps to one restaurant via the root `managers/{uid}/restaurantId`
node. Import [`docs/seed-data.json`](seed-data.json) into the database
(⋮ → Import JSON) so the manager has a tenant, tables to print, and the customer
has a menu to browse. The file already contains the manager UID
(`AVNP9YjBkSP6mhzcx8JdybRfHRy1`) under `managers/`. If you use a different
account, replace that key with your own UID (copy it from *Authentication →
Users*).

To add another restaurant, create a second block under `restaurants/` and give
the new manager a `restaurantId` under `managers/` — both apps are
tenant-scoped, so each restaurant only ever sees its own menu, tables and
orders.

To get the manager UID: sign in on the manager app, or look in
*Authentication → Users* in the Firebase Console (the row's identifier).

### Adding tenants from the CLI

Manual console editing works, but for new tenants there is a one-command helper:

```bash
node tools/create-tenant.mjs <service-account.json> \
    --uid <managerUID> --restaurant bistro-downtown --name "Bistro Downtown" \
    --tables "Table_1,Table_2" --seed-demo-menu
```

It writes `managers/{uid}/restaurantId`, the restaurant's `info/name`, the
legacy `managers` map, tables and (optionally) menu items, then prints the QR
URLs. It needs no `npm install` (Node 18+ standard library only). To create the
manager's Auth account too, add
`--create-user --email owner@bistro.com --password secret --api-key <webApiKey>`.

Get the inputs from the console:
- **service account**: *Project settings → Service accounts → Generate new
  private key*. This file can read/write the whole project — treat it like a
  password, never commit it (`.gitignore` already ignores
  `service-account*.json`).
- **uid / web API key / database URL**: *Authentication → Users* and *Project
  settings → Your apps* (the web app you registered). Pass `--database-url`
  if your database is not in the default region.

Run with `--dry-run` first to preview the exact paths it will write.

### Adding tenants from the app

The main admin account (`haris.sdq@gmail.com`) sees an extra **Admin** tab when
it signs in. From there it can create a restaurant (name + tables) and either
become its manager or create + link a new manager account (email + password) —
no console work needed. Creating a new restaurant as your own manager switches
the app to it immediately. Restaurants can also be **deleted** from the same
tab (removes the tenant, its data, and unlinks its managers); the admin-only
`.write` rule on `restaurants/$restaurantId` makes the delete work.

## 5. Verify end-to-end

1. Manager app: sign in → top bar shows the restaurant name → menu shows the
   two seed items → *QR Codes* shows the tables plus the **Take Away** QR and
   can display their bitmaps.
2. Print/scan a table QR (or open
   `https://harissdq.github.io/DigiMenu/?restaurant=demo-restaurant&table=Table_1`
   directly) → enter name/phone → order a *Chicken Karahi* in the browser.
3. Take-away: open
   `https://harissdq.github.io/DigiMenu/?restaurant=demo-restaurant&takeaway=1`
   → enter name, phone **and delivery address** → order.
4. Manager app: *Orders* tab shows the new order instantly (take-away orders
   show "Take Away" and the delivery address). **Accept** it (moves to *Active*
   as `ACCEPTED`), then *Start preparing* → *Ready* → *Mark done*. The
   customer's confirmation page follows the same timeline live; a **Reject**
   (with a reason) shows the reason instead.
