#!/usr/bin/env node
/**
 * 从 Controller 源码自动提取全量接口清单，重新生成 docs/架构与接口清单.md 的
 * 「## 四、全量接口清单」小节（从该标题到文件末尾整体替换）。
 *
 * 用法：node scripts/gen_api_list.mjs
 * 说明列取每个接口上方最近的 Javadoc / // 注释，没有则写 “—”。
 */
import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const CTRL_DIR = join(ROOT, 'see_you/src/main/java/com/seeyou/see_you/controller');
const DOC = join(ROOT, 'docs/架构与接口清单.md');

function extractComment(lines, idx) {
  // 从标注行往上找最近的注释（/** ... */ 或 // xxx），取其摘要
  let j = idx - 1;
  while (j >= 0 && lines[j].trim() === '') j--;
  if (j < 0) return '';
  const line = lines[j].trim();
  if (line.endsWith('*/')) {
    // 多行 Javadoc：回溯到 /**，取第一条非空非 @ 的文本行
    let k = j;
    while (k >= 0 && !lines[k].includes('/**')) k--;
    if (k < 0) return '';
    for (let m = k; m <= j; m++) {
      const t = lines[m].replace(/^\s*\/?\*\+?\s?/, '').trim();
      if (t && t !== '/' && !t.startsWith('@')) return t;
    }
    return '';
  }
  if (line.startsWith('/**')) return line.replace(/^\/\*\*\s?/, '').replace(/\s?\*\/$/, '').trim();
  if (line.startsWith('//')) return line.replace(/^\/\/\s?/, '').trim();
  return '';
}

function parseController(file) {
  const src = readFileSync(join(CTRL_DIR, file), 'utf8');
  const lines = src.split(/\r?\n/);
  const prefixM = src.match(/@RequestMapping\("([^"]*)"\)/);
  const prefix = prefixM ? prefixM[1] : '';
  const apis = [];
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/@(Get|Post|Put|Delete|Patch|Request)Mapping(?:\("([^"]*)"\))?/);
    if (!m) continue;
    const http = m[1] === 'Request' ? 'ANY' : m[1].replace('Mapping', '').toUpperCase();
    const path = m[2] || '';
    let handler = '';
    for (let k = i + 1; k < Math.min(i + 6, lines.length); k++) {
      const hm = lines[k].match(/public\s+[\w<>,\s.?]+?\s+(\w+)\s*\(/);
      if (hm) { handler = hm[1]; break; }
    }
    apis.push({ http, path: prefix + path, handler, desc: extractComment(lines, i) || '—' });
  }
  return { name: file.replace(/\.java$/, ''), prefix, apis };
}

const controllers = readdirSync(CTRL_DIR)
  .filter((f) => f.endsWith('.java'))
  .map(parseController)
  .sort((a, b) => a.name.localeCompare(b.name));

const total = controllers.reduce((s, c) => s + c.apis.length, 0);

let body = `## 四、全量接口清单\n\n\n`;
for (const c of controllers) {
  body += `#### ${c.name}  \`${c.prefix}\`  （${c.apis.length} 个）\n\n`;
  body += `| 方法 | 路径 | 处理方法 | 说明 |\n|---|---|---|---|\n`;
  for (const a of c.apis) body += `| ${a.http} | \`${a.path}\` | ${a.handler} | ${a.desc} |\n`;
  body += `\n`;
}

const doc = readFileSync(DOC, 'utf8');
const HEAD = '## 四、全量接口清单';
const pos = doc.indexOf(HEAD);
if (pos < 0) {
  console.error(`未在 ${DOC} 中找到「${HEAD}」，中止`);
  process.exit(1);
}
let updated = doc.slice(0, pos) + body.trimEnd() + '\n';

// 同步更新文件头的统计行（Controller 数 / 接口总数）
updated = updated.replace(
  /\*\*统计\*\*：\d+ 个 Controller · \*\*\d+ 个 HTTP 接口\*\* · \d+ 张业务表/,
  `**统计**：${controllers.length} 个 Controller · **${total} 个 HTTP 接口** · 21 张业务表`,
);
writeFileSync(DOC, updated);
console.log(`已生成：${controllers.length} 个 Controller，共 ${total} 个接口`);
