'use strict';
// A WebView that is never on screen may get no animation frames. kestrel.js counts frames for two seconds after it is
// installed, and prepareForHiddenPage switches a frameless page to timer-driven frames and instant transitions.

require('../support/no-network');

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM, VirtualConsole } = require('jsdom');

const kestrelSource = fs.readFileSync(
  path.resolve(__dirname, '..', '..', '..', 'app', 'src', 'main', 'assets', 'js', 'kestrel.js'), 'utf8');

function pageWith({ frames }) {
  const dom = new JSDOM('<!doctype html><title>Item | eBay</title><body><div id="box">x</div></body>', {
    url: 'https://www.ebay.co.uk/itm/100000000001',
    runScripts: 'outside-only',
    // Without pretendToBeVisual jsdom has no requestAnimationFrame at all: the closest stand-in for a page that never
    // gets a frame.
    pretendToBeVisual: frames,
    virtualConsole: new VirtualConsole(),
  });
  dom.window.eval(kestrelSource);
  return dom.window;
}

function call(window, name) {
  return JSON.parse(window.eval('window.__kestrel.' + name + '()'));
}

const wait = (millis) => new Promise((resolve) => setTimeout(resolve, millis));

test('a page without frames gets timer frames, and transitions stay on until a dialog is stuck', async () => {
  const window = pageWith({ frames: false });
  await wait(600);
  const prepared = call(window, 'prepareForHiddenPage');
  assert.equal(prepared.applied, true);
  assert.equal(prepared.timerFrames, true);
  assert.equal(window.document.getElementById('kestrel-hidden-page'), null);
  const ran = await new Promise((resolve) => {
    window.requestAnimationFrame(() => resolve(true));
    setTimeout(() => resolve(false), 300);
  });
  assert.equal(ran, true, 'a requestAnimationFrame callback runs on the timer');
  assert.equal(call(window, 'report').environment.hiddenPageMode, true);
  window.close();
});

test('a page that gets frames keeps its own requestAnimationFrame', async () => {
  const window = pageWith({ frames: true });
  const ownFrame = window.requestAnimationFrame;
  await wait(600);
  const prepared = call(window, 'prepareForHiddenPage');
  assert.equal(prepared.timerFrames, false);
  assert.ok(prepared.frames > 0);
  assert.equal(window.requestAnimationFrame, ownFrame);
  window.close();
});

test('a dialog window that stays transparent gets transitions switched off after 1.5 s of looks', async () => {
  const window = pageWith({ frames: true });
  window.document.body.insertAdjacentHTML('beforeend',
    '<div class="lightbox-dialog" role="dialog"><div class="lightbox-dialog__window lightbox-dialog__window--animate"' +
    ' style="opacity: 0">loading</div></div>');
  call(window, 'prepareForHiddenPage');
  call(window, 'layerState');
  assert.equal(window.document.getElementById('kestrel-hidden-page'), null, 'not at the first look');
  await wait(1600);
  call(window, 'layerState');
  assert.ok(window.document.getElementById('kestrel-hidden-page'), 'switched off once it stayed transparent');
  assert.equal(call(window, 'report').environment.transitionsOff, true);
  window.close();
});

test('nothing is switched off when no dialog is transparent', async () => {
  const window = pageWith({ frames: true });
  call(window, 'prepareForHiddenPage');
  await wait(1600);
  call(window, 'layerState');
  assert.equal(window.document.getElementById('kestrel-hidden-page'), null);
  window.close();
});

test('the report carries the frame count and a bid-area copy with addresses reduced to hosts', async () => {
  const window = pageWith({ frames: true });
  window.document.body.insertAdjacentHTML('beforeend',
    '<div data-testid="x-bid-action"><a id="bidBtn_btn" href="https://www.ebay.co.uk/bfl/placebid/1?secret=1">' +
    'Submit bid</a><script>var x = 1;</script></div>');
  await wait(300);
  const report = call(window, 'report');
  assert.ok(report.environment.animationFrames > 0);
  assert.match(report.bidArea, /bidBtn_btn/);
  assert.doesNotMatch(report.bidArea, /secret=1/);
  assert.doesNotMatch(report.bidArea, /var x = 1/);
  window.close();
});
