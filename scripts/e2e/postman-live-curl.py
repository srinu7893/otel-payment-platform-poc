"""Seed and verify ready-to-send Postman sandbox records using real curl from CMD.

Uses Windows platform TLS trust. JWTs stay in memory/stdin, never command lines
or evidence files. Only the public frontend proxies calls to private services.
"""
import json,subprocess,uuid
from decimal import Decimal
from pathlib import Path
BASE='https://payment-frontend-52916499838.us-central1.run.app/api/v1'
CURL=r'C:\Windows\System32\curl.exe'
root=Path('artifacts/postman-live-curl');root.mkdir(parents=True,exist_ok=True)
checks=[]
def call(path,body=None,token=None,expected=(200,),origin=None):
    url=(origin or BASE)+path
    config=['url = '+json.dumps(url),'silent','show-error','max-time = 180','write-out = "\\n%{http_code}"']
    if token:config.append('header = '+json.dumps('Authorization: Bearer '+token))
    if body is not None:
        config+=['request = "POST"','header = "Content-Type: application/json"','data = '+json.dumps(json.dumps(body))]
    # Corporate network cannot reach certificate revocation endpoints. Retain
    # chain/hostname verification; tolerate unavailable revocation checks only.
    proc=subprocess.run([CURL,'--ssl-revoke-best-effort','--config','-'],input='\n'.join(config)+'\n',capture_output=True,text=True)
    if proc.returncode:raise RuntimeError('curl transport error: '+proc.stderr[:500])
    raw,code=proc.stdout.rsplit('\n',1);code=int(code)
    assert code in expected,f'{path}: HTTP {code}, expected {expected}'
    if code in (401,403):data={}
    else:data=json.loads(raw) if raw.strip() else {}
    checks.append({'method':'POST' if body is not None else 'GET','path':path,'status':code})
    print(checks[-1],flush=True);return data
def main():
    sender=call('/auth/login',{'username':'demo','password':'demo123'})
    receiver=call('/auth/login',{'username':'receiver','password':'receiver123'})
    s=sender['accessToken'];r=receiver['accessToken']
    profile=call('/customers/'+sender['customerId'],token=s)
    other=call('/customers/'+receiver['customerId'],token=r)
    before=call('/wallet',token=s);receiver_before=call('/wallet',token=r)
    key=lambda:str(uuid.uuid4())
    deposit=call('/wallet/deposits',{'idempotencyKey':key(),'amount':20},s)
    payment=call('/payments',{'idempotencyKey':key(),'accountNumber':profile['accountNumber'],'merchant':'Postman ready data coffee','amount':2},s,(200,201))
    refundable=call('/payments',{'idempotencyKey':key(),'accountNumber':profile['accountNumber'],'merchant':'Postman ready data refund','amount':1},s,(200,201))
    refund=call('/payments/'+refundable['paymentId']+'/refunds',{'idempotencyKey':key()},s,(200,201))
    transfer=call('/transfers',{'idempotencyKey':key(),'senderAccount':profile['accountNumber'],'receiverAccount':other['accountNumber'],'amount':1,'currency':'INR'},s,(200,201))
    request=call('/money-requests',{'idempotencyKey':key(),'payerAccount':other['accountNumber'],'amount':1,'note':'Postman ready data lunch share'},s,(200,201))
    for row in [payment,transfer,refund]:assert row['status']=='COMPLETED',row['status']
    call('/payments/'+payment['paymentId'],token=s)
    call('/payments/'+refundable['paymentId']+'/refund',token=s)
    call('/transfers/'+transfer['transferId'],token=s)
    call('/wallet/activity?page=0&size=20',token=s)
    call('/money-requests?page=0&size=20',token=r)
    after=call('/wallet',token=s);receiver_after=call('/wallet',token=r)
    assert Decimal(str(after['balance']))==Decimal(str(before['balance']))+17
    assert Decimal(str(receiver_after['balance']))==Decimal(str(receiver_before['balance']))+1
    call('/actuator/health',expected=(401,403),origin='https://mock-bank-service-52916499838.us-central1.run.app')
    data={'live_sender_account':profile['accountNumber'],'live_receiver_account':other['accountNumber'],
          'live_sender_customer_id':sender['customerId'],'live_receiver_customer_id':receiver['customerId'],
          'live_payment_id':payment['paymentId'],'live_refund_payment_id':refundable['paymentId'],
          'live_transfer_id':transfer['transferId'],'live_request_id':request['id'],'live_deposit_id':deposit['id']}
    (root/'seed.json').write_text(json.dumps(data,indent=2))
    (root/'results.json').write_text(json.dumps({'status':'PASS','checks':checks,'senderBalance':after['balance'],'receiverBalance':receiver_after['balance'],'scope':'Actual curl calls; INR sandbox funds only; no JWTs saved'},indent=2))
    print(json.dumps({'status':'PASS','checks':len(checks),'records':data}),flush=True)
if __name__=='__main__':main()
