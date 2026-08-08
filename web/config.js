// DigiMenu customer web app - Firebase configuration.
//
// Get these values from the Firebase Console:
//   Project settings -> General -> Your apps -> (Web app) -> SDK setup and configuration
//
// The web SDK config is public by design; security is enforced by the
// Realtime Database rules, NOT by keeping this file secret.
//
// Note: the app.js script loads the compat SDKs (firebase-app-compat,
// firebase-database-compat) directly from gstatic, so this file must stay a
// plain script that only assigns window.DIGIMENU_FIREBASE_CONFIG.
window.DIGIMENU_FIREBASE_CONFIG = {
  apiKey: "AIzaSyCG-dRxSkkxX_nnpopCz7eb3Wq4E72Ei2E",
  authDomain: "com-digimenu-manager.firebaseapp.com",
  databaseURL: "https://com-digimenu-manager-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "com-digimenu-manager",
  storageBucket: "com-digimenu-manager.firebasestorage.app",
  messagingSenderId: "433694338658",
  appId: "1:433694338658:web:56e969a3709dcec72eeb3b"
};
