'use strict';
// The bid layer, end to end, on the SYNTHETIC fixture (tools/jstest/fixtures/bid-layer-synthetic.html), whose
// inline script imitates eBay's client code. See that file's header for what it is built from.

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { openSyntheticLayer, call, settle, trustedTap } = require('../support/harness');

const CEILING_12_50 = { ceilingMinor: 1250, currency: 'GBP' };

async function openLayer(options) {
  const page = await openSyntheticLayer(options);
  const opened = call(page.window, 'openBidLayer');
  assert.equal(opened.ok, true, JSON.stringify(opened));
  return page;
}

function placeBidCalls(page) {
  return page.calls.filter((entry) => entry.url.indexOf('/bfl/placebid') >= 0);
}

test('probe sees a signed-in, hydrated item page', async () => {
  const page = await openSyntheticLayer();
  const probe = call(page.window, 'probe');
  assert.equal(probe.signedIn, true);
  assert.equal(probe.greetingName, 'Sam');
  assert.equal(probe.bidButton.present, true);
  assert.equal(probe.bidButton.tag, 'button');
  assert.equal(probe.bidButton.disabled, false);
  assert.equal(probe.bidButton.loading, false);
  assert.equal(probe.bidButton.signInHref, null);
  assert.equal(probe.bidButton.label, 'Submit bid');
  assert.equal(probe.layerOpen, false);
});

test('openBidLayer clicks the bid button once and layerState reads the layer', async () => {
  const page = await openLayer();
  assert.equal(page.window.__fixtureCounts.bidButtonClicks, 1);
  const layer = call(page.window, 'layerState');
  assert.equal(layer.open, true);
  assert.equal(layer.ready, true);
  assert.equal(layer.container.selector, '.placebid-redesign-wrapper');
  assert.equal(layer.input.selector, 'input[id*="@placebid-section-@offer-section-@price"]');
  assert.equal(layer.input.type, 'tel');
  assert.equal(layer.submit.label, 'Bid');
  assert.equal(layer.submit.found, true);
  assert.equal(layer.submit.enabled, false, 'eBay keeps the button disabled until an amount is typed');
  assert.equal(layer.minimumText, 'Enter £5.57 or more');
  assert.equal(layer.minimumAmountText, '£5.57');
  assert.deepEqual(layer.powerBids, ['Bid £5.57', 'Bid £6.07', 'Bid £6.57']);
  assert.equal(layer.lowestPowerBidAmountText, '£5.57');
  assert.equal(layer.interstitial, null, 'the user agreement small print is not an interstitial');
  assert.equal(layer.inlineError, null);
  assert.equal(layer.error, null);
  assert.equal(layer.signInRequired, false);
  assert.equal(call(page.window, 'openBidLayer').alreadyOpen, true, 'opening twice does not click again');
  assert.equal(page.window.__fixtureCounts.bidButtonClicks, 1);
});

test('typeMax below the minimum shows eBay\'s inline error and keeps the button disabled', async () => {
  const page = await openLayer();
  const typed = call(page.window, 'typeMax', '4');
  assert.equal(typed.ok, true);
  assert.equal(typed.value, '4');
  assert.equal(typed.inlineError, 'Enter £5.57 or more');
  assert.equal(typed.minimumAmountText, '£5.57');
  assert.equal(typed.submitEnabled, false);
});

test('typeMax types per character, replaces an earlier value and enables the button', async () => {
  const page = await openLayer();
  const input = page.window.document.querySelector('input.textbox__control');
  const seen = [];
  input.addEventListener('input', (event) => seen.push(input.value + '|' + event.inputType + '|' + event.data));
  call(page.window, 'typeMax', '4');
  seen.length = 0;
  const typed = call(page.window, 'typeMax', '12.50');
  assert.deepEqual(seen, ['|deleteContentBackward|null', '1|insertText|1', '12|insertText|2', '12.|insertText|.',
    '12.5|insertText|5', '12.50|insertText|0']);
  assert.equal(typed.value, '12.50');
  assert.equal(typed.inlineError, null);
  assert.equal(typed.submitEnabled, true);
  assert.equal(typed.submitLabel, 'Bid');
  assert.notEqual(page.window.document.activeElement, input, 'the field is left like a person leaves it');
});

