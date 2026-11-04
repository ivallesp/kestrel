'use strict';
// The guards that keep the product rules when eBay's page does something unexpected: the entry and submit label
// rules, the hold on bid requests, the ceiling, the one press per document, and checks that must reach the user.

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { call, openPage, openSyntheticLayer, settle, trustedTap } = require('../support/harness');

const ITEM = 'https://www.ebay.co.uk/itm/127820098965';

function confirmCalls(page) {
  return page.calls.filter((entry) => entry.url.indexOf('action=confirmbid') >= 0);
}

test('an entry button that carries an amount is never pressed', async () => {
  const page = await openSyntheticLayer();
  const button = page.window.document.getElementById('bidBtn_btn');
  button.querySelector('.ux-call-to-action__text').textContent = 'Bid £5.57';
  assert.equal(call(page.window, 'probe').bidButton.recognised, false);
  assert.deepEqual(call(page.window, 'openBidLayer'), { ok: false, reason: 'bidButtonNotRecognised', label: 'Bid £5.57' });
  assert.equal(call(page.window, 'armTap', 'bidButton').reason, 'bidButtonNotRecognised');
  assert.equal(page.window.__fixtureCounts.bidButtonClicks, 0);
});

test('while the observer holds, no bid request leaves the page, and preparation requests still do', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'openBidLayer');
  call(page.window, 'armObserver', { hold: true, currency: 'GBP' });
  page.window.document.querySelector('.place-bid-actions__powerbids-wrapper button').click();
  await settle(page.window);
  const reply = call(page.window, 'replyState');
  assert.equal(reply.blocked.length, 1);
  assert.equal(reply.blocked[0].reason, 'Kestrel had not pressed the bid button');
  assert.equal(confirmCalls(page).length, 0);
  assert.equal(page.calls.filter((entry) => entry.url.indexOf('action=trisk') >= 0).length, 1, 'the risk check is not a bid');
});

test('only an explicit release lifts the hold, and then the ceiling applies', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'openBidLayer');
  call(page.window, 'armObserver', { hold: true, currency: 'GBP' });
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP' });
  assert.equal(call(page.window, 'armObserver', {}).hold, true, 'a ceiling alone does not release the hold');
  assert.equal(call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false }).hold, false);
  call(page.window, 'submit', { expectValue: '12.50' });
  await settle(page.window);
  assert.equal(confirmCalls(page).length, 1);
  assert.deepEqual(call(page.window, 'replyState').newNotices, ['Your current bid puts you in the lead.']);
});

test('a submit whose label changed after arming is not pressed by a native tap', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'openBidLayer');
  call(page.window, 'typeMax', '12.50');
  const armed = call(page.window, 'armTap', 'submit', { expectValue: '12.50' });
  const submit = page.window.document.querySelector('.place-bid-actions__submit button');
  submit.textContent = 'Bid £20.00';
  trustedTap(page.window, submit, armed.x, armed.y);
  const result = call(page.window, 'tapResult');
  assert.equal(result.reached, false);
  assert.equal(result.labelChanged, true);
  assert.equal(page.window.__fixtureCounts.submitClicks, 0);
});

test('the ceiling reads the body of fetch(Request) before it is sent', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false });
  const request = new Request('https://www.ebay.co.uk/bfl/placebid?action=confirmbid', {
    method: 'POST',
    body: JSON.stringify({ price: { currency: 'GBP', value: '1250.00' } }),
  });
  await assert.rejects(page.window.fetch(request), /Failed to fetch/);
  assert.equal(confirmCalls(page).length, 0);
  assert.match(call(page.window, 'replyState').blocked[0].reason, /above the maximum/);
  const allowed = new Request('https://www.ebay.co.uk/bfl/placebid?action=confirmbid', {
    method: 'POST',
    body: JSON.stringify({ price: { currency: 'GBP', value: '12.50' } }),
  });
  await page.window.fetch(allowed);
  assert.equal(confirmCalls(page).length, 1);
});

