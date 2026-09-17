// PRAXIC Main Console 2026 - editorial flow, no blocks-on-blocks, Revex, session live
let allPlayers = [];
let selectedName = null;
let filterMode = 'all';
let searchQuery = '';
let liveInterval = null;
let detailInterval = null;
let tpsHistory = [];
let onlineHistory = [];
let currentDetailTab = 'overview';
let revexStatusCache = null;

function riskLevel(confidence, banned) {
  if (banned) return 'banned';
  if (confidence >= 0.8) return 'critical';
  if (confidence >= 0.6) return 'high';
  if (confidence >= 0.3) return 'suspicious';
  return 'clean';
}
function riskLevelFromPlayer(p) {
  if (p.banned) return 'banned';
  return riskLevel(p.confidence||0, false);
}
function humanRisk(conf, banned) {
  if (banned) {
    const level = 'banned';
    return { level, title: t('risk.banned.title') || 'BANNED', desc: t('risk.banned.desc') || 'Player is banned by REVEX', action: t('risk.banned.action') || 'Banned' };
  }
  const level = riskLevel(conf, false);
  return { level, title: t('risk.' + level + '.title'), desc: t('risk.' + level + '.desc'), action: t('risk.' + level + '.action') };
}
function humanAim(anl) {
  if (!anl) return { text: t('behavior.aim.clean'), level: 'clean', raw: '' };
  const cv = anl.rotationSpeedCV ?? -1;
  const entropy = anl.entropy ?? -1;
  const maxSnap = anl.maxSnapAngle ?? 0;
  let text = t('behavior.aim.clean'), level='clean';
  if (cv >=0 && cv <=0.18) { text = t('behavior.aim.bot'); level='critical'; }
  else if (entropy >=0 && entropy < 2.0) { text = t('behavior.aim.suspicious'); level='suspicious'; }
  else if (maxSnap > 90) { text = t('behavior.aim.suspicious'); level='high'; }
  const parts = [];
  if (entropy >=0) parts.push(`entropy ${entropy}`);
  if (cv >=0) parts.push(`CV ${cv}`);
  if (maxSnap) parts.push(`snap ${maxSnap}°`);
  return { text, level, raw: parts.join(' · ') || '-' };
}
function humanClicks(anl) {
  if (!anl) return { text: t('behavior.clicks.clean'), level: 'clean', raw: '' };
  const cps = anl.avgCps ?? 0;
  const total = anl.totalCps ?? cps;
  const std = anl.clickStdDev ?? -1;
  let text = t('behavior.clicks.clean'), level='clean';
  const displayCps = total > 0 ? total : cps;
  if (cps >= 20) { text = t('behavior.clicks.fast', {cps: cps.toFixed(1)}); level='critical'; }
  else if (std >=0 && std < 3 && cps >= 5) { text = t('behavior.clicks.bot'); level='critical'; }
  else if (displayCps >= 12) { text = t('behavior.clicks.fast', {cps: displayCps.toFixed(1)}); level='suspicious'; }
  let raw = '';
  if (total !== undefined && cps !== total) raw = `total ${total} · combat ${cps} · σ ${std}ms`;
  else raw = `CPS ${cps} · total ${total} · σ ${std}ms`;
  return { text, level, raw };
}
function humanMovement(anl) {
  if (!anl) return { text: t('behavior.movement.clean'), level: 'clean', raw: '' };
  const speed = anl.avgSpeed ?? 0;
  const jumpFreq = anl.jumpFreq ?? -1;
  let text = t('behavior.movement.clean'), level='clean';
  if (jumpFreq >= 2.5) { text = t('behavior.movement.bhop', {freq: jumpFreq.toFixed(1)}); level='high'; }
  else if (speed >= 0.9) { text = t('behavior.movement.fast'); level='high'; }
  return { text, level, raw: `speed ${speed} b/t · jump ${jumpFreq} j/s · strafe ${anl.strafeRatio ?? '-'}` };
}
function humanTiming(anl) {
  if (!anl) return { text: t('behavior.timing.clean'), level: 'clean', raw: '' };
  const packetStd = anl.packetStdDev ?? -1;
  let text = t('behavior.timing.clean'), level='clean';
  if (packetStd >=0 && packetStd < 1.0) { text = t('behavior.timing.timer'); level='high'; }
  return { text, level, raw: `packet σ ${packetStd}ms` };
}
function humanBaseline(bl) {
  if (!bl) return { text: '-', level: 'clean', raw: '' };
  if (!bl.ready) return { text: t('behavior.baseline.warming', {collected: bl.collected||0, required: bl.required||6000}), level: 'suspicious', raw: `${bl.collected}/${bl.required}` };
  const dev = bl.deviationScore ?? 0;
  if (dev >= 3.0) return { text: t('behavior.baseline.changed'), level: 'high', raw: `dev ${dev}` };
  return { text: t('behavior.baseline.ready'), level: 'clean', raw: `dev ${dev}` };
}
function humanCheckName(check) { const key = 'check.' + check; const name = t(key); return name === key ? check.replace('Check','') : name; }
function humanEvent(e) {
  const checkName = humanCheckName(e.check);
  const desc = e.details || '';
  if (currentLang === 'ru') {
    if (e.check.includes('Fly')) return `Полет: ${desc}`;
    if (e.check.includes('Speed')) return `Скорость: ${desc}`;
    if (e.check.includes('Reach')) return `Рич: ${desc}`;
    if (e.check.includes('Jesus')) return `Вода: ${desc}`;
    if (e.check.includes('Scaffold')) return `Скаффолд: ${desc}`;
    if (e.check.includes('Timer')) return `Таймер: ${desc}`;
    if (e.check.includes('Ghost')) return `Ловушка: ${desc}`;
    if (e.check.includes('AutoArmor')) return `Авто-броня: ${desc}`;
    if (e.check.includes('FastBreak')) return `Быстрое ломание: ${desc}`;
    return `${checkName}: ${desc}`;
  } else {
    if (e.check.includes('Fly')) return `Flight: ${desc}`;
    if (e.check.includes('Speed')) return `Speed: ${desc}`;
    if (e.check.includes('Reach')) return `Reach: ${desc}`;
    if (e.check.includes('Jesus')) return `Jesus: ${desc}`;
    if (e.check.includes('Scaffold')) return `Scaffold: ${desc}`;
    if (e.check.includes('Timer')) return `Timer: ${desc}`;
    if (e.check.includes('Ghost')) return `Honeypot: ${desc}`;
    if (e.check.includes('AutoArmor')) return `AutoArmor: ${desc}`;
    if (e.check.includes('FastBreak')) return `FastBreak: ${desc}`;
    return `${checkName}: ${desc}`;
  }
}

