// Install tooling: npm install --prefix artifacts/browser-tests --no-save playwright
const {chromium}=require('../../artifacts/browser-tests/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const url=process.argv[2] || 'http://localhost:3000';
(async()=>{
  const browser=await chromium.launch({channel:'msedge',headless:true});
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  page.setDefaultTimeout(90000);
  const errors=[];const checks=[];
  page.on('pageerror',e=>errors.push(e.message));page.on('dialog',dialog=>dialog.accept());
  const shot=async name=>{fs.mkdirSync('artifacts/screenshots',{recursive:true});await page.screenshot({path:`artifacts/screenshots/wallet-${name}.png`,fullPage:true});};
  const nav=async name=>page.getByRole('navigation',{name:'Payment features'}).getByRole('button',{name,exact:true}).click();
  const loggedIn=async()=>{await page.getByTestId('balance').waitFor();await page.getByRole('button',{name:'Refresh account',exact:true}).waitFor();};
  const confirm=async()=>{await page.getByRole('button',{name:'Confirm transaction',exact:true}).click();await page.getByRole('status').waitFor();await page.getByRole('button',{name:'Refresh account',exact:true}).waitFor();assert.equal(await page.getByRole('alert').count(),0);};
  try{
    await page.goto(url);await page.getByRole('button',{name:'Sign in',exact:true}).click();await loggedIn();
    assert.match(await page.getByTestId('balance').innerText(),/₹/);checks.push('Balance displayed');await shot('overview');
    await nav('Add demo money');await page.getByLabel('Amount (INR)',{exact:true}).fill('2.00');await page.getByRole('button',{name:'Review deposit',exact:true}).click();await confirm();checks.push('Deposit confirmation and completed result');
    await nav('Send money');await page.getByLabel('Recipient account',{exact:true}).fill('ACC2001');await page.getByLabel('Amount (INR)',{exact:true}).fill('0.50');await page.getByRole('button',{name:'Review transfer',exact:true}).click();await page.getByRole('region',{name:'Review transaction'}).waitFor();await confirm();checks.push('Recipient review and transfer');await shot('transfer');
    await nav('Pay merchant');await page.getByLabel('Merchant name').fill('Browser acceptance store');await page.getByLabel('Amount (INR)',{exact:true}).fill('0.25');await page.getByRole('button',{name:'Review payment',exact:true}).click();await confirm();checks.push('Merchant payment');
    await nav('History');await page.getByRole('button',{name:'Details',exact:true}).first().click();await page.getByRole('dialog',{name:'Transaction details'}).waitFor();await page.getByRole('button',{name:'Refund payment',exact:true}).click();await page.getByRole('status').waitFor();await page.getByRole('dialog').waitFor({state:'hidden'});checks.push('Transaction details and refund');await shot('history');
    await nav('Request money');await page.getByLabel('Payer account',{exact:true}).fill('ACC2001');await page.getByLabel('Amount (INR)',{exact:true}).fill('0.10');await page.getByLabel('Note (optional)').fill('Browser acceptance request');await page.getByRole('button',{name:'Review request',exact:true}).click();await confirm();checks.push('Money request creation');
    await page.getByRole('button',{name:'Sign out',exact:true}).click();await page.getByLabel('Username',{exact:true}).fill('receiver');await page.getByLabel('Password',{exact:true}).fill('receiver123');await page.getByRole('button',{name:'Sign in',exact:true}).click();await loggedIn();await nav('Request money');
    const row=page.getByRole('row').filter({hasText:'Browser acceptance request'}).filter({hasText:'OPEN'}).first();await row.getByRole('button',{name:'Pay request',exact:true}).click();await page.getByRole('status').waitFor();await page.getByRole('button',{name:'Refresh account',exact:true}).waitFor();checks.push('Receiver can approve request');await shot('requests');
    await nav('How to use');await page.getByRole('heading',{name:'How to use your demo account'}).waitFor();await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),false);checks.push('Help and mobile layout');
    assert.deepEqual(errors,[]);fs.writeFileSync('artifacts/wallet-browser.json',JSON.stringify({status:'PASS',url,checks},null,2));console.log(JSON.stringify({status:'PASS',checks},null,2));
  }catch(error){await shot('failure').catch(()=>{});fs.writeFileSync('artifacts/wallet-browser.json',JSON.stringify({status:'FAIL',checks,error:error.message,errors},null,2));throw error;}
  finally{await browser.close();}
})().catch(error=>{console.error(error.message);process.exitCode=1;});
