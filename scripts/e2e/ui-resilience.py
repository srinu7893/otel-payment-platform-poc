"""Deployed browser failure/recovery checks. Injects client-only failures, no money changes."""
import argparse,json,os,sys
from pathlib import Path
sys.path.insert(0,str(Path('artifacts/selenium-deps').resolve()));os.environ['SE_CACHE_PATH']=str(Path('artifacts/selenium-cache').resolve())
from selenium import webdriver
from selenium.webdriver.edge.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

def main():
    p=argparse.ArgumentParser();p.add_argument('--url',default='https://payment-frontend-52916499838.us-central1.run.app');a=p.parse_args()
    options=webdriver.EdgeOptions();options.add_argument('--headless=new');options.add_argument('--window-size=1440,1000')
    class LocalService(Service):
        @property
        def service_url(self):return f'http://127.0.0.1:{self.port}'
    cached=list(Path('artifacts/selenium-cache/msedgedriver/win64').glob('*/msedgedriver.exe'))
    driver=webdriver.Edge(options=options,service=LocalService(str(cached[-1].resolve())) if cached else LocalService());wait=WebDriverWait(driver,120);checks=[]
    def body():return driver.find_element(By.TAG_NAME,'body').text
    def click(label):wait.until(EC.element_to_be_clickable((By.XPATH,"//button[normalize-space(.)="+json.dumps(label)+"]"))).click()
    def refreshed():wait.until(EC.element_to_be_clickable((By.XPATH,"//button[normalize-space(.)='Refresh account']")))
    try:
        driver.get(a.url)
        for name,value in [('username','demo'),('password','demo123')]:
            element=wait.until(EC.visibility_of_element_located((By.NAME,name)));element.clear();element.send_keys(value)
        click('Sign in');refreshed()
        assert 'Unavailable' not in driver.find_element(By.CSS_SELECTOR,'[data-testid=balance]').text
        checks.append('Account loads and shows last-check status')
        driver.set_window_size(390,844)
        assert not driver.execute_script('return document.documentElement.scrollWidth > innerWidth'), 'Mobile page overflows horizontally'
        checks.append('Mobile wallet has no page overflow');driver.set_window_size(1440,1000)
        driver.execute_script(r'''window.__originalFetch=window.fetch; window.fetch=(url,opts)=>{
          if(String(url).match(/\/api\/v1\/wallet(?:\?|$)/))return Promise.resolve(new Response(JSON.stringify({code:'INTERNAL_ERROR',message:'password=SECRET-MUST-NOT-LEAK'}),{status:503,headers:{'Content-Type':'application/json'}}));
          return window.__originalFetch(url,opts);};''')
        click('Refresh account');refreshed();assert 'SECRET-MUST-NOT-LEAK' not in body()
        assert 'Unavailable' in driver.find_element(By.CSS_SELECTOR,'[data-testid=balance]').text
        click('Add demo money')
        assert not driver.find_element(By.XPATH,"//button[normalize-space(.)='Review deposit']").is_enabled()
        checks.append('Service failure is sanitized and money review is disabled')
        driver.execute_script('window.fetch=window.__originalFetch');click('Refresh account');refreshed()
        assert driver.find_element(By.XPATH,"//button[normalize-space(.)='Review deposit']").is_enabled()
        checks.append('Account refresh recovers without page reload')
        driver.execute_script('''window.fetch=(url,opts)=>window.__originalFetch(url,opts).then(async response=>{
          if(String(url).includes('/api/v1/customers/') && response.ok){const data=await response.clone().json();data.name={invalid:true};return new Response(JSON.stringify(data),{status:200,headers:{'Content-Type':'application/json'}});}return response;});''')
        click('Refresh account');wait.until(lambda d:'We couldn’t display this page' in body())
        assert 'check history' in body();click('Reload and sign in');wait.until(EC.visibility_of_element_located((By.NAME,'username')))
        checks.append('Unexpected render error offers safe recovery instead of blank page')
        root=Path('artifacts/ui-resilience');root.mkdir(parents=True,exist_ok=True);result={'status':'PASS','checks':checks,'scope':'Browser-only injected failures; no money moved'}
        (root/'results.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result))
    finally:driver.quit()
if __name__=='__main__':main()