function renderPlayersList() {
  const container = document.getElementById('players-list');
  if (!container) return;
  const q = searchQuery.toLowerCase();
  let filtered = allPlayers.filter(p => {
    if (!p.name.toLowerCase().includes(q)) return false;
    if (filterMode === 'online') return p.online !== false;
    if (filterMode === 'offline') return p.online === false;
    return true;
  });
  filtered.sort((a,b) => (b.confidence||0) - (a.confidence||0));
  const totalEl = document.getElementById('players-total');
  if (totalEl) totalEl.textContent = `${filtered.length} / ${allPlayers.length}`;
  if (filtered.length === 0) {
    container.innerHTML = `<div class="empty"><p>${escapeHtml(t('players.empty'))}</p></div>`;
    return;
  }
  let html = `<table class="players-table"><thead><tr><th>Player</th><th>Ping</th><th>VL</th><th>Risk</th></tr></thead><tbody>`;
  filtered.forEach(p => {
    const level = riskLevelFromPlayer(p);
    const isOffline = p.online === false;
    const active = selectedName === p.name ? ' active' : '';
    const bannedBadge = p.banned ? `<span style="color:#ef4444;">BAN</span>` : '';
    html += `<tr class="${isOffline ? 'offline' : ''}${p.banned ? ' banned' : ''}${active}" data-name="${escapeHtml(p.name)}">
      <td><div class="player-name-cell">${escapeHtml(p.name)} ${bannedBadge}</div><div class="player-meta-cell"><span>${escapeHtml(p.uuid ? p.uuid.substring(0,8) : '')}</span>${p.ghostTraps ? `<span>👻${p.ghostTraps}</span>` : ''}${p.whitelisted ? `<span style="color:var(--accent3);">WL</span>` : ''}${p.banned ? `<span style="color:#ef4444;">BANNED</span>` : ''}</div></td>
      <td class="mono">${isOffline ? '-' : (p.ping||0)+'ms'}</td>
      <td class="vl-cell">${p.totalVl||0}</td>
      <td><span class="risk-badge risk-${level}">${level}</span></td>
    </tr>`;
  });
  html += `</tbody></table>`;
  container.innerHTML = html;
  container.querySelectorAll('tr[data-name]').forEach(tr => {
    tr.addEventListener('click', () => {
      selectedName = tr.getAttribute('data-name');
      currentDetailTab = 'overview';
      container.querySelectorAll('tr').forEach(c => c.classList.remove('active'));
      tr.classList.add('active');
      loadPlayerDetail(selectedName, true);
      if (detailInterval) clearInterval(detailInterval);
      detailInterval = setInterval(() => { if (selectedName) loadPlayerDetail(selectedName, false); }, 2000);
    });
  });
}

