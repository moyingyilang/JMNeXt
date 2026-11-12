// 从官方 APK 的 main.*.js 里抽出 i18next 的两套字典。
// 结构形如：const yt={"zh-TW":{translation:JSON.parse('{...}')},"zh-CN":{...}}
// 用法：node extract-i18n.js <main.js> <输出目录>
const fs = require('fs');
const path = require('path');

const [jsPath, outDir] = process.argv.slice(2);
if (!jsPath || !outDir) {
  console.error('用法: node extract-i18n.js <main.js> <输出目录>');
  process.exit(2);
}
const src = fs.readFileSync(jsPath, 'utf8');

// JS 字符串里转义的反斜杠与引号，先取出原始片段再交给 JSON.parse 解码
function readJsStringAt(i) {
  // i 指向开引号之后；返回 [解码后的文本, 结束位置]
  let out = '';
  for (let j = i; j < src.length; j++) {
    const ch = src[j];
    if (ch === '\\') {
      const n = src[++j];
      if (n === 'n') out += '\n';
      else if (n === 't') out += '\t';
      else if (n === 'r') out += '\r';
      else if (n === 'u') { out += String.fromCharCode(parseInt(src.substr(j + 1, 4), 16)); j += 4; }
      else out += n;
    } else if (ch === "'") {
      return [out, j + 1];
    } else {
      out += ch;
    }
  }
  throw new Error('字符串没有闭合');
}

function grab(lang) {
  const marker = '"' + lang + '":{translation:JSON.parse(';
  const at = src.indexOf(marker);
  if (at < 0) return null;
  let i = at + marker.length;
  const quote = src[i];
  if (quote !== "'" && quote !== '"') throw new Error(lang + ' 的字典不是字符串字面量');
  const [jsonText] = readJsStringAt(i + 1);
  return JSON.parse(jsonText);
}

fs.mkdirSync(outDir, { recursive: true });
for (const lang of ['zh-TW', 'zh-CN']) {
  const dict = grab(lang);
  if (!dict) { console.error('没找到 ' + lang); process.exit(3); }
  const file = path.join(outDir, lang + '.json');
  fs.writeFileSync(file, JSON.stringify(dict, null, 2) + '\n');
  const leaves = (function count(o) {
    let n = 0;
    for (const k of Object.keys(o)) n += (o[k] && typeof o[k] === 'object') ? count(o[k]) : 1;
    return n;
  })(dict);
  console.log('  ' + lang + ': ' + leaves + ' 条 -> ' + file);
}
