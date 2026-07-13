"use strict";
/* Rumb desktop mode — vanilla SPA. Served by the phone's embedded server. */

/* ============================================================= *
 *  API helper                                                   *
 * ============================================================= */
async function api(path, opts) {
  const o = Object.assign({ credentials: "include" }, opts || {});
  const res = await fetch(path, o);
  if (res.status === 401) {
    showLogin();
    throw { auth: true };
  }
  return res;
}
async function apiJson(path, opts) {
  const res = await api(path, opts);
  if (!res.ok) throw new Error("HTTP " + res.status);
  return res.json();
}

/* ============================================================= *
 *  Format helpers (all null-guarded)                            *
 * ============================================================= */
function pad2(n) { return n < 10 ? "0" + n : "" + n; }

// milliseconds -> H:MM:SS or M:SS
function hms(ms) {
  if (ms == null || isNaN(ms)) return "—";
  let s = Math.round(ms / 1000);
  const h = Math.floor(s / 3600); s -= h * 3600;
  const m = Math.floor(s / 60); s -= m * 60;
  return h > 0 ? h + ":" + pad2(m) + ":" + pad2(s) : m + ":" + pad2(s);
}
// seconds -> H:MM:SS
function hmsSec(sec) { return sec == null ? "—" : hms(sec * 1000); }

// meters -> "12.3 km"
function km(m) {
  if (m == null || isNaN(m)) return "—";
  return (m / 1000).toFixed(1) + " km";
}
function kmFromKm(v, dec) {
  if (v == null || isNaN(v)) return "—";
  return v.toFixed(dec == null ? 1 : dec) + " km";
}
function num(v, dec, unit) {
  if (v == null || isNaN(v)) return "—";
  return v.toFixed(dec == null ? 0 : dec) + (unit || "");
}
function date(epochMs) {
  if (epochMs == null) return "—";
  const d = new Date(epochMs);
  return pad2(d.getDate()) + "/" + pad2(d.getMonth() + 1) + "/" + d.getFullYear();
}
function dateTime(epochMs) {
  if (epochMs == null) return "—";
  const d = new Date(epochMs);
  return date(epochMs) + " " + pad2(d.getHours()) + ":" + pad2(d.getMinutes());
}
function esc(s) {
  return (s == null ? "" : String(s)).replace(/[&<>"']/g, c =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

const ACT_ICON = {
  running: "🏃", trail_running: "🏃", walking: "🚶", hiking: "🥾", trekking: "🥾",
  cycling: "🚴", biking: "🚴", mountain_biking: "🚵", mtb: "🚵",
  swimming: "🏊", skiing: "⛷️", default: "📍"
};
function actIcon(t) {
  if (!t) return ACT_ICON.default;
  const k = t.toLowerCase();
  for (const key in ACT_ICON) if (k.indexOf(key) >= 0) return ACT_ICON[key];
  return ACT_ICON.default;
}
const DIFF_LABEL = { EASY: "Fàcil", MODERATE: "Moderat", HARD: "Difícil", VERY_HARD: "Molt difícil" };
function diffBadge(d) {
  if (!d) return "";
  return '<span class="badge diff-' + esc(d) + '">' + (DIFF_LABEL[d] || esc(d)) + "</span>";
}

/* ============================================================= *
 *  Toast + misc UI helpers                                      *
 * ============================================================= */
let toastTimer = null;
function toast(msg, isErr) {
  let el = document.querySelector(".toast");
  if (el) el.remove();
  el = document.createElement("div");
  el.className = "toast" + (isErr ? " err" : "");
  el.textContent = msg;
  document.body.appendChild(el);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.remove(), 3200);
}
function loadingHtml() { return '<div class="loading">Carregant…</div>'; }
function emptyHtml(msg) { return '<div class="empty">' + esc(msg) + "</div>"; }

/* ============================================================= *
 *  Login gate                                                   *
 * ============================================================= */
const $login = document.getElementById("login");
const $app = document.getElementById("app");

function showLogin() {
  $login.classList.remove("hidden");
  $app.classList.add("hidden");
  const pin = document.getElementById("pin");
  pin.value = "";
  setTimeout(() => pin.focus(), 50);
}
function hideLogin() {
  $login.classList.add("hidden");
  $app.classList.remove("hidden");
}

document.getElementById("loginForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const pin = document.getElementById("pin").value.trim();
  const errEl = document.getElementById("loginError");
  errEl.classList.add("hidden");
  try {
    const res = await fetch("/api/auth", {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pin })
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok && data.ok) {
      hideLogin();
      boot();
    } else {
      errEl.classList.remove("hidden");
    }
  } catch (_) {
    errEl.textContent = "Error de connexió";
    errEl.classList.remove("hidden");
  }
});

/* ============================================================= *
 *  Canvas chart helpers                                         *
 * ============================================================= */
