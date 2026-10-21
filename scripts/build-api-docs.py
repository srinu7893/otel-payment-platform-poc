"""Generate service OpenAPI contracts and curl/body documentation from controller sources.

This is a build-time catalog, not a claim that each service runs springdoc.
Private contracts are documentation-only in the browser; no route/IAM is added.
"""
import copy,json,re,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SERVICES=['api-gateway','auth-service','customer-service','payment-service','gateway-service','mock-bank-service','notification-service']
PUBLIC='https://payment-frontend-52916499838.us-central1.run.app'
PORTS=dict(zip(SERVICES,[8088,8079,8084,8080,8081,8082,8083]))
SEEDS={v['key']:v['value'] for v in json.loads((ROOT/'postman/Live-Data.postman_collection.json').read_text())['variable']}
BODY_EXAMPLES={}
for folder in json.loads((ROOT/'postman/Live-Data.postman_collection.json').read_text())['item']:
 for item in folder['item']:
  request=item['request']
  if request.get('body'):BODY_EXAMPLES.setdefault((request['method'],request['url'].split('.run.app',1)[1]),json.loads(request['body']['raw']))

def balanced(text,start,opening='(',closing=')'):
 depth=0;quoted=False;escape=False
 for index in range(start,len(text)):
  char=text[index]
  if quoted:
   if escape:escape=False
   elif char=='\\':escape=True
   elif char=='"':quoted=False
   continue
  if char=='"':quoted=True
  elif char==opening:depth+=1
  elif char==closing:
   depth-=1
   if depth==0:return text[start+1:index],index+1
 raise ValueError('Unbalanced source annotation')

def fields(text):
 result=[];begin=0;depth=0;angle=0;quoted=False;escape=False
 for i,char in enumerate(text):
  if quoted:
   if escape:escape=False
   elif char=='\\':escape=True
   elif char=='"':quoted=False
   continue
  if char=='"':quoted=True
  elif char=='(':depth+=1
  elif char==')':depth-=1
  elif char=='<':angle+=1
  elif char=='>':angle-=1
  elif char==',' and depth==0 and angle==0:result.append(text[begin:i]);begin=i+1
 result.append(text[begin:]);return result

def example(name,context=''):
 if name in ['idempotencyKey','refundId','paymentId']:return str(uuid.uuid5(uuid.NAMESPACE_URL,context+'/'+name))
 if name=='originalPaymentId':return SEEDS['live_payment_id']
 if name in ['accountNumber','senderAccount','account']:return 'ACC1001'
 if name in ['receiverAccount','payerAccount']:return 'ACC2001'
 if name in ['name','customerName']:return 'Demo Customer'
 if name=='email':return 'demo@example.com'
 if name=='username':return 'demo'
 if name=='password':return 'demo123' if 'login' in context else 'Postman-documentation-123!'
 if name in ['amount','balance']:return 1
 if name=='currency':return 'INR'
 if name=='merchant':return 'Swagger sandbox purchase'
 if name=='note':return 'Swagger sandbox request'
 if name=='active':return True
 return 'example'