test('typeMax refuses anything that is not a plain amount', async () => {
  const page = await openLayer();
  for (const text of ['', '1e5', '-1', '12.345', '1,234.50', '12 50', 'abc', null, 12.5]) {
    const typed = call(page.window, 'typeMax', text);
    assert.equal(typed.ok, false, String(text));
    assert.equal(typed.reason, 'badAmountText');
  }
  assert.equal(page.window.document.querySelector('input.textbox__control').value, '');
});

test('a DOM-event submit places one bid, and the observer records the reply with timings', async () => {
  const page = await openLayer();
  call(page.window, 'typeMax', '12.50');
  const armed = call(page.window, 'armObserver', CEILING_12_50);
  assert.deepEqual(armed, { ok: true, fetchWrapped: true, xhrWrapped: true, resourceObserver: false, ceilingMinor: 1250 });
  const submitted = call(page.window, 'submit', { expectValue: '12.50' });
  assert.equal(submitted.ok, true, JSON.stringify(submitted));
  assert.equal(submitted.dispatched, true);
  assert.equal(submitted.label, 'Bid');
  assert.equal(typeof submitted.perfTime, 'number');
  assert.equal(typeof submitted.epochTime, 'number');
  await settle(page.window);
  assert.equal(page.window.__fixtureCounts.submitClicks, 1);
  assert.equal(page.window.__fixtureCounts.powerBidClicks, 0);
  const reply = call(page.window, 'replyState');
  assert.deepEqual(reply.newNotices, ['Your current bid puts you in the lead.']);
  assert.equal(reply.pressed, true);
  assert.equal(reply.pressMethod, 'dom');
  const actions = reply.records.map((record) => record.via + ':' + record.action + ':' + record.status);
  assert.deepEqual(actions, ['xhr:trisk:200', 'fetch:confirmbid:200']);
  const confirm = reply.records[1];
  assert.deepEqual(confirm.requestPrice, { value: '12.50', currency: 'GBP' });
  assert.equal(confirm.priceVerified, true);
  assert.equal(confirm.method, 'POST');
  assert.match(confirm.url, /^\/bfl\/placebid\?action=confirmbid&modules=POWER_BID_LAYER/);
  assert.match(confirm.bodyPrefix, /"action":"confirmbid"/);
  assert.ok(confirm.finishedPerf >= confirm.startedPerf);
  assert.ok(confirm.finishedEpoch >= confirm.startedEpoch);
  assert.ok(!JSON.stringify(reply.records).includes('synthetic-srt'), 'request tokens are never recorded');
  assert.deepEqual(reply.blocked, []);
});

test('submit can only happen once per page', async () => {
  const page = await openLayer();
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'armObserver', CEILING_12_50);
  assert.equal(call(page.window, 'submit', { expectValue: '12.50' }).dispatched, true);
  await settle(page.window);
  const again = call(page.window, 'submit', { expectValue: '12.50' });
  assert.deepEqual(again, { ok: false, dispatched: false, reason: 'alreadySubmitted' });
  assert.equal(call(page.window, 'armTap', 'submit', { expectValue: '12.50' }).reason, 'alreadySubmitted');
  await settle(page.window);
  assert.equal(page.window.__fixtureCounts.submitClicks, 1);
  assert.equal(placeBidCalls(page).filter((entry) => entry.url.includes('confirmbid')).length, 1);
});

test('submit refuses when the field no longer shows the typed value, or shows an error', async () => {
  const page = await openLayer();
  call(page.window, 'typeMax', '12.50');
  assert.equal(call(page.window, 'submit', { expectValue: '12.00' }).reason, 'amountChanged');
  assert.equal(call(page.window, 'submit', {}).reason, 'expectedValueMissing');
  call(page.window, 'typeMax', '4');
  assert.equal(call(page.window, 'submit', { expectValue: '4' }).reason, 'submitDisabled');
  page.window.document.querySelector('.place-bid-actions__submit button').disabled = false;
  assert.equal(call(page.window, 'submit', { expectValue: '4' }).reason, 'inlineError');
  await settle(page.window);
  assert.equal(page.window.__fixtureCounts.submitClicks, 0);
});

test('an outbid reply is reported with eBay\'s text and the new button label', async () => {
  const page = await openLayer({ scenario: 'outbid' });
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'armObserver', CEILING_12_50);
  call(page.window, 'submit', { expectValue: '12.50' });
  await settle(page.window);
  const reply = call(page.window, 'replyState');
  assert.deepEqual(reply.newNotices, ['Another bidder is winning. Increase your bid.']);
  assert.ok(reply.addedLines.includes('Increase max bid'), JSON.stringify(reply.addedLines));
});

