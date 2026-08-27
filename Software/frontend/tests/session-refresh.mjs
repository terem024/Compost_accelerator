import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

// Run against a local Vite server. API responses are isolated browser fixtures.
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.TEST_BASE_URL || 'http://127.0.0.1:5176';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext();
const page = await context.newPage();
const fixture = {
  user: { id: 1, name: 'Session Test', email: 'test@example.com', role: 'OPERATOR' },
  sessionToken: 'local-test-token', expiresAt: new Date(Date.now() + 3600000).toISOString(),
};
let mode = 'ok';
let validations = 0;
const errors = [];
page.on('pageerror', (error) => errors.push(error.message));
await page.route('**/api/**', async (route) => {
  const path = new URL(route.request().url()).pathname;
  if (path === '/api/auth/session') {
    validations++;
    if (mode === 'offline') return route.abort('internetdisconnected');
    if (mode === 'unavailable') return route.fulfill({ status: 503, json: { message: 'Temporarily unavailable.' } });
    if (mode === 'invalid') return route.fulfill({ status: 401, json: { message: 'Session expired.' } });
    if (mode === 'malformed') return route.fulfill({ status: 200, body: 'not-json' });
    return route.fulfill({ json: { success: true, ...fixture } });
  }
  if (path === '/api/auth/logout') return route.fulfill({ status: 503, json: { message: 'Unavailable.' } });
  const data = path.endsWith('/latest') || path.endsWith('/active') ? null
    : path.endsWith('/thresholds') ? { moistureMin: 50, gasMax: 60 }
      : path === '/api/actuator-status' ? { runtimeStatuses: [] }
        : path === '/api/sensor-connection/status' ? { connectionStatus: 'CONNECTED' } : [];
  return route.fulfill({ json: data });
});

const seed = () => page.evaluate((session) => localStorage.setItem('compostAuthSession', JSON.stringify(session)), fixture);
const token = () => page.evaluate(() => JSON.parse(localStorage.getItem('compostAuthSession'))?.sessionToken);
const ready = () => page.waitForFunction(() => !document.querySelector('.app-loading'));
try {
  await page.goto(base);
  await seed();
  for (const route of ['/dashboard', '/prediction', '/logs', '/settings']) {
    await page.goto(base + route);
    await ready();
    const count = validations;
    await page.reload();
    await ready();
    assert.equal(new URL(page.url()).pathname, route);
    assert.equal(await token(), fixture.sessionToken);
    assert.equal(validations - count, 1, 'StrictMode must share the validation request');
    console.log('PASS refresh ' + route);
  }
  for (const failure of ['unavailable', 'offline', 'malformed']) {
    mode = failure;
    await page.goto(base + '/dashboard');
    await page.getByRole('heading', { name: 'Connection interrupted' }).waitFor();
    assert.equal(await token(), fixture.sessionToken);
    assert.equal(await page.getByRole('heading', { name: 'Dashboard', exact: true }).count(), 0);
    for (const viewport of [{ width: 1280, height: 800 }, { width: 390, height: 844 }]) {
      await page.setViewportSize(viewport);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
      if (failure === 'unavailable') await page.screenshot({ path: join(tmpdir(), `compost-session-recovery-${viewport.width}.png`) });
    }
    mode = 'ok';
    await page.getByRole('button', { name: 'Try again' }).click();
    await ready();
    assert.equal(new URL(page.url()).pathname, '/dashboard');
    console.log('PASS recover without logout: ' + failure);
  }
  mode = 'invalid';
  await page.reload();
  await page.waitForURL(base + '/');
  assert.equal(await token(), undefined);
  console.log('PASS rejected session returns to login');

  mode = 'ok';
  await seed();
  await page.goto(base + '/settings');
  await ready();
  await page.getByRole('button', { name: 'Logout', exact: true }).click();
  await page.waitForURL(base + '/');
  assert.equal(await token(), undefined);
  console.log('PASS explicit logout clears session even if server is down');
  assert.deepEqual(errors, []);
} finally {
  await browser.close();
}
