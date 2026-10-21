"""Build a self-contained, filled Postman collection from verified curl seed IDs."""
import json,re,uuid
from pathlib import Path
BASE='https://payment-frontend-52916499838.us-central1.run.app/api/v1'
path=Path('postman/Live-Data.postman_collection.json')
old=json.loads(path.read_text())
seed=json.loads(Path('artifacts/postman-live-curl/seed.json').read_text())
values={**seed,'live_latest_refund_payment_id':seed['live_refund_payment_id'],'gcp_project_id':'project-c9bd3d0e-266f-47bf-852','otel_require_trace':'true'}
routes={};folders=[]
def request(name,method,route,body=None,expected=200,tests=(),save=None):
    routes[name]=route
    actual=re.sub(r'\{\{(\w+)\}\}',lambda m:values[m[1]],route)
    headers=[{'key':'X-Correlation-Id','value':'{{$guid}}'}]
    req={'method':method,'url':BASE+actual,'header':headers,'auth':{'type':'noauth'},
         'description':'Ready to Send. Full deployed URL and body are filled. No environment required. The collection obtains a fresh sandbox JWT automatically; private services remain private.'}
    if body is not None:
        headers.append({'key':'Content-Type','value':'application/json'})
        req['body']={'mode':'raw','raw':json.dumps(body,indent=2),'options':{'raw':{'language':'json'}}}
    code=[f'pm.test("HTTP {expected}",()=>pm.response.to.have.status({expected}));',*tests]
    if save:code.append(f'if(pm.response.code==={expected}) pm.collectionVariables.set({json.dumps(save[0])},pm.response.json()[{json.dumps(save[1])}]);')
    return {'name':name,'request':req,'event':[{'listen':'test','script':{'type':'text/javascript','exec':code}}]}
def key():return str(uuid.uuid4())
ready_user='postman_ready_'+uuid.uuid4().hex[:10]
ready_password='Postman-ready-'+uuid.uuid4().hex[:16]+'!9'
folders.append({'name':'00 - Login and registration - bodies already filled','item':[
    request('Login existing demo customer','POST','/auth/login',{'username':'demo','password':'demo123'},tests=['pm.test("Login returns a session",()=>pm.expect(pm.response.json().accessToken).to.be.a("string"));']),
    request('Login existing receiver','POST','/auth/login',{'username':'receiver','password':'receiver123'}),
    request('Register a new sandbox customer and bank account','POST','/auth/register',{'username':ready_user,'password':ready_password,'name':'Postman Ready Customer','email':ready_user+'@example.com'}),
    request('Login the registered customer','POST','/auth/login',{'username':ready_user,'password':ready_password})]})
folders.append({'name':'01 - Fetch existing data immediately','item':[
    request('My existing profile','GET','/customers/{{live_sender_customer_id}}'),
    request('My current balance','GET','/wallet'),
    request('My real bank activity','GET','/wallet/activity?page=0&size=20',tests=['pm.test("Persisted activity exists",()=>pm.expect(pm.response.json().length).to.be.above(0));']),
    request('My payment history','GET','/payments?page=0&size=20'),
    request('Retrieve latest payment by ID','GET','/payments/{{live_payment_id}}',tests=['pm.test("Correct persisted payment",()=>pm.expect(pm.response.json().paymentId).to.eql(pm.collectionVariables.get("live_payment_id")));']),
    request('Retrieve seeded refund','GET','/payments/{{live_refund_payment_id}}/refund'),
    request('My outgoing transfers','GET','/transfers?page=0&size=20'),
    request('Retrieve latest transfer by ID','GET','/transfers/{{live_transfer_id}}',tests=['pm.test("Correct persisted transfer",()=>pm.expect(pm.response.json().transferId).to.eql(pm.collectionVariables.get("live_transfer_id")));']),
    request('My money requests','GET','/money-requests?page=0&size=20'),
    request('My notifications','GET','/notifications?page=0&size=20'),
    request('Receiver: current balance','GET','/wallet'),
    request('Lookup receiver account','GET','/wallet/recipients/{{live_receiver_account}}')]})