test('a verification step is handed to the user, never answered', async () => {
  const page = await openSyntheticLayer({ scenario: 'verification' });
  assert.equal(call(page.window, 'openBidLayer').ok, true);
  const layer = call(page.window, 'layerState');
  assert.equal(layer.ready, false);
  assert.equal(layer.input, null);
  assert.equal(layer.interstitial.kind, 'verification');
  assert.match(layer.interstitial.text, /Verify it's you/);
  assert.match(layer.interstitial.text, /phone number/);
  assert.equal(call(page.window, 'typeMax', '12.50').reason, 'amountFieldNotFound');
  assert.equal(call(page.window, 'submit', { expectValue: '12.50' }).reason, 'bidLayerClosed');
});

test('a terms step is handed to the user', async () => {
  const page = await openSyntheticLayer({ scenario: 'terms' });
  call(page.window, 'openBidLayer');
  const layer = call(page.window, 'layerState');
  assert.equal(layer.interstitial.kind, 'terms');
  assert.match(layer.interstitial.text, /I agree/);
});

test('eBay\'s "Oops" layer failure is reported as an error', async () => {
  const page = await openLayer({ scenario: 'oops' });
  const layer = call(page.window, 'layerState');
  assert.equal(layer.ready, false);
  assert.match(layer.error, /Oops\. Looks like we're having trouble connecting/);
});

test('a phone-verification notice inside a working layer blocks the bid', async () => {
  const page = await openLayer();
  const notices = page.window.document.querySelector('.place-bid-notices');
  notices.innerHTML = '<div class="inline-notice inline-notice--attention"><span class="inline-notice__main">Verify your phone number to bid on this item.</span></div>';
  const layer = call(page.window, 'layerState');
  assert.equal(layer.ready, false);
  assert.equal(layer.interstitial.kind, 'verification');
});

test('an earlier outbid notice at the top of the layer is not an amount error', async () => {
  const page = await openLayer();
  const notices = page.window.document.querySelector('.place-bid-notices');
  notices.innerHTML = '<div class="inline-notice inline-notice--attention" role="alert"><span class="inline-notice__main">You\'ve been outbid. Increase your bid.</span></div>';
  const typed = call(page.window, 'typeMax', '12.50');
  assert.equal(typed.inlineError, null);
  assert.equal(call(page.window, 'layerState').ready, true);
});

test('closeLayer closes the layer with its close button, never a bid button', async () => {
  const page = await openLayer();
  const closed = call(page.window, 'closeLayer');
  assert.equal(closed.method, 'closeButton');
  assert.equal(call(page.window, 'layerState').open, false);
  assert.equal(page.window.__fixtureCounts.submitClicks, 0);
  assert.equal(page.window.__fixtureCounts.powerBidClicks, 0);
  assert.deepEqual(call(page.window, 'closeLayer'), { ok: true, closed: true, method: 'none' });
});

test('closeLayer falls back to Escape when there is no close button', async () => {
  const page = await openLayer();
  page.window.document.querySelector('.lightbox-dialog__close').remove();
  assert.equal(call(page.window, 'closeLayer').method, 'escape');
  assert.equal(call(page.window, 'layerState').open, false);
});

test('the ceiling blocks a bid request above the maximum, whatever pressed the button', async () => {
  const page = await openLayer();
  call(page.window, 'armObserver', { ceilingMinor: 500, currency: 'GBP' });
  // A press Kestrel would never make: the "Bid £5.57" power-bid button sends 5.57, above the 5.00 ceiling.
  page.window.document.querySelector('.place-bid-actions__powerbids-wrapper button').click();
  await settle(page.window);
  const reply = call(page.window, 'replyState');
  assert.equal(reply.blocked.length, 1);
  assert.equal(reply.blocked[0].action, 'confirmbid');
  assert.deepEqual(reply.blocked[0].requestPrice, { value: '5.57', currency: 'GBP' });
  assert.match(reply.blocked[0].reason, /above the maximum/);
  assert.equal(placeBidCalls(page).filter((entry) => entry.url.includes('confirmbid')).length, 0, 'never sent');
  assert.deepEqual(reply.newNotices, ["Oops. Looks like we're having trouble connecting to our server."]);
});

test('the ceiling blocks a bid request in another currency', async () => {
  const page = await openLayer();
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'USD' });
  call(page.window, 'submit', { expectValue: '12.50' });
  await settle(page.window);
  const reply = call(page.window, 'replyState');
  assert.match(reply.blocked[0].reason, /currency GBP is not USD/);
  assert.equal(placeBidCalls(page).filter((entry) => entry.url.includes('confirmbid')).length, 0);
});