// Prepare a canvas for crisp drawing on HiDPI; returns {ctx,w,h}.
function setupCanvas(cv) {
  const dpr = window.devicePixelRatio || 1;
  const rect = cv.getBoundingClientRect();
  const w = Math.max(1, Math.round(rect.width));
  const h = Math.max(1, Math.round(rect.height));
  cv.width = w * dpr;
  cv.height = h * dpr;
  const ctx = cv.getContext("2d");
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);
  return { ctx, w, h };
}

// Generic line chart: xs/ys arrays (same length). color, fill optional.
function lineChart(cv, xs, ys, opts) {
  opts = opts || {};
  const { ctx, w, h } = setupCanvas(cv);
  const pad = { l: 42, r: 10, t: 10, b: 20 };
  const iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
  const pts = [];
  for (let i = 0; i < ys.length; i++) if (ys[i] != null && !isNaN(ys[i])) pts.push([xs[i], ys[i]]);
  if (pts.length < 2) {
    ctx.fillStyle = "#8b96a5"; ctx.font = "12px sans-serif";
    ctx.fillText("Sense dades", pad.l, pad.t + ih / 2);
    return;
  }
  const xmin = xs[0], xmax = xs[xs.length - 1] || 1;
  let ymin = Infinity, ymax = -Infinity;
  for (const p of pts) { if (p[1] < ymin) ymin = p[1]; if (p[1] > ymax) ymax = p[1]; }
  if (ymin === ymax) { ymin -= 1; ymax += 1; }
  const sx = v => pad.l + (xmax === xmin ? 0 : (v - xmin) / (xmax - xmin)) * iw;
  const sy = v => pad.t + ih - (v - ymin) / (ymax - ymin) * ih;

  // grid + y labels
  ctx.strokeStyle = "#2a323d"; ctx.fillStyle = "#8b96a5";
  ctx.font = "10px sans-serif"; ctx.lineWidth = 1;
  for (let i = 0; i <= 3; i++) {
    const yv = ymin + (ymax - ymin) * i / 3;
    const y = sy(yv);
    ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(w - pad.r, y); ctx.stroke();
    ctx.fillText((opts.fmt ? opts.fmt(yv) : yv.toFixed(0)), 4, y + 3);
  }
  // x labels (km)
  ctx.textAlign = "center";
  for (let i = 0; i <= 4; i++) {
    const xv = xmin + (xmax - xmin) * i / 4;
    ctx.fillText((xv / 1000).toFixed(1), sx(xv), h - 5);
  }
  ctx.textAlign = "left";

  const color = opts.color || "#E63946";
  if (opts.fill) {
    const grad = ctx.createLinearGradient(0, pad.t, 0, pad.t + ih);
    grad.addColorStop(0, opts.fill);
    grad.addColorStop(1, "rgba(0,0,0,0)");
    ctx.beginPath();
    ctx.moveTo(sx(pts[0][0]), sy(pts[0][1]));
    for (const p of pts) ctx.lineTo(sx(p[0]), sy(p[1]));
    ctx.lineTo(sx(pts[pts.length - 1][0]), pad.t + ih);
    ctx.lineTo(sx(pts[0][0]), pad.t + ih);
    ctx.closePath(); ctx.fillStyle = grad; ctx.fill();
  }
  ctx.beginPath();
  ctx.moveTo(sx(pts[0][0]), sy(pts[0][1]));
  for (const p of pts) ctx.lineTo(sx(p[0]), sy(p[1]));
  ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.lineJoin = "round"; ctx.stroke();
}

// Bar chart: labels[], values[]. color.
function barChart(cv, labels, values, opts) {
  opts = opts || {};
  const { ctx, w, h } = setupCanvas(cv);
  const pad = { l: 34, r: 10, t: 10, b: 24 };
  const iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
  let ymax = 0;
  for (const v of values) if (v > ymax) ymax = v;
  if (ymax === 0) ymax = 1;
  const n = values.length;
  const bw = iw / n * 0.66;
  const step = iw / n;

  ctx.strokeStyle = "#2a323d"; ctx.fillStyle = "#8b96a5"; ctx.font = "10px sans-serif";
  for (let i = 0; i <= 3; i++) {
    const yv = ymax * i / 3, y = pad.t + ih - (yv / ymax) * ih;
    ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(w - pad.r, y); ctx.stroke();
    ctx.fillText(yv.toFixed(0), 4, y + 3);
  }
  ctx.textAlign = "center";
  for (let i = 0; i < n; i++) {
    const x = pad.l + step * i + (step - bw) / 2;
    const bh = (values[i] / ymax) * ih;
    ctx.fillStyle = opts.color || "#E63946";
    ctx.fillRect(x, pad.t + ih - bh, bw, bh);
    if (labels[i] != null && (n <= 12)) {
      ctx.fillStyle = "#8b96a5";
      ctx.fillText(labels[i], x + bw / 2, h - 8);
    }
  }
  ctx.textAlign = "left";
}

