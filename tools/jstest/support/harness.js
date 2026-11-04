'use strict';
// Shared helpers for the kestrel.js tests: loading pages into jsdom without any network, a small layout stub
// (jsdom does no layout), fake fetch/XMLHttpRequest, and calling the helper the way Kotlin does.

require('./no-network');

const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const { JSDOM, VirtualConsole } = require('jsdom');

const repositoryRoot = path.resolve(__dirname, '..', '..', '..');
const kestrelPath = path.join(repositoryRoot, 'app', 'src', 'main', 'assets', 'js', 'kestrel.js');
const itemFixtureDirectory = path.join(repositoryRoot, 'app', 'src', 'test', 'resources', 'fixtures', 'item');
const autoRefreshFixtureDirectory = path.join(repositoryRoot, 'app', 'src', 'test', 'resources', 'fixtures', 'autorefresh');
const syntheticLayerPath = path.join(__dirname, '..', 'fixtures', 'bid-layer-synthetic.html');

const kestrelSource = fs.readFileSync(kestrelPath, 'utf8');

function readGzip(file) {
  return zlib.gunzipSync(fs.readFileSync(file)).toString('utf8');
}

function canonicalUrl(html) {
  const match = /<link rel="canonical" href="([^"]+)"/.exec(html);
  return match ? match[1] : null;
}

function itemFixtures() {
  return fs.readdirSync(itemFixtureDirectory)
    .filter((name) => name.endsWith('.html.gz'))
    .sort()
    .map((name) => {
      const html = readGzip(path.join(itemFixtureDirectory, name));
      return { name, html, url: canonicalUrl(html) };
    });
}

function autoRefreshFixture(name) {
  return fs.readFileSync(path.join(autoRefreshFixtureDirectory, name), 'utf8');
}

function makeRect(x, y, width, height) {
  return { x, y, left: x, top: y, width, height, right: x + width, bottom: y + height };
}

function isHiddenInJsdom(window, element) {
  for (let node = element; node && node.nodeType === 1; node = node.parentElement) {
    if (node.hidden || window.getComputedStyle(node).display === 'none') return true;
  }
  return false;
}

// Elements with data-rect="x y width height" get that box; every other element gets defaultRect.
function stubLayout(window, defaultRect) {
  const rectOf = (element) => {
    const attribute = element.getAttribute && element.getAttribute('data-rect');
    if (attribute) {
      const [x, y, width, height] = attribute.trim().split(/\s+/).map(Number);
      return makeRect(x, y, width, height);
    }
    return makeRect(...defaultRect);
  };
  window.Element.prototype.getBoundingClientRect = function getBoundingClientRect() {
    return rectOf(this);
  };
  window.Element.prototype.getClientRects = function getClientRects() {
    const rect = rectOf(this);
    return rect.width > 0 || rect.height > 0 ? [rect] : [];
  };
  window.document.elementFromPoint = function elementFromPoint(x, y) {
    let topmost = null;
    for (const element of window.document.querySelectorAll('[data-rect]')) {
      const rect = rectOf(element);
      const inside = x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom;
      if (inside && !isHiddenInJsdom(window, element)) topmost = element;
    }
    return topmost;
  };
}

// Replaces the page's fetch and XMLHttpRequest. respond(call) returns {status, body} or throws for a network error.
function installNetworkFakes(window, respond) {
  const calls = [];
  window.fetch = function fetch(input, init) {
    const call = {
      via: 'fetch',
      url: String(typeof input === 'string' ? input : input.url),
      method: (init && init.method) || 'GET',
      body: init ? init.body : undefined,
      credentials: init ? init.credentials : undefined,
    };
    calls.push(call);
    return new window.Promise((resolve, reject) => {
      window.setTimeout(() => {
        try {
          const reply = respond(call);
          resolve(new Response(reply.body, { status: reply.status }));
        } catch (error) {
          reject(new window.TypeError('Failed to fetch'));
        }
      }, 0);
    });
  };
  class FakeXMLHttpRequest extends window.EventTarget {
    constructor() {
      super();
      this.readyState = 0;
      this.status = 0;
      this.responseText = '';
      this.response = '';
      this.responseType = '';
    }

    open(method, url) {
      this.method = method;
      this.url = String(url);
      this.readyState = 1;
    }

    setRequestHeader() {}

    abort() {}

    send(body) {
      const call = { via: 'xhr', url: this.url, method: this.method, body };
      calls.push(call);
      window.setTimeout(() => {
        let reply;
        try {
          reply = respond(call);
        } catch (error) {
          this.dispatchEvent(new window.ProgressEvent('error'));
          this.dispatchEvent(new window.ProgressEvent('loadend'));
          return;
        }
        this.status = reply.status;
        this.responseText = reply.body;
        this.response = reply.body;
        this.readyState = 4;
        this.dispatchEvent(new window.Event('readystatechange'));
        this.dispatchEvent(new window.ProgressEvent('load'));
        this.dispatchEvent(new window.ProgressEvent('loadend'));
      }, 0);
    }
  }
  window.XMLHttpRequest = FakeXMLHttpRequest;
  return calls;
}