test('a bid body Kestrel cannot read is let through and marked unverified', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'armObserver', CEILING_12_50);
  await page.window.fetch('/bfl/placebid?action=confirmbid', { method: 'POST', body: 'price=99' });
  await settle(page.window);
  const reply = call(page.window, 'replyState');
  assert.equal(reply.blocked.length, 0);
  assert.equal(reply.records[0].priceVerified, false);
  assert.equal(reply.records[0].requestPrice, null);
});

test('requests other than the bid pass through the observer untouched', async () => {
  const page = await openSyntheticLayer({ respond: () => ({ status: 200, body: 'other' }) });
  call(page.window, 'armObserver', { ceilingMinor: 1, currency: 'GBP' });
  const response = await page.window.fetch('/itemmodules/1?module_groups=AUTO_REFRESH', { credentials: 'include' });
  assert.equal(await response.text(), 'other');
  assert.equal(call(page.window, 'replyState').records.length, 0);
  call(page.window, 'disarmObserver');
  assert.equal(page.window.fetch.name, 'fetch', 'disarm restores the page\'s own fetch');
});

test('elementCenter gives the CSS-pixel centre, the pixel ratio and a hit test', async () => {
  const page = await openLayer();
  const submit = call(page.window, 'elementCenter', 'submit');
  assert.deepEqual(
    { found: submit.found, x: submit.x, y: submit.y, hit: submit.hit, label: submit.label, inViewport: submit.inViewport },
    { found: true, x: 206, y: 484, hit: true, label: 'Bid', inViewport: true },
  );
  assert.equal(submit.devicePixelRatio, 1);
  const input = call(page.window, 'elementCenter', 'input');
  assert.equal(input.x, 206);
  assert.equal(input.y, 402);
  const button = call(page.window, 'elementCenter', 'bidButton');
  assert.equal(button.x, 206);
  assert.equal(button.y, 624);
  assert.equal(button.hit, false, 'the open layer covers the bid button');
  assert.equal(call(page.window, 'elementCenter', 'powerBid').found, false, 'there is no way to target a power bid');
});

test('a native tap that reaches the submit is the one press, and DOM events are then refused', async () => {
  const page = await openLayer();
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'armObserver', CEILING_12_50);
  const armed = call(page.window, 'armTap', 'submit', { expectValue: '12.50' });
  assert.equal(armed.ok, true, JSON.stringify(armed));
  assert.equal(armed.x, 206);
  assert.equal(armed.y, 484);
  const submit = page.window.document.querySelector('.place-bid-actions__submit button');
  trustedTap(page.window, submit, armed.x, armed.y);
  const result = call(page.window, 'tapResult');
  assert.equal(result.reached, true);
  assert.deepEqual(call(page.window, 'submit', { expectValue: '12.50' }), { ok: false, dispatched: false, reason: 'alreadySubmitted' });
  trustedTap(page.window, submit, armed.x, armed.y);
  await settle(page.window);
  assert.equal(page.window.__fixtureCounts.submitClicks, 1, 'a second native tap on the submit is swallowed');
  const reply = call(page.window, 'replyState');
  assert.equal(reply.pressMethod, 'native');
  assert.deepEqual(reply.newNotices, ['Your current bid puts you in the lead.']);
});

test('a DOM fallback is refused when the native tap reached the submit, even before tapResult is read', async () => {
  const page = await openLayer();
  call(page.window, 'typeMax', '12.50');
  const armed = call(page.window, 'armTap', 'submit', { expectValue: '12.50' });
  trustedTap(page.window, page.window.document.querySelector('.place-bid-actions__submit button'), armed.x, armed.y);
  assert.deepEqual(call(page.window, 'submit', { expectValue: '12.50' }), { ok: false, dispatched: false, reason: 'nativeTapReached' });
  await settle(page.window);
  assert.equal(page.window.__fixtureCounts.submitClicks, 1);
  assert.equal(call(page.window, 'replyState').pressMethod, 'native');
});