folders.append({'name':'02 - Create and retrieve new transactions','item':[
    request('Add INR 10 demo money','POST','/wallet/deposits',{'idempotencyKey':key(),'amount':10},save=('live_deposit_id','id')),
    request('Receiver: add INR 10 demo money','POST','/wallet/deposits',{'idempotencyKey':key(),'amount':10}),
    request('Create INR 2 merchant payment','POST','/payments',{'idempotencyKey':key(),'accountNumber':seed['live_sender_account'],'merchant':'Postman manual coffee purchase','amount':2},201,save=('live_payment_id','paymentId')),
    request('Fetch the payment just created','GET','/payments/{{live_payment_id}}'),
    request('Create INR 1 transfer to receiver','POST','/transfers',{'idempotencyKey':key(),'senderAccount':seed['live_sender_account'],'receiverAccount':seed['live_receiver_account'],'amount':1,'currency':'INR'},201,tests=['pm.test("Transfer completed",()=>pm.expect(pm.response.json().status).to.eql("COMPLETED"));'],save=('live_transfer_id','transferId')),
    request('Fetch the transfer just created','GET','/transfers/{{live_transfer_id}}'),
    request('Create INR 1 money request','POST','/money-requests',{'idempotencyKey':key(),'payerAccount':seed['live_receiver_account'],'amount':1,'note':'Postman manual shared coffee'},save=('live_request_id','id')),
    request('Receiver: pay latest money request','POST','/money-requests/{{live_request_id}}/pay',{},tests=['pm.test("Request paid",()=>pm.expect(pm.response.json().status).to.eql("PAID"));']),
    request('Refund latest merchant payment','POST','/payments/{{live_payment_id}}/refunds',{'idempotencyKey':key()},201,save=('live_latest_refund_payment_id','paymentId')),
    request('Fetch refund just created','GET','/payments/{{live_latest_refund_payment_id}}/refund'),
    request('Fetch updated balance','GET','/wallet'),
    request('Fetch updated activity','GET','/wallet/activity?page=0&size=20'),
    request('Fetch updated payment history','GET','/payments?page=0&size=20'),
    request('Fetch updated transfers','GET','/transfers?page=0&size=20'),
    request('Fetch updated money requests','GET','/money-requests?page=0&size=20')]})