// Gap chart: green fill below zero (faster), red above (slower).
function gapChart(cv, gaps) {
  const { ctx, w, h } = setupCanvas(cv);
  const pad = { l: 46, r: 10, t: 12, b: 22 };
  const iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
  if (!gaps || gaps.length < 2) {
    ctx.fillStyle = "#8b96a5"; ctx.font = "12px sans-serif";
    ctx.fillText("Sense dades de diferència", pad.l, pad.t + ih / 2);
    return;
  }
  const xmax = gaps[gaps.length - 1].distM || 1;
  let gmin = Infinity, gmax = -Infinity;
  for (const g of gaps) { if (g.gapSeconds < gmin) gmin = g.gapSeconds; if (g.gapSeconds > gmax) gmax = g.gapSeconds; }
  if (gmin > 0) gmin = 0; if (gmax < 0) gmax = 0;
  if (gmin === gmax) { gmin -= 1; gmax += 1; }
  const sx = v => pad.l + (v / xmax) * iw;
  const sy = v => pad.t + ih - (v - gmin) / (gmax - gmin) * ih;

  // y grid + labels (seconds)
  ctx.strokeStyle = "#2a323d"; ctx.fillStyle = "#8b96a5"; ctx.font = "10px sans-serif";
  for (let i = 0; i <= 4; i++) {
    const yv = gmin + (gmax - gmin) * i / 4, y = sy(yv);
    ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(w - pad.r, y); ctx.stroke();
    ctx.fillText((yv > 0 ? "+" : "") + yv.toFixed(0) + "s", 4, y + 3);
  }
  ctx.textAlign = "center";
  for (let i = 0; i <= 4; i++) {
    const xv = xmax * i / 4;
    ctx.fillText((xv / 1000).toFixed(1), sx(xv), h - 5);
  }
  ctx.textAlign = "left";

  const zeroY = sy(0);
  // filled area split at zero line
  function fillArea(sign, color) {
    ctx.beginPath();
    ctx.moveTo(sx(gaps[0].distM), zeroY);
    for (const g of gaps) {
      const gv = sign > 0 ? Math.max(0, g.gapSeconds) : Math.min(0, g.gapSeconds);
      ctx.lineTo(sx(g.distM), sy(gv));
    }
    ctx.lineTo(sx(gaps[gaps.length - 1].distM), zeroY);
    ctx.closePath(); ctx.fillStyle = color; ctx.fill();
  }
  fillArea(-1, "rgba(46,204,113,.35)");  // below zero = faster = green
  fillArea(1, "rgba(230,57,70,.35)");    // above zero = slower = red

  // line
  ctx.beginPath();
  ctx.moveTo(sx(gaps[0].distM), sy(gaps[0].gapSeconds));
  for (const g of gaps) ctx.lineTo(sx(g.distM), sy(g.gapSeconds));
  ctx.strokeStyle = "#e6edf3"; ctx.lineWidth = 1.5; ctx.stroke();

  // zero line
  ctx.beginPath(); ctx.moveTo(pad.l, zeroY); ctx.lineTo(w - pad.r, zeroY);
  ctx.strokeStyle = "#8b96a5"; ctx.lineWidth = 1; ctx.setLineDash([4, 3]); ctx.stroke();
  ctx.setLineDash([]);
}

/* ============================================================= *
 *  Leaflet map management                                       *
 * ============================================================= */
const TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const TILE_ATTR = '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';
const maps = {}; // id -> L.Map (so we can tear down)

function destroyMap(key) {
  if (maps[key]) { maps[key].remove(); delete maps[key]; }
}
function newMap(el, key) {
  destroyMap(key);
  const map = L.map(el, { attributionControl: true, zoomControl: true });
  L.tileLayer(TILE_URL, { attribution: TILE_ATTR, maxZoom: 19 }).addTo(map);
  maps[key] = map;
  return map;
}
// Draw a polyline from samples; fit bounds.
function drawTrackMap(el, key, samples) {
  const map = newMap(el, key);
  const pts = (samples || []).filter(s => s.lat != null && s.lon != null).map(s => [s.lat, s.lon]);
  if (pts.length === 0) { map.setView([41.39, 2.16], 12); return map; }
  const line = L.polyline(pts, { color: "#E63946", weight: 4, opacity: .9 }).addTo(map);
  map.fitBounds(line.getBounds(), { padding: [24, 24] });
  // start / end markers
  L.circleMarker(pts[0], { radius: 6, color: "#fff", fillColor: "#2ECC71", fillOpacity: 1, weight: 2 })
    .addTo(map).bindTooltip("Inici");
  L.circleMarker(pts[pts.length - 1], { radius: 6, color: "#fff", fillColor: "#E63946", fillOpacity: 1, weight: 2 })
    .addTo(map).bindTooltip("Final");
  setTimeout(() => map.invalidateSize(), 60);
  return map;
}

