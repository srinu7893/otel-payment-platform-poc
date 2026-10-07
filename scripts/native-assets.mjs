import { mkdir, writeFile } from 'node:fs/promises';
const repos = [['tempo','grafana/tempo','v2.9.0'],['collector','open-telemetry/opentelemetry-collector-releases','v0.136.0'],['prometheus','prometheus/prometheus','v3.6.0'],['loki','grafana/loki','v3.5.5'],['rabbitmq','rabbitmq/rabbitmq-server','v4.1.4'],['erlang','erlang/otp','OTP-27.3.4.3']];
await mkdir('artifacts/native', { recursive: true });
for (const [name, repo, version] of repos) {
  const r = await fetch(`https://api.github.com/repos/${repo}/releases/tags/${version}`);
  if (!r.ok) { console.log(name, r.status); continue; }
  const data = await r.json();
  const assets = data.assets.filter(a=>/windows.*amd64|win64|windows.*zip|checksums|sha256/i.test(a.name));
  await writeFile(`artifacts/native/${name}-release.json`, JSON.stringify(assets,null,2));
  console.log(`${name}: saved official release metadata (${assets.length} matching assets)`);
}