def schema(java_type,controller,context,seen=()):
 java_type=java_type.strip();short=java_type.split('.')[-1]
 if java_type.startswith('ResponseEntity<'):return schema(java_type[15:-1],controller,context,seen)
 if java_type.startswith(('List<','Set<')):return {'type':'array','items':schema(java_type.split('<',1)[1][:-1],controller,context,seen)}
 if java_type.startswith('Page<'):return {'type':'object','properties':{'content':{'type':'array','items':schema(java_type[5:-1],controller,context,seen)},'number':{'type':'integer'},'size':{'type':'integer'},'totalElements':{'type':'integer'},'totalPages':{'type':'integer'}}}
 if short in ['String','Instant','LocalDateTime','OffsetDateTime','UUID']:
  return {'type':'string',**({'format':'uuid'} if short=='UUID' else {'format':'date-time'} if short in ['Instant','LocalDateTime','OffsetDateTime'] else {})}
 if short in ['int','Integer','long','Long']:return {'type':'integer'}
 if short in ['double','Double','BigDecimal','float']:return {'type':'number'}
 if short in ['boolean','Boolean']:return {'type':'boolean'}
 if short=='void':return {}
 if short in ['Object','Map'] or java_type.startswith('Map<') or short in seen:return {'type':'object'}
 candidates=[controller,*sorted((ROOT/context.split(':')[0]/'src/main/java').rglob('*.java'))]
 for file in candidates:
  text=file.read_text();enum=re.search(r'\benum\s+'+re.escape(short)+r'\s*\{([^}]+)',text)
  if enum:return {'type':'string','enum':re.findall(r'\b[A-Z][A-Z_]+\b',enum[1].split(';')[0])}
 for file in candidates:
  text=file.read_text();match=re.search(r'\brecord\s+'+re.escape(short)+r'\s*\(',text)
  if not match:continue
  params,_=balanced(text,match.end()-1);props={};required=[]
  for field in fields(params):
   cleaned=re.sub(r'@\w+(?:\([^)]*\))?','',field).strip();parts=cleaned.rsplit(None,1)
   if len(parts)!=2:raise ValueError('Cannot document record field: '+field)
   kind,name=parts;prop=schema(kind,file,context,(*seen,short))
   if prop.get('type') not in ['array','object']:
    sample=example(name,context)
    if prop.get('format')=='uuid':
     try:uuid.UUID(str(sample))
     except ValueError:sample=str(uuid.uuid5(uuid.NAMESPACE_URL,context+'/'+name))
    if prop.get('format')=='date-time':sample='2026-10-21T00:00:00Z'
    if prop.get('type') in ['number','integer'] and not isinstance(sample,(int,float)):sample=0
    if prop.get('type')=='boolean':sample=True
    if prop.get('enum') and sample not in prop['enum']:sample=prop['enum'][0]
    prop['example']=sample
   if '@NotNull' in field or '@NotBlank' in field:required.append(name)
   size=re.search(r'@Size\(([^)]*)\)',field)
   if size:
    for key,output in [('min','minLength'),('max','maxLength')]:
     m=re.search(key+r'\s*=\s*(\d+)',size[1])
     if m:prop[output]=int(m[1])
   minimum=re.search(r'@DecimalMin\((?:value\s*=\s*)?"([\d.]+)"',field)
   maximum=re.search(r'@DecimalMax\("([\d.]+)"',field)
   if minimum:prop['minimum']=float(minimum[1])
   if maximum:prop['maximum']=float(maximum[1])
   if '@Email' in field:prop['format']='email'
   pattern=re.search(r'@Pattern\(regexp\s*=\s*"([^"]+)"',field)
   if pattern:prop['pattern']=pattern[1]
   if '@Digits' in field and 'fraction=2' in field.replace(' ',''):prop['multipleOf']=.01
   if '/internal/onboarding/' in context and name=='accountNumber':prop['example']='ACC'+'A'*24
   props[name]=prop
  return {'type':'object','properties':props,**({'required':required} if required else {})}
 # Entity responses can contain fields not described by a Java record. Keep
 # an honest object contract rather than inventing response fields.
 return {'type':'object','description':java_type+' response; observed fields are in the execution report.'}

def normalize(route):
 result='';i=0
 while i<len(route):
  if route[i]=='{':
   content,end=balanced(route,i,'{','}');result+='{'+content.split(':')[0]+'}';i=end
  else:result+=route[i];i+=1
 return result

def inventory():
 result=[]
 for service in SERVICES:
  for file in sorted((ROOT/service/'src/main/java').rglob('*Controller.java')):
   text=file.read_text();head=text.split('class ',1)[0]
   base_match=re.search(r'@RequestMapping\s*\(\s*"([^"]*)"',head);base=base_match[1] if base_match else ''
   mappings=list(re.finditer(r'@(Get|Post|Put|Patch|Delete)Mapping\b',text))
   for index,match in enumerate(mappings):
    end=match.end();tail=text[end:];annotation=''
    first=re.search(r'\S',tail)
    if first and tail[first.start()]=='(':annotation,end=balanced(text,end+first.start())
    route_match=re.search(r'"([^"]*)"',annotation);suffix=route_match[1] if route_match else ''
    segment=text[end:mappings[index+1].start() if index+1<len(mappings) else len(text)]
    method=re.search(r'public\s+([\w.<>, ?]+)\s+(\w+)\s*\(',segment)
    if not method:raise ValueError('No handler method: '+str(file)+suffix)
    parameters,_=balanced(segment,method.end()-1)
    request=re.search(r'@RequestBody\s+([\w.<>,]+)\s+\w+',parameters)
    route=normalize(base+suffix)
    paths=[route.replace('{action}',action) for action in ['pay','decline','cancel']] if '{action}' in route else [route]
    for route in paths:
     public=service not in ['gateway-service','mock-bank-service'] and not route.startswith('/internal/')
     result.append({'service':service,'method':match[1].upper(),'path':route,'handler':method[2],'responseType':method[1].strip(),'parametersSource':parameters,'bodyType':request[1] if request else None,'source':file.relative_to(ROOT).as_posix(),'private':not public,'file':file,'segment':segment})
 result.append({'service':'frontend','method':'GET','path':'/health','handler':'health','responseType':'String','parametersSource':'','bodyType':None,'source':'frontend/cloud-run.conf.template','private':False,'file':ROOT/'frontend/cloud-run.conf.template','segment':''})
 return result

