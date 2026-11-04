'use strict';
// probe() and modelText() on the real archived eBay item pages in app/src/test/resources/fixtures/item.
// All captures are signed out (Wayback crawler). Live auctions carry the server-rendered bid button, which is
// disabled and inside a "loading" container until eBay's scripts hydrate it (they do not run here).

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { itemFixtures, openItemPage, call } = require('../support/harness');

const fixtures = itemFixtures();
const liveAuctions = new Set([
  '20260114234459_127622190770.html.gz',
  '20260121005928_127622190770.html.gz',
  '20260427110748_127820098965.html.gz',
]);

test('the item fixtures are all present', () => {
  assert.equal(fixtures.length, 15);
  for (const fixture of fixtures) assert.ok(fixture.url, fixture.name + ' has a canonical URL');
});

for (const fixture of fixtures) {
  test('probe reads ' + fixture.name, async () => {
    const page = await openItemPage(fixture);
    const probe = call(page.window, 'probe');
    assert.equal(probe.ok, true);
    assert.equal(probe.version, '1');
    assert.equal(probe.url, fixture.url);
    assert.match(probe.title, /\| eBay/);
    assert.equal(probe.readyState, 'complete');
    assert.equal(probe.visibilityState, 'visible');
    assert.equal(probe.signedIn, false, 'GHpre.userAuth is false on every signed-out capture');
    assert.equal(probe.greetingName, null, 'the greeting name is empty when signed out');
    assert.equal(probe.hasItemModel, true);
    assert.equal(probe.layerOpen, false);
    if (liveAuctions.has(fixture.name)) {
      assert.equal(probe.bidButton.present, true);
      assert.equal(probe.bidButton.selector, '#bidBtn_btn');
      assert.equal(probe.bidButton.tag, 'a');
      assert.equal(probe.bidButton.disabled, true, 'server HTML ships the button disabled');
      assert.equal(probe.bidButton.loading, true, 'server HTML ships the container with class loading');
      assert.match(probe.bidButton.signInHref, /^https:\/\/signin\.ebay\.(co\.uk|com)\/ws\/eBayISAPI\.dll\?SignIn/);
      assert.match(probe.bidButton.label, /^(Submit bid|Place bid)$/);
    } else {
      assert.equal(probe.bidButton.present, false, 'ended and fixed-price pages have no bid button');
    }
    assert.equal(page.calls.length, 0, 'probe sends no request');
  });

  test('modelText reduces ' + fixture.name, async () => {
    const page = await openItemPage(fixture);
    const model = call(page.window, 'modelText');
    assert.equal(model.ok, true);
    const text = model.text;
    assert.equal(model.length, text.length);
    assert.ok(text.startsWith('<title>'), 'starts with the title');
    assert.ok(text.includes('"SEMANTIC_DATA":{'), 'keeps the SEMANTIC_DATA module');
    assert.ok(text.includes('"BUY_BOX":{'), 'keeps the BUY_BOX module');
    assert.equal(model.modelScripts, 1, 'one inline script carries the page model');
    assert.equal(model.jsonLdScripts, 2, 'both JSON-LD scripts are kept');
    assert.equal((text.match(/<script type="application\/ld\+json">/g) || []).length, 2);
    assert.ok(/"@type":"Product"/.test(text), 'keeps the JSON-LD Product');
    assert.ok(text.includes('<link rel="canonical" href="' + fixture.url + '">'));
    assert.ok(/<meta property="og:image" content="https:\/\/i\.ebayimg\.com\//.test(text), 'keeps og:image');
    assert.ok(/\bGHpre\s*=\s*\{/.test(text), 'keeps the header script with userAuth');
    assert.ok(!text.includes('resetCart'), 'drops unrelated header scripts');
    const outsideScripts = text.replace(/<script[^>]*>[\s\S]*?<\/script>/g, '');
    const tags = outsideScripts.match(/<[a-z]+/g) || [];
    assert.deepEqual([...new Set(tags)].sort(), ['<link', '<meta', '<title'], 'only title, meta and link outside scripts');
    assert.ok(text.length < fixture.html.length * 0.6, 'much smaller than the page: ' + text.length + ' of ' + fixture.html.length);
    if (fixture.html.includes('"STATUS_MESSAGE":{')) assert.ok(text.includes('"STATUS_MESSAGE":{'));
    if (fixture.html.includes('"AUTOREFRESH":{')) assert.ok(text.includes('"AUTOREFRESH":{'));
    if (fixture.html.includes('listingV2ById')) assert.ok(text.includes('listingV2ById'));
    if (fixture.html.includes('pixListingById')) assert.ok(text.includes('pixListingById'));
    assert.equal(page.calls.length, 0, 'modelText sends no request');
  });
}

test('modelText keeps every model and JSON-LD script byte for byte', async () => {
  const fixture = fixtures.find((candidate) => candidate.name === '20260427110748_127820098965.html.gz');
  const page = await openItemPage(fixture);
  const text = call(page.window, 'modelText').text;
  for (const script of page.window.document.querySelectorAll('script')) {
    const body = script.textContent;
    const isModel = /SEMANTIC_DATA|BUY_BOX|STATUS_MESSAGE|AUTOREFRESH|listingV2ById|pixListingById/.test(body);
    const isJsonLd = script.getAttribute('type') === 'application/ld+json';
    if (isModel || isJsonLd) assert.ok(text.includes(body), 'script kept verbatim');
  }
});

test('the title is escaped so the reduced page parses like the original', async () => {
  const fixture = fixtures.find((candidate) => candidate.name === '20260121005928_127622190770.html.gz');
  const page = await openItemPage(fixture);
  const text = call(page.window, 'modelText').text;
  assert.ok(text.startsWith("<title>IBM Model M 1391401 'Clicky' Mechanical Keyboard"), text.slice(0, 80));
});
