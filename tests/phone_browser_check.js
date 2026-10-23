'use strict';
// Run after build_local.py. Requires Playwright and a Chromium executable; artifacts stay in the given output directory.
const fs=require('fs'),path=require('path'),cp=require('child_process');
const {chromium}=require(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES?process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES+'/playwright':'playwright');
const root=path.resolve(process.argv[2]||'.'),chrome=process.argv[3],output=path.resolve(process.argv[4]||'/tmp/qiping-browser-check');fs.mkdirSync(output,{recursive:true});
let count=0;function check(value,label){if(!value)throw Error(label);count++;console.log('PASS browser '+label);}
const server=cp.spawn('java',['-cp',path.join(root,'build/standalone/tests'),'cn.wengmang.qiping.PhoneBrowserHarness',path.join(root,'app/src/main/assets/phone-wallpaper')]);
let browser;server.stderr.on('data',data=>process.stderr.write(data));
(async()=>{
 const lines=await new Promise((resolve,reject)=>{let text='';server.stdout.on('data',data=>{text+=data;const values=text.trim().split('\n');if(values.length>=2)resolve(values);});server.on('error',reject);server.on('exit',code=>{if(code)reject(Error('Harness exit '+code));});});
 const url=lines[0],code=lines[1],origin=new URL(url).origin;browser=await chromium.launch({executablePath:chrome,headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const context=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:1,isMobile:true,hasTouch:true});const page=await context.newPage();const errors=[],external=[];
 page.on('pageerror',error=>errors.push(error.message));page.on('request',request=>{if(!request.url().startsWith(origin)&&!request.url().startsWith('blob:'))external.push(request.url());});
 await page.goto(url);await page.locator('#editor').waitFor({state:'visible'});check(await page.locator('#connection').innerText()!=='等待连接','QR session connects');check(!page.url().includes('#'),'session token removed from address bar');check(await page.locator('#scene').inputValue()==='1','television category preselected');
 check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'390 px layout has no horizontal overflow');
 await page.screenshot({path:path.join(output,'mobile-connected.png'),fullPage:true});
 await page.locator('#file').setInputFiles({name:'too-big.jpg',mimeType:'image/jpeg',buffer:Buffer.alloc(20*1024*1024+1)});check((await page.locator('#error').innerText()).includes('20 MB'),'oversized image rejected before upload');check(await page.locator('#send').isDisabled(),'invalid selection cannot upload');
 // A real one-pixel PNG is enough for browser preview; Android decoder/large-image checks run separately.
 const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aH1cAAAAASUVORK5CYII=','base64');
 await page.locator('#file').setInputFiles({name:'phone-wallpaper.png',mimeType:'image/png',buffer:png});await page.waitForFunction(()=>!document.getElementById('send').disabled);check(await page.locator('#send').isEnabled(),'valid selection enables upload');await page.locator('#send').click();await page.waitForFunction(()=>document.getElementById('status').textContent.includes('电视上确认'));check(await page.locator('#send').isDisabled(),'pending TV approval prevents another upload');await page.screenshot({path:path.join(output,'mobile-pending.png'),fullPage:true});
 const rejected=await page.evaluate(async token=>{const r=await fetch('/upload?scene=2',{method:'POST',headers:{'X-Qiping-Token':token,'Content-Type':'image/png'},body:new Uint8Array(65536)});return [r.status,(await r.json()).message];},url.split('#')[1]);check(rejected[0]===409&&rejected[1].includes('上一张'),'concurrent image returns readable guidance');
 server.stdin.write('apply\n');await page.waitForFunction(()=>document.getElementById('status').textContent.includes('电视已应用'));check(true,'phone receives TV approval');
 await page.locator('#scene').selectOption('5');await page.locator('#file').setInputFiles({name:'second.png',mimeType:'image/png',buffer:png});await page.waitForTimeout(2200);check(await page.locator('#send').isEnabled(),'polling does not erase next selected image');await page.locator('#send').click();await page.waitForFunction(()=>document.getElementById('status').textContent.includes('电视上确认'));check(await page.locator('#scene').inputValue()==='5','second upload keeps selected category');
 server.stdin.write('reject\n');await page.waitForFunction(()=>document.getElementById('status').textContent.includes('放弃'));check(true,'phone receives TV discard');
 const manual=await context.newPage();await manual.goto(origin+'/');await manual.locator('#pairing').waitFor({state:'visible'});check(await manual.locator('#editor').isHidden(),'manual address asks for code');await manual.locator('#code').fill(code==='000000'?'111111':'000000');await manual.locator('#pair').click();await manual.waitForFunction(()=>document.getElementById('error').textContent.includes('不正确'));check(true,'incorrect pairing code explained');await manual.locator('#code').fill(code);await manual.locator('#pair').click();await manual.locator('#editor').waitFor({state:'visible'});check(true,'manual pairing connects');
 await manual.setViewportSize({width:320,height:740});check(await manual.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'320 px screen has no horizontal overflow');
 check(errors.length===0,'no JavaScript errors');check(external.length===0,'no external page dependencies');
 server.stdin.end('quit\n');await manual.waitForFunction(()=>document.getElementById('connection').textContent==='连接已结束',{},{timeout:15000});check(await manual.locator('#send').isDisabled(),'disconnection stops polling and disables upload');
 console.log('PASS: Phone browser '+count+' checks');
})().catch(error=>{console.error(error.stack);process.exitCode=1;}).finally(async()=>{if(browser)await browser.close();if(!server.stdin.writableEnded)server.stdin.end('quit\n');});
