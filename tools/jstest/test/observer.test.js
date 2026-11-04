'use strict';
// The watch on the bid request: what it reads, what it lets through while holding, and how page dialogs and new
// documents are seen.

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { call, openPage, openSyntheticLayer, settle } = require('../support/harness');

const PRICE_12_50 = JSON.stringify({ price: { currency: 'GBP', value: '12.50' } });
const PRICE_1250 = JSON.stringify({ price: { currency: 'GBP', value: '1250.00' } });
const CONFIRM_URL = 'https://www.ebay.co.uk/bfl/placebid?action=confirmbid';

function confirmCalls(page) {
  return page.calls.filter((entry) => entry.url.indexOf('action=confirmbid') >= 0);
}

test('options changed after the call are what gets checked, so a higher body cannot slip past the ceiling', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false });
  const init = { method: 'POST' };
  const pending = page.window.fetch(new Request(CONFIRM_URL, { method: 'POST', body: PRICE_12_50 }), init);
  init.body = PRICE_1250;
  await assert.rejects(pending, /Failed to fetch/);
  assert.equal(confirmCalls(page).length, 0);
  assert.match(call(page.window, 'replyState').blocked[0].reason, /above the maximum/);
});

test('a body that reads differently each time is sent as it was checked', async () => {
  const page = await openSyntheticLayer({ respond: () => ({ status: 200, body: '{}' }) });
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false });
  let reads = 0;
  const init = { method: 'POST' };
  Object.defineProperty(init, 'body', {
    get() {
      reads += 1;
      return reads === 1 ? PRICE_12_50 : PRICE_1250;
    },
  });
  await page.window.fetch(CONFIRM_URL, init);
  const sent = confirmCalls(page);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].body, PRICE_12_50, 'the transport got the body the ceiling saw');
  assert.equal(sent[0].method, 'POST');
});

test('inherited fetch options travel unchanged', async () => {
  const page = await openSyntheticLayer({ respond: () => ({ status: 200, body: '{}' }) });
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false });
  await page.window.fetch(CONFIRM_URL, Object.create({ method: 'POST', body: PRICE_12_50 }));
  const sent = confirmCalls(page);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].method, 'POST');
  assert.equal(sent[0].body, PRICE_12_50);
  assert.equal(call(page.window, 'replyState').records[0].priceVerified, true);
});

test('a Request whose options give a null body is checked by its own body', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false });
  await assert.rejects(page.window.fetch(new Request(CONFIRM_URL, { method: 'POST', body: PRICE_1250 }), { body: null }),
    /Failed to fetch/);
  assert.equal(confirmCalls(page).length, 0);
});

test('an alert inside eBay\'s submit handler stops the bid that follows it', async () => {
  const page = await openSyntheticLayer({ scenario: 'alertOnSubmit' });
  call(page.window, 'openBidLayer');
  call(page.window, 'armObserver', { hold: true, currency: 'GBP' });
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false });
  assert.equal(call(page.window, 'submit', { expectValue: '12.50' }).dispatched, true);
  await settle(page.window);
  assert.equal(confirmCalls(page).length, 0, 'no bid left the page after the alert');
  const reply = call(page.window, 'replyState');
  assert.equal(reply.blockedSincePress.length, 1);
  assert.match(reply.blockedSincePress[0].reason, /the page showed a message first \(alert: Please acknowledge the terms/);
  const released = call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false });
  assert.equal(released.hold, true, 'a hold from a page dialog cannot be released on this document');
  assert.match(released.holdReason, /alert: Please acknowledge/);
});

test('a request stopped while waiting is not taken as the answer to the later press', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'openBidLayer');
  call(page.window, 'armObserver', { hold: true, currency: 'GBP' });
  page.window.document.querySelector('.place-bid-actions__powerbids-wrapper button').click();
  await settle(page.window);
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false });
  call(page.window, 'submit', { expectValue: '12.50' });
  await settle(page.window);
  const reply = call(page.window, 'replyState');
  assert.equal(reply.blocked.length, 1, 'the stray power-bid request was stopped');
  assert.deepEqual(reply.blockedSincePress, []);
  assert.equal(confirmCalls(page).length, 1);
});

test('the hold lets a priced request through when it does not place a bid', async () => {
  const page = await openSyntheticLayer({ respond: () => ({ status: 200, body: '{"valid":true}' }) });
  call(page.window, 'armObserver', { hold: true, currency: 'GBP' });
  const send = (url) => page.window.fetch(url, { method: 'POST', body: PRICE_12_50 }).then(() => 'sent', () => 'blocked');
  assert.equal(await send('/bfl/placebid/100000000001'), 'sent', 'a check of the typed amount, without an action');
  assert.equal(await send('/bfl/placebid?action=validate'), 'sent');
  assert.equal(await send('/bfl/placebid?action=trisk'), 'sent');
  assert.equal(await send('/bfl/placebid?action=confirmbid'), 'blocked');
  assert.equal(await send('/bfl/placebid?action=placeBid'), 'blocked');
});

test('every document gets its own token, carried by the typing and the reply', () => {
  const html = '<div class="placebid-redesign-wrapper"><input class="textbox__control" type="tel" id="x-@placebid-price">' +
    '<div class="place-bid-actions__submit"><button class="btn btn--fluid btn--primary" type="button">Bid</button></div></div>';
  const first = openPage('https://www.ebay.co.uk/itm/127820098965', html);
  const typed = call(first.window, 'typeMax', '12.50');
  assert.ok(typed.documentToken);
  assert.equal(call(first.window, 'replyState').documentToken, typed.documentToken);
  const second = openPage('https://www.ebay.co.uk/itm/127820098965', html);
  assert.notEqual(call(second.window, 'replyState').documentToken, typed.documentToken);
});
