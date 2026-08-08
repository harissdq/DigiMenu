/* DigiMenu customer web app: scan QR -> lead capture -> live menu -> order. */
(function () {
  "use strict";

  var RESTAURANT_ID = "demo-restaurant"; // must match FirebaseRefs.DEFAULT_RESTAURANT
  var STATUS_NEW = "NEW";

  var CONFIG = window.DIGIMENU_FIREBASE_CONFIG || {};
  var configIsPlaceholder =
    !CONFIG.apiKey || CONFIG.apiKey.indexOf("YOUR_") === 0 ||
    !CONFIG.databaseURL || CONFIG.databaseURL.indexOf("YOUR_") === 0;

  var $ = function (id) { return document.getElementById(id); };

  var tableId = null;
  var tableLabel = null;
  var lead = { name: "", phone: "" };
  var cart = {}; // itemId -> { item, qty }
  var db = null;

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
    var raw = params.get("table") || "";
    return raw.trim();
  }

  function formatRs(value) {
    return "Rs. " + Number(value || 0).toFixed(2);
  }

  function currency(value) {
    return "Rs. " + value.toFixed(2);
  }

  /* ---------- lifecycle ---------- */

  function init() {
    tableId = parseTableId();
    if (!tableId) {
      $("table-error-msg").textContent =
        "This QR code does not point to a table. Please ask the staff.";
      show("table-error");
      return;
    }
    if (configIsPlaceholder) {
      show("setup-error");
      return;
    }

    firebase.initializeApp(CONFIG);
    db = firebase.database();

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
    $("lead-error").textContent = "";
    if (!name) { $("lead-error").textContent = "Please enter your name."; return; }
    if (!isValidPhone(phone)) { $("lead-error").textContent = "Please enter a valid phone number."; return; }
    lead = { name: name, phone: phone };
    startMenu();
  }

  /* ---------- live menu ---------- */

  function startMenu() {
    show("menu");
    db.ref("restaurants/" + RESTAURANT_ID + "/menu")
      .on("value", renderMenu);
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
      tableId: tableId,
      tableLabel: tableLabel,
      customerName: lead.name,
      customerPhone: lead.phone,
      items: items,
      total: total,
      status: STATUS_NEW,
      createdAt: Date.now()
    };

    db.ref("restaurants/" + RESTAURANT_ID + "/orders").push().set(order)
      .then(function () {
        $("confirm-table").textContent = tableLabel;
        $("confirm-number").textContent = "Order for " + lead.name + " (" + lead.phone + ")";
        cart = {};
        refreshCart();
        show("confirmation");
        button.disabled = false;
        button.textContent = "Place order";
      })
      .catch(function (err) {
        button.disabled = false;
        button.textContent = "Place order";
        alert("Order failed: " + err.message);
      });
  }

  /* ---------- wire up ---------- */

  $("lead-form").addEventListener("submit", submitLead);
  $("place-order").addEventListener("click", placeOrder);

  init();
})();
