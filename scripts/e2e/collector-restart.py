"""Isolated real collector queue test: backend outage, kill, restart, recovery."""
import argparse,gzip,http.server,json,os,socket,subprocess,tempfile,threading,time,urllib.request
from pathlib import Path

def port():
    with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def wait(fn,seconds=40):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try:
            if fn():return
        except (OSError,ValueError):pass
        time.sleep(.2)
    raise AssertionError('Collector restart drill did not converge')
def main():
    p=argparse.ArgumentParser();p.add_argument('--collector',required=True);p.add_argument('--out',default='artifacts/collector-restart/results.json');a=p.parse_args()
    collector=str(Path(a.collector).resolve());receiver,metrics=port(),port();healthy=False;seen=set()
    markers={f'queue-restart-{i:02}' for i in range(20)}
    class Sink(http.server.BaseHTTPRequestHandler):
        def do_POST(self):
            body=self.rfile.read(int(self.headers['Content-Length']))
            if self.headers.get('Content-Encoding')=='gzip':body=gzip.decompress(body)
            if healthy:
                seen.update(marker for marker in markers if marker.encode() in body)
                self.send_response(200);self.send_header('Content-Type','application/x-protobuf');self.end_headers()
            else:self.send_response(503);self.end_headers()
        def log_message(self,*args):pass
    sink=http.server.ThreadingHTTPServer(('127.0.0.1',0),Sink);threading.Thread(target=sink.serve_forever,daemon=True).start()
    with tempfile.TemporaryDirectory(prefix='otel-queue-drill-') as tmp:
        root=Path(tmp);queue=root/'queue';queue.mkdir()
        config={'receivers':{'otlp':{'protocols':{'http':{'endpoint':f'127.0.0.1:{receiver}'}}}},
            'exporters':{'otlphttp':{'endpoint':f'http://127.0.0.1:{sink.server_port}',
                'sending_queue':{'queue_size':100,'num_consumers':1,'storage':'file_storage'},
                'retry_on_failure':{'initial_interval':'1s','max_interval':'1s','max_elapsed_time':'0s'}}},
            'extensions':{'file_storage':{'directory':queue.as_posix()}},
            'service':{'extensions':['file_storage'],'pipelines':{'traces':{'receivers':['otlp'],'exporters':['otlphttp']}},
                'telemetry':{'metrics':{'readers':[{'pull':{'exporter':{'prometheus':{'host':'127.0.0.1','port':metrics,'without_type_suffix':True,'without_units':True}}}}]}}}}
        file=root/'collector.json';file.write_text(json.dumps(config));log=open(root/'collector.log','wb')
        def start():return subprocess.Popen([collector,'--config='+str(file)],stdout=log,stderr=log)
        proc=start()
        def metrics_text():return urllib.request.urlopen(f'http://127.0.0.1:{metrics}/metrics',timeout=2).read().decode()
        try:
            wait(lambda:'otelcol_' in metrics_text())
            for i,marker in enumerate(sorted(markers)):
                now=time.time_ns()
                data={'resourceSpans':[{'resource':{'attributes':[{'key':'service.name','value':{'stringValue':'isolated-restart-drill'}}]},'scopeSpans':[{'spans':[{'traceId':f'{i+1:032x}','spanId':f'{i+1:016x}','name':marker,'startTimeUnixNano':str(now),'endTimeUnixNano':str(now+1000)}]}]}]}
                req=urllib.request.Request(f'http://127.0.0.1:{receiver}/v1/traces',data=json.dumps(data).encode(),headers={'Content-Type':'application/json'})
                with urllib.request.urlopen(req,timeout=5) as response:assert response.status==200
            wait(lambda:any(line.startswith('otelcol_exporter_queue_size{') and float(line.rsplit(' ',1)[1])>0 for line in metrics_text().splitlines()))
            assert not seen,'Outage unexpectedly delivered data'
            proc.kill();proc.wait(timeout=10)
            healthy=True;proc=start()
            wait(lambda:seen==markers)
            result={'status':'PASS','accepted':len(markers),'recovered':len(seen),'resubmitted':0,
                    'scope':'Real pinned collector; persistent local disk survives process kill. Not Cloud Run instance disk durability.'}
            out=Path(a.out);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2));print(json.dumps(result))
        finally:
            if proc.poll() is None:proc.terminate();proc.wait(timeout=15)
            sink.shutdown();log.close()
if __name__=='__main__':main()
