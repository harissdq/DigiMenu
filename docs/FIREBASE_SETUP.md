# Firebase setup

Both apps talk to one Firebase project. This document covers the three things
that are **not** committed to the repo: the `google-services.json` config files,
the Realtime Database rules, and the demo seed data.

## 1. Create the project

1. Go to the [Firebase Console](https://console.firebase.google.com) and create
   a project.
2. Add **two Android apps** to it (same project):
   - Package name `com.digimenu.manager`
   - Package name `com.digimenu.customer`
3. Download the generated `google-services.json` and place it at:
   - `manager/google-services.json`
   - `customer/google-services.json`
4. Enable **Email/Password** in *Authentication → Sign-in method*.
5. Create the manager account (e.g. `owner@example.com` / a strong password).

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
> customer without an account can place an order — but only if the payload
> carries a `tableId`. Reads are manager-only, so customers never see other
> people's orders. For stricter control, generate a short-lived signed token
> per scan and validate it server-side (out of scope for the boilerplate).

## 3. Seed the demo tenant

The apps default to `restaurants/demo-restaurant` (`FirebaseRefs.DEFAULT_RESTAURANT`).
Import the JSON below into the database (⋮ → Import JSON) so the manager has a
table to print and the customer has a menu to browse.

```json
{
  "restaurants": {
    "demo-restaurant": {
      "managers": {
        "<REPLACE_WITH_MANAGER_UID>": true
      },
      "tables": {
        "Table_1": {
          "id": "Table_1",
          "label": "Table 1",
          "createdAt": 1700000000000
        },
        "Table_2": {
          "id": "Table_2",
          "label": "Table 2",
          "createdAt": 1700000000000
        }
      },
      "menu": {
        "demo-item-1": {
          "id": "demo-item-1",
          "name": "Chicken Karahi",
          "description": "Served with naan",
          "price": 1800,
          "category": "Mains",
          "available": true,
          "updatedAt": 1700000000000
        },
        "demo-item-2": {
          "id": "demo-item-2",
          "name": "Chai",
          "description": "Fresh, hot, with cardamom",
          "price": 150,
          "category": "Drinks",
          "available": true,
          "updatedAt": 1700000000000
        }
      },
      "orders": {}
    }
  }
}
```

To get the manager UID: sign in on the manager app, or look in
*Authentication → Users* in the Firebase Console (the row's identifier).

## 4. Verify end-to-end

1. Manager app: sign in → menu shows the two seed items → *QR Codes* shows the
   tables and can display their QR bitmaps.
2. Customer app: scan the printed QR (or the on-screen one) → enter name/phone →
   order a *Chicken Karahi*.
3. Manager app: *Orders* tab shows the new order instantly.
