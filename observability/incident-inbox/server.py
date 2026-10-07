#!/usr/bin/env python3
"""Isolated local PoC notification receiver. No outbound messaging or production auth."""
import hashlib
from contextlib import contextmanager
import html
import json
import os
import sqlite3
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

class Inbox:
    def __init__(self, path):
        self.path=str(path)
        self.lock=threading.Lock()
        with self.connect() as db:
            db.execute('create table if not exists incidents (id text primary key, fingerprint text, name text, service text, owner text, severity text, summary text, status text, started text, ended text, received real, acknowledged real)')
    @contextmanager
    def connect(self):
        db=sqlite3.connect(self.path, timeout=5)
        try:
            with db:
                yield db
        finally:
            db.close()
    def ingest(self, payload):
        alerts=payload.get('alerts')
        if not isinstance(alerts,list) or not 1<=len(alerts)<=100:
            raise ValueError('Expected 1..100 alerts')
        values=[]
        for alert in alerts:
            labels=alert.get('labels',{}); annotations=alert.get('annotations',{})
            if not isinstance(labels,dict) or not isinstance(annotations,dict):raise ValueError('Invalid fields')
            status=alert.get('status'); start=alert.get('startsAt'); fingerprint=alert.get('fingerprint')
            if status not in ('firing','resolved') or not isinstance(start,str) or not start or not isinstance(fingerprint,str) or not fingerprint:raise ValueError('Invalid alert identity/status')
            def safe(value):
                if not isinstance(value,str):raise ValueError('Fields must be strings')
                return value[:500]
            ident=hashlib.sha256((safe(fingerprint)+'|'+safe(start)).encode()).hexdigest()
            values.append((ident,safe(fingerprint),safe(labels.get('alertname','unknown')),safe(labels.get('service',labels.get('service_name','platform'))),safe(labels.get('owner','poc-support')),safe(labels.get('severity','warning')),safe(annotations.get('summary','')),status,safe(start),safe(alert.get('endsAt','')),time.time()))
        with self.lock, self.connect() as db:
            for value in values:
                db.execute('insert into incidents values (?,?,?,?,?,?,?,?,?,?,?,NULL) on conflict(id) do update set status=case when incidents.status="resolved" then "resolved" else excluded.status end, ended=case when excluded.status="resolved" then excluded.ended else incidents.ended end',value)
            db.execute('delete from incidents where id not in (select id from incidents order by received desc limit 2000)')
        return len(values)
    def list(self):
        with self.connect() as db:
            db.row_factory=sqlite3.Row
            return [dict(r) for r in db.execute('select * from incidents order by received desc limit 200')]
    def acknowledge(self, ident):
        with self.lock, self.connect() as db:
            cursor=db.execute('update incidents set acknowledged=coalesce(acknowledged,?) where id=?',(time.time(),ident))
            return cursor.rowcount==1

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def send(self,status,body,content_type='application/json'):
        data=body.encode() if isinstance(body,str) else json.dumps(body).encode()
        self.send_response(status);self.send_header('Content-Type',content_type);self.send_header('Content-Length',str(len(data)));self.send_header('X-Content-Type-Options','nosniff');self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(data)
    def do_GET(self):
        if self.path=='/health':self.send(200,{'status':'UP'});return
        if self.path=='/api/incidents':self.send(200,self.server.inbox.list());return
        if self.path!='/':self.send(404,{'error':'Not found'});return
        rows=[]
        for item in self.server.inbox.list():
            fields=[item[k] for k in ('name','service','owner','status','summary','started')]
            cells=''.join('<td>'+html.escape(str(v))+'</td>' for v in fields)
            ack='Acknowledged' if item['acknowledged'] else '<button data-id="'+item['id']+'">Acknowledge</button>'
            rows.append('<tr>'+cells+'<td>'+ack+'</td></tr>')
        self.send(200,'<!doctype html><html><head><meta charset="utf-8"><title>PoC incident inbox</title><style>body{font:16px system-ui;padding:32px;color:#19364a}table{border-collapse:collapse}td,th{padding:12px;border-bottom:1px solid #ccd}button{padding:8px}td{max-width:360px;overflow-wrap:anywhere}</style></head><body><h1>PoC incident inbox</h1><p>Local Alertmanager delivery, ownership and acknowledgement demo. Refresh to see updates. This is not a real on-call pager.</p><p><a href="http://localhost:9093">Alertmanager: silences / active alerts</a> · <a href="http://localhost:3001/d/telemetry-pipeline">Pipeline dashboard</a></p><table><tr><th>Alert</th><th>Service</th><th>Owner</th><th>Status</th><th>Summary</th><th>Started</th><th>Action</th></tr>'+''.join(rows)+'</table><script>document.querySelectorAll("button").forEach(b=>b.onclick=async()=>{const r=await fetch("/api/ack",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({id:b.dataset.id})});if(r.ok)location.reload();else alert("Acknowledgement failed");});</script></body></html>','text/html; charset=utf-8')
    def do_POST(self):
        if self.path not in ('/webhook','/api/ack'):self.send(404,{'error':'Not found'});return
        # Browser writes must come from this origin. Alertmanager has no Origin header.
        origin=self.headers.get('Origin')
        if origin and origin!='http://'+self.headers.get('Host',''):self.send(403,{'error':'Origin rejected'});return
        try:
            length=int(self.headers.get('Content-Length','0'))
            if not 0<length<=262144 or self.headers.get('Content-Type','').split(';')[0]!='application/json':raise ValueError('Invalid request')
            payload=json.loads(self.rfile.read(length))
            if not isinstance(payload,dict):raise ValueError('Object required')
            if self.path=='/webhook':self.send(200,{'accepted':self.server.inbox.ingest(payload)})
            else:
                ident=payload.get('id')
                if not isinstance(ident,str) or len(ident)!=64:raise ValueError('Invalid id')
                found=self.server.inbox.acknowledge(ident);self.send(200 if found else 404,{'acknowledged':found})
        except (ValueError,TypeError,KeyError,AttributeError):self.send(400,{'error':'Invalid request'})
        except sqlite3.Error:self.send(503,{'error':'Inbox storage unavailable'})

if __name__=='__main__':
    path=Path(os.environ.get('INBOX_DB','/data/inbox.sqlite'));path.parent.mkdir(parents=True,exist_ok=True)
    server=ThreadingHTTPServer((os.environ.get('INBOX_HOST','0.0.0.0'),int(os.environ.get('INBOX_PORT','8090'))),Handler);server.inbox=Inbox(path);server.serve_forever()
