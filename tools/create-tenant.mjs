#!/usr/bin/env node
/**
 * DigiMenu tenant creator.
 *
 * One command to add a new restaurant (tenant) + link a manager account, using
 * a Firebase service account to talk to the Realtime Database REST API. No npm
 * dependencies — runs on Node 18+ with just the standard library.
 *
 * Usage:
 *   node tools/create-tenant.mjs <service-account.json> \
 *       --uid <managerUID> --restaurant <restaurantId> --name "Restaurant Name" \
 *       [--tables "T1,T2"] [--seed-demo-menu] [--menu menu.json] \
 *       [--create-user --email a@b.c --password secret --api-key <webApiKey>] \
 *       [--database-url https://...] [--dry-run]
 *
 * Where do the inputs come from?
 *   - service-account.json: Firebase Console -> Project settings -> Service
 *     accounts -> Generate new private key. Treat it like a password: it can
 *     read/write everything in the project. Do NOT commit it.
 *   - manager UID: Firebase Console -> Authentication -> Users (the row's id),
 *     or after signing in on the manager app.
 *   - --api-key / --database-url: Firebase Console -> Project settings -> Your
 *     apps -> the web app you registered.
 */
import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";
import crypto from "node:crypto";

const RESTAURANT_ID_REGEX = /^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/;
const TOKEN_SCOPE =
  "https://www.googleapis.com/auth/firebase.database https://www.googleapis.com/auth/userinfo.email";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const TOKEN_AUD = "https://oauth2.googleapis.com/token";
const DB_SUFFIX = "-default-rtdb.firebaseio.com";

const USAGE = `DigiMenu admin tool.

Create a tenant:
  node tools/create-tenant.mjs <service-account.json> \\
      --uid <managerUID> --restaurant <restaurantId> --name "Restaurant Name" \\
      [--tables "T1,T2"] [--seed-demo-menu] [--menu menu.json] \\
      [--create-user --email a@b.c --password secret --api-key <webApiKey>] \\
      [--database-url https://...] [--dry-run]

Publish the Realtime Database rules (avoids the console):
  node tools/create-tenant.mjs publish-rules <service-account.json> \\
      [docs/database.rules.json] [--database-url https://...] [--dry-run]

Options (create):
  --uid <uid>            Manager account uid (Authentication -> Users). Required.
  --restaurant <id>      Tenant id, URL-safe (e.g. "bistro-downtown"). Required.
  --name <name>          Display name shown in the app top bar. Required.
  --tables "T1,T2"       Table ids to seed (default: Table_1,Table_2).
  --seed-demo-menu       Seed the two demo menu items (Chicken Karahi, Chai).
  --menu <file.json>     Seed menu items from a JSON file (object or array).
  --create-user          Create the manager Auth account first (needs --api-key).
  --email <email>        Email for the new account (with --create-user).
  --password <pass>      Password for the new account (with --create-user).
  --api-key <key>        Public web API key (with --create-user).

Options (all):
  --database-url <url>   Realtime Database URL from the console. Defaults to
                         https://<project>-default-rtdb.firebaseio.com.
  --dry-run              Print what would happen without sending anything.
  --help                 Show this help.`;

/* ------------------------------------------------------------------ args */

function parseArgs(argv) {
  const out = { tables: [] };
  const set = (k, v) => {
    if (out[k] !== undefined && Array.isArray(out[k])) out[k].push(v);
    else out[k] = v;
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const take = () => {
      i++;
      if (i >= argv.length) throw new Error(`Missing value for ${a}`);
      return argv[i];
    };
    switch (a) {
      case "--help":
      case "-h":
        out.help = true;
        break;
      case "--dry-run":
        out.dryRun = true;
        break;
      case "--seed-demo-menu":
        out.seedDemoMenu = true;
        break;
      case "--create-user":
        out.createUser = true;
        break;
      case "--tables":
        out.tables = take()
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean);
        break;
      case "--uid":
        out.uid = take().trim();
        break;
      case "--restaurant":
        out.restaurant = take().trim();
        break;
      case "--name":
        out.name = take().trim();
        break;
      case "--menu":
        out.menuFile = take();
        break;
      case "--email":
        out.email = take().trim();
        break;
      case "--password":
        out.password = take();
        break;
      case "--api-key":
        out.apiKey = take().trim();
        break;
      case "--database-url":
        out.databaseUrl = take().trim().replace(/\/+$/, "");
        break;
      default:
        if (a.startsWith("-")) throw new Error(`Unknown option: ${a}`);
        if (!out.subcommand && a === "publish-rules") out.subcommand = a;
        else if (!out.serviceAccount) out.serviceAccount = a;
        else if (out.subcommand === "publish-rules" && !out.rulesFile) out.rulesFile = a;
        else throw new Error(`Unexpected argument: ${a}`);
    }
  }
  return out;
}

