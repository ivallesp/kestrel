'use strict';
// Properties the Kotlin side relies on: installing twice is harmless, no function throws, every function returns
// a JSON string, and autoRefresh only ever fetches eBay's AUTO_REFRESH endpoint on the page's own origin.

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { JSDOM } = require('jsdom');
const {
  autoRefreshFixture, call, installKestrel, installNetworkFakes, makeVirtualConsole, openSyntheticLayer, settle, stubLayout,
} = require('../support/harness');

const FUNCTIONS = ['probe', 'modelText', 'autoRefresh', 'autoRefreshResult', 'openBidLayer', 'layerState', 'typeMax',
  'armObserver', 'disarmObserver', 'submit', 'elementCenter', 'armTap', 'tapResult', 'disarmTap', 'armTouchProbe',
  'touchProbeResult', 'replyState', 'closeLayer', 'report'];

const AUTO_REFRESH_PATH = '/itemmodules/127820098965?module_groups=AUTO_REFRESH&viewer_item_relation=0&isSemiPersistentBin=false&upiState=notEligible&upiDelayTime=0&upiDelayReason=NotApplicable&includeFGQL=false';

function blankPage(html) {
  const dom = new JSDOM(html || '<!doctype html><title>Blank</title><body></body>', {
    url: 'https://www.ebay.co.uk/itm/127820098965',
    runScripts: 'outside-only',
    pretendToBeVisual: true,
    virtualConsole: makeVirtualConsole(),
    beforeParse(window) {
      installNetworkFakes(window, () => {
        throw new Error('no network');
      });
      stubLayout(window, [0, 0, 100, 20]);
    },
  });
  installKestrel(dom.window);
  return dom.window;
}

test('installing twice keeps the first helper and its state', async () => {
  const page = await openSyntheticLayer();
  const first = page.window.__kestrel;
  call(page.window, 'openBidLayer');
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'submit', { expectValue: '12.50' });
  installKestrel(page.window);
  assert.equal(page.window.__kestrel, first);
  assert.equal(call(page.window, 'submit', { expectValue: '12.50' }).reason, 'alreadySubmitted');
});

test('the helper is not enumerable on window and cannot be changed by the page', () => {
  const window = blankPage();
  assert.equal(Object.keys(window).includes('__kestrel'), false);
  assert.equal(Object.isFrozen(window.__kestrel), true);
  window.eval('window.__kestrel = null; window.__kestrel.probe = null;');
  assert.notEqual(window.__kestrel, null);
  assert.equal(typeof window.__kestrel.probe, 'function');
});

test('every function returns a JSON string on an empty page, whatever it is given', () => {
  const window = blankPage();
  const odd = [undefined, null, 0, -1, 'x', '', {}, [], { expectValue: 7 }, { ceilingMinor: 'a' }, true];
  for (const name of FUNCTIONS) {
    for (const argument of odd) {
      const raw = window.__kestrel[name](argument, argument);
      assert.equal(typeof raw, 'string', name);
      const parsed = JSON.parse(raw);
      assert.equal(typeof parsed.ok, 'boolean', name + ' says ok or not');
    }
  }
});

test('no function throws when the document is torn apart', () => {
  const window = blankPage('<!doctype html><title>Torn</title><body><div role="dialog"><input class="textbox__control"></div></body>');
  window.document.documentElement.removeChild(window.document.body);
  for (const name of FUNCTIONS) {
    const parsed = JSON.parse(window.__kestrel[name]('12.50'));
    assert.equal(typeof parsed.ok, 'boolean', name);
  }
});

