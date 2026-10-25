import { createServer, request } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { gzipSync } from 'node:zlib';

const root = process.argv[2];
const port = Number(process.argv[3] || 17803);
const backend = { host: '127.0.0.1', port: Number(process.argv[4] || 17800) };

const types = {
  '.html': 'text/html; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.wasm': 'application/wasm',
  '.map': 'application/json',
  '.css': 'text/css; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
};
const compressible = new Set(['.wasm', '.mjs', '.js', '.html', '.css', '.map']);

/** 反代到 Ktor。path 可改写（/legacy 前缀剥掉）。 */
function proxy(req, res, pathOverride) {
  const up = request(
    { host: backend.host, port: backend.port, path: pathOverride || req.url, method: req.method, headers: req.headers },
    (upRes) => {
      res.writeHead(upRes.statusCode || 502, upRes.headers);
      upRes.pipe(res);
    },
  );
  up.on('error', () => {
    res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('后端（Ktor :' + backend.port + '）未运行');
  });
  req.pipe(up);
}

createServer(async (req, res) => {
  const p0 = new URL(req.url, 'http://x').pathname;

  if (p0.startsWith('/api/') || p0.startsWith('/img/')) return proxy(req, res, null);

  // 手写版 WebUI 挂在 /legacy 下，剥掉前缀转发给 Ktor 的 /。
  if (p0 === '/legacy' || p0.startsWith('/legacy/')) {
    const stripped = p0.replace(/^\/legacy/, '') || '/';
    return proxy(req, res, stripped + (req.url.includes('?') ? '?' + req.url.split('?')[1] : ''));
  }

  try {
    let p = decodeURIComponent(p0);
    if (p === '/' || p === '') p = '/index.html';
    const file = join(root, normalize(p).replace(/^(\.\.[/\\])+/, ''));
    let buf = await readFile(file);
    const ext = extname(file);
    const headers = { 'Content-Type': types[ext] || 'application/octet-stream' };
    const accept = String(req.headers['accept-encoding'] || '');
    if (compressible.has(ext) && /\bgzip\b/.test(accept) && buf.length > 1024) {
      buf = gzipSync(buf, { level: 6 });
      headers['Content-Encoding'] = 'gzip';
      headers['Vary'] = 'Accept-Encoding';
    }
    headers['Content-Length'] = buf.length;
    res.writeHead(200, headers);
    res.end(buf);
  } catch {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('not found');
  }
}).listen(port, '127.0.0.1', () =>
  console.log(`serving ${root} on :${port}  proxy /api,/img -> :${backend.port}  legacy -> /legacy  gzip on`),
);