async function loadPlayerDetail(name, showLoading = true) {
  const detailBody = document.getElementById('detail-body');
  const historyBody = document.getElementById('history-body');
  if (!detailBody || !historyBody) return;
  if (showLoading) {
    detailBody.innerHTML = `<div class="empty"><p>Loading ${escapeHtml(name)}...</p></div>`;
    historyBody.innerHTML = `<div class="empty"><p>Loading...</p></div>`;
  }
  try {
    const [p, revexInspect] = await Promise.all([
      getPlayer(name),
      getRevexInspect(name)
    ]);
    const anl = p.analytics || {};
    const baseline = p.baseline || {};
    const isPlayerBanned = p.banned || (revexInspect && (revexInspect.ban || revexInspect.banLive));
    const risk = humanRisk(p.confidence||0, isPlayerBanned);
    const aim = humanAim(anl);
    const clicks = humanClicks(anl);
    const move = humanMovement(anl);
    const time = humanTiming(anl);
    const base = humanBaseline(baseline);
    const isOffline = p.online === false || p.offline === true;
    const prevScroll = detailBody.scrollTop;
    const totalVl = Object.values(p.violations||{}).reduce((a,b)=>a+b,0);
    const pct = Math.min(100, Math.max(4, (p.confidence||0)*100));
    const sessionMin = p.sessionTicks ? Math.floor(p.sessionTicks/20/60) : 0;
    const sessionSec = p.sessionTicks ? Math.floor((p.sessionTicks/20)%60) : 0;

    // Revex data
    const isBanned = isPlayerBanned;
    const banData = (revexInspect && (revexInspect.banLive || revexInspect.ban)) || null;
    const escData = revexInspect && revexInspect.escalation ? revexInspect.escalation : null;
    let ladder = [];
    let currentStep = 0;
    if (revexStatusCache && revexStatusCache.defaultEscalation) ladder = revexStatusCache.defaultEscalation;
    if (escData) {
      if (escData.step !== undefined) currentStep = escData.step;
      else if (escData.checks) {
        let max = 0;
        Object.values(escData.checks).forEach(v => { if (v.step > max) max = v.step; });
        currentStep = max;
      }
    }

    const revexTabLabel = isBanned ? `${t('revex.title')} · ${t('revex.banned')}` : t('revex.title');

    detailBody.innerHTML = `
      <div class="detail-flow">
        <div class="detail-hero-new">
          <div class="detail-hero-left">
            <div class="detail-name-huge">
              ${escapeHtml(p.name)}
              <span class="status-pill ${isOffline ? 'offline' : 'online'}">${isOffline ? t('detail.offline') : t('detail.online')}</span>
              ${p.whitelisted ? `<span class="status-pill wl">WHITELIST</span>` : ''}
              ${isBanned ? `<span class="status-pill banned">${t('revex.banned')}</span>` : ''}
            </div>
            <div class="detail-uuid-new">${escapeHtml(p.uuid||'')} · session ${sessionMin}m ${sessionSec}s · ${p.sessionStart ? new Date(p.sessionStart).toLocaleTimeString() : ''}</div>
            <div class="detail-meta-new">
              ${p.ping >=0 ? `<span class="detail-pill-new">Ping <strong>${p.ping}ms</strong></span>` : ''}
              ${p.health >=0 ? `<span class="detail-pill-new">HP <strong>${p.health.toFixed(1)}</strong></span>` : ''}
              ${p.gameMode ? `<span class="detail-pill-new">${escapeHtml(p.gameMode)}</span>` : ''}
              <span class="detail-pill-new">VL <strong>${totalVl}</strong></span>
              ${p.ghostTraps ? `<span class="detail-pill-new" style="color:#fca5a5;border-color:rgba(239,68,68,0.25);background:rgba(239,68,68,0.08);">👻 ${p.ghostTraps}</span>` : ''}
              ${p.movementState ? `<span class="detail-pill-new">${escapeHtml(p.movementState)} · air ${p.airTicks||0}</span>` : ''}
            </div>
            <div class="detail-actions-new">
              <button class="btn primary" onclick="doReset('${escapeHtml(p.name)}')">Reset</button>
              <button class="btn ${p.whitelisted ? 'danger' : ''}" onclick="doWhitelist('${escapeHtml(p.name)}', ${!p.whitelisted})">${p.whitelisted ? 'Remove WL' : 'Add WL'}</button>
              <button class="btn" onclick="navigator.clipboard.writeText('${escapeHtml(p.uuid||'')}').then(()=>showToast('UUID copied'))">Copy UUID</button>
              ${isBanned ? `<button class="btn danger" onclick="doRevexPardon('${escapeHtml(p.name)}')">${t('revex.pardon')}</button>` : ''}
              ${escData ? `<button class="btn" onclick="doRevexReset('${escapeHtml(p.name)}')">${t('revex.reset')}</button>` : ''}
            </div>
          </div>
        </div>

        <div class="risk-section-new">
          <div class="risk-circle-wrap">
            <div class="risk-circle-bg ${risk.level}" style="--pct:${pct}%">
              <div class="risk-circle-inner">
                <div class="risk-circle-value">${pct.toFixed(0)}%</div>
                <div class="risk-circle-label">${risk.level}</div>
              </div>
            </div>
          </div>
          <div class="risk-info-new">
            <div class="risk-title-huge"><span class="status-dot dot-${risk.level==='clean'?'ok':risk.level==='suspicious'?'warn':'danger'}"></span>${escapeHtml(risk.title)}</div>
            <div class="risk-desc-huge">${escapeHtml(risk.desc)}</div>
            <div class="risk-action-huge">${escapeHtml(risk.action)}</div>
            <div class="risk-meta-line"><span>confidence ${(p.confidence||0).toFixed(3)}</span><span>·</span><span>anomaly ${(p.anomaly||0).toFixed(3)}</span><span>·</span><span>session ${sessionMin}m ${sessionSec}s</span></div>
          </div>
        </div>

        <div class="detail-tabs">
          <button class="detail-tab ${currentDetailTab==='overview'?'active':''}" data-tab="overview">Overview</button>
          <button class="detail-tab ${currentDetailTab==='metrics'?'active':''}" data-tab="metrics">Metrics</button>
          <button class="detail-tab ${currentDetailTab==='violations'?'active':''}" data-tab="violations">Violations · ${totalVl}</button>
          <button class="detail-tab ${currentDetailTab==='revex'?'active':''}" data-tab="revex">${revexTabLabel}</button>
        </div>

        <div class="detail-tab-content" id="detail-tab-content">
          ${renderDetailTab(p, anl, baseline, aim, clicks, move, time, base, revexInspect, banData, escData, ladder, currentStep)}
        </div>
      </div>
    `;

    // Tab switching
    detailBody.querySelectorAll('.detail-tab').forEach(btn => {
      btn.addEventListener('click', () => {
        currentDetailTab = btn.getAttribute('data-tab');
        detailBody.querySelectorAll('.detail-tab').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        document.getElementById('detail-tab-content').innerHTML = renderDetailTab(p, anl, baseline, aim, clicks, move, time, base, revexInspect, banData, escData, ladder, currentStep);
      });
    });

    if (!showLoading) detailBody.scrollTop = prevScroll;

    // History - always show, but now grouped by session
    const allEvents = [];
    (p.history||[]).forEach(h => allEvents.push({...h, source:'history'}));
    (p.evidence||[]).forEach(ev => allEvents.push({...ev, source:'evidence', isCurrentSession: ev.createdAt && p.sessionStart ? ev.createdAt >= p.sessionStart : false}));
    allEvents.sort((a,b) => (b.timestamp||'').localeCompare(a.timestamp||''));

    if (allEvents.length === 0) {
      historyBody.innerHTML = `<div class="empty"><p>${t('history.empty')}</p></div>`;
    } else {
      const currentSessionEvents = allEvents.filter(ev => ev.isCurrentSession || ev.source==='history');
      const pastEvents = allEvents.filter(ev => !ev.isCurrentSession && ev.source==='evidence');
      historyBody.innerHTML = `
        <div style="padding:12px 12px 0 12px;">
          <div class="section-title" style="margin:0 0 8px 0;"><span>Current Session · ${currentSessionEvents.length}</span></div>
        </div>
        <div class="timeline">
          ${currentSessionEvents.map(ev => `
            <div class="event">
              <div class="event-top"><span class="event-check">${escapeHtml(humanCheckName(ev.check))}</span><span class="event-time">${escapeHtml(ev.timestamp||'')}</span></div>
              <div class="event-human">${escapeHtml(humanEvent(ev))}</div>
              <div class="event-details">${escapeHtml(ev.world||'')} ${ev.x!==undefined ? `${ev.x.toFixed(1)} ${ev.y.toFixed(1)} ${ev.z.toFixed(1)}` : ''} · VL ${ev.vl||''} ${ev.ping ? '· '+ev.ping+'ms' : ''}</div>
              <div class="event-actions"><span class="event-action action-${(ev.action||'flag').toLowerCase()}">${escapeHtml(ev.action||'flag')}</span>${ev.confidence!==undefined ? `<span class="tag">Risk ${(ev.confidence*100).toFixed(0)}%</span>` : ''}${ev.isCurrentSession ? `<span class="tag" style="color:var(--ok);border-color:rgba(34,197,94,0.3);background:var(--ok-dim);">live</span>` : ''}</div>
            </div>
          `).join('')}
        </div>
        ${pastEvents.length ? `
          <div style="padding:12px 12px 0 12px; margin-top:12px;">
            <div class="section-title" style="margin:0 0 8px 0;"><span>Past Sessions · ${pastEvents.length}</span></div>
          </div>
          <div class="timeline" style="opacity:0.6;">
            ${pastEvents.slice(0,10).map(ev => `
              <div class="event">
                <div class="event-top"><span class="event-check">${escapeHtml(humanCheckName(ev.check))}</span><span class="event-time">${escapeHtml(ev.timestamp||'')}</span></div>
                <div class="event-human">${escapeHtml(humanEvent(ev))}</div>
                <div class="event-details">${escapeHtml(ev.world||'')} · VL ${ev.vl||''}</div>
              </div>
            `).join('')}
          </div>
        ` : ''}
      `;
    }

  } catch (e) {
    if (showLoading) {
      detailBody.innerHTML = `<div class="empty"><p>Failed: ${escapeHtml(e.message)}</p></div>`;
      historyBody.innerHTML = `<div class="empty"><p>-</p></div>`;
    }
  }
}