test('an exception inside a function becomes ok false with the message, and is kept for the report', () => {
  const window = blankPage('<!doctype html><title>Broken</title><body><div role="dialog"><input class="textbox__control"></div></body>');
  const realComputedStyle = window.getComputedStyle;
  window.getComputedStyle = () => {
    throw new Error('broken');
  };
  for (const name of FUNCTIONS) {
    const parsed = JSON.parse(window.__kestrel[name]('12.50'));
    assert.equal(typeof parsed.ok, 'boolean', name);
  }
  const layer = JSON.parse(window.__kestrel.layerState());
  assert.equal(layer.ok, false);
  assert.match(layer.error, /broken/);
  assert.equal(layer.function, 'layerState');
  window.getComputedStyle = realComputedStyle;
  const report = JSON.parse(window.__kestrel.report());
  assert.ok(report.errors.some((error) => error.where === 'layerState' && /broken/.test(error.message)));
});

test('autoRefresh fetches eBay\'s own endpoint once, with credentials, and returns status and text', async () => {
  const reply = autoRefreshFixture('20260427110806_www.ebay.co.uk_127820098965.json');
  const page = await openSyntheticLayer({ respond: () => ({ status: 200, body: reply }) });
  const started = call(page.window, 'autoRefresh', AUTO_REFRESH_PATH);
  assert.equal(started.ok, true);
  const again = call(page.window, 'autoRefresh', AUTO_REFRESH_PATH);
  assert.equal(again.requestId, started.requestId, 'a second call while one is running reuses it');
  assert.equal(again.alreadyRunning, true);
  assert.equal(call(page.window, 'autoRefreshResult', started.requestId).done, false);
  await settle(page.window);
  const result = call(page.window, 'autoRefreshResult', started.requestId);
  assert.equal(result.done, true);
  assert.equal(result.status, 200);
  assert.equal(result.text, reply);
  assert.equal(result.error, null);
  assert.equal(page.calls.length, 1);
  assert.equal(page.calls[0].credentials, 'include');
  assert.equal(page.calls[0].method, 'GET');
  assert.equal(page.calls[0].url, 'https://www.ebay.co.uk' + AUTO_REFRESH_PATH);
});

test('autoRefresh reports a failed request', async () => {
  const page = await openSyntheticLayer({ respond: () => ({ status: 503, body: 'Error Page | eBay' }) });
  const started = call(page.window, 'autoRefresh', AUTO_REFRESH_PATH);
  await settle(page.window);
  const result = call(page.window, 'autoRefreshResult', started.requestId);
  assert.equal(result.status, 503);
  const failing = await openSyntheticLayer({
    respond: () => {
      throw new Error('offline');
    },
  });
  const failed = call(failing.window, 'autoRefresh', AUTO_REFRESH_PATH);
  await settle(failing.window);
  const failure = call(failing.window, 'autoRefreshResult', failed.requestId);
  assert.equal(failure.done, true);
  assert.equal(failure.status, null);
  assert.match(failure.error, /Failed to fetch/);
});

test('autoRefresh refuses any URL that is not AUTO_REFRESH on the page\'s own origin', async () => {
  const page = await openSyntheticLayer({ respond: () => ({ status: 200, body: '{}' }) });
  const refused = [
    'https://www.ebay.com' + AUTO_REFRESH_PATH,
    'http://www.ebay.co.uk' + AUTO_REFRESH_PATH,
    'https://evil.example/itemmodules/1?module_groups=AUTO_REFRESH',
    '//evil.example/itemmodules/1?module_groups=AUTO_REFRESH',
    '/bfl/placebid?action=confirmbid',
    '/itemmodules/127820098965?module_groups=BID_LAYER',
    '/itm/127820098965',
    'javascript:alert(1)',
    '',
    42,
    null,
  ];
  for (const url of refused) {
    assert.deepEqual(call(page.window, 'autoRefresh', url), { ok: false, reason: 'urlNotAllowed' }, String(url));
  }
  assert.equal(page.calls.length, 0);
  assert.equal(call(page.window, 'autoRefresh', 'https://www.ebay.co.uk' + AUTO_REFRESH_PATH).ok, true);
});

test('autoRefreshResult for an unknown request says so', () => {
  const window = blankPage();
  assert.deepEqual(call(window, 'autoRefreshResult', 99), { ok: false, reason: 'unknownRequest', requestId: 99 });
});