/* ============================================================= *
 *  Tab routing                                                  *
 * ============================================================= */
const TABS = ["library", "routes", "competition", "records", "progress"];
let currentTab = "library";

function switchTab(tab) {
  if (TABS.indexOf(tab) < 0) tab = "library";
  currentTab = tab;
  document.querySelectorAll(".tab").forEach(b =>
    b.classList.toggle("active", b.dataset.tab === tab));
  document.querySelectorAll(".view").forEach(v =>
    v.classList.toggle("active", v.id === "view-" + tab));
  renderTab(tab);
}
document.getElementById("tabs").addEventListener("click", (e) => {
  const btn = e.target.closest(".tab");
  if (btn) switchTab(btn.dataset.tab);
});

function renderTab(tab) {
  if (tab === "library") renderLibrary();
  else if (tab === "routes") renderRoutes();
  else if (tab === "competition") renderCompetitions();
  else if (tab === "records") renderRecords();
  else if (tab === "progress") renderProgress();
}

/* ============================================================= *
 *  Shared track list + detail (used by Library & Routes)        *
 * ============================================================= */
function trackRow(t) {
  return '<tr data-id="' + t.id + '">' +
    '<td><span class="act-icon">' + actIcon(t.activityType) + "</span></td>" +
    "<td><b>" + esc(t.name) + "</b>" +
    (t.isCompetition ? '<span class="badge badge-comp">Competició</span>' : "") +
    (t.archived ? '<span class="badge badge-arch">Arxivat</span>' : "") + "</td>" +
    "<td>" + kmFromKm(t.distanceKm) + "</td>" +
    "<td>" + num(t.ascentM, 0, " m") + "</td>" +
    "<td>" + diffBadge(t.difficulty) + "</td>" +
    "<td>" + esc(t.municipality || "—") + "</td>" +
    "<td>" + date(t.createdAt) + "</td>" +
    "</tr>";
}

function trackTable(tracks, onClick) {
  if (!tracks.length) return emptyHtml("Cap activitat encara.");
  const html =
    '<div class="tbl-wrap"><table><thead><tr>' +
    "<th></th><th>Nom</th><th>Distància</th><th>Desnivell</th><th>Dificultat</th><th>Municipi</th><th>Data</th>" +
    "</tr></thead><tbody>" + tracks.map(trackRow).join("") + "</tbody></table></div>";
  const wrap = document.createElement("div");
  wrap.innerHTML = html;
  wrap.querySelectorAll("tr[data-id]").forEach(tr =>
    tr.addEventListener("click", () => onClick(Number(tr.dataset.id))));
  return wrap;
}

// Build the stats grid, hiding null rows.
function statsGrid(s) {
  const rows = [];
  const add = (k, v) => { if (v !== "—" && v != null) rows.push([k, v]); };
  add("Distància", kmFromKm(s.distanceKm));
  add("Temps total", hmsSec(s.totalTimeS));
  add("Temps en moviment", hmsSec(s.movingTimeS));
  add("Vel. mitjana", num(s.avgSpeedKmh, 1, " km/h"));
  add("Vel. màxima", num(s.maxSpeedKmh, 1, " km/h"));
  add("Ascens", num(s.ascentM, 0, " m"));
  add("Descens", num(s.descentM, 0, " m"));
  add("FC mitjana", num(s.avgHr, 0, " bpm"));
  add("FC màxima", num(s.maxHr, 0, " bpm"));
  add("Cadència", num(s.avgCadence, 0, " rpm"));
  add("Potència", num(s.avgPower, 0, " W"));
  add("Calories", num(s.kcal, 0, " kcal"));
  return '<div class="stats-grid">' + rows.map(r =>
    '<div class="stat"><div class="k">' + r[0] + '</div><div class="v">' + r[1] + "</div></div>").join("") + "</div>";
}

