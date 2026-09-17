// PRAXIC API helper - token handling, safe fetch, live polling
const TOKEN = (window.PRAXIC_TOKEN && window.PRAXIC_TOKEN !== '{{TOKEN}}') ? window.PRAXIC_TOKEN : '';

function apiHeaders() {
  const h = {};
  if (TOKEN) h['X-Praxic-Token'] = TOKEN;
  return h;
}

function apiFetch(path, opts = {}) {
  const headers = { ...apiHeaders(), ...(opts.headers || {}) };
  let url = path;
  if (TOKEN) {
    const urlParams = new URLSearchParams(window.location.search);
    const qpToken = urlParams.get('token');
    if (qpToken && !path.includes('token=')) {
      url += (path.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(qpToken);
    }
  }
  return fetch(url, { ...opts, headers });
}

async function getPlayers() {
  const r = await apiFetch('/api/players');
  if (!r.ok) throw new Error('players ' + r.status);
  return await r.json();
}

async function getAllPlayers() {
  try {
    const r = await apiFetch('/api/players/all');
    if (!r.ok) throw new Error('all ' + r.status);
    return await r.json();
  } catch (e) {
    return await getPlayers();
  }
}

async function getPlayer(nameOrUuid) {
  const r = await apiFetch('/api/player/' + encodeURIComponent(nameOrUuid));
  if (!r.ok) throw new Error('player ' + r.status);
  return await r.json();
}

async function getStatus() {
  const r = await apiFetch('/api/status');
  if (!r.ok) throw new Error('status ' + r.status);
  return await r.json();
}

async function getMetrics() {
  const r = await apiFetch('/api/metrics');
  if (!r.ok) throw new Error('metrics ' + r.status);
  return await r.json();
}

async function getIncidents() {
  const r = await apiFetch('/api/incidents');
  if (!r.ok) throw new Error('incidents ' + r.status);
  return await r.json();
}

async function actionReset(name) {
  const r = await apiFetch('/api/action/reset/' + encodeURIComponent(name), { method: 'POST' });
  if (!r.ok) throw new Error('reset ' + r.status);
  return await r.json();
}

async function actionWhitelist(name, add) {
  const action = add ? 'add' : 'remove';
  const r = await apiFetch('/api/action/whitelist?player=' + encodeURIComponent(name) + '&action=' + action, { method: 'POST' });
  if (!r.ok) throw new Error('whitelist ' + r.status);
  return await r.json();
}

async function getWhitelist() {
  const r = await apiFetch('/api/whitelist');
  if (!r.ok) throw new Error('whitelist list ' + r.status);
  return await r.json();
}
async function getRevexStatus() {
  try { const r = await apiFetch('/api/revex/status'); if (!r.ok) throw new Error('revex status '+r.status); return await r.json(); } catch(e) { return {present:false}; }
}
async function getRevexBans() {
  try { const r = await apiFetch('/api/revex/bans'); if (!r.ok) throw new Error('bans '+r.status); return await r.json(); } catch(e) { return []; }
}
async function getRevexInspect(name) {
  try { const r = await apiFetch('/api/revex/inspect/' + encodeURIComponent(name)); if (!r.ok) throw new Error('inspect '+r.status); return await r.json(); } catch(e) { return null; }
}
async function actionRevexUnban(name) {
  const r = await apiFetch('/api/action/revex/unban?player=' + encodeURIComponent(name), { method: 'POST' });
  if (!r.ok) throw new Error('unban '+r.status); return await r.json();
}
async function actionRevexReset(name) {
  const r = await apiFetch('/api/action/revex/reset?player=' + encodeURIComponent(name), { method: 'POST' });
  if (!r.ok) throw new Error('reset '+r.status); return await r.json();
}
async function actionRevexPardon(name) {
  const r = await apiFetch('/api/action/revex/pardon?player=' + encodeURIComponent(name), { method: 'POST' });
  if (!r.ok) throw new Error('pardon '+r.status); return await r.json();
}

function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\"/g, '&quot;').replace(/'/g, '&#39;');
}

function showToast(msg, type = 'info') {
  const existing = document.querySelector('.toast');
  if (existing) existing.remove();
  const div = document.createElement('div');
  div.className = 'toast';
  div.textContent = msg;
  if (type === 'error') {
    div.style.borderColor = '#ef4444';
    div.style.color = '#fecaca';
    div.style.background = 'rgba(127,29,29,0.9)';
  }
  document.body.appendChild(div);
  setTimeout(() => div.remove(), 3000);
}

// Simple sparkline / line graph using canvas
function drawSparkline(canvas, data, color = '#6366f1') {
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const dpr = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  canvas.width = rect.width * dpr;
  canvas.height = rect.height * dpr;
  ctx.scale(dpr, dpr);
  const w = rect.width;
  const h = rect.height;
  ctx.clearRect(0,0,w,h);

  if (!data || data.length < 2) return;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;

  // Grid
  ctx.strokeStyle = 'rgba(255,255,255,0.04)';
  ctx.lineWidth = 1;
  for (let i=0;i<3;i++) {
    const y = (h / 3) * i;
    ctx.beginPath();
    ctx.moveTo(0,y);
    ctx.lineTo(w,y);
    ctx.stroke();
  }

  // Line
  ctx.strokeStyle = color;
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  data.forEach((v,i) => {
    const x = (i / (data.length-1)) * w;
    const y = h - ((v - min) / range) * h;
    if (i===0) ctx.moveTo(x,y);
    else ctx.lineTo(x,y);
  });
  ctx.stroke();

  // Fill
  ctx.lineTo(w,h);
  ctx.lineTo(0,h);
  ctx.closePath();
  ctx.fillStyle = color + '18';
  ctx.fill();
}
