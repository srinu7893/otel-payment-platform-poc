import { readFile, writeFile, mkdir, stat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import { createWriteStream, createReadStream } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
const selections = { tempo: /^tempo_.*windows_amd64.tar.gz$/, collector: /^otelcol-contrib_.*windows_amd64.tar.gz$/, prometheus: /^prometheus-.*windows-amd64.zip$/, loki: /^loki-windows-amd64.exe.zip$/, rabbitmq: /^rabbitmq-server-windows-.*.zip$/, erlang: /^otp_win64_.*.zip$/ };
async function digest(file) { const hash = createHash('sha256'); for await (const chunk of createReadStream(file)) hash.update(chunk); return hash.digest('hex'); }
async function install(name, asset) {
  const file = `artifacts/native/${asset.name}`;
  const dir = `artifacts/native/${name}`;
  const expected = asset.digest?.replace('sha256:', '');
  if (!expected) throw new Error(`No verified SHA256 for ${name}`);
  let valid = false;
  try { valid = (await stat(file)).size > 0 && await digest(file) === expected; } catch {}
  if (!valid) {
    console.log(`Downloading ${name}: ${asset.name}`);
    const response = await fetch(asset.browser_download_url, { signal: AbortSignal.timeout(600000) });
    if (!response.ok) throw new Error(`${name}: HTTP ${response.status}`);
    await pipeline(Readable.fromWeb(response.body), createWriteStream(file));
    if (await digest(file) !== expected) throw new Error(`${name}: SHA256 mismatch`);
  }
  await mkdir(dir, { recursive: true });
  await new Promise((resolve, reject) => {
    const child = spawn('tar.exe', ['-xf', file, '-C', dir], { windowsHide: true, stdio: 'inherit' });
    child.on('error', reject); child.on('exit', code => code === 0 ? resolve() : reject(new Error(`Extract ${name}: ${code}`)));
  });
  console.log(`Ready: ${name} (SHA256 verified)`);
}
if (process.argv.includes('grafana')) {
  await install('grafana', { name: 'grafana_12.2.0_17949786146_windows_amd64.tar.gz', browser_download_url: 'https://dl.grafana.com/grafana/release/12.2.0/grafana_12.2.0_17949786146_windows_amd64.tar.gz', digest: 'sha256:a1824c72f8d785d6fe004d675a3fc0342f5e30e28ead75976d23356f387cd8fb' });
}
const selected = process.argv.slice(2);
const results = await Promise.allSettled(Object.entries(selections).filter(([name]) => !selected.length || selected.includes(name)).map(async ([name, pattern]) => {
  const assets = JSON.parse(await readFile(`artifacts/native/${name}-release.json`, 'utf8'));
  const asset = assets.find(a => pattern.test(a.name));
  if (!asset) throw new Error(`Missing ${name} release asset`);
  await install(name, asset);
}));
for (const result of results) if (result.status === 'rejected') { console.error(result.reason); process.exitCode = 1; }
