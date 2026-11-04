'use strict';
// Where the layer may be: inside a dialog on the item page, or (if eBay serves one) a separate /bfl/placebid page.
// The item page itself must never be read as a layer.

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { JSDOM } = require('jsdom');
const { call, installKestrel, installNetworkFakes, makeVirtualConsole, stubLayout } = require('../support/harness');

const FORM = '<input class="textbox__control" type="tel" id="s0-@placebid-section-@offer-section-@price">' +
  '<div class="place-bid-actions__submit"><button class="btn btn--fluid btn--primary" type="button">Submit bid</button></div>';

function pageAt(url, bodyHtml) {
  const dom = new JSDOM('<!doctype html><title>Place bid | eBay</title><body>' + bodyHtml + '</body>', {
    url,
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

test('a separate bid page is read as the layer', () => {
  const window = pageAt('https://www.ebay.co.uk/bfl/placebid/127820098965?currencyId=GBP', FORM);
  const layer = call(window, 'layerState');
  assert.equal(layer.ready, true);
  assert.equal(layer.container.selector, 'body (bid page)');
  assert.equal(layer.submit.label, 'Submit bid');
  assert.equal(call(window, 'typeMax', '12.50').value, '12.50');
});

test('the item page itself is never taken for a layer, even with a textbox and a bid button', () => {
  const window = pageAt('https://www.ebay.co.uk/itm/127820098965', FORM);
  const layer = call(window, 'layerState');
  assert.equal(layer.open, false);
  assert.equal(layer.ready, false);
  assert.equal(call(window, 'typeMax', '12.50').reason, 'amountFieldNotFound');
  assert.equal(call(window, 'submit', { expectValue: '12.50' }).reason, 'bidLayerClosed');
});

test('an offer dialog with a price field is not a bid layer', () => {
  const window = pageAt('https://www.ebay.co.uk/itm/127820098965',
    '<div role="dialog" aria-label="Make an offer"><input class="textbox__control" type="tel" id="offer-price">' +
    '<button class="btn btn--primary" type="button">Review offer</button><button class="btn btn--primary" type="button">Make offer</button></div>');
  const layer = call(window, 'layerState');
  assert.equal(layer.ready, false, 'no button says bid and nothing else');
  assert.deepEqual(layer.submit.rejected.map((entry) => entry.label), ['Review offer', 'Make offer']);
});

test('buy-it-now and power-bid buttons are never the submit', () => {
  const window = pageAt('https://www.ebay.co.uk/itm/127820098965',
    '<div class="placebid-redesign-wrapper"><input class="textbox__control" type="tel" id="x-@placebid-price">' +
    '<button class="btn btn--fluid btn--primary" type="button">Bid £5.57</button>' +
    '<button class="btn btn--fluid btn--primary" type="button">Buy It Now</button>' +
    '<button class="btn btn--fluid btn--primary" type="button">Cancel bid</button></div>');
  const layer = call(window, 'layerState');
  assert.equal(layer.submit.found, false);
  assert.equal(layer.ready, false);
  assert.equal(call(window, 'elementCenter', 'submit').found, false);
});