test('the ceiling rounds request amounts up, never down', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'armObserver', { ceilingMinor: 1250, currency: 'GBP', hold: false });
  const send = (value) => page.window.fetch('/bfl/placebid?action=confirmbid', {
    method: 'POST',
    body: JSON.stringify({ price: { currency: 'GBP', value } }),
  }).then(() => 'sent', () => 'blocked');
  assert.equal(await send('12.504'), 'blocked');
  assert.equal(await send('12.51'), 'blocked');
  assert.equal(await send(12.51), 'blocked');
  assert.equal(await send('12.500'), 'sent');
  assert.equal(await send('12.50'), 'sent');
  assert.equal(await send(12.5), 'sent');
  call(page.window, 'armObserver', { ceilingMinor: 1230, currency: 'GBP' });
  assert.equal(await send(12.3), 'sent', 'binary floating point does not push 12.30 over 12.30');
});

test('a native press outlives its guard, so no DOM fallback can press again', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'openBidLayer');
  call(page.window, 'typeMax', '12.50');
  const armed = call(page.window, 'armTap', 'submit', { expectValue: '12.50' });
  trustedTap(page.window, page.window.document.querySelector('.place-bid-actions__submit button'), armed.x, armed.y);
  call(page.window, 'disarmTap');
  assert.equal(call(page.window, 'tapResult').reached, true, 'the press is still known after the guard is gone');
  assert.equal(call(page.window, 'submit', { expectValue: '12.50' }).reason, 'nativeTapReached');
  await settle(page.window);
  assert.equal(page.window.__fixtureCounts.submitClicks, 1);
});

test('a submit armed for a native tap that never came is not pressed once the guard is gone', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'openBidLayer');
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'armTap', 'submit', { expectValue: '12.50' });
  call(page.window, 'disarmTap');
  assert.equal(call(page.window, 'submit', { expectValue: '12.50' }).reason, 'nativeTapUnknown');
  await settle(page.window);
  assert.equal(page.window.__fixtureCounts.submitClicks, 0);
});

test('a verification dialog with a code field is never taken for the bid layer', () => {
  const page = openPage(ITEM,
    '<div role="dialog" aria-modal="true"><h2>Verify your phone number</h2><p>Enter the 6-digit code we sent.</p>' +
    '<input class="textbox__control" type="tel" id="code" autocomplete="one-time-code">' +
    '<button class="btn btn--primary" type="button">Verify and bid</button></div>');
  const layer = call(page.window, 'layerState');
  assert.equal(layer.ready, false);
  assert.equal(layer.input, null, 'the code field is not an amount field');
  assert.equal(layer.interstitial.kind, 'verification');
  assert.equal(call(page.window, 'typeMax', '12.50').reason, 'amountFieldNotFound');
  assert.equal(page.window.document.getElementById('code').value, '');
});

test('a phone number field inside a layer means a verification step', () => {
  const page = openPage(ITEM,
    '<div class="placebid-redesign-wrapper"><input class="textbox__control" type="tel" id="x-@placebid-price">' +
    '<label>Mobile number<input type="tel" name="phoneNumber" autocomplete="tel"></label>' +
    '<div class="place-bid-actions__submit"><button class="btn btn--fluid btn--primary" type="button">Bid</button></div></div>');
  const layer = call(page.window, 'layerState');
  assert.equal(layer.ready, false);
  assert.equal(layer.interstitial.kind, 'verification');
  assert.equal(call(page.window, 'typeMax', '12.50').reason, 'interstitial');
});

test('a check nested inside the layer blocks it', () => {
  const page = openPage(ITEM,
    '<div class="placebid-redesign-wrapper"><input class="textbox__control" type="tel" id="x-@placebid-price">' +
    '<div class="place-bid-actions__submit"><button class="btn btn--fluid btn--primary" type="button">Bid</button></div>' +
    '<div role="dialog"><p>Verify your phone number to keep bidding.</p></div></div>');
  const layer = call(page.window, 'layerState');
  assert.equal(layer.ready, false);
  assert.equal(layer.interstitial.kind, 'verification');
});

test('an unknown modal over the layer is handed to the user; a popover is not in the way', () => {
  const layerHtml = '<div class="placebid-redesign-wrapper"><input class="textbox__control" type="tel" id="x-@placebid-price">' +
    '<div class="place-bid-actions__submit"><button class="btn btn--fluid btn--primary" type="button">Bid</button></div></div>';
  const modal = openPage(ITEM, layerHtml + '<div role="dialog" aria-modal="true"><p>Choose your account</p></div>');
  const blocked = call(modal.window, 'layerState');
  assert.equal(blocked.ready, false);
  assert.equal(blocked.interstitial.kind, 'unknown');
  assert.equal(blocked.interstitial.text, 'Choose your account');
  const popover = openPage(ITEM, layerHtml + '<div role="dialog"><p>Extended bidding: this auction may extend.</p></div>');
  assert.equal(call(popover.window, 'layerState').ready, true);
});

