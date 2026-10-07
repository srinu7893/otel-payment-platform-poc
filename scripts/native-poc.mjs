import { spawn, spawnSync } from 'node:child_process';
import { mkdir, readFile, writeFile, readdir, open } from 'node:fs/promises';
import { openSync, closeSync, existsSync, watchFile } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import net from 'node:net';
const root = fileURLToPath(new URL('../', import.meta.url));
process.chdir(root);
const runtime = path.join(root, 'artifacts/native-runtime');
const java = process.env.POC_JAVA_HOME || 'C:/Program Files/Zulu/zulu-21';
const pg = process.env.PG_HOME || 'C:/Program Files/PostgreSQL/17';
const erlang = path.join(root, 'artifacts/native/erlang');
const config = name => path.join(runtime, 'config', name);
const env = { ...process.env, JAVA_HOME: java, ERLANG_HOME: erlang, PGCONNECT_TIMEOUT: '5', PGPASSWORD: 'payments', RABBITMQ_BASE: path.join(runtime, 'rabbitmq'), RABBITMQ_CONFIG_FILE: config('rabbitmq'), RABBITMQ_ENABLED_PLUGINS_FILE: config('enabled_plugins'), RABBITMQ_NODENAME: 'otel_poc@localhost', RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS: '+S 2:2 -kernel inet_dist_use_interface {127,0,0,1}', ERL_EPMD_ADDRESS: '127.0.0.1', RABBITMQ_SERVER_START_ARGS: `-home "${path.join(runtime, 'home')}"` };
// RabbitMQ's batch environment comparisons do not accept embedded quotes.
env.RABBITMQ_SERVER_START_ARGS = `-home ${path.join(runtime, 'home')}`;
env.APPDATA = path.join(runtime, 'home');
env.ERL_CRASH_DUMP = path.join(runtime, 'logs', 'erl_crash.dump');
env.DEBUG = 'false';
env.LOGGING_LEVEL_ROOT = 'INFO';
const stateFile = path.join(runtime, 'processes.json');
let state = {};
try { state = JSON.parse(await readFile(stateFile, 'utf8')); } catch {}
async function save() { await writeFile(stateFile, JSON.stringify(state, null, 2)); }
function run(exe, args, extra = {}) {
  const result = spawnSync(exe, args, { cwd: root, env, windowsHide: true, stdio: 'inherit', ...extra });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${path.basename(exe)} failed: ${result.status}`);
}
async function listening(port) {
  return new Promise(resolve => { const socket = net.connect({ host: '127.0.0.1', port }); socket.setTimeout(1000); socket.once('connect', () => { socket.destroy(); resolve(true); }); socket.once('error', () => resolve(false)); socket.once('timeout', () => { socket.destroy(); resolve(false); }); });
}
async function start(name, exe, args, extra = {}, port) {
  if (process.argv[2] === 'start' && process.argv[3] && process.argv[3] !== name) return;
  if (state[name]) {
    const list = spawnSync('C:/Windows/System32/tasklist.exe', ['/FI', `PID eq ${state[name].pid}`, '/FO', 'CSV', '/NH'], { windowsHide: true, encoding: 'utf8' });
    if (list.status !== 0) throw new Error(`Cannot verify tracked process identity: ${list.error || list.stderr}`);
    const image = list.stdout.match(/^"([^"]+)"/)?.[1];
    if (image?.toLowerCase() === path.basename(exe).toLowerCase()) {
      if (port && !await listening(port)) throw new Error(`${name}: tracked process exists but port ${port} is not ready; inspect its log`);
      console.log(`Already running: ${name} (${state[name].pid})`); return;
    }
    delete state[name]; await save();
  }
  if (port && await listening(port)) throw new Error(`${name}: port ${port} is occupied by an untracked process`);
  const log = path.join(runtime, 'logs', `${name}.log`);
  const fd = openSync(log, 'a');
  const child = spawn(exe, args, { cwd: root, env: { ...env, ...extra }, detached: true, windowsHide: true, stdio: ['ignore', fd, fd] });
  await new Promise((resolve, reject) => { child.once('spawn', resolve); child.once('error', reject); });
  closeSync(fd); child.unref();
  state[name] = { pid: child.pid, exe, port, log, startedAt: new Date().toISOString() }; await save();
  console.log(`Started ${name}: PID ${child.pid}, log ${log}`);
}
async function postgres() {
  const data = path.join(runtime, 'postgres');
  if (!existsSync(path.join(data, 'PG_VERSION'))) {
    const password = path.join(runtime, 'config', 'pg-password.txt');
    await writeFile(password, 'payments\n');
    run(path.join(pg, 'bin/initdb.exe'), ['-D', data, '-U', 'payments', '--pwfile', password, '--auth=scram-sha-256', '--encoding=UTF8', '--locale=C']);
  }
  const status = spawnSync(path.join(pg, 'bin/pg_ctl.exe'), ['-D', data, 'status'], { env, windowsHide: true });
  if (status.status !== 0) {
    if (await listening(55432)) throw new Error('PostgreSQL port 55432 is occupied');
    run(path.join(pg, 'bin/pg_ctl.exe'), ['-D', data, '-l', path.join(runtime, 'logs/postgres.log'), '-o', '-p 55432 -h 127.0.0.1', '-w', 'start']);
  }
  const args = ['-h', '127.0.0.1', '-p', '55432', '-U', 'payments', '-d', 'postgres', '-tAc', "SELECT 1 FROM pg_database WHERE datname='payments'"];
  const result = spawnSync(path.join(pg, 'bin/psql.exe'), args, { env, windowsHide: true, encoding: 'utf8' });
  if (result.status !== 0) throw new Error(result.stderr || 'Database connection failed');
  if (result.stdout.trim() !== '1') run(path.join(pg, 'bin/createdb.exe'), ['-h', '127.0.0.1', '-p', '55432', '-U', 'payments', 'payments']);
  console.log('PostgreSQL ready on 127.0.0.1:55432 (isolated project data)');
}
async function infra() {
  await postgres();
  await mkdir(path.join(runtime, 'home', 'erlang'), { recursive: true });
  await start('rabbitmq', path.join(erlang,'bin/erl.exe'), ['-noinput','-s','rabbit','boot','-boot','start_sasl','+W','w','+S','2:2','-kernel','inet_dist_use_interface','{127,0,0,1}','-kernel','prevent_overlapping_partitions','false'], { ERL_LIBS: path.join(root,'artifacts/native/rabbitmq/rabbitmq_server-4.1.4/plugins') }, 5672);
  await start('tempo', path.join(root, 'artifacts/native/tempo/tempo.exe'), [`-config.file=${config('tempo.yaml')}`], {}, 3200);
  await start('loki', path.join(root, 'artifacts/native/loki/loki-windows-amd64.exe'), [`-config.file=${config('loki.yaml')}`], {}, 3100);
  await start('prometheus', path.join(root, 'artifacts/native/prometheus/prometheus-3.6.0.windows-amd64/prometheus.exe'), [`--config.file=${config('prometheus.yaml')}`, `--storage.tsdb.path=${path.join(runtime,'prometheus')}`, '--storage.tsdb.retention.time=24h', '--web.listen-address=127.0.0.1:9090', '--web.enable-remote-write-receiver'], {}, 9090);
  await start('otel-collector', path.join(root, 'artifacts/native/collector/otelcol-contrib.exe'), [`--config=${config('collector.yaml')}`], {}, 14318);
}
async function grafana() {
  const entries = await readdir(path.join(root, 'artifacts/native/grafana'));
  const home = path.join(root, 'artifacts/native/grafana', entries.find(n => n.startsWith('grafana-')) || '');
  await start('grafana', path.join(home, 'bin/grafana.exe'), ['server', '--homepath', home, '--config', config('grafana.ini')], {}, 3001);
}
async function support() {
  await start('incident-inbox',path.join(root,'artifacts/native/python/python.exe'),[path.join(root,'observability/incident-inbox/server.py')],{INBOX_HOST:'127.0.0.1',INBOX_PORT:'9094',INBOX_DB:path.join(runtime,'incident-inbox.sqlite')},9094);
  await start('alertmanager',path.join(root,'artifacts/native/alertmanager/alertmanager-0.28.1.windows-amd64/alertmanager.exe'),[`--config.file=${config('alertmanager.yaml')}`,`--storage.path=${path.join(runtime,'alertmanager')}`,'--web.listen-address=127.0.0.1:9093','--cluster.listen-address='],{},9093);
  await start('native-monitor',process.execPath,[path.join(root,'scripts/native-monitor.mjs')],{},9097);
}
const services = { 'auth-service':8079, 'customer-service':8084, 'mock-bank-service':8082, 'gateway-service':18081, 'notification-service':8083, 'payment-service':8080, 'api-gateway':8088 };
async function apps() {
  for (const [name, port] of Object.entries(services)) {
    const appEnv = {
      SERVER_PORT: String(port), SERVER_ADDRESS:'127.0.0.1', SPRING_DATASOURCE_URL:'jdbc:postgresql://127.0.0.1:55432/payments', SPRING_DATASOURCE_USERNAME:'payments', SPRING_DATASOURCE_PASSWORD:'payments', SPRING_RABBITMQ_HOST:'127.0.0.1', SPRING_RABBITMQ_USERNAME:'payments', SPRING_RABBITMQ_PASSWORD:'payments', JWT_SECRET:'change-this-demo-secret-change-this-demo-secret', CLIENTS_GATEWAY_BASE_URL:'http://127.0.0.1:18081', CLIENTS_BANK_BASE_URL:'http://127.0.0.1:8082', CLIENTS_CUSTOMER_BASE_URL:'http://127.0.0.1:8084', SERVICES_GATEWAY_URL:'http://127.0.0.1:18081', SERVICES_BANK_URL:'http://127.0.0.1:8082',
      BUSINESS_DOMAIN_MONITORING_ENABLED: name==='payment-service'?'true':'false',
      RISK_MAX_TRANSACTION_AMOUNT:'50000.00', RISK_MAX_DAILY_CUSTOMER_AMOUNT:'100000.00', RISK_MAX_DAILY_ACCOUNT_AMOUNT:'100000.00',
      OTEL_EXPORTER_OTLP_ENDPOINT:'http://127.0.0.1:14318', OTEL_EXPORTER_OTLP_PROTOCOL:'http/protobuf', OTEL_TRACES_EXPORTER:'otlp', OTEL_METRICS_EXPORTER:'otlp', OTEL_LOGS_EXPORTER:'otlp', OTEL_PROPAGATORS:'tracecontext,baggage', OTEL_TRACES_SAMPLER:'always_on', OTEL_METRIC_EXPORT_INTERVAL:'10000', OTEL_RESOURCE_ATTRIBUTES:'service.namespace=payment-poc,deployment.environment.name=local,service.version=0.1.0', OTEL_INSTRUMENTATION_MICROMETER_ENABLED:'true', OTEL_INSTRUMENTATION_LOGBACK_APPENDER_EXPERIMENTAL_CAPTURE_MDC_ATTRIBUTES:'correlationId', OTEL_INSTRUMENTATION_JDBC_STATEMENT_SANITIZER_ENABLED:'true', OTEL_SERVICE_NAME:name,
    };
    await start(name, path.join(java, 'bin/java.exe'), ['-Xms64m','-Xmx384m',`-javaagent:${path.join(root,'observability/agent/opentelemetry-javaagent.jar')}`,'-jar',path.join(root,name,`target/${name}-0.1.0-SNAPSHOT.jar`)], appEnv, port);
  }
}
async function frontend() {
  await start('frontend', process.execPath, [path.join(root,'frontend/node_modules/vite/bin/vite.js'), '--host','127.0.0.1','--port','3000','--strictPort', '--config', path.join(root,'frontend/vite.config.js'), path.join(root,'frontend')], {}, 3000);
}
try {
  const action = process.argv[2] || 'status';
  if (['start','infra','apps','grafana','frontend','support'].includes(action)) await import('./native-config.mjs');
  if (action === 'start' || action === 'infra') await infra();
  if (action === 'start' || action === 'grafana') await grafana();
  if (action === 'start' || action === 'apps') await apps();
  if (action === 'start' || action === 'frontend') await frontend();
  if (action === 'start' || action === 'support') await support();
  if (action === 'status') for (const [name, item] of Object.entries(state)) console.log(`${name}: ${item.port && await listening(item.port) ? 'LISTENING' : 'NOT LISTENING'} port=${item.port} pid=${item.pid}`);
  if (action === 'logs') await followLogs(process.argv[3] || 'payment-service');
  if (action === 'restart') {
    const name = process.argv[3];
    if (!services[name]) throw new Error('restart requires one Java service name');
    await stop(name);
    await apps();
  }
  if (action === 'stop') {
    if (process.argv[3] && !state[process.argv[3]]) throw new Error('Unknown tracked service');
    await stop(process.argv[3]);
  }
  if (!['start','infra','apps','grafana','frontend','support','status','logs','stop','restart'].includes(action)) throw new Error('Usage: scripts\\run-poc.cmd start [service]|support|status|check|build|logs [service]|restart service|stop [service]');
} catch (error) { console.error(error); process.exitCode = 1; }

async function followLogs(name) {
  if (!state[name]) throw new Error(`Unknown service: ${name}. Use status to list services.`);
  const file = state[name].log;
  const handle = await open(file, 'r');
  let position = Math.max(0, (await handle.stat()).size - 12000);
  async function readNew() {
    const size = (await handle.stat()).size;
    if (size < position) position = 0;
    while (position < size) {
      const bytes = Buffer.alloc(Math.min(65536, size - position));
      const result = await handle.read(bytes, 0, bytes.length, position);
      if (!result.bytesRead) break;
      position += result.bytesRead;
      process.stdout.write(bytes.subarray(0, result.bytesRead));
    }
  }
  await readNew();
  console.log(`\nFollowing ${file} (Ctrl+C stops following)`);
  let reading = Promise.resolve();
  watchFile(file, { interval: 1000 }, () => { reading = reading.then(readNew).catch(console.error); });
}
async function stop(only) {
  for (const [name, item] of Object.entries(state).reverse()) {
    if (only && name !== only) continue;
    const list = spawnSync('C:/Windows/System32/tasklist.exe', ['/FI',`PID eq ${item.pid}`,'/FO','CSV','/NH'], { windowsHide:true, encoding:'utf8' });
    if (list.status !== 0) throw new Error('Unable to verify process identity; nothing further will be stopped.');
    const image = list.stdout.match(/^"([^"]+)"/)?.[1];
    if (image?.toLowerCase() === path.basename(item.exe).toLowerCase()) {
      run('C:/Windows/System32/taskkill.exe', ['/PID', String(item.pid), '/T', '/F']);
      console.log(`Stopped ${name}`);
    } else console.log(`Skipped ${name}: recorded process is no longer present`);
    delete state[name]; await save();
  }
  if (only) return;
  const data = path.join(runtime, 'postgres');
  const status = spawnSync(path.join(pg, 'bin/pg_ctl.exe'), ['-D',data,'status'], { env,windowsHide:true });
  if (status.status === 0) run(path.join(pg,'bin/pg_ctl.exe'), ['-D',data,'-m','fast','-w','stop']);
}