function renderMetricCard(label, desc, value, sub, level) {
  const lvlClass = level ? ` ${level}` : '';
  return `<div class="metric-card">
    <div class="metric-card-top">
      <div class="metric-card-label">${escapeHtml(label)}</div>
      <div class="metric-card-desc">${escapeHtml(desc)}</div>
    </div>
    <div class="metric-card-value${lvlClass}">${value}</div>
    ${sub ? `<div class="metric-card-sub">${sub}</div>` : ''}
  </div>`;
}

function renderBar(percent, level) {
  const p = Math.max(0, Math.min(100, percent));
  const lvl = level ? ` ${level}` : '';
  return `<div class="metric-bar"><div class="metric-bar-fill${lvl}" style="width:${p}%"></div></div>`;
}

function renderDetailTab(p, anl, baseline, aim, clicks, move, time, base, revexInspect, banData, escData, ladder, currentStep) {
  if (currentDetailTab === 'overview') {
    return `
      <div class="detail-section">
        <div class="detail-section-head"><span>Behavior - Human Readable</span></div>
        <div class="behavior-flow">
          <div class="behavior-row"><div class="behavior-row-main"><div class="behavior-row-title">Aim Assist <span class="status-dot dot-${aim.level==='clean'?'ok':aim.level==='suspicious'?'warn':'danger'}"></span></div><div class="behavior-row-value">${escapeHtml(aim.text)}</div><div class="behavior-row-raw">${escapeHtml(aim.raw)}</div></div></div>
          <div class="behavior-row"><div class="behavior-row-main"><div class="behavior-row-title">Clicks <span class="status-dot dot-${clicks.level==='clean'?'ok':clicks.level==='suspicious'?'warn':'danger'}"></span></div><div class="behavior-row-value">${escapeHtml(clicks.text)}</div><div class="behavior-row-raw">${escapeHtml(clicks.raw)}</div></div></div>
          <div class="behavior-row"><div class="behavior-row-main"><div class="behavior-row-title">Movement <span class="status-dot dot-${move.level==='clean'?'ok':move.level==='suspicious'?'warn':'danger'}"></span></div><div class="behavior-row-value">${escapeHtml(move.text)}</div><div class="behavior-row-raw">${escapeHtml(move.raw)}</div></div></div>
          <div class="behavior-row"><div class="behavior-row-main"><div class="behavior-row-title">Timer / Packets <span class="status-dot dot-${time.level==='clean'?'ok':time.level==='suspicious'?'warn':'danger'}"></span></div><div class="behavior-row-value">${escapeHtml(time.text)}</div><div class="behavior-row-raw">${escapeHtml(time.raw)}</div></div></div>
          <div class="behavior-row"><div class="behavior-row-main"><div class="behavior-row-title">Profile - ${baseline.ready ? 'Ready' : 'Warming'} <span class="status-dot dot-${base.level==='clean'?'ok':'warn'}"></span></div><div class="behavior-row-value">${escapeHtml(base.text)}</div><div class="behavior-row-raw">deviation ${baseline.deviationScore ?? '-'} · collected ${baseline.collected||0}/${baseline.required||6000} · ready ${baseline.ready ? 'yes' : 'no'}</div></div></div>
        </div>
      </div>
    `;
  } else if (currentDetailTab === 'metrics') {
    const entropy = anl.entropy;
    const entropyPct = entropy !== undefined && entropy >=0 ? Math.min(100, (entropy/4)*100) : 0;
    const entropyLevel = entropy>=0 && entropy<2.0 ? 'danger' : entropy>=0 && entropy<3 ? 'warn' : 'ok';

    const maxSnap = anl.maxSnapAngle ?? 0;
    const snapPct = Math.min(100, (maxSnap/180)*100);
    const snapLevel = maxSnap>=90 ? 'danger' : maxSnap>=60 ? 'warn' : 'ok';

    const rotCV = anl.rotationSpeedCV;
    const rotPct = rotCV>=0 ? Math.min(100, (1 - Math.min(1, rotCV/0.5))*100) : 0;
    const rotLevel = rotCV>=0 && rotCV<=0.18 ? 'danger' : rotCV>=0 && rotCV<=0.3 ? 'warn' : 'ok';

    const combatCps = anl.avgCps ?? 0;
    const totalCps = anl.totalCps ?? combatCps;
    const cpsPct = Math.min(100, (totalCps/20)*100);
    const cpsLevel = combatCps>=20 ? 'danger' : totalCps>=12 ? 'warn' : 'ok';

    const clickStd = anl.clickStdDev;
    const clickStdPct = clickStd>=0 ? Math.min(100, Math.max(0, (20-clickStd)/20*100)) : 0;
    const clickStdLevel = clickStd>=0 && clickStd<3 ? 'danger' : clickStd>=0 && clickStd<7 ? 'warn' : 'ok';

    const packetStd = anl.packetStdDev;
    const packetStdPct = packetStd>=0 ? Math.min(100, Math.max(0, (10-packetStd)/10*100)) : 0;
    const packetStdLevel = packetStd>=0 && packetStd<1 ? 'danger' : packetStd>=0 && packetStd<2.5 ? 'warn' : 'ok';

    const avgSpeed = anl.avgSpeed ?? 0;
    const speedPct = Math.min(100, (avgSpeed/1.5)*100);
    const speedLevel = avgSpeed>=0.9 ? 'danger' : avgSpeed>=0.6 ? 'warn' : 'ok';

    const jumpFreq = anl.jumpFreq ?? 0;
    const jumpPct = Math.min(100, (jumpFreq/4)*100);
    const jumpLevel = jumpFreq>=2.5 ? 'danger' : jumpFreq>=1.5 ? 'warn' : 'ok';

    const confidence = p.confidence ?? 0;
    const confPct = confidence*100;

    const baselineCollected = baseline.collected||0;
    const baselineRequired = baseline.required||6000;
    const baselinePct = baselineRequired>0 ? Math.min(100, (baselineCollected/baselineRequired)*100) : 0;

    return `
      <div class="detail-section">
        <div class="detail-section-head"><span>${t('metrics.title')}</span></div>
        <div class="metrics-new">
          <div class="metrics-group">
            <div class="metrics-group-head">${t('metrics.combat')}</div>
            <div class="metrics-grid">
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.entropy')}</div>
                <div class="metric-big-value ${entropyLevel}">${entropy ?? '-'}</div>
                <div class="metric-big-desc">${t('metrics.entropy.desc')}</div>
                ${renderBar(entropyPct, entropyLevel)}
              </div>
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.maxSnap')}</div>
                <div class="metric-big-value ${snapLevel}">${maxSnap ? maxSnap + '°' : '-'}</div>
                <div class="metric-big-desc">${t('metrics.maxSnap.desc')}</div>
                ${renderBar(snapPct, snapLevel)}
              </div>
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.postKillSnap')}</div>
                <div class="metric-big-value">${anl.postKillSnap ?? '-'}${anl.postKillSnap ? '°' : ''}</div>
                <div class="metric-big-desc">${t('metrics.postKillSnap.desc')}</div>
              </div>
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.rotCV')}</div>
                <div class="metric-big-value ${rotLevel}">${rotCV ?? '-'}</div>
                <div class="metric-big-desc">${t('metrics.rotCV.desc')}</div>
                ${renderBar(rotPct, rotLevel)}
              </div>
            </div>
          </div>

          <div class="metrics-group">
            <div class="metrics-group-head">${t('metrics.clicks')}</div>
            <div class="metrics-grid">
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.totalCps')}</div>
                <div class="metric-big-value ${cpsLevel}">${totalCps ?? '-'}</div>
                <div class="metric-big-desc">${t('metrics.totalCps.desc')}</div>
                ${renderBar(cpsPct, cpsLevel)}
              </div>
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.avgCps')}</div>
                <div class="metric-big-value ${combatCps>=20?'danger':combatCps>=12?'warn':'ok'}">${combatCps ?? '-'}</div>
                <div class="metric-big-desc">${t('metrics.avgCps.desc')}</div>
                ${renderBar(Math.min(100,(combatCps/20)*100), combatCps>=20?'danger':combatCps>=12?'warn':'ok')}
              </div>
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.clickStd')}</div>
                <div class="metric-big-value ${clickStdLevel}">${clickStd ?? '-'}${clickStd ? ' ms' : ''}</div>
                <div class="metric-big-desc">${t('metrics.clickStd.desc')}</div>
                ${renderBar(clickStdPct, clickStdLevel)}
              </div>
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.packetStd')}</div>
                <div class="metric-big-value ${packetStdLevel}">${packetStd ?? '-'}${packetStd ? ' ms' : ''}</div>
                <div class="metric-big-desc">${t('metrics.packetStd.desc')}</div>
                ${renderBar(packetStdPct, packetStdLevel)}
              </div>
            </div>
          </div>

          <div class="metrics-group">
            <div class="metrics-group-head">${t('metrics.movement')}</div>
            <div class="metrics-grid">
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.avgSpeed')}</div>
                <div class="metric-big-value ${speedLevel}">${avgSpeed ?? '-'}</div>
                <div class="metric-big-desc">${t('metrics.avgSpeed.desc')}</div>
                ${renderBar(speedPct, speedLevel)}
              </div>
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.strafeRatio')}</div>
                <div class="metric-big-value">${anl.strafeRatio ?? '-'}</div>
                <div class="metric-big-desc">${t('metrics.strafeRatio.desc')}</div>
              </div>
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.jumpFreq')}</div>
                <div class="metric-big-value ${jumpLevel}">${jumpFreq ?? '-'}${jumpFreq ? ' j/s' : ''}</div>
                <div class="metric-big-desc">${t('metrics.jumpFreq.desc')}</div>
                ${renderBar(jumpPct, jumpLevel)}
              </div>
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.moveState')}</div>
                <div class="metric-big-value muted small">${escapeHtml(p.movementState||'-')}</div>
                <div class="metric-big-desc">${t('metrics.moveState.desc')}</div>
              </div>
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.airPhase')}</div>
                <div class="metric-big-value">${p.airTicks ?? '-'} / ${p.phaseTicks ?? '-'}</div>
                <div class="metric-big-desc">${t('metrics.airPhase.desc')}</div>
              </div>
            </div>
          </div>

          <div class="metrics-group">
            <div class="metrics-group-head">${t('metrics.system')}</div>
            <div class="metrics-grid">
              <div class="metric-big wide">
                <div class="metric-big-label">${t('metrics.confidence')}</div>
                <div class="metric-big-value ${confidence>=0.8?'danger':confidence>=0.3?'warn':'ok'}">${(confidence).toFixed(3)} / ${(p.anomaly||0).toFixed(3)}</div>
                <div class="metric-big-desc">${t('metrics.confidence.desc')}</div>
                ${renderBar(confPct, confidence>=0.8?'danger':confidence>=0.3?'warn':'ok')}
              </div>
              <div class="metric-big">
                <div class="metric-big-label">${t('metrics.buffers')}</div>
                <div class="metric-big-value muted small">${p.noSlowBuffer ?? '-'} / ${p.badPacketBuffer ?? '-'} / ${p.criticalsBuffer ?? '-'}</div>
                <div class="metric-big-desc">${t('metrics.buffers.desc')}</div>
              </div>
              <div class="metric-big wide">
                <div class="metric-big-label">${t('metrics.baseline')}</div>
                <div class="metric-big-value muted small">${baselineCollected} / ${baselineRequired} · dev ${baseline.deviationScore ?? '-'}</div>
                <div class="metric-big-desc">${t('metrics.baseline.desc')}</div>
                ${renderBar(baselinePct, baselinePct>=100?'ok':'warn')}
              </div>
            </div>
          </div>
        </div>
      </div>
    `;
  } else if (currentDetailTab === 'violations') {
    const entries = Object.entries(p.violations||{}).filter(([k,v])=>v>0).sort((a,b)=>b[1]-a[1]);
    const max = entries.length ? Math.max(...entries.map(e=>e[1])) : 1;
    return `
      <div class="detail-section">
        <div class="detail-section-head"><span>Violations - ${entries.length} checks triggered</span></div>
        <div class="vio-flow">
          ${entries.length ? entries.map(([k,v]) => {
            const pct = Math.min(100, (v/max)*100);
            const lvl = v>=10 ? 'critical' : v>=5 ? 'high' : '';
            return `<div class="vio-flow-row"><span class="vio-flow-name">${escapeHtml(humanCheckName(k))}</span><div class="vio-flow-bar"><div class="vio-flow-fill ${lvl}" style="width:${pct}%"></div></div><span class="vio-flow-count">${v}</span></div>`;
          }).join('') : `<div class="empty"><p>No violations</p></div>`}
        </div>
      </div>
    `;
  } else if (currentDetailTab === 'revex') {
    const ban = banData;
    const hasBan = !!ban;
    const banReason = ban ? (ban.reason || '') : '';
    const bannedAt = ban ? (ban.bannedAt || '') : '';
    const expiresAt = ban ? (ban.expiresAt ?? -1) : -1;
    const permanent = ban ? (ban.permanent ?? false) : false;
    const isGlobal = revexStatusCache && revexStatusCache.escalationMode !== 'per_check';
    const modeText = isGlobal ? t('revex.global') : t('revex.perCheck');
    const statusText = revexStatusCache && revexStatusCache.present ? (revexStatusCache.enabled ? t('revex.enabled') + ' · ' + (revexStatusCache.escalationMode||'global') : t('revex.disabled')) : t('revex.notInstalled');
    return `
      <div class="revex-hero">
        <div class="revex-title">${t('revex.title')} - ${statusText}</div>
        <div class="revex-desc">${t('revex.desc')}</div>
        ${hasBan ? `
          <div class="revex-ban-card">
            <div class="ban-reason">${permanent ? 'Permanent' : 'Temporary'} - ${escapeHtml(banReason)}</div>
            <div class="ban-meta">${escapeHtml(bannedAt)} · ${permanent ? 'permanent' : 'expires ' + (expiresAt>0 ? new Date(expiresAt).toLocaleString() : '')} · ${ban.remainingText || ''}</div>
            <div style="margin-top:10px; display:flex; gap:8px;">
              <button class="btn danger" onclick="doRevexUnban('${escapeHtml(p.name)}')">${t('revex.unban')}</button>
              <button class="btn" onclick="doRevexPardon('${escapeHtml(p.name)}')">${t('revex.pardon')}</button>
            </div>
          </div>
        ` : `<div style="margin-top:10px; font-size:13px; color:var(--muted);">${t('revex.noBan')}</div>`}
        <div style="margin-top:14px;">
          <div class="detail-section-head" style="margin:0 0 8px 0;"><span>${t('revex.ladder')} · ${t('revex.step')} ${currentStep} / ${ladder.length||5}</span></div>
          <div class="revex-ladder">
            ${(ladder.length ? ladder : ['warn','tempban 15m','tempban 1h','tempban 1d','ban']).map((act, idx) => {
              let cls = '';
              if (idx < currentStep) cls = 'done';
              if (idx === currentStep) cls = 'active';
              return `<span class="revex-step ${cls}">${idx+1}. ${escapeHtml(act)}</span>`;
            }).join('')}
          </div>
          <div style="margin-top:10px; font-size:11px; color:var(--muted2);">${t('revex.mode')}: ${escapeHtml(modeText)} · ${t('revex.resetAfter')} ${revexStatusCache ? escapeHtml(revexStatusCache.escalationResetTime||'24h') : '24h'}</div>
          ${escData ? `<div style="margin-top:12px;"><div class="detail-section-head" style="margin:0 0 8px 0;"><span>Escalation Details</span></div><pre style="font-size:11px; background:var(--bg3); border:1px solid var(--border); padding:10px; border-radius:8px; overflow:auto; max-height:200px;">${escapeHtml(JSON.stringify(escData, null, 2))}</pre><div style="margin-top:8px;"><button class="btn" onclick="doRevexReset('${escapeHtml(p.name)}')">${t('revex.reset')}</button></div></div>` : ''}
        </div>
      </div>
      <div class="detail-section">
        <div class="detail-section-head"><span>${t('revex.how')}</span></div>
        <div style="font-size:13px; color:var(--muted); line-height:1.5;">
          ${t('revex.how.desc')}
        </div>
      </div>
    `;
  }
  return '';
}

