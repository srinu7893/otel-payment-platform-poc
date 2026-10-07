import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url)).replaceAll('\\', '/').replace(/\/$/, '');
process.chdir(root);
const base = `${root}/artifacts/native-runtime`;
for (const dir of ['config', 'logs', 'postgres', 'rabbitmq', 'home', 'tempo/wal', 'tempo/blocks', 'tempo/generator', 'loki/chunks', 'loki/rules', 'loki/compactor', 'collector/queues', 'prometheus', 'grafana/data', 'grafana/logs', 'grafana/plugins', 'grafana/provisioning/datasources', 'grafana/provisioning/dashboards']) await mkdir(`${base}/${dir}`, { recursive: true });
async function convert(source, destination, substitutions) {
  let text = await readFile(source, 'utf8');
  for (const [from, to] of substitutions) text = text.replaceAll(from, to);
  await writeFile(destination, text);
}
await convert('observability/collector.yaml', `${base}/config/collector.yaml`, [
  ['0.0.0.0:4317','127.0.0.1:14317'],['0.0.0.0:4318','127.0.0.1:14318'],['tempo:4317','127.0.0.1:24317'],['loki:3100','127.0.0.1:3100'],['/var/lib/otelcol/queues',`${base}/collector/queues`],['0.0.0.0','127.0.0.1'],
]);
await convert('observability/tempo.yaml', `${base}/config/tempo.yaml`, [
  ['http_listen_port: 3200','http_listen_port: 3200\n  http_listen_address: 127.0.0.1\n  grpc_listen_address: 127.0.0.1'],['0.0.0.0:4317','127.0.0.1:24317'],['0.0.0.0:4318','127.0.0.1:24318'],['/var/tempo',`${base}/tempo`],['prometheus:9090','127.0.0.1:9090'],
]);
await convert('observability/loki.yaml', `${base}/config/loki.yaml`, [
  ['http_listen_port: 3100','http_listen_port: 3100\n  http_listen_address: 127.0.0.1\n  grpc_listen_port: 9096\n  grpc_listen_address: 127.0.0.1'],['/loki',`${base}/loki`],['replication_factor: 1','replication_factor: 1\n  instance_addr: 127.0.0.1'],
]);
await convert('observability/prometheus.yaml', `${base}/config/prometheus.yaml`, [
  ['/etc/prometheus/alerts.yaml',`${root}/observability/alerts.yaml`],['otel-collector:','127.0.0.1:'],['rabbitmq:','127.0.0.1:'],['tempo:','127.0.0.1:'],['alertmanager:','127.0.0.1:'],
]);
await convert('observability/grafana/provisioning/datasources/datasources.yaml', `${base}/grafana/provisioning/datasources/datasources.yaml`, [
  ['http://prometheus:','http://127.0.0.1:'],['http://tempo:','http://127.0.0.1:'],['http://loki:','http://127.0.0.1:'],
]);
await convert('observability/grafana/provisioning/dashboards/dashboards.yaml', `${base}/grafana/provisioning/dashboards/dashboards.yaml`, [['/var/lib/grafana/dashboards',`${root}/observability/grafana/dashboards`]]);
await writeFile(`${base}/config/rabbitmq.conf`, `listeners.tcp.1 = 127.0.0.1:5672\nmanagement.tcp.ip = 127.0.0.1\nmanagement.tcp.port = 15672\nprometheus.tcp.ip = 127.0.0.1\nprometheus.tcp.port = 15692\ndefault_user = payments\ndefault_pass = payments\ndefault_user_tags.administrator = true\n`);
await writeFile(`${base}/config/enabled_plugins`, '[rabbitmq_management,rabbitmq_prometheus].\n');
await writeFile(`${base}/config/grafana.ini`, `[server]\nhttp_addr = 127.0.0.1\nhttp_port = 3001\nroot_url = http://localhost:3001\n[paths]\ndata = ${base}/grafana/data\nlogs = ${base}/grafana/logs\nplugins = ${base}/grafana/plugins\nprovisioning = ${base}/grafana/provisioning\n[security]\nadmin_user = admin\nadmin_password = otel-demo-admin\n[users]\nallow_sign_up = false\n[analytics]\nreporting_enabled = false\ncheck_for_updates = false\n[plugins]\npreinstall_disabled = true\n`);
await mkdir(`${base}/alertmanager`,{recursive:true});
await convert('observability/alertmanager.yaml',`${base}/config/alertmanager.yaml`,[['http://incident-inbox:8090','http://127.0.0.1:9094']]);
const promFile=`${base}/config/prometheus.yaml`;
let prom=await readFile(promFile,'utf8');
prom=prom.replace('scrape_configs:',`scrape_configs:\n- job_name: native-monitor\n  static_configs:\n  - targets: ['127.0.0.1:9097']`);
prom=prom.replace('rule_files:',`rule_files:\n- ${root}/observability/native-alerts.yaml`);
await writeFile(promFile,prom);
console.log(`Generated native configuration: ${base}/config`);
