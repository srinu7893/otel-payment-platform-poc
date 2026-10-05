#!/usr/bin/env python3
"""Send a labelled synthetic log to the disposable Collector; verify stored body redaction."""
import json,time,uuid,urllib.request,urllib.parse
from pathlib import Path
marker='privacy-probe-'+uuid.uuid4().hex
body=marker+' password=FAKE_PASSWORD access_token=FAKE_ACCESS api_key=FAKE_KEY Authorization: Bearer FAKE.BEARER.TOKEN'
record={'resourceLogs':[{'resource':{'attributes':[{'key':'service.name','value':{'stringValue':'privacy-probe'}}]},
                        'scopeLogs':[{'scope':{'name':'poc.privacy.acceptance'},'logRecords':[{'timeUnixNano':str(time.time_ns()),'severityNumber':9,'severityText':'INFO','body':{'stringValue':body}}]}]}]}
request=urllib.request.Request('http://localhost:4318/v1/logs',data=json.dumps(record).encode(),headers={'Content-Type':'application/json'})
with urllib.request.urlopen(request,timeout=10) as response: assert response.status==200
query='{service_name="privacy-probe"} |= "'+marker+'"'
for attempt in range(30):
    url='http://localhost:3100/loki/api/v1/query_range?'+urllib.parse.urlencode({'query':query,'start':str(time.time_ns()-120_000_000_000),'end':str(time.time_ns()),'limit':100})
    with urllib.request.urlopen(url,timeout=10) as response: data=json.load(response)
    lines=[row[1] for stream in data['data']['result'] for row in stream['values']]
    if lines:
        assert all('FAKE_' not in line and 'FAKE.BEARER.TOKEN' not in line for line in lines), 'Sensitive probe marker survived processing'
        assert any(line.count('[REDACTED]')==4 for line in lines), 'Expected four redacted fields'
        Path('artifacts/privacy-results.json').write_text(json.dumps({'status':'PASS','scope':'OTLP log body only; not console output or complete PII policy','query':query,'storedLines':lines},indent=2))
        print('PASS: synthetic password, access token, API key and Bearer body values redacted in Loki')
        break
    time.sleep(2)
else: raise AssertionError('Redacted probe did not reach Loki')
