// 第一片只做健康检查，确认页面与服务通。
const el = document.getElementById('status');
try {
  const r = await fetch('/api/health');
  const j = await r.json();
  if (el) el.textContent = `服务正常 v${j.version}`;
} catch (e) {
  if (el) el.textContent = '服务未连接';
}
