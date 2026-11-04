/*
 * Kestrel page helper. Kotlin injects this file into eBay pages with WebView.evaluateJavascript and then
 * calls window.__kestrel.<function>(...).
 *
 * - Installing it again on the same document is harmless (one helper per version).
 * - No function throws: every function returns a JSON string with "ok" true or false.
 * - The only request it makes itself is autoRefresh: eBay's own AUTO_REFRESH endpoint, same origin,
 *   the call eBay's item page makes on its own.
 * - It never presses a power-bid button, presses the bid submit at most once per document, and blocks a bid
 *   request whose amount is above the ceiling Kestrel armed it with (the user's maximum is a hard ceiling).
 * - Waiting is done by Kotlin polling; nothing here blocks.
 */
(function () {
  'use strict';

  var VERSION = '1';
  if (window.__kestrel && window.__kestrel.version === VERSION) return;
  // Names this document: after a navigation the helper is installed afresh, with a new token and no press history.
  var DOCUMENT_TOKEN = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);

  // ------------------------------------------------------------------------------------------------
  // Selectors and texts, in the order they are tried. Each entry says where and when it was observed.
  // ------------------------------------------------------------------------------------------------
  var TABLE = {
    bidButton: [
      ['#bidBtn_btn', 'item pages, ebay.co.uk 2026-04-27 and ebay.com 2026-01-21'],
      ['[data-testid="x-bid-action"] button', 'signed-in bid capture, ebay.com 2026-04-30'],
      ['[data-testid="x-bid-action"] a', 'item page, ebay.co.uk 2026-04-27'],
    ],
    bidButtonContainer: [
      ['[data-testid="x-bid-action"]', 'item page, ebay.co.uk 2026-04-27'],
      ['.x-bid-action', 'item page, ebay.com 2026-01-14'],
    ],
    // A third entry 'generic' marks a container that is not known to be the bid layer: in one, only a field that says
    // it takes a bid or an amount (texts.amountField) is taken for the maximum.
    layerContainer: [
      ['.placebid-redesign-wrapper', 'signed-in bid capture, ebay.com 2026-04-30'],
      ['.place-bid-wrapper-ocv', 'bid tool, 2025-11-17'],
      ['#bid-offer-summary', 'bid tool, 2024-07-26'],
      ['[role="dialog"][aria-label="Place your bid"]', 'page model a11yDialogLabel, ebay.co.uk 2026-04-27 (inferred)'],
      ['.lightbox-dialog__window', 'eBay dialog markup, 2025-2026', 'generic'],
      ['.drawer-dialog__window', 'eBay mobile dialog markup, 2025-2026 (inferred)', 'generic'],
      ['.fullscreen-dialog__window', 'eBay mobile dialog markup, 2025-2026 (inferred)', 'generic'],
      ['[role="dialog"]', 'generic dialog', 'generic'],
    ],
    amountInput: [
      ['input[id*="@placebid-section-@offer-section-@price"]', 'signed-in bid capture, ebay.com 2026-04-30'],
      ['input[id*="@placebid"]', 'bid tool, 2026-10-09'],
      ['input.textbox__control[type="tel"]', 'bid tool, 2025-09'],
      ['input.textbox__control', 'bid tool, 2025-04'],
      ['input[id*="price"]', 'bid tool, 2025-11-17'],
      ['#MaxBidId', 'legacy item pages, 2017-2021'],
      ['input[name="maxbid"]', 'legacy item pages, 2017-2021'],
    ],
    submitButton: [
      ['div.place-bid-actions__submit > button.btn--primary', 'bid tool, 2025-09'],
      ['button.btn--fluid.btn--primary', 'signed-in bid capture, ebay.com 2026-04-30'],
      ['button[type="submit"]', 'bid tool, 2026-10-09'],
      ['button.btn--primary:not([disabled])', 'bid tool, 2025-11-17'],
    ],
    // Power-bid buttons are also recognised by their label ("Bid" and an amount, texts.powerBidLabel) wherever they sit.
    powerBidButtons: [
      ['.place-bid-actions__powerbids-wrapper button', 'bid tools, 2024-07-26 and 2025-11-17'],
      ['.place-bid-actions__powerbids button', 'inferred from the 2024-07-26 wrapper'],
    ],
    // The amount field's own group, where its error text lives (eBay form markup, inferred).
    amountFieldGroup: [
      ['.field', 'eBay form markup (inferred)'],
      ['.place-bid-actions__max-bid', 'inferred from the 2025 place-bid-actions layer'],
      ['[class*="max-bid"]', 'inferred'],
    ],
    closeButton: [
      ['.lightbox-dialog__close', 'eBay dialog markup, 2025-2026'],
      ['.drawer-dialog__close', 'eBay mobile dialog markup (inferred)'],
      ['.fullscreen-dialog__close', 'eBay mobile dialog markup (inferred)'],
      ['button[aria-label]', 'any labelled button whose label says close (filtered by texts.closeLabel)'],
    ],
    notices: [
      ['span.inline-notice__main', 'bid result notice, 2024-07-26'],
      ['.inline-notice', 'eBay notice markup, 2024-2026'],
      ['.section-notice', 'eBay notice markup, 2024-2026'],
      ['.page-notice', 'eBay notice markup, 2024-2026'],
      ['[role="alert"]', 'generic live region'],
      ['[role="status"]', 'generic live region'],
    ],
    inlineError: [
      ['.field__description--attention', 'eBay form markup, 2025-2026 (inferred)'],
      ['.textbox__error', 'eBay form markup (inferred)'],
      ['.inline-notice--attention', 'eBay notice markup, 2024-2026'],
      ['.inline-notice--priority', 'eBay notice markup (inferred)'],
      ['[role="alert"]', 'generic live region'],
    ],
    dialogs: [
      ['[role="dialog"]', 'generic dialog'],
      ['[role="alertdialog"]', 'generic dialog'],
      ['dialog[open]', 'generic dialog'],
      ['.lightbox-dialog', 'eBay dialog markup, 2025-2026'],
      ['.drawer-dialog', 'eBay mobile dialog markup (inferred)'],
      ['.fullscreen-dialog', 'eBay mobile dialog markup (inferred)'],
    ],
    cookieBanner: [
      ['#gdpr-banner', 'eBay consent banner (inferred)'],
      ['[id*="gdpr"]', 'eBay consent banner (inferred)'],
      ['[class*="gdpr"]', 'eBay consent banner (inferred)'],
      ['[id*="consent"]', 'consent banners (inferred)'],
    ],
    greetingName: [['#gh-ident-srvr-name', 'header greeting, ebay.co.uk 2026-04-27']],
    greetingWrap: [['#gh-ident-srvr-wrap', 'header greeting, ebay.co.uk 2026-04-27']],
    canonical: [['link[rel="canonical"]', 'item pages 2025-06 to 2026-04']],
    openGraph: [['meta[property^="og:"]', 'item pages 2025-06 to 2026-04']],
    // Inline scripts that carry the page model (bare key text; the script global's name changes between builds).
    modelKeys: [
      ['SEMANTIC_DATA', 'item pages 2025-06 to 2026-04'],
      ['BUY_BOX', 'item pages 2025-06 to 2026-04'],
      ['STATUS_MESSAGE', 'ended item pages 2025-06 to 2026-01'],
      ['AUTOREFRESH', 'live item pages 2026-01 to 2026-04'],
      ['listingV2ById', 'item pages 2025-09 to 2026-02'],
      ['pixListingById', 'item pages from 2026-04-27'],
    ],
    // The header script that sets window.GHpre (userAuth); kept so the parser can read the sign-in flag.
    signInScript: [[/\bGHpre\s*=\s*\{/, 'item page header, 2025-06 to 2026-04']],
    texts: {
      bidButtonLoadingClass: ['loading', 'server-rendered call to action, 2026-01 and 2026-04'],
      greetingUnrecognisedClass: ['gh-identity__srvr--unrec', 'header greeting script, ebay.co.uk 2026-04-27'],
      signInUrl: [/\/\/signin\.ebay\.|eBayISAPI\.dll\?SignIn/i, 'sign-in redirects and signed-out bid button, 2026-04-27'],
      bidPagePath: [/^\/bfl\/placebid\//, 'bid layer address /bfl/placebid/<item>, signed-in capture 2026-04-30'],
      // The request that places the bid is action=confirmbid; trisk and layer loads only prepare it.
      placingAction: [/confirm|place|submit/i, 'POST /bfl/placebid?action=confirmbid, signed-in capture 2026-04-30'],
      // A press on the submit button (or the item page's bid button) is only allowed when its label says bid and
      // nothing else.
      submitLabel: [/\bbid\b|\bbieten\b|\bgebot\b|\bench[e\u00e9]rir\b|\bench\u00e8re\b|\bpujar?\b|\bbieden\b|\blicytuj\b/i, 'labels Bid, Submit bid, Place bid, Increase bid, Increase max bid (2025-09 to 2026-10); German, French, Spanish, Dutch and Polish inferred'],
      // Amounts mean a power-bid button; the words mean another action (buy, offer, a review or verification step,
      // retract, cancel), in the same languages.
      submitLabelDeny: [/\d|buy|kaufen|pay\b|checkout|offer|angebot|preisvorschlag|review|retract|zur[u\u00fc]ck|cancel|abbrechen|close|history|watch|verif|code|send|acheter|offre|annuler|historique|comprar|ofert|cancelar|historial|kopen|annuleren|\bkup\b|anuluj/i, 'safety rule'],
      // An input for a verification code or a phone number (id, name, autocomplete, label or placeholder).
      checkField: [/one-time-code|\botp\b|passcode|verif|captcha|\bpin\b|\bcode\b|phone|mobile|\btel\b|\bage\b|birth|\bdob\b|bday/i, 'verification code fields #code, 2025-09; age checks; autocomplete tokens'],
      // A field that says it takes the bid amount (id, name, label, placeholder), as a generic dialog's must.
      amountField: [/bid|price|amount|\bmax|gebot|ench[e\u00e9]r|puja|bod\b|kwot|oferta|offerta/i, 'amount fields @placebid-section-@offer-section-@price, maxbid, "Your max bid"'],
      // In a layer that has the amount field, only these phrases mean a check stands in the way (small print about
      // terms, payment or identity checks is normal there).
      layerCheck: [/enter (?:the|your|a) (?:\d+[- ]digit )?(?:code|passcode|pin)\b|we(?:'ve| have)? (?:sent|texted|emailed) (?:you )?(?:a |the |your )?(?:code|text|message|verification)|(?:send|resend|get) (?:a |the |me a |new )?code\b|verify (?:your )?(?:phone|mobile)\b|confirm (?:your|you are of) age|verify it'?s you|confirm (?:it'?s|that it'?s) you|captcha|are you (?:a )?human|not a robot|verify your age|date of birth/i, 'verification steps reported 2025-07 to 2026-11'],
      powerBidLabel: [/^\s*(?:bid|bieten)\s+(\S*\d.*)$/i, 'power-bid buttons "Bid $X.XX", 2024-07-26 and 2025-11-17'],
      minimum: [
        [/\benter\s+(.{1,30}?)\s+or\s+(?:more|higher)\b/i, 'legacy too-low text "Enter $X or more", 2019; a bid tool, 2026'],
        [/\bminimum bid(?:\s+is|:)?\s+(.{1,30}?)(?:[.!]\s|[.!]?$)/i, 'inferred'],
        [/\bmindestens\s+(.{1,30}?)\s+(?:ein|bieten)\b/i, 'German, inferred'],
      ],
      closeLabel: [/\bclose\b|\bdismiss\b|\bschlie(?:ss|\u00df)en\b/i, 'dialog close buttons (aria-label "Close dialog")'],
      closeLabelDeny: [/\bbid\b|buy|pay\b|offer/i, 'safety rule'],
      layerError: [/oops|trouble connecting|something went wrong|refresh your browser|try again later/i, 'bid call to action errorMessage, 2026-01 and 2026-04'],
      // Interstitials that need the user. Checked in this order.
      interstitials: [
        ['verification', /verif|confirm (?:it'?s|that it'?s) you|security (?:check|code|step|measure)|one[- ]time (?:code|pass)|enter (?:the|your|a) (?:\d+-digit )?code|we (?:sent|texted|emailed)|captcha|are you (?:a )?human|not a robot/i, 'phone verification reports 2025-07; eBay help 2026-11-04'],
        ['phone', /\bphone\b|mobile number|text message|\bsms\b/i, 'phone verification for bidding, reported 2025-07-14'],
        ['age', /\bage\b|18 or (?:over|older)|over 18|date of birth|age[- ]restricted/i, 'UK age-restricted items, eBay help 2025-08-20'],
        ['payment', /add (?:a )?(?:payment method|card|postage address|shipping address|delivery address)|(?:choose|select|enter|update|confirm) (?:a |your )?(?:payment method|card details|postage address|shipping address|delivery address)|payment method (?:is )?(?:required|needed)|(?:no|missing) (?:payment method|postage address|shipping address)/i, 'eBay help 2026-11-04: payment method and postage address prompts'],
        ['signIn', /\bsign[ -]?in\b|\blog[ -]?in\b|\bpassword\b/i, 'sign-in prompts 2025-2026'],
        ['terms', /\bterms\b|user agreement|\bi agree\b|\baccept\b|disclaimer|acknowledg/i, 'bid disclaimers (age, customs, adult items), sniper status lists 2026'],
      ],
      // Kinds that block the bid even when they show up as a notice inside a working layer (terms do not:
      // a layer may carry small print about the user agreement).
      blockingNoticeKinds: [['verification', 'phone', 'age', 'payment', 'signIn'], 'safety rule'],
    },
  };

  var PLACE_BID_PATH = '/bfl/placebid';
  var AUTO_REFRESH_PATH = '/itemmodules/';
  var BODY_PREFIX_LIMIT = 4096;
  var AUTO_REFRESH_TEXT_LIMIT = 1048576;
  var AUTO_REFRESH_TIMEOUT_MILLIS = 15000;
  var GUARD_AUTO_DISARM_MILLIS = 10000;
  var PRESS_SEQUENCE_MILLIS = 1000;
  var PROBE_TOLERANCE_CSS_PX = 4;
  var REPORT_TEXT_LIMIT = 400;
  var GUARDED_EVENTS = ['pointerdown', 'pointerup', 'pointercancel', 'touchstart', 'touchend', 'touchcancel',
    'mousedown', 'mouseup', 'click', 'dblclick', 'contextmenu'];
  var PRESS_START_EVENTS = { pointerdown: true, touchstart: true, mousedown: true, click: true };
  var SKIPPED_TEXT_TAGS = { SCRIPT: true, STYLE: true, NOSCRIPT: true, TEMPLATE: true, SVG: true, svg: true };
  var BLOCK_TAGS = { DIV: true, P: true, LI: true, UL: true, OL: true, H1: true, H2: true, H3: true, H4: true,
    H5: true, H6: true, SECTION: true, ARTICLE: true, HEADER: true, FOOTER: true, BR: true, TR: true, TABLE: true,
    FORM: true, BUTTON: true, DIALOG: true };

  // Tests replace the trust check because jsdom cannot create trusted events. Pages never define this.
  var hooks = window.__kestrelTestHooks || {};
  var isTrustedEvent = typeof hooks.isTrusted === 'function' ? hooks.isTrusted : function (event) {
    return event.isTrusted === true;
  };

  var nativeFetch = typeof window.fetch === 'function' ? window.fetch : null;

  var state = {
    errors: [],
    observer: { armed: false, ceiling: null, hold: false, dialogHoldReason: null, dialogs: [], dialogWraps: [],
      records: [], blocked: [], fetchOriginal: null, fetchWrapper: null,
      xhrOpenOriginal: null, xhrSendOriginal: null, xhrOpenWrapper: null, xhrSendWrapper: null,
      resourceObserver: null, xhrUrls: typeof WeakMap === 'function' ? new WeakMap() : null },
    press: { phase: 'idle', method: null, label: null, perfTime: null, epochTime: null, baseline: null },
    guard: null,
    probe: null,
    autoRefresh: { counter: 0, inFlightId: null, results: {} },
  };

  // ------------------------------------------------------------------------------------------------
  // Small helpers
  // ------------------------------------------------------------------------------------------------

  function now() {
    return window.performance && typeof window.performance.now === 'function' ? window.performance.now() : Date.now();
  }

  function recordError(where, error) {
    var message = error && error.message ? error.message : String(error);
    state.errors.push({ where: where, message: truncate(message, 300), at: Date.now() });
    if (state.errors.length > 20) state.errors.shift();
  }

  function truncate(text, limit) {
    if (text === null || text === undefined) return text;
    text = String(text);
    return text.length > limit ? text.slice(0, limit) : text;
  }

  function normalize(text) {
    if (!text) return '';
    return String(text).replace(/[\u200b-\u200d\ufeff]/g, '').replace(/[\s\u00a0\u202f]+/g, ' ').trim();
  }

  function queryAll(root, selector) {
    try {
      return Array.prototype.slice.call((root || document).querySelectorAll(selector));
    } catch (error) {
      recordError('selector ' + selector, error);
      return [];
    }
  }

  function firstRendered(root, entries) {
    for (var i = 0; i < entries.length; i++) {
      var nodes = queryAll(root, entries[i][0]);
      for (var j = 0; j < nodes.length; j++) {
        if (isRendered(nodes[j])) return { element: nodes[j], selector: entries[i][0] };
      }
    }
    return null;
  }

  function firstPresent(root, entries) {
    for (var i = 0; i < entries.length; i++) {
      var nodes = queryAll(root, entries[i][0]);
      if (nodes.length > 0) return { element: nodes[0], selector: entries[i][0] };
    }
    return null;
  }

  function isHiddenElement(element) {
    if (element.hidden) return true;
    if (element.classList && element.classList.contains('clipped')) return true;
    var style = window.getComputedStyle(element);
    return style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse' ||
      style.opacity === '0';
  }

  function isRendered(element) {
    if (!element || !element.isConnected) return false;
    for (var node = element; node && node.nodeType === 1; node = node.parentElement) {
      if (isHiddenElement(node)) return false;
    }
    var rects = element.getClientRects();
    if (!rects || rects.length === 0) return false;
    var box = element.getBoundingClientRect();
    return box.width > 1 && box.height > 1;
  }

  function visibleText(root, limit) {
    if (!root) return '';
    var parts = [];
    var length = 0;
    // Block elements start a new line; inline elements are separated by a space, since minified markup often has
    // no whitespace between them ("bid</label><span>Enter" must not read "bidEnter").
    var walker = document.createTreeWalker(root, 1 | 4, {
      acceptNode: function (node) {
        if (node.nodeType === 1) {
          if (SKIPPED_TEXT_TAGS[node.tagName] || isHiddenElement(node)) return 2;
          parts.push(BLOCK_TAGS[node.tagName] ? '\n' : ' ');
          return 3;
        }
        return 1;
      },
    });
    while (walker.nextNode()) {
      var value = walker.currentNode.nodeValue;
      if (value) {
        parts.push(value);
        length += value.length;
        if (limit && length > limit * 2) break;
      }
    }
    var text = parts.join('');
    return limit ? truncate(text, limit * 2) : text;
  }

  function visibleLines(root, limit) {
    return visibleText(root, limit).split('\n').map(normalize).filter(function (line) {
      return line.length > 0;
    });
  }

  function textOf(root, limit) {
    return truncate(visibleLines(root, limit).join(' '), limit || 100000);
  }

  function labelOf(element) {
    if (!element) return null;
    var text = textOf(element, 200);
    if (text) return text;
    return normalize(element.getAttribute('aria-label') || element.getAttribute('value') ||
      element.getAttribute('title') || '') || null;
  }

  function isEnabled(element) {
    if (!element) return false;
    if (element.disabled === true || element.hasAttribute('disabled')) return false;
    return element.getAttribute('aria-disabled') !== 'true';
  }

  function matchFirst(patterns, text) {
    for (var i = 0; i < patterns.length; i++) {
      var match = patterns[i][0].exec(text);
      if (match) return match;
    }
    return null;
  }

  function amountNumber(text) {
    if (!text) return null;
    var match = /\d[\d.,'\s\u00a0\u202f]*\d|\d/.exec(String(text));
    if (!match) return null;
    var digits = match[0].replace(/[\s\u00a0\u202f']/g, '');
    var lastSeparator = Math.max(digits.lastIndexOf('.'), digits.lastIndexOf(','));
    if (lastSeparator >= 0 && digits.length - lastSeparator - 1 <= 2) {
      digits = digits.slice(0, lastSeparator).replace(/[.,]/g, '') + '.' + digits.slice(lastSeparator + 1);
    } else {
      digits = digits.replace(/[.,]/g, '');
    }
    var value = Number(digits);
    return isFinite(value) ? value : null;
  }

  function cleanAmountText(text) {
    if (!text) return null;
    var cleaned = normalize(text).replace(/[.!:;,]+$/, '');
    return /\d/.test(cleaned) ? cleaned : null;
  }

  function pathAndQuery(url) {
    try {
      var parsed = new URL(url, location.href);
      return parsed.pathname + parsed.search;
    } catch (error) {
      return truncate(String(url), 300);
    }
  }

  function isCookieBanner(element) {
    for (var i = 0; i < TABLE.cookieBanner.length; i++) {
      try {
        if (element.closest(TABLE.cookieBanner[i][0])) return true;
      } catch (error) {
        recordError('cookie banner', error);
      }
    }
    return /\bcookies?\b/i.test(element.getAttribute('aria-label') || '');
  }

  function viewportInfo() {
    var viewport = window.visualViewport;
    var scale = viewport && viewport.scale > 0 ? viewport.scale : 1;
    return {
      scale: scale,
      offsetLeft: viewport ? viewport.offsetLeft : 0,
      offsetTop: viewport ? viewport.offsetTop : 0,
      width: viewport ? viewport.width * scale : window.innerWidth,
      height: viewport ? viewport.height * scale : window.innerHeight,
    };
  }

  function devicePixelRatio() {
    return typeof window.devicePixelRatio === 'number' && window.devicePixelRatio > 0 ? window.devicePixelRatio : 1;
  }

  // ------------------------------------------------------------------------------------------------
  // Finding things on the page
  // ------------------------------------------------------------------------------------------------

  function signInHrefOf(element) {
    var href = element && element.getAttribute('href');
    return href && TABLE.texts.signInUrl[0].test(href) ? href : null;
  }

  function findBidButton() {
    var found = firstRendered(document, TABLE.bidButton) || firstPresent(document, TABLE.bidButton);
    if (!found) return null;
    var element = found.element;
    var container = null;
    for (var i = 0; i < TABLE.bidButtonContainer.length && !container; i++) {
      try {
        container = element.closest(TABLE.bidButtonContainer[i][0]);
      } catch (error) {
        recordError('bid button container', error);
      }
    }
    var loadingClass = TABLE.texts.bidButtonLoadingClass[0];
    var label = labelOf(element);
    return {
      element: element,
      selector: found.selector,
      tag: element.tagName.toLowerCase(),
      label: label,
      // The entry button follows the submit's label rule: a button with an amount on it would place that bid.
      recognised: judgeSubmitLabel(label).recognised && !isInsidePowerBids(element),
      disabled: !isEnabled(element),
      loading: !!(container && container.classList.contains(loadingClass)) || element.classList.contains(loadingClass),
      signInHref: signInHrefOf(element),
      visible: isRendered(element),
    };
  }

  // A field for a verification code or a phone number. Never an amount field, even when it matches the selectors.
  function isCheckField(input) {
    var described = [input.id, input.getAttribute('name'), input.getAttribute('autocomplete'),
      input.getAttribute('aria-label'), input.getAttribute('placeholder'), labelTextOf(input)].join(' ');
    return TABLE.texts.checkField[0].test(described);
  }

  function saysAmount(input) {
    var described = [input.id, input.getAttribute('name'), input.getAttribute('aria-label'),
      input.getAttribute('placeholder'), labelTextOf(input)].join(' ');
    return TABLE.texts.amountField[0].test(described);
  }

  // The text of the field's own labels: <label for>, a wrapping <label>, and aria-labelledby.
  function labelTextOf(input) {
    var parts = [];
    var labels = input.labels ? Array.prototype.slice.call(input.labels) : queryAll(document, 'label').filter(function (label) {
      return (input.id && label.htmlFor === input.id) || label.contains(input);
    });
    labels.forEach(function (label) {
      parts.push(label.textContent || '');
    });
    (input.getAttribute('aria-labelledby') || '').split(/\s+/).forEach(function (id) {
      var labelledBy = id ? document.getElementById(id) : null;
      if (labelledBy) parts.push(labelledBy.textContent || '');
    });
    return normalize(parts.join(' '));
  }

  function findCheckField(container) {
    return queryAll(container, 'input').filter(function (input) {
      return isRendered(input) && isCheckField(input);
    })[0] || null;
  }

  function findInput(container) {
    for (var i = 0; i < TABLE.amountInput.length; i++) {
      var nodes = queryAll(container, TABLE.amountInput[i][0]);
      for (var j = 0; j < nodes.length; j++) {
        var node = nodes[j];
        var type = (node.getAttribute('type') || 'text').toLowerCase();
        if (type === 'hidden' || type === 'checkbox' || type === 'radio' || type === 'submit') continue;
        if (isCheckField(node)) continue;
        if (isRendered(node)) return { element: node, selector: TABLE.amountInput[i][0] };
      }
    }
    return null;
  }

  function findLayer() {
    var fallback = null;
    for (var i = 0; i < TABLE.layerContainer.length; i++) {
      var nodes = queryAll(document, TABLE.layerContainer[i][0]);
      var generic = TABLE.layerContainer[i][2] === 'generic';
      for (var j = 0; j < nodes.length; j++) {
        var node = nodes[j];
        if (!isRendered(node) || isCookieBanner(node)) continue;
        var input = findInput(node);
        // Any textbox in a dialog Kestrel cannot place (an age or account check, say) is not the maximum's field.
        if (input && generic && !saysAmount(input.element)) input = null;
        if (input) return { element: node, selector: TABLE.layerContainer[i][0], input: input };
        if (!fallback) fallback = { element: node, selector: TABLE.layerContainer[i][0], input: null };
      }
    }
    // A separate bid page instead of an in-page layer: the whole page is the layer. Only on that address, so the item
    // page's own "Submit bid" button is never taken for a layer's submit.
    if (!fallback && TABLE.texts.bidPagePath[0].test(location.pathname) && document.body) {
      var pageInput = findInput(document.body);
      return { element: document.body, selector: 'body (bid page)', input: pageInput };
    }
    return fallback;
  }

  function isInsidePowerBids(element) {
    for (var i = 0; i < TABLE.powerBidButtons.length; i++) {
      var wrapper = TABLE.powerBidButtons[i][0].replace(/\s+button$/, '');
      try {
        if (element.closest(wrapper)) return true;
      } catch (error) {
        recordError('power bids', error);
      }
    }
    return false;
  }

  function judgeSubmitLabel(label) {
    if (!label) return { recognised: false, problem: 'no label' };
    if (TABLE.texts.submitLabelDeny[0].test(label)) return { recognised: false, problem: 'label is not a plain bid' };
    if (!TABLE.texts.submitLabel[0].test(label)) return { recognised: false, problem: 'label does not say bid' };
    return { recognised: true, problem: null };
  }

  function findSubmit(container) {
    var rejected = [];
    for (var i = 0; i < TABLE.submitButton.length; i++) {
      var nodes = queryAll(container, TABLE.submitButton[i][0]);
      for (var j = 0; j < nodes.length; j++) {
        var node = nodes[j];
        if (!isRendered(node) || isInsidePowerBids(node)) continue;
        var label = labelOf(node);
        var verdict = judgeSubmitLabel(label);
        if (!verdict.recognised) {
          if (rejected.length < 5) rejected.push({ label: label, problem: verdict.problem });
          continue;
        }
        return { element: node, selector: TABLE.submitButton[i][0], label: label, enabled: isEnabled(node), rejected: rejected };
      }
    }
    return { element: null, selector: null, label: null, enabled: false, rejected: rejected };
  }

  function findPowerBids(container) {
    var seen = [];
    var bids = [];
    function add(node) {
      if (seen.indexOf(node) >= 0 || !isRendered(node)) return;
      seen.push(node);
      var label = labelOf(node);
      var match = label ? TABLE.texts.powerBidLabel[0].exec(label) : null;
      if (!match) return;
      bids.push({ element: node, label: label, amountText: cleanAmountText(match[1]) });
    }
    for (var i = 0; i < TABLE.powerBidButtons.length; i++) {
      queryAll(container, TABLE.powerBidButtons[i][0]).forEach(add);
    }
    queryAll(container, 'button').forEach(add);
    return bids;
  }

  function lowestPowerBid(bids) {
    var lowest = null;
    bids.forEach(function (bid) {
      var value = amountNumber(bid.amountText);
      if (value !== null && (lowest === null || value < lowest.value)) lowest = { value: value, bid: bid };
    });
    return lowest ? lowest.bid : null;
  }

  function findMinimum(container) {
    var lines = visibleLines(container, 4000);
    for (var i = 0; i < lines.length; i++) {
      var match = matchFirst(TABLE.texts.minimum, lines[i]);
      if (match) {
        var amount = cleanAmountText(match[1]);
        if (amount) return { text: lines[i], amountText: amount };
      }
    }
    return null;
  }

  function noticeElements(root) {
    var found = [];
    for (var i = 0; i < TABLE.notices.length; i++) {
      queryAll(root, TABLE.notices[i][0]).forEach(function (node) {
        if (!isRendered(node)) return;
        for (var k = 0; k < found.length; k++) {
          if (found[k] === node || found[k].contains(node)) return;
        }
        found = found.filter(function (other) {
          return !node.contains(other);
        });
        found.push(node);
      });
    }
    return found;
  }

  function noticeTexts(root) {
    var texts = [];
    noticeElements(root).forEach(function (node) {
      var text = textOf(node, 600);
      if (text && texts.indexOf(text) < 0) texts.push(text);
    });
    return texts;
  }

  function pageNoticeRoot() {
    return document.getElementById('mainContent') || document.body;
  }

  // The amount field's own error. Only text tied to the field counts: a notice at the top of the layer (for example
  // "You've been outbid" from an earlier bid) is not an error about the amount being typed now.
  function inlineErrorText(layer) {
    if (!layer || !layer.input) return null;
    var input = layer.input.element;
    if (input.getAttribute('aria-invalid') === 'true') {
      var ids = (input.getAttribute('aria-describedby') || '').split(/\s+/);
      for (var i = 0; i < ids.length; i++) {
        var described = ids[i] ? document.getElementById(ids[i]) : null;
        var describedText = described && isRendered(described) ? textOf(described, 300) : '';
        if (describedText) return describedText;
      }
    }
    var group = null;
    for (var k = 0; k < TABLE.amountFieldGroup.length && !group; k++) {
      try {
        group = input.closest(TABLE.amountFieldGroup[k][0]);
      } catch (error) {
        recordError('amount field group', error);
      }
    }
    if (!group || !layer.element.contains(group)) {
      group = input.parentElement && input.parentElement.parentElement ? input.parentElement.parentElement : input.parentElement;
    }
    var found = group ? firstRendered(group, TABLE.inlineError) : null;
    var text = found ? textOf(found.element, 300) : '';
    return text || null;
  }

  // The visible dialogs, outermost only unless includeNested.
  function renderedDialogs(includeNested) {
    var dialogs = [];
    TABLE.dialogs.forEach(function (entry) {
      queryAll(document, entry[0]).forEach(function (node) {
        if (dialogs.indexOf(node) < 0 && isRendered(node) && !isCookieBanner(node)) dialogs.push(node);
      });
    });
    if (includeNested) return dialogs;
    return dialogs.filter(function (node) {
      return !dialogs.some(function (other) {
        return other !== node && other.contains(node);
      });
    });
  }

  function interstitialKind(text, allowedKinds) {
    var entries = TABLE.texts.interstitials;
    for (var i = 0; i < entries.length; i++) {
      if (allowedKinds && allowedKinds.indexOf(entries[i][0]) < 0) continue;
      if (entries[i][1].test(text)) return entries[i][0];
    }
    return null;
  }

  function isSignInPage() {
    return TABLE.texts.signInUrl[0].test(location.href);
  }

  function detectInterstitial(layer) {
    var passwords = queryAll(document, 'input[type="password"]').filter(isRendered);
    if (passwords.length > 0 || isSignInPage()) {
      var holder = passwords.length > 0 ? (passwords[0].closest('form, [role="dialog"]') || document.body) : document.body;
      return { kind: 'signIn', text: truncate(textOf(holder, 600), 600) };
    }
    var layerElement = layer ? layer.element : null;
    var amountField = layer && layer.input ? layer.input.element : null;
    // Every other dialog counts, including one nested inside the layer. The layer, what holds it, and a dialog that
    // holds the amount field (the bid form itself, inside a wrapper) do not.
    var dialogs = renderedDialogs(true).filter(function (dialog) {
      if (!layerElement) return true;
      if (dialog === layerElement || dialog.contains(layerElement)) return false;
      return !(amountField && dialog.contains(amountField));
    });
    for (var i = 0; i < dialogs.length; i++) {
      var dialogText = textOf(dialogs[i], 600);
      var kind = dialogText ? interstitialKind(dialogText) : null;
      if (!kind && findCheckField(dialogs[i])) kind = 'verification';
      // A modal Kestrel cannot read is in the way: a person could not press the bid button behind it.
      if (!kind && isModal(dialogs[i])) kind = 'unknown';
      if (kind) return { kind: kind, text: truncate(dialogText, 600) };
    }
    if (layer && !layer.input) {
      var layerText = textOf(layer.element, 600);
      var layerKind = layerText ? interstitialKind(layerText) : null;
      if (!layerKind && findCheckField(layer.element)) layerKind = 'verification';
      if (layerKind) return { kind: layerKind, text: truncate(layerText, 600) };
    }
    if (layer && layer.input) {
      // A working layer may carry small print about terms or payment; only a field for a code or a phone number,
      // a check phrase, or a blocking notice means a step stands between the amount and the bid.
      var fullText = textOf(layer.element, 2000);
      if (findCheckField(layer.element) || TABLE.texts.layerCheck[0].test(fullText)) {
        return { kind: 'verification', text: truncate(fullText, 600) };
      }
      var blocking = TABLE.texts.blockingNoticeKinds[0];
      var notices = noticeTexts(layer.element);
      for (var k = 0; k < notices.length; k++) {
        var noticeKind = interstitialKind(notices[k], blocking);
        if (noticeKind) return { kind: noticeKind, text: truncate(notices[k], 600) };
      }
    }
    return null;
  }

  function isModal(dialog) {
    return dialog.getAttribute('aria-modal') === 'true' || dialog.getAttribute('role') === 'alertdialog' ||
      (dialog.tagName === 'DIALOG' && dialog.hasAttribute('open'));
  }

  // A dialog other than the one holding [element] that lies on top of it: a person could not press through it, so
  // neither does Kestrel, whatever the dialog says.
  function blockingCover(element) {
    if (typeof document.elementFromPoint !== 'function') return null;
    if (typeof element.scrollIntoView === 'function') {
      try {
        element.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' });
      } catch (error) {
        element.scrollIntoView(false);
      }
    }
    var box = element.getBoundingClientRect();
    var topmost = document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2);
    if (!topmost || topmost === element || element.contains(topmost) || isCookieBanner(topmost)) return null;
    var dialogSelector = TABLE.dialogs.map(function (entry) {
      return entry[0];
    }).join(', ');
    var coverDialog = topmost.closest(dialogSelector);
    if (!coverDialog || coverDialog.contains(element)) return null;
    return coverDialog;
  }

  function elementFor(which) {
    if (which === 'bidButton') {
      var bidButton = findBidButton();
      return bidButton ? { element: bidButton.element, selector: bidButton.selector, label: bidButton.label } : null;
    }
    var layer = findLayer();
    if (!layer) return null;
    if (which === 'input') {
      return layer.input ? { element: layer.input.element, selector: layer.input.selector, label: null } : null;
    }
    if (which === 'submit') {
      var submit = findSubmit(layer.element);
      return submit.element ? { element: submit.element, selector: submit.selector, label: submit.label } : null;
    }
    return null;
  }

  function centerOf(element) {
    if (typeof element.scrollIntoView === 'function') {
      try {
        element.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' });
      } catch (error) {
        element.scrollIntoView(false);
      }
    }
    var box = element.getBoundingClientRect();
    var centerX = box.left + box.width / 2;
    var centerY = box.top + box.height / 2;
    var viewport = viewportInfo();
    var x = (centerX - viewport.offsetLeft) * viewport.scale;
    var y = (centerY - viewport.offsetTop) * viewport.scale;
    var hit = null;
    if (typeof document.elementFromPoint === 'function') {
      var topmost = document.elementFromPoint(centerX, centerY);
      hit = !!(topmost && (topmost === element || element.contains(topmost)));
    }
    return {
      x: x,
      y: y,
      width: box.width * viewport.scale,
      height: box.height * viewport.scale,
      inViewport: x >= 0 && y >= 0 && x < viewport.width && y < viewport.height,
      hit: hit,
      devicePixelRatio: devicePixelRatio(),
    };
  }

  // ------------------------------------------------------------------------------------------------
  // Pressing things
  // ------------------------------------------------------------------------------------------------

  function dispatchPointer(element, type, base, buttons) {
    var options = Object.assign({}, base, {
      pointerId: 1, pointerType: 'touch', isPrimary: true, width: 1, height: 1,
      pressure: buttons ? 0.5 : 0, buttons: buttons,
    });
    var event = typeof window.PointerEvent === 'function' ? new window.PointerEvent(type, options)
      : new window.MouseEvent(type, options);
    element.dispatchEvent(event);
  }

  function dispatchMouse(element, type, base, buttons) {
    element.dispatchEvent(new window.MouseEvent(type, Object.assign({}, base, { buttons: buttons, detail: 1 })));
  }

  // The DOM event sequence of a tap at the element's centre. These events are untrusted; Kotlin prefers a
  // native MotionEvent when the touch probe shows native taps reach the page. When [stillValid] is given it is
  // checked before every event: eBay's handlers may re-render the button mid-press (into a power bid, say), and the
  // sequence stops before the click. Returns 'complete', 'notStarted' (nothing was dispatched) or 'partial' (some
  // events went out, and a handler may already have acted on them).
  function domPress(element, stillValid) {
    var box = element.getBoundingClientRect();
    var x = box.left + box.width / 2;
    var y = box.top + box.height / 2;
    var base = { bubbles: true, cancelable: true, composed: true, view: window, clientX: x, clientY: y,
      screenX: x, screenY: y, button: 0 };
    var steps = [
      function () {
        dispatchPointer(element, 'pointerdown', base, 1);
      },
      function () {
        dispatchMouse(element, 'mousedown', base, 1);
        try {
          element.focus({ preventScroll: true });
        } catch (error) {
          recordError('focus', error);
        }
      },
      function () {
        dispatchPointer(element, 'pointerup', base, 0);
      },
      function () {
        dispatchMouse(element, 'mouseup', base, 0);
      },
      function () {
        dispatchMouse(element, 'click', base, 0);
      },
    ];
    for (var i = 0; i < steps.length; i++) {
      if (stillValid && !stillValid()) return i === 0 ? 'notStarted' : 'partial';
      steps[i]();
    }
    return 'complete';
  }

  // The armed button still says bid and nothing else, outside the power bids, in this document.
  function pressableStill(element) {
    return function () {
      return element.isConnected && !isInsidePowerBids(element) && judgeSubmitLabel(labelOf(element)).recognised;
    };
  }

  function eventPoint(event) {
    var touch = event.changedTouches && event.changedTouches[0] ? event.changedTouches[0]
      : (event.touches && event.touches[0] ? event.touches[0] : null);
    var source = touch || event;
    return { x: typeof source.clientX === 'number' ? source.clientX : null,
      y: typeof source.clientY === 'number' ? source.clientY : null };
  }

  // One capture listener on window sees trusted taps before eBay's handlers. In "target" mode it lets through
  // only taps on the armed element and swallows the rest, so a native tap that lands anywhere else (a power-bid
  // button, Buy It Now) does nothing. In "swallow" mode (touch probe, late taps after a fallback) it swallows all.
  // The armed element must still be what it was armed as when the finger lands: eBay may re-render a node.
  function targetStillValid(guard) {
    if (!guard.target || !guard.target.isConnected || isInsidePowerBids(guard.target)) return false;
    return judgeSubmitLabel(labelOf(guard.target)).recognised;
  }

  function guardListener(event) {
    var guard = state.guard;
    if (!guard || !isTrustedEvent(event)) return;
    var point = eventPoint(event);
    var at = now();
    // One tap on the target passes: its events until its click, or for a second after it started.
    if (guard.pressStartedAt !== null && at - guard.pressStartedAt > PRESS_SEQUENCE_MILLIS) guard.pressCompleted = true;
    var onTarget = guard.mode === 'target' && !guard.pressCompleted && guard.target &&
      event.target instanceof window.Node && guard.target.contains(event.target);
    // Checked on every event of the tap: the label could change between the finger going down and the click.
    if (onTarget && !targetStillValid(guard)) {
      guard.labelChanged = true;
      guard.pressCompleted = true;
      onTarget = false;
    }
    if (guard.events.length < 40) {
      guard.events.push({ type: event.type, x: point.x, y: point.y, at: at, onTarget: onTarget });
    }
    if (onTarget) {
      if (PRESS_START_EVENTS[event.type]) {
        guard.reached = true;
        if (guard.pressStartedAt === null) guard.pressStartedAt = at;
        // The press is recorded where it outlives the guard, so no later fallback can press a second time.
        if (guard.which === 'submit' && state.press.phase === 'nativeArmed') {
          state.press.phase = 'pressed';
          state.press.method = 'native';
          state.press.perfTime = at;
          state.press.epochTime = Date.now();
        }
      }
      if (event.type === 'click') guard.pressCompleted = true;
      return;
    }
    guard.swallowed += 1;
    if (event.cancelable) event.preventDefault();
    event.stopImmediatePropagation();
  }

  function armGuard(mode, target, which) {
    disarmGuard();
    var guard = { mode: mode, target: target, which: which, events: [], reached: false, swallowed: 0,
      armedAt: now(), timer: null, pressStartedAt: null, pressCompleted: false, labelChanged: false };
    GUARDED_EVENTS.forEach(function (type) {
      window.addEventListener(type, guardListener, { capture: true, passive: false });
    });
    // The submit guard stays until Kotlin disarms it: expiring would let a late native tap through unguarded.
    if (which !== 'submit') guard.timer = window.setTimeout(disarmGuard, GUARD_AUTO_DISARM_MILLIS);
    state.guard = guard;
    return guard;
  }

  function disarmGuard() {
    var guard = state.guard;
    if (!guard) return;
    GUARDED_EVENTS.forEach(function (type) {
      window.removeEventListener(type, guardListener, { capture: true });
    });
    if (guard.timer) window.clearTimeout(guard.timer);
    state.guard = null;
    state.lastGuard = guardSummary(guard);
    // A submit armed for a native tap that never arrived may not be pressed later: an unguarded tap could still land.
    if (guard.which === 'submit' && state.press.phase === 'nativeArmed') state.press.phase = 'abandoned';
  }

  function guardSummary(guard) {
    if (!guard) return null;
    return { mode: guard.mode, which: guard.which, reached: guard.reached, swallowed: guard.swallowed,
      events: guard.events.slice(0, 20) };
  }

  function captureBaseline(layer) {
    state.press.baseline = {
      pageNotices: noticeTexts(pageNoticeRoot()),
      layerNotices: layer ? noticeTexts(layer.element) : [],
      layerLines: layer ? visibleLines(layer.element, 6000) : [],
    };
  }

  // Everything that must hold right before the submit is pressed. Returns null when it may be pressed.
  function submitProblem(layer, submit, options) {
    // The amount was typed and checked in one document; a press in any other (after a navigation) is not that bid.
    if (options && typeof options.documentToken === 'string' && options.documentToken !== DOCUMENT_TOKEN) {
      return 'documentChanged';
    }
    if (!layer || !layer.input) return 'bidLayerClosed';
    if (!submit.element) return 'submitNotFound';
    if (!submit.enabled) return 'submitDisabled';
    var expected = options && typeof options.expectValue === 'string' ? options.expectValue : null;
    if (expected === null) return 'expectedValueMissing';
    if (layer.input.element.value !== expected) return 'amountChanged';
    if (inlineErrorText(layer)) return 'inlineError';
    if (detectInterstitial(layer)) return 'interstitial';
    if (blockingCover(submit.element)) return 'covered';
    return null;
  }

  // ------------------------------------------------------------------------------------------------
  // Watching the bid request
  // ------------------------------------------------------------------------------------------------

  function isPlaceBidUrl(url) {
    return typeof url === 'string' && url.indexOf(PLACE_BID_PATH) >= 0;
  }

  function actionOf(url) {
    try {
      return new URL(url, location.href).searchParams.get('action');
    } catch (error) {
      return null;
    }
  }

  // Hundredths, rounded up: "12.504" counts as 12.51, so an amount even slightly above the ceiling is above it.
  function minorUnits(value) {
    var text = typeof value === 'number' && isFinite(value) ? String(value) : (typeof value === 'string' ? value.trim() : '');
    var match = /^(\d+)(?:\.(\d+))?$/.exec(text);
    if (!match) return null;
    var fraction = match[2] || '';
    var minor = Number(match[1]) * 100 + Number((fraction + '00').slice(0, 2));
    if (/[1-9]/.test(fraction.slice(2))) minor += 1;
    return isFinite(minor) ? minor : null;
  }

  function priceInBody(body) {
    if (typeof body !== 'string') return { known: false };
    var parsed;
    try {
      parsed = JSON.parse(body);
    } catch (error) {
      return { known: false };
    }
    if (!parsed || typeof parsed !== 'object') return { known: false };
    var price = parsed.price;
    var value = price && typeof price === 'object' ? price.value : price;
    var minor = minorUnits(value);
    if (minor === null) return { known: false };
    var currency = price && typeof price === 'object' && typeof price.currency === 'string' ? price.currency : null;
    return { known: true, minor: minor, currency: currency, value: String(value) };
  }

  // The last line of the hard ceiling: a bid request for more than the armed amount, or in another currency, is
  // never sent. A body Kestrel cannot read is let through and reported, so a format change cannot silently stop
  // every snipe; the typed value and the guarded tap already protect the amount.
  function ceilingVerdict(url, body) {
    var observer = state.observer;
    var ceiling = observer.ceiling;
    var price = priceInBody(body);
    var action = actionOf(url) || '';
    // Until Kestrel presses submit (a dry run, or the wait before the snipe), and for the rest of the document once
    // the page opened a dialog, no bid may leave the page. Other requests (layer loads, checks of the typed amount)
    // still go: the layer needs them before the press.
    var held = observer.hold || observer.dialogHoldReason !== null;
    if (held && TABLE.texts.placingAction[0].test(action)) {
      return { block: true, reason: observer.dialogHoldReason || 'Kestrel had not pressed the bid button',
        price: price.known ? price : null };
    }
    if (!ceiling || !price.known) return { block: false, price: price.known ? price : null, verified: false };
    if (price.currency && price.currency !== ceiling.currency) {
      return { block: true, reason: 'currency ' + price.currency + ' is not ' + ceiling.currency, price: price };
    }
    if (price.minor > ceiling.minor) {
      return { block: true, reason: 'amount ' + price.value + ' is above the maximum', price: price };
    }
    return { block: false, price: price, verified: true };
  }

  function startRecord(via, method, url, verdict) {
    var record = {
      via: via,
      method: method ? String(method).toUpperCase() : null,
      action: actionOf(url),
      url: pathAndQuery(url),
      requestPrice: verdict.price ? { value: verdict.price.value, currency: verdict.price.currency } : null,
      priceVerified: !!verdict.verified,
      status: null,
      bodyPrefix: null,
      error: null,
      startedPerf: now(),
      startedEpoch: Date.now(),
      finishedPerf: null,
      finishedEpoch: null,
    };
    state.observer.records.push(record);
    if (state.observer.records.length > 20) state.observer.records.shift();
    return record;
  }

  function finishRecord(record, status, body, error) {
    record.status = typeof status === 'number' ? status : null;
    record.bodyPrefix = typeof body === 'string' ? body.slice(0, BODY_PREFIX_LIMIT) : null;
    record.error = error ? truncate(String(error), 200) : null;
    record.finishedPerf = now();
    record.finishedEpoch = Date.now();
  }

  function recordBlocked(via, url, verdict) {
    state.observer.blocked.push({ via: via, action: actionOf(url), url: pathAndQuery(url), reason: verdict.reason,
      requestPrice: verdict.price ? { value: verdict.price.value, currency: verdict.price.currency } : null,
      atPerf: now(), atEpoch: Date.now() });
  }

  function wrapFetch() {
    if (typeof window.fetch !== 'function') return false;
    var original = window.fetch;
    var wrapper = function (input, init) {
      var self = this === undefined ? window : this;
      var args = arguments;
      var url = typeof input === 'string' ? input : (input && typeof input.url === 'string' ? input.url : String(input));
      if (!isPlaceBidUrl(url)) return original.apply(self, args);
      var method = (init && init.method) || (input && typeof input === 'object' && input.method) || 'GET';
      var isRequest = input && typeof input === 'object' && typeof input.clone === 'function';
      // fetch(Request) sends the Request's own body when the options give none (absent or null): read a copy first.
      if (isRequest && (!init || init.body == null)) {
        var copy;
        try {
          copy = input.clone();
        } catch (error) {
          return sendChecked(null);
        }
        return copy.text().then(sendChecked, function () {
          return sendChecked(null);
        });
      }
      return sendChecked(null);

      // Checks the body that is about to be sent, then sends the caller's own arguments in the same step: what is
      // checked is what is sent, even if the options changed while a Request's body was being read.
      function sendChecked(requestBody) {
        var optionsBody = init ? init.body : undefined;
        var fromOptions = optionsBody != null;
        var body = fromOptions ? optionsBody : requestBody;
        var verdict = ceilingVerdict(url, typeof body === 'string' ? body : null);
        if (verdict.block) {
          recordBlocked('fetch', url, verdict);
          return Promise.reject(new TypeError('Failed to fetch'));
        }
        var record = startRecord('fetch', method, url, verdict);
        var callArgs = args;
        if (init && typeof init === 'object') {
          // Native fetch reads the options again: give it the body that was checked as an own property (undefined when
          // the Request's own body was checked, so that one is sent), with every other option still read from the
          // caller's object, own or inherited.
          var forwarded = Object.create(init);
          Object.defineProperty(forwarded, 'body', { value: fromOptions ? optionsBody : undefined, enumerable: true,
            configurable: true, writable: true });
          callArgs = [input, forwarded];
        }
        var promise = original.apply(self, callArgs);
        observeResponse(promise, record);
        return promise;
      }
    };

    function observeResponse(promise, record) {
      promise.then(function (response) {
        var copy;
        try {
          copy = response.clone();
        } catch (error) {
          finishRecord(record, response.status, null, error);
          return;
        }
        copy.text().then(function (text) {
          finishRecord(record, response.status, text, null);
        }, function (error) {
          finishRecord(record, response.status, null, error);
        });
      }, function (error) {
        finishRecord(record, null, null, error);
      });
    }
    window.fetch = wrapper;
    state.observer.fetchOriginal = original;
    state.observer.fetchWrapper = wrapper;
    return true;
  }

  function wrapXhr() {
    var Request = window.XMLHttpRequest;
    if (typeof Request !== 'function' || !Request.prototype) return false;
    var proto = Request.prototype;
    var originalOpen = proto.open;
    var originalSend = proto.send;
    var urls = state.observer.xhrUrls;
    var openWrapper = function (method, url) {
      try {
        if (urls) urls.set(this, { method: method, url: String(url) });
      } catch (error) {
        recordError('xhr open', error);
      }
      return originalOpen.apply(this, arguments);
    };
    var sendWrapper = function (body) {
      var request = this;
      var opened = urls ? urls.get(request) : null;
      if (!opened || !isPlaceBidUrl(opened.url)) return originalSend.apply(request, arguments);
      var verdict = ceilingVerdict(opened.url, typeof body === 'string' ? body : null);
      if (verdict.block) {
        recordBlocked('xhr', opened.url, verdict);
        window.setTimeout(function () {
          request.dispatchEvent(new window.ProgressEvent('error'));
          request.dispatchEvent(new window.ProgressEvent('loadend'));
        }, 0);
        return undefined;
      }
      var record = startRecord('xhr', opened.method, opened.url, verdict);
      request.addEventListener('loadend', function () {
        var text = null;
        try {
          if (request.responseType === '' || request.responseType === 'text') text = request.responseText;
          else if (request.responseType === 'json') text = JSON.stringify(request.response);
        } catch (error) {
          recordError('xhr body', error);
        }
        finishRecord(record, request.status, text, request.status === 0 ? 'network error' : null);
      });
      return originalSend.apply(request, arguments);
    };
    proto.open = openWrapper;
    proto.send = sendWrapper;
    state.observer.xhrOpenOriginal = originalOpen;
    state.observer.xhrSendOriginal = originalSend;
    state.observer.xhrOpenWrapper = openWrapper;
    state.observer.xhrSendWrapper = sendWrapper;
    return true;
  }

  // Resource timing catches a bid request sent through a fetch reference eBay took before the wrap.
  function observeResources() {
    if (typeof window.PerformanceObserver !== 'function') return false;
    try {
      var observer = new window.PerformanceObserver(function (list) {
        list.getEntries().forEach(function (entry) {
          if (!isPlaceBidUrl(entry.name)) return;
          var record = {
            via: 'resource', method: null, action: actionOf(entry.name), url: pathAndQuery(entry.name),
            requestPrice: null, priceVerified: false,
            status: typeof entry.responseStatus === 'number' && entry.responseStatus > 0 ? entry.responseStatus : null,
            bodyPrefix: null, error: null, startedPerf: entry.startTime, startedEpoch: null,
            finishedPerf: entry.responseEnd, finishedEpoch: null,
          };
          state.observer.records.push(record);
          if (state.observer.records.length > 20) state.observer.records.shift();
        });
      });
      observer.observe({ type: 'resource', buffered: false });
      state.observer.resourceObserver = observer;
      return true;
    } catch (error) {
      recordError('resource observer', error);
      return false;
    }
  }

  // ------------------------------------------------------------------------------------------------
  // The API
  // ------------------------------------------------------------------------------------------------

  function probe() {
    var bidButton = findBidButton();
    var greeting = firstPresent(document, TABLE.greetingName);
    var wrap = firstPresent(document, TABLE.greetingWrap);
    var name = greeting ? normalize(greeting.element.textContent) : '';
    var unrecognised = wrap && wrap.element.classList.contains(TABLE.texts.greetingUnrecognisedClass[0]);
    var header = window.GHpre;
    var layer = findLayer();
    return {
      ok: true,
      version: VERSION,
      url: location.href,
      title: document.title,
      readyState: document.readyState,
      visibilityState: document.visibilityState || null,
      signedIn: header && typeof header.userAuth === 'boolean' ? header.userAuth : null,
      greetingName: name && !unrecognised ? name : null,
      hasItemModel: modelScripts().length > 0,
      bidButton: {
        present: !!bidButton,
        selector: bidButton ? bidButton.selector : null,
        tag: bidButton ? bidButton.tag : null,
        label: bidButton ? bidButton.label : null,
        recognised: bidButton ? bidButton.recognised : false,
        disabled: bidButton ? bidButton.disabled : false,
        loading: bidButton ? bidButton.loading : false,
        signInHref: bidButton ? bidButton.signInHref : null,
        visible: bidButton ? bidButton.visible : false,
      },
      layerOpen: !!layer,
      devicePixelRatio: devicePixelRatio(),
      viewportWidth: window.innerWidth,
      viewportHeight: window.innerHeight,
    };
  }

  function modelScripts() {
    var keys = TABLE.modelKeys.map(function (entry) {
      return entry[0];
    });
    return queryAll(document, 'script').filter(function (script) {
      if (script.hasAttribute('src')) return false;
      var type = (script.getAttribute('type') || '').toLowerCase();
      if (type && type !== 'text/javascript' && type !== 'module' && type !== 'application/javascript') return false;
      var text = script.textContent || '';
      for (var i = 0; i < keys.length; i++) {
        if (text.indexOf(keys[i]) >= 0) return true;
      }
      return false;
    });
  }

  function escapeHtml(text) {
    return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function scriptBlock(script, type) {
    var text = (script.textContent || '').replace(/<\/script/gi, '<\\/script');
    return (type ? '<script type="' + type + '">' : '<script>') + text + '</script>';
  }

  function modelText() {
    var parts = ['<title>' + escapeHtml(document.title) + '</title>'];
    queryAll(document, TABLE.openGraph[0][0]).forEach(function (meta) {
      parts.push('<meta property="' + escapeHtml(meta.getAttribute('property') || '') + '" content="' +
        escapeHtml(meta.getAttribute('content') || '') + '">');
    });
    var canonical = firstPresent(document, TABLE.canonical);
    if (canonical) parts.push('<link rel="canonical" href="' + escapeHtml(canonical.element.getAttribute('href') || '') + '">');
    var signInPattern = TABLE.signInScript[0][0];
    var models = modelScripts();
    var jsonLd = 0;
    queryAll(document, 'script').forEach(function (script) {
      if (script.hasAttribute('src')) return;
      var type = (script.getAttribute('type') || '').toLowerCase();
      if (type === 'application/ld+json') {
        parts.push(scriptBlock(script, 'application/ld+json'));
        jsonLd += 1;
      } else if (models.indexOf(script) >= 0 || signInPattern.test(script.textContent || '')) {
        parts.push(scriptBlock(script, null));
      }
    });
    var text = parts.join('\n');
    return { ok: true, url: location.href, title: document.title, text: text, modelScripts: models.length,
      jsonLdScripts: jsonLd, length: text.length };
  }

  function autoRefreshTarget(url) {
    if (typeof url !== 'string' || url.length === 0 || url.length > 2000) return null;
    var parsed;
    try {
      parsed = new URL(url, location.href);
    } catch (error) {
      return null;
    }
    if (parsed.origin !== location.origin || parsed.protocol !== 'https:') return null;
    if (parsed.pathname.indexOf(AUTO_REFRESH_PATH) !== 0) return null;
    if ((parsed.searchParams.get('module_groups') || '').indexOf('AUTO_REFRESH') < 0) return null;
    return parsed.href;
  }

  function autoRefresh(url) {
    var target = autoRefreshTarget(url);
    if (!target) return { ok: false, reason: 'urlNotAllowed' };
    var refresh = state.autoRefresh;
    if (refresh.inFlightId !== null) return { ok: true, requestId: refresh.inFlightId, alreadyRunning: true };
    var fetchFunction = nativeFetch || (typeof window.fetch === 'function' ? window.fetch : null);
    if (!fetchFunction) return { ok: false, reason: 'fetchUnavailable' };
    var id = ++refresh.counter;
    var result = { requestId: id, done: false, status: null, text: null, error: null, startedPerf: now(), elapsedMillis: null };
    refresh.results[id] = result;
    Object.keys(refresh.results).forEach(function (key) {
      if (Number(key) < id - 3) delete refresh.results[key];
    });
    refresh.inFlightId = id;
    var controller = typeof window.AbortController === 'function' ? new window.AbortController() : null;
    var timer = controller ? window.setTimeout(function () {
      controller.abort();
    }, AUTO_REFRESH_TIMEOUT_MILLIS) : null;
    function finish(status, text, error) {
      if (timer) window.clearTimeout(timer);
      result.done = true;
      result.status = status;
      result.text = typeof text === 'string' ? text.slice(0, AUTO_REFRESH_TEXT_LIMIT) : null;
      result.error = error ? truncate(String(error && error.name ? error.name + ': ' + error.message : error), 200) : null;
      result.elapsedMillis = Math.round(now() - result.startedPerf);
      if (refresh.inFlightId === id) refresh.inFlightId = null;
    }
    var options = { credentials: 'include' };
    if (controller) options.signal = controller.signal;
    try {
      fetchFunction.call(window, target, options).then(function (response) {
        return response.text().then(function (text) {
          finish(response.status, text, null);
        });
      }).catch(function (error) {
        finish(null, null, error);
      });
    } catch (error) {
      finish(null, null, error);
    }
    return { ok: true, requestId: id };
  }

  function autoRefreshResult(requestId) {
    var result = state.autoRefresh.results[requestId];
    if (!result) return { ok: false, reason: 'unknownRequest', requestId: requestId };
    return { ok: true, requestId: requestId, done: result.done, status: result.status, text: result.text,
      error: result.error, elapsedMillis: result.elapsedMillis };
  }

  function openBidLayer() {
    if (findLayer()) return { ok: true, alreadyOpen: true };
    var bidButton = findBidButton();
    if (!bidButton) return { ok: false, reason: 'bidButtonNotFound' };
    if (bidButton.signInHref) return { ok: false, reason: 'signInRequired', signInHref: bidButton.signInHref };
    if (!bidButton.recognised) return { ok: false, reason: 'bidButtonNotRecognised', label: bidButton.label };
    if (bidButton.loading) return { ok: false, reason: 'pageNotReady' };
    if (bidButton.disabled) return { ok: false, reason: 'bidButtonDisabled' };
    if (state.guard && state.guard.which === 'bidButton') {
      if (state.guard.reached) return { ok: false, reason: 'nativeTapReached' };
      state.guard.mode = 'swallow';
    }
    centerOf(bidButton.element);
    var outcome = domPress(bidButton.element, pressableStill(bidButton.element));
    if (outcome === 'notStarted') return { ok: false, reason: 'labelChanged' };
    return { ok: true, partial: outcome === 'partial', selector: bidButton.selector, label: bidButton.label };
  }

  function layerState() {
    var layer = findLayer();
    var interstitial = detectInterstitial(layer);
    var result = {
      ok: true,
      url: location.href,
      title: document.title,
      open: !!layer,
      ready: false,
      container: null,
      input: null,
      submit: null,
      minimumText: null,
      minimumAmountText: null,
      powerBids: [],
      lowestPowerBidAmountText: null,
      notices: [],
      inlineError: null,
      interstitial: interstitial,
      pendingDialogText: null,
      layerError: null,
      signInRequired: isSignInPage() || !!(interstitial && interstitial.kind === 'signIn'),
    };
    if (!layer) {
      if (!interstitial) {
        var dialogs = renderedDialogs();
        if (dialogs.length > 0) result.pendingDialogText = truncate(textOf(dialogs[0], 300), 300) || null;
      }
      return result;
    }
    result.container = { selector: layer.selector, label: layer.element.getAttribute('aria-label') };
    result.notices = noticeTexts(layer.element);
    if (layer.input) {
      var input = layer.input.element;
      result.input = { selector: layer.input.selector, id: input.id || null, type: (input.getAttribute('type') || 'text').toLowerCase(),
        inputMode: input.getAttribute('inputmode'), value: input.value, disabled: !isEnabled(input),
        invalid: input.getAttribute('aria-invalid') === 'true' };
      var submit = findSubmit(layer.element);
      result.submit = { selector: submit.selector, label: submit.label, enabled: submit.enabled,
        found: !!submit.element, rejected: submit.rejected };
      var bids = findPowerBids(layer.element);
      result.powerBids = bids.map(function (bid) {
        return bid.label;
      });
      var lowest = lowestPowerBid(bids);
      result.lowestPowerBidAmountText = lowest ? lowest.amountText : null;
      var minimum = findMinimum(layer.element);
      result.minimumText = minimum ? minimum.text : null;
      result.minimumAmountText = minimum ? minimum.amountText : null;
      result.inlineError = inlineErrorText(layer);
      var cover = submit.element && !interstitial ? blockingCover(submit.element) : null;
      if (cover) result.interstitial = { kind: 'unknown', text: truncate(textOf(cover, 600), 600) };
      result.ready = !!submit.element && !result.interstitial;
    } else if (!interstitial) {
      var text = textOf(layer.element, 300);
      if (TABLE.texts.layerError[0].test(text)) {
        result.layerError = truncate(text, 300);
      } else {
        // A step eBay loads into a frame (an identity or phone check) has no text or field Kestrel can see.
        var frames = framesIn(layer.element.closest(DIALOG_SELECTOR) || layer.element);
        var framed = frames.length > 0 ? 'eBay shows a page from ' + frames.map(frameHost).join(', ') + ' in its dialog' : '';
        result.pendingDialogText = truncate(text, 300) || framed || null;
      }
    }
    return result;
  }

  var DIALOG_SELECTOR = TABLE.dialogs.map(function (entry) {
    return entry[0];
  }).join(', ');

  function framesIn(root) {
    return queryAll(root, 'iframe, frame').filter(isRendered);
  }

  // Only the host: a frame's address can carry tokens.
  function frameHost(frame) {
    try {
      return new URL(frame.getAttribute('src') || '', location.href).host || 'an unnamed source';
    } catch (error) {
      return 'an unnamed source';
    }
  }

  function nativeValueSetter(input) {
    var descriptor = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
    return descriptor && descriptor.set ? descriptor.set : null;
  }

  function fireInput(input, inputType, data) {
    var options = { bubbles: true, cancelable: false, inputType: inputType, data: data };
    var event = typeof window.InputEvent === 'function' ? new window.InputEvent('input', options)
      : new window.Event('input', { bubbles: true });
    input.dispatchEvent(event);
  }

  function typeMax(text) {
    if (typeof text !== 'string' || !/^\d{1,9}(?:[.,]\d{1,2})?$/.test(text)) return { ok: false, reason: 'badAmountText' };
    var layer = findLayer();
    if (!layer || !layer.input) return { ok: false, reason: 'amountFieldNotFound' };
    // Nothing is typed while eBay asks for something else: the amount must never land in a check's field.
    var check = detectInterstitial(layer);
    if (check) return { ok: false, reason: 'interstitial', interstitial: check };
    var input = layer.input.element;
    if (!isEnabled(input) || input.readOnly) return { ok: false, reason: 'amountFieldDisabled' };
    var setter = nativeValueSetter(input);
    if (!setter) return { ok: false, reason: 'valueSetterMissing' };
    input.focus();
    if (input.value !== '') {
      setter.call(input, '');
      fireInput(input, 'deleteContentBackward', null);
    }
    for (var i = 0; i < text.length; i++) {
      setter.call(input, text.slice(0, i + 1));
      fireInput(input, 'insertText', text.charAt(i));
    }
    input.dispatchEvent(new window.Event('change', { bubbles: true }));
    input.blur();
    var after = findLayer() || layer;
    var submit = after.element ? findSubmit(after.element) : { enabled: false, label: null };
    var minimum = after.element ? findMinimum(after.element) : null;
    var lowest = after.element ? lowestPowerBid(findPowerBids(after.element)) : null;
    return {
      ok: true,
      value: input.value,
      inlineError: inlineErrorText(after),
      minimumText: minimum ? minimum.text : null,
      minimumAmountText: minimum ? minimum.amountText : null,
      lowestPowerBidAmountText: lowest ? lowest.amountText : null,
      submitEnabled: submit.enabled,
      submitLabel: submit.label,
      inputSelector: layer.input.selector,
      documentToken: DOCUMENT_TOKEN,
    };
  }

  // options.hold true blocks every bid request (Kestrel is not pressing submit yet); hold false, with a ceiling,
  // lets one bid through up to the ceiling. A hold is only released by an explicit hold false.
  function armObserver(options) {
    var observer = state.observer;
    var ceiling = options && typeof options.ceilingMinor === 'number' && isFinite(options.ceilingMinor) &&
      typeof options.currency === 'string' ? { minor: Math.floor(options.ceilingMinor), currency: options.currency } : null;
    if (ceiling) observer.ceiling = ceiling;
    if (options && options.hold === true) observer.hold = true;
    if (options && options.hold === false) observer.hold = false;
    if (!observer.armed) {
      observer.fetchWrapped = wrapFetch();
      observer.xhrWrapped = wrapXhr();
      observer.resourceObserved = observeResources();
      observer.dialogsWatched = watchDialogs();
      observer.armed = true;
    }
    var held = observer.hold || observer.dialogHoldReason !== null;
    return { ok: true, fetchWrapped: !!observer.fetchWrapped, xhrWrapped: !!observer.xhrWrapped,
      resourceObserver: !!observer.resourceObserved, ceilingMinor: observer.ceiling ? observer.ceiling.minor : null,
      hold: held, holdReason: observer.dialogHoldReason };
  }

  // A JavaScript dialog the page opens while Kestrel works on it (an alert inside eBay's submit handler, say) is
  // answered by the WebView, which lets the page run on. Whatever comes after it, no bid leaves this document:
  // the message is for the user.
  function watchDialogs() {
    var observer = state.observer;
    ['alert', 'confirm', 'prompt'].forEach(function (name) {
      var original = window[name];
      if (typeof original !== 'function') return;
      var wrapper = function (message) {
        var text = name + ': ' + truncate(message === undefined ? '' : String(message), 300);
        observer.dialogs.push(text);
        if (observer.dialogs.length > 10) observer.dialogs.shift();
        observer.dialogHoldReason = 'the page showed a message first (' + text + ')';
        return original.apply(window, arguments);
      };
      window[name] = wrapper;
      observer.dialogWraps.push({ name: name, original: original, wrapper: wrapper });
    });
    return observer.dialogWraps.length > 0;
  }

  function disarmObserver() {
    var observer = state.observer;
    observer.dialogWraps.forEach(function (wrap) {
      if (window[wrap.name] === wrap.wrapper) window[wrap.name] = wrap.original;
    });
    observer.dialogWraps = [];
    if (observer.fetchWrapper && window.fetch === observer.fetchWrapper) window.fetch = observer.fetchOriginal;
    if (window.XMLHttpRequest && window.XMLHttpRequest.prototype) {
      var proto = window.XMLHttpRequest.prototype;
      if (observer.xhrOpenWrapper && proto.open === observer.xhrOpenWrapper) proto.open = observer.xhrOpenOriginal;
      if (observer.xhrSendWrapper && proto.send === observer.xhrSendWrapper) proto.send = observer.xhrSendOriginal;
    }
    if (observer.resourceObserver) observer.resourceObserver.disconnect();
    observer.fetchWrapper = null;
    observer.xhrOpenWrapper = null;
    observer.xhrSendWrapper = null;
    observer.resourceObserver = null;
    observer.armed = false;
    return { ok: true };
  }

  function elementCenter(which) {
    var found = elementFor(which);
    if (!found) return { ok: true, found: false, which: which };
    var center = centerOf(found.element);
    return { ok: true, found: true, which: which, x: center.x, y: center.y, width: center.width, height: center.height,
      inViewport: center.inViewport, hit: center.hit, devicePixelRatio: center.devicePixelRatio,
      visible: isRendered(found.element), label: found.label, selector: found.selector };
  }

  // Prepares a native tap on the bid button or the submit: checks the element, arms the guard and returns where
  // to tap. For the submit it also marks the one press of this document as taken.
  function armTap(which, options) {
    if (which !== 'bidButton' && which !== 'submit') return { ok: false, reason: 'unknownTarget' };
    if (which === 'submit') {
      if (state.press.phase !== 'idle') return { ok: false, reason: 'alreadySubmitted' };
      var layer = findLayer();
      var submit = layer && layer.input ? findSubmit(layer.element) : { element: null };
      var problem = submitProblem(layer, submit, options);
      if (problem) return { ok: false, reason: problem };
      var center = centerOf(submit.element);
      if (!center.inViewport) return { ok: false, reason: 'notInViewport' };
      captureBaseline(layer);
      state.press.phase = 'nativeArmed';
      state.press.label = submit.label;
      armGuard('target', submit.element, 'submit');
      return { ok: true, which: which, x: center.x, y: center.y, hit: center.hit, devicePixelRatio: center.devicePixelRatio,
        label: submit.label, perfTime: now() };
    }
    var bidButton = findBidButton();
    if (!bidButton) return { ok: false, reason: 'bidButtonNotFound' };
    if (bidButton.signInHref) return { ok: false, reason: 'signInRequired' };
    if (!bidButton.recognised) return { ok: false, reason: 'bidButtonNotRecognised' };
    if (bidButton.disabled || bidButton.loading) return { ok: false, reason: 'bidButtonNotReady' };
    var buttonCenter = centerOf(bidButton.element);
    if (!buttonCenter.inViewport) return { ok: false, reason: 'notInViewport' };
    armGuard('target', bidButton.element, 'bidButton');
    return { ok: true, which: which, x: buttonCenter.x, y: buttonCenter.y, hit: buttonCenter.hit,
      devicePixelRatio: buttonCenter.devicePixelRatio, label: bidButton.label, perfTime: now() };
  }

  function tapResult() {
    var guard = state.guard;
    var nativePress = state.press.method === 'native';
    if (!guard) {
      return { ok: true, armed: false, reached: nativePress, pressPerfTime: state.press.perfTime,
        last: state.lastGuard || null, documentToken: DOCUMENT_TOKEN };
    }
    return { ok: true, armed: true, which: guard.which, reached: guard.reached || (guard.which === 'submit' && nativePress),
      swallowed: guard.swallowed, labelChanged: guard.labelChanged, events: guard.events.slice(0, 20),
      pressPerfTime: state.press.perfTime, documentToken: DOCUMENT_TOKEN };
  }

  function disarmTap() {
    disarmGuard();
    return { ok: true };
  }

  // A point whose topmost element is in this document and is not a frame. The guard swallows the probe tap only in
  // this document: a tap over an iframe (an advert, for example) would reach the frame.
  function pickProbePoint(viewport) {
    var fractions = [[0.5, 0.5], [0.5, 0.3], [0.5, 0.7], [0.25, 0.5], [0.75, 0.5], [0.5, 0.15], [0.5, 0.85]];
    for (var i = 0; i < fractions.length; i++) {
      var x = Math.round(viewport.width * fractions[i][0]);
      var y = Math.round(viewport.height * fractions[i][1]);
      var clientX = viewport.offsetLeft + x / viewport.scale;
      var clientY = viewport.offsetTop + y / viewport.scale;
      var topmost = typeof document.elementFromPoint === 'function' ? document.elementFromPoint(clientX, clientY) : null;
      if (topmost && topmost.closest('iframe, frame, object, embed')) continue;
      return { x: x, y: y, clientX: clientX, clientY: clientY };
    }
    return null;
  }

  function armTouchProbe() {
    var viewport = viewportInfo();
    var point = pickProbePoint(viewport);
    if (!point) return { ok: false, error: 'no spot without a frame to probe' };
    armGuard('swallow', null, 'probe');
    state.probe = { x: point.x, y: point.y, expectClientX: point.clientX, expectClientY: point.clientY, armedAt: now() };
    return { ok: true, x: point.x, y: point.y, devicePixelRatio: devicePixelRatio() };
  }

  function touchProbeResult(finish) {
    var probeState = state.probe;
    var guard = state.guard;
    var events = guard && guard.which === 'probe' ? guard.events.slice(0, 20) : [];
    var first = events.filter(function (event) {
      return PRESS_START_EVENTS[event.type] && event.x !== null;
    })[0];
    var matched = !!(probeState && first && Math.abs(first.x - probeState.expectClientX) <= PROBE_TOLERANCE_CSS_PX &&
      Math.abs(first.y - probeState.expectClientY) <= PROBE_TOLERANCE_CSS_PX);
    var result = { ok: true, trusted: events.length > 0, matched: matched, events: events,
      expectClientX: probeState ? probeState.expectClientX : null, expectClientY: probeState ? probeState.expectClientY : null };
    if (finish && guard && guard.which === 'probe') disarmGuard();
    state.probeResult = result;
    return result;
  }

  function submitPress(options) {
    var press = state.press;
    if (press.phase === 'pressed') {
      return { ok: false, dispatched: false, reason: press.method === 'native' ? 'nativeTapReached' : 'alreadySubmitted' };
    }
    if (press.phase === 'abandoned') return { ok: false, dispatched: false, reason: 'nativeTapUnknown' };
    var layer = findLayer();
    var submit = layer && layer.input ? findSubmit(layer.element) : { element: null };
    if (press.phase === 'nativeArmed') {
      // The native tap never reached the button: swallow any late trusted tap, then press once with DOM events.
      // Without the guard there is no way to know that no tap landed, so nothing is pressed.
      if (!state.guard || state.guard.which !== 'submit') return { ok: false, dispatched: false, reason: 'nativeTapUnknown' };
      state.guard.mode = 'swallow';
      var fallbackProblem = submitProblem(layer, submit, options);
      if (fallbackProblem) return { ok: false, dispatched: false, reason: fallbackProblem };
    } else {
      var problem = submitProblem(layer, submit, options);
      if (problem) return { ok: false, dispatched: false, reason: problem };
      captureBaseline(layer);
    }
    press.phase = 'pressed';
    press.method = 'dom';
    press.label = submit.label;
    centerOf(submit.element);
    press.perfTime = now();
    press.epochTime = Date.now();
    // The one press is spent even when it stops part-way: a button that changed under the finger is not pressed again.
    // Once any event went out, eBay's handler may already have sent the bid ("Submitting..."), so it counts as sent.
    var outcome = domPress(submit.element, pressableStill(submit.element));
    if (outcome === 'notStarted') return { ok: false, dispatched: false, reason: 'labelChanged', label: submit.label };
    return { ok: true, dispatched: true, partial: outcome === 'partial', label: submit.label, selector: submit.selector,
      perfTime: press.perfTime, epochTime: press.epochTime };
  }

  function difference(current, baseline) {
    if (!baseline) return current.slice();
    return current.filter(function (item) {
      return baseline.indexOf(item) < 0;
    });
  }

  function replyState() {
    var layer = findLayer();
    var baseline = state.press.baseline;
    var layerNotices = layer ? noticeTexts(layer.element) : [];
    var pageNotices = noticeTexts(pageNoticeRoot()).filter(function (text) {
      return layerNotices.indexOf(text) < 0;
    });
    var notices = layerNotices.concat(pageNotices);
    var baselineNotices = baseline ? baseline.layerNotices.concat(baseline.pageNotices) : null;
    var lines = layer ? visibleLines(layer.element, 6000) : [];
    return {
      ok: true,
      url: location.href,
      title: document.title,
      layerOpen: !!layer,
      notices: notices,
      newNotices: difference(notices, baselineNotices),
      addedLines: baseline ? difference(lines, baseline.layerLines).slice(0, 30) : [],
      inlineError: layer ? inlineErrorText(layer) : null,
      // eBay may answer the press with a check (phone verification, sign-in) instead of placing the bid.
      interstitial: detectInterstitial(layer),
      records: state.observer.records.slice(),
      blocked: state.observer.blocked.slice(),
      // Only a request stopped after the press answers the press; one stopped while waiting does not.
      blockedSincePress: state.press.perfTime === null ? [] : state.observer.blocked.filter(function (entry) {
        return entry.atPerf >= state.press.perfTime;
      }),
      pressed: state.press.phase === 'pressed',
      pressMethod: state.press.method,
      pressPerfTime: state.press.perfTime,
      documentToken: DOCUMENT_TOKEN,
      nowPerf: now(),
    };
  }

  function closeLayer() {
    var layer = findLayer();
    if (!layer) return { ok: true, closed: true, method: 'none' };
    var dialog = layer.element.closest('[role="dialog"], .lightbox-dialog, .drawer-dialog, .fullscreen-dialog') || layer.element;
    var close = null;
    for (var i = 0; i < TABLE.closeButton.length && !close; i++) {
      var nodes = queryAll(dialog, TABLE.closeButton[i][0]);
      for (var j = 0; j < nodes.length; j++) {
        var label = normalize(nodes[j].getAttribute('aria-label') || labelOf(nodes[j]) || '');
        var isClose = i < TABLE.closeButton.length - 1 || TABLE.texts.closeLabel[0].test(label);
        if (isClose && !TABLE.texts.closeLabelDeny[0].test(label) && isRendered(nodes[j]) && !isInsidePowerBids(nodes[j])) {
          close = nodes[j];
          break;
        }
      }
    }
    if (close) {
      domPress(close);
      return { ok: true, closed: false, method: 'closeButton' };
    }
    var escape = { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true, cancelable: true };
    (document.activeElement || document.body).dispatchEvent(new window.KeyboardEvent('keydown', escape));
    (document.activeElement || document.body).dispatchEvent(new window.KeyboardEvent('keyup', escape));
    return { ok: true, closed: false, method: 'escape' };
  }

  function report() {
    var bidButton = findBidButton();
    var layer = findLayer();
    var submit = layer && layer.input ? findSubmit(layer.element) : null;
    var header = window.GHpre;
    return {
      ok: true,
      version: VERSION,
      url: location.href,
      title: document.title,
      readyState: document.readyState,
      visibilityState: document.visibilityState || null,
      devicePixelRatio: devicePixelRatio(),
      viewport: { width: window.innerWidth, height: window.innerHeight },
      signedIn: header && typeof header.userAuth === 'boolean' ? header.userAuth : null,
      matched: {
        bidButton: bidButton ? bidButton.selector : null,
        bidButtonLabel: bidButton ? bidButton.label : null,
        // Why a bid layer never opened can be a bid button eBay's scripts never made usable.
        bidButtonState: bidButton ? { disabled: bidButton.disabled, loading: bidButton.loading,
          visible: bidButton.visible, recognised: bidButton.recognised } : null,
        layerContainer: layer ? layer.selector : null,
        amountInput: layer && layer.input ? layer.input.selector : null,
        submitButton: submit ? submit.selector : null,
        submitLabel: submit ? submit.label : null,
        rejectedSubmitLabels: submit ? submit.rejected : [],
        powerBids: layer ? findPowerBids(layer.element).map(function (bid) {
          return bid.label;
        }) : [],
      },
      dialogs: renderedDialogs().slice(0, 5).map(function (dialog) {
        return { role: dialog.getAttribute('role'), label: dialog.getAttribute('aria-label'),
          text: truncate(textOf(dialog, REPORT_TEXT_LIMIT), REPORT_TEXT_LIMIT) };
      }),
      frames: framesIn(document).slice(0, 10).map(function (frame) {
        var holder = layer ? (layer.element.closest(DIALOG_SELECTOR) || layer.element) : null;
        return { host: frameHost(frame), inLayer: !!(holder && holder.contains(frame)) };
      }),
      notices: (layer ? noticeTexts(layer.element) : noticeTexts(pageNoticeRoot())).slice(0, 10).map(function (text) {
        return truncate(text, REPORT_TEXT_LIMIT);
      }),
      inlineError: layer ? inlineErrorText(layer) : null,
      interstitial: detectInterstitial(layer),
      observer: { armed: state.observer.armed, ceilingMinor: state.observer.ceiling ? state.observer.ceiling.minor : null,
        records: state.observer.records.slice(-5).map(function (record) {
          return Object.assign({}, record, { bodyPrefix: truncate(record.bodyPrefix, 600) });
        }),
        blocked: state.observer.blocked.slice(-5) },
      press: { phase: state.press.phase, method: state.press.method, label: state.press.label,
        perfTime: state.press.perfTime },
      guard: guardSummary(state.guard) || state.lastGuard || null,
      touchProbe: state.probeResult || null,
      errors: state.errors.slice(-20),
    };
  }

  // ------------------------------------------------------------------------------------------------
  // Installation
  // ------------------------------------------------------------------------------------------------

  function safe(name, implementation) {
    return function () {
      try {
        return JSON.stringify(implementation.apply(null, arguments));
      } catch (error) {
        recordError(name, error);
        try {
          return JSON.stringify({ ok: false, error: truncate(error && error.message ? error.message : String(error), 300),
            function: name });
        } catch (secondError) {
          return '{"ok":false}';
        }
      }
    };
  }

  window.addEventListener('error', function (event) {
    state.errors.push({ where: 'page', message: truncate(event.message, 300), source: pathAndQuery(event.filename || ''),
      line: event.lineno || null, at: Date.now() });
    if (state.errors.length > 20) state.errors.shift();
  });

  var api = {
    version: VERSION,
    probe: safe('probe', probe),
    modelText: safe('modelText', modelText),
    autoRefresh: safe('autoRefresh', autoRefresh),
    autoRefreshResult: safe('autoRefreshResult', autoRefreshResult),
    openBidLayer: safe('openBidLayer', openBidLayer),
    layerState: safe('layerState', layerState),
    typeMax: safe('typeMax', typeMax),
    armObserver: safe('armObserver', armObserver),
    disarmObserver: safe('disarmObserver', disarmObserver),
    submit: safe('submit', submitPress),
    elementCenter: safe('elementCenter', elementCenter),
    armTap: safe('armTap', armTap),
    tapResult: safe('tapResult', tapResult),
    disarmTap: safe('disarmTap', disarmTap),
    armTouchProbe: safe('armTouchProbe', armTouchProbe),
    touchProbeResult: safe('touchProbeResult', touchProbeResult),
    replyState: safe('replyState', replyState),
    closeLayer: safe('closeLayer', closeLayer),
    report: safe('report', report),
  };
  Object.freeze(api);
  try {
    Object.defineProperty(window, '__kestrel', { value: api, configurable: true, enumerable: false, writable: false });
  } catch (error) {
    window.__kestrel = api;
  }
})();
