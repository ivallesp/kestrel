'use strict';
// The two-step bid layer seen on ebay.co.uk (2026-11-06): type the max, "Review bid", then eBay's confirm screen,
// whose "Confirm bid" is the one press. "Review bid" is never the bid, and a dry run never gets past it.

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');
const { call, installKestrel, installNetworkFakes, makeVirtualConsole, settle, stubLayout } = require('../support/harness');

const fixture = fs.readFileSync(path.join(__dirname, '..', 'fixtures', 'bid-layer-two-step.html'), 'utf8');

async function openTwoStep(scenario = 'normal') {
  let calls = null;
  const dom = new JSDOM(fixture, {
    url: 'https://www.ebay.co.uk/itm/100000000001',
    runScripts: 'dangerously',
    pretendToBeVisual: true,
    virtualConsole: makeVirtualConsole(),
    beforeParse(window) {
      window.__fixtureScenario = scenario;
      window.__kestrelTestHooks = { isTrusted: (event) => event.__simulatedTrusted === true };
      calls = installNetworkFakes(window, () => ({ status: 200, body: '{}' }));
      stubLayout(window, [0, 0, 100, 20]);
    },
  });
  if (dom.window.document.readyState !== 'complete') {
    await new Promise((resolve) => dom.window.addEventListener('load', resolve, { once: true }));
  }
  installKestrel(dom.window);
  return { window: dom.window, calls };
}

function confirmCalls(page) {
  return page.calls.filter((entry) => entry.url.indexOf('action=confirmbid') >= 0);
}

async function typeAndReview(page, amount = '12.50') {
  call(page.window, 'openBidLayer');
  await settle(page.window);
  const typed = call(page.window, 'typeMax', amount);
  const reviewed = call(page.window, 'pressReview', { expectValue: amount, documentToken: typed.documentToken });
  return { typed, reviewed };
}

test('the entry step is ready on "Review bid", which is never taken for the bid button', async () => {
  const page = await openTwoStep();
  call(page.window, 'openBidLayer');
  await settle(page.window);
  const before = call(page.window, 'layerState');
  assert.equal(before.ready, true);
  assert.equal(before.submit.found, false);
  assert.equal(before.review.found, true);
  assert.equal(before.review.enabled, false, 'disabled until an amount is typed');
  call(page.window, 'typeMax', '12.50');
  assert.equal(call(page.window, 'layerState').review.enabled, true);
});

test('"Review bid" is pressed only for the amount in the field of this document', async () => {
  const page = await openTwoStep();
  call(page.window, 'openBidLayer');
  await settle(page.window);
  const typed = call(page.window, 'typeMax', '12.50');
  assert.equal(call(page.window, 'pressReview', { expectValue: '13.00', documentToken: typed.documentToken }).reason,
    'amountChanged');
  assert.equal(call(page.window, 'pressReview', { expectValue: '12.50', documentToken: 'another' }).reason,
    'documentChanged');
  assert.equal(page.window.__fixtureCounts.reviewClicks, 0);
  assert.equal(call(page.window, 'pressReview', { expectValue: '12.50', documentToken: typed.documentToken }).ok, true);
  assert.equal(page.window.__fixtureCounts.reviewClicks, 1);
});

test('after "Review bid" the confirm screen is ready, shows the amount, and nothing was bid', async () => {
  const page = await openTwoStep();
  await typeAndReview(page);
  await settle(page.window);
  const confirm = call(page.window, 'layerState');
  assert.equal(confirm.reviewed, true);
  assert.equal(confirm.amountShown, true);
  assert.equal(confirm.submit.found, true);
  assert.equal(confirm.submit.label, 'Confirm bid');
  assert.equal(confirm.ready, true);
  assert.equal(page.window.__fixtureCounts.confirmClicks, 0);
  assert.equal(confirmCalls(page).length, 0);
});

test('a dry run holds every bid request, even if the confirm button were pressed', async () => {
  const page = await openTwoStep();
  call(page.window, 'armObserver', { hold: true });
  const { typed } = await typeAndReview(page);
  await settle(page.window);
  const pressed = call(page.window, 'submit', { expectValue: '12.50', documentToken: typed.documentToken });
  assert.equal(pressed.dispatched, true);
  await settle(page.window);
  assert.equal(confirmCalls(page).length, 0, 'the hold stopped the bid request');
  assert.equal(call(page.window, 'replyState').blocked.length, 1);
});

test('the one press on the confirm screen sends the bid for the reviewed amount', async () => {
  const page = await openTwoStep();
  call(page.window, 'armObserver', { hold: true });
  const { typed } = await typeAndReview(page);
  await settle(page.window);
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false });
  const pressed = call(page.window, 'submit', { expectValue: '12.50', documentToken: typed.documentToken });
  assert.equal(pressed.dispatched, true);
  assert.equal(pressed.label, 'Confirm bid');
  await settle(page.window);
  assert.equal(confirmCalls(page).length, 1);
  assert.equal(JSON.parse(confirmCalls(page)[0].body).price.value, '12.50');
  assert.equal(call(page.window, 'submit', { expectValue: '12.50', documentToken: typed.documentToken }).dispatched, false,
    'one press per document');
});

test('a confirm screen that shows another amount is never pressed', async () => {
  const page = await openTwoStep('confirmShowsAnotherAmount');
  const { typed } = await typeAndReview(page);
  await settle(page.window);
  assert.equal(call(page.window, 'layerState').amountShown, false);
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false });
  assert.equal(call(page.window, 'submit', { expectValue: '12.50', documentToken: typed.documentToken }).reason,
    'amountNotShown');
  assert.equal(page.window.__fixtureCounts.confirmClicks, 0);
});

test('without "Review bid" pressed, a field-less screen is no place to bid', async () => {
  const page = await openTwoStep();
  call(page.window, 'openBidLayer');
  await settle(page.window);
  const typed = call(page.window, 'typeMax', '12.50');
  // eBay moves to the confirm screen on its own (Kestrel never pressed review): no review record, no press.
  page.window.document.querySelector('#review').click();
  await settle(page.window);
  assert.equal(call(page.window, 'submit', { expectValue: '12.50', documentToken: typed.documentToken }).reason,
    'bidLayerClosed');
});