// Render a track detail into a container. showHr controls HR chart.
async function renderTrackDetail(container, id, backFn) {
  container.innerHTML = loadingHtml();
  let d;
  try { d = await apiJson("/api/track/" + id); }
  catch (e) { if (!e.auth) container.innerHTML = emptyHtml("No s'ha pogut carregar."); return; }
  const t = d.track, s = d.stats, samples = d.samples || [];
  const hasHr = samples.some(x => x.hr != null);
  const hasEle = samples.some(x => x.ele != null);
  const hasSpeed = samples.some(x => x.speedKmh != null);

  container.innerHTML =
    '<span class="back-link">← Tornar</span>' +
    '<div class="detail-head">' +
    "<div><h2 class=\"title\">" + actIcon(t.activityType) + " " + esc(t.name) +
    (t.isCompetition ? '<span class="badge badge-comp">Competició</span>' : "") + "</h2>" +
    '<p class="subtitle">' + esc(t.municipality || "") + (t.municipality ? " · " : "") +
    date(t.createdAt) + " · " + diffBadge(t.difficulty) + "</p></div>" +
    '<div class="detail-actions">' +
    '<a class="btn" href="/api/track/' + t.id + '/gpx">⬇ Baixa GPX</a>' +
    '<button class="btn" data-act="rename">✎ Reanomenar</button>' +
    '<button class="btn btn-danger" data-act="delete">🗑 Eliminar</button>' +
    "</div></div>" +
    statsGrid(s) +
    '<div class="map" id="detailMap"></div>' +
    '<div class="charts">' +
    (hasEle ? '<div class="chart-box"><h4>Altitud (m) / distància</h4><canvas class="chart" id="cEle"></canvas></div>' : "") +
    (hasSpeed ? '<div class="chart-box"><h4>Velocitat (km/h) / distància</h4><canvas class="chart" id="cSpd"></canvas></div>' : "") +
    (hasHr ? '<div class="chart-box"><h4>Freqüència cardíaca (bpm) / distància</h4><canvas class="chart" id="cHr"></canvas></div>' : "") +
    "</div>";

  container.querySelector(".back-link").addEventListener("click", backFn);
  container.querySelector('[data-act="rename"]').addEventListener("click", () => renameTrack(t, backFn));
  container.querySelector('[data-act="delete"]').addEventListener("click", () => deleteTrack(t, backFn));

  drawTrackMap(container.querySelector("#detailMap"), "detail", samples);

  const xs = samples.map(x => x.distM);
  if (hasEle) lineChart(container.querySelector("#cEle"), xs, samples.map(x => x.ele), { color: "#8b96a5", fill: "rgba(139,150,165,.4)" });
  if (hasSpeed) lineChart(container.querySelector("#cSpd"), xs, samples.map(x => x.speedKmh), { color: "#2ECC71", fill: "rgba(46,204,113,.35)" });
  if (hasHr) lineChart(container.querySelector("#cHr"), xs, samples.map(x => x.hr), { color: "#E63946", fill: "rgba(230,57,70,.3)" });
}

async function renameTrack(t, done) {
  const name = prompt("Nou nom:", t.name);
  if (name == null || !name.trim()) return;
  try {
    const res = await api("/api/track/" + t.id + "/rename", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: name.trim() })
    });
    if (res.ok) { toast("Reanomenat"); done(); }
    else toast("Error en reanomenar", true);
  } catch (e) { if (!e.auth) toast("Error de connexió", true); }
}
async function deleteTrack(t, done) {
  if (!confirm("Eliminar «" + t.name + "»?")) return;
  try {
    const res = await api("/api/track/" + t.id, { method: "DELETE" });
    if (res.ok) { toast("Eliminat"); done(); }
    else toast("Error en eliminar", true);
  } catch (e) { if (!e.auth) toast("Error de connexió", true); }
}

/* ============================================================= *
 *  View: Biblioteca (TRAINING)                                  *
 * ============================================================= */
async function renderLibrary() {
  const v = document.getElementById("view-library");
  v.innerHTML = '<h2 class="title">Biblioteca</h2><p class="subtitle">Els teus entrenaments</p>' + loadingHtml();
  destroyMap("detail");
  let tracks;
  try { tracks = await apiJson("/api/tracks?kind=TRAINING"); }
  catch (e) { if (!e.auth) v.innerHTML = emptyHtml("No s'ha pogut carregar."); return; }
  const list = document.createElement("div");
  list.appendChild(trackTable(tracks, (id) => openLibraryDetail(id)));
  v.innerHTML = '<h2 class="title">Biblioteca</h2><p class="subtitle">' + tracks.length + " entrenaments</p>";
  v.appendChild(list);
}
function openLibraryDetail(id) {
  const v = document.getElementById("view-library");
  renderTrackDetail(v, id, () => renderLibrary());
}

/* ============================================================= *
 *  View: Per seguir (ROUTE) + import + route creation           *
 * ============================================================= */
async function renderRoutes() {
  const v = document.getElementById("view-routes");
  destroyMap("detail");
  v.innerHTML =
    '<h2 class="title">Per seguir</h2><p class="subtitle">Rutes importades i creades</p>' +
    '<div class="toolbar">' +
    '<label class="btn">⬆ Importar<input id="importFile" type="file" accept=".gpx,.kml,.tcx" hidden></label>' +
    '<button class="btn btn-primary" id="btnCreate">✚ Crear ruta</button>' +
    "</div>" +
    '<div id="routesList">' + loadingHtml() + "</div>" +
    '<div id="routeEditor"></div>';

  v.querySelector("#importFile").addEventListener("change", handleImport);
  v.querySelector("#btnCreate").addEventListener("click", () => openRouteEditor());

  let tracks;
  try { tracks = await apiJson("/api/tracks?kind=ROUTE"); }
  catch (e) { if (!e.auth) v.querySelector("#routesList").innerHTML = emptyHtml("No s'ha pogut carregar."); return; }
  const listEl = v.querySelector("#routesList");
  listEl.innerHTML = "";
  listEl.appendChild(trackTable(tracks, (id) => openRouteDetail(id)));
}
function openRouteDetail(id) {
  const v = document.getElementById("view-routes");
  destroyMap("routeEdit");
  renderTrackDetail(v, id, () => renderRoutes());
}

