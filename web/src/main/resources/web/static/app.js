// 页面逻辑：首页 / 详情 / 收藏 / 设置。两套布局共用，只按 <html data-layout> 决定渲染形态。
const layout = document.documentElement.dataset.layout || 'desktop';
const content = document.getElementById('content');
const statusEl = document.getElementById('status');

const api = async (path, opts) => {
  const r = await fetch(path, Object.assign({ credentials: 'same-origin' }, opts || {}));
  if (!r.ok) throw new Error(`${path} → ${r.status}`);
  return r.json();
};

const escapeHtml = (s) => String(s ?? '').replace(/[&<>"']/g, c => (
  { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
));

async function health() {
  try {
    const j = await api('/api/health');
    if (statusEl) statusEl.textContent = `服务正常 v${j.version}`;
  } catch {
    if (statusEl) statusEl.textContent = '服务未连接';
  }
}

// ---------- 首页 ----------
async function renderHome() {
  if (!content) return;
  content.innerHTML = '<p class="hint">载入首页…</p>';
  try {
    const sections = await api('/api/home');
    const cards = [];
    (sections || []).forEach(sec => (sec.items || []).forEach(it => cards.push(it)));
    if (cards.length === 0) { content.innerHTML = '<p class="hint">首页为空（可能需要登录）</p>'; return; }
    content.innerHTML = '';
    const grid = document.createElement('div');
    grid.className = layout === 'mobile' ? 'm-grid' : 'grid';
    cards.slice(0, 60).forEach(it => {
      const a = document.createElement('a');
      a.className = 'card';
      a.href = `#/album/${encodeURIComponent(it.id)}`;
      a.innerHTML = `<span class="card-t">${escapeHtml(it.name || it.id)}</span>`;
      grid.appendChild(a);
    });
    content.appendChild(grid);
  } catch (e) {
    content.innerHTML = `<p class="hint">首页载入失败：${escapeHtml(e.message)}（先登录）</p>`;
  }
}

// ---------- 详情 ----------
async function renderAlbum(id) {
  if (!content) return;
  content.innerHTML = '<p class="hint">载入详情…</p>';
  try {
    const a = await api(`/api/album/${encodeURIComponent(id)}`);
    const fav = a.favorited ? '取消收藏' : '收藏';
    content.innerHTML = `
      <h2 class="title">${escapeHtml(a.name || id)}</h2>
      <div class="row">
        <button id="fav">${fav}</button>
      </div>
      <ol class="chapters">
        ${(a.series || []).map(s => `
          <li><a href="#/read/${encodeURIComponent(s.id)}">${escapeHtml(s.name || s.id)}</a></li>`).join('')}
      </ol>`;
    document.getElementById('fav').addEventListener('click', async () => {
      try {
        const r = await api(`/api/favorite/${encodeURIComponent(id)}`, { method: 'POST' });
        document.getElementById('fav').textContent = r.favorited ? '取消收藏' : '收藏';
      } catch (e) { alert(`收藏失败：${e.message}`); }
    });
    if (statusEl) statusEl.textContent = `详情 ${id}`;
  } catch (e) {
    content.innerHTML = `<p class="hint">详情载入失败：${escapeHtml(e.message)}</p>`;
  }
}

// ---------- 收藏 ----------
async function renderFavorites() {
  if (!content) return;
  content.innerHTML = '<p class="hint">载入收藏…</p>';
  try {
    const list = await api('/api/favorites');
    if (!list || list.length === 0) { content.innerHTML = '<p class="hint">还没有收藏</p>'; return; }
    content.innerHTML = `<ul class="fav-list">${list.map(f => `
      <li><a href="#/album/${encodeURIComponent(f.id)}">${escapeHtml(f.title || f.id)}</a></li>`).join('')}</ul>`;
  } catch (e) {
    content.innerHTML = `<p class="hint">收藏载入失败：${escapeHtml(e.message)}（先登录）</p>`;
  }
}

// ---------- 搜索 ----------
async function renderSearch() {
  if (!content) return;
  const q0 = decodeURIComponent((location.hash.split('?q=')[1] || '')).trim();
  content.innerHTML = `
    <h2 class="title">搜索</h2>
    <form id="sf" class="row" style="display:flex;gap:8px">
      <input id="q" name="q" value="${escapeHtml(q0)}" placeholder="关键词 / 作者 / 标签"
             style="flex:1;background:#161a22;color:var(--fg);border:1px solid var(--line);border-radius:6px;padding:8px">
      <button type="submit">搜索</button>
    </form>
    <div id="sr"></div>`;
  const form = document.getElementById('sf');
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const q = document.getElementById('q').value.trim();
    location.hash = q ? `#/search?q=${encodeURIComponent(q)}` : '#/search';
  });
  const sr = document.getElementById('sr');
  if (!q0) { sr.innerHTML = '<p class="hint">输入关键词后回车</p>'; return; }
  sr.innerHTML = '<p class="hint">搜索中…</p>';
  try {
    const list = await api(`/api/search?q=${encodeURIComponent(q0)}`);
    if (!list || list.length === 0) { sr.innerHTML = '<p class="hint">没有结果</p>'; return; }
    sr.innerHTML = `<div class="${layout === 'mobile' ? 'm-grid' : 'grid'}">${list.map(it => `
      <a class="card" href="#/album/${encodeURIComponent(it.id)}">
        <span class="card-t">${escapeHtml(it.name || it.id)}</span>
      </a>`).join('')}</div>`;
  } catch (e) {
    sr.innerHTML = `<p class="hint">搜索失败：${escapeHtml(e.message)}</p>`;
  }
}

