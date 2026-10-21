"""Verify service Swagger catalogs, automatic login and a protected read in Edge."""
import argparse,json,os,sys
from pathlib import Path
sys.path.insert(0,str(Path('artifacts/selenium-deps').resolve()));os.environ['SE_CACHE_PATH']=str(Path('artifacts/selenium-cache').resolve())
from selenium import webdriver
from selenium.webdriver.edge.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait,Select
from selenium.webdriver.support import expected_conditions as EC
def main():
 p=argparse.ArgumentParser();p.add_argument('--url',default='https://payment-frontend-52916499838.us-central1.run.app/api-docs/');a=p.parse_args()
 options=webdriver.EdgeOptions();options.add_argument('--headless=new');options.add_argument('--window-size=1440,1100')
 class LocalService(Service):
  @property
  def service_url(self):return f'http://127.0.0.1:{self.port}'
 cached=list(Path('artifacts/selenium-cache/msedgedriver/win64').glob('*/msedgedriver.exe'))
 driver=webdriver.Edge(options=options,service=LocalService(str(cached[-1].resolve())) if cached else LocalService());wait=WebDriverWait(driver,120);checks=[]
 manifest=json.loads(Path('frontend/public/api-docs/specs/manifest.json').read_text())
 try:
  driver.get(a.url);wait.until(lambda d:len(d.find_elements(By.CSS_SELECTOR,'#service option'))==len(manifest))
  for catalog in manifest:
   Select(driver.find_element(By.ID,'service')).select_by_value(catalog['id'])
   wait.until(lambda d:len(d.find_elements(By.CSS_SELECTOR,'.opblock'))==catalog['operations'])
   assert not driver.find_elements(By.CSS_SELECTOR,'.swagger-ui .errors-wrapper'), 'Swagger schema rendering failed'
   if catalog['private']:
    driver.find_elements(By.CSS_SELECTOR,'.opblock-summary')[0].click()
    assert not driver.find_elements(By.CSS_SELECTOR,'.try-out__btn'),'Private contract exposed browser execution'
    assert not driver.find_element(By.ID,'signin').is_displayed()
   checks.append('Catalog renders '+catalog['id']+' with '+str(catalog['operations'])+' operations')
  Select(driver.find_element(By.ID,'service')).select_by_value('public')
  wait.until(lambda d:len(d.find_elements(By.CSS_SELECTOR,'.opblock'))==30)
  driver.find_element(By.CSS_SELECTOR,'#signin button').click()
  wait.until(lambda d:'Signed in. Roles:' in d.find_element(By.ID,'auth-status').text)
  checks.append('Swagger login acquires application JWT without copying it')
  block=next(b for b in driver.find_elements(By.CSS_SELECTOR,'.opblock-get') if b.find_element(By.CSS_SELECTOR,'.opblock-summary-path').get_attribute('data-path')=='/api/v1/wallet')
  driver.execute_script('arguments[0].scrollIntoView({block:"center"})',block);block.find_element(By.CSS_SELECTOR,'.opblock-summary').click()
  wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR,'.try-out__btn'))).click()
  block.find_element(By.CSS_SELECTOR,'.execute').click()
  wait.until(lambda d:block.find_elements(By.CSS_SELECTOR,'.live-responses-table') and 'ACC1001' in block.find_element(By.CSS_SELECTOR,'.live-responses-table').text)
  code=block.find_element(By.CSS_SELECTOR,'.live-responses-table tbody .response-col_status').text.strip().splitlines()[0]
  assert code=='200','Swagger wallet returned HTTP '+code
  checks.append('Protected wallet Try it out returns HTTP 200 and actual account')
  assert 'curl' in block.find_element(By.CSS_SELECTOR,'.curl-command').text
  checks.append('Swagger generates curl for the executed request')
  driver.execute_script('window.scrollTo(0,0)');driver.find_element(By.ID,'clear-token').click();assert driver.find_element(By.ID,'auth-status').text=='Session cleared.'
  assert driver.execute_script('return !Object.keys(localStorage).some(k=>/token|auth/i.test(k))')
  checks.append('Session can be cleared and is not persisted in browser storage')
  login=next(b for b in driver.find_elements(By.CSS_SELECTOR,'.opblock-post') if b.find_element(By.CSS_SELECTOR,'.opblock-summary-path').get_attribute('data-path')=='/api/v1/auth/login')
  driver.execute_script('arguments[0].scrollIntoView({block:"center"})',login);login.find_element(By.CSS_SELECTOR,'.opblock-summary').click()
  wait.until(lambda d:login.find_elements(By.CSS_SELECTOR,'.try-out__btn'))
  login.find_element(By.CSS_SELECTOR,'.try-out__btn').click()
  wait.until(lambda d:login.find_elements(By.CSS_SELECTOR,'.execute'))
  login.find_element(By.CSS_SELECTOR,'.execute').click()
  wait.until(lambda d:'Signed in for API requests' in d.find_element(By.ID,'auth-status').text)
  checks.append('Swagger Login operation also authorizes subsequent requests automatically')
  driver.execute_script('arguments[0].scrollIntoView({block:"center"})',block);block.find_element(By.CSS_SELECTOR,'.execute').click()
  wait.until(lambda d:block.find_element(By.CSS_SELECTOR,'.live-responses-table tbody .response-col_status').text.strip().splitlines()[0]=='200')
  checks.append('Protected read works after operation-based login')
  result={'status':'PASS','url':a.url,'checks':checks,'scope':'Catalog rendering and one authenticated wallet GET; no direct internal mutations or tokens in evidence'}
  root=Path('artifacts/api-docs-browser');root.mkdir(parents=True,exist_ok=True);(root/'results.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
 finally:driver.quit()
if __name__=='__main__':main()