test('payment small print does not block, an actual payment step does', () => {
  const layerWith = (notice) => '<div class="placebid-redesign-wrapper"><div class="inline-notice"><span>' + notice + '</span></div>' +
    '<input class="textbox__control" type="tel" id="x-@placebid-price">' +
    '<div class="place-bid-actions__submit"><button class="btn btn--fluid btn--primary" type="button">Bid</button></div></div>';
  const small = openPage(ITEM, layerWith('We will use your saved payment method if you win.'));
  assert.equal(call(small.window, 'layerState').ready, true);
  const needed = openPage(ITEM, layerWith('Add a payment method to place your bid.'));
  const layer = call(needed.window, 'layerState');
  assert.equal(layer.ready, false);
  assert.equal(layer.interstitial.kind, 'payment');
});

test('submit labels in the supported languages are recognised, other actions in them are not', () => {
  const layerWith = (label) => '<div class="placebid-redesign-wrapper"><input class="textbox__control" type="tel" id="x-@placebid-price">' +
    '<div class="place-bid-actions__submit"><button class="btn btn--fluid btn--primary" type="button">' + label + '</button></div></div>';
  ['Bid', 'Submit bid', 'Place bid', 'Increase max bid', 'Bieten', 'Gebot bestätigen', 'Enchérir', 'Pujar', 'Bieden', 'Licytuj']
    .forEach((label) => assert.equal(call(openPage(ITEM, layerWith(label)).window, 'layerState').ready, true, label));
  ['Verify and bid', 'Send code', 'Review bid', 'Retract bid', 'Cancel bid', 'Bid history', 'Acheter', 'Comprar', 'Kup teraz',
    'Sofort-Kaufen', 'Make offer', 'Bid £5.57']
    .forEach((label) => assert.equal(call(openPage(ITEM, layerWith(label)).window, 'layerState').ready, false, label));
});

test('a press is refused on any document but the one the amount was typed in', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'openBidLayer');
  const typed = call(page.window, 'typeMax', '12.50');
  assert.equal(call(page.window, 'armTap', 'submit', { expectValue: '12.50', documentToken: 'another-document' }).reason,
    'documentChanged');
  assert.equal(call(page.window, 'submit', { expectValue: '12.50', documentToken: 'another-document' }).reason,
    'documentChanged');
  assert.equal(call(page.window, 'tapResult').documentToken, typed.documentToken);
  assert.equal(call(page.window, 'submit', { expectValue: '12.50', documentToken: typed.documentToken }).dispatched, true);
  await settle(page.window);
  assert.equal(page.window.__fixtureCounts.submitClicks, 1);
});

test('a DOM press stops when eBay turns the button into something else mid-press', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'openBidLayer');
  call(page.window, 'typeMax', '12.50');
  const submit = page.window.document.querySelector('.place-bid-actions__submit button');
  submit.addEventListener('pointerdown', () => {
    submit.textContent = 'Bid £20.00';
  });
  const pressed = call(page.window, 'submit', { expectValue: '12.50' });
  assert.equal(pressed.dispatched, true, 'events went out, so the outcome is possibly sent');
  assert.equal(pressed.partial, true);
  await settle(page.window);
  assert.equal(page.window.__fixtureCounts.submitClicks, 0, 'the click never reached the changed button');
  assert.equal(call(page.window, 'submit', { expectValue: '12.50' }).reason, 'alreadySubmitted', 'the one press is spent');
});

test('a button that eBay relabels "Submitting..." on the finger going down counts as pressed', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'openBidLayer');
  call(page.window, 'typeMax', '12.50');
  const submit = page.window.document.querySelector('.place-bid-actions__submit button');
  let sentOnPointerDown = 0;
  submit.addEventListener('pointerdown', () => {
    sentOnPointerDown += 1;
    submit.textContent = 'Submitting...';
  });
  const pressed = call(page.window, 'submit', { expectValue: '12.50' });
  assert.equal(sentOnPointerDown, 1);
  assert.equal(pressed.dispatched, true, 'never reported as not sent once the page acted');
  assert.equal(pressed.partial, true);
});
