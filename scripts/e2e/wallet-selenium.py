#!/usr/bin/env python3
"""Selenium UI acceptance suite. Creates two sandbox users and moves mock INR only.

Install: python -m pip install --target artifacts/selenium-deps selenium
Run from repository root. Credentials/tokens are never written to the report.
"""
import argparse,json,os,re,sys,time,uuid
from decimal import Decimal
from pathlib import Path
from urllib.parse import urlparse
sys.path.insert(0,str(Path('artifacts/selenium-deps').resolve()))
os.environ['SE_CACHE_PATH']=str(Path('artifacts/selenium-cache').resolve())
from selenium import webdriver
from selenium.webdriver.edge.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait,Select
from selenium.webdriver.support import expected_conditions as EC

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--url',default='https://payment-frontend-52916499838.us-central1.run.app')
parser.add_argument('--out',default='artifacts/selenium-wallet-results.json')
args=parser.parse_args()
options=webdriver.EdgeOptions();options.add_argument('--headless=new');options.add_argument('--window-size=1440,1100')
class LoopbackService(Service):
    @property
    def service_url(self):return f'http://127.0.0.1:{self.port}'
print('Starting Selenium Edge browser',flush=True)
cached=list(Path('artifacts/selenium-cache/msedgedriver/win64').glob('*/msedgedriver.exe'))
driver=webdriver.Edge(options=options,service=LoopbackService(str(cached[-1].resolve())) if cached else LoopbackService());wait=WebDriverWait(driver,90)
driver.set_page_load_timeout(90)
print('Browser started',flush=True)
results=[];completed=False;screens=Path('artifacts/selenium-screenshots');screens.mkdir(parents=True,exist_ok=True)
suffix=uuid.uuid4().hex[:10]
users={label:{'username':'selenium_'+label+'_'+suffix,'password':'Selenium-test-'+uuid.uuid4().hex,'name':'Selenium '+label.upper(),'email':label+'_'+suffix+'@example.com'} for label in ['a','b']}

def text():return driver.find_element(By.TAG_NAME,'body').text
def button(name,scope=''):
    return wait.until(EC.element_to_be_clickable((By.XPATH,scope+"//button[normalize-space(.)="+json.dumps(name)+"]")))
def click(name,scope=''):button(name,scope).click()
def field(name,value):
    element=wait.until(EC.visibility_of_element_located((By.NAME,name)));element.clear();element.send_keys(str(value));return element
def nav(label):click(label,"//nav[@aria-label='Payment features']")
def ready():
    wait.until(EC.element_to_be_clickable((By.XPATH,"//button[normalize-space(.)='Refresh account']")))
    assert not driver.find_elements(By.CSS_SELECTOR,'[role=alert]'), 'Unexpected wallet error banner'
def money():
    nav('My account');ready()
    value=driver.find_element(By.CSS_SELECTOR,'[data-testid=balance]').text
    assert 'Unavailable' not in value,'Balance is unavailable'
    return Decimal(re.sub(r'[^\d.-]','',value))
def equal(actual,expected):assert actual==expected,f'Expected {expected}, got {actual}'
def shot(name):driver.save_screenshot(str(screens/(name+'.png')))
def check(name,action,critical=True):
    print('CHECK:',name,flush=True)
    try:
        value=action();results.append({'name':name,'status':'PASS'});print('PASS:',name,flush=True);return value
    except Exception as error:
        message=str(error).split('Stacktrace:')[0][:600]
        for user in users.values():message=message.replace(user['password'],'[REDACTED]')
        diagnostics=driver.execute_script('return [...document.querySelectorAll("input")].map(e=>({name:e.name,length:e.value.length,valid:e.checkValidity(),message:e.validationMessage}))')
        alerts=[e.text for e in driver.find_elements(By.CSS_SELECTOR,'.error')]
        results.append({'name':name,'status':'FAIL','error':message,'form':diagnostics,'alerts':alerts});shot('failure-'+str(len(results)));print('FAIL:',name,message,diagnostics,alerts,flush=True)
        if critical:raise
def login(user):
    field('username',user['username']);field('password',user['password']);click('Sign in');wait.until(EC.presence_of_element_located((By.CSS_SELECTOR,'[data-testid=balance]')));ready()