async function handleImport(e) {
  const file = e.target.files && e.target.files[0];
  if (!file) return;
  const name = file.name.replace(/\.[^.]+$/, "");
  try {
    const text = await file.text();
    const url = "/api/import?name=" + encodeURIComponent(name) +
      "&kind=ROUTE&filename=" + encodeURIComponent(file.name);
    const res = await api(url, { method: "POST", body: text });
    const data = await res.json().catch(() => ({}));
    if (res.ok && data.ok) { toast("Importat correctament"); renderRoutes(); }
    else toast("Error en importar" + (data.error ? ": " + data.error : ""), true);
  } catch (err) { if (!err.auth) toast("No s'ha pogut llegir el fitxer", true); }
  e.target.value = "";
}

// Route editor with interactive Leaflet map.
function openRouteEditor() {
  const ed = document.getElementById("routeEditor");
  document.getElementById("routesList").classList.add("hidden");
  ed.innerHTML =
    '<span class="back-link">← Tornar a la llista</span>' +
    '<h3 class="section-title">Crear ruta</h3>' +
    '<p class="hint">Clica al mapa per afegir punts de pas. Calen com a mínim 2 punts.</p>' +
    '<div class="route-editor">' +
    '<div class="route-form">' +
    '<input id="rName" type="text" placeholder="Nom de la ruta" style="min-width:220px">' +
    '<select id="rProfile"><option value="HIKING">Excursionisme</option>' +
    '<option value="TREKKING">Trekking</option><option value="MTB">BTT</option>' +
    '<option value="SHORTEST">Més curt</option></select>' +
    '<span class="wp-count" id="wpCount">0 punts</span>' +
    '<button class="btn" id="rUndo">↶ Desfés</button>' +
    '<button class="btn btn-ghost" id="rClear">Buidar</button>' +
    '<button class="btn btn-primary" id="rSave">Desar</button>' +
    "</div>" +
    '<div class="map map-lg" id="routeMap"></div>' +
    "</div>";

  ed.querySelector(".back-link").addEventListener("click", closeRouteEditor);

  const map = newMap(ed.querySelector("#routeMap"), "routeEdit");
  map.setView([41.3874, 2.1686], 12);
  setTimeout(() => map.invalidateSize(), 60);

  const waypoints = [];
  let poly = L.polyline([], { color: "#E63946", weight: 4 }).addTo(map);
  const markers = [];
  const countEl = ed.querySelector("#wpCount");

  function refresh() {
    poly.setLatLngs(waypoints.map(w => [w.lat, w.lng]));
    countEl.textContent = waypoints.length + (waypoints.length === 1 ? " punt" : " punts");
  }
  map.on("click", (e) => {
    const wp = { lat: e.latlng.lat, lng: e.latlng.lng };
    waypoints.push(wp);
    const idx = markers.length;
    const m = L.circleMarker([wp.lat, wp.lng], {
      radius: 6, color: "#fff", weight: 2, fillColor: "#E63946", fillOpacity: 1
    }).addTo(map).bindTooltip("" + (idx + 1));
    markers.push(m);
    refresh();
  });
  ed.querySelector("#rUndo").addEventListener("click", () => {
    if (!waypoints.length) return;
    waypoints.pop();
    const m = markers.pop();
    if (m) map.removeLayer(m);
    refresh();
  });
  ed.querySelector("#rClear").addEventListener("click", () => {
    waypoints.length = 0;
    markers.forEach(m => map.removeLayer(m));
    markers.length = 0;
    refresh();
  });
  ed.querySelector("#rSave").addEventListener("click", async () => {
    const name = ed.querySelector("#rName").value.trim();
    const profile = ed.querySelector("#rProfile").value;
    if (!name) { toast("Posa un nom a la ruta", true); return; }
    if (waypoints.length < 2) { toast("Calen com a mínim 2 punts", true); return; }
    try {
      const res = await api("/api/route", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, profile, waypoints })
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok && data.ok) { toast("Ruta desada"); closeRouteEditor(); renderRoutes(); }
      else toast("Error en desar" + (data.error ? ": " + data.error : ""), true);
    } catch (err) { if (!err.auth) toast("Error de connexió", true); }
  });
}
function closeRouteEditor() {
  destroyMap("routeEdit");
  document.getElementById("routeEditor").innerHTML = "";
  const rl = document.getElementById("routesList");
  if (rl) rl.classList.remove("hidden");
}

/* ============================================================= *
 *  View: Competició                                             *
 * ============================================================= */
