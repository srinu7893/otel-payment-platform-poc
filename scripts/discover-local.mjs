import { readdir, stat } from 'node:fs/promises';
import path from 'node:path';
const roots = ['C:/Program Files', 'C:/Program Files (x86)', 'C:/Apps', 'C:/Mphasis', 'C:/Temp', 'C:/Users/padegapati.reddy/Downloads', 'C:/Users/padegapati.reddy/AppData/Local/Programs', 'C:/Users/padegapati.reddy/POC'];
const names = /^(java\.exe|javac\.exe|mvn\.cmd|erl\.exe|postgres\.exe|pg_ctl\.exe|rabbitmq-server\.bat|grafana-server\.exe|grafana\.exe|prometheus\.exe|otelcol.*\.exe|loki.*\.exe|tempo.*\.exe|jaeger.*\.exe)$/i;
async function walk(dir, depth) {
  let entries; try { entries = await readdir(dir, { withFileTypes: true }); } catch { return; }
  for (const entry of entries) {
    const file = path.join(dir, entry.name);
    if (names.test(entry.name)) console.log(file);
    if (entry.isDirectory() && !entry.isSymbolicLink() && depth > 0 && !/^(node_modules|\.git|\.codex|\.cache|WindowsApps|Microsoft|Windows Defender.*)$/i.test(entry.name)) await walk(file, depth - 1);
    if (depth === 4 && /java|jdk|zulu|maven|apache|rabbit|erlang|postgres|grafana|prometheus|otel|loki|tempo|jaeger/i.test(entry.name)) console.log(`Candidate: ${file}`);
  }
}
for (const root of roots) { console.log(`Searching ${root}`); await walk(root, 4); }
