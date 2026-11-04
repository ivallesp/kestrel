# Competitor teardown of eBay sniper apps and services, focused on visual and interaction design (Baytomat, bidbag, Gixen, Auction Sniper, Myibidder, eSnipe, BidSlammer, Bidnapper/EZsniper, plus JBidwatcher, Biet-O-Matic BE, Snipesnipe, Deal Hunt), researched 2026-11-04

## Summary
All five Play Store sniper apps (Baytomat, bidbag, Gixen, Auction Sniper, Myibidder) and the web services eSnipe, BidSlammer and Bidnapper bid from their own servers. Each one links to eBay with an authorization token, which most of them say must be renewed every 18 months. Every one of them sells "your phone can be off" as its main benefit, which our phone-based model cannot offer.

Their designs are dated:
- Gixen's Android app wraps a 2023 "Version BETA 1.0" mobile web page that lists raw fields such as "Status (main): SCHEDULED".
- Myibidder's store screenshots are from 2019, and its list never shows the current price.
- bidbag has a dark, 2010s-era interface. It splits the money field into two boxes and shows £ and € in the same dialog.
- Baytomat mixes untranslated German into the English interface.
- Auction Sniper's 2025 redesign looks the most modern. But it shipped broken (watches failed to load), and its own screenshots show the status bar and gesture bar overlapping the interface.

Reviews mostly reward reliability ("set and forget") and low cost, and users put up with dated UI. They punish silent failures, prices that are hours old, eBay links that expire unnoticed, confusing credit schemes, and editing being locked 3 to 6 minutes before the end.

Patterns worth borrowing:
- BidSlammer: a preview before setting a snipe (minimum to bid, shipping, seller, bid chart) and an "Auth Expires 533d" tile.
- eSnipe: outcome cards that show "Saved 5.86" plus a log per snipe.
- Baytomat: the lead time shown next to the countdown, and a push/email notification grid.
- bidbag: share-to-app and "Event Rules".
- Deal Hunt: "9m left • Ends 2:03 PM EDT", which gives both the time left and the clock time with its time zone.

Gixen:
- Bid offsets are 3, 6, 8, 10, 12 or 15 seconds. The default is 6, and changing it needs Mirror ($11.99/year, bids from two hosting locations).
- Free accounts get 4 winning snipes a month.
- In a group, winning one item cancels the others. Items in a group must end at least 2 minutes apart.
- Gixen links to eBay through eBay's authorization (since 2017), but its own login has no two-factor sign-in.
- Its Mirror-only API is api.php with username, password, itemid, maxbid, snipegroup and bidoffset. Third-party code reports it was disabled for some accounts in 2026.

On 2026-09-05 Gixen's admin reported that eBay is testing one-minute bid-time extensions: on eBay Motors, live on eBay Australia from about 2026-09-14, with the UK and US possibly next. No competitor interface handles an end time that moves yet.

A 2025 review shows Google blocks "sign in with Google" for eBay inside embedded browsers. That applies directly to our WebView sign-in.

Gaps none of them fill well:
- A proof-of-bid receipt captured from the WebView.
- Visible eBay session and phone readiness checks.
- An outbid notification with a one-tap "raise max".
- Total cost in GBP.
- Plain-language results.
- Edits allowed until a few seconds before the end.
- A countdown that understands extended bidding.
- A practice run (dry run) mode.

## Findings
- **CT1** [medium] eBay began testing bid-time extensions in September 2026: a late bid extends the auction end to 1 minute after the last bid. Rollout started with eBay Motors, eBay Australia went live around 2026-09-14, and eBay UK and US 'may follow'.
  - evidence: Gixen forum topic 'eBay extended bidding', first post by mario (Site Admin) Sat Sep 05 2026: 'eBay started testing and rolling out bid time extensions', 'These extend the auction end time 1 minute from the time of the last bid'. Reply by blubarb19 Mon Sep 14 2026 says eBay Australia started the change that day. https://gixen.com/forum/viewtopic.php?t=11808 (fetched 2026-11-04)
  - implication: Countdown, status and scheduling must handle an end time that moves. Show an 'Extended' state, re-read the end time after each late bid, and keep the user informed. This is a design must even though it is not yet confirmed for ebay.co.uk (inferred).
- **CT2** [medium] Gixen's response to extended bidding: on the affected sites it bids in steps, detects extensions as late as possible, and waits up to 12 extensions before submitting the user's maximum. Gixen Mirror now bids 1 second before the end, and a 1-second offset 'may now be the best choice'. Users asked for 65 to 70 second lead options, and the admin said 'a few weeks'.
  - evidence: Same thread t=11808: mario posts 2026-09-05 and 2026-09-07; Cupid 2026-09-21: 'Gixen extends at most 12 times before submitting the maximum'; alex 2026-09-07 asks for 65s/70s lead time.
  - implication: Sniping strategy is changing in 2026. The UI needs a small, explainable strategy choice (snipe at the end vs. bid before the extension window) rather than only an 'offset seconds' number (inferred).
- **CT3** [high] Gixen bid offsets are 3, 6 (default), 8, 10, 12 or 15 seconds, and changing the offset requires a Mirror subscription. The original 2008 API documentation lists 3, 6, 8, 10 or 15 with default 6.
  - evidence: https://www.gixen.com/main/faq.php ('choose 3, 6 (default), 8, 10, 12 or 15 seconds from the [Offset:] drop-down box'), fetched 2026-11-04, footer © 2006-2026. Archived API post by mario 2008-01-23: 'bidoffset=value (3, 6, 8, 10 or 15. Default value is 6)' http://web.archive.org/web/20160326143132/http://www.gixen.com/forum/viewtopic.php?p=9186. Play review 2025-08-24: 'The default bid (without subscription) is six seconds before the end'.
  - implication: Users notice and resent a fixed 6 s offset. Our free app can offer any lead time, with a default of about 5 to 6 s and a short explanation of the risk of going lower.
- **CT4** [high] Gixen Mirror costs $11.99 a year. It sends snipes from two hosting locations and adds unlimited snipes, contingency groups, multi-win groups, offsets of 3 to 15 s, hourly (not daily) end-time refresh, CSV import/export and no ads. Free accounts are limited to 4 winning snipes a month and 4 pending snipes (a group counts as one).
  - evidence: https://www.gixen.com/main/index.php fetched 2026-11-04. Forum t=9439 'New restriction on free accounts', mario 2019-09-02 ('Free users will be limited to 4 winning snipes / month') and 2019-09-04 (max 4 pending). Play review 2025-10-30: told 'I'd had my four free snipes for the month' on the first attempt.
  - implication: Users value redundancy and pay for it. A single phone needs an explicit reliability story (readiness checks and a fallback). Hidden quotas that surface at action time feel hostile; validate everything when the snipe is created.
- **CT5** [high] Gixen snipe groups: you pick a group number from the [Bid Group:] drop-down, and remaining bids in the group are cancelled once one item is won. Items must not end within 2 minutes of each other. Group snipes are not mirrored. Contingency groups (first loss cancels the rest) and multi-win groups need Mirror.
  - evidence: https://www.gixen.com/main/faq.php: 'All items with the same group number will be grouped together and remaining bids canceled once an item in the group is won'; 'Group bids can be used for items that do not have end times within 2 minutes of each other'; 'If this is a group snipe, note that these are not mirrored.'
  - implication: Copy the semantics (win 1 of N, stop after N), but show end-time spacing visually and warn on conflicts instead of failing silently.
- **CT6** [high] Gixen API: GET https://www.gixen.com/api.php?username=...&password=...& (Gixen credentials) plus the parameters below.
- Add a snipe: itemid and maxbid (required); snipegroup (0 = none), quantity, bidoffset, bidoffsetmirror (optional).
- Delete: ditemid.
- List: listsnipesmain=1 or listsnipesmirror=1.
- Other: purgecompleted=1; notags=1 drops the HTML.
- Replies: 'OK <item> ADDED', 'OK <item> DELETED', 'OK COMPLETEDPURGED', '<br />OK MAIN LISTED'.
- List lines are '|#!#|'-separated: itemid, endtime (unix), maxbid, status (N scheduled, S skipped, D completed), message, title, snipegroup, quantity, bidoffset.
- Errors look like 'ERROR (CODE): MESSAGE'.
  - evidence: Official post 'Gixen API released (use at your own risk for now)' by mario 2008-01-23 (archived 2016-03-26) http://web.archive.org/web/20160326143132/http://www.gixen.com/forum/viewtopic.php?p=9186. /var/tmp/sniper-research/src/cyberfox_gixen/gixen.rb lines 16-28, 107-180 (commit 2015-01-31) adds purgecompleted and 'OK COMPLETEDPURGED'. /var/tmp/sniper-research/src/redyinn_Preisradar-code/server/routes/gixen.js (commit 2026-07-22) uses itemid, maxbid, notags, snipegroup, listsnipesmain. leesouthwart/Pokemon2 config/gixen.php (2026-10-27) points to https://www.gixen.com/xmlapi.php.
  - implication: An optional Gixen backup path is technically possible: the app registers the same snipe on Gixen as a fallback. It needs the user's Gixen Mirror account and API access (see CT7) (inferred design idea).
- **CT7** [medium] The Gixen API is Mirror-only. In 2026 some accounts get 'ERROR (501): API DISABLED'. One developer reports that it needs an active Mirror subscription and enabling 'Enable API access' in Gixen settings, while another tool scrapes the web forms instead because 'Gixen's official API (api.php/xmlapi.php) is disabled for some accounts'.
  - evidence: 2008 post: 'mirror users only can use it'. cyberfox README: 'you must be a paid user of Gixen Mirror'. Preisradar gixen.js comments dated 2026-07-08 and error hint for code 501 (also documents 101, 103, 106, 171). hsukenooi/gixen-cli README and gixen_client.py docstring (repo commit 2026-06-01): web form fields newitemid, newmaxbid, newbidoffset, newbidoffsetmirror, newsnipegroup, dbidid, ismodified, purgecompleted; login POST to https://www.gixen.com/main/home_1.php with username/password/signin.
  - implication: Do not rely on Gixen as a fallback without testing the user's account. If offered, show its state explicitly ('Backup on Gixen: active / API disabled').
- **CT8** [high] Gixen login and eBay link:
- Before 2017, Gixen logged users in with their eBay username and password (the 2016 page shows 'eBay username / eBay Password').
- Since 2017 it uses its own username and password, with an eBay authorization that expires after 18 months. Changing the eBay password disables all authorizations.
- Gixen shows a red warning on login if the authorization expires within 10 days.
- The Gixen account has no two-factor sign-in, and the app had no fingerprint login.
  - evidence: FAQ (2026-11-04): 'Gixen used to require this, but this changed back in 2017', 'eBay authorizations automatically expire after 18 months'. Archived 2016 page login form 'eBay username / eBay Password'. Forum t=9623 (mario 2020-02-02, replies about the red warning and emails). Trustpilot review 2025-11-30 'No 2FA with your Gixen account' https://www.trustpilot.com/review/gixen.com. Forum t=10912 mario 2023-03-30 (fingerprint 'may come later'). Play review 2023-10-28: 'doesn't tell you when its permissions with your eBay account have expired'.
  - implication: Expiry of the link to eBay is a known cause of failure. Our equivalent (eBay WebView session cookies) needs a visible 'session OK, checked at HH:MM' state, checks before each auction, and an app lock.
- **CT9** [high] Gixen's Android app (v2.1.1, last updated 2024-08-18, 10,000+ installs, 4.74 stars from 1,044 ratings) is a thin wrapper around Gixen's mobile web UI ('© 2006 - 2023 Gixen.com - Version BETA 1.0'). It lacks website features (assigning a group when adding, multi-delete, postage, seller) and has no push notifications.
  - evidence: Play page https://play.google.com/store/apps/details?id=com.gixen.gixen&hl=en&gl=GB fetched 2026-11-04 (/var/tmp/sniper-research/design-teardown/play-pages/com.gixen.gixen.html). Screenshot /var/tmp/sniper-research/screens/com.gixen.gixen/shot05.png. Reviews: 2023-03-29 'It's web-based so it's not as fast or intuitive', 2024-01-25 (missing groups, multi-delete), 2024-09-21 ('postage is not listed against each item'), 2025-09-25 ('can't add a new snipe to a group... no shipping cost... would have been nice if there were push notifications').
  - implication: The most praised service has the weakest mobile UX. A native Compose app with push and full data per item is a clear differentiator.