function makeVirtualConsole() {
  const virtualConsole = new VirtualConsole();
  const messages = [];
  virtualConsole.on('jsdomError', (error) => messages.push({ kind: 'jsdomError', message: String(error && error.message) }));
  virtualConsole.on('error', (...parts) => messages.push({ kind: 'error', message: parts.map(String).join(' ') }));
  virtualConsole.on('warn', (...parts) => messages.push({ kind: 'warn', message: parts.map(String).join(' ') }));
  virtualConsole.messages = messages;
  return virtualConsole;
}

function refuseAllRequests(call) {
  throw new Error('unexpected request to ' + call.url);
}

function installKestrel(window) {
  window.eval(kestrelSource);
}

function loaded(window) {
  if (window.document.readyState === 'complete') return Promise.resolve();
  return new Promise((resolve) => window.addEventListener('load', () => resolve(), { once: true }));
}

// A real archived item page. Page scripts do not run, except the header script that defines window.GHpre,
// which the helper reads the way it would on the phone.
async function openItemPage(fixture) {
  const virtualConsole = makeVirtualConsole();
  let calls = null;
  const dom = new JSDOM(fixture.html, {
    url: fixture.url,
    runScripts: 'outside-only',
    pretendToBeVisual: true,
    virtualConsole,
    beforeParse(window) {
      calls = installNetworkFakes(window, refuseAllRequests);
      stubLayout(window, [0, 0, 100, 20]);
    },
  });
  for (const script of dom.window.document.querySelectorAll('script')) {
    if (!script.hasAttribute('src') && /\bGHpre\s*=\s*\{/.test(script.textContent)) dom.window.eval(script.textContent);
  }
  await loaded(dom.window);
  installKestrel(dom.window);
  return { dom, window: dom.window, calls, virtualConsole };
}

// The synthetic bid-layer page. Its inline script (an imitation of eBay's client code) runs.
async function openSyntheticLayer({ scenario = 'leading', respond, trustedHook = true } = {}) {
  const html = fs.readFileSync(syntheticLayerPath, 'utf8');
  const virtualConsole = makeVirtualConsole();
  let calls = null;
  const dom = new JSDOM(html, {
    url: 'https://www.ebay.co.uk/itm/100000000001',
    runScripts: 'dangerously',
    pretendToBeVisual: true,
    virtualConsole,
    beforeParse(window) {
      window.__fixtureScenario = scenario;
      if (trustedHook) window.__kestrelTestHooks = { isTrusted: (event) => event.__simulatedTrusted === true };
      calls = installNetworkFakes(window, respond || defaultBidResponder);
      stubLayout(window, [0, 0, 100, 20]);
    },
  });
  await loaded(dom.window);
  installKestrel(dom.window);
  return { dom, window: dom.window, calls, virtualConsole };
}

function defaultBidResponder(call) {
  if (call.url.indexOf('/bfl/placebid') >= 0) {
    return { status: 200, body: JSON.stringify({ action: new URL(call.url, 'https://www.ebay.co.uk').searchParams.get('action'), ok: true }) };
  }
  throw new Error('unexpected request to ' + call.url);
}

// Calls window.__kestrel.<name>(...) with JSON literal arguments, as Kotlin does, and parses the JSON reply.
function call(window, name, ...args) {
  const expression = 'window.__kestrel.' + name + '(' + args.map((arg) => JSON.stringify(arg)).join(',') + ')';
  const raw = window.eval(expression);
  if (typeof raw !== 'string') throw new Error(name + ' returned ' + typeof raw + ', not a JSON string');
  return JSON.parse(raw);
}

// Lets timers and promise callbacks run (fake network replies, the imitation eBay code reacting to them).
async function settle(window, turns = 12) {
  for (let i = 0; i < turns; i++) {
    await new Promise((resolve) => window.setTimeout(resolve, 0));
  }
}

// Simulates a native tap that reached the page as trusted events (jsdom cannot create trusted events, so the
// helper's test hook treats events carrying __simulatedTrusted as trusted).
function trustedTap(window, element, x, y) {
  for (const type of ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click']) {
    const EventClass = type.startsWith('pointer') && window.PointerEvent ? window.PointerEvent : window.MouseEvent;
    const event = new EventClass(type, { bubbles: true, cancelable: true, composed: true, clientX: x, clientY: y, view: window });
    event.__simulatedTrusted = true;
    element.dispatchEvent(event);
  }
}

module.exports = {
  autoRefreshFixture,
  call,
  itemFixtures,
  kestrelSource,
  openItemPage,
  openSyntheticLayer,
  settle,
  trustedTap,
  installKestrel,
  makeVirtualConsole,
  installNetworkFakes,
  stubLayout,
};