async function doReset(name) {
  if (!confirm('Reset violations for ' + name + '?')) return;
  try { await actionReset(name); showToast('Reset done: ' + name); refreshPlayers(); if (selectedName === name) loadPlayerDetail(name, false); } catch (e) { showToast('Failed: ' + e.message, 'error'); }
}
async function doWhitelist(name, add) {
  if (!confirm((add ? 'Add to' : 'Remove from') + ' whitelist for ' + name + '?')) return;
  try { await actionWhitelist(name, add); showToast('Whitelist ' + (add ? 'added' : 'removed') + ': ' + name); refreshPlayers(); if (selectedName === name) loadPlayerDetail(name, false); } catch (e) { showToast('Failed: ' + e.message, 'error'); }
}
async function doRevexUnban(name) { if (!confirm('Unban ' + name + ' in REVEX?')) return; try { await actionRevexUnban(name); showToast('REVEX unban: ' + name); if (selectedName===name) loadPlayerDetail(name,false); } catch(e){ showToast('Failed: '+e.message,'error'); } }
async function doRevexReset(name) { if (!confirm('Reset escalation for ' + name + '?')) return; try { await actionRevexReset(name); showToast('REVEX reset: ' + name); if (selectedName===name) loadPlayerDetail(name,false); } catch(e){ showToast('Failed: '+e.message,'error'); } }
async function doRevexPardon(name) { if (!confirm('Pardon ' + name + ' (unban+reset)?')) return; try { await actionRevexPardon(name); showToast('REVEX pardon: ' + name); if (selectedName===name) loadPlayerDetail(name,false); } catch(e){ showToast('Failed: '+e.message,'error'); } }

