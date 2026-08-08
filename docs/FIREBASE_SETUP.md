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

### Rules

```json
{
  "rules": {
    "restaurants": {
      "$restaurantId": {
        "menu": {
          ".read": true,
          ".write": "auth != null && root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true"
        },
        "tables": {
          ".read": true,
          ".write": "auth != null && root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true"
        },
        "orders": {
          "$orderId": {
            ".read": "auth != null && root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true",
            ".write": "newData.exists() && newData.child('tableId').val() != null"
          }
        },
        "managers": {
          ".read": "auth != null && root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true",
          ".write": "auth != null && root.child('restaurants').child($restaurantId).child('managers').child(auth.uid).val() == true"
        }
      }
    }
  }
}
```

> `orders` is intentionally writable by **anyone** (`newData` write) so a
> customer in their phone browser can place an order without an account — but
> only if the payload carries a `tableId`. Reads are manager-only, so customers
> never see other people's orders. For stricter control, generate a short-lived
> signed token per scan and validate it server-side (out of scope for the
> boilerplate).

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

The apps default to `restaurants/demo-restaurant` (`FirebaseRefs.DEFAULT_RESTAURANT`).
Import [`docs/seed-data.json`](seed-data.json) into the database (⋮ → Import JSON)
so the manager has a table to print and the customer has a menu to browse. The
file already contains the manager UID; if you use a different account, replace
its key under `managers/` with your own UID (copy it from *Authentication →
Users*).

To get the manager UID: sign in on the manager app, or look in
*Authentication → Users* in the Firebase Console (the row's identifier).

## 5. Verify end-to-end

1. Manager app: sign in → menu shows the two seed items → *QR Codes* shows the
   tables and can display their QR bitmaps.
2. Print/scan a table QR (or open
   `https://harissdq.github.io/DigiMenu/?table=Table_1` directly) → enter
   name/phone → order a *Chicken Karahi* in the browser.
3. Manager app: *Orders* tab shows the new order instantly.
