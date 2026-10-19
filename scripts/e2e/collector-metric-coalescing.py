"""Real pinned-collector regression for duplicate cumulative metric export batches."""
import argparse,gzip,http.server,json,socket,subprocess,tempfile,threading,time,urllib.request
from pathlib import Path
def port():
    with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def wait(fn,seconds=30):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try:
            if fn():return
        except OSError:pass
        time.sleep(.1)
    raise AssertionError('Metric coalescing did not converge')
def main():
    p=argparse.ArgumentParser();p.add_argument('--collector',required=True);a=p.parse_args();receiver,health=port(),port();batches=[]
    class Sink(http.server.BaseHTTPRequestHandler):
        def do_POST(self):
            body=self.rfile.read(int(self.headers['Content-Length']))
            if self.headers.get('Content-Encoding')=='gzip':body=gzip.decompress(body)
            batches.append(json.loads(body));self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(b'{}')
        def log_message(self,*args):pass
    sink=http.server.ThreadingHTTPServer(('127.0.0.1',0),Sink);threading.Thread(target=sink.serve_forever,daemon=True).start()
    with tempfile.TemporaryDirectory(prefix='otel-metric-regression-') as tmp:
        root=Path(tmp);config={'receivers':{'otlp':{'protocols':{'http':{'endpoint':f'127.0.0.1:{receiver}'}}}},
            'processors':{'interval':{'interval':'10s','pass_through':{'gauge':False,'summary':False}}},
            'exporters':{'otlphttp':{'endpoint':f'http://127.0.0.1:{sink.server_port}','encoding':'json','sending_queue':{'num_consumers':1}}},
            'extensions':{'health_check':{'endpoint':f'127.0.0.1:{health}'}},
            'service':{'extensions':['health_check'],'pipelines':{'metrics':{'receivers':['otlp'],'processors':['interval'],'exporters':['otlphttp']}}}}
        file=root/'config.json';file.write_text(json.dumps(config));log=open(root/'collector.log','wb');proc=subprocess.Popen([a.collector,'--config='+str(file)],stdout=log,stderr=log)
        try:
            def ready():
                with urllib.request.urlopen(f'http://127.0.0.1:{health}',timeout=2) as response:return response.status==200
            wait(ready);start=time.time_ns()
            for value in [1,2]:
                point={'startTimeUnixNano':str(start),'timeUnixNano':str(time.time_ns()),'asInt':str(value)}
                data={'resourceMetrics':[{'resource':{'attributes':[{'key':'service.name','value':{'stringValue':'isolated-metrics'}}]},'scopeMetrics':[{'scope':{'name':'regression'},'metrics':[
                    {'name':'test_counter','sum':{'aggregationTemporality':2,'isMonotonic':True,'dataPoints':[point]}},
                    {'name':'test_gauge','gauge':{'dataPoints':[{**point,'asInt':str(value*10)}]}}]}]}]}
                req=urllib.request.Request(f'http://127.0.0.1:{receiver}/v1/metrics',data=json.dumps(data).encode(),headers={'Content-Type':'application/json'})
                with urllib.request.urlopen(req,timeout=5) as response:assert response.status==200
            wait(lambda:any(batch.get('resourceMetrics') or batch.get('resource_metrics') for batch in batches))
            for batch in batches:
                if 'resource_metrics' in batch:batch['resourceMetrics']=batch.pop('resource_metrics')
                for resource in batch.get('resourceMetrics',[]):
                    if 'scope_metrics' in resource:resource['scopeMetrics']=resource.pop('scope_metrics')
            metrics=[metric for batch in batches for resource in batch.get('resourceMetrics',[]) for scope in resource['scopeMetrics'] for metric in scope['metrics']]
            assert len(metrics)==2,'Duplicate time series remained in emitted interval'
            values={m['name']:next(iter(m.get('sum',m.get('gauge'))['dataPoints']))['asInt'] for m in metrics}
            assert int(values['test_counter'])==2,'Cumulative total was lost'
            assert int(values['test_gauge'])==20,'Latest gauge was not selected'
            result={'status':'PASS','inputExports':2,'outputSeries':2,'counterTotal':2,'latestGauge':20,'intervalSeconds':10}
            out=Path('artifacts/collector-metric-coalescing/results.json');out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2));print(json.dumps(result))
        except Exception:
            print('Synthetic received payloads:',json.dumps(batches)[:2000])
            log.flush();print((root/'collector.log').read_text(errors='replace')[-5000:]);raise
        finally:
            if proc.poll() is None:proc.terminate();proc.wait(timeout=15)
            sink.shutdown();log.close()
if __name__=='__main__':main()
