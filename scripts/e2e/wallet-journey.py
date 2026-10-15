#!/usr/bin/env python3
"""Exercise the demo wallet through the public edge; never print access tokens.

This intentionally adds INR 10 of mock funds and makes small demo transactions.
Use a sandbox deployment, not a real payment integration.
"""
import argparse
import json
import time
import uuid
from decimal import Decimal
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--url',required=True,help='Frontend origin or API gateway origin')
parser.add_argument('--out',default='artifacts/wallet-journey.json')
args=parser.parse_args()
base=args.url.rstrip('/')+'/api/v1'
checks=[]

def call(path,data=None,token=None,expected=(200,201)):
    correlation='wallet-e2e-'+str(uuid.uuid4())
    req=Request(base+path,data=json.dumps(data).encode() if data is not None else None,
        headers={'Content-Type':'application/json','X-Correlation-Id':correlation,**({'Authorization':'Bearer '+token} if token else {})})
    try:
        with urlopen(req,timeout=90) as response:status=response.status;raw=response.read()
    except HTTPError as error:status=error.code;raw=error.read()
    try:body=json.loads(raw)
    except ValueError:body={}
    assert status in expected,f'{path}: HTTP {status}, code={body.get("code")}, correlation={correlation}'
    return body

def record(name):checks.append(name);print('PASS:',name,flush=True)
def login(user,password):return call('/auth/login',{'username':user,'password':password})['accessToken']
def balance(token):return Decimal(str(call('/wallet',token=token)['balance']))
def key():return str(uuid.uuid4())
def settled(kind,result,token):
    for _ in range(30):
        if result['status'] not in ('PENDING','PROCESSING','RECONCILIATION_REQUIRED'):return result
        time.sleep(2);result=call('/'+kind+'/'+result['paymentId' if kind=='payments' else 'transferId'],token=token)
    raise AssertionError('Transaction did not settle: '+result['status'])

try:
    demo=login('demo','demo123');receiver=login('receiver','receiver123')
    own=call('/wallet',token=demo);other=call('/wallet',token=receiver)
    assert own['accountNumber']!=other['accountNumber'];record('Both customers can see their own balances')
    call('/wallet',expected=(401,));record('Anonymous wallet access denied')
    before=balance(demo);receiver_before=balance(receiver)
    body={'idempotencyKey':key(),'amount':10}
    first=call('/wallet/deposits',body,demo);second=call('/wallet/deposits',body,demo)
    assert first['id']==second['id'] and balance(demo)==before+Decimal('10');record('Deposit retry credits only once')
    call('/wallet/deposits',{'idempotencyKey':key(),'amount':-1},demo,expected=(400,))
    call('/wallet/deposits',{'idempotencyKey':key(),'amount':0.001},demo,expected=(400,));record('Invalid money amounts rejected')
    recipient=call('/wallet/recipients/'+other['accountNumber'],token=demo)
    assert recipient['customerName'] and 'balance' not in recipient;record('Recipient lookup returns name without balance')
    call('/wallet/recipients/UNKNOWN999',token=demo,expected=(404,));record('Unknown account rejected')
    payment=settled('payments',call('/payments',{'idempotencyKey':key(),'accountNumber':own['accountNumber'],'merchant':'Wallet acceptance store','amount':1},demo),demo)
    assert payment['status']=='COMPLETED';assert balance(demo)==before+Decimal('9');record('Merchant payment debits balance')
    call('/payments/'+payment['paymentId'],token=receiver,expected=(403,));record('Other customer cannot view payment details')
    refund_key=key()
    refund=call('/payments/'+payment['paymentId']+'/refunds',{'idempotencyKey':refund_key},demo)
    for _ in range(30):
        if refund['status'] not in ('PROCESSING','PENDING','RECONCILIATION_REQUIRED'):break
        time.sleep(2);refund=call('/payments/'+payment['paymentId']+'/refund',token=demo)
    assert refund['status']=='COMPLETED'
    call('/payments/'+payment['paymentId']+'/refunds',{'idempotencyKey':refund_key},demo)
    assert balance(demo)==before+Decimal('10');record('Refund restores funds exactly once')
    transfer_body={'idempotencyKey':key(),'senderAccount':own['accountNumber'],'receiverAccount':other['accountNumber'],'amount':2,'currency':'INR'}
    transfer=settled('transfers',call('/transfers',transfer_body,demo),demo)
    assert transfer['status']=='COMPLETED'
    replay=call('/transfers',transfer_body,demo);assert replay['transferId']==transfer['transferId']
    assert balance(demo)==before+Decimal('8') and balance(receiver)==receiver_before+Decimal('2');record('Transfer and replay debit/credit both accounts once')
    call('/transfers',{'idempotencyKey':key(),'senderAccount':other['accountNumber'],'receiverAccount':own['accountNumber'],'amount':1,'currency':'INR'},demo,expected=(403,));record('Sender account spoofing rejected')
    request=call('/money-requests',{'idempotencyKey':key(),'payerAccount':other['accountNumber'],'amount':1,'note':'Acceptance request'},demo)
    assert request['status']=='OPEN' and balance(demo)==before+Decimal('8');record('Money request does not move money')
    incoming=call('/money-requests',token=receiver)['content'];assert any(r['id']==request['id'] for r in incoming)
    call('/money-requests/'+request['id']+'/pay',{},demo,expected=(403,));record('Requester cannot approve their own request')
    paid=call('/money-requests/'+request['id']+'/pay',{},receiver)
    for _ in range(30):
        if paid['status']!='PROCESSING':break
        time.sleep(2);paid=call('/money-requests/'+request['id']+'/pay',{},receiver)
    assert paid['status']=='PAID'
    call('/money-requests/'+request['id']+'/pay',{},receiver)
    assert balance(demo)==before+Decimal('9') and balance(receiver)==receiver_before+Decimal('1');record('Payer approval transfers once, including retry')
    for action in ('decline','cancel'):
        r=call('/money-requests',{'idempotencyKey':key(),'payerAccount':other['accountNumber'],'amount':1,'note':action},demo)
        result=call('/money-requests/'+r['id']+'/'+action,{},receiver if action=='decline' else demo)
        assert result['status']==('DECLINED' if action=='decline' else 'CANCELLED')
        assert call('/money-requests/'+r['id']+'/pay',{},receiver)['status']==result['status']
    record('Declined and cancelled requests cannot be paid')
    assert balance(demo)==before+Decimal('9');record('Decline/cancel never debit funds')
    activity=call('/wallet/activity',token=demo)
    assert {'PAYMENT','REFUND','DEPOSIT','TRANSFER'} <= {a['type'] for a in activity}
    receiver_activity=call('/wallet/activity',token=receiver)
    assert any(a['direction']=='CREDIT' and a['type']=='TRANSFER' for a in receiver_activity);record('Bank activity shows all transaction types and incoming credits')
    assert call('/payments',token=demo)['totalElements']>=1 and call('/transfers',token=demo)['totalElements']>=1
    call('/wallet/activity?page=1&size=20',token=demo);record('History, details and pagination accessible')
    for _ in range(20):
        notifications=call('/notifications',token=demo)['content']
        if any(n.get('paymentId')==payment['paymentId'] for n in notifications):break
        time.sleep(3)
    assert any(n.get('paymentId')==payment['paymentId'] for n in notifications);record('Asynchronous payment notification delivered')
    result={'status':'PASS','url':args.url,'checks':checks}
except Exception as error:
    result={'status':'FAIL','url':args.url,'checks':checks,'error':str(error)}
    raise
finally:
    output=Path(args.out);output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(result,indent=2),encoding='utf-8')