def authorization(row):
 p=row['path'];m=row['method']
 if row['private']:return 'Cloud Run invoker identity; trusted service/operator only'
 if row['service']=='frontend':return 'Anonymous frontend health check'
 if p.startswith('/api/v1/auth/') or p=='/api/v1/telemetry/rum':return 'Anonymous'+(' with allowed Origin and bounded body' if p.endswith('/rum') else '')
 if p.startswith('/api/v1/ops/'):return 'Bearer JWT: SUPPORT or ADMIN'
 if p.startswith('/api/v1/customers'):
  return 'Bearer JWT: ADMIN' if m!='GET' else 'Bearer JWT: owner, SUPPORT or ADMIN' if '{id}' in p else 'Bearer JWT: SUPPORT or ADMIN'
 if p.startswith(('/api/v1/wallet','/api/v1/money-requests')):return 'Bearer JWT: CUSTOMER; ownership/payer rules apply'
 return 'Bearer JWT: CUSTOMER, SUPPORT or ADMIN; operation-specific ownership/role rules apply'

def param_example(name,route):
 if name=='id' and '/customers/' in route:return 'demo-customer'
 if name=='id' and '/payments/' in route:return SEEDS['live_payment_id']
 if name=='id' and '/transfers/' in route:return SEEDS['live_transfer_id']
 if name=='id' and '/money-requests/' in route:return SEEDS['live_request_id']
 if name in ['account','accountNumber']:return 'ACC1001'
 if name=='number':return 'ACC'+'A'*24
 return str(uuid.uuid5(uuid.NAMESPACE_URL,route+'/'+name))