- **CT10** [high] Gixen cards show an unordered list of fields, all with equal visual weight:
- 'ItemId: 155388568558', 'Time to end: 2 days, 34 minutes', 'Currency: USD', 'Max bid: 0.00', 'Current bid: 338.0', 'Status (main): SCHEDULED', 'Offset (main): 8', 'Snipe group: Not grouped'.
- The edit screen shows 'Snipe group: 0'.
- A snipe with max 0.00 is labelled SCHEDULED.
- DELETE ITEM and EDIT ITEM sit side by side with equal weight.
- The menu shows both Login and Log Out.
- The logo takes about 10% of screen height.
  - evidence: /var/tmp/sniper-research/screens/com.gixen.gixen/shot01.png, shot03.png, shot04.png (Play listing, 2023-2024 build). Review 2023-06-05 asks for 'colored backgrounds, i.e. green/red when you have won/lost, or yellow if an items current bid is higher than your max bid'.
  - implication: Rank the information: photo and title, then 'Your max £X vs current £Y' with a winning/outbid state, then the countdown. Hide internals (main/mirror, group numbers), and separate destructive actions.
- **CT11** [high] Baytomat list design (v14.x, updated 2026-10-09, 4.81 stars from 9,782 ratings):
- Each item shows 'Bid: €2.00 (1 points)', coloured green or red by whether the current price is under your max, then 'Actual: €1.00 (1 Bid)' and 'Time left: 04d 19:24:53 (-4 s)'.
- Tabs: RUNNING / WON / LOST, with a summary line 'Bids:3 / Credits: 125 / Needed: 3'.
- The footer shows 'Status: Connected to <ebay user>' and 'Credit points: 125 saved: 4 (1.7%)'.
- Problems: the outbid state is shown by colour alone, 'Actual' is a literal translation of German 'Aktuell', German strings are left untranslated in the English UI ('eBay Konto', 'KONTO WECHSELN', 'Warnungen anzeigen'), number formats are mixed ('EUR 1,00' vs '€1.00'), and the empty state reads '-None-'.
  - evidence: /var/tmp/sniper-research/screens/com.baytomat2/shot01.jpg, shot02.jpg, shot04.jpg, shot13.jpg, shot14.jpg, shot15.jpg, shot16.jpg (Play listing fetched 2026-11-04).
  - implication: Borrow: the lead time shown next to the countdown, a persistent 'connected as' line, and a clear won/lost split. Avoid: colour-only status, translation leftovers, mixed number formats.
- **CT12** [high] Baytomat charges 1 to 25 'points' per won auction depending on the final price:
- 1 point for 1.00-39.99, 2 for 40-69.99, 3 for 70-109.99, up to 25 points above 2000.
- Points cost 0.70 to 1.25 € each.
- Users find it opaque ('seems each point is £100', 2026-03-05).
- Several reviews say Baytomat offers credits for 5-star reviews, which inflates its 4.8 rating.
  - evidence: Baytomat FAQ JSON https://www.baytomat.com/admin/faqs/index_api.json ('How much is the use of baytomat.com?'), fetched 2026-11-04. Play reviews 2021-08-13 ('offered 3 free points to write a 5 star review'), 2025-10-15 ('they bribe for 5 star reviews'), 2026-04-06 ('credits given for reviewing'), 2026-10-19 ('PROMISED 5 CREDITS UPON GIVING A 5 STAR RATING').
  - implication: Store ratings in this category are a weak signal of quality. For our free personal app the lesson is: no gating condition (credits, quotas) should ever stop a bid at the end.
- **CT13** [high] Baytomat's Play description still says registration uses 'your eBay access data. These are stored securely encrypted on our servers'. Its FAQ instead tells users to approve a connection between their eBay account and 'our service partner Snip' through the eBay login. Some users accuse it of password theft ('scam...steals your ebay passwords', 2026-08-22).
  - evidence: Play description (/var/tmp/sniper-research/design-teardown/parsed/com.baytomat2.txt); FAQ JSON entries 'Why do I have to connect to 'Snip'?' and 'I use the eBay security key (2FA), can i use baytomat with it?'; Play review 2026-08-22.
  - implication: Credential trust is a live user concern. Our app should say plainly on the sign-in screen: you sign in on eBay's own page, the session stays on this phone, and nothing is sent to a third party.
- **CT14** [high] How often competitors refresh prices:
- Baytomat: hourly for auctions ending in more than an hour, every 3 minutes inside the last hour, but only every 2 hours during the Sunday rush.
- Auction Sniper checks for outbids: every 10 minutes if ending within 30 minutes, every 20 minutes within an hour, every 12 hours within a day, and maybe not at all at 7 days.
- Myibidder: current bid updated '1-2 times per day'.
- Gixen (2023): free accounts get batch prices every 2 to 3 hours.
  - evidence: Baytomat FAQ JSON 'How often are the prices updated?'. Auction Sniper FAQ https://www.auctionsniper.com/help/faq/ ('How come I'm outbid but my snipe doesn't know it yet?'). Myibidder FAQ https://www.myibidder.com/login/faq/4 q13. Gixen forum t=10912, mario 2023-03-31.
  - implication: These are benchmarks for a 'gentle' monitoring rate. A schedule that speeds up near the end, plus an always-visible 'price checked N min ago', already beats every competitor.
- **CT15** [high] bidbag (v8.22.95, updated 2026-10-23, 4.46 stars from 2,039 ratings) uses a dark, skeuomorphic, 2010s-style interface:
- Tiny yellow 'Time left: 10h 46m 05s' pills.
- A coloured status column (yellow striped, red, green check) and coloured list count badges (yellow/red/green/grey) with no legend.
- 'Time left: 1 Days 11h 04m 28s'.
- An EDIT AUCTION dialog that splits the max bid into whole and decimal fields and shows '£' on the left and '€' on the right.
- A 'Lead time in seconds' slider set to 3s.
  - evidence: /var/tmp/sniper-research/screens/com.bidbag.mobile/shot01.png to shot05.png and shot07.png (app version shown 'bidbag Mobile v7.4.54').
  - implication: Use one money field formatted for the listing currency, explain status icons in text, and use readable countdown type.
- **CT16** [high] bidbag features:
- Adding items: share-to-app from the browser or eBay app, link and watchlist import.
- Lists with 'Group Bidding' / 'Win one auction'.
- Event Rules (new in Oct 2026): 'If won / If lost then bid on / then cancel'.
- 'Triple Bid' redundancy from German data centres, and a lead time of 1 to 10 s on the web.
- Notes and tags, CSV and iCal export.
- Email and SMS alerts, including replying 'GEBOT X,XX' to an outbid SMS to raise the max (10 cents per SMS).
- An AI 'Professional value analysis' (1 point).
- Pricing: 7 free credits, then 1 credit per won auction from $0.19. The eBay key is valid 18 months.
  - evidence: Play description and recent changes (/var/tmp/sniper-research/design-teardown/parsed/com.bidbag.mobile.txt); https://www.bidbag.us/ page text and https://www.bidbag.us/faq/ (German FAQ: 'Triple Bid', 'GEBOT X,XX', 'Der Schlüssel ... 18 Monate gültig'); screenshot shot06.png (Android share sheet 'Share via bidbag Mobile').
  - implication: Share-to-app is the natural add flow on Android. Raising the max straight from an outbid alert is a valued pattern (bidbag only offers it through paid SMS). Event rules are a power feature, optional for v1.
- **CT17** [high] bidbag's 'Smart Bidding' / 'Automatischer Erhöhungsschritt' can bid above the user's maximum: a 2026-02-27 review says 'I set a max of 375 and it made a bid of 400'. The FAQ warns the option can raise your maximum and says it is off by default.
  - evidence: Play review 2026-02-27 with developer reply ('Its a feature, not a bug!'); review 2023-04-30 (20 euros over max); bidbag FAQ: 'Achtung: Diese Option kann Ihr Maximalgebot ... erhöhen. Als Standardeinstellung ist diese Option deshalb deaktiviert.'
  - implication: Make the max a hard ceiling. Any 'next increment' helper must show the exact worst-case amount and need a deliberate per-snipe opt-in.
- **CT18** [medium] eBay's consent screen for bidbag scared a 2025 user by listing broad permissions: 'View and manage your eBay marketing activities... inventory and offers... account settings... order fulfillments'. The developer said the scopes are set by eBay.
  - evidence: Play review 2025-09-22 (v70.42.11) with developer reply 2026-02-06.
  - implication: Sniping with OAuth carries a scope 'scare'. Our WebView approach avoids it, but it must show that the user is on genuine eBay pages (visible host, no injected login form).
- **CT19** [high] Auction Sniper's 2025 redesign:
- Layout: bottom navigation Search / My Watches / My Snipes / More, rounded cards, and tabs ACTIVE / WON / LOST.
- Visual problems in its own Play screenshots: the status-bar clock overlaps the app-bar title, the gesture handle draws through the 'My Watches' / 'My Snipes' labels and the Add Snipe button, and item thumbnails are cropped at the screen edge.
- Contrast and layout: grey low-contrast titles, a permanent 'Important Notice' card, a 'Choose Filter' sheet that only holds sort options, and three differently coloured outline buttons (Clear / Save / Advanced).
- Formatting: 'Time left: Ended' in red on the Won tab, US dates with no time zone ('Ends: 12/12/2025 at 1:33AM'), and 'Current: 10.00' with no currency.
  - evidence: /var/tmp/sniper-research/screens/com.auctiva.auctionsniper/shot03.png to shot09.png and shot11.png to shot16.png (dates visible in UI: 09/25/2025, 12/12/2025).
  - implication: Android 15+ enforces edge-to-edge for new target SDKs (inferred platform fact). Handle WindowInsets in Compose (Scaffold, safe drawing) and test on the Pixel 9a with gesture navigation. Always show currency and local time zone.
- **CT20** [high] Auction Sniper's Add Snipe form has: 'Item Number *', 'Current Price', 'Lead Time (seconds) *' (5 by default), 'Maximum Bid *', and under Advanced 'Second Shot Bids: None'.
Its rules:
- Fee: 1.95% of the final price (min $0.35, max $35) after 3 free wins. The Play text still says 1.75%.
- No adding, changing or cancelling within 5 minutes of the end.
- Bids from 'multiple servers in multiple locations', retried up to 15 times on eBay errors.
- A 7 to 12 s lead time is advised on Sunday evenings (Pacific time).
  - evidence: Screenshot shot16.png (Add Snipe) and shot15.png (Edit Watch: 'Maximum Quantity', 'Maximum Offer ($)', 'Comment', 'Enable Notifications'). https://auctionsniper.com/help/ and https://auctionsniper.com/help/auction-sniper-pricing/ (© 2001-2026, fetched 2026-11-04). Status page https://www.auctionsniper.com/help/auction-sniping/status-messages/ ('Unknown Status... continues to bid for you up to 15 times'). Play description ('1.75%').
  - implication: A 5-minute edit lock is a user pain we can avoid. Keep 'advanced' bid features off the default path.
- **CT21** [high] Auction Sniper's December 2025 app update broke core flows. 19 of its 71 Play reviews since 2024 are 1-star.
  - evidence: Play reviews (/var/tmp/sniper-research/design-teardown/reviews/com.auctiva.auctionsniper.json): 2025-12-27 'ITEM DETAILS ERROR FAILED TO RETRIEVE WATCHES', 2026-01-29 'flash new app... doesn't work properly', 2026-02-07 'Latest version doesn't work', 2024-08-28 'all it says is "verifying ebay account connection"', 2025-05-31 PayPal funding not credited, 2024-08-06 'tried to update a snipe 3 minutes before it ended... error... won't let me go under 3 seconds'.
  - implication: For a personal app, the add → arm → bid → result path needs regression tests on every change. A cosmetic redesign must never risk it.
- **CT22** [high] Myibidder (3.18 stars from 7,154 ratings, 500,000+ installs, updated 2026-06-07, contains ads) still shows store screenshots from Nov 7, 2019:
- A menu-first home screen.
- Items listed with 'Time Left: 2/20:21:51' (days/hh:mm:ss, readable as a date) and 'My Snipe: US $ 250.06' or '<not set>', but no current price.
- A snipe screen with an 'Activate:' toggle separate from 'Save', 'Cur.Bid:' and 'Time: 5 sec.'.
- A footer 'Updated: Nov 7, 2019 8:50:48 PM' with a manual refresh button.
- A radioactive-trefoil logo.
  - evidence: /var/tmp/sniper-research/screens/com.myibidder.aclient1/shot01.png to shot06.png and shot08.png; Play page fetched 2026-11-04.
  - implication: Never show a countdown in a format that reads like a date. Always show the current price next to the max, and a freshness label.
- **CT23** [high] Myibidder's two-step arming (Save, then a separate 'Activate' toggle) caused missed auctions: 'you have to actually move the active toggle or it doesn't save the snipe, missed 3 auctions' (2025-01-13). Snipes must also be 'added & activated' at least 3 minutes before the end and cancelled at least 1 minute before.
  - evidence: Play review 2025-01-13 and developer reply; Play description; FAQ https://www.myibidder.com/login/faq/4 q5-q6.
  - implication: Saving a max should arm the snipe by default, shown with a clear 'Armed' state. Never let an unarmed snipe sit silently near its end; notify the user.