def logout():click('Sign out');wait.until(EC.presence_of_element_located((By.NAME,'username')))
def register(label):
    user=users[label];click('New user? Create account')
    for name in ['name','email','username','password']:field(name,user[name])
    field('confirmPassword',user['password']);click('Create account')
    for attempt in range(3):
        wait.until(lambda d:d.find_elements(By.CSS_SELECTOR,'[data-testid=balance]') or d.find_elements(By.CSS_SELECTOR,'[role=alert]'))
        if driver.find_elements(By.CSS_SELECTOR,'[data-testid=balance]'):break
        alert=driver.find_element(By.CSS_SELECTOR,'[role=alert]').text
        assert 'REGISTRATION_PENDING' in alert,alert
        print('Resuming pending registration with the same details',flush=True)
        click('Create account')
    wait.until(EC.presence_of_element_located((By.CSS_SELECTOR,'[data-testid=balance]')));ready()
    account=re.search(r'Account (ACC[A-Z0-9]+)',driver.find_element(By.CSS_SELECTOR,'.wallet > .row p').text).group(1)
    user['account']=account;equal(money(),Decimal('0'));return account
def review(tab,amount,**extra):
    nav(tab)
    for name,value in extra.items():field(name,value)
    field('amount',amount)
    kinds={'Add demo money':'deposit','Pay merchant':'payment','Send money':'transfer','Request money':'request'}
    click('Review '+kinds[tab]);wait.until(EC.visibility_of_element_located((By.CSS_SELECTOR,'[aria-label="Review transaction"]')))
def confirm(status='COMPLETED'):
    click('Confirm transaction');status_contains(status)
def deposit(amount):review('Add demo money',amount);confirm()
def request(note,amount='1'):
    review('Request money',amount,account=users['b']['account'],note=note);confirm('OPEN')
def request_row(note):return wait.until(EC.presence_of_element_located((By.XPATH,"//tr[td[normalize-space(.)="+json.dumps(note)+"]]")))
def row_click(note,label):request_row(note).find_element(By.XPATH,".//button[normalize-space(.)="+json.dumps(label)+"]").click()
def accept():wait.until(EC.alert_is_present()).accept()
def status_contains(value):wait.until(lambda d:any(value in node.text for node in d.find_elements(By.CSS_SELECTOR,'[role=status]')));ready()
def lost_payment(merchant,amount):
    driver.execute_script('window.__seleniumLosePayment=true')
    review('Pay merchant',amount,merchant=merchant);click('Confirm transaction')
    wait.until(lambda d:d.find_elements(By.CSS_SELECTOR,'[role=alert]') and 'Simulated lost payment response' in d.find_element(By.CSS_SELECTOR,'[role=alert]').text)
def normal_payment(merchant,amount):review('Pay merchant',amount,merchant=merchant);confirm()

