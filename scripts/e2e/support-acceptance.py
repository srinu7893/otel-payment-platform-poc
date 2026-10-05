#!/usr/bin/env python3
"""Real DB gauges and Prometheus -> Alertmanager -> inbox outage/recovery evidence."""
import json
import subprocess
import time
import urllib.parse
import urllib.request
from pathlib import Path
from otel_acceptance import request,eventually,login,payment,notification

results=[]
def query(expr):
    data=request('http://localhost:9090/api/v1/query?'+urllib.parse.urlencode({'query':expr}))
    assert data['status']=='success';return data['data']['result']
def value(expr):
    rows=query(expr);assert rows,'No series: '+expr;return float(rows[0]['value'][1])
def compose(*args):subprocess.run(['bash','scripts/otel-compose.sh',*args],check=True,timeout=120)
def db(sql):
    raw=subprocess.check_output(['bash','scripts/otel-compose.sh','exec','-T','postgres','psql','-U','payments','-d','payments','-tA','-c',sql],text=True,timeout=20)
    return float(raw.strip())
def check_snapshot():
    completed=db("select count(*) from payment.payments where status='COMPLETED'")
    sent=db("select count(*) from notification.notifications where status='SENT'")
    assert value('max(poc_payment_records{status="COMPLETED"})')==completed
    assert value('max(poc_notification_records{status="SENT"})')==sent
    assert value('min(poc_business_snapshot_healthy)')==1
    assert value('max(time() - poc_business_snapshot_last_success_timestamp_seconds)')<40
    assert value('poc_synthetic_journey_success')==1
    return {'completedPayments':completed,'sentNotificationRecords':sent,'boundedStatusLabels':sorted({r['metric']['status'] for r in query('poc_payment_records')})}
def incident(status):
    rows=request('http://localhost:9094/api/incidents')
    return next((r for r in rows if r['name']=='OutboxDeliveryDelayed' and r['status']==status and (status=='firing' or r['id']==firing['id'])),None)

try:
    snapshot=eventually(check_snapshot,timeout=100);results.append({'scenario':'Durable metric snapshots equal live database counts','status':'PASS','snapshot':snapshot})
    token=login();compose('stop','rabbitmq')
    try:
        ident=payment(token)
        def age_alert():
            assert value('max(poc_outbox_pending)')>=1
            assert value('max(poc_outbox_oldest_pending_seconds)')>60
            firing=incident('firing');assert firing,'Alert not delivered to inbox';return firing
        firing=eventually(age_alert,timeout=240)
        request('http://localhost:9094/api/ack',{'id':firing['id']})
        results.append({'scenario':'Real delayed outbox alert routed and acknowledged','status':'PASS','incidentId':firing['id'],'paymentId':ident,'owner':firing['owner']})
    finally:compose('start','rabbitmq')
    eventually(lambda:notification(token,ident),timeout=180)
    def recovered():
        assert value('max(poc_outbox_pending)')==0
        assert value('max(poc_outbox_oldest_pending_seconds)')==0
        resolved=incident('resolved');assert resolved and resolved['id']==firing['id'];assert resolved['acknowledged'];return resolved
    resolved=eventually(recovered,timeout=180)
    results.append({'scenario':'Notification catch-up and same incident resolved','status':'PASS','incidentId':resolved['id']})
    print('PASS: durable gauges, real alert delivery, acknowledgement and resolution',flush=True)
except Exception as error:
    results.append({'scenario':'Support enhancement acceptance','status':'FAIL','errorType':type(error).__name__});raise
finally:
    Path('artifacts/support-results.json').write_text(json.dumps(results,indent=2))
    try:Path('artifacts/incident-inbox.json').write_text(json.dumps(request('http://localhost:9094/api/incidents'),indent=2))
    except Exception:pass