- **CT24** [medium] Embedded-browser sign-in breaks for some users:
- A 2025-11-16 Myibidder review quotes Google's error: 'eBay's request does not comply with Google's "Use secure browsers" policy.'
- Others report being 'stuck in 0auth2 hell' (2026-08-16).
- bidbag (2019): 'This can't be completed on a small screen' and (2020) a captcha in the in-app browser.
- Baytomat (2026-08-24): 'stuck in a loop of popups when trying to log in'.
  - evidence: Play reviews in /var/tmp/sniper-research/design-teardown/reviews/com.myibidder.aclient1.json, parsed/com.bidbag.mobile.txt, reviews/com.baytomat2.json.
  - implication: Our WebView sign-in will fail if the user signs into eBay with Google (and likely other third-party sign-in providers; inferred). Detect this and guide the user to a supported method (eBay password or passkey, to be verified). Persist cookies and show sign-in state clearly.
- **CT25** [high] eSnipe:
- Lead time 'Snipe in the last 7, 5, 3 or 1 second'.
- Bid groups 'used to win only one auction'; snipes must be at least 30 seconds apart.
- 'Minimum Bid Protect' (opt-in second snipe at the minimum bid if it is at most max plus one increment).
- Snipe Tool browser extension ('Over 20,000 users... every month') and an installable web app (PWA).
- eBay link through an eBay redirect, valid 18 months.
- Pricing: 3-day unlimited trial, then WinPoints. 30 points (= $0.30) for wins up to 19.99, 1.5 points per $1 from 20.00 to 1,999.99, 3,000 points (= $30) at 2,000 or more. In effect since 2024-05-11.
  - evidence: https://www.esnipe.com/features, https://www.esnipe.com/pricing, https://www.esnipe.com/help/article/8401911482125-bid-group, .../10064272205965-minimum-bid-protect, .../36768526684301-update-re-link-ebay-account, https://www.esnipe.com/help/news/26595902777997-winpoint-pricing-changes, https://www.esnipe.com/app (all © 2026, fetched 2026-11-04).
  - implication: The eSnipe web app is the most coherent service. Copy its granular statuses and outcome cards, and offer a 1 to 7 s lead time range.
- **CT26** [high] eSnipe has 19 named statuses: Ready, Scheduled, Checking, High Bidder, Cancelled, Cancelled by Group, Ended Early, Won, Won (Manual), Below Minimum, Max Bid Low, Outbid, Reserve Not Met, eBay Link Issue, Bid Blocked, Watch, Not Certain, Auction Site Error, Missed. Its won cards show 'Final 47.91 USD', 'Max Bid 53.77 USD', '3 second buffer', 'Saved 5.86', 'Your snipe was the last bid.' and a 'Log' button. Its add box accepts 'eBay Item # or link'.
  - evidence: https://www.esnipe.com/help/article/360060702591-snipe-definitions-statuses; help screenshots /var/tmp/sniper-research/screens/esnipe.com/help_18070942658445.jpg (won card dated 07/28/23), help_5995996003981.png (add box), help_5996014818189.jpg (Snipe/Watch tabs, 'Buffer 3 seconds', 'Max Bid (USD)', 'Bid Group (Win one auction)', 'Finish 5d 7h').
  - implication: Ready-made vocabulary for our state machine and result screen. Add savings to won results and a per-snipe log.
- **CT27** [high] BidSlammer free plan: '8-10 Second Lead Time', watch list and bidding list import, Slam-It toolbar and 'Log in with eBay' single sign-on.
Premium (from $8/month, billed yearly) adds:
- 1-second lead time and '3-2-WON' (three bids at once from Oregon, Los Angeles and Dallas at 3, 2 and 1 s).
- Bid groups (auctions at least 5 minutes apart).
- 'Outbid Alerts (since eBay can't send them)', SMS, a real-time snipe log, analytics and an API.
Also offered: pay-per-use at $2 a win. Edits are allowed until 5 s before close; changing the lead time means re-adding the snipe.
  - evidence: https://www.bidslammer.com/ , https://www.bidslammer.com/faq/ , https://www.bidslammer.com/pricing/ (© 2001-2026, fetched with curl 2026-11-04, saved under /var/tmp/sniper-research/design-teardown/web/). API doc https://www.bidslammer.com/help/api-documentation/ : addBid?key=&item_no=&bid=&newGroupName=&delay=(1-15)&enabled=&force=; sample response delay 7.
  - implication: An outbid alert is a selling point exactly because eBay cannot send one for a snipe that has not been placed. Our 'price passed your max' notification fills that gap without a subscription.
