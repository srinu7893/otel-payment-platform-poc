"""Isolated Prometheus -> Alertmanager -> webhook firing/recovery drill."""
import argparse,http.server,json,socket,subprocess,tempfile,threading,time,urllib.request
from pathlib import Path

def port():
    with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def wait(fn,seconds=45):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try:
            if fn():return
        except OSError:pass
        time.sleep(.25)
    raise AssertionError('Alert firing/recovery did not converge')
def main():
    p=argparse.ArgumentParser();p.add_argument('--prometheus',required=True);p.add_argument('--alertmanager',required=True);a=p.parse_args()
    state=0;events=[];prom,am,cluster=port(),port(),port()
    class Receiver(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200);self.send_header('Content-Type','text/plain; version=0.0.4');self.end_headers();self.wfile.write(f'poc_isolated_alert_drill {state}\n'.encode())
        def do_POST(self):
            data=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
            events.extend(row['status'] for row in data.get('alerts',[]));self.send_response(200);self.end_headers()
        def log_message(self,*args):pass
    server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Receiver);threading.Thread(target=server.serve_forever,daemon=True).start()
    with tempfile.TemporaryDirectory(prefix='otel-alert-drill-') as tmp:
        root=Path(tmp);rule=root/'rules.json';rule.write_text(json.dumps({'groups':[{'name':'isolated','rules':[{'alert':'IsolatedDeliveryDrill','expr':'poc_isolated_alert_drill == 1','for':'1s'}]}]}))
        ac=root/'alertmanager.json';ac.write_text(json.dumps({'route':{'receiver':'local','group_wait':'1s','group_interval':'1s','repeat_interval':'1h'},'receivers':[{'name':'local','webhook_configs':[{'url':f'http://127.0.0.1:{server.server_port}/alerts','send_resolved':True}]}]}))
        pc=root/'prometheus.json';pc.write_text(json.dumps({'global':{'scrape_interval':'1s','evaluation_interval':'1s'},'rule_files':[rule.as_posix()],
            'scrape_configs':[{'job_name':'isolated','static_configs':[{'targets':[f'127.0.0.1:{server.server_port}']}]}],
            'alerting':{'alertmanagers':[{'static_configs':[{'targets':[f'127.0.0.1:{am}']}]}]}}))
        log=open(root/'process.log','wb');processes=[]
        try:
            processes.append(subprocess.Popen([a.alertmanager,'--config.file='+str(ac),'--storage.path='+str(root/'am'),'--web.listen-address=127.0.0.1:'+str(am),'--cluster.listen-address=127.0.0.1:'+str(cluster)],stdout=log,stderr=log))
            processes.append(subprocess.Popen([a.prometheus,'--config.file='+str(pc),'--storage.tsdb.path='+str(root/'prom'),'--web.listen-address=127.0.0.1:'+str(prom)],stdout=log,stderr=log))
            def ready():
                with urllib.request.urlopen(f'http://127.0.0.1:{prom}/-/ready',timeout=2) as response:return response.status==200
            wait(ready);state=1;wait(lambda:'firing' in events);state=0;wait(lambda:'resolved' in events)
            result={'status':'PASS','firing':events.count('firing'),'resolved':events.count('resolved'),'scope':'Real local Prometheus and Alertmanager with isolated webhook. Not proof of GCP channel delivery.'}
            out=Path('artifacts/alert-delivery/results.json');out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2));print(json.dumps(result))
        except Exception:
            log.flush();print((root/'process.log').read_text(errors='replace')[-10000:]);raise
        finally:
            for proc in reversed(processes):
                if proc.poll() is None:proc.terminate();proc.wait(timeout=15)
            server.shutdown();log.close()
if __name__=='__main__':main()