/* ------------------------------------------------------------ JWT + token */

function b64url(buf) {
  return Buffer.from(buf).toString("base64url");
}

function signJwt(claims, privateKeyPem) {
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = b64url(JSON.stringify(claims));
  const signer = crypto.createSign("RSA-SHA256");
  signer.update(`${header}.${payload}`);
  const sig = signer.sign(privateKeyPem);
  return `${header}.${payload}.${b64url(sig)}`;
}

function buildJwt(clientEmail, privateKeyPem) {
  const now = Math.floor(Date.now() / 1000);
  return signJwt(
    {
      iss: clientEmail,
      scope: TOKEN_SCOPE,
      aud: TOKEN_AUD,
      iat: now,
      exp: now + 3600,
    },
    privateKeyPem,
  );
}

/** Exchanges a signed JWT for an OAuth2 access token (Google service account). */
async function exchangeToken(clientEmail, privateKeyPem, tokenUrl = TOKEN_URL) {
  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion: buildJwt(clientEmail, privateKeyPem),
  });
  const res = await fetch(tokenUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });
  const json = await res.json();
  if (!res.ok || !json.access_token) {
    throw new Error(`Token exchange failed (${res.status}): ${JSON.stringify(json)}`);
  }
  return json.access_token;
}

/* ------------------------------------------------------- Identity Toolkit */

/** Creates an email/password Auth account; returns its uid. */
async function createUser(apiKey, email, password) {
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${encodeURIComponent(apiKey)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    },
  );
  const json = await res.json();
  if (!res.ok || !json.localId) {
    throw new Error(`Account creation failed (${res.status}): ${JSON.stringify(json)}`);
  }
  return json.localId;
}

/* ------------------------------------------------------------- DB payload */

const DEMO_MENU = {
  "demo-item-1": {
    id: "demo-item-1",
    name: "Chicken Karahi",
    description: "Served with naan",
    price: 1800,
    category: "Mains",
    available: true,
    updatedAt: Date.now(),
  },
  "demo-item-2": {
    id: "demo-item-2",
    name: "Chai",
    description: "Fresh, hot, with cardamom",
    price: 150,
    category: "Drinks",
    available: true,
    updatedAt: Date.now(),
  },
};

function normalizeMenuItem(raw, fallbackId) {
  const id = raw.id || fallbackId;
  return {
    id,
    name: String(raw.name || raw.label || id),
    description: raw.description != null ? String(raw.description) : "",
    price: Number(raw.price || 0),
    category: raw.category != null ? String(raw.category) : "Mains",
    available: raw.available !== false,
    updatedAt: raw.updatedAt != null ? Number(raw.updatedAt) : Date.now(),
  };
}

function loadMenu(menuFile) {
  const parsed = JSON.parse(fs.readFileSync(menuFile, "utf8"));
  const entries = Array.isArray(parsed) ? parsed : Object.values(parsed);
  const items = {};
  entries.forEach((raw, idx) => {
    const item = normalizeMenuItem(raw, raw.id || `menu-item-${idx + 1}`);
    items[item.id] = item;
  });
  return items;
}

/** Builds the multi-path update for the whole tenant. */
function buildPayload(opts) {
  if (!opts.uid) throw new Error("Missing required option: --uid");
  if (!opts.restaurant) throw new Error("Missing required option: --restaurant");
  if (!RESTAURANT_ID_REGEX.test(opts.restaurant)) {
    throw new Error(
      `Invalid --restaurant id "${opts.restaurant}" (use only A-Z a-z 0-9 _ -).`,
    );
  }
  if (!opts.name) throw new Error("Missing required option: --name");
  if (!opts.name.trim()) throw new Error("--name must not be blank.");

  const r = opts.restaurant;
  const payload = {
    [`managers/${opts.uid}/restaurantId`]: r,
    [`restaurants/${r}/info`]: { name: opts.name.trim() },
    [`restaurants/${r}/managers/${opts.uid}`]: true,
    [`restaurants/${r}/orders`]: {},
  };

  const tables = opts.tables.length ? opts.tables : ["Table_1", "Table_2"];
  tables.forEach((id) => {
    payload[`restaurants/${r}/tables/${id}`] = {
      id,
      label: id.replace(/_/g, " "),
      createdAt: Date.now(),
    };
  });

  const items = { ...(opts.seedDemoMenu ? DEMO_MENU : {}), ...(opts.menuItems || {}) };
  Object.values(items).forEach((item) => {
    payload[`restaurants/${r}/menu/${item.id}`] = item;
  });

  return payload;
}

async function applyUpdate(databaseUrl, accessToken, payload) {
  const res = await fetch(`${databaseUrl}/.json`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(payload),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`Database write failed (${res.status}): ${text}`);
  }
  return JSON.parse(text);
}

