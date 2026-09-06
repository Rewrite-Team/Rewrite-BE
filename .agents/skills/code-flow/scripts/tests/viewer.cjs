// Optional UI verification: node viewer.cjs <output directory containing api-003/020.html>
// Requires Playwright. Set CHROME_BIN to use an existing Chrome installation.
const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const {pathToFileURL} = require('node:url');

(async () => {
  const out = path.resolve(process.argv[2]);
  const browser = await chromium.launch({headless:true, ...(process.env.CHROME_BIN ? {executablePath:process.env.CHROME_BIN} : {})});
  try {
    const page = await browser.newPage({viewport:{width:1280,height:1000}, reducedMotion:'reduce'});
    const errors = [], requests = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('request', req => {if (!req.url().startsWith('file:')) requests.push(req.url());});
    const box = id => page.locator(`[data-call="${id}"]`);
    const rect = id => box(id).evaluate(el => ({top:el.offsetTop, bottom:el.offsetTop+el.offsetHeight, left:el.offsetLeft, height:el.offsetHeight}));
    await page.goto(pathToFileURL(path.join(out,'api-003.html')).href);
    assert.equal(await page.locator('.activation').count(),5);
    const parent = await rect('issue'), child = await rect('sign');
    assert(parent.top < child.top && parent.bottom > child.bottom && parent.left < child.left);
    await box('sign').locator('button').click();
    assert.equal(await page.locator('.ancestor').count(),2);
    assert.match(await page.locator('#code').textContent(),/Mac.getInstance/);
    assert.equal(await page.locator('.tx').count(),0);
    await page.locator('#home').click();
    await page.screenshot({path:path.join(out,'sync.png'),fullPage:true});

    await page.goto(pathToFileURL(path.join(out,'api-020.html')).href);
    const before = await rect('controller'), serviceBefore = await rect('service');
    assert.equal(await box('worker').count(),0);
    assert.equal(await page.locator('[role="tab"]').count(),0);
    const widthBefore = await page.locator('#canvas').evaluate(el=>el.offsetWidth);
    await page.locator('[data-async="listener"]').click();
    assert.equal(await box('worker').count(),1);
    assert.deepEqual(await rect('controller'),before);
    assert.deepEqual(await rect('service'),serviceBefore);
    assert((await page.locator('#canvas').evaluate(el=>el.offsetWidth)) > widthBefore);
    await box('client').locator('button').click();
    assert.equal(await box('controller').evaluate(el=>el.classList.contains('ancestor')),false);
    assert.equal(await box('worker').evaluate(el=>el.classList.contains('ancestor')),true);
    const client = await rect('client');
    const bands = await page.locator('.tx').evaluateAll(els=>els.map(el=>({top:el.offsetTop,bottom:el.offsetTop+el.offsetHeight,left:el.offsetLeft,right:el.offsetLeft+el.offsetWidth})));
    assert(!bands.some(b=>b.top<=client.top && b.bottom>=client.bottom && b.left<=client.left && b.right>=client.left+240), 'LLM must be outside TX');
    await page.locator('#viewport').evaluate(el=>{el.scrollLeft=0;el.scrollTop=0;});
    await page.screenshot({path:path.join(out,'async-root.png'),fullPage:true});
    await box('worker').locator('button').scrollIntoViewIfNeeded();
    await page.screenshot({path:path.join(out,'async-expanded.png'),fullPage:true});
    await page.locator('[data-async="listener"]').click();
    assert.equal(await box('worker').count(),0);
    assert.equal(await page.locator('.selected').getAttribute('data-call'),'controller');
    for (const width of [736,360]) {
      await page.setViewportSize({width,height:900});
      await page.emulateMedia({colorScheme:width===360?'dark':'light'});
      await page.locator('#home').click();
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'no page-level horizontal overflow');
      const clipped = await page.locator('.call-head,.call-foot,.step,.case-label,.branch-label,.async-button').evaluateAll(els=>els.filter(el=>el.scrollHeight>el.clientHeight+2).map(el=>el.textContent));
      assert.deepEqual(clipped,[],'visible text should fit');
      await page.screenshot({path:path.join(out,`width-${width}.png`),fullPage:true});
    }
    assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);
    console.log('PASS: parent lifetimes, same-class calls, async expansion/collapse, TX exclusion, source selection, mobile overflow and offline loading');
  } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