pre=r'''// This collection intentionally ignores environments, including a stale selected environment.
const BASE=__BASE__;
const routes=__ROUTES__;
const actors={sender:{username:'demo',password:'demo123',name:'Demo User',email:'demo@example.com'},receiver:{username:'receiver',password:'receiver123',name:'Receiver User',email:'receiver@example.com'}};
const requestedTrace=pm.variables.replaceIn('{{$guid}}').replace(/-/g,'');
pm.variables.set('otel_requested_trace_id',requestedTrace);
pm.request.headers.upsert({key:'traceparent',value:'00-'+requestedTrace+'-'+pm.variables.replaceIn('{{$guid}}').replace(/-/g,'').slice(0,16)+'-01'});
function stop(message){if(pm.execution && pm.execution.skipRequest)pm.execution.skipRequest();throw new Error(message);}
function call(path,body,token,done,attempt=0){
 const headers={'Content-Type':'application/json'};if(token)headers.Authorization='Bearer '+token;
 pm.sendRequest({url:BASE+path,method:body===null?'GET':'POST',header:headers,...(body===null?{}:{body:{mode:'raw',raw:JSON.stringify(body)}})},(error,response)=>{
   // Only reads and login are retried. Registration/money operations are never blindly retried.
   if((body===null || path==='/auth/login') && (error || [429,502,503,504].includes(response.code)) && attempt<2){setTimeout(()=>call(path,body,token,done,attempt+1),1000);return;}
   if(error)return stop('Unable to reach the deployed frontend. Check Postman Console/TLS/network; the business request was skipped.');
   done(response);
 });
}
function ensure(who,done){
 const actor=actors[who];
 function profile(session){
   call('/customers/'+session.customerId,null,session.accessToken,response=>{
     if(response.code!==200)return stop('Customer profile unavailable (HTTP '+response.code+'). Business request skipped.');
     const data=response.json();pm.collectionVariables.set('live_'+who+'_customer_id',session.customerId);pm.collectionVariables.set('live_'+who+'_account',data.accountNumber);done(session.accessToken);
   });
 }
 call('/auth/login',{username:actor.username,password:actor.password},null,response=>{
   if(response.code===200)return profile(response.json());
   if(response.code!==401)return stop('Sandbox login failed (HTTP '+response.code+'). Business request skipped.');
   // Fresh database: create this sandbox customer/account, then use its actual assigned account.
   call('/auth/register',actor,null,created=>{
     if(created.code!==200)return stop('Sandbox account setup returned HTTP '+created.code+'. Retry the same request after checking the response; no money request was sent.');
     call('/auth/login',{username:actor.username,password:actor.password},null,logged=>{if(logged.code!==200)return stop('New sandbox account login failed.');profile(logged.json());});
   });
 });
}
const path=routes[pm.info.requestName];
if(path.startsWith('/auth/')){pm.request.url.update(BASE+path);}
else ensure('sender',sender=>ensure('receiver',receiver=>{
 const route=path.replace(/{{(w+)}}/g,(_,key)=>{const value=pm.collectionVariables.get(key);if(!value)return stop('Missing saved record '+key+'. Run the create requests first.');return encodeURIComponent(value);});
 pm.request.url.update(BASE+route);
 pm.request.headers.upsert({key:'Authorization',value:'Bearer '+(pm.info.requestName.startsWith('Receiver:')?receiver:sender)});
 if(pm.request.body && pm.request.body.raw){
   const body=JSON.parse(pm.request.body.raw);
   if('accountNumber' in body)body.accountNumber=pm.collectionVariables.get('live_sender_account');
   if('senderAccount' in body)body.senderAccount=pm.collectionVariables.get('live_sender_account');
   if('receiverAccount' in body)body.receiverAccount=pm.collectionVariables.get('live_receiver_account');
   if('payerAccount' in body)body.payerAccount=pm.collectionVariables.get('live_receiver_account');
   pm.request.body.update(JSON.stringify(body,null,2));
 }
}));'''.replace('__BASE__',json.dumps(BASE)).replace('__ROUTES__',json.dumps(routes))
diagnostics=next(e for e in old['event'] if e['listen']=='test')
diagnostics=json.loads(json.dumps(diagnostics).replace('pm.environment','pm.collectionVariables'))
info=old['info'];info['name']='Payment Platform - Live Data - Ready to Send (No Environment)'
info['description']='Import ONLY this collection. Full public frontend URLs, existing customer accounts, persisted transaction IDs and all JSON bodies are filled. No environment or copied JWT required. Each business request logs into sandbox accounts automatically. Run all three folders in order for registration/create/fetch testing; existing reads can be sent individually. All money is simulated INR.'
result={'info':info,'variable':[{'key':k,'value':v,'type':'string'} for k,v in values.items()],
        'auth':{'type':'noauth'},'event':[{'listen':'prerequest','script':{'type':'text/javascript','exec':pre.splitlines()}},diagnostics],'item':folders}
text=json.dumps(result,indent=2)+'\n'
assert 'pm.environment' not in text
for folder in folders:
 for item in folder['item']:
  assert item['request']['url'].startswith(BASE) and '{{' not in item['request']['url']
  assert '{{' not in item['request'].get('body',{}).get('raw','')
path.write_text(text,encoding='utf-8')
print('Built',sum(len(f['item']) for f in folders),'filled requests; no environment dependency or stored JWT')