test('a native tap that misses is swallowed, then DOM events press once, and late taps are swallowed', async () => {
  const page = await openLayer();
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'armObserver', CEILING_12_50);
  call(page.window, 'armTap', 'submit', { expectValue: '12.50' });
  const document = page.window.document;
  trustedTap(page.window, document.querySelector('.place-bid-actions__powerbids-wrapper button'), 76, 322);
  trustedTap(page.window, document.getElementById('binBtn_btn_1'), 206, 684);
  const missed = call(page.window, 'tapResult');
  assert.equal(missed.reached, false);
  assert.ok(missed.swallowed >= 10, 'every event of both stray taps was swallowed');
  assert.equal(page.window.__fixtureCounts.powerBidClicks, 0);
  assert.equal(page.window.__fixtureCounts.buyItNowClicks, 0);
  const fallback = call(page.window, 'submit', { expectValue: '12.50' });
  assert.equal(fallback.dispatched, true);
  trustedTap(page.window, document.querySelector('.place-bid-actions__submit button'), 206, 484);
  await settle(page.window);
  assert.equal(page.window.__fixtureCounts.submitClicks, 1, 'the late native tap did not press a second time');
  call(page.window, 'disarmTap');
  assert.equal(call(page.window, 'tapResult').armed, false);
});

test('a native tap on the bid button opens the layer through the guard', async () => {
  const page = await openSyntheticLayer();
  const armed = call(page.window, 'armTap', 'bidButton');
  assert.equal(armed.ok, true);
  assert.deepEqual([armed.x, armed.y], [206, 624]);
  trustedTap(page.window, page.window.document.getElementById('bidBtn_btn'), armed.x, armed.y);
  assert.equal(call(page.window, 'tapResult').reached, true);
  assert.equal(call(page.window, 'openBidLayer').alreadyOpen, true);
  assert.equal(page.window.__fixtureCounts.bidButtonClicks, 1);
});

test('the touch probe swallows its tap and checks where it landed', async () => {
  const page = await openSyntheticLayer();
  const probe = call(page.window, 'armTouchProbe');
  assert.equal(probe.ok, true);
  const target = page.window.document.elementFromPoint(probe.x, probe.y) || page.window.document.body;
  let reachedPage = false;
  page.window.document.addEventListener('click', () => {
    reachedPage = true;
  });
  trustedTap(page.window, target, probe.x, probe.y);
  const result = call(page.window, 'touchProbeResult', true);
  assert.equal(result.trusted, true);
  assert.equal(result.matched, true);
  assert.equal(reachedPage, false, 'the probe tap never reaches the page');
  assert.equal(call(page.window, 'tapResult').armed, false, 'finishing the probe disarms it');
});

test('the touch probe reports a tap that landed in the wrong place', async () => {
  const page = await openSyntheticLayer();
  const probe = call(page.window, 'armTouchProbe');
  trustedTap(page.window, page.window.document.body, probe.x * 2.625, probe.y * 2.625);
  const result = call(page.window, 'touchProbeResult', true);
  assert.equal(result.trusted, true);
  assert.equal(result.matched, false);
});

test('untrusted events never count as a native tap', async () => {
  const page = await openSyntheticLayer({ trustedHook: false });
  call(page.window, 'armTouchProbe');
  page.window.document.body.dispatchEvent(new page.window.MouseEvent('click', { bubbles: true, clientX: 10, clientY: 10 }));
  assert.equal(call(page.window, 'touchProbeResult', true).trusted, false);
});

test('report collects what matched, the observer records and recent errors', async () => {
  const page = await openLayer();
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'armObserver', CEILING_12_50);
  call(page.window, 'submit', { expectValue: '12.50' });
  await settle(page.window);
  const report = call(page.window, 'report');
  assert.equal(report.matched.bidButton, '#bidBtn_btn');
  assert.equal(report.matched.layerContainer, '.placebid-redesign-wrapper');
  assert.equal(report.matched.amountInput, 'input[id*="@placebid-section-@offer-section-@price"]');
  assert.equal(report.matched.submitButton, 'div.place-bid-actions__submit > button.btn--primary');
  assert.equal(report.matched.submitLabel, 'Bid');
  assert.equal(report.press.phase, 'pressed');
  assert.equal(report.observer.records.length, 2);
  assert.equal(report.signedIn, true);
  assert.ok(Array.isArray(report.errors));
  assert.ok(JSON.stringify(report).length < 20000);
});
