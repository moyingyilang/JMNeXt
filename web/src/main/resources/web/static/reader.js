// 阅读器。两套形态共用这套逻辑，只按 <html data-layout> 切换渲染方式：
// - desktop：横向双页，左右键 / 点两侧翻页
// - mobile：纵向滚动，滚到哪算哪（懒加载）
// 进度始终 POST /api/progress/<chapter>，服务端按会话命名空间落本地存储。

const layout = document.documentElement.dataset.layout || 'desktop';
const content = document.getElementById('content');

function chapterOf() {
  const m = location.hash.match(/^#\/read\/(.+)$/);
  return m ? decodeURIComponent(m[1]) : null;
}

async function api(path, opts) {
  const r = await fetch(path, Object.assign({ credentials: 'same-origin' }, opts || {}));
  if (!r.ok) throw new Error(`${path} → ${r.status}`);
  return r.json();
}

let saveTimer = null;
function saveProgress(chapter, page) {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    api(`/api/progress/${encodeURIComponent(chapter)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ page }),
    }).catch(() => { /* 进度失败不打扰阅读 */ });
  }, 400);
}

async function openReader() {
  const chapter = chapterOf();
  if (!chapter || !content) return;
  content.innerHTML = '<p class="hint">载入中…</p>';
  let data;
  try {
    data = await api(`/api/read/${encodeURIComponent(chapter)}`);
  } catch (e) {
    content.innerHTML = `<p class="hint">载入失败：${e.message}</p>`;
    return;
  }
  if (!data.images || data.images.length === 0) {
    content.innerHTML = '<p class="hint">这一话没有图片</p>';
    return;
  }
  if (layout === 'mobile') renderScroll(chapter, data.images);
  else renderSpread(chapter, data.images);
}

function renderScroll(chapter, images) {
  content.className = 'm-main reader-scroll';
  content.innerHTML = '';
  images.forEach((src, i) => {
    const img = new Image();
    img.loading = 'lazy';
    img.decoding = 'async';
    img.src = src;
    img.className = 'page';
    img.dataset.index = String(i);
    content.appendChild(img);
  });
  // 滚动到哪一页：取离视口顶部最近的那张
  const io = new IntersectionObserver((entries) => {
    const visible = entries.filter(e => e.isIntersecting)
      .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0];
    if (visible) saveProgress(chapter, Number(visible.target.dataset.index || 0));
  }, { root: null, threshold: 0.5 });
  content.querySelectorAll('img.page').forEach(el => io.observe(el));
}

function renderSpread(chapter, images) {
  content.className = 'main reader-spread';
  let left = 0;
  const paint = () => {
    const pair = images.slice(left, left + 2);
    content.innerHTML = '';
    pair.forEach((src, k) => {
      const img = new Image();
      img.src = src;
      img.className = 'page' + (pair.length === 1 ? ' single' : '');
      img.dataset.index = String(left + k);
      content.appendChild(img);
    });
    saveProgress(chapter, left);
  };
  const step = (d) => {
    const next = Math.min(Math.max(left + d * 2, 0), Math.max(images.length - 1, 0));
    if (next === left) return;
    left = next;
    paint();
  };
  document.addEventListener('keydown', (e) => {
    if (!chapterOf()) return;
    if (e.key === 'ArrowLeft') step(-1);
    if (e.key === 'ArrowRight' || e.key === ' ') { e.preventDefault(); step(1); }
  });
  content.addEventListener('click', (e) => {
    const r = content.getBoundingClientRect();
    step(e.clientX < r.left + r.width / 2 ? -1 : 1);
  });
  paint();
}

window.addEventListener('hashchange', () => {
  if (chapterOf()) openReader();
  else if (content) content.innerHTML = '<p class="hint">第一片：仅骨架与访问头分流。接口与阅读器在后续片。</p>';
});

if (chapterOf()) openReader();
