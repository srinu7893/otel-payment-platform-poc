"""Selenium verifies production web-vital ingestion; no login or money changes."""
import argparse,json,os,sys,time
from pathlib import Path
sys.path.insert(0,str(Path('artifacts/selenium-deps').resolve()))
os.environ['SE_CACHE_PATH']=str(Path('artifacts/selenium-cache').resolve())
from selenium import webdriver
from selenium.webdriver.edge.service import Service
from selenium.webdriver.support.ui import WebDriverWait

def main():
    p=argparse.ArgumentParser();p.add_argument('--url',default='https://payment-frontend-52916499838.us-central1.run.app');a=p.parse_args()
    options=webdriver.EdgeOptions();options.add_argument('--headless=new');options.add_argument('--window-size=1200,900')
    options.set_capability('ms:loggingPrefs',{'performance':'ALL'})
    class LocalService(Service):
        @property
        def service_url(self):return f'http://127.0.0.1:{self.port}'
    cached=list(Path('artifacts/selenium-cache/msedgedriver/win64').glob('*/msedgedriver.exe'))
    driver=webdriver.Edge(options=options,service=LocalService(str(cached[-1].resolve())) if cached else LocalService())
    records={};checks=[]
    try:
        driver.set_page_load_timeout(90);driver.get(a.url)
        WebDriverWait(driver,30).until(lambda d:d.execute_script('return window.__pocTelemetryStarted === true'))
        # Real browser error lifecycle event; text must never appear in the payload.
        driver.execute_script('window.dispatchEvent(new ErrorEvent("error", {message:"SECRET-MUST-NOT-LEAVE-PAGE"}));')
        deadline=time.monotonic()+15
        while time.monotonic()<deadline:
            for item in driver.get_log('performance'):
                message=json.loads(item['message'])['message'];method=message['method'];params=message.get('params',{})
                if method=='Network.requestWillBeSent' and '/api/v1/telemetry/rum' in params.get('request',{}).get('url',''):
                    request=params['request'];body=json.loads(request.get('postData','{}'))
                    assert set(body)=={'name','value'},'Unexpected RUM fields'
                    assert 'SECRET' not in json.dumps(body)
                    assert not any(k.lower() in {'authorization','cookie'} for k in request.get('headers',{}))
                    records[params['requestId']]={'name':body['name'],'status':None}
                if method=='Network.responseReceived' and params.get('requestId') in records:
                    records[params['requestId']]['status']=params['response']['status']
            names={r['name'] for r in records.values() if r['status']==204}
            if {'PAGE_VIEW','JS_ERROR'}<=names:break
            time.sleep(.2)
        assert {'PAGE_VIEW','JS_ERROR'}<=names,'Production RUM intake not observed'
        checks=['Production page-view request accepted','Browser error count accepted without raw message','Only bounded name/value sent without credentials']
        result={'status':'PASS','checks':checks,'measurements':list(records.values()),'framework':'Selenium Edge'}
        root=Path('artifacts/browser-telemetry');root.mkdir(parents=True,exist_ok=True);(root/'results.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
    finally:driver.quit()
if __name__=='__main__':main()
