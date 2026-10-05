#!/usr/bin/env python3
"""Render the service/dashboard review without mixing application E2E evidence."""
from pathlib import Path
from html import escape
import os, json, subprocess, base64
from guide_markdown import render_markdown
root=Path(__file__).resolve().parents[1]
source=os.environ.get('POC_DASHBOARD_REVISION') or subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
run=os.environ.get('POC_DASHBOARD_RUN') or os.environ.get('GITHUB_RUN_ID','not run in this environment')
body=render_markdown((root/'docs/DASHBOARDS_AND_DEPLOYMENT.md').read_text())
# Local URLs are deliberately links to the user's recreated stack.
import re
body=re.sub(r'(?<![\w"/=])(http://localhost:3001[^\s<]*)',lambda m:'<a href="'+m[1]+'">'+m[1]+'</a>',body)
for n in range(1,12):body=body.replace('<h4>Step '+str(n)+' ', '<h4 id="step-'+str(n)+'">Step '+str(n)+' ')
checks=root/'artifacts/dashboard-review.json';browser=root/'artifacts/dashboard-browser.json'
evidence='<h2>Separate dashboard evidence</h2><p>Dashboard source: <code>'+escape(source)+'</code>; dashboard run: '+escape(run)+'. Application E2E results retain the source/run recorded in the review.</p>'
if checks.exists():
 rows=json.loads(checks.read_text());evidence+='<p>'+str(len(rows))+' dashboard provisioning/query/coverage checks recorded.</p>'
if browser.exists():evidence+='<pre>'+escape(browser.read_text())+'</pre>'
else:evidence+='<p>Browser rendering has not been verified by this document generation.</p>'
if os.environ.get('POC_EMBED_SCREENSHOTS','false').lower()=='true':
 for uid,label in [('platform-overview','Consolidated platform metrics'),('service-payment-service','Payment service metrics and raw logs'),('service-frontend','Frontend coverage and its limitations')]:
  image=root/'artifacts/service-dashboards'/f'{uid}.png'
  if image.exists():evidence+='<details><summary>'+escape(label)+'</summary><img style="max-width:100%;height:auto" alt="'+escape(label)+'" src="data:image/png;base64,'+base64.b64encode(image.read_bytes()).decode()+'"></details>'
css='''body{margin:0;background:#f3f6fa;color:#173044;font:17px/1.65 system-ui,sans-serif}header{background:#12354c;color:white;padding:36px max(24px,calc((100vw - 1100px)/2))}main{max-width:1100px;margin:auto;padding:28px;background:white}h1{line-height:1.2}h2,h3,h4{line-height:1.3;margin-top:30px}a{color:#075e91}table{border-collapse:collapse;width:100%;font-size:15px}th,td{padding:12px;text-align:left;vertical-align:top;border:1px solid #dbe4eb;overflow-wrap:anywhere}th{background:#edf3f7}.scroll{overflow-x:auto}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#edf3f7;padding:20px;font-size:14px}nav{padding:20px;background:#edf3f7}nav a{display:inline-block;margin:6px 16px 6px 0}li{margin:8px 0}code{overflow-wrap:anywhere}@media(max-width:700px){main{padding:15px}table{font-size:13px}td,th{padding:7px}}'''
nav='<nav aria-label="Deployment steps">'+''.join('<a href="#step-'+str(n)+'">Step '+str(n)+'</a>' for n in range(1,12))+'</nav>'
html='<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>OTel dashboards, review and Cloud Run next steps</title><style>'+css+'</style></head><body><header><h1>Service dashboards and the path to Cloud Run</h1><p>What to inspect, what remains, and the deployment sequence.</p></header><main>'+nav+evidence+body+'</main></body></html>'
out=root/'docs/DASHBOARDS_AND_DEPLOYMENT.html';out.write_text(html);print(str(out))