async function renderCompetitions() {
  const v = document.getElementById("view-competition");
  destroyMap("detail");
  v.innerHTML = '<h2 class="title">Competició</h2><p class="subtitle">Els teus reptes</p>' + loadingHtml();
  let comps;
  try { comps = await apiJson("/api/competitions"); }
  catch (e) { if (!e.auth) v.innerHTML = emptyHtml("No s'ha pogut carregar."); return; }
  if (!comps.length) {
    v.innerHTML = '<h2 class="title">Competició</h2>' + emptyHtml("Cap competició encara.");
    return;
  }
  const cards = comps.map(c =>
    '<div class="card" data-ref="' + c.refId + '">' +
    '<div class="card-head"><div><div class="card-title">' +
    actIcon(c.activityType) + " " + esc(c.name) + "</div>" +
    '<div class="card-meta">' + c.attemptCount + (c.attemptCount === 1 ? " intent" : " intents") + "</div></div></div>" +
    '<div class="card-big">' + hms(c.bestMs) + "</div>" +
    '<div class="card-meta">Millor temps</div></div>').join("");
  v.innerHTML = '<h2 class="title">Competició</h2><p class="subtitle">' + comps.length + " reptes</p>" +
    '<div class="grid">' + cards + "</div>";
  v.querySelectorAll(".card[data-ref]").forEach(c =>
    c.addEventListener("click", () => openCompetition(Number(c.dataset.ref))));
}

async function openCompetition(refId) {
  const v = document.getElementById("view-competition");
  v.innerHTML = loadingHtml();
  let d;
  try { d = await apiJson("/api/competition/" + refId); }
  catch (e) { if (!e.auth) v.innerHTML = emptyHtml("No s'ha pogut carregar."); return; }
  const rows = (d.attempts || []).map(a =>
    '<tr class="norow' + (a.isBest ? " best" : "") + '">' +
    "<td>" + dateTime(a.dateMs) + (a.isBest ? " 🏆" : "") + "</td>" +
    "<td>" + hms(a.durationMs) + "</td>" +
    "<td>" + num(a.avgSpeedKmh, 1, " km/h") + "</td>" +
    "<td>" + num(a.avgHr, 0, " bpm") + "</td></tr>").join("");
  v.innerHTML =
    '<span class="back-link">← Tornar</span>' +
    '<h2 class="title">' + esc(d.name) + "</h2>" +
    '<p class="subtitle">' + (d.attempts ? d.attempts.length : 0) + " intents</p>" +
    '<div class="tbl-wrap"><table><thead><tr><th>Data</th><th>Temps</th><th>Vel. mitjana</th><th>FC mitjana</th></tr></thead><tbody>' +
    (rows || '<tr class="norow"><td colspan="4">Sense intents</td></tr>') + "</tbody></table></div>" +
    '<div class="chart-box" style="margin-top:20px"><h4>Diferència de l\'últim intent vs. millor (verd = més ràpid, vermell = més lent)</h4>' +
    '<canvas class="chart tall" id="cGap"></canvas></div>';
  v.querySelector(".back-link").addEventListener("click", () => renderCompetitions());
  gapChart(v.querySelector("#cGap"), d.gap || []);
}

/* ============================================================= *
 *  View: Rècords                                                *
 * ============================================================= */
const REC_LABEL = {
  FASTEST_1K: "1 km més ràpid", FASTEST_5K: "5 km més ràpid", FASTEST_10K: "10 km més ràpid",
  FASTEST_HALF: "Mitja marató més ràpida", LONGEST_DISTANCE: "Distància més llarga",
  MAX_ASCENT: "Ascens màxim", MAX_SPEED: "Velocitat màxima", LONGEST_TIME: "Durada més llarga"
};
function recordValue(r) {
  switch (r.kind) {
    case "LONGEST_DISTANCE": return kmFromKm((r.value || 0) / 1000, 1);
    case "MAX_ASCENT": return num(r.value, 0, " m");
    case "MAX_SPEED": return num(r.value, 1, " km/h");
    case "LONGEST_TIME": return hms(r.value);
    default: return hms(r.valueMs); // FASTEST_* time records
  }
}
const TROPHY_SVG =
  '<svg viewBox="0 0 24 24" fill="none"><path d="M6 3h12v3a6 6 0 0 1-12 0V3Z" fill="#E63946"/>' +
  '<path d="M4 4H2v2a4 4 0 0 0 4 4M20 4h2v2a4 4 0 0 1-4 4" stroke="#f0ad4e" stroke-width="1.6"/>' +
  '<path d="M10 12h4v3h-4z" fill="#f0ad4e"/><path d="M8 20h8v1.5H8z" fill="#f0ad4e"/>' +
  '<path d="M10 15h4l1 5H9l1-5Z" fill="#E63946"/></svg>';

