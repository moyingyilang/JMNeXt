import { readFile, access } from 'node:fs/promises';
import { join, dirname, basename } from 'node:path';

const root = process.argv[2];
const html = await readFile(join(root, 'index.html'), 'utf8');
const mapRaw = html.match(/<script type="importmap">([\s\S]*?)<\/script>/);
const importMap = mapRaw ? JSON.parse(mapRaw[1]).imports || {} : {};
console.log('  import map:', JSON.stringify(importMap));

const seen = new Set();
const missing = [];
const bare = [];
const ok = [];

async function resolveRelative(fromFile, spec) {
  const base = dirname(fromFile);
  let p = spec.split('?')[0];
  return join(base, p);
}

async function scan(file, fromLabel) {
  if (seen.has(file)) return;
  seen.add(file);
  let src;
  try { src = await readFile(file, 'utf8'); } catch { missing.push(`${fromLabel}: 文件不存在 ${file}`); return; }
  const specs = new Set();
  for (const m of src.matchAll(/\bfrom\s+['"]([^'"]+)['"]/g)) specs.add(m[1]);
  for (const m of src.matchAll(/\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g)) specs.add(m[1]);
  for (const m of src.matchAll(/\bimport\s+['"]([^'"]+)['"]/g)) specs.add(m[1]);
  for (const spec of specs) {
    if (spec.startsWith('./') || spec.startsWith('../') || spec.startsWith('/')) {
      const f = await resolveRelative(file, spec);
      try { await access(f); ok.push(spec); await scan(f, spec); }
      catch { missing.push(`缺失: ${spec}（被 ${basename(file)} 引用）`); }
    } else if (spec.startsWith('node:') || spec === 'module') {
      // 仅 Node 分支使用，浏览器不会走
    } else if (importMap[spec]) {
      const f = join(root, importMap[spec].replace(/^\.\//, ''));
      try { await access(f); ok.push(`${spec} -> ${importMap[spec]}`); }
      catch { missing.push(`import map 指向的文件不存在: ${spec} -> ${importMap[spec]}`); }
    } else {
      bare.push(spec);
    }
  }
}

await scan(join(root, 'JMNeXt-uiweb.mjs'), 'index.html');
console.log(`  扫描文件数: ${seen.size}`);
console.log(`  可解析: ${ok.length} 处`);
const uniqMissing = [...new Set(missing)];
const uniqBare = [...new Set(bare)];
if (uniqMissing.length) { console.log('  缺失:'); uniqMissing.forEach(m => console.log('    - ' + m)); }
if (uniqBare.length) { console.log('  未映射裸模块（浏览器会失败）:'); uniqBare.forEach(m => console.log('    - ' + m)); }
if (!uniqMissing.length && !uniqBare.length) console.log('  结论: 模块图完整，全部可解析');
