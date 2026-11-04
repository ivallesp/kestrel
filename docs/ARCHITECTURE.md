# Kestrel architecture

Kestrel is a single-user Android app (Pixel 9a, Android 17, sideloaded) that places the user's own eBay
auction bids a few seconds before the end, from the phone, through the user's own signed-in eBay web session.
Research behind every decision: `docs/research/` (bid flow, Android platform, eBay rules, competitors, design).

## 1. Ground rules (product and safety)

1. The user's maximum is a hard ceiling. Kestrel never bids above it, never raises it by itself, never buys, never pays.
2. One bid per snipe: the full maximum, once, a few seconds before the end. eBay proxy bidding does the rest.
   A retry is allowed only with the identical amount (it cannot raise what the user pays).
3. Kestrel drives eBay's normal web pages in a normal WebView, at a person's pace. It never replays eBay's internal
   requests from outside the page, never changes its user agent, and never calls eBay from OkHttp.
4. If eBay shows a captcha, a "verify it's you" step, phone verification, an age check or any page Kestrel does not
   recognise, Kestrel stops and hands the page to the user (notification + visible WebView). It never tries to solve it.
5. The session (cookies) stays in the WebView cookie store on the phone. No server, no analytics, no third-party calls
   except time.google.com for clock checks.
6. Dry runs never submit. The dry-run code path has no access to the submit step (enforced in code, tested).
7. Monitoring is gentle: page loads per listing follow `MonitorPolicy` (about hourly far from the end, faster in the last
   hour, eBay's own `refreshInSeconds` as the floor in the last minutes).

## 2. Packages

```
app.kestrel
  KestrelApp            Application: AppContainer, notification channels
  AppContainer          manual dependency container (no DI framework)
  model/                pure Kotlin domain: Money, EbaySite, ItemRef, Listing, Snipe, SnipeStatus, TimelineEvent, Group
  ebay/                 pure Kotlin eBay knowledge: ItemUrlParser, PageModelExtractor, ListingParser,
                        AutoRefreshParser, PageClassifier, BidIncrements, BuyerProtectionFee, LayerReport
  engine/               orchestration: SnipeEngine (one snipe window), BidAttempt, DryRun, ResultResolver,
                        MonitorPolicy, GroupPolicy, TrueTime (SNTP), EngineClock
  web/                  WebView side: EbayWebViewFactory, WebViewDriver (suspend API), PageScript (loads assets/js/kestrel.js)
  platform/             Android glue: SnipeScheduler (AlarmManager), AlarmReceiver, SnipeService (specialUse FGS),
                        CheckWorker (WorkManager), SystemEventReceiver (boot/update/time), Notifications,
                        NotificationActionReceiver, Readiness
  data/                 Room database (snipes, groups, price points, timeline), SnipeRepository, SettingsStore (DataStore)
  ui/                   Compose UI (see docs/DESIGN.md)
assets/js/kestrel.js    page helper injected with evaluateJavascript
```

`model`, `ebay`, `engine` (except the WebView-facing adapters) are plain Kotlin and are unit-tested on the JVM against
real archived eBay pages in `app/src/test/resources/fixtures/`.

## 3. Reading a listing

Source of truth: the JSON page model inline in the item page HTML (2025-2026 pages, all sites).
`PageModelExtractor` finds a module by its key text (`"SEMANTIC_DATA":{`, `"BUY_BOX":{`, `"STATUS_MESSAGE":{`),
then cuts the balanced JSON object with a string-aware brace scanner. Never depend on the script global's name
(`$MC`, `$M_96613636_C` change between builds).

| Field | Primary | Fallback |
|---|---|---|
| End time (ms, UTC) | `SEMANTIC_DATA.endDate.value` | `BUY_BOX.bidInfo.endTime.endTime.value`, then `listingLifecycle.endDate` |
| Original scheduled end | `SEMANTIC_DATA.refreshTimers.itemEnding.value` | |
| Status | `SEMANTIC_DATA.listingStatus` (ACTIVE/ENDED) | `auctionData.biddingEndedAt` present |
| Current price | `SEMANTIC_DATA.currentBidPrice {value, currency, convertedFromValue, convertedFromCurrency}` | `BUY_BOX.bidPrice.value` |
| Bid count | `SEMANTIC_DATA.bidCount` | `BUY_BOX.bidInfo.bidCount.value` |
| Reserve | `SEMANTIC_DATA.hasReservePrice` | |
| Signed-in user leads | `SEMANTIC_DATA.isHighBidder`, `auctionData.bidderAuctionDetails.userBiddingStatus` | |
| Seller, site | `sellerUserName`, `marketplaceListedOn` (EBAY_GB/EBAY_US/EBAY_DE) | |
| Extended bidding | `auctionData.extendedBidding` non-null, label text "Extended bidding" / "reset the time to" | end time moving |
| Title, image, postage | JSON-LD `Product` (`name`, `image[]`, `offers.shippingDetails[].shippingRate`) | `og:title`, `og:image` |
| Buyer Protection | `BUY_BOX.bidPriceProgram` text "incl. £X for Buyer Protection" | |
| Ended text | `STATUS_MESSAGE` text ("Item sold on", "Bidding ended on", "was ended by the seller") | |

Live updates near the end use the page's own `AUTO_REFRESH` endpoint, fetched from inside the item page
(same origin, credentials included), at no more than eBay's `refreshInSeconds`. `AutoRefreshParser` finds the state
whose `eventName` contains `bid-price-section`.

Sign-in state: `window.GHpre.userAuth`, a `signin.` host or `/ws/eBayISAPI.dll?SignIn` in the URL. `PageClassifier`
maps URL + title to NORMAL, SIGN_IN, CHALLENGE (`/splashui/challenge`, wait in place), CAPTCHA (`/splashui/captcha`,
hand to user), RATE_WALL (title "Error Page | eBay", back off at least 8 s with jitter), NOT_FOUND, OTHER.

## 4. Placing the bid (2025-2026 flow, one in-page layer, no confirm page)

1. Load `https://www.<site>/itm/<itemId>` in a fresh headless WebView (never attached to a window, so Chromium keeps the
   page "visible" and timers unthrottled). Give it a phone-sized layout with `measure`/`layout`.
2. Wait for hydration: `#bidBtn_btn` (fallback `[data-testid=x-bid-action] a, [data-testid=x-bid-action] button`)
   present, not `disabled`, its container without the `loading` class. A sign-in `href` means the session is gone.
3. Open the layer (T-45 s): click the bid button. Wait for a layer container (`.placebid-redesign-wrapper`,
   `.place-bid-wrapper-ocv`, `#bid-offer-summary`, `[role=dialog]`) and the amount input
   (`input[id*='@placebid-section-@offer-section-@price']`, `input[id*='@placebid']`, `input.textbox__control[type=tel]`,
   `input.textbox__control`, `input[id*='price']`, legacy `#MaxBidId`).
4. Type the max with real per-character input events (focus, value via the native setter, `input` and `change` events),
   read back the value, read any minimum ("Enter £X or more") and the lowest power-bid label as the live minimum.
   If max < minimum: stop, status "Price passed your max". Never click a power-bid button.
5. Install a response observer (wrap `fetch` and `XMLHttpRequest` in the page) that records the status and body of the
   `/bfl/placebid?action=confirmbid` call and timestamps it.
6. At `fireAt = trueEnd - lead`: click the primary submit button (`div.place-bid-actions__submit > button.btn--primary`,
   `button.btn--fluid.btn--primary`, `button[type=submit]` with text Bid / Submit bid / Place bid / Increase bid /
   Increase max bid, `button.btn--primary:not([disabled])`). Prefer a native tap (MotionEvent at the element centre) when
   the input probe shows native input reaches the page; otherwise DOM events.
7. Outcome within about 4 s: confirmbid response and/or the layer notice. "Your current bid puts you in the lead." =
   LEADING; "Another bidder is winning" = OUTBID_IMMEDIATELY (someone's max is higher); other notice text = REJECTED with
   eBay's text; nothing = UNCONFIRMED (one retry with the same amount if at least 2 s remain, else follow the page).
8. Follow to the true end through Extended Bidding (AUTO_REFRESH, end time may move), then resolve the result.

Selectors and texts live in `assets/js/kestrel.js` as ordered fallback chains, and every run writes a `LayerReport`
(which selector matched, texts seen, timings) into the timeline, so a changed eBay page is diagnosable from the phone.

## 5. One snipe window (SnipeService)

```
T-10 min   dry run (short FGS): load page, check session, open layer, type max, read minimum, close. Never submit.
T-120 s    exact alarm -> AlarmReceiver -> startForegroundService(SnipeService). Backup alarm at T-75 s.
           startForeground first (Live Update: "Bids at 18:21:34"), partial wake lock (timeout), SNTP sync.
T-90 s     load item page, classify, parse listing, check status/price/session/extended bidding.
T-45 s     open bid layer, type max, verify, install response observer.
T-lead     fire (main-thread Handler at an elapsedRealtime anchor, no AlarmManager: 5 s minimum futurity).
T+0..      follow AUTO_REFRESH through extensions; after the page says ENDED, reload and resolve WON/LOST.
end        result notification, timeline, destroy WebView, release wake lock, stop service.
```

Several windows can overlap; each has its own WebView and coroutine. Alarms: `setExactAndAllowWhileIdle`
(`ELAPSED_REALTIME_WAKEUP`) with `USE_EXACT_ALARM` (granted at install for this sideloaded app); `setAlarmClock` as the
fallback when battery is not Unrestricted. Foreground service type `specialUse`.

## 6. Time

`TrueTime` queries time.google.com by SNTP (4-8 samples, lowest round trip wins) and anchors true UTC to
`elapsedRealtime`. Fallback: `SystemClock.currentNetworkTimeClock()`, then the system clock (flagged in the timeline).
Countdowns use `elapsedRealtime`; stored times are UTC epoch milliseconds.

## 7. Monitoring and alerts

`MonitorPolicy.nextCheck(now, end)`: more than 24 h left every 6 h; 6-24 h every 2 h; 1-6 h hourly; 10-60 min every
15 min; dry run at T-10 min; window from T-120 s. Jitter +-10 %. WorkManager runs checks more than an hour out;
exact alarms run the last hour. A check loads the item page in a headless WebView, parses it and updates the snipe.
Alerts: price passed max (with Raise max inline reply), ended early, Buy It Now visible, sign-in needed, needs you
(captcha/verification), result.

## 8. Groups

`GroupPolicy`: "Win 1 of N". Before a member's window opens, if a member is WON, cancel the rest. If an earlier member is
still running (extended bidding) or unresolved at fire time, skip this member and say so (never risk winning two).
The UI warns when members end less than 3 minutes apart.

## 9. Status model

Watching (no max) -> Armed -> (Attention: price over max | sign-in needed | needs you | dry run failed) ->
Bidding (window open, bid sent, extended) -> Won | Ended (lost, ended early, cancelled, skipped by group, failed).
Every transition writes a timeline event with UTC time and details; the result screen shows eBay's own reply text.
