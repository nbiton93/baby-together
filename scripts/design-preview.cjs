// Render the actual shared UI with synthetic local data, without Google credentials.
const { chromium, webkit } = require('playwright');
const { createServer } = require('node:http');
const { readFileSync, mkdirSync } = require('node:fs');
const { join } = require('node:path');
const { randomUUID } = require('node:crypto');
const assert = require('node:assert/strict');
const C = require('../shared/core.js');
const root = join(__dirname, '..');
const fixture = C.fresh(randomUUID());
fixture.author = 'אבא';
const now = Date.now();
function add(target, value, at = now) {
  fixture.own.push(C.makeOp(fixture, target, value, false, randomUUID(), at));
}
const birth = new Date(now - 7 * 86400000);
add('profile', { name: 'קטנטנה', birth: C.localDate(birth) });
function event(kind, detail, amount, ago, ended = null, note = '') {
  add(randomUUID(), { kind, detail, amount, minutes: null,
    started: new Date(now - ago * 60000).toISOString(), ended, note });
}
event('feed', 'formula', 90, 30);
event('diaper', 'wet', null, 75);
event('sleep', 'sleep', null, 150, new Date(now - 85 * 60000).toISOString());
event('feed', 'milk', 60, 170);
C.parseState(JSON.stringify(fixture));
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' };
const server = createServer((req, res) => {
  const name = new URL(req.url, 'http://localhost').pathname.slice(1) || 'index.html';
  if (!/^(index\.html|style\.css|core\.js|app\.js|bridge\.js|cloud\.js)$/.test(name)) {
    res.writeHead(404); res.end(); return;
  }
  res.setHeader('Content-Type', types[name.slice(name.lastIndexOf('.'))] + '; charset=utf-8');
  // Request local diagnostic samples only; this adds no permitted source or inline style.
  const content = readFileSync(join(root, 'shared', name), 'utf8');
  res.end(name === 'index.html' ? content.replace("style-src 'self'", "style-src 'self' 'report-sample'") : content);
});
async function run() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const url = 'http://127.0.0.1:' + server.address().port;
  mkdirSync(join(root, 'out/design'), { recursive: true });
  for (const [name, browserType] of [['chromium', chromium], ['webkit', webkit]]) {
    const browser = await browserType.launch();
    try {
      const context = await browser.newContext({
        viewport: { width: 390, height: 844 }, deviceScaleFactor: 1,
        isMobile: true, hasTouch: true, locale: 'he-IL', timezoneId: 'Asia/Jerusalem',
        reducedMotion: 'reduce'
      });
      await context.addInitScript(({ data }) => {
        // The production bridge stays unchanged. Only this isolated test page mocks native IO.
        if (!localStorage.getItem('bt-design-fixture')) localStorage.setItem('bt-design-fixture', data);
        window.BabyNative = { postMessage(raw) {
          const { id, method, args } = JSON.parse(raw);
          queueMicrotask(() => {
            if (method === 'read') window.BTReply(id, true, localStorage.getItem('bt-design-fixture'));
            else if (method === 'write') { localStorage.setItem('bt-design-fixture', args.data); window.BTReply(id, true, true); }
            else if (method === 'uuid') window.BTReply(id, true, crypto.randomUUID());
            else if (method === 'config') window.BTReply(id, true, { platform: 'design-fixture', googleConfigured: true });
            else window.BTReply(id, false, 'פעולה לא זמינה בתצוגת בדיקה');
          });
        }};
        window.designCspErrors = [];
        document.addEventListener('securitypolicyviolation', event => window.designCspErrors.push({ directive: event.violatedDirective, source: event.sourceFile, line: event.lineNumber, blocked: event.blockedURI, sample: event.sample }));
      }, { data: JSON.stringify(fixture) });
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(url);
      await page.locator('h1').waitFor();
      assert.equal(await page.locator('h1').innerText(), 'קטנטנה');
      async function fits(label) {
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), true, name + ': horizontal overflow on ' + label);
        assert.equal(await page.locator('html').getAttribute('dir'), 'rtl');
      }
      async function cleanCsp(label) {
        const reports = await page.evaluate(() => window.designCspErrors);
        // Both styles remain BLOCKED by CSP. These exact, harmless reports come from:
        // 1. Playwright 1.64's WebKit screenshot animation sync: screenshotter.ts, body {}.
        // 2. WebKit HTMLSelectElement.cpp's UA shadow slot: text-overflow:inherit.
        // A minimal native-select page reproduced (2), with no application JS or inline DOM.
        // https://github.com/microsoft/playwright/blob/v1.64.0/packages/playwright-core/src/server/screenshotter.ts
        // https://github.com/WebKit/WebKit/blob/main/Source/WebCore/html/HTMLSelectElement.cpp
        const expectedToolOrUaReport = e => name === 'webkit' && e.blocked === 'inline' && (
          (e.directive === 'style-src-elem' && e.sample === 'body {}' && !e.source) ||
          (e.directive === 'style-src-attr' && e.sample === 'text-overflow:inherit')
        );
        assert.deepEqual(reports.filter(e => !expectedToolOrUaReport(e)), [], name + ': application CSP on ' + label);
        assert.equal(await page.locator('style, [style]').count(), 0, name + ': unexpected inline application styles on ' + label);
      }
      async function shot(label, log = true) {
        await fits(label);
        await cleanCsp('before ' + label);
        await page.screenshot({ path: join(root, 'out/design', name + '-' + label + '.png'), animations: 'allow', caret: 'initial' });
        if (log && name === 'webkit') {
          const image = await page.screenshot({ type: 'jpeg', quality: 78, animations: 'allow', caret: 'initial' });
          // Only synthetic UI pixels. Used for remote visual review when no local browser is available.
          console.log('DESIGN_IMAGE:' + label + ':' + image.toString('base64'));
        }
      }
      assert.equal(await page.locator('.stat.sleep strong').innerText(), '1:05');
      await shot('journal');
      await page.locator('[data-action=add][data-kind=feed]').click();
      await page.locator('#amount').fill('80');
      await shot('feed-form');
      await page.locator('#entry-form button[type=submit]').click();
      await page.locator('#modal').waitFor({ state: 'hidden' });
      assert.equal(await page.locator('.entry.feed').count(), 3);
      await page.locator('#toast').waitFor({ state: 'visible' });
      assert.equal(await page.locator('#toast').innerText(), 'נשמר בטלפון');
      await cleanCsp('save notification');
      await page.reload();
      await page.locator('.entry.feed').first().waitFor();
      assert.equal(await page.locator('.entry.feed').count(), 3);
      await page.locator('[data-tab=stats]').click();
      await shot('week');
      await page.locator('[data-tab=family]').click();
      await shot('together');
      assert.equal(await page.locator('[data-action=share]').isDisabled(), true);
      assert.equal(await page.locator('[data-action=connect]').isEnabled(), true);
      await page.setViewportSize({ width: 320, height: 740 });
      await fits('family 320');
      await page.locator('[data-tab=journal]').click();
      await shot('narrow', false);
      await page.locator('[data-kind=sleep][data-action=add]').click();
      assert.equal(await page.locator('.stat.sleep strong').evaluate(el => el.scrollWidth <= el.clientWidth + 1), true, name + ': sleep summary wraps at 320px');
      await fits('sleep form 320');
      await page.locator('#entry-form button[type=submit]').click();
      await page.locator('#modal').waitFor({ state: 'hidden' });
      await page.locator('.running').waitFor();
      await fits('active timer 320');
      await page.setViewportSize({ width: 768, height: 1024 });
      await shot('tablet', false);
      assert.equal(await page.locator('.hero-art').evaluate(el => getComputedStyle(el).animationName), 'none');
      assert.deepEqual(errors, [], name + ': JavaScript errors');
      await cleanCsp('complete');
      console.log(name + ': journal, form save/reload, weekly summary, sharing, 320px/768px layout and reduced motion passed');
      await context.close();
    } finally { await browser.close(); }
  }
}
run().catch(error => { console.error(error); process.exitCode = 1; }).finally(() => server.close());