async function openWhitelistModal() {
  const existing = document.getElementById('wl-modal'); if (existing) existing.remove();
  const overlay = document.createElement('div'); overlay.className = 'modal-overlay'; overlay.id = 'wl-modal';
  overlay.innerHTML = `<div class="modal-box"><div class="modal-header"><h3>Whitelisted Players</h3><button class="modal-close" onclick="closeWhitelistModal()">✕</button></div><div class="modal-body" id="wl-body"><div class="empty"><p>Loading...</p></div></div><div class="modal-footer"><button class="btn" onclick="closeWhitelistModal()">Close</button></div></div>`;
  overlay.addEventListener('click', (e) => { if (e.target === overlay) closeWhitelistModal(); });
  document.body.appendChild(overlay);
  try {
    const list = await getWhitelist();
    const body = document.getElementById('wl-body');
    if (list.length === 0) body.innerHTML = `<div class="empty"><p>No whitelisted players</p></div>`;
    else body.innerHTML = list.map(w => `<div class="whitelist-item"><div><div class="wl-name">${escapeHtml(w.name)}</div><div class="wl-uuid">${escapeHtml(w.uuid)} ${w.online ? '· online' : '· offline'}</div></div><button class="btn danger" onclick="doWhitelist('${escapeHtml(w.name)}', false); setTimeout(()=>openWhitelistModal(), 400)">Remove</button></div>`).join('');
  } catch (e) { document.getElementById('wl-body').innerHTML = `<div class="empty"><p>Failed: ${escapeHtml(e.message)}</p></div>`; }
}
function closeWhitelistModal() { const el = document.getElementById('wl-modal'); if (el) el.remove(); }

