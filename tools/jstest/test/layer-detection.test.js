'use strict';
// What counts as the bid layer, as a check in its way, and as a press of its submit.

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { call, openPage, openSyntheticLayer, settle } = require('../support/harness');

const ITEM = 'https://www.ebay.co.uk/itm/127820098965';
const FORM = '<input class="textbox__control" type="tel" id="x-@placebid-price" data-rect="16 380 380 44">' +
  '<div class="place-bid-actions__submit"><button class="btn btn--fluid btn--primary" type="button" data-rect="16 460 380 48">Bid</button></div>';

function trustedEvent(window, element, type) {
  const EventClass = type.startsWith('pointer') && window.PointerEvent ? window.PointerEvent : window.MouseEvent;
  const event = new EventClass(type, { bubbles: true, cancelable: true, clientX: 206, clientY: 484, view: window });
  event.__simulatedTrusted = true;
  element.dispatchEvent(event);
}

test('a submit relabelled between the finger going down and the click is not pressed', async () => {
  const page = await openSyntheticLayer();
  call(page.window, 'openBidLayer');
  call(page.window, 'typeMax', '12.50');
  call(page.window, 'armTap', 'submit', { expectValue: '12.50' });
  const submit = page.window.document.querySelector('.place-bid-actions__submit button');
  trustedEvent(page.window, submit, 'pointerdown');
  submit.textContent = 'Bid £20.00';
  ['mousedown', 'pointerup', 'mouseup', 'click'].forEach((type) => trustedEvent(page.window, submit, type));
  await settle(page.window);
  assert.equal(page.window.__fixtureCounts.submitClicks, 0);
  assert.equal(call(page.window, 'tapResult').labelChanged, true);
});

test('a code field known only by its label is a verification step', () => {
  const page = openPage(ITEM,
    '<div role="dialog" aria-modal="true"><h2>Verify your phone number</h2>' +
    '<label for="s0-1-2">Verification code</label><input class="textbox__control" type="tel" id="s0-1-2">' +
    '<button class="btn btn--primary" type="button">Confirm bid</button></div>');
  const layer = call(page.window, 'layerState');
  assert.equal(layer.ready, false);
  assert.equal(layer.interstitial.kind, 'verification');
  assert.equal(call(page.window, 'typeMax', '12.50').ok, false);
  assert.equal(page.window.document.getElementById('s0-1-2').value, '');
});

test('a phone step whose field has no telling name is still caught by its words', () => {
  const page = openPage(ITEM,
    '<div class="placebid-redesign-wrapper"><p>Verify your phone number to continue.</p>' + FORM + '</div>');
  const layer = call(page.window, 'layerState');
  assert.equal(layer.ready, false);
  assert.equal(layer.interstitial.kind, 'verification');
});

test('another dialog lying over the submit is in the way, modal or not', () => {
  const page = openPage(ITEM,
    '<div class="placebid-redesign-wrapper" data-rect="0 100 411 600">' + FORM + '</div>' +
    '<div class="lightbox-dialog" role="dialog" data-rect="0 400 411 200"><p>Choose your account</p></div>');
  const layer = call(page.window, 'layerState');
  assert.equal(layer.ready, false);
  assert.deepEqual(layer.interstitial, { kind: 'unknown', text: 'Choose your account' });
  assert.equal(call(page.window, 'submit', { expectValue: '' }).reason, 'covered');
});

test('a dialog that does not cover the submit leaves the layer usable', () => {
  const page = openPage(ITEM,
    '<div class="placebid-redesign-wrapper" data-rect="0 100 411 600">' + FORM + '</div>' +
    '<div class="lightbox-dialog" role="dialog" data-rect="0 0 411 90"><p>Extended bidding: this auction may extend.</p></div>');
  assert.equal(call(page.window, 'layerState').ready, true);
});

test('the bid form\'s own dialog inside a wrapper is the layer, not a check in its way', () => {
  const page = openPage(ITEM,
    '<div class="placebid-redesign-wrapper"><div role="dialog" aria-modal="true" aria-label="Place your bid">' + FORM +
    '<p>When you bid, you\'re committing to buy this item if you win. By bidding you agree to the terms of the User Agreement.</p>' +
    '</div></div>');
  const layer = call(page.window, 'layerState');
  assert.equal(layer.interstitial, null);
  assert.equal(layer.ready, true);
});

test('an age check is never answered with the maximum', () => {
  const page = openPage(ITEM,
    '<div role="dialog" aria-modal="true"><h2>Confirm your age</h2><label for="age">Your age</label>' +
    '<input id="age" name="age" type="number" class="textbox__control">' +
    '<button class="btn btn--primary" type="button">Confirm bid</button></div>');
  const layer = call(page.window, 'layerState');
  assert.equal(layer.ready, false);
  assert.equal(layer.input, null);
  assert.equal(layer.interstitial.kind, 'age');
  assert.equal(call(page.window, 'typeMax', '18').ok, false);
  assert.equal(page.window.document.getElementById('age').value, '');
});

test('in a dialog that is not known to be the bid layer, only a field that says it takes a bid is the amount field', () => {
  const unnamed = openPage(ITEM, '<div role="dialog"><input class="textbox__control" id="s0-1-2">' +
    '<button class="btn btn--primary" type="button">Bid</button></div>');
  assert.equal(call(unnamed.window, 'layerState').input, null);
  assert.equal(call(unnamed.window, 'typeMax', '12.50').reason, 'amountFieldNotFound');
  const labelled = openPage(ITEM, '<div role="dialog"><label for="s0-1-2">Your max bid</label>' +
    '<input class="textbox__control" id="s0-1-2"><button class="btn btn--primary" type="button">Bid</button></div>');
  assert.equal(call(labelled.window, 'layerState').ready, true);
});

test('a check eBay hosts in a frame inside its dialog is pending for the user, not a layer that did not open', () => {
  // The 2026-04 UK bid dialog keeps a section for content eBay loads from elsewhere; an identity check can arrive there.
  const page = openPage(ITEM,
    '<div class="lightbox-dialog txn-wrapper-vim__dialog" role="dialog" aria-modal="true" data-rect="0 60 411 760">' +
    '<div class="lightbox-dialog__window" data-rect="0 60 411 760">' +
    '<section class="txn-wrapper__external-content" data-rect="0 60 411 700">' +
    '<iframe src="https://pages.ebay.co.uk/verify/phone?token=secret" data-rect="0 60 411 700"></iframe>' +
    '</section></div></div>');
  const layer = call(page.window, 'layerState');
  assert.equal(layer.input, null);
  assert.ok(layer.pendingDialogText, JSON.stringify(layer));
  assert.match(layer.pendingDialogText, /pages\.ebay\.co\.uk/);
  const report = call(page.window, 'report');
  assert.deepEqual(report.frames, [{ host: 'pages.ebay.co.uk', inLayer: true }]);
  assert.ok(!JSON.stringify(report).includes('secret'), 'frame addresses are reported by host only');
});