- **CT28** [high] BidSlammer dashboard:
- Summary tiles: 'Queued 6', 'Active Bids $433.00', 'Subscr. Valid 481d', 'Auth Expires 533d'.
- Cards: large price ('90.75 USD', '12 bids'), seller with feedback percentage, 'Ends: 2d 02h', and an editable max with green-check / red-X buttons.
- Statuses shown as emoji faces ('Snipe Set', 'Won!', 'Win in group'); list rows tinted red or green.
- Add-snipe preview: 'Minimum to bid: 202.5 USD', 'Ends in 8m 24s', '64 bids', 'Quantity: 1', 'Buy it Now option: No', seller and since-year, shipping, plus a 'Bid Activity' chart.
- A 'win line' chart plots snipe bid against closing price.
  - evidence: /var/tmp/sniper-research/screens/bidslammer.com/scr-best-dashboard.png, scr-add-snipe.png, scr-list-view.png, scr-win-line.png (from https://www.bidslammer.com/screenshots/).
  - implication: Adopt a preview before the snipe is set (minimum to bid, shipping, seller, bid history) and an eBay session health tile. Avoid emoji-only status and unlabelled colour tints.
- **CT29** [high] BidSlammer documents 50+ ways a snipe can fail. Many are eBay-side checks or interstitial pages that a WebView bid will meet directly: 'Bid Increment Not Met', 'Proxy Bid Waiting', 'Blocked By Seller', 'Bad Ship Location', 'Blocked - Seller Buy Limit Reached', 'Cutlery Age Disclaimer', 'Customs/Duties Disclaimer', 'Adult Item Disclaimer', 'Buy It Now Exact Value Required', 'Token Invalid', 'Account Suspended'.
  - evidence: https://www.bidslammer.com/help/status-messages ('There are over 50 ways you can lose a snipe, and we track almost all of them' in https://www.bidslammer.com/faq/).
  - implication: Pre-flight checks (ships to UK, buyer requirements, disclaimers, Buy It Now cap, reserve) and plain-language failure explanations are core UX for us, not edge cases.
- **CT30** [high] Bidnapper (Abercrombie Online LLC, since 2001; sister mobile app EZsniper com.ezsniper, 500+ installs, last updated 2024-01-26):
- Pricing: $2 per win prepaid, or $8.33/month billed yearly.
- Default 'interval' 4-5 s.
- 'All additions, deletions, and changes... must be done prior to six minutes before the end'.
- Statuses: Pending, Queued, Won, Done, Bid Too Low, Reserve, Early, Late, Error, Password, Blocked; a 'Response' button shows eBay's reply.
- BULK ADD (comma lines), SNAPPER toolbar, re-link every 18 months with reminders, and an alert 'if bidding goes beyond your bid'.
  - evidence: https://www.bidnapper.com/help/ and https://www.bidnapper.com/pricing/ (fetched 2026-11-04); https://www.ezsniper.com/ (same operator footer '© 2001 - Abercrombie Online, LLC'); Play page com.ezsniper.
  - implication: A 6-minute lock is the worst in class. Showing eBay's actual reply ('Response') is a valued idea; capture it automatically.
- **CT31** [high] EZsniper's list rows truncate data off-screen ('Price USD 207.5 Shipping 11.45 Bic...', 'Bids :'). They show seller feedback ('gr_liquidators (15252 / 97.7%)') and lost reasons in capitals ('BID TOO LOW'), have an 'Ad Supported' account status and a 'Mad Settings' marketing caption.
  - evidence: /var/tmp/sniper-research/screens/com.ezsniper/shot01.jpg to shot03.jpg.
  - implication: Shipping and seller data belong on the card, but laid out so nothing is clipped on a 6.3-inch screen.
- **CT32** [high] JBidwatcher (desktop Java, last commit 2018-02-20):
- Default snipe time 30 s.
- MultiSnipe groups, each given a user-chosen background colour.
- 'Auto-subtract shipping and insurance (p/p)' for setting a total budget.
- A 'Dangerous Multisnipe' warning when group auctions end within the snipe time of another.
- An option to 'Submit snipes to the Gixen web service', and time synchronisation.
  - evidence: /var/tmp/sniper-research/src/cyberfox_jbidwatcher/src/com/jbidwatcher/auction/AuctionEntry.java lines 410-426; src/com/jbidwatcher/auction/MultiSnipe.java; src/com/jbidwatcher/ui/SnipeDialog.java line 79; src/com/jbidwatcher/ui/commands/UserActions.java lines 574-581.
  - implication: Two proven ideas missing from current mobile apps: budget by total cost (subtract postage) and conflict warnings for groups.
- **CT33** [high] Biet-O-Matic BE (a browser extension that bids from the user's own logged-in browser tabs, so the closest model to ours; last commit 2021-01-22):
- End time shown both ways: '14.12.2019, 12:10:27 (vor 2 Tagen)'.
- Shipping column, eBay page status text per row.
- Two levels of arming: a global 'Automatikmodus' plus a per-item 'Aktiv'. Its log shows aborts: 'Automatisches bieten für Artikel inaktiv'.
- A 'Simulated bidding' mode that runs to the last step without confirming.
  - evidence: /var/tmp/sniper-research/src/sweitzel_Biet-O-Matic-BE/repo/README.md; screenshots copied to /var/tmp/sniper-research/screens/biet-o-matic-be/bom_overview.png and bom_log.png.
  - implication: A dry-run that walks to eBay's confirm page builds trust for local bidding. Two-level arming is a failure mode to avoid.
- **CT34** [high] Snipesnipe (a self-hosted local-browser sniper for eBay Canada, commit 2026-10-15) uses explicit safety language:
- 'Rehearsal is the default', 'Saving never arms a bid; scheduling/arming is a separate action', and a 'Review & arm' step to check amount, currency and end time.
- 'The UI labels your browser's timezone and stores UTC'.
- 'Clicked means a click was dispatched, not that eBay accepted the bid'.
- It accepts full URLs, item numbers and 'https://ebay.io/m/…' share links.
  - evidence: /var/tmp/sniper-research/src/pauljones0_snipesnipe/repo/README.md.
  - implication: Honest status meanings (dispatched vs accepted vs won) and parsing short share links are worth copying. Separate arming conflicts with CT23 and is better kept out of the default path.
- **CT35** [high] Monitoring-only competitors set the 2026 visual baseline:
- Deal Hunt for eBay Auctions (com.zeedub.ebaybuyertool, released 2026-08-06) shows '$12.50', 'Used' chip, '$4.00 shipping • 6 bids', '9m left • Ends 2:03 PM EDT', and reminder chips '1h / 15 min / 10 min / 5 min / 1 min'.
- eSnipe.app (not linked to esnipe.com) is a free alerts service with checks 'as often as every minute', Discord/iOS/Android push, and 'no eBay login required'.
  - evidence: /var/tmp/sniper-research/screens/com.zeedub.ebaybuyertool/shot01.png and shot02.png; https://esnipe.app/ (© 2026).
  - implication: Show price, shipping and bids on one line, time left plus the local clock time with time zone, and chip-style presets. That is the modern bar.
- **CT36** [high] Every competitor that bids for you does it from a server and sells that as the core benefit:
- Baytomat: 'we bid even when the app is off'.
- Gixen: 'your smartphone or computer doesn't have to be on'.
- Myibidder: 'you can turn off or disconnect your phone after you scheduled your snipe'.
- bidbag: 'Your device can stay offline'.
  - evidence: Play descriptions in /var/tmp/sniper-research/design-teardown/parsed/com.baytomat2.txt, com.gixen.gixen.txt, com.myibidder.aclient1.txt, com.bidbag.mobile.txt.
  - implication: Our model reverses the category's main promise. The UI must make phone readiness visible (charge, network, exact alarm, battery optimisation) and offer a fallback when the phone cannot bid.
- **CT37** [high] The 18-month eBay link renewal is common across services (Gixen, bidbag, eSnipe, Bidnapper, Auction Sniper; BidSlammer shows an 'Auth Expires' countdown), and it causes painful moments: 'I had to reconnect... every 18 months.. what a pain!! ... took all afternoon to fix' (Auction Sniper review 2021-05-16).
  - evidence: Gixen FAQ; bidbag FAQ ('18 Monate gültig'); eSnipe help article 36768526684301; Bidnapper help ('eBay requires you to re-link every 18 months, but we keep track of this and let you know'); Auction Sniper Play review 2021-05-16; BidSlammer dashboard screenshot.
  - implication: Our cookie session has its own (unknown) lifetime. Users need the same 'we track it and warn you early' behaviour, made concrete as session checks before each armed auction.
- **CT38** [high] The most damaging complaint across apps is a snipe that was silently not placed, or a result nobody explains.
  - evidence: Myibidder 2026-06-21 ('after it ended, it said bid not set, but i made sure it was!'); Baytomat 2026-04-27 ('only explanation given by app was "problem at ebay"'); bidbag 2025-03-24 (email said lost, 'it didn't put on a bid for me at all'); Gixen 2025-08-24 ('there's no evidence in my ebay account of even bidding'); Auction Sniper 2024-02-20 ('my bid wasn't even visible').
  - implication: Each snipe needs a timeline (checked, armed, bid sent, eBay response, final result), with eBay's exact response text and a screenshot of the result page as a receipt.
- **CT39** [high] Users praise reliability and value far more than looks, and accept dated UI. Gixen's newest 200 Play reviews (2025-07-24 to 2026-10-26) are 182 five-star and 5 one-star, with comments like 'bit clucky UI but it works', 'the design is a bit dated 2000s style', 'slightly clunky but charming'.
  - evidence: /var/tmp/sniper-research/design-teardown/reviews/com.gixen.gixen.json (fetched via Play batchexecute UsvDTd, newest sort, 2026-11-04).
  - implication: The design must never trade away reliability. Better design is the user's own motivation, so the visual gap is a real opening.
- **CT40** [high] How late you can still change a snipe varies widely:
- Bidnapper: 6 minutes before the end.
- Auction Sniper: 5 minutes.
- Myibidder: must be active 3 minutes before, can be cancelled up to 1 minute before.
- BidSlammer: 5 seconds.
Users hit these locks when prices move late.
  - evidence: Bidnapper help; Auction Sniper help ('less than 5 minutes left'); Myibidder FAQ q5-q6; BidSlammer FAQ ('at least 5 seconds before it closes'); Auction Sniper review 2024-08-06.
  - implication: Bidding on the phone allows edits until a short, visible lock (for example a few seconds before the bid fires). Show the lock time on the card.
- **CT41** [high] Required spacing between items in a group differs by service, and a one-minute extension makes it worse:
- Gixen 2 minutes, eSnipe 30 seconds, Myibidder 1 minute, BidSlammer 5 minutes.
- Auction Sniper refuses to bid until the earlier result is known ('Bid Group not placed').
- JBidwatcher warns with 'Dangerous Multisnipe'.
- Gixen's moderator notes 'a dependent auction may be won before the first one ends' under extensions (2026-09-21).
  - evidence: Gixen FAQ; eSnipe bid group article; Myibidder FAQ section 2 q11; BidSlammer FAQ; Auction Sniper status messages; JBidwatcher UserActions.java; Gixen forum t=11808.
  - implication: A group screen should show end times on a timeline, flag conflicts, and explain what happens if two items end close together.
- **CT42** [high] Outbid and result alerts by service:
- Baytomat: push and email matrix (outbid, won, lost).
- bidbag: email and SMS, with SMS reply to raise.
- Auction Sniper: push for outbid, lose and win.
- BidSlammer: premium outbid alerts and SMS.
- Bidnapper: alert when bidding passes your bid.
- Gixen's app: email only; users ask for push.
  - evidence: Baytomat settings screenshot shot08.jpg ('if overbidden / if auction won / if auction lost', Push and Email columns); bidbag FAQ; Auction Sniper Play description; BidSlammer home; Bidnapper home; Gixen Play review 2025-09-25.
  - implication: A native notification that the price has passed your max, with a 'Raise max' action and a suggested amount, is the strongest single interaction win.
- **CT43** [high] Users want postage, seller and fees on the screen where they set the max, and they budget by total cost. A 2026 review flags eBay's 2025 buyer protection fees.
  - evidence: Gixen Play reviews 2023-07-01 (seller feedback, postage), 2024-09-21 ('I always take postage into account when setting my snipe'), 2025-09-25; Myibidder review 2026-03-08 ('has not updated since eBay's buyer protection fees were added in 2025'); JBidwatcher 'Auto-subtract shipping'.
  - implication: Show and let the user budget by total cost in GBP: max bid plus postage to the UK plus any buyer protection fee. The fee formula was not verified here.
- **CT44** [high] Pricing across the category:
- Gixen: free (4 wins a month) or $11.99/year.
- eSnipe: about 1.5% per win (min $0.30, max $30).
- Auction Sniper: 1.95% (min $0.35, max $35).
- BidSlammer: $8/month or $2 a win.
- Bidnapper: $2 a win or $8.33/month.
- Baytomat: 1 to 25 points a win at 0.70 to 1.25 € per point.
- bidbag: from $0.19 a win.
- Myibidder: free web, ads or credits in the app; a reviewer cites a $15/month option.
Users dislike percentage fees ('really offended by sniping services that want a percentage', Reddit 2025-03-13).
  - evidence: Sources in CT4, CT12, CT16, CT20, CT25, CT27, CT30; Myibidder review 2025-11-12; archived Reddit thread 'Is sniping still a thing?' (created 2025-03-13) http://web.archive.org/web/20250405193228/https://www.reddit.com/r/Ebay/comments/1ja4bdt/is_sniping_still_a_thing/ ; comment 'i only pay $6/year for unlimited bid snipes'.
  - implication: Our app has no fees or quotas. The UI should never show credits or points, which removes a whole class of complaints.
- **CT45** [high] Watchlist import is clumsy everywhere:
- Gixen imports items with max 0, so they need an edit before they bid.
- Auction Sniper only imports auction-format watches and shows a permanent 'Important Notice'.
- Myibidder makes you tap every item, includes ended items, and 'Doesn't sync' (2026-10-10).
- BidSlammer and eSnipe treat max 0 as a watch-only item.
  - evidence: Gixen FAQ ('Imported items get a price of 0'); Auction Sniper screenshot shot05.png; Myibidder reviews 2020-05-03, 2022-04-25, 2026-10-10; BidSlammer FAQ; eSnipe statuses ('Watch').
  - implication: Offer a 'from your watchlist' inbox (auctions only, ending soonest, ended items hidden) where you set the max with one tap. Make 'watching' and 'armed' visually distinct.
- **CT46** [high] Competitors' time displays are inconsistent and often ambiguous:
- Gixen: '2 days, 34 minutes'.
- Baytomat: '04d 19:24:53 (-4 s)'.
- bidbag: '1 Days 11h 04m 28s'.
- Myibidder: '2/20:21:51'.
- Auction Sniper: 'Ends: 12/12/2025 at 1:33AM' with no time zone.
- eSnipe: 'Finish 5d 7h'.
- BidSlammer: 'Ends: 2d 02h'.
Only Deal Hunt and Biet-O-Matic pair time left with the local clock time (Deal Hunt also shows the zone).
  - evidence: Screenshots listed in CT10, CT11, CT15, CT22, CT19, CT26, CT28, CT33, CT35.
  - implication: Use one format: time left (with seconds under 1 hour) plus the end in Europe/London time (for example 'Ends 21:04:37 Sun (UK)'). Use tabular figures so the digits do not jump.
- **CT47** [high] Several apps signal state by colour alone or with low contrast: Baytomat's red/green bid text, BidSlammer's row tints and emoji, bidbag's coloured bars and badges, Gixen's light-purple underlined titles, Auction Sniper's grey titles.
  - evidence: Screenshots listed in CT10, CT11, CT15, CT19, CT28.
  - implication: Pair colour with an icon and words ('Winning at £42', 'Outbid: £61 > your £55'). Check contrast in both light and dark themes.
- **CT48** [high] Destructive actions sit too close to frequent ones: Gixen's DELETE ITEM next to EDIT ITEM at equal weight, Baytomat's LOGOUT next to a red DELETE ACCOUNT, and Auction Sniper's REMOVE FROM WATCHLIST just under ADD TO SNIPES.
  - evidence: /var/tmp/sniper-research/screens/com.gixen.gixen/shot01.png; com.baytomat2/shot08.jpg and shot14.jpg; com.auctiva.auctionsniper/shot14.png.
  - implication: Put destructive actions in overflow menus with undo (a snackbar). Never place them beside 'edit max'.
- **CT49** [medium] Users handle ties and round numbers by bidding odd amounts. Myibidder's FAQ suggests '$50.03 or $50.04'. Ties go to the earlier bid, which works against a snipe placed at the last second.
  - evidence: Archived Reddit thread created 2024-08-30 (r/Ebay 'Does max bidding prevent sniping?') http://web.archive.org/web/20250804100204/https://www.reddit.com/r/Ebay/comments/1f4nx6i/does_max_bidding_prevent_sniping/ ; Myibidder FAQ section 2 q7; Baytomat FAQ ('If there are two equal bid amounts, the EARLIER one wins'); Reddit 2023-11-22 thread (archived 2025-03-07).
  - implication: When the user types a round max, suggest a few pence extra and explain why in one line.
- **CT50** [medium] Outages and third-party dependencies hit users even when the sniper itself works:
- Gixen 'down all day' (Trustpilot 2026-06-01; Gixen blamed a datacentre failure).
- Baytomat blamed 'a huge server failure' at eBay on 2026-04-27.
- Auction Sniper's PayPal funding failures (2025-05-31).
  - evidence: Trustpilot gixen.com review 2026-06-01; Baytomat developer replies 2026-05-02; Auction Sniper Play review 2025-05-31.
  - implication: Bidding on the phone removes the sniper-server dependency but not eBay outages. The result screen must say when eBay failed to respond, with timestamps.
- **CT51** [medium] The category's visual identity is weapon and target imagery: an archery target (Gixen), an orange bullseye (Baytomat), a target on stripes in eBay's four brand colours (bidbag), a crosshair with a serif 'A' (Auction Sniper), a radioactive trefoil (Myibidder), an hourglass (EZsniper). Only Deal Hunt uses a neutral price-tag icon.
  - evidence: /var/tmp/sniper-research/screens/icons_contact_sheet.png built from each app's Play icon.
  - implication: A calmer timing or price identity (clock, gavel, tag) will stand out. Avoid eBay's colours and logo; using them may be a trademark risk (inferred).

## Open questions
- Has eBay UK (ebay.co.uk) or eBay US turned on the 1-minute 'bid time extension' for any categories as of November 2026? The only source is Gixen's admin (2026-09-05), who says AU went live about 2026-09-14 and that UK and US 'may follow'. This needs a primary eBay source.
- What is the exact eBay UK buyer protection fee formula, and is it shown on auction listings and the bid review page? My one eBay help fetch returned an error page.
- How does the user sign into eBay: password, passkey, Google or Apple? Google blocks embedded WebView sign-in ('Use secure browsers'). Does Android WebView on the Pixel 9a support passkeys/WebAuthn for eBay sign-in (androidx.webkit), and does it survive sessions long enough?
- How long does an eBay web session in a WebView stay valid without re-authentication (2FA or captcha)? This decides how often session checks and pre-flight alerts are needed.
- What does an eBay share link from the eBay Android app look like in 2026 (Snipesnipe mentions https://ebay.io/m/…)? Is resolving it on the phone acceptable?
- Gixen API: is 'Enable API access' a real setting, and will Mirror subscribers get API access in 2026? The official 2008 doc post is gone, and third-party code reports 'ERROR (501): API DISABLED'.
- What does Gixen's Play in-app purchase priced '£2.79 if billed through Play' buy (Mirror term?)?
- Exact behaviour of Auction Sniper 'Second Shot Bids' (whether it can exceed the max), and whether bidbag's 'Certified application ... on eBay USA' claim means it has official bidding API access.
- Which eBay pre-bid disclaimer or interstitial pages (age, customs, recall, ticket terms) appear in the 2026 mobile web bid flow on ebay.co.uk? These affect both the WebView automation and the pre-flight warnings.
- Reddit coverage is thin (403 to this host); only three archived r/Ebay threads (2023-11-22, 2024-08-30, 2025-03-13) were readable. More 2025-2026 user sentiment could come from another network route.

## Report
# Competitor teardown: eBay sniper apps and services, visual and interaction design (researched 2026-11-04)

## 0. Method, sources and limits

- **Play pages.** Fetched 2026-11-04 with curl, a desktop Chrome User-Agent and `hl=en&gl=GB`. Saved to `/var/tmp/sniper-research/design-teardown/play-pages/<package>.html` and parsed with my own script (`scripts/parse_play.py`). Parsed text and JSON are in `/var/tmp/sniper-research/design-teardown/parsed/`.
- **Play reviews.** The newest 200 reviews per app came from Play's public `batchexecute` endpoint (rpc `UsvDTd`, sort = newest) via `scripts/play_reviews.py`, saved in `/var/tmp/sniper-research/design-teardown/reviews/<package>.json`. I also used the 20 reviews embedded in each Play page.
- **Screenshots.** Taken from `play-lh.googleusercontent.com` with the suffix replaced by `=w1440-h1440`. Saved to `/var/tmp/sniper-research/screens/<package>/shotNN.(png|jpg)` and all viewed with the Read tool. Baytomat and EZsniper images are JPEG files and were renamed `.jpg`.
- **Extra screens:**
  - `/var/tmp/sniper-research/screens/bidslammer.com/` (from https://www.bidslammer.com/screenshots/)
  - `/var/tmp/sniper-research/screens/esnipe.com/` (eSnipe help-centre images)
  - `/var/tmp/sniper-research/screens/biet-o-matic-be/` (from a GitHub repo)
  - `/var/tmp/sniper-research/screens/icons_contact_sheet.png`
- **Web pages.** Read with WebFetch, or curl plus my html2text script, for: gixen.com (FAQ, index, forum topics), esnipe.com, bidslammer.com, bidnapper.com, ezsniper.com, auctionsniper.com, myibidder.com, baytomat.com (FAQ JSON), bidbag.us, trustpilot.com.
- **Reddit.** Returns HTTP 403 to this machine and WebFetch is blocked. I used Wayback snapshots of three r/Ebay threads, created 2023-11-22, 2024-08-30 and 2025-03-13.
- **GitHub code (read only, never run):**
  - `/var/tmp/sniper-research/src/cyberfox_gixen` (2015)
  - `redyinn_Preisradar-code` (2026-07-22)
  - `leesouthwart_Pokemon2` (2026-10-27)
  - `hsukenooi_gixen-cli/repo` (2026-06-01)
  - `cyberfox_jbidwatcher` (2018-02-20)
  - `sweitzel_Biet-O-Matic-BE/repo` (2021-01-22)
  - `pauljones0_snipesnipe/repo` (2026-10-15)
- **eBay traffic.** One WebFetch to a www.ebay.co.uk help URL (buyer protection fee), which returned an eBay error page. No other request touched an eBay host.
- **Marking.** Anything marked "(inferred)" is my reading, not a quoted source.

## 1. Snapshot of the Play apps (Play page data, 2026-11-04)

| App (package) | Developer | Rating (ratings) | Histogram 1★..5★ | Installs (exact) | Last update | Version / min Android | Monetisation on Play | First release |
|---|---|---|---|---|---|---|---|---|
| Gixen eBay Auction Sniper (com.gixen.gixen) | Gixen | 4.74 (1,044) | 40/0/0/101/894 | 10,000+ (24,459) | Aug 18 2024 | 2.1.1 / 7.0 | IAP "£2.79 if billed through Play" | Feb 7 2023 |
| Sniper for ebay bid auctions (com.baytomat2) | THINKLAB GmbH | 4.81 (9,782) | 332/79/121/37/9,193 | 100,000+ (225,536) | Oct 9 2026 | 14.2 (per review reply) | points | Apr 25 2019 |
| Auction bid sniper for eBay (com.bidbag.mobile) | bidbag Mobile | 4.46 (2,039) | 236/0/16/76/1,692 | 100,000+ (104,589) | Oct 23 2026 | 8.22.95 / 6.0 | credits | Jan 11 2013 |
| Auction Sniper (com.auctiva.auctionsniper) | AuctionSniper | 4.13 (1,228) | 217/0/45/83/868 | 100,000+ (103,144) | Sep 30 2026 | 3.23.0 (per review) | % fee | Dec 16 2015 |
| Auction Bid Sniper for eBay (com.myibidder.aclient1) | Myibidder.com | 3.18 (7,154) | 2,500/643/283/500/3,218 | 500,000+ (858,769) | Jun 7 2026 | 2.2.72 / 7.0 | Contains ads; IAP £0.99 to £139.99 | Mar 22 2011 |
| EZsniper (com.ezsniper) | Abercrombie Online (operator of Bidnapper) | n/a | n/a | 500+ | Jan 26 2024 | | ad supported | |
| Deal Hunt for eBay Auctions (com.zeedub.ebaybuyertool), monitoring only | Cultivate Digital | n/a | n/a | 50+ | Aug 6 2026 | | | Aug 6 2026 |

Play search pages for "ebay auction sniper", "ebay sniper bid" and "sniper for ebay bid auctions" surface only these five bidding apps; everything else is games or unrelated. Other ids seen (`io.velarion.app`, `com.sniperpro.sniperpro`) are not eBay snipers: one is an EA FC trading tool, the other a trading course.

## 2. Feature matrix

### 2a. Bidding engine, timing and redundancy

| Service | Bids from | Lead time (default; range) | Latest edit/cancel | Redundancy | Extended-bidding handling (Nov 2026) |
|---|---|---|---|---|---|
| Gixen | Gixen servers | 6 s; 3/6/8/10/12/15 s (changing needs Mirror). 2026-09: Mirror bids 1 s before end on extension sites | not documented in sources read | Mirror: "snipes are sent from two hosting locations" ($11.99/yr). All users get an extra attempt "one second before auction end" (bid-shielding protection, 2023-07-26) | Bids in steps and waits up to 12 extensions before sending the max (eBay Motors and AU, 2026-09) |
| Baytomat | servers ("Several high-performance servers bid ... at the same time") | 4 s shown in settings; FAQ recommends 3-5 s weekdays, 6-8 s Sunday 18:30-21:30; >15 s too high | not stated | multiple servers | not found |
| bidbag | servers in German data centres | slider (3 s shown); web 1-10 s | not stated | "Triple Bid": each bid up to three times if an agent fails | not found |
| Auction Sniper | "multiple servers in multiple locations" | 5 s in Add Snipe form; users report a minimum of 3 s; 7-12 s advised on Sunday evenings (Pacific) | no add/modify/cancel in the last 5 minutes | retries "up to 15 times" on unknown eBay status | not found |
| Myibidder | servers | 6 s default, 6-10 recommended; "My Time" preference | must be active ≥3 min before end; cancel ≥1 min before | not stated | not found |
| eSnipe | servers "spread across multiple physical locations" | 7, 5, 3 or 1 s (3 s shown in help screenshot) | not stated | multi-location | not found |
| BidSlammer | servers in Oregon, Los Angeles, Dallas | Free: 8-10 s. Premium: 1 s; API `delay` 1-15 (sample 7) | until 5 s before close; changing lead time means re-adding the snipe | Premium "3-2-WON": 3 simultaneous bids at 3, 2, 1 s | not found |
| Bidnapper / EZsniper | servers, "multiple locations" | default 4-5 s | all changes before the last 6 minutes | multi-location | not found |
| JBidwatcher (desktop, 2018) | user's PC (or submit to Gixen) | 30 s default (`snipemilliseconds`) | n/a | can hand off to Gixen | n/a |
| Biet-O-Matic BE (browser extension, 2021) | user's own browser tabs (closest to our model) | configurable | n/a | none; the tab must stay open | n/a |
| **Our app** | **the user's phone (WebView session)** | free choice | can be seconds before the end (inferred) | single device: needs a readiness checklist and a fallback | must handle a moving end time |

### 2b. Groups and rules

| Service | Groups | Spacing rule | Extras |
|---|---|---|---|
| Gixen | pick a group number from the "[Bid Group:]" drop-down; "remaining bids canceled once an item in the group is won"; group snipes are not mirrored | items "do not have end times within 2 minutes of each other" | Mirror only: contingency groups ("first lost item cancels all the others"), multi-win groups |
| Baytomat | "Bidding in groups": bids on items one after another until the "desired quantity" is won; the remaining items are then deleted | not stated | dialog: "Create a group and put several (equal) auctions in it" |
| bidbag | each list can be set to "Nur eine Auktion gewinnen" / "Win one Auction" (Group Bidding toggle) | not stated | **Event Rules** (2026-10): "If won / If lost → then bid on / then cancel"; "Smart Bidding" automatic increments can exceed your max (off by default) |
| Auction Sniper | Bid Groups "folder with a set maximum price"; stops after a win | holds the next bid until the previous result is known ("Bid Group not placed") | "Second Shot Bids" (a review says "re bid last second 1 to 5 increments"; exact behaviour inferred) |
| Myibidder | groups with "To Win: N" | "at least 1 minute apart" | |
| eSnipe | "Bid Group (Win one auction)" field | "at least 30 seconds apart" | "Minimum Bid Protect" (opt-in second snipe at the minimum bid, at most max + 1 increment) |
| BidSlammer | Premium bid groups | "at least 5 minutes apart" | API `newGroupName` |
| JBidwatcher | MultiSnipe with a colour per group | "Dangerous Multisnipe" warning when items end within the snipe time | "Auto-subtract shipping and insurance" |

### 2c. Account, eBay link, security

| Service | Sniper account | eBay link | Expiry / re-auth UX | Security notes |
|---|---|---|---|---|
| Gixen | Gixen username + password (eBay credentials before 2017) | eBay authorization since 2017 | 18 months; an eBay password change "will automatically disable all authorizations"; red warning on login when expiring within 10 days; reminder emails (2020) | no 2FA (Trustpilot 2025-11-30); no fingerprint login in app (2023) |
| Baytomat | email + Baytomat password | FAQ: approve a connection to "our service partner Snip" on eBay. Play text still says eBay access data are "stored securely encrypted on our servers" | not stated | FAQ: works with the eBay security key (2FA); login popup loops reported (2026-08-24) |
| bidbag | username + password + email | "eBay Zugangsschlüssel" (token) | 18 months; earlier problems with small screens (2019) and captcha in the in-app browser (2020) | consent screen listed broad scopes (2025-09-22 review) |
| Auction Sniper | sign-up asks username, password, confirm password, email, timezone; email activation to unlock 3 free snipes | "secure connection" (no eBay password) | relink about every 18 months (review 2021); "Invalid eBay Link" status | "Configure PIN" in More |
| Myibidder | Myibidder username/password | OAuth2 link inside the app | users report links dropping ("not linked to Ebay" an hour later, 2024-04-27) | no 2FA; fingerprint unreliable (2025-02-09); Google blocks sign-in in the embedded view (2025-11-16) |
| eSnipe | eSnipe account | redirect to eBay login, "Agree" | "valid for only 18 months"; changing eBay login invalidates it | statuses include "eBay Link Issue" |
| BidSlammer | "Log in with eBay" single sign-on, no separate password | eBay token | dashboard tile "Auth Expires 533d"; "Renew Authorization" link; statuses "Token Invalid"/"Token Revoked" | revoke via eBay "Third-party app access" |
| Bidnapper | username/password/email | "eBay bidding API" link | "re-link every 18 months, but we keep track of this and let you know" | |
| Biet-O-Matic BE | none | the browser's own eBay session | session expiry breaks bidding | local only |
| **Our app** | none | eBay web session cookies in a WebView | unknown cookie lifetime: needs session checks (inferred) | add biometric app lock (inferred) |

### 2d. Adding items, import, monitoring, notifications, results

| Service | Add methods | Watchlist import | Price refresh | Notifications | Result reporting |
|---|---|---|---|---|---|
| Gixen | item ID + max; Import Watchlist; Autosnipe browser extension (Firefox/Chrome/Safari); Desktop Manager (64-bit, 2026-09); CSV (Mirror) | yes; imported max = 0, must edit | free: batch every 2-3 h (2023); subscribers on request if >10 min old; Mirror: end times hourly vs daily | email at end (review 2025-08); no push in app (review 2025-09) | statuses SCHEDULED, WON, OUTBID, "BID UNDER ASKING PRICE", ENDED; main and mirror shown separately |
| Baytomat | eBay import, eBay search, manual | yes (BIN ignored) | hourly; <1 h every 3 min; Sunday rush every 2 h | push + email for "if overbidden", "if auction won", "if auction lost" | RUNNING/WON/LOST tabs; "saved: 4 (1.7%)" |
| bidbag | share from browser/eBay app, link, watch list, manual, search; "bidbag Remote" extension; Delcampe also | yes | live updates on web; "Aktuell" shows last update date | email + SMS for outbid and won/lost; reply "GEBOT X,XX" to raise | email "Your highest bid was... sold for... Your minimum bid would have been..." |
| Auction Sniper | Search eBay + saved searches, My Watches, Add Snipe by item number | yes, auction format only | ≤30 min every 10 min; ≤1 h every 20 min; ≤1 day every 12 h; 7 days maybe none; "Update Prices" | push for outbid/lose/win; email; "Device Notifications" | about 29 statuses (Ready, Success, You Won!, Manual Win, Bid Too Low, Bid Below Increment, Proxy Outbid, Not in Time, Ended Early, Invalid eBay Link, Blocked by seller, ...) |
| Myibidder | item number, Import Watch List, Import Bidding List, Browse eBay, extensions | yes, tap each; includes ended items | 1-2 times per day; manual Refresh | email when done; email if not linked | ACTIVE/WON/LOST/COMPLETED |
| eSnipe | "Add Snipe: eBay Item # or link", Snipe Tool, add from watchlist, eBay Search | yes | not stated | "Auction outcome notifications", "Previous bid blocked warnings", combined-shipping notices | 19 statuses, per-snipe "Log", "Saved 5.86" |
| BidSlammer | item number, search, Slam-It toolbar, import watch and bidding lists, API, SMS | yes | "Refresh Bids/Prices" button | Premium outbid alerts, SMS, daily search alerts | 50+ status messages, real-time snipe log, analytics charts |
| Bidnapper | add form, My eBay Import, SNAPPER, BULK ADD (CSV lines) | yes | not stated | alert "if bidding goes beyond your bid"; re-link reminders | statuses + "Response" button showing eBay's reply |

### 2e. Pricing (all from vendor pages, 2026-11-04 unless noted)

- **Gixen:** free with "Up to 4 winning snipes per month"; Mirror $11.99/year.
- **eSnipe:** 3-day unlimited trial, then WinPoints: 30 points (= $0.30) for wins up to 19.99, 1.5 points per $1 from 20.00 to 1,999.99, 3,000 points (= $30) at 2,000 or more. Effective 2024-05-11.
- **Auction Sniper:** 3 free wins, then 1.95% of the final price (min $0.35, max $35). The Play text still says 1.75%.
- **BidSlammer:** free tier; Premium from $8/month billed yearly; pay-per-use $2 per win.
- **Bidnapper/EZsniper:** $2 per win prepaid, or $8.33/month billed yearly.
- **Baytomat:** 10 free points; packs from 8 points for €10 to 300 points for €210; 1 to 25 points per win depending on the final price.
- **bidbag:** 7 free credits, then from $0.19 per won auction; SMS 10 cents each.
- **Myibidder:** web free (donations); app credits, ads and optional subscriptions (a review cites $15/month); wins under 10.00 are free.

## 3. Design teardown per app (screens viewed)

### 3.1 Gixen app (com.gixen.gixen)
Screens: `/var/tmp/sniper-research/screens/com.gixen.gixen/shot01.png` (snipe list), `shot02.png` (Add a snipe), `shot03.png` (edit), `shot04.png` (menu open), `shot05.png` and `shot06.png` (login, tablet).

What it is: a web page in an app frame, footer "© 2006 - 2023 Gixen.com - Version BETA 1.0".

Layout: a large GIXEN.COM logo and hamburger take the top ~10%. Then two full-width navy buttons "ADD ITEM" / "IMPORT WATCHLIST", then grey cards.

Card content, with no hierarchy:
- "ItemId: 155388568558" (the label is a link)
- thumbnail
- title as a light-purple underlined link
- "Time to end: 2 days, 34 minutes"
- "Currency: USD", "Max bid: 0.00", "Current bid: 338.0"
- "Status (main): SCHEDULED", "Offset (main): 8", "Snipe group: Not grouped"
- equal grey buttons "DELETE ITEM" | "EDIT ITEM"

Problems:
1. The money values have no currency symbol; currency is a separate row.
2. Decimals are inconsistent ("338.0" vs "0.00").
3. A max of 0.00 is labelled SCHEDULED, so an imported watch item looks armed.
4. "(main)" leaks the mirror architecture.
5. The countdown has no seconds and no absolute end time.
6. The menu lists "Login" and "Log Out" together, which a 2023 review called confusing.
7. "Add a snipe" asks for "Item ID:" (no paste-link hint) and "Max bid:".
8. The edit screen labels the group "Snipe group: 0" while the list says "Not grouped".
9. Login reads "Please log in with your Gixen credentials"; account creation opens a browser (2023 review).

What users still love: it is simple and it works.

### 3.2 Baytomat (com.baytomat2)
Screens: `/var/tmp/sniper-research/screens/com.baytomat2/shot01.jpg` to `shot16.jpg`. Phone shots are from a German-locale Samsung; tablet shots are in English.

Main list:
- Top: title "Bids" (orange) and a "+" button; tabs RUNNING / WON / LOST; summary "Bids:3 / Credits: 125 / Needed: 3".
- Cards: title (2 lines); "Bid: €2.00 (1 points)" in green or red; "Actual: €1.00 (1 Bid)"; "Time left: 04d 19:24:53 (-4 s)"; thumbnail on the right.
- Footer: "Status: Connected to <user>" and "Credit points: 125 / saved: 4 (1.7%)".

"+" opens a side sheet "New bid" with "eBay import / eBay search / New bid".

New bid form: photo, title, "Actual €150.00 (0 Bids)", "Your max. bid 180" with a green tick, "Sec. before end 4 >", "Group (optional) -None- >". The eye icon opens the listing.

Groups screen: "GROUP NAME", "DESIRED QUANTITY", with the explainer dialog "Baytomat bids successively on all auctions in a group until the desired quantity of auctions has been won. All following auctions in this group will then be deleted."

Settings:
- a Push/Email checkbox grid for "if overbidden / if auction won / if auction lost"
- "Show pictures (longer loading times)", "Show warnings", "Sec. before end 4", "Your eBay domain ebay.de / ebay.com.au", "Show ended auctions Quarter/Month", "Language"
- a blue LOGOUT next to a red DELETE ACCOUNT

Long-press menu: Edit / Details / Share / Search similar / Delete (red) / Cancel.

Points shown inline: "$100.00 (3 points)".

Problems:
1. Status by colour only.
2. "Actual" mistranslation.
3. Untranslated German strings ("eBay Konto", "KONTO WECHSELN", "Warnungen anzeigen").
4. Mixed formats ("EUR 1,00" on import, "€1.00" in the list).
5. Empty state "-None-".
6. An account is required before seeing anything (review 2026-04-01).
7. Login popup loops (2026-08-24).

Worth copying: the lead time next to the countdown, the visible connection line, and the notification grid.

### 3.3 bidbag (com.bidbag.mobile)
Screens: `/var/tmp/sniper-research/screens/com.bidbag.mobile/shot01.png` to `shot07.png`. These are marketing frames on a navy background around a dark UI "bidbag Mobile v7.4.54"; `shot06` is an Android 4-era share sheet.

Header: "Credits: 108  In deposit: 4", a target logo, "Search active list...".

Cards:
- blue title text on dark grey with a green or blue "i" icon
- a tiny yellow pill "Time left: 10h 46m 05s"; red pills for others; "Ended: 5/23/2025 5:57 PM"; green "Won: 5/24/2025 7:45 AM"
- a white panel "Current bid € 480.00 / Maximum bid € 520.00"
- a vertical status column (yellow stripes with a circular-arrow icon, red with pause, green with a tick) and a gear button

Lists drawer: All / Won / Important / Ended / Rare Art, each with four coloured count badges (no legend); "List Actions" (New/Rename/Delete) and a "Group Bidding" toggle.

EDIT AUCTION dialog: "Maximum Bid £ [366] . [00] €" (two fields, two currency symbols), "Lead time in seconds 3s" slider, "Save changes"/"Cancel".

AI dialog "PROFESSIONAL VALUE ANALYSIS... Fees: Only 1 bidbag point each".

Marketing captions do not match the screens ("Reseller Tools for Smart Sellers" over an edit dialog).

Worth copying: Android share target, lists, event rules, editing lead time per snipe.

### 3.4 Auction Sniper (com.auctiva.auctionsniper)
Screens: `/var/tmp/sniper-research/screens/com.auctiva.auctionsniper/shot01.png` to `shot08.png` (phone, 2025 redesign), `shot09.png` to `shot16.png` (7-inch), `shot17.png` to `shot24.png` (10-inch).

Onboarding: SIGN IN / SIGN UP tabs. Sign-up asks Username, Password, Confirm Password, Email Address, Select Timezone. The disabled "Sign In" button is a pale gradient.

Bottom navigation: Search, My Watches, My Snipes, More.

Search: hero "Find Your Perfect Item"; saved searches as cards with empty grey circles; three outline pills Clear (grey), Save (green), Advanced (purple); "SORT: ENDING SOONEST". Results: "Current Price $29.99", "0 Bids", "Time Left: 28s" in red.

My Watches: a permanent "Important Notice: Only auction format watches are imported..." card; "Your Watches (1)"; "Tap any item to view details and manage your watch". The "Choose Filter" sheet holds Ending Soonest / Ending Later / Cancel.

My Snipes: ACTIVE / WON / LOST. Won cards: "Current Price US $22.50", "Ends: 09/25/2025 9:56PM", "Time left: Ended" (red), "Max Bid US $26.00", "Bids 3".

Item Details: a large cropped hero photo with a "1d 8h 17m" pill, "US $10.00", "0 bids", "Ends: 12/12/2025 at 1:33AM", then four stacked full-width buttons: ADD TO SNIPES, REMOVE FROM WATCHLIST (red outline), EDIT WATCH, VIEW ON EBAY.

Add Snipe: gradient header "Current: 10.00"; "Item Details" (Item Number *, Current Price 10.00); "Bid Settings" (Lead Time (seconds) * 5, Maximum Bid *); "Advanced Settings" (Second Shot Bids: None).

More: Account Information, Preferences, Email Notifications, Device Notifications, Configure PIN, Payment.

Visible bugs: the clock overlaps the title (shot09), and the gesture handle draws through the labels and the "Add Snipe" button (shot13, shot16).

Worth copying: the bottom-navigation layout, Add Snipe sections, separate email and device notification settings.

### 3.5 Myibidder (com.myibidder.aclient1)
Screens: `/var/tmp/sniper-research/screens/com.myibidder.aclient1/shot01.png` to `shot12.png`. The UI clock reads "Updated: Nov 7, 2019".

Login: radioactive-trefoil logo, "Myibidder Login", a cyan Login button, Reset Password, Create Account.

Home is a menu: "My Snipes: Items 7/7, Groups 3; Add Items: Add eBay items; My Account: Balance/Snipes 25, eBay Account Linked, Preferences, Logout; Support: Contact Support". Footer: "Updated: ... [refresh]".

Add dialog: Add eBay Item Number / Import Watch List / Import Bidding List / Browse eBay.

Items (ACTIVE/WON/LOST/COMPLETED): "Time Left: 2/20:21:51", grey item number, "BuyItNow: US $ 385.00", "My Snipe: US $ 250.06" or "<not set>". **No current price.**

Snipe screen: "Time Left", "Seller: tn61716(198)", "Cur.Bid: US $ 100.00", "My Snipe": "Activate:" toggle, "Maximum Bid: US $ [260.03]", "Group: Pixel 3", "Time: 5 sec.", a "Save" button, a trash icon in the footer.

Groups: "Pixel 3 / To Win: 1 / 7 item(s)".

### 3.6 eSnipe (web, PWA)
Screens: `/var/tmp/sniper-research/screens/esnipe.com/*` (help images, won cards dated 07/28/23).

- Add box: "Add Snipe: eBay Item # or link" with an orange "Enter" button.
- Empty state explains both add paths and offers "Add the Snipe Tool".
- Add page: Snipe / Watch tabs; "Minimum 1,999.00 USD"; "Buffer 3 seconds"; "Max Bid (USD)"; "Item Notes (Optional)"; "Bid Group (Win one auction)"; "WinPoints: 75"; Save; then Current / Bids / Finish "5d 7h" / Seller / Item #.
- Won list: tabs Won / Lost / All, a truck filter icon, page size 25, three layout icons (List, Tile, Compact).
- Won card: title, seller link, "2 days ago 07/28/23 13:18:18", boxes "Bids 12 | Final 47.91 USD (green border) | Max Bid 53.77 USD", a note line ("Demand Protection"), "3 second buffer", "Saved 5.86", "Your snipe was the last bid.", trash and "Log" buttons.

### 3.7 BidSlammer (web)
Screens: `/var/tmp/sniper-research/screens/bidslammer.com/*`.

Dashboard:
- left navigation: Watching (0), Current (6), Completed (51749), Archive, Bid Groups, Real Time Log, Preferences, Subscription
- tiles: Queued 6 / Active Bids $433.00 / Subscr. Valid 481d / Auth Expires 533d
- "Add a Snipe": Item Number, Max Bid with −/+ steppers, Lead Time 7, Bid Group "no group"
- "Search eBay US"
- "Current Snipes" cards with price, currency and bids on the left; title, item number, "by seller (99.8%)", "Ends: 2d 02h", an input with the snipe amount, and ✓ ✗ 👤 💬 buttons
- emoji legend: Snipe Set / Won! / Win in group

List view: tinted rows with columns status, picture, title, seller, bids, cost, your snipe, shipping ("3.29 Flat 5 to 7 days"), ends ("20h 57m 1s").

Add preview: "Minimum to bid: 202.5 USD, Ends in 8m 24s, 64 bids, Quantity: 1, Buy it Now option: No, Sold by nyczxjay403 (100%, since 2008), Shipping is free, Ships from Staten Island..." plus a "Bid Activity" chart.

### 3.8 EZsniper (com.ezsniper, Bidnapper's app)
Screens: `/var/tmp/sniper-research/screens/com.ezsniper/shot01.jpg` to `shot03.jpg`. Teal app bar "Watching" with a refresh icon; row text clipped ("Bic", "Bids :"); "5d 3h 17m 29s"; "Lost Auctions ... BID TOO LOW, Bid 200.00 Price 630.00 Shipping 0.00"; More screen with "Account Status: Ad Supported - eBay (all)", "Add By Auction Number", "Archive", "Lost", "Manage Logins", "Subscribe", "Connect to Ebay".

### 3.9 Reference designs outside the sniper category
- **Deal Hunt (2026).** `/var/tmp/sniper-research/screens/com.zeedub.ebaybuyertool/`. Segmented controls (All/New/Used; 5M/1h/3h/6h/12h/24h), sort chips (Ending, Price ↑, Price ↓, Bids ↑, Bids ↓), "55 auctions ending within 3 hours", cards "$12.50 [Used] / $4.00 shipping • 6 bids / 9m left • Ends 2:03 PM EDT / [Watch] [Open in eBay]", and a reminders bottom sheet with chips "1h 15 min 10 min 5 min 1 min".
- **Biet-O-Matic BE.** `/var/tmp/sniper-research/screens/biet-o-matic-be/`. Table with Artikelnr., Beschreibung, "Auktions Ende 14.12.2019, 12:10:27 (vor 2 Tagen)", Preis, Versandkosten, Status (eBay text in a green or yellow box), "Ihr Maximalgebot [2,54] [x] Aktiv"; the log shows timestamps with outcome text.

## 4. What users say (Play reviews 2024 to 2026, Trustpilot, Reddit)

Data volumes: Gixen newest 200 reviews span 2025-07-24 to 2026-10-26; Baytomat 200 span 2026-01-31 to 2026-11-01; bidbag 70 since 2024; Auction Sniper 71 since 2024; Myibidder 47 since 2024.

Star mix since 2024:
- Gixen: 5★ 182, 4★ 11, 3★ 1, 2★ 1, 1★ 5
- Baytomat: 5★ 186, 1★ 8 (rating likely inflated by credit incentives)
- bidbag: 5★ 62, 4★ 1, 2★ 2, 1★ 5
- Auction Sniper: 5★ 50, 4★ 1, 3★ 1, 1★ 19
- Myibidder: 5★ 20, 4★ 4, 3★ 6, 2★ 4, 1★ 13

### Top complaints (ranked by severity and frequency)
- **C1. Snipe silently not placed, or result unexplained.**
  - Myibidder 2026-06-21: "after it ended, it said bid not set, but i made sure it was!"
  - Baytomat 2026-04-27: "only explanation given by app was 'problem at ebay'"
  - bidbag 2025-03-24: email "We're sorry you lost..." although "it didn't put on a bid for me at all"
  - Gixen 2025-08-24: "no evidence in my ebay account of even bidding"
- **C2. App broken after an update or showing server errors.**
  - Auction Sniper 2025-12-27: "ITEM DETAILS ERROR FAILED TO RETRIEVE WATCHES"; 2026-02-07: "Latest version doesn't work"; 2024-08-28: stuck on "verifying ebay account connection"
  - bidbag 2026-04-27: "lots of crashes"
  - Myibidder 2026-10-20: "freezes on opening"
- **C3. eBay link or sign-in problems.**
  - Myibidder 2025-11-16: Google's "Use secure browsers" policy; 2026-08-16: "stuck in 0auth2 hell"; 2024-04-27: "the snipe failed due to 'not linked to Ebay'"
  - Auction Sniper 2026-04-15: "I can't link my ebay account"
  - Baytomat 2026-08-24: login popup loop
- **C4. Pricing opacity, quotas and price hikes.**
  - Baytomat 2026-03-05: points confusion; 2026-05-11: "Insane price hike"
  - Gixen 2025-10-30: told four free snipes were used on the first snipe
  - Auction Sniper 2026-03-22: "said I had no free snipes"
  - Myibidder 2025-11-12: "$15a month"
- **C5. Hidden second step / arming.** Myibidder 2025-01-13: "missed 3 auctions due to this change".
- **C6. Max not respected.** bidbag 2026-02-27: "I set a max of 375 and it made a bid of 400".
- **C7. Late edit locks and minimum lead time.** Auction Sniper 2024-08-06: "tried to update a snipe 3 minutes before it ended... error... won't let me go under 3 seconds".
- **C8. Missing context on the snipe screen.** Gixen 2024-09-21: "postage is not listed against each item"; 2025-09-25: "can't see the seller name". Myibidder 2026-03-08: buyer protection fees.
- **C9. Watchlist sync and import friction.** Myibidder 2026-10-10: "Doesn't sync to your ebay account. You have to add every item every time"; Gixen 2026-05-17: "fails to load your watch list correctly at the critical moment".
- **C10. Security.** Gixen Trustpilot 2025-11-30: "the developer is still NOT offering 2FA"; Myibidder 2025-02-09: "no provision for Two Factor Authentication"; Baytomat 2026-08-22: "steals your ebay passwords".
- **Also:** defensive developer replies (Myibidder and Baytomat blame users or call reviews "SPAM"), and support that cannot be reached (bidbag 2025-04-27: "email... undeliverable"; Gixen Trustpilot 2026-08-13: "automated contact is frustrating").

### Top praises
- **P1. Set and forget, never misses.**
  - Gixen 2026-07-17: "Have been using the Gixen app for over two years. It has never failed."
  - Baytomat 2026-08-15: "Set and forget"
  - bidbag 2026-04-15: "purposefully, simple, powerful exactly how an app should be"
- **P2. Value.** Gixen 2026-03-01: "I pay a little every year to this guy... Well worth it"; Reddit 2025-03-13: "i only pay $6/year for unlimited bid snipes".
- **P3. Sleep and freedom.** Gixen 2025-11-17: "I don't have to set my alarm for eBay bidding"; Baytomat 2026-08-14: "no longer have to stay up late to bid on international auctions".
- **P4. Avoiding bidding wars and auction fever.** Several reviews say they stay on budget and avoid emotional bidding.
- **P5. Edit or reduce the max before the end without a retraction.** Gixen 2025-03-01: "Allows for Bidders remorse as reducing a bid"; bidbag 2025-07-30: "edit a bid... in both amount & bid time".
- **P6. Low lead time.** Baytomat 2026-06-15: "The ability to post bids within the closing 4 seconds is amazing".
- **P7. Groups.** Baytomat 2026-09-25: "set up a group auction and worked very well! Got a laptop for a really good price".
- **P8. Outbid notifications.** bidbag 2024-03-11: "The notifications if you've been outbid I have found also extremely useful".
- **P9. Redundancy.** Gixen 2024-11-18: "unique split mirror bidding which works superbly".

### Trustpilot
- **gixen.com:** TrustScore 3.7, 817 reviews (13 in the last 12 months), 97% five-star; recent 1-star reviews cover a missed snipe, billing after cancelling, a day-long outage (2026-06-01), no 2FA, and the iOS app deleting a bid when the max is edited (2026-03-01).
- **esnipe.com:** 4.9, 756 reviews, praise for "Easy to use and good interface!" (2026-10-21) and "Really smooth navigation".
- **baytomat.com:** 3.3 from 4 reviews ("credits never expire, then you find they do expire", 2025-04-04; login "doesn't recognize my credentials", 2025-01-18).
- **auctionsniper.com:** no reviews.

### Reddit (archived snapshots)
- 2023-11-22 "Gixen vs. eBay's own auto bid feature": ties go to the earlier bid; one user asks "Is it worth setting it up on two platforms just in case the other misses?".
- 2024-08-30 "Does max bidding prevent sniping?": users bid odd amounts ("$113... $113.50... $117").
- 2025-03-13 "Is sniping still a thing?":
  - recommendations: Myibidder ("Looks like an abandoned project. There's not even a pricing section on their website") and Auction Sniper ("good app and the pricing is decent")
  - dislike of percentage fees; praise for groups and anti-bid-shielding repeat bids
  - an ethics thread ("Sniping is dirty business")

## 5. Gixen specifics

- **Offsets.** "3, 6 (default), 8, 10, 12 or 15 seconds" (FAQ, © 2006-2026); changing requires Mirror. The original API listed `bidoffset` 3, 6, 8, 10, 15 (default 6) and `bidoffsetmirror`. Since 2026-09, Mirror bids 1 second before the end on extended-bidding sites, and the admin says "a 1-second offset may now be the best choice". gixen-cli's README claims 1-15 (unverified).
- **Mirror.**
  - Price: $11.99/year (index page, 2026-11-04). Earlier: $6.53/year (forum, 2022-06-11), with a ~10% rise planned then.
  - Features: "Multi-server, fault-tolerant sniping: snipes are sent from two hosting locations", unlimited snipes, no ads, contingency groups, multi-win groups, snipe times 3-15 s, end times refreshed hourly instead of daily, CSV export/import, "100% reliable" claim (excluding eBay issues). The free service claims 99.5%.
  - Payment: card payments since the forum announcement updated 2026-10-20.
  - The web UI and API list main and mirror statuses separately.
- **Groups.** Group number from "[Bid Group:]"; "remaining bids canceled once an item in the group is won"; items not within 2 minutes of each other; groups not mirrored; Bid Assistant incompatible; contingency and multi-win need Mirror. Free accounts may have at most 4 pending snipes, and a group counts as one (2019).
- **Freshness.** Free accounts get prices from a batch process every 2-3 hours; subscribers refresh on request if older than 10 minutes (mario, 2023-03-31).
- **Extra attempt.** Since 2023-07-26, all users get an additional bid attempt one second before the end, as protection against bid shielding.
- **API endpoint.** `https://www.gixen.com/api.php?username=<gixen user>&password=<gixen pass>&...` (also `xmlapi.php` in a 2026 config). Documented by mario 2008-01-23; "mirror users only".
  - Add: `itemid=`, `maxbid=` (required); `snipegroup=` (0 = none), `quantity=` (later marked obsolete), `bidoffset=`, `bidoffsetmirror=`.
  - Delete: `ditemid=`. List: `listsnipesmain=1`, `listsnipesmirror=1`. Purge completed: `purgecompleted=1`. Plain text: `notags=1`.
  - Success replies: `OK <item_number> ADDED`, `OK <item_number> DELETED`, `OK COMPLETEDPURGED`; a list ends with `<br />OK MAIN LISTED` or `<br />OK MIRROR LISTED`.
  - List rows: `<br />|#!#|itemid|#!#|endtime(unix)|#!#|maxbid|#!#|status|#!#|message|#!#|title|#!#|snipegroup|#!#|quantity|#!#|bidoffset`. Status N = scheduled, S = skipped ("current price is already found to be larger than maximum bid"), D = completed.
  - Error format `ERROR (CODE): MESSAGE`. Codes from the 2015 wrapper: 100/101 COULD NOT LOG IN, 1001 GIXEN DOWN, 111 NON SUBSCRIBER, 115 ACCOUNT SUSPENDED, 131/132/133 username/password/max bid not specified, 201 MAXIMUM NUMBER OF SNIPES EXCEEDED, 202 ITEM ALREADY PRESENT, 203 COULD NOT GET INFO FOR ITEM, 301 AUCTION NUMBER OR MONEY FORMAT INVALID, 302/304 COULD NOT ADD SNIPE TO MIRROR, 401 EMPTY USERNAME OR EMPTY PASSWORD, 241/242/771/772 DELETE QUERY FAILED, 501 SSL REQUIRED.
  - 2026 code reports 103 (login wrong), 106 (auction ended or unknown), 171 (log in once in a browser) and **501 "API DISABLED"**. One developer says this needs an active Mirror plus "Enable API access" in Gixen settings (unverified setting name).
  - gixen-cli (2026-06-01) logs into the web UI instead: POST https://www.gixen.com/main/home_1.php with `username`, `password`, `signin=signin`; forms on home_2.php with `newitemid`, `newmaxbid`, `newbidoffset`, `newbidoffsetmirror`, `newsnipegroup`, `dbidid`, `ismodified=1`, and `purgecompleted=1&gixenlinkcontinue=1`.
- **Login now.** Gixen username and password, on the web and in the app (the app "stays logged in unless you explicitly log out"; no fingerprint login, 2023; no 2FA, 2025-11-30). eBay is linked by authorization (since 2017): it expires after 18 months, changing the eBay password disables it, there is a red warning within 10 days of expiry, and there were 2020 "re-authorize" emails. Earlier users hit re-authorization loops ("You do not have a linked eBay account.", 2020-02-03).
- **Apps.** iPhone and Android apps released 2023-03-28. Early bugs: password managers not recognising login fields, a blank Settings page on Android 10 (2024), and stale "Time to end" and "Current bid". Android v2.1.1 last updated 2024-08-18.
- **Forum signals (2024 to 2026).** "Gixen Autosnipe (Firefox/Chrome/Safari)" (last post 2026-09-27), "Gixen Desktop Manager (64-bit) released" (2026-09-12), "eBay extended bidding" (2026-09-05 to 09-30), "Credit card payments now available to buy Gixen Mirror" (2026-10-20), "New Target settings for eBay country sites" (main and mirror target site; mainly decides which eBay customer service and rules apply).

## 6. Top 10 UX failures to avoid

1. **U1: Silent failures and no proof.** Generic "problem at ebay" and lost-emails for bids never placed (C1). Instead, keep a per-snipe timeline with eBay's verbatim response and a saved screenshot of the result page.
2. **U2: Ambiguous or two-step arming.** Myibidder's separate "Activate"; Biet-O-Matic's global "Automatikmodus" plus per-item "Aktiv"; Gixen showing an imported 0.00 item as "SCHEDULED". Instead, Save means Armed, with one prominent state chip and a warning before the end for anything not armed.
3. **U3: eBay link or session expiry found at auction end.** Myibidder "not linked", Auction Sniper "verifying ebay account connection", Gixen "doesn't tell you when its permissions... expired". Instead, check the session when a snipe is armed and again before the end, and send an actionable alert.
4. **U4: Sign-in that breaks in embedded browsers.** Google's "Use secure browsers" block, "can't be completed on a small screen", captcha loops, popup loops. Instead, detect third-party sign-in and guide the user to a supported method; never show a fake login form.
5. **U5: Stale or missing live data.** Myibidder shows no current price (updated 1-2 times a day); Gixen free batches every 2-3 h; Auction Sniper checks every 12 h a day out. Instead, show current price beside your max, with "checked N min ago", and check more often near the end.
6. **U6: Jargon, raw field dumps, colour-only status.** "Status (main): SCHEDULED", "<not set>", "Cur.Bid", emoji faces, unlabelled badges. Instead, write one-line plain-language states with an icon and colour.
7. **U7: Inconsistent money and time formatting.** £ and € in one dialog, split money fields, "EUR 1,00" vs "€1.00", "10.00" with no currency, US dates with no time zone, "2/20:21:51", "1 Days". Instead, use en-GB formatting, the listing currency symbol plus a GBP equivalent, and local time with the zone.
8. **U8: Lockouts and hidden limits that bite late.** 5 to 6 minute edit locks, 3-minute activation deadlines, quotas announced at action time, a bid that "would collapse" for lack of points. Instead, validate at creation and allow edits until a short, visible lock.
9. **U9: "Smart" features that exceed the max.** bidbag Smart Bidding (375 to 400), eSnipe Minimum Bid Protect, Auction Sniper Second Shot. Instead, the max is a hard ceiling; anything else is opt-in per snipe and shows the worst case.
10. **U10: Fragile releases and layout bugs.** Auction Sniper's Dec 2025 redesign broke watches and its screenshots show inset overlap; bidbag crashes; Baytomat translation leftovers; Gixen's app lacks web features. Instead, add regression tests for the bid path and handle insets on the Pixel 9a (gesture navigation, 120 Hz).

Also avoid: destructive buttons next to edit (Gixen, Baytomat, Auction Sniper), and group timing conflicts with no warning (only JBidwatcher warns).

## 7. Top 10 features users value

1. **V1:** Reliable "set and forget" placement of the bid.
2. **V2:** Free or cheap, paying only on a win, no subscription traps (our app: free, no quotas).
3. **V3:** Fast adding: paste a link (eSnipe accepts "eBay Item # or link"), share from the eBay app or browser (bidbag), watchlist import (all), extension buttons (eSnipe Snipe Tool, BidSlammer Slam-It, Bidnapper SNAPPER, Gixen Autosnipe).
4. **V4:** Change or cancel the max before the end without an eBay retraction.
5. **V5:** Control of lead time, including low values (1 to 4 s are praised; a fixed 6 s is criticised).
6. **V6:** "Win one of these" groups, with optional quantity (Baytomat "DESIRED QUANTITY", Myibidder "To Win").
7. **V7:** Outbid, won and lost notifications (push, email, SMS), with outbid alerts most valued "since eBay can't send them".
8. **V8:** Context when setting the snipe: minimum to bid, postage, seller and feedback, bid count and history (BidSlammer preview; users asking Gixen for it).
9. **V9:** A clear outcome with savings ("Saved 5.86", "saved: 4 (1.7%)", "won... $17.50 less than my max").
10. **V10:** Redundancy and robustness (Gixen Mirror, 3-2-WON, Triple Bid, extra 1 s attempt, retries).

## 8. Opportunities none of them handle well

1. **O1: Proof-of-bid receipt.** The WebView can capture eBay's confirm and result pages (screenshot plus parsed text and timestamps) as a receipt on the snipe card. Competitors only relay a status string (Bidnapper "Response", eSnipe "Log").
2. **O2: Session health as a first-class status.** "Signed in to eBay as <user>, checked 14:02", checked again before the end, with a notification that opens straight to re-sign-in. BidSlammer's "Auth Expires 533d" is the closest existing pattern.
3. **O3: Phone readiness checklist, honestly presented.** Battery and charging, network, exact-alarm permission, battery optimisation exemption, Do Not Disturb exceptions, plus a "phone must stay on" explanation. Everyone else sells "phone can be off" (inferred for implementation details).
4. **O4: Fallback when the phone cannot bid.** Optionally place a normal eBay proxy bid early (for example at T-10 min) if a readiness check fails, or register a Gixen snipe as a backup when the user has Mirror plus API access (CT6, CT7). No competitor offers a fallback to an early bid (inferred).
5. **O5: Extended-bidding awareness.** An "Extended +1:00" chip, re-read end times after the extension, plain-language strategy text, and group logic aware of extensions (CT1, CT2).
6. **O6: Total-cost budgeting in GBP.** Max plus postage to the UK plus any buyer protection fee plus import charges; an "all-in budget" mode that works out the max bid (the JBidwatcher subtract-shipping idea). Show the listing currency and the GBP equivalent.
7. **O7: Outbid or "price passed your max" notification with an inline action.** Raise the max by the next increment or a custom amount from the notification (Android RemoteInput), optionally behind a biometric prompt. Only bidbag offers anything similar, via paid SMS.
8. **O8: Checks before arming.** Ships to UK, seller buyer requirements, disclaimer pages (age, customs, adult), Buy It Now cap, reserve not met, quantity: the BidSlammer list of 50+ failure modes turned into early warnings.
9. **O9: Plain-language results with the numbers.** "Outbid: winning bid £62.99, your max £61.99" / "Won for £47.91, £5.86 under your max" / "eBay did not respond at 21:04:31".
10. **O10: Late edits.** Allow changing or cancelling the max until a short visible lock (seconds, not minutes), since the bid is local.
11. **O11: Group timeline.** End times as a vertical timeline with conflict highlighting ("these two end 40 s apart: you might win both"), "win 1 of N" and "stop after N".
12. **O12: Modern Android craft.**
    - Material 3 and dynamic colour, edge-to-edge done properly, large countdowns in tabular figures, absolute local end time.
    - An ongoing countdown notification for the final minutes; an Android 16 Live Update style surface would fit (inferred platform capability).
    - A home-screen widget for the next ending snipe.
13. **O13: Dry run.** A rehearsal mode that runs the bid flow up to the confirm screen without confirming, as Biet-O-Matic's "Simulated bidding" and Snipesnipe's "Rehearsal" do, to prove the session and the bid path work.
14. **O14: Privacy and security by design.** No sniper account, no third-party server, no fees; an app lock before raising a max; all data stored locally.

## 9. Design recommendations for our app (recommendations, derived from the evidence above)

- **List (home).** Each card shows:
  - thumbnail, title (2 lines), and a site badge (UK/US)
  - large current price with currency and "N bids"; "Your max £X" with state chip **Winning / Outbid / Armed / Not armed / Ended**
  - countdown (seconds when under 1 h) plus the end time in UK time
  - shipping to UK and seller feedback on a second line
  - a "checked 3 min ago" freshness label
  
  Sort by ending soonest; WON and LOST in separate tabs or sections. Keep destructive actions in overflow with undo.
- **Add.** Primary path is the Android share target from the eBay app or Chrome, plus paste-link detection. The preview shows minimum to bid, current bids, shipping to UK, seller, end time, plus pre-flight warnings. One money field in the listing currency with a GBP hint, lead time chips (1, 3, 5, 7, 10 s, default about 5 to 6), optional group. Saving arms it.
- **Detail.** Timeline (Added → Armed → Session checked → Bid sent T-5.0 s → eBay response → Result), with a receipt screenshot, and "Edit max until T-xx s".
- **Notifications.** Outbid with "Raise to £N" and "Edit" actions; session problem ("Sign in again before 21:04"); readiness problem ("Battery saver is on"); result with the numbers.
- **Settings.** Default lead time, notification grid (outbid, won, lost, session, readiness), sites, app lock.

## 10. Appendix: reference tables

### Status vocabularies (verbatim)
- **eSnipe (19):** Ready, Scheduled, Checking, High Bidder, Cancelled, Cancelled by Group, Ended Early, Won, Won (Manual), Below Minimum, Max Bid Low, Outbid, Reserve Not Met, eBay Link Issue, Bid Blocked, Watch, Not Certain, Auction Site Error, Missed.
- **Auction Sniper:** Ready, Success, You Won!, Manual Win, Bid Too Low, Bid Below Increment, Proxy Outbid, Reserve Not Met, Not in Time, Ended Early, Invalid eBay Link, Deleted, Large Bid Restricted, Bid Group not placed, Blocked by seller, Blocked by eBay, Ebay Requires Verification, Ebay Server Error, Ebay Not Responding, Ebay Buying Limit Reached, Not Preapproved, Invalid eBay Email, Security Key Blocked, Seller Purchase Limit Reached, Seller has blocked bidders from your location, Banned By Seller, eBay Account Verification Required, Unknown Status, High Bidder.
- **Bidnapper:** Pending, Queued, Won, Done, Bid Too Low, Reserve, Early, Late, Error, Password, Blocked.
- **Gixen:** API N / S / D. UI: SCHEDULED, WON, OUTBID, BID UNDER ASKING PRICE, ENDED.
- **BidSlammer:** 50+ messages, including Bid Placed OK, High Bidder, Win in Group, Bid Too Low, Bid Increment Not Met, Proxy Bid Waiting, Reserve Not Met, Blocked By Seller, Hard Blocked - Not Permitted, Item Ended Early, Buy It Now Exact Value Required, Bad Ship Location, Seller Requires Credit Card, Watch Item, Blocked - Seller Buy Limit Reached, Blocked - Unpaid Item Strikes, Ticket Restrictions, Export Restricted, Cutlery Age Disclaimer, Cosmetics Age Disclaimer, Customs/Duties Disclaimer, Adult Item Disclaimer, Import Restrictions, Invalid Item Number, eBay Account Upgrade Required, Buying Limit Restriction, Token Invalid, Token Revoked, Account Suspended.

### Time formats seen
Gixen "2 days, 34 minutes"; Baytomat "04d 19:24:53 (-4 s)"; bidbag "Time left: 1 Days 11h 04m 28s" and "Won: 5/24/2025 7:45 AM"; Auction Sniper "Time Left: 28s", "1d 13h 33m", "Ends: 12/12/2025 at 1:33AM"; Myibidder "2/20:21:51"; eSnipe "Finish 5d 7h", "2 days ago 07/28/23 13:18:18"; BidSlammer "Ends: 2d 02h", "20h 57m 1s", "Ends in 8m 24s"; EZsniper "5d 3h 17m 29s"; Deal Hunt "9m left • Ends 2:03 PM EDT"; Biet-O-Matic "14.12.2019, 12:10:27 (vor 2 Tagen)".

### Group spacing rules
Gixen 2 min; eSnipe 30 s; Myibidder 1 min; BidSlammer 5 min; Auction Sniper waits for the previous result; JBidwatcher warns.

### Edit lock windows
Bidnapper 6 min; Auction Sniper 5 min; Myibidder activate 3 min / cancel 1 min; BidSlammer 5 s.

### Monitoring cadences
- Baytomat: 1 h, then 3 min inside the last hour, but 2 h during the Sunday rush.
- Auction Sniper: 10 min / 20 min / 12 h / none, by time remaining.
- Myibidder: 1-2 times a day.
- Gixen: free 2-3 h; subscriber on request if >10 min old.