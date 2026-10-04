const { chromium } = require('playwright');
const fs = require('fs');
const assert = require('node:assert/strict');
fs.mkdirSync('artifacts/screenshots', {recursive:true});
(async () => {
  const browser = await chromium.launch({headless:true});
  const context = await browser.newContext({viewport:{width:1440,height:1100},locale:'en-US',timezoneId:'Asia/Kolkata'});
  const page = await context.newPage();
  const errors=[];
  const diagnostics=[];
  page.on('console', m => { if(m.type()==='error') diagnostics.push({type:'console',message:m.text()}); });
  page.on('requestfailed', r => diagnostics.push({type:'requestfailed',url:r.url(),error:r.failure()?.errorText}));
  page.on('response', r => { if(r.status()>=400) diagnostics.push({type:'http',url:r.url(),status:r.status()}); });
  page.on('pageerror', e => errors.push(e.message));
  const screenshot = name => page.screenshot({path:`artifacts/screenshots/${name}.png`,fullPage:true});
  try {
    await page.goto('http://localhost:3000');
    await page.getByRole('button',{name:'Sign in',exact:true}).click();
    await page.getByRole('button',{name:'payment',exact:true}).waitFor();
    await screenshot('01-customer-overview');
    await page.getByRole('button',{name:'payment',exact:true}).click();
    await page.waitForFunction(()=>document.querySelector('input[name=accountNumber]')?.value==='ACC1001');
    await page.getByLabel('Merchant',{exact:true}).fill('Browser demo');
    await page.getByLabel('Amount',{exact:true}).fill('1.00');
    await page.getByRole('button',{name:'Submit payment',exact:true}).click();
    await page.locator('.success').filter({hasText:'COMPLETED'}).waitFor();
    await page.waitForFunction(()=>document.querySelector('input[name=merchant]')?.value==='');
    assert.equal(await page.locator('.error').count(),0,'Payment UI reported an error');
    await screenshot('02-browser-payment-completed');
    await page.getByRole('button',{name:'transfer',exact:true}).click();
    await page.getByLabel('Amount',{exact:true}).fill('1.00');
    await page.getByRole('button',{name:'Send transfer',exact:true}).click();
    await page.locator('.success').filter({hasText:/Transfer .*COMPLETED/}).waitFor();
    assert.equal(await page.locator('.error').count(),0,'Transfer UI reported an error');
    await screenshot('03-browser-transfer-completed');
    await page.getByRole('button',{name:'Sign out',exact:true}).click();
    await page.getByLabel('Username').fill('support');
    await page.getByLabel('Password').fill('support123');
    await page.getByRole('button',{name:'Sign in',exact:true}).click();
    await page.getByRole('heading',{name:'Support / Admin dashboard'}).waitFor();
    await page.getByRole('link',{name:'Operations dashboard',exact:true}).waitFor();
    await page.waitForTimeout(3000);
    await screenshot('04-support-operations');
    const response=await context.request.post('http://localhost:3001/login',{data:{user:'admin',password:process.env.GRAFANA_ADMIN_PASSWORD||'otel-demo-admin'}});
    assert.equal(response.status(),200,'Grafana login');
    for (const [i,uid] of ['payment-poc','payment-business','telemetry-pipeline'].entries()) {
      await page.goto(`http://localhost:3001/d/${uid}?from=now-15m&to=now&kiosk`);
      await page.getByText('Grafana has failed to load its application files',{exact:false}).waitFor({timeout:2000}).then(()=>{throw new Error('Grafana frontend assets failed to load')},()=>{});
      await page.locator('[data-testid="data-testid Panel header"], [data-testid="data-testid Panel container"], .panel-container').first().waitFor({timeout:60000});
      await page.waitForTimeout(8000);
      await screenshot(`0${i+5}-${uid}`);
    }
    assert.deepEqual(errors,[],'Browser runtime errors');
    fs.writeFileSync('artifacts/browser-results.json',JSON.stringify({status:'PASS',checks:['customer payment submit/reset','customer transfer submit/reset','support dashboard','three live Grafana screenshots'],errors},null,2));
  } catch (error) {
    await screenshot('99-browser-failure').catch(()=>{});
    fs.writeFileSync('artifacts/browser-results.json',JSON.stringify({status:'FAIL',error:error.message,errors},null,2));
    throw error;
  } finally {fs.writeFileSync('artifacts/browser-diagnostics.json',JSON.stringify(diagnostics,null,2));await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
