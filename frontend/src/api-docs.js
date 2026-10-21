import SwaggerUI from 'swagger-ui-dist/swagger-ui-bundle.js';
import 'swagger-ui-dist/swagger-ui.css';
import './api-docs.css';

const service=document.querySelector('#service'),status=document.querySelector('#auth-status');
let ui,token='',catalog;
function authorize(value){token=value;if(ui && token)ui.preauthorizeApiKey('bearerAuth',token);}
function selected(){return catalog.find(entry=>entry.id===service.value);}
function render(){
  const entry=selected();
  document.querySelector('#signin').hidden=entry.private;
  document.querySelector('#catalog-status').textContent=entry.private
    ? `${entry.operations} internal operations. Documentation only: no public gateway route exists. Use the documented authenticated Cloud Shell/local proxy curl recipe. Browser execution is disabled.`
    : `${entry.operations} operations through this frontend origin. Expand an operation for body fields, roles, responses and curl. Idempotency keys replay unchanged money requests.`;
  ui=SwaggerUI({url:entry.url,dom_id:'#swagger-ui',deepLinking:true,filter:true,displayRequestDuration:true,
    persistAuthorization:false,validatorUrl:null,supportedSubmitMethods:entry.private?[]:['get','post','put','patch','delete'],
    requestInterceptor(request){
      const url=new URL(request.url,location.origin);
      if(url.origin!==location.origin)throw new Error('Use the current frontend origin. Tokens are never forwarded to another host.');
      if(entry.private && !url.pathname.startsWith('/api-docs/specs/'))throw new Error('Internal catalog is documentation only.');
      request.credentials='omit';return request;
    },
    responseInterceptor(response){
      const url=new URL(response.url||'/',location.origin);
      if(url.origin===location.origin && ['/api/v1/auth/login','/api/v1/auth/register'].includes(url.pathname)){
        let body=response.obj;try{if(!body)body=JSON.parse(response.text||response.data||'{}');}catch{/* Not a login JSON response. */}
        const value=body?.accessToken||body?.session?.accessToken;
        if(response.status===200 && typeof value==='string'){authorize(value);status.textContent='Signed in for API requests. Session exists only in this page memory.';}
      }
      return response;
    },onComplete(){if(token && !entry.private)authorize(token);}
  });
}
document.querySelector('#signin').addEventListener('submit',async event=>{
  event.preventDefault();const button=event.currentTarget.querySelector('button');button.disabled=true;
  try{
    const response=await fetch('/api/v1/auth/login',{method:'POST',credentials:'omit',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:document.querySelector('#username').value,password:document.querySelector('#password').value})});
    if(!response.ok)throw new Error('Login returned HTTP '+response.status);
    const session=await response.json();authorize(session.accessToken);
    status.textContent='Signed in. Roles: '+session.roles.join(', ')+'. Tokens stay in page memory; reload or Clear session to discard them.';
  }catch(error){token='';ui.authActions.logout(['bearerAuth']);status.textContent=error.message;}
  finally{button.disabled=false;}
});
document.querySelector('#clear-token').addEventListener('click',()=>{token='';ui.authActions.logout(['bearerAuth']);status.textContent='Session cleared.';});
async function initialize(){try{
  const response=await fetch('/api-docs/specs/manifest.json',{credentials:'omit'});if(!response.ok)throw new Error('Unable to load API catalog');catalog=await response.json();
  for(const entry of catalog){const option=document.createElement('option');option.value=entry.id;option.textContent=entry.name;service.append(option);}
  const requested=new URLSearchParams(location.search).get('service');if(catalog.some(e=>e.id===requested))service.value=requested;
  service.addEventListener('change',render);render();
}catch(error){status.textContent=error.message;}}
initialize();