def operation(row):
 service=row['service'];route=row['path'];method=row['method'];file=row['file'];context=service+':'+method+route
 auth=authorization(row);body=schema(row['bodyType'],file,context) if row['bodyType'] else None
 if route=='/api/v1/telemetry/rum':body={'type':'object','required':['name','value'],'additionalProperties':False,'properties':{'name':{'type':'string','enum':['CLS','FCP','INP','LCP','TTFB','PAGE_VIEW','JS_ERROR']},'value':{'type':'number','minimum':0}},'description':'CLS: 0..5; web-vital milliseconds: 0..120000; PAGE_VIEW and JS_ERROR: exactly 1. Only name/value fields accepted.','example':{'name':'PAGE_VIEW','value':1}}
 if service=='gateway-service' and route.endswith('/deposits'):body={'type':'object','required':['idempotencyKey','amount'],'properties':{'idempotencyKey':{'type':'string','format':'uuid'},'amount':{'type':'number','minimum':.01,'maximum':100000}}}
 example_body=BODY_EXAMPLES.get((method,route))
 if body is not None and example_body is None:
  example_body=body.get('example') or {name:prop.get('example',example(name,context)) for name,prop in body.get('properties',{}).items()}
  if route.endswith('/auth/register'):
   example_body.update(username='swagger_sandbox_user',password='Swagger-sandbox-123!',name='Swagger Sandbox User',email='swagger_sandbox_user@example.com')
 params=[{'name':name,'in':'path','required':True,'schema':{'type':'string'},'example':param_example(name,route)} for name in re.findall(r'\{(\w+)\}',route)]
 if route.endswith('/telemetry/rum'):params.append({'name':'Origin','in':'header','required':True,'schema':{'type':'string','format':'uri'},'example':PUBLIC,'description':'Browser sends its current origin automatically. Curl must send this allowed frontend origin.'})
 for args,kind,name in re.findall(r'@RequestParam(?:\(([^)]*)\))?\s+([\w.]+)\s+(\w+)',row['parametersSource']):
  default=re.search(r'defaultValue\s*=\s*"([^"]*)"',args);prop=schema(kind,file,context)
  if default:prop['default']=int(default[1]) if prop.get('type')=='integer' else default[1]
  params.append({'name':name,'in':'query','required':'required = false' not in args and 'required=false' not in args and not default,'schema':prop})
 success='201' if ('HttpStatus.CREATED' in row['segment'] or '.status(201)' in row['segment']) else '204' if 'HttpStatus.NO_CONTENT' in row['segment'] or route.endswith('/telemetry/rum') else '200'
 responses={success:{'description':'Successful response; business outcome is in the returned status, if present.',**({'content':{'application/json':{'schema':schema(row['responseType'],file,context)}}} if success!='204' else {})}}
 for code,text in [('400','Invalid request or body'),('401','Missing/invalid application or Cloud Run credentials'),('403','Insufficient role/ownership/invoker permission'),('404','Record not found'),('405','HTTP method not supported'),('409','State or idempotency conflict'),('415','Unsupported media type'),('422','Business risk policy rejected the operation where applicable'),('500','Unexpected processing failure; use correlation/trace IDs'),('503','Dependency or provisioning unavailable')]:responses[code]={'description':text,'content':{'application/json':{'schema':{'$ref':'#/components/schemas/ApiError'}}}}
 anonymous=auth.startswith('Anonymous')
 security=[{'cloudRunIdentity':[]}] if row['private'] else [] if anonymous else [{'bearerAuth':[]}]
 result={'operationId':service.replace('-','_')+'_'+method.lower()+'_'+re.sub(r'\W+','_',route).strip('_'),'tags':[service],
  'summary':re.sub(r'([A-Z])',r' \1',row['handler']).capitalize()+' — '+route.split('/')[-1],
  'description':auth+'. '+('Internal-only contract, not routed by the public API gateway. Browser execution is disabled in the private catalog.' if row['private'] else 'Use the public frontend entry point. No Google identity token is needed by the browser/Postman client.')+' Source: '+row['source'],
  'security':security,'parameters':params,'responses':responses,'x-source':row['source'],'x-required-access':auth,'x-private':row['private']}
 if body is not None:result['requestBody']={'required':True,'content':{'application/json':{'schema':body,'example':example_body}}}
 if service=='frontend':result['responses']['200']['content']={'text/plain':{'schema':{'type':'string','example':'ok'}}}
 return result,example_body

def spec(name,rows,private=False):
 result={'openapi':'3.0.3','info':{'title':'Payment platform — '+name,'version':'1.1.0','description':'Generated build-time contracts from controller handlers. Private catalogs document service-only endpoints; they are not public routes. Request/response execution evidence is produced separately by scripts/run-api-tests.cmd.'},
  'servers':[{'url':f'http://127.0.0.1:{PORTS.get(name,8088)}','description':'Authenticated local proxy / native service; private contracts are not public gateway routes'}] if private else [{'url':'/','description':'Current frontend origin; routes through the API gateway'},{'url':PUBLIC,'description':'Deployed sandbox frontend'}],
  'paths':{},'components':{'schemas':{'ApiError':{'type':'object','properties':{'timestamp':{'type':'string','format':'date-time'},'code':{'type':'string'},'message':{'type':'string'},'path':{'type':'string'},'correlationId':{'type':'string'},'fieldErrors':{'type':'object','additionalProperties':{'type':'string'}}}}},'securitySchemes':{'bearerAuth':{'type':'http','scheme':'bearer','bearerFormat':'JWT','description':'Application JWT from POST /api/v1/auth/login. Swagger Sign in acquires it in memory.'},'cloudRunIdentity':{'type':'apiKey','in':'header','name':'X-Serverless-Authorization','description':'Bearer Google ID token with the selected private service canonical URL as audience; caller must have run.invoker. This is separate from the application JWT.'}}}}
 for row in rows:result['paths'].setdefault(row['path'],{})[row['method'].lower()]=operation(row)[0]
 result['components']['securitySchemes'].pop('bearerAuth' if private else 'cloudRunIdentity')
 return result

