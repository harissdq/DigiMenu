/* DigiMenu customer web app: scan QR -> lead capture -> live menu -> order.
 *
 * Works in two modes:
 *  - Firebase mode: talks to the restaurant's Realtime Database (config.js).
 *  - DEMO mode: no Firebase config needed - uses a sample menu and stores
 *    orders in localStorage so the whole flow can be tried immediately.
 */
(function () {
  "use strict";

  var RESTAURANT_ID = "demo-restaurant"; // tenant id, overridden from ?restaurant=
  var IS_TAKEAWAY = false; // set from ?takeaway=1 (public take-away QR)
  var STATUS_NEW = "NEW";
  var STATUS_ACCEPTED = "ACCEPTED";
  var STATUS_PREPARING = "PREPARING";
  var STATUS_READY = "READY";
  var STATUS_DONE = "DONE";
  var STATUS_CANCELLED = "CANCELLED";
  var STATUS_REJECTED = "REJECTED";

  // Customer-facing happy path; the current step is highlighted by status.
  var ORDER_TRACK_STEPS = [
    { key: STATUS_NEW, label: "Placed" },
    { key: STATUS_ACCEPTED, label: "Accepted" },
    { key: STATUS_PREPARING, label: "Preparing" },
    { key: STATUS_READY, label: "Ready" },
    { key: STATUS_DONE, label: "Completed" }
  ];

  var CONFIG = window.DIGIMENU_FIREBASE_CONFIG || {};
  var IS_DEMO =
    !CONFIG.apiKey || String(CONFIG.apiKey).indexOf("YOUR_") === 0 ||
    !CONFIG.databaseURL || String(CONFIG.databaseURL).indexOf("YOUR_") === 0;

  var DEMO_MENU = [
    { id: "demo-item-1", name: "Chicken Karahi", description: "Served with fresh naan", price: 1800, category: "Mains", available: true },
    { id: "demo-item-2", name: "Chicken Biryani", description: "Fragrant rice, spicy masala", price: 950, category: "Mains", available: true },
    { id: "demo-item-3", name: "Tandoori Chicken", description: "Half, char-grilled", price: 1400, category: "Grills", available: true },
    { id: "demo-item-4", name: "Vegetable Daal", description: "Yellow lentils, tadka", price: 500, category: "Mains", available: true },
    { id: "demo-item-5", name: "Chai", description: "Fresh, hot, with cardamom", price: 150, category: "Drinks", available: true },
    { id: "demo-item-6", name: "Cold Drink", description: "500 ml, chilled", price: 120, category: "Drinks", available: false }
  ];

  var $ = function (id) { return document.getElementById(id); };

  var tableId = null;
  var tableLabel = null;
  var lead = { name: "", phone: "" };
  var cart = {}; // itemId -> { item, qty }
  var db = null;

  /* ---------- helpers ---------- */

  function show(id) {
    var sections = document.querySelectorAll(".screen");
    for (var i = 0; i < sections.length; i++) {
      sections[i].classList.toggle("hidden", sections[i].id !== id);
    }
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function parseTableId() {
    var params = new URLSearchParams(window.location.search);
    return (params.get("table") || "").trim();
  }

  function formatRs(value) {
    return "Rs. " + Number(value || 0).toFixed(2);
  }

  function currency(value) {
    return "Rs. " + value.toFixed(2);
  }

  /* ---------- lifecycle ---------- */

  function init() {
    if (IS_DEMO) {
      var banner = document.createElement("div");
      banner.className = "demo-banner";
      banner.textContent = "DEMO MODE - using a sample menu. Add your Firebase web config to web/config.js to go live.";
      document.body.insertBefore(banner, document.body.firstChild);
    } else {
      firebase.initializeApp(CONFIG);
      db = firebase.database();
    }

    var params = new URLSearchParams(window.location.search);
    var restaurantParam = (params.get("restaurant") || "").trim();
    if (restaurantParam) RESTAURANT_ID = restaurantParam;
    IS_TAKEAWAY = params.get("takeaway") === "1" || params.get("takeaway") === "true";

    if (IS_TAKEAWAY) {
      // Public take-away QR: no table, customer orders from home.
      tableLabel = "Take Away";
      $("table-label").textContent = "Take Away";
      $("lead-table").textContent = "Take Away";
      $("lead-address").classList.remove("hidden");
      show("lead");
      return;
    }

    tableId = parseTableId();
    if (!tableId) {
      $("table-error-msg").textContent =
        "This QR code does not point to a table. Please ask the staff.";
      show("table-error");
      return;
    }

    $("table-label").textContent = tableId;
    $("lead-table").textContent = tableId;

    verifyTable()
      .then(function (label) {
        tableLabel = label || tableId;
        show("lead");
      })
      .catch(function (err) {
        $("table-error-msg").textContent =
          "Table " + tableId + " was not found (" + err.message + "). Please ask the staff.";
        show("table-error");
      });
  }

  function verifyTable() {
    if (IS_DEMO) {
      return Promise.resolve(tableId);
    }
    return db.ref("restaurants/" + RESTAURANT_ID + "/tables/" + tableId)
      .once("value")
      .then(function (snap) {
        if (!snap.exists()) {
          throw new Error("not in the table registry");
        }
        var val = snap.val();
        return val && val.label ? val.label : tableId;
      });
  }

  /* ---------- lead capture ---------- */

  function isValidPhone(phone) {
    return phone.replace(/\D/g, "").length >= 7;
  }

  function submitLead(event) {
    event.preventDefault();
    var name = $("lead-name").value.trim();
    var phone = $("lead-phone").value.trim();
    var address = $("lead-address").value.trim();
    $("lead-error").textContent = "";
    if (!name) { $("lead-error").textContent = "Please enter your name."; return; }
    if (!isValidPhone(phone)) { $("lead-error").textContent = "Please enter a valid phone number."; return; }
    if (IS_TAKEAWAY && !address) { $("lead-error").textContent = "Please enter your delivery address."; return; }
    lead = { name: name, phone: phone, address: address };
    startMenu();
  }

  /* ---------- live menu ---------- */

  function startMenu() {
    show("menu");
    if (IS_DEMO) {
      renderMenu({
        forEach: function (cb) {
          DEMO_MENU.forEach(function (item) {
            cb({ key: item.id, val: function () { return item; } });
          });
        }
      });
    } else {
      db.ref("restaurants/" + RESTAURANT_ID + "/menu")
        .on("value", renderMenu);
    }
  }

  function renderMenu(snapshot) {
    var items = [];
    snapshot.forEach(function (child) {
      var v = child.val();
      if (v) {
        items.push({
          id: child.key,
          name: v.name || "Unnamed",
          description: v.description || "",
          price: Number(v.price) || 0,
          category: v.category || "Main",
          available: v.available !== false
        });
      }
    });

    var available = items.filter(function (i) { return i.available; });
    $("menu-empty").classList.toggle("hidden", available.length > 0);

    var container = $("menu-list");
    container.innerHTML = "";

    var byCategory = {};
    available.forEach(function (item) {
      (byCategory[item.category] = byCategory[item.category] || []).push(item);
    });

    Object.keys(byCategory).sort().forEach(function (category) {
      var heading = document.createElement("div");
      heading.className = "menu-category";
      heading.textContent = category;
      container.appendChild(heading);

      byCategory[category].forEach(function (item) {
        container.appendChild(buildMenuItem(item));
      });
    });
  }

  function buildMenuItem(item) {
    var card = document.createElement("div");
    card.className = "menu-item";
    card.id = "item-" + item.id;

    var info = document.createElement("div");
    info.className = "menu-item-info";

    var nameRow = document.createElement("div");
    nameRow.className = "menu-item-name";
    nameRow.innerHTML = escapeHtml(item.name) +
      (item.available ? "" : '<span class="badge">OUT OF STOCK</span>');

    info.appendChild(nameRow);

    if (item.description) {
      var desc = document.createElement("div");
      desc.className = "menu-item-desc";
      desc.textContent = item.description;
      info.appendChild(desc);
    }

    var price = document.createElement("div");
    price.className = "menu-item-price";
    price.textContent = formatRs(item.price);
    info.appendChild(price);

    card.appendChild(info);

    var controls = document.createElement("div");
    controls.className = "stepper";

    if (!item.available) {
      card.classList.add("out");
    } else {
      var addBtn = document.createElement("button");
      addBtn.className = "btn-add";
      addBtn.textContent = "Add";
      addBtn.addEventListener("click", function () { addToCart(item); });
      controls.appendChild(addBtn);
    }

    card.appendChild(controls);
    return card;
  }

  function refreshControls(item) {
    var card = $("item-" + item.id);
    if (!card) return;
    var stepper = card.querySelector(".stepper");
    stepper.innerHTML = "";

    var entry = cart[item.id];
    var qty = entry ? entry.qty : 0;

    if (qty === 0) {
      var addBtn = document.createElement("button");
      addBtn.className = "btn-add";
      addBtn.textContent = "Add";
      addBtn.addEventListener("click", function () { addToCart(item); });
      stepper.appendChild(addBtn);
    } else {
      var minus = document.createElement("button");
      minus.textContent = "\u2212";
      minus.addEventListener("click", function () { removeFromCart(item); });

      var qtySpan = document.createElement("span");
      qtySpan.className = "qty";
      qtySpan.textContent = String(qty);

      var plus = document.createElement("button");
      plus.textContent = "+";
      plus.addEventListener("click", function () { addToCart(item); });

      stepper.appendChild(minus);
      stepper.appendChild(qtySpan);
      stepper.appendChild(plus);
    }
  }

  /* ---------- cart ---------- */

  function addToCart(item) {
    if (cart[item.id]) {
      cart[item.id].qty += 1;
    } else {
      cart[item.id] = { item: item, qty: 1 };
    }
    refreshControls(item);
    refreshCart();
  }

  function removeFromCart(item) {
    if (!cart[item.id]) return;
    cart[item.id].qty -= 1;
    if (cart[item.id].qty <= 0) {
      delete cart[item.id];
    }
    refreshControls(item);
    refreshCart();
  }

  function cartLines() {
    return Object.keys(cart).map(function (id) { return cart[id]; });
  }

  function refreshCart() {
    var lines = cartLines();
    var count = lines.reduce(function (sum, l) { return sum + l.qty; }, 0);
    var total = lines.reduce(function (sum, l) { return sum + l.qty * l.item.price; }, 0);
    $("cart-count").textContent = count + " item" + (count === 1 ? "" : "s");
    $("cart-total").textContent = currency(total);
    $("cart-bar").classList.toggle("hidden", count === 0);
  }

  /* ---------- order ---------- */

  function placeOrder() {
    var button = $("place-order");
    var lines = cartLines();
    if (lines.length === 0) return;

    button.disabled = true;
    button.textContent = "Placing order...";

    var items = {};
    lines.forEach(function (line) {
      items[line.item.id] = {
        name: line.item.name,
        price: line.item.price,
        qty: line.qty
      };
    });

    var total = lines.reduce(function (sum, l) { return sum + l.qty * l.item.price; }, 0);

    var order = {
      orderType: IS_TAKEAWAY ? "takeaway" : "dine-in",
      tableId: IS_TAKEAWAY ? "TAKEAWAY" : tableId,
      tableLabel: IS_TAKEAWAY ? "Take Away" : tableLabel,
      customerName: lead.name,
      customerPhone: lead.phone,
      address: IS_TAKEAWAY ? lead.address : "",
      items: items,
      total: total,
      status: STATUS_NEW,
      createdAt: Date.now()
    };

    var done = function (order) {
      $("confirm-table").textContent = tableLabel;
      var confirmText = "Order for " + lead.name + " (" + lead.phone + ")";
      if (IS_TAKEAWAY) confirmText += " \u2014 deliver to: " + lead.address;
      $("confirm-number").textContent = confirmText;
      cart = {};
      refreshCart();
      show("confirmation");
      startTracking(order);
      button.disabled = false;
      button.textContent = "Place order";
    };

    if (IS_DEMO) {
      window.setTimeout(function () {
        try {
          var history = JSON.parse(localStorage.getItem("digimenu_demo_orders") || "[]");
          history.push(order);
          localStorage.setItem("digimenu_demo_orders", JSON.stringify(history));
        } catch (e) { /* storage may be unavailable; still confirm */ }
        done(order);
      }, 600);
      return;
    }

    // push() first so we own the key: the customer keeps it to follow their
    // order's live status on the confirmation screen.
    var newRef = db.ref("restaurants/" + RESTAURANT_ID + "/orders").push();
    order.id = newRef.key;
    newRef.set(order)
      .then(function () { done(order); })
      .catch(function (err) {
        button.disabled = false;
        button.textContent = "Place order";
        alert("Order failed: " + err.message);
      });
  }

  /* ---------- live order status (confirmation screen) ---------- */

  // The full order node is manager/admin-only, so the tracker reads the two
  // public status fields individually (rules expose only those). Tracking works
  // because the order key is the (effectively unguessable) push id.
  var trackingRefs = [];

  function startTracking(order) {
    if (IS_DEMO) {
      renderOrderStatus(order);
      return;
    }
    stopTracking();
    var statusRef = db.ref("restaurants/" + RESTAURANT_ID + "/orders/" + order.id + "/status");
    var reasonRef = db.ref("restaurants/" + RESTAURANT_ID + "/orders/" + order.id + "/declineReason");

    var render = function () {
      statusRef.once("value").then(function (snap) {
        var status = snap.val() || STATUS_NEW;
        return reasonRef.once("value").then(function (reasonSnap) {
          renderOrderStatus({ status: status, declineReason: reasonSnap.val() || "" });
        });
      });
    };

    statusRef.on("value", render);
    reasonRef.on("value", render);
    trackingRefs.push({ ref: statusRef, cb: render });
    trackingRefs.push({ ref: reasonRef, cb: render });
  }

  function stopTracking() {
    for (var i = 0; i < trackingRefs.length; i++) {
      trackingRefs[i].ref.off("value", trackingRefs[i].cb);
    }
    trackingRefs = [];
  }

  function renderOrderStatus(order) {
    var container = $("order-status");
    container.innerHTML = "";

    if (order.status === STATUS_REJECTED) {
      var rejected = document.createElement("div");
      rejected.className = "status-alert";
      var rejectedTitle = document.createElement("div");
      rejectedTitle.className = "status-alert-title";
      rejectedTitle.textContent = "Order rejected";
      rejected.appendChild(rejectedTitle);
      rejected.appendChild(document.createTextNode(
        order.declineReason ? "Reason: " + order.declineReason : "Please contact the restaurant."
      ));
      container.appendChild(rejected);
      return;
    }

    if (order.status === STATUS_CANCELLED) {
      var cancelled = document.createElement("div");
      cancelled.className = "status-alert";
      var cancelledTitle = document.createElement("div");
      cancelledTitle.className = "status-alert-title";
      cancelledTitle.textContent = "Order cancelled";
      cancelled.appendChild(cancelledTitle);
      cancelled.appendChild(document.createTextNode("Please contact the restaurant."));
      container.appendChild(cancelled);
      return;
    }

    var currentIndex = -1;
    for (var i = 0; i < ORDER_TRACK_STEPS.length; i++) {
      if (ORDER_TRACK_STEPS[i].key === order.status) currentIndex = i;
    }
    if (currentIndex === -1) currentIndex = 0;

    var steps = document.createElement("div");
    steps.className = "status-steps";
    ORDER_TRACK_STEPS.forEach(function (step, i) {
      var el = document.createElement("div");
      el.className = "status-step";
      if (i < currentIndex) el.classList.add("done");
      if (i === currentIndex) el.classList.add("current");

      var dot = document.createElement("div");
      dot.className = "status-dot";
      dot.textContent = i < currentIndex ? "\u2713" : String(i + 1);

      var label = document.createElement("div");
      label.className = "status-label";
      label.textContent = step.label;

      el.appendChild(dot);
      el.appendChild(label);
      steps.appendChild(el);
    });
    container.appendChild(steps);

    var note = document.createElement("p");
    note.className = "muted";
    note.textContent =
      "Keep this page open — it updates automatically as the restaurant prepares your order.";
    container.appendChild(note);
  }

  /* ---------- connectivity banner ---------- */

  function setOnline(online) {
    var banner = $("offline-banner");
    if (banner) banner.classList.toggle("hidden", online);
  }

  /* ---------- wire up ---------- */

  $("lead-form").addEventListener("submit", submitLead);
  $("place-order").addEventListener("click", placeOrder);
  window.addEventListener("online", function () { setOnline(true); });
  window.addEventListener("offline", function () { setOnline(false); });

  init();
})();