async function refreshPlayers() {
  try { const players = await getAllPlayers(); allPlayers = players; renderPlayersList(); } catch (e) { console.error('refreshPlayers', e); }
}
async function refreshBottomBar() {
  try {
    const m = await getMetrics();
    tpsHistory.push(m.tps >=0 ? m.tps : 20);
    onlineHistory.push(m.online || 0);
    if (tpsHistory.length > 60) tpsHistory.shift();
    if (onlineHistory.length > 60) onlineHistory.shift();
    const left = document.getElementById('bottom-left');
    if (left) left.innerHTML = `<span class="dot"></span><span>TPS ${m.tps ?? '-'}</span><span class="sep"></span><span>MSPT ${m.mspt ?? '-'}ms</span><span class="sep"></span><span>ON ${m.online ?? '-'}</span><span class="sep"></span><span>FLAGS ${m.totalFlags ?? '-'}</span>`;
    const right = document.getElementById('bottom-right');
    if (right) { const now = new Date().toLocaleTimeString(); right.textContent = `${now} · PRAXIC ${m.version || ''}`; }
    const tpsCanvas = document.getElementById('tps-graph'); if (tpsCanvas) drawSparkline(tpsCanvas, tpsHistory, '#6366f1');
    const onlineCanvas = document.getElementById('online-graph'); if (onlineCanvas) drawSparkline(onlineCanvas, onlineHistory, '#22c55e');
  } catch (e) {}
}
function setupFilters() {
  const searchEl = document.getElementById('search');
  if (searchEl) searchEl.addEventListener('input', (e) => { searchQuery = e.target.value; renderPlayersList(); });
  document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      filterMode = tab.getAttribute('data-filter');
      renderPlayersList();
    });
  });
  const wlBtn = document.getElementById('open-whitelist'); if (wlBtn) wlBtn.addEventListener('click', openWhitelistModal);
}
async function initMain() {
  applyI18n();
  setupFilters();
  try { revexStatusCache = await getRevexStatus(); } catch(e) {}
  refreshPlayers();
  refreshBottomBar();
  liveInterval = setInterval(() => { refreshPlayers(); refreshBottomBar(); }, 2000);
  window.praxicRerender = () => { applyI18n(); renderPlayersList(); if (selectedName) loadPlayerDetail(selectedName, false); };
  document.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); const searchEl = document.getElementById('search'); if (searchEl) searchEl.focus(); }
    if (e.key === 'Escape') closeWhitelistModal();
  });
}
if (document.getElementById('players-list')) { document.addEventListener('DOMContentLoaded', initMain); }