def main():
 rows=inventory();out=ROOT/'frontend/public/api-docs/specs';out.mkdir(parents=True,exist_ok=True)
 docs=ROOT/'docs/api';(docs/'bodies').mkdir(parents=True,exist_ok=True);manifest=[]
 public=[r for r in rows if not r['private']]
 catalogs=[('public',public,False)]+[(s,[r for r in rows if r['service']==s and not r['private']],False) for s in [*SERVICES,'frontend'] if any(r['service']==s and not r['private'] for r in rows)]+[(s+'-internal',[r for r in rows if r['service']==s and r['private']],True) for s in SERVICES if any(r['service']==s and r['private'] for r in rows)]
 for name,entries,private in catalogs:
  data=spec(name.removesuffix('-internal'),entries,private);(out/(name+'.json')).write_text(json.dumps(data,indent=2)+'\n')
  manifest.append({'id':name,'name':name+(' (private, documentation only)' if private else ''),'url':'/api-docs/specs/'+name+'.json','private':private,'operations':len(entries)})
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
 # JSON is a valid YAML 1.2 document; preserve the gateway's existing public URL.
 (ROOT/'api-gateway/src/main/resources/static/openapi.yaml').write_text(json.dumps(spec('public',[r for r in public if r['service']!='frontend']),indent=2)+'\n')
 lines=['# API endpoint and curl catalog','', 'Generated from Java controller handlers by `python scripts/build-api-docs.py`. Schema response objects that are not records are intentionally generic; actual observed JSON is in the automated execution report. This catalog is documentation coverage, not a claim that every operation has been executed.','',f'Public entry: `{PUBLIC}`. Public Swagger UI: `/api-docs/`. Frontend is a UI/reverse proxy, not a separate business API service.','', 'Public calls use application JWTs. Use Swagger Sign in or the automatic runner; no token copying is needed there. Private direct calls additionally need a Google ID token with canonical service audience and invoker permission. Their browser Try it out is disabled. Internal mutation endpoints must not be treated as end-user payment entry points.','', '| Service | Method | Path | Required access |','| --- | --- | --- | --- |']
 for row in rows:lines.append(f'| {row["service"]} | {row["method"]} | `{row["path"]}` | {authorization(row)} |')
 index=[]
 for row in rows:
  op,body=operation(row);slug=op['operationId'];actual=row['path']
  for param in op['parameters']:
   if param['in']=='path':actual=actual.replace('{'+param['name']+'}',str(param['example']))
  bodyfile='docs/api/bodies/'+slug+'.json'
  if body is not None:(ROOT/bodyfile).write_text(json.dumps(body,indent=2)+'\n')
  lines+=['',f'## {row["service"]}: {row["method"]} {row["path"]}','',authorization(row)+'.','',f'Source: `{row["source"]}`.','']
  if body is not None:lines+=['Filled request body:', '```json',json.dumps(body,indent=2),'```','']
  if row['private']:
   lines+=['Cloud Shell / bash direct service curl (requires your existing invoker and token-impersonation permissions):','```bash',f'SERVICE_URL=$(gcloud run services describe {row["service"]} --project project-c9bd3d0e-266f-47bf-852 --region us-central1 --format="value(status.url)")','CLOUD_ID_TOKEN=$(gcloud auth print-identity-token --audiences="$SERVICE_URL" --impersonate-service-account=otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com)',f'curl --request {row["method"]} "$SERVICE_URL{actual}" \\', '  --header "X-Serverless-Authorization: Bearer $CLOUD_ID_TOKEN"'+(' \\' if body is not None else '')]
   if body is not None:lines+=['  --header "Content-Type: application/json" \\',f'  --data-binary "@{bodyfile}"']
   lines+=['```','']
  else:
   lines+=['CMD curl (PAYMENT_JWT is a fresh application JWT; the automated runner handles it without pasting):','```cmd',f'curl.exe --request {row["method"]} "{PUBLIC}{actual}"'+(' ^' if body is not None or op['security'] else '')]
   if op['security']:lines.append('  --header "Authorization: Bearer %PAYMENT_JWT%"'+(' ^' if body is not None else ''))
   if row['path'].endswith('/telemetry/rum'):lines.append('  --header "Origin: '+PUBLIC+'" ^')
   if body is not None:lines+=['  --header "Content-Type: application/json" ^',f'  --data-binary "@{bodyfile}"']
   lines+=['```','']
  index.append({k:v for k,v in row.items() if k not in ['file','segment','parametersSource']})
 (docs/'ENDPOINTS_AND_CURL.md').write_text('\n'.join(lines).rstrip()+'\n')
 (docs/'endpoint-inventory.json').write_text(json.dumps(index,indent=2)+'\n')
 print(json.dumps({'servicesIncludingFrontend':len(SERVICES)+1,'documentedOperations':len(rows),'publicOperations':len(public),'catalogs':len(manifest)}))
if __name__=='__main__':main()
