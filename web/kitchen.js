/* DigiMenu kitchen display (P5): a live queue of active orders for the kitchen.
 *
 * Reads the same manager-only orders node as the Android dashboard, so staff
 * sign in with a restaurant manager account (see FIREBASE_SETUP.md). Moving an
 * order forward only ever uses legal OrderStatus transitions:
 *   NEW -> ACCEPTED -> PREPARING -> READY
 * No build step; plain browser JS.
 */
(function () {
  "use strict";

  var CONFIG = window.DIGIMENU_FIREBASE_CONFIG || {};
  var IS_DEMO =
    !CONFIG.apiKey || String(CONFIG.apiKey).indexOf("YOUR_") === 0 ||
    !CONFIG.databaseURL || String(CONFIG.databaseURL).indexOf("YOUR_") === 0;

  var STATUS_NEW = "NEW";
  var STATUS_ACCEPTED = "ACCEPTED";
  var STATUS_PREPARING = "PREPARING";
  var STATUS_READY = "READY";

  var $ = function (id) { return document.getElementById(id); };
  var db = null;
  var ordersRef = null;
  var seenOrderIds = {};

  function show(id) {
    var sections = document.querySelectorAll(".screen");
    for (var i = 0; i < sections.length; i++) {
      sections[i].classList.toggle("hidden", sections[i].id !== id);
    }
  }

  function formatTime(ms) {
    if (!ms) return "";
    var d = new Date(ms);
    function pad(n) { return (n < 10 ? "0" : "") + n; }
    return pad(d.getHours()) + ":" + pad(d.getMinutes());
  }

  function init() {
    if (IS_DEMO) {
      $("kds-error").textContent =
        "The kitchen display needs a real Firebase config (web/config.js) - demo mode has no backend.";
      return;
    }
    firebase.initializeApp(CONFIG);
    db = firebase.database();
    firebase.auth().onAuthStateChanged(function (user) {
      if (user) { startQueue(user.uid); } else { show("kds-login"); }
    });

    $("kds-login-btn").addEventListener("click", login);
    $("kds-password").addEventListener("keydown", function (e) {
      if (e.key === "Enter") login();
    });
  }

  function login() {
    var email = $("kds-email").value.trim();
    var password = $("kds-password").value;
    $("kds-error").textContent = "";
    if (!email || !password) {
      $("kds-error").textContent = "Enter your email and password.";
      return;
    }
    var btn = $("kds-login-btn");
    btn.disabled = true;
    btn.textContent = "Signing in\u2026";
    firebase.auth().signInWithEmailAndPassword(email, password)
      .catch(function (err) {
        btn.disabled = false;
        btn.textContent = "Sign in";
        $("kds-error").textContent = err.message;
      });
  }

  function startQueue(uid) {
    db.ref("managers/" + uid + "/restaurantId").once("value", function (snap) {
      var restaurantId = snap.val();
      if (!restaurantId) {
        firebase.auth().signOut();
        $("kds-error").textContent = "This account is not linked to a restaurant.";
        return;
      }
      $("kitchen-status").textContent = restaurantId;
      show("kds-queue");
      ordersRef = db.ref("restaurants/" + restaurantId + "/orders");
      ordersRef.on("value", renderQueue);
    }, function () {
      $("kds-error").textContent = "Could not read the restaurant for this account.";
    });
  }

  function renderQueue(snapshot) {
    var active = [];
    var preparing = [];
    var ready = [];

    snapshot.forEach(function (child) {
      var v = child.val();
      if (!v) return;
      var status = v.status || STATUS_NEW;
      if (status === "DONE" || status === "CANCELLED" || status === "REJECTED") return;

      var order = {
        id: child.key,
        tableLabel: v.tableLabel,
        items: v.items || {},
        status: status,
        createdAt: v.createdAt || 0
      };

      if (status === STATUS_NEW) {
        if (!seenOrderIds[child.key]) playBeep();
        active.push(order);
      } else if (status === STATUS_ACCEPTED) {
        active.push(order);
      } else if (status === STATUS_PREPARING) {
        preparing.push(order);
      } else if (status === STATUS_READY) {
        ready.push(order);
      }
      seenOrderIds[child.key] = true;
    });

    function byOldest(a, b) { return (a.createdAt || 0) - (b.createdAt || 0); }
    active.sort(byOldest);
    preparing.sort(byOldest);
    ready.sort(byOldest);

    var list = $("kitchen-list");
    list.innerHTML = "";
    $("kds-empty").classList.toggle("hidden", active.length + preparing.length + ready.length > 0);

    appendSection(list, "To prepare", active);
    appendSection(list, "Preparing", preparing);
    appendSection(list, "Ready", ready);
  }

  function appendSection(list, title, orders) {
    if (orders.length === 0) return;
    var header = document.createElement("div");
    header.className = "kitchen-section";
    header.textContent = title + " (" + orders.length + ")";
    list.appendChild(header);
    orders.forEach(function (order) { list.appendChild(orderCard(order)); });
  }

  function orderCard(order) {
    var card = document.createElement("div");
    card.className = "kitchen-card" + (order.status === STATUS_READY ? " ready" : "");

    var head = document.createElement("div");
    head.className = "kitchen-card-head";
    var label = document.createElement("span");
    label.className = "kitchen-label";
    label.textContent = order.tableLabel || "Take Away";
    var time = document.createElement("span");
    time.className = "kitchen-time";
    time.textContent = formatTime(order.createdAt);
    head.appendChild(label);
    head.appendChild(time);
    card.appendChild(head);

    Object.keys(order.items).forEach(function (key) {
      var line = order.items[key];
      var row = document.createElement("div");
      row.className = "kitchen-line";
      row.textContent = "\u00d7 " + (line.qty || 0) + "  " + (line.name || "Item");
      card.appendChild(row);
    });

    card.appendChild(actionFor(order));
    return card;
  }

  function actionFor(order) {
    var action = document.createElement("button");
    action.className = "kitchen-action";
    if (order.status === STATUS_NEW) {
      action.textContent = "Accept order";
      action.addEventListener("click", function () {
        setStatus(order.id, STATUS_ACCEPTED);
      });
    } else if (order.status === STATUS_ACCEPTED) {
      action.textContent = "Start preparing";
      action.addEventListener("click", function () {
        setStatus(order.id, STATUS_PREPARING);
      });
    } else if (order.status === STATUS_PREPARING) {
      action.textContent = "Ready";
      action.addEventListener("click", function () {
        setStatus(order.id, STATUS_READY);
      });
    }
    return action;
  }

  function setStatus(orderId, status) {
    if (!ordersRef) return;
    ordersRef.child(orderId).update({
      status: status,
      statusChangedAt: Date.now()
    });
  }

  function playBeep() {
    try {
      var ctx = new (window.AudioContext || window.webkitAudioContext)();
      var osc = ctx.createOscillator();
      var gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.type = "triangle";
      osc.frequency.value = 660;
      gain.gain.setValueAtTime(0.25, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.4);
      osc.start();
      osc.stop(ctx.currentTime + 0.4);
    } catch (e) { /* audio unavailable; queue still updates visually */ }
  }

  init();
})();