async function renderRecords() {
  const v = document.getElementById("view-records");
  destroyMap("detail");
  v.innerHTML = '<h2 class="title">Rècords</h2><p class="subtitle">Les teves millors marques</p>' + loadingHtml();
  let recs;
  try { recs = await apiJson("/api/records"); }
  catch (e) { if (!e.auth) v.innerHTML = emptyHtml("No s'ha pogut carregar."); return; }
  if (!recs.length) { v.innerHTML = '<h2 class="title">Rècords</h2>' + emptyHtml("Cap rècord encara."); return; }
  const cards = recs.map(r =>
    '<div class="card static">' +
    '<div class="card-head"><div class="rec-badge">' + TROPHY_SVG + "</div>" +
    '<div class="card-meta" style="text-align:right">' + date(r.dateMs) + "</div></div>" +
    '<div class="card-title" style="margin-top:6px">' + (REC_LABEL[r.kind] || esc(r.kind)) + "</div>" +
    '<div class="card-big">' + recordValue(r) + "</div>" +
    '<div class="card-meta">' + esc(r.trackName || "—") + "</div></div>").join("");
  v.innerHTML = '<h2 class="title">Rècords</h2><p class="subtitle">' + recs.length + " marques</p>" +
    '<div class="grid">' + cards + "</div>";
}

/* ============================================================= *
 *  View: Progrés                                                *
 * ============================================================= */
function deltaHtml(cur, prev) {
  if (prev == null || prev === 0) {
    if (cur > 0) return '<span class="delta up">nou</span>';
    return '<span class="delta flat">—</span>';
  }
  const pct = Math.round((cur - prev) / prev * 100);
  const cls = pct > 0 ? "up" : pct < 0 ? "down" : "flat";
  const sign = pct > 0 ? "+" : "";
  return '<span class="delta ' + cls + '">' + sign + pct + "%</span>";
}

async function renderProgress() {
  const v = document.getElementById("view-progress");
  destroyMap("detail");
  v.innerHTML = '<h2 class="title">Progrés</h2><p class="subtitle">Últimes 12 setmanes</p>' + loadingHtml();
  let p;
  try { p = await apiJson("/api/progress"); }
  catch (e) { if (!e.auth) v.innerHTML = emptyHtml("No s'ha pogut carregar."); return; }
  const weeks = p.weeks || [];
  const cur = weeks[weeks.length - 1] || { km: 0, hours: 0, ascentM: 0, count: 0 };
  const prev = weeks[weeks.length - 2] || { km: 0, hours: 0, ascentM: 0, count: 0 };

  const tile = (k, v2, d) =>
    '<div class="tile"><div class="k">' + k + '</div><div class="v">' + v2 + "</div>" + d + "</div>";

  v.innerHTML =
    '<h2 class="title">Progrés</h2><p class="subtitle">Aquesta setmana vs. l\'anterior</p>' +
    '<div class="tiles">' +
    tile("Distància", cur.km.toFixed(1) + " km", deltaHtml(cur.km, prev.km)) +
    tile("Temps", cur.hours.toFixed(1) + " h", deltaHtml(cur.hours, prev.hours)) +
    tile("Ascens", cur.ascentM + " m", deltaHtml(cur.ascentM, prev.ascentM)) +
    tile("Activitats", cur.count, deltaHtml(cur.count, prev.count)) +
    "</div>" +
    '<div class="streak">Ratxa: <b>' + p.streakWeeks + "</b> " +
    (p.streakWeeks === 1 ? "setmana seguida" : "setmanes seguides") + "</div>" +
    '<div class="chart-box"><h4>Distància per setmana (km) — 12 setmanes</h4>' +
    '<canvas class="chart tall" id="cWeeks"></canvas></div>' +
    '<h3 class="section-title">Totals històrics</h3>' +
    '<div class="tiles">' +
    tile("Distància total", p.totalKm.toFixed(0) + " km", "") +
    tile("Temps total", p.totalHours.toFixed(0) + " h", "") +
    tile("Ascens total", p.totalAscentM + " m", "") +
    tile("Activitats totals", p.totalCount, "") +
    "</div>";

  const labels = weeks.map(w => {
    const d = new Date(w.startEpochDay * 86400000);
    return pad2(d.getDate()) + "/" + pad2(d.getMonth() + 1);
  });
  barChart(v.querySelector("#cWeeks"), labels, weeks.map(w => w.km), { color: "#E63946" });
}

/* ============================================================= *
 *  Boot                                                         *
 * ============================================================= */
async function boot() {
  hideLogin();
  switchTab("library");
}

// Redraw active-tab charts/maps on resize (debounced).
let resizeTimer = null;
window.addEventListener("resize", () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    if (!$app.classList.contains("hidden")) renderTab(currentTab);
  }, 250);
});

// Initial probe: hit a cheap endpoint; 401 -> login, else load app.
(async function init() {
  try {
    const res = await fetch("/api/tracks?kind=TRAINING", { credentials: "include" });
    if (res.status === 401) { showLogin(); return; }
    boot();
  } catch (_) {
    showLogin();
  }
})();