// ---------- 历史 ----------
async function renderHistory() {
  if (!content) return;
  content.innerHTML = '<p class="hint">载入历史…</p>';
  try {
    const list = await api('/api/history');
    if (!list || list.length === 0) { content.innerHTML = '<p class="hint">还没有阅读记录</p>'; return; }
    content.innerHTML = `<ul class="fav-list">${list.map(h => `
      <li><a href="#/read/${encodeURIComponent(h.chapterId || h.chapter)}">
        ${escapeHtml(h.title || h.aid || '')}</a>
        <span class="hint"> 第 ${escapeHtml(String(h.page ?? 0))} 页</span></li>`).join('')}</ul>`;
  } catch (e) {
    content.innerHTML = `<p class="hint">历史载入失败：${escapeHtml(e.message)}（先登录）</p>`;
  }
}

// ---------- 设置 ----------
async function renderSettings() {
  if (!content) return;
  content.innerHTML = '<p class="hint">载入设置…</p>';
  let s = { theme: 'system', readerMode: 'scroll' };
  try { s = Object.assign(s, await api('/api/settings')); } catch { /* 用默认值 */ }
  content.innerHTML = `
    <form class="settings" id="sf">
      <label>主题
        <select name="theme">
          ${['system', 'light', 'dark'].map(v => `<option value="${v}"${s.theme === v ? ' selected' : ''}>${v}</option>`).join('')}
        </select>
      </label>
      <label>阅读模式
        <select name="readerMode">
          ${['scroll', 'page'].map(v => `<option value="${v}"${s.readerMode === v ? ' selected' : ''}>${v}</option>`).join('')}
        </select>
      </label>
      <button type="submit">保存</button>
    </form>`;
  document.getElementById('sf').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    try {
      await api('/api/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ theme: f.get('theme'), readerMode: f.get('readerMode') }),
      });
      if (statusEl) statusEl.textContent = '设置已保存';
    } catch (err) { alert(`保存失败：${err.message}`); }
  });
}

// ---------- 路由（hash） ----------
function route() {
  const h = location.hash || '';
  let m;
  if (!h || h === '#/') return renderHome();
  if ((m = h.match(/^#\/favorites$/))) return renderFavorites();
  if ((m = h.match(/^#\/settings$/))) return renderSettings();
  if ((m = h.match(/^#\/history$/))) return renderHistory();
  if (h.match(/^#\/search/)) return renderSearch();
  if ((m = h.match(/^#\/album\/(.+)$/))) return renderAlbum(decodeURIComponent(m[1]));
  if (h.match(/^#\/read\//)) return; // 阅读器由 reader.js 处理
  return renderHome();
}

window.addEventListener('hashchange', route);
health();
if (!location.hash.match(/^#\/read\//)) route();