try:
    driver.get(args.url)
    print('Loaded deployed frontend',flush=True)
    driver.execute_script("""
      const original=window.fetch.bind(window);
      window.fetch=async (...args)=>{
        const response=await original(...args);
        if(window.__seleniumLosePayment && (args[1]?.method||'GET')==='POST' && new URL(args[0],location.href).pathname==='/api/v1/payments' && response.ok){
          window.__seleniumLosePayment=false;await response.clone().text();throw new TypeError('Simulated lost payment response');
        }
        if(window.__seleniumPendingPayment && (args[1]?.method||'GET')==='POST' && new URL(args[0],location.href).pathname==='/api/v1/payments' && response.ok){
          window.__seleniumPendingPayment=false;const body=await response.json();body.status='PROCESSING';
          return new Response(JSON.stringify(body),{status:response.status,headers:{'Content-Type':'application/json'}});
        }
        return response;
      };
    """)
    check('Invalid login is rejected',lambda:(field('username','unknown_'+suffix),field('password','Invalid-password-123'),click('Sign in'),wait.until(EC.visibility_of_element_located((By.CSS_SELECTOR,'[role=alert]')))))
    click('New user? Create account')
    for name,value in {'name':'New User','email':'invalid-email','username':'ab','password':'short','confirmPassword':'short'}.items():field(name,value)
    check('Registration enforces email, username and password constraints',lambda:equal(driver.execute_script('return document.querySelector("form").checkValidity()'),False))
    user=users['a']
    for name in ['name','email','username','password']:field(name,user[name])
    field('confirmPassword','Mismatch-password-123');click('Create account')
    check('Password confirmation mismatch is rejected',lambda:wait.until(lambda d:'Passwords do not match' in text()))
    click('Back to sign in')
    check('Register user A and create a zero-balance account',lambda:register('a'));shot('registered-account')
    logout();check('Register user B with a distinct bank account',lambda:register('b'));assert users['a']['account']!=users['b']['account'];logout()
    check('New user can sign in with chosen credentials',lambda:login(users['a']))
    check('Insufficient funds produce a failed payment without a debit',lambda:(review('Pay merchant','0.01',merchant='Insufficient funds '+suffix),confirm('FAILED'),equal(money(),Decimal('0'))))
    review('Add demo money','5');click('Go back')
    check('Cancelling deposit review does not move funds',lambda:equal(money(),Decimal('0')))
    nav('Add demo money');field('amount','-1')
    check('Negative amount is blocked by the form',lambda:equal(driver.execute_script('return document.querySelector("input[name=amount]").checkValidity()'),False))
    field('amount','0.001')
    check('Amounts below one paisa or with extra precision are blocked',lambda:equal(driver.execute_script('return document.querySelector("input[name=amount]").checkValidity()'),False))
    check('Deposit credits the account exactly',lambda:(deposit('10'),equal(money(),Decimal('10'))))
    nav('Send money');field('account',users['a']['account']);field('amount','1');click('Review transfer')
    check('Self transfer is rejected before confirmation',lambda:wait.until(lambda d:'Choose a different account' in text()))
    nav('Send money');field('account','UNKNOWN999');field('amount','1');click('Review transfer')
    check('Unknown recipient is rejected',lambda:wait.until(lambda d:'ACCOUNT_NOT_FOUND' in text()))
    nav('Pay merchant');field('merchant','   ');field('amount','1');click('Review payment')
    check('Whitespace-only merchant is rejected before review',lambda:equal(len(driver.find_elements(By.CSS_SELECTOR,'[aria-label="Review transaction"]')),0),False)
    nav('My account')
    merchant='Selenium store '+suffix
    check('Merchant payment debits exactly once',lambda:(normal_payment(merchant,'1'),equal(money(),Decimal('9'))))
    nav('History');click('Details',"//section[h2[normalize-space(.)='Merchant payments']]")
    dialog=wait.until(EC.visibility_of_element_located((By.CSS_SELECTOR,'[role=dialog]')))
    payment_id=dialog.find_element(By.XPATH,".//dt[.='paymentId']/following-sibling::dd").text
    check('Payment details show a complete transaction ID',lambda:equal(str(uuid.UUID(payment_id)),payment_id))
    click('Refund payment');accept();status_contains('COMPLETED');wait.until(EC.invisibility_of_element_located((By.CSS_SELECTOR,'[role=dialog]')))
    check('Full refund restores the account balance',lambda:equal(money(),Decimal('10')))
    nav('History');click('Details',"//section[h2[normalize-space(.)='Merchant payments']]")
    wait.until(EC.visibility_of_element_located((By.CSS_SELECTOR,'[role=dialog]')))
    check('Refunded payment cannot be refunded twice through the UI',lambda:equal(len(driver.find_elements(By.XPATH,"//button[normalize-space(.)='Refund payment']")),0));click('Close details')
    check('Transfer verifies recipient and debits sender',lambda:(review('Send money','2',account=users['b']['account']),wait.until(lambda d:users['b']['name'] in driver.find_element(By.CSS_SELECTOR,'[aria-label="Review transaction"]').text),confirm(),equal(money(),Decimal('8'))))
    nav('History');click('Details',"//section[h2[normalize-space(.)='Outgoing transfers']]")
    dialog=wait.until(EC.visibility_of_element_located((By.CSS_SELECTOR,'[role=dialog]')))
    transfer_id=dialog.find_element(By.XPATH,".//dt[.='transferId']/following-sibling::dd").text
    check('Transfer details show the masked recipient and transaction ID',lambda:(equal(str(uuid.UUID(transfer_id)),transfer_id),equal(dialog.find_element(By.XPATH,".//dt[.='receiverAccount']/following-sibling::dd").text,'****'+users['b']['account'][-4:])));click('Close details')
    request('cancel-'+suffix);row_click('cancel-'+suffix,'Cancel request');status_contains('CANCELLED')
    check('Requester can cancel without moving money',lambda:equal(money(),Decimal('8')))
    request('decline-'+suffix);request('pay-'+suffix);logout();login(users['b'])
    check('Recipient sees incoming transfer credit',lambda:(equal(money(),Decimal('2')),wait.until(lambda d:'TRANSFER' in text() and 'CREDIT' in text())))
    nav('Request money');row_click('decline-'+suffix,'Decline');status_contains('DECLINED')
    check('Payer can decline without moving money',lambda:equal(money(),Decimal('2')))
    nav('Request money');row_click('pay-'+suffix,'Pay request');wait.until(EC.alert_is_present()).dismiss()
    check('Dismissing payer confirmation does not move money',lambda:equal(money(),Decimal('2')))
    nav('Request money');row_click('pay-'+suffix,'Pay request');accept();status_contains('PAID')
    check('Payer approval debits the payer exactly once',lambda:equal(money(),Decimal('1')));logout();login(users['a'])
    check('Approved request credits requester and conserves funds',lambda:equal(money(),Decimal('9')))
    check('Lost payment response can be retried without a duplicate debit',lambda:(lost_payment('Lost response immediate '+suffix,'0.10'),confirm(),equal(money(),Decimal('8.90'))))
    lost_payment('Lost response after deposit '+suffix,'0.20');deposit('0.01');normal_payment('Lost response after deposit '+suffix,'0.20')
    check('Uncertain payment retains retry protection after another successful action',lambda:equal(money(),Decimal('8.71')),False)
    driver.execute_script('window.__seleniumPendingPayment=true')
    review('Pay merchant','0.10',merchant='Pending response '+suffix);confirm('PROCESSING')
    deposit('0.01');normal_payment('Pending response '+suffix,'0.10')
    check('Pending payment retains retry protection after another successful action',lambda:equal(money(),Decimal('8.62')),False)
    # Enough genuine UI deposits to exercise the second activity page; no API seeding.
    for i in range(21):deposit('0.01')
    check('Repeated small deposits all complete',lambda:ready())
    nav('History');click('Next page');wait.until(lambda d:'Page 2' in text());ready();shot('history-page-2')
    nav('Request money');ready()
    check('Changing features resets pagination to the first page',lambda:equal('Page 1' in text(),True),False)
    if 'Page 2' in text():click('Previous page');ready()
    nav('Notifications');ready();check('Notifications show delivered payment events',lambda:wait.until(lambda d:'PAYMENT_COMPLETED' in text() and 'SENT' in text()))
    nav('How to use');check('Help is accessible',lambda:wait.until(lambda d:'How to use your demo account' in text()))
    driver.set_window_size(390,844)
    check('Mobile help has no horizontal page overflow',lambda:equal(driver.execute_script('return document.documentElement.scrollWidth>innerWidth'),False));shot('mobile');driver.set_window_size(1440,1100)
    logout();login_data={'username':'support','password':'support123'}
    field('username',login_data['username']);field('password',login_data['password']);click('Sign in')
    wait.until(lambda d:'Support / Admin dashboard' in text());wait.until(EC.element_to_be_clickable((By.XPATH,"//button[normalize-space(.)='Refresh operations']")))
    check('Support dashboard loads service health rows',lambda:equal(len(driver.find_elements(By.XPATH,"//section[div/div/h2[.='Service health']]//tbody/tr")),6))
    field_input=driver.find_element(By.CSS_SELECTOR,'.inlineForm input[placeholder="UUID"]');field_input.send_keys(payment_id);click('Search');wait.until(EC.visibility_of_element_located((By.CSS_SELECTOR,'.resultBox')))
    check('Support can look up another customer payment',lambda:wait.until(lambda d:payment_id in driver.find_element(By.CSS_SELECTOR,'.resultBox').text))
    field_input=driver.find_element(By.CSS_SELECTOR,'.inlineForm input[placeholder="UUID"]');field_input.clear();field_input.send_keys('not-a-uuid');click('Search')
    wait.until(lambda d:len(d.find_elements(By.CSS_SELECTOR,'.error'))>0)
    check('Invalid support lookup produces validation rather than internal error',lambda:equal('INTERNAL_ERROR' in text(),False),False)
    if urlparse(args.url).hostname not in ('localhost','127.0.0.1'):
        check('Cloud observability links do not point to localhost',lambda:equal(any('localhost' in a.get_attribute('href') for a in driver.find_elements(By.CSS_SELECTOR,'a[href]')),False),False)
    else:
        results.append({'name':'Cloud observability links do not point to localhost','status':'SKIP','reason':'Local stack uses local dashboards'})
    logout();field('username','admin');field('password','admin123');click('Sign in');wait.until(lambda d:'Support / Admin dashboard' in text())
    check('Admin sees the operations dashboard',lambda:wait.until(lambda d:'Roles: ADMIN' in text()));shot('operations')
    completed=True
except Exception as error:
    if not results or results[-1]['status']!='FAIL':check('Selenium journey execution',lambda:(_ for _ in ()).throw(error),False)
finally:
    report={'status':'FAIL' if not completed or any(r['status']=='FAIL' for r in results) else 'PASS','completed':completed,'framework':'Selenium WebDriver','browser':driver.capabilities.get('browserVersion'),'url':args.url,'checks':results,'scope':'Mock INR transactions; generated account credentials and tokens are excluded'}
    output=Path(args.out);output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,indent=2),encoding='utf-8')
    print('RESULT:',report['status'],'passed=',sum(r['status']=='PASS' for r in results),'failed=',sum(r['status']=='FAIL' for r in results),flush=True)
    driver.quit()
sys.exit(0 if report['status']=='PASS' else 1)