/** Reads the currently active Realtime Database rules. */
async function fetchRules(databaseUrl, accessToken) {
  const res = await fetch(`${databaseUrl}/.settings/rules.json`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`Reading rules failed (${res.status}): ${text}`);
  return JSON.parse(text);
}

/** Publishes new Realtime Database rules (bypasses the console entirely). */
async function publishRules(databaseUrl, accessToken, rules) {
  const res = await fetch(`${databaseUrl}/.settings/rules.json?writeSizeLimit=medium`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(rules),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`Publishing rules failed (${res.status}): ${text}`);
  return text;
}

async function publishRulesMain(opts, sa) {
  const rulesPath = path.resolve(opts.rulesFile || "docs/database.rules.json");
  if (!fs.existsSync(rulesPath)) {
    console.error(`Rules file not found: ${rulesPath}`);
    process.exit(2);
  }
  let rules;
  try {
    rules = JSON.parse(fs.readFileSync(rulesPath, "utf8"));
  } catch (err) {
    console.error(`Rules file is not valid JSON: ${err.message}`);
    process.exit(2);
  }
  if (!rules || typeof rules.rules !== "object") {
    console.error('Rules file must contain a "rules" object.');
    process.exit(2);
  }
  const databaseUrl = opts.databaseUrl || `https://${sa.project_id}${DB_SUFFIX}`;

  if (opts.dryRun) {
    console.log("DRY RUN — would publish to:");
    console.log(`  ${databaseUrl}/.settings/rules.json`);
    console.log(JSON.stringify(rules, null, 2));
    return;
  }

  console.log("Exchanging service-account JWT for an access token ...");
  const accessToken = await exchangeToken(sa.client_email, sa.private_key);

  const current = await fetchRules(databaseUrl, accessToken);
  console.log(
    "Current rules top-level keys:",
    Object.keys((current && current.rules) || {}).join(", ") || "(none)",
  );

  console.log(`Publishing rules to ${databaseUrl} ...`);
  await publishRules(databaseUrl, accessToken, rules);
  console.log("Rules published. The app admin tab + restaurant writes are now allowed.");
}

/* ------------------------------------------------------------------- main */

async function main() {
  let opts;
  try {
    opts = parseArgs(process.argv.slice(2));
  } catch (err) {
    console.error(`Error: ${err.message}\n\n${USAGE}`);
    process.exit(2);
  }
  if (opts.help) {
    console.log(USAGE);
    process.exit(0);
  }
  if (!opts.serviceAccount) {
    console.error(`Missing service account file.\n\n${USAGE}`);
    process.exit(2);
  }

  const saPath = path.resolve(opts.serviceAccount);
  if (!fs.existsSync(saPath)) {
    console.error(`Service account file not found: ${saPath}`);
    process.exit(2);
  }
  const sa = JSON.parse(fs.readFileSync(saPath, "utf8"));

  if (opts.subcommand === "publish-rules") {
    await publishRulesMain(opts, sa);
    return;
  }

  if (opts.createUser) {
    if (!opts.email || !opts.password || !opts.apiKey) {
      console.error("--create-user requires --email, --password and --api-key.");
      process.exit(2);
    }
  }

  if (opts.menuFile) opts.menuItems = loadMenu(opts.menuFile);

  const payload = buildPayload(opts);
  const databaseUrl =
    opts.databaseUrl || `https://${sa.project_id}${DB_SUFFIX}`;

  if (opts.dryRun) {
    console.log("DRY RUN — would write:");
    console.log(`  databaseUrl: ${databaseUrl}`);
    console.log(JSON.stringify(payload, null, 2));
    return;
  }

  let uid = opts.uid;
  if (opts.createUser) {
    console.log(`Creating Auth account ${opts.email} ...`);
    uid = await createUser(opts.apiKey, opts.email, opts.password);
    console.log(`  created uid: ${uid}`);
  }

  console.log("Exchanging service-account JWT for an access token ...");
  const accessToken = await exchangeToken(sa.client_email, sa.private_key);

  console.log(`Seeding tenant "${opts.restaurant}" ...`);
  const written = await applyUpdate(databaseUrl, accessToken, payload);
  const paths = Object.keys(written).length;
  console.log(`Done. Wrote ${paths} paths.`);

  console.log(`
Restaurant "Name": ${opts.name}
Manager uid:       ${uid}
Table QRs:         https://harissdq.github.io/DigiMenu/?restaurant=${opts.restaurant}&table=<id>
Take Away QR:      https://harissdq.github.io/DigiMenu/?restaurant=${opts.restaurant}&takeaway=1
`);
}

const isEntryPoint =
  process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;

if (isEntryPoint) {
  main().catch((err) => {
    console.error(`Error: ${err.message}`);
    process.exit(1);
  });
}
