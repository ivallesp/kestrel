# eBay rules and mechanics that affect a sniper's correctness and the user's account safety (on-device WebView sniper, ebay.co.uk primary), researched 2026-11-04

## Summary
eBay's own help pages (US snapshot 2026-07-17, UK 2025-10-18) still say "Bid sniping ... including the use of software that places bids for you ... is allowed on eBay". The User Agreements also ban automated access without eBay's permission. The US version (since 2026-02-20) adds "buy-for-me agents, LLM-driven bots, or any end-to-end flow that attempts to place orders without human review". The UK version (effective 2026-08-10) has only the generic robots, load and circumvention clauses. The biggest correctness change is Extended Bidding: any bid in the final 60 seconds resets the clock to 60 seconds, so the end time stops being fixed. It has applied to all eBay Australia auctions since mid-August 2026 (with an apparent off/on period in September). In the US it is a test in some categories (help page 2026-10-10). UK item pages already carry the label "Extended bidding Bids placed in the last 1 minute reset the time to 60 seconds.". The app must detect this per listing and keep following the auction through extensions. Proxy bidding sets the price at the second-highest maximum plus one increment, capped at the winner's maximum; when two maximums are equal the earlier bid wins. The minimum next bid is the start price, or the current bid plus one increment, and eBay rejects anything lower. eBay has tested different increments (2025), so the app should read the displayed minimum rather than trust a hardcoded table. On ebay.co.uk the Buyer Protection fee for UK private sellers is £0.10 + 7% up to £20 + 4% from £20 to £300 + 2% from £300 to £4,000 (since 2025-07-17). This fee is already included in displayed prices and in the bids you place, but bid-history pages show amounts without it. That mismatch has caused rejected snipes for Gixen users as recently as 2026-11-01. Bids are effectively final: a retraction inside the last 12 hours is only possible within 1 hour of placing the bid and for specific reasons, and US Trading Cards bids can't be retracted at all. Sellers can end an auction at any time by selling to the current high bidder. Buy It Now disappears after the first bid or once the reserve is met, and Best Offer on auctions (8 sites) lasts until the first qualifying bid. Several checks can block a last-second bid: per-auction phone verification (2025), "standard security steps" for first-time or higher-value bids (2026), UK age checks, seller buyer requirements and captchas. A pre-flight minutes before the end is needed. Sniping services' default offsets range from 3 to 10 seconds (Gixen 6, eSnipe 3 or 5, EZsniper 4 to 5, PerfectSniper 7). Under Extended Bidding Gixen moved to 6 seconds on its main server and 1 second on its mirror, and its incremental-bidding approach has produced many failure reports.

## Findings
- **ST1** [high] eBay US and UK help pages explicitly allow bid sniping, including software that places bids for you; the US and UK texts say the same thing.
  - evidence: US https://www.ebay.com/help/buying/bidding/bid-sniping?id=4224 (Wayback 2026-07-17; same text 2024-08-12): "Bid sniping—including the use of software that places bids for you—is allowed on eBay, but it doesn’t guarantee you’ll win an auction." UK https://www.ebay.co.uk/help/buying/auctions-bidding/bid-sniping?id=4224&ra=true (Wayback 2025-10-18; same text 2024-10-07): "Bid sniping – including the use of software that places bids for you – is allowed on eBay". Both add: "some try to synchronize with eBay servers" and warn about giving third parties "personal data, like your eBay password". Local: /var/tmp/sniper-research/rules/wayback/us_bid-sniping_20260717194427.html, uk_bid-sniping-2025-10_20251018113421.html
  - implication: The core feature is permitted by eBay's own help text. Keep a copy of this quote in the app's about/help screen. It is the strongest argument that a user-configured snipe is not prohibited automation.
- **ST2** [high] The US User Agreement (effective 2026-06-28; the clause has applied since 2026-02-20) bans automated access, explicitly including buy-for-me agents and end-to-end order flows without human review, unless eBay gives express permission.
  - evidence: https://www.ebay.com/help/policies/member-behaviour-policies/user-agreement?id=4259 (Wayback 2026-10-26), section 3 Using eBay: "use any robot, spider, scraper, data mining tools, data gathering and extraction tools, or other automated means (including, without limitation buy-for-me agents, LLM-driven bots, or any end-to-end flow that attempts to place orders without human review) to access our Services for any purpose, except with the prior express permission of eBay;". Effective-date line: "this agreement is effective as of June 28, 2026 ... The previous amendment ... effective for all users on February 20, 2026." News: cxnetwork.com 2026-01-28 ("Taking effect from February 20 ... buy-for-me agents, LLM-driven bots"). Local: wayback/us_user-agreement_20261026135639.txt
  - implication: For ebay.com use, keep a human in the loop for every snipe: the user picks the item and confirms the maximum explicitly. The app must never auto-raise bids, never auto-buy and never run checkout or payment. Present the snipe as the user's own scheduled bid.
- **ST3** [high] The UK User Agreement (effective for existing users 2026-08-10) has only the generic robots clause, with no AI-agent wording. It also bans bypassing robot exclusion headers, imposing unreasonable load, and circumventing technical measures.
  - evidence: https://www.ebay.co.uk/help/policies/member-behaviour-policies/user-agreement?id=4259 (Wayback 2026-10-26): "use any robot, spider, scraper, data mining tools, data gathering and extraction tools, or other automated means to access our Services for any purpose, except with the prior express permission of eBay;" / "bypass our robot exclusion headers, interfere with the working of our Services, or impose an unreasonable or disproportionately large load on our infrastructure;" / "circumvent any technical measures we use to provide the Services." Effective: "from 10 August 2026 for existing users. The previous amendment ... 22 July 2025." Local: wayback/uk_user-agreement_20261026174414.txt
  - implication: For a UK resident (the US UA names eBay (UK) Limited as the contracting entity), the binding rules are low load, no bypassing of captchas or verification, and no scraping. Treat the help page's sniping allowance as eBay's permission for snipes only (inferred interpretation), not for broader scraping.
- **ST4** [high] Both User Agreements forbid sharing log-in credentials with third parties.
  - evidence: US UA (2026-10-26) and UK UA (2026-10-26): "share your log in credentials with any third parties. If you require that authorised third parties ... have access to your account we offer Multi-user account access for that purpose;"
  - implication: Keep the session entirely on the phone (WebView cookie store). Never send passwords, cookies or tokens to any server, including a developer backend. This is also the main design difference from server-based sniper apps.
- **ST5** [high] eBay robots.txt (UK v28 Aug 2026, US v26.2 Apr 2026) contains a 'Robots & Agent Policy' saying checkouts are for humans. It disallows crawling of the bid-history (/bfl/), /ws/, /itm/fetchmodules and /itm/watch/ paths.
  - evidence: https://www.ebay.co.uk/robots.txt (Wayback 2026-10-07, header '# v28_UK_August_2026'): "# Checkouts are strictly for human users." "# * Automated scraping, buy-for-me agents, LLM-driven bots, or any end-to-end flow that attempts to place orders without human review is strictly prohibited." Disallow lines include "Disallow: /bfl/", "Disallow: /ws/", "Disallow: /itm/fetchmodules", "Disallow: /itm/watch/", "Disallow: /itm/*action=BESTOFFER". Same policy block in https://www.ebay.com/robots.txt (Wayback 2026-05-15, 'v26.2_COM_April_2026'). Local: wayback/uk_robots_20261007095600.html
  - implication: Don't poll bid-history (/bfl/viewbids/) or internal module endpoints in a loop. Read state from the normal item page the user would see. Never automate checkout or payment; let eBay autopay or the user pay.
- **ST6** [high] There is no official API route: PlaceOffer forbids scheduled or sniping use and is closed to new applicants, and the Buy Offer API (placeProxyBid) is Limited Release.
  - evidence: PlaceOffer docs https://developer.ebay.com/Devzone/xml/docs/Reference/eBay/PlaceOffer.html (Wayback 2026-07-15): "The automatic and/or scheduled use of PlaceOffer is prohibited. An example is sniping, which is prohibited."; "Note: eBay is no longer accepting applications." Offer API overview (Wayback 2026-05-05): "This is a (Limited Release) API available only to select developers approved by business units." Incumbents bid through eBay-authorised tokens: Gixen FAQ "this changed back in 2017. Now you will be asked to authorize Gixen on eBay ... authorizations automatically expire after 18 months"; EZsniper FAQ (2023-11-24) "bid for you using the eBay bidding API ... re-link every 18 months".
  - implication: This confirms the WebView-session design. The incumbents appear to be grandfathered API users (inferred), so our app's traffic will look like a person using a browser, not like an API client.
- **SAFE1** [medium] eBay documents specific triggers for bidding limits or restrictions. Sniping is not among them, and no credible report was found of an account suspended specifically for using a mainstream sniper.
  - evidence: US https://www.ebay.com/help/buying/buying-limits-restrictions/buying-limits-restrictions?id=4012 (Wayback 2026-02-11), same text UK 2025-04-24: "We may limit your ability to bid on auctions or make a Best Offer if: You are bidding on an expensive item / We need to verify your identity / You need to add a payment method ... / You have a history of canceled orders for not paying on time / You've suddenly and significantly increased your bidding and buying activity / You have several items awaiting payment". Restrictions: "You have multiple accounts, and one or more has a buying restriction". Sept 2026 Seller News (Wayback 2026-10-27): "Buyers with repeat unpaid items across multiple accounts will be blocked from bidding on auctions." The only sniper-specific report found is an undated Auction Sniper community thread title, "My account suspended by eBay for using Auction Sniper!!", quoting a generic suspension message. The site is offline ("This Crowdstack Has Been Unplugged"), so the cause is unverified.
  - implication: The real account risks are unpaid wins, sudden bursts of bidding (for example many snipes on a fresh account), missing payment method and verification failures. The app should warn on a surge (many snipes in one day) and remind the user to pay or keep autopay.
- **SAFE2** [high] Since July 2025 eBay has sometimes required SMS or phone-call verification before a bid is accepted. It is per auction, its stated aim includes reducing sniping, and it breaks last-second manual bids.
  - evidence: VAR 2025-07-13 https://www.valueaddedresource.net/ebay-auction-bidding-phone-number-verification/ quoting eBay community staff: "When you complete the verification once you won't need to do it again for the same auction. The bid goes ahead as normal. However you may be prompted to verify on future auctions. We are unable to share any specific criteria"; eBay UK staff goal "reducing sniping, shill bidding and unpaid item situations". EcommerceBytes 2025-07-22 snippet: "Phone verification has been introduced to the bidding process." Buyers reported the code arriving after the auction ended.
  - implication: A pre-flight is needed. Several minutes before the end (for example T-10 min), open the bid layer up to the amount screen without submitting. If a verification interstitial appears, alert the user at once (foreground notification plus visible WebView) so they can verify before the end. Never try to automate code entry.
- **SAFE3** [high] eBay (US, autumn 2026) says first-time bidders and higher-value bids may face 'standard security steps' or identity verification.
  - evidence: US Automatic bidding https://www.ebay.com/help/buying/bidding/automatic-bidding?id=4014 (Wayback 2026-10-19): "If you are placing high bids for the first time on eBay, we may ask you to verify your identity or take other standard security steps". Yahoo snippet of https://www.ebay.com/help/buying/bidding/bidding?id=4003 seen 2026-11-04 (not in the 2026-10-10 snapshot): "When bidding in an auction for the first time on eBay, or placing bids on higher-value items, you may be asked to verify your information with some standard security steps." Seller News Sept 2026: "eBay may verify additional buyer details before auctions". Niceggie 2026-09-10 summary.
  - implication: For expensive items or a user's first auctions, run the pre-flight early (hours before) and tell the user that a verification prompt may appear. Treat any unknown interstitial in the bid flow as 'needs human', not as an error to retry.
- **SAFE4** [high] eBay shows captcha or verification pages to ordinary users and to non-browser fetchers; the PlaceOffer docs say even API bidders may get CAPTCHA challenges.
  - evidence: A direct WebFetch of https://www.ebay.com/help/buying/bidding/bidding-basics?id=4003 on 2026-11-04 returned only: "To keep eBay a safe place to buy and sell, we will occasionally ask you to verify yourself." Reddit r/Ebay 2025-02-08 'Ebay Catchpa Loop Fix'; Facebook resellers group 2026-06-01 ('Lately I've been getting this verification Captcha from Ebay'); eBay UK community 'Cannot make purchases due to captcha' (undated). PlaceOffer docs (2026-07-15): "eBay may ask users to respond to a CAPTCHA challenge from time to time." This machine's IP gets HTTP 403. An earlier agent's cookie jar (/var/tmp/sniper-research/ebay-pages/cj.txt, 2026-11-04) holds cookies bm_so and bm_s, and the error page carries reference id 0.566bdc17.1793760840.cb1980e.
  - implication: Detect captcha or verification pages (for example the text "verify yourself") in the WebView. Stop automation, notify the user and hand them the visible WebView. Never solve or bypass a challenge: the UA forbids circumventing technical measures.
- **SAFE5** [medium] eBay pages run bot management and behavioural risk telemetry (inferred from cookie names and global-header config).
  - evidence: Cookie names bm_so and bm_s on .ebay.co.uk (cj.txt, 2026-11-04) match Akamai Bot Manager's naming (inferred). eBay's global header in archived pages (for example uk_buyer-protection-fee_20261025014429.html) contains: risk={behavior_collection_interval:JSON.parse("{\"2500857\":5000,\"2507978\":5000,\"default\":15000}")} (inferred meaning: behaviour sampling every 5 to 15 s).
  - implication: Send every eBay request through the real WebView with JavaScript enabled, eBay's own scripts running and the default WebView UA. Don't replay bid requests with OkHttp or curl outside the WebView, don't spoof UAs, and don't mix in requests from any other IP.
- **SAFE6** [high] eBay's own polling guidance for auction-status checks (written for its API) caps frequency at 3 checks per minute during the last 3 minutes, ramping up gradually.
  - evidence: PlaceOffer docs (Wayback 2026-07-15): "Your application should gradually increase polling frequency as the ending time approaches. Making three GetItemStatus API calls each minute during the last three minutes of an auction should be the maximum frequency." and "please do not use GetItem for frequent item status checks." Gixen moderator 2025-10-05: "Updates are periodic, more frequent towards the end of the auction."
  - implication: Use this as the ceiling for page loads per item. Suggested budget (inferred): a check when added, then about hourly to T-1h, every 10 min to T-10min, every 1 to 2 min to T-3min, at most every 20 s in the last 3 min. One extra fetch per Extended Bidding round is acceptable.
- **SAFE7** [medium] Incremental 'probing' bids resemble the patterns eBay names as shill-bidding signals.
  - evidence: UK Shill bidding policy https://www.ebay.co.uk/help/policies/selling-policies/selling-practices-policy/shill-bidding-policy?id=4353 (Wayback 2025-11-07): "Other questionable patterns include multiple bids in short, deliberate intervals, or bidding several times in small amounts even when other bids haven't been placed on an item."
  - implication: Default to one full-maximum bid per snipe. Avoid a Gixen-style ladder of small bids during extensions: it adds failure modes (see END5) and creates bid patterns eBay flags (inferred risk).
- **SESS1** [medium] eBay offers password, passkey, Google, Facebook and Apple sign-in. Google blocks OAuth in embedded WebViews, and passkeys in a WebView need explicit app support, so password plus 2-step sign-in is the dependable WebView path (inferred).
  - evidence: https://www.ebay.com/help/account/signing-account/signing-account?id=4189 (Wayback 2026-09-11) and UK equivalent (2026-04-18) list 'Signing in with Passkeys', 'Google', 'Facebook', 'Apple'. Google policy error: "This user-agent is not permitted to make OAuth authorisation request to Google as it is classified as an embedded user-agent" (stackoverflow q/40591090; Auth0 article 2025-09-10). WebView WebAuthn requires androidx.webkit setup (github deephand/webauthn-in-webview 2025-06-24).
  - implication: In onboarding, tell the user to sign in with email/username and password (with eBay 2-step verification) inside the app. 'Continue with Google' will likely fail in the WebView. Before each snipe, check that the session is still signed in (SAFE2 pre-flight).
- **PX1** [high] Under proxy bidding, the winner pays the second-highest maximum plus one increment, capped at the winner's own maximum. A later bid that beats the leader's maximum by less than a full increment wins at its own amount.
  - evidence: Gixen FAQ (fetched 2026-11-04): "The winning bid amount will be the second-highest bid plus up to one increment, which could be much less than your maximum bid." Legacy eBay help http://pages.ebay.com/help/buy/bid-increments.html (Wayback 2016-01-02): "You can be outbid by less than a full increment. The winning bid needs only to be higher than the next highest maximum bid by one cent." (example: $20.01 beats a $20.00 max). Legacy overview (Wayback 2016-01-23): Tom max $12, Laura max $15, "Tom's bid is raised to his maximum of $12.00. Laura's bid is now $12.50." Current US Automatic bidding (2026-10-19): "We’ll bid in increments on your behalf to keep you in the lead but only up to your limit."
  - implication: Expected price = max(start, min(winnerMax, secondMax + inc(secondMax))), raised to the reserve when winnerMax >= reserve. The app's 'you would pay' estimate is that value, never more than the user's max (plus fees and postage).
- **PX2** [high] Official minimum-next-bid rule: the starting price if there are no bids, otherwise the current bid plus the bid increment. Lower bids are rejected.
  - evidence: Browse API getItem docs (Wayback 2026-03-12) field minimumPriceToBid: "The minimum price of the next bid, which means to place a bid it must be equal to or greater than this amount. If the auction hasn't received any bids, the minimum bid price is the same as the starting bid. Otherwise, the minimum bid price is equal to the current bid plus the bid increment." Gixen FAQ: "I entered a bid of $101.00, but the winning bid was $100.00 and Gixen says BID UNDER ASKING PRICE ... your bid had to be at least $2.50 higher". Gixen forum t=11405 (2025-01-10): bid $107.55 lost to current $106.50 because the increment became $2.50 above $100.
  - implication: Before firing, and when monitoring, compare the user's max with the minimum. If max < minimum, the snipe cannot be placed: send 'price passed your maximum' right away instead of firing a doomed bid. Watch increment bracket edges ($100, £60, £150, ...).
- **PX3** [medium] Ties go to the earlier bid: if two bidders set the same maximum, the first one placed wins.
  - evidence: eBay Canada community 2024-01-04 (search snippet): "if the same maximum bid is made by multiple users, the bid placed first takes precedence." esniper man page (yhfudev/esniper, 2018): "In case two bids are within one bid increment of each other, the first bid placed wins." PerfectSniper help (undated, (c) 2005-2026): "if two people bid a similar amount (or the same amount) then the person who bids the first will win." No current eBay help page states the tie rule explicitly (not found).
  - implication: Late snipes lose ties to earlier equal maximums. Suggest odd amounts (eBay's US tips page, 2025-10-09: "Try bidding an uneven amount. Thinking of spending $10.50? Try $10.63 instead."); for example, default the max to end in odd pence.
- **PX4** [high] Reserve price: bids below it show 'Reserve not met' and the item doesn't sell. When a bidder's maximum meets the reserve, the price jumps straight to the reserve. Sellers may lower the reserve during the auction or send a Second Chance Offer after it ends.
  - evidence: US https://www.ebay.com/help/buying/bidding/reserve-prices-work?id=4018 (Wayback 2026-07-12): "If you bid below the reserve price, you'll see a \"Reserve not met\" message ... even if you're the highest bidder at the end of the auction, you won't win the item." / "Sellers can lower their reserve price during the auction or make a Second Chance Offer". DE automatic bidding (Wayback 2026-04-21): start EUR 1,00, reserve EUR 100,00; "Bieter B gibt ein Maximalgebot von EUR 150,00 ab. Das aktuelle Gebot wird auf EUR 100,00 erhöht". Browse API: reservePriceMet boolean.
  - implication: Show 'reserve not met' in the UI. If the user's max is below an unmet reserve, winning is impossible: say so before the end. A max above the reserve can push the price straight to the reserve.
- **PX5** [high] A Second Chance Offer lets a losing bidder buy at the amount of their own last bid (their maximum), not at a second-price.
  - evidence: UK https://www.ebay.co.uk/help/buying/auctions-bidding/second-chance-offer?id=4017 (Wayback 2025-11-06): "If you receive a Second Chance Offer, you'll be able to buy the item at the amount you offered in your last bid." Offers last "either 1, 3, 5, or 7 days".
  - implication: In the 'lost' report, mention that a Second Chance Offer may arrive and would cost the user's full max. Warn about fake SCO emails: eBay says genuine ones appear in Messages and Bids/Offers.
- **PX6** [high] Shill bidding (the seller or people they know bidding to raise the price) is prohibited, and eBay detects it from bid patterns.
  - evidence: UK Shill bidding policy (Wayback 2025-11-07): "Shill bidding is when sellers – or someone they know – place a bid on their item to drive the price up."; "People who know the seller ... can't bid on that seller's items, but they can use options like Buy It Now". Forum concern that extensions invite shill bids: stampboards thread 2026-08-05; maestronet thread late Oct 2026.
  - implication: Warn the user not to snipe items from friends or family sellers. Under Extended Bidding, a sudden late counter-bid could be a shill (no way to verify); the app can only report it.
- **INC1** [high] US increment table (same on ebay.ca and ebay.com.au).
  - evidence: US https://www.ebay.com/help/buying/bidding/automatic-bidding?id=4014 (Wayback 2026-10-19): $0.01–$0.99: $0.05; $1.00–$4.99: $0.25; $5.00–$24.99: $0.50; $25.00–$99.99: $1.00; $100.00–$249.99: $2.50; $250.00–$499.99: $5.00; $500.00–$999.99: $10.00; $1000.00–$2499.99: $25.00; $2500.00–$4999.99: $50.00; $5000.00 and up: $100.00. Identical tables: ebay.ca (Wayback 2026-06-11), ebay.com.au (Wayback 2025-08-19).
  - implication: Use as a fallback for USD, CAD and AUD listings only; prefer the minimum bid eBay displays.
- **INC2** [high] UK (GBP) increment table.
  - evidence: https://www.ebay.co.uk/help/buying/bidding/automatic-bidding?id=4014 (Wayback 2025-12-09): £0.01 - £0.99: £0.05; £1.00 - £4.99: £0.20; £5.00 - £14.99: £0.50; £15.00 - £59.99: £1.00; £60.00 - £149.99: £2.00; £150.00 - £299.99: £5.00; £300.00 - £599.99: £10.00; £600.00 - £1,499.99: £20.00; £1,500.00 - £2999.99: £50.00; £3,000.00 and up: £100.00. Local: wayback/uk_automatic-bidding_20251209212147.html
  - implication: Primary fallback table. For UK private sellers the displayed increment is larger because of the Buyer Protection fee (INC6).
- **INC3** [high] ebay.de, ebay.at and ebay.ch use a coarse EUR table that starts at EUR 1,00.
  - evidence: DE https://www.ebay.de/help/buying/bidding/automatic-bidding?id=4014 (Wayback 2026-04-21): EUR 1,00 bis EUR 49,99: EUR 0,50; EUR 50,00 bis EUR 499,99: EUR 1,00; EUR 500,00 bis EUR 999,99: EUR 5,00; EUR 1.000,00 bis EUR 4.999,99: EUR 10,00; Über EUR 5.000,00: EUR 50,00. Same on ebay.at (2026-04-21). ebay.ch help (2026-06-09) shows the same rows, labelled EUR.
  - implication: The fallback for ebay.de differs a lot from the UK table: at EUR 400 the increment is EUR 1, not 10. Don't reuse the GBP table for EUR sites.
- **INC4** [high] ebay.fr, ebay.it and ebay.es use an EUR table starting at 0,01 with a 0,50 step up to 49,99; ebay.nl uses a UK-style table in EUR; ebay.pl uses a US-style table in PLN.
  - evidence: FR (Wayback 2024-11-09), IT (2026-05-17), ES (2026-05-18): 0,01-49,99: 0,50; 50,00-499,99: 1,00; 500,00-999,99: 5,00; 1.000-4.999,99: 10,00; 5.000+: 50,00. NL (2026-04-11): 0,01-0,99: 0,05; 1,00-4,99: 0,20; 5,00-14,99: 0,50; 15,00-59,99: 1,00; 60,00-149,99: 2,00; 150,00-299,99: 5,00; 300,00-599,99: 10,00; 600,00-1499,99: 20,00; 1500,00-2999,99: 50,00; 3000+: 100,00. PL (2024-12-14): 1,00-4,99 zł: 0,25; 5,00-24,99: 0,50; 25,00-99,99: 1,00; 100,00-249,99: 2,50; 250,00-499,99: 5,00; 500,00-999,99: 10,00; 1000-2499,99: 25,00; 2500-4999,99: 50,00; 5000+: 100.
  - implication: Key fallback tables by site, not by currency (EUR has three different tables).
- **INC5** [high] eBay tests and changes increments without notice and reserves the right to do so; in 2025 some accounts saw $1 increments on cheap items.
  - evidence: US Automatic bidding (Wayback 2025-04-12): "We’re currently testing out new bid increments in order to make the bidding process less complex for both buyers and sellers. As a result, you may experience a difference in your bid increments from the chart below." VAR 2025-03-20 'eBay Tests Increasing Auction Bid Increments For Low Priced Items' (forced $1 increments below $25). Legacy help (2016): "we may, from time to time, change (increase or decrease) these increments."
  - implication: Read the minimum from the live bid layer (for example the 'Enter £X or more' text) on every attempt. Use the static table only to sanity-check, and take max(displayed, computed).
- **INC6** [medium] On ebay.co.uk, auctions from UK private sellers are displayed fee-inclusive while bid-history pages show fee-exclusive amounts. The minimum eBay reports can be wrong, which has caused 'BID UNDER ASKING PRICE' failures.
  - evidence: Gixen forum t=11857 (2026-10-15): the main auction page shows 'incl. GBP 8.37 Buyer Protection fee'; bid history shows the ex-fee 191.83; moderator: "the minium bid that Ebay would have accepted was GBP 205.20 with the buyers premium included". t=11633 (2025-11-15): "On ebay.co.uk you'll see the current price including the buyers' premium/protection fee on the auction page, but if you look at the bids page you'll see the price excluding it." t=11468 (2025-03-08): increments "are based on the auction price once buyer fees are taken off". t=11879 (2026-11-01, Gixen owner, eBay AU auction): "the increment was not really 1.00, but 1.06 ... eBay misreported the next minimum ... Gixen will calculate BPF itself".
  - implication: Parse price, minimum and max on the same page and site the bid is placed from (ebay.co.uk item page). Never mix in bid-history numbers. Compute the fee-inclusive minimum yourself and use the larger value. Tell the user their max includes the fee for private sellers.
- **END1** [high] Extended Bidding: a bid in the final 60 seconds resets the remaining time to 60 seconds, repeating until a full minute passes with no bids. The US help page calls it a test in some categories (Trading cards and Vehicles).
  - evidence: US How bidding works https://www.ebay.com/help/buying/bidding/bidding?id=4003 (Wayback 2026-10-10): "We're testing extended bidding on auction listings in some categories. Any bid placed in the final 60 seconds resets the clock to 60 seconds. The auction ends when the clock runs out with no new bids." Earlier wording (Yahoo snippet of .../bidding-basics?id=4003): "We're testing extended bidding on select Trading cards and Vehicles auctions." US users notified: collectors.com thread 2026-09-02.
  - implication: The end time is no longer fixed. The scheduler must treat 'scheduled end' as provisional, re-read the end after any bid in the last minute (including the user's own), and not report won or lost until the page shows the listing ended.
- **END2** [high] eBay Australia applies Extended Bidding to all AU-listed auctions from mid-August 2026, including when buyers on other sites see those listings. The feature was apparently switched off around 2026-09-08 and back on by mid-September.
  - evidence: AU announcement (Wayback 2026-10-21) https://community.ebay.com.au/forum/news-updates-44/topic/introducing-extended-bidding-helping-auctions-reach-their-full-potential-8571/: "From mid-August, we’re rolling out Extended Bidding on all auction items listed in Australia"; "When a bid is placed during the final minute of an auction, the remaining time will reset to 60 seconds until all bidding stops."; "Extended Bidding applies to all auction items listed in Australia, including when those listings are seen by buyers in other markets." (the original text said 'From 17 August'; VAR 2026-08-03). Gixen owner, t=11811, 2026-09-08: "It looks like eBay reverted time extensions on eBay Australia (my guess)"; user 2026-09-14: "eBay Australia has started this today"; 2026-11-03: "Extended Bidding Ing is Definitely ON in Australia".
  - implication: A UK user sniping an AU-listed item will meet Extended Bidding even on ebay.co.uk. Because eBay toggles it, detect it per listing at run time (END4) and never infer it from the site.
- **END3** [medium] In the UK, Extended Bidding has not been formally announced, but by August 2026 help pages had dropped the category restriction, and UK users saw it from September 2026.
  - evidence: VAR 2026-08-03 (updated 2026-08-23): "eBay has updated bidding policy help pages for the US, UK, Canada and Germany to remove mention of specific categories"; "Official announcements have not yet appeared in the US, UK, Canada or Germany". Stampboards t=110407 post by 'Rog' 2026-09-06T23:57Z: "This popped up just now for me on the UK site". Gixen blog 2026-09-23: "So far this is rolled out on eBay Motors and eBay Australia. The other markets may follow, including the biggest, eBay UK and eBay US." Earlier UK test (Wayback 2025-07-15): "extending auction durations by 2 minutes when a bid is placed in the last 2 minutes ... select items in the Trading Cards category".
  - implication: Assume any UK listing may have Extended Bidding, now or soon. Build it in from the start and parse the reset window from the label (it was 2 minutes in the 2025 test, 60 seconds now) rather than hardcoding 60 s.
- **END4** [medium] Listings with Extended Bidding show a visible label: 'Extended bidding Bids placed in the last 1 minute reset the time to 60 seconds.'
  - evidence: Yahoo search snippets of item pages (2026): https://www.ebay.co.uk/itm/287661551361 "Extended bidding Bids placed in the last 1 minute reset the time to 60 seconds. The highest bid wins when time runs out."; https://www.ebay.co.uk/itm/267819003587 "... reset the time to 60 seconds. Learn more"; https://www.ebay.com/itm/147663231815, https://www.ebay.ca/itm/257816827199, https://www.ebay.com.au/itm/257823150682 same label.
  - implication: Detection signal: case-insensitive match on 'Extended bidding' or 'reset the time to'. Store an extendedBidding flag per snipe and re-check it at T-10 min, since flags can flip. Show it in the UI ('this auction can run past its end time').
- **END5** [high] Extended Bidding cut Gixen's win rate by about 10%. Gixen's response (incremental bidding over up to 12 extensions) has produced many failure reports in September to November 2026.
  - evidence: Gixen blog 2026-09-23: "Gixen now wins around 10% less auctions"; "Gixen will do the bidding incrementally ... wait the maximum of 12 extensions"; "For extended auctions Gixen main server will default to 6 seconds ... detect whether or not an auction extends, which requires 3 calls to eBay instead of one." Failures: t=11811 2026-09-08 (max $12.50, sold $6.56); t=11890 2026-11-03 (max AU$217.79, "Gixen completely stopped bidding at AU $155.85", sold AU$158.35); t=11854 2026-10-13/11-03 (won/lost mis-reported, blamed on an eBay bug); t=11808 2026-09-21 moderator: "until the maximum submitted by Gixen is outbid there's not even any way to know whether your bid will eventually win".
  - implication: For a one-user app, simplicity wins. Default to one full-max bid near the end (proxy bidding then defends it through extensions) and keep monitoring until the true end. Treat incremental strategies as optional and experimental. Group logic ('win only one of these') must allow for auctions overrunning by minutes.
- **END6** [low] Extended Bidding has buggy edge cases: one auction was extended by 60 minutes instead of 60 seconds, and a non-leading late bid reportedly did not extend another.
  - evidence: collectors.com thread 1125884 (2026-09-28): bid with 7 s left, auction showed 'ended' and won, then "Come to find out, instead of adding 60 seconds to the end, it added 60 minutes." Same thread: "I put a shallow bid in with 6 seconds left expecting to get another 60 seconds ... Bid registered and item closed." (coin category, possibly no Extended Bidding). Open question in the same thread: "Does it only reset if a new bid leader is made?"
  - implication: After the scheduled end, keep checking at a low rate until the page explicitly says the listing ended, and re-check once about 1 to 2 minutes later before sending 'won' or 'lost'. Don't assume +60 s exactly.
- **END7** [high] Sellers can end auctions early. With 12 or more hours left they can cancel bids and end; with under 12 hours left and a bid at or above the reserve (or no reserve), they can only end by selling to the current high bidder, which they may do at any time.
  - evidence: UK https://www.ebay.co.uk/help/selling/listings/creating-managing-listings/canceling-listing?id=4146 (Wayback 2026-07-14) table: 'Fewer than 12 hours | 1 or more bids with reserve price met or no reserve price set | No, you can only sell the item to the highest bidder'; with reserve not met: may end, fee for private sellers "5% of the highest bid, up to a maximum fee of £20". US version (Wayback 2026-06-10) same structure. Dev guide end-early (Wayback 2026-10-14): "If you specify SellToHighBidder, the listing ends as a successful auction"; "An End Item call will fail ... if there are 12 hours or fewer remaining ... and there are one or more active and qualifying bids". Gixen forum t=11883 2026-11-02: "This can happen when a seller ends an auction early, Ebay allows that."
  - implication: A snipe can be pre-empted at any moment. Poll during the final hours (SAFE6), detect 'ended' states, and report 'ended early' rather than 'lost'. Don't fire into an already-ended listing.
- **END8** [high] Buy It Now on an auction usually disappears after the first bid, or after the reserve is met. UK exceptions keep it until bids reach 50% of the BIN price; the US says BIN may stay 'for a limited time' in some categories. The BIN price must be 30% (US) or 40% (UK) above the start price.
  - evidence: US https://www.ebay.com/help/selling/listings/selling-buy-now?id=4109 (Wayback 2026-10-13): "When someone bids, the Buy It Now option usually disappears; however, in the case of reserve price listings, the Buy It Now option remains until the reserve price is met. In some categories, the Buy It Now option may continue to be available after the first bid for a limited time." / "at least 30% higher". UK (Wayback 2025-10-10): "at least 40% higher"; "In certain categories, Buy It Now disappears only after 50% of the Buy It Now price is reached through bidding: Parts & Accessories on eBay Motors, Tickets, Clothing, Shoes & Accessories, Cell Phones & PDAs". Offer API overview (2026-05-05): "after the first bid is placed, the Buy It Now option is removed".
  - implication: While BIN is visible, someone can end the auction instantly. Show 'BIN available at £X' and let the user decide to buy manually (the app must not auto-buy). Watch for an 'ended/sold' state.
- **END9** [high] Best Offer can be enabled on auction listings on 8 sites (instead of Buy It Now) until the first qualifying bid. An accepted offer ends the auction early.
  - evidence: Trading API BestOfferDetailsType (Wayback 2026-07-11): "The Best Offer feature is now available for auction listings on the following sites: US, Canada, UK, Germany, Australia, France, Italy, and Spain. However, sellers must choose between offering Best Offer or Buy It Now on an auction listing ... this feature will no longer be applicable once the listing receives its first qualifying bid." Gixen forum t=11865 (2026-10-21, eBay AU): "The seller accepted a Best Offer, that's allowed on Ebay."
  - implication: For auctions with no bids and Best Offer enabled, warn that the item can sell before the snipe fires. Point the user to a manual offer if they want certainty; the app shouldn't send offers automatically.
- **END10** [medium] End times are exact to the millisecond in UTC (API format). Displayed times use local zones, and a US daylight-saving change (PDT to PST, 2026-11-01) confused at least one sniper analysis.
  - evidence: Browse getItem docs (2026-03-12): itemEndDate "returned in UTC format (yyyy-MM-ddThh:mm:ss.sssZ)". Gixen forum t=11883 2026-11-02: "It started at: 25 Oct 2026 at 6:00:00pm PDT Finished at: Nov 1, 2026 at 6:00PM PST with a duration of: 7 days ... a swap over in the listing of the bid times on Nov 1, from PDT to PST". UK clocks changed 2026-10-25 (inferred calendar fact).
  - implication: Store end time as a UTC epoch value, parsed from machine-readable data where possible, not from a 'Sun 18:00' string. Re-parse it at T-10 min and T-60 s. Never compute it as start time plus duration.
- **END11** [high] eBay says it uses UTC and that any internet time server can keep a client in sync. Android may leave the wall clock up to about 2 s off, so use the network time clock (API 33+, available on the Pixel 9a).
  - evidence: GeteBayOfficialTime docs (Wayback 2025-01-20): "If you are using this call just to get the time, eBay uses UTC so any internet time server can be used to keep the client in sync with UTC time." AOSP time detection docs (fetched 2026-11-04): network (NTP) prioritised over telephony (NITZ); a suggestion within "a couple of seconds" won't change device time by default (sample systemClockUpdateThresholdMillis=2000). Android SystemClock reference: currentNetworkTimeClock() "Added in API level 33 ... synchronized using a remote network source outside the device ... it will either return a valid time or throw."
  - implication: Fire times should come from SystemClock.currentNetworkTimeClock() (fall back to your own SNTP query), plus elapsedRealtime for countdowns. Don't use System.currentTimeMillis(), which a 2 s error could push past a 3 s offset.
- **END12** [high] Private listings hide bidder identities, and bid-history usernames are anonymised in general.
  - evidence: US How bidding works (Wayback 2026-10-10): "Sellers sometimes create private listings that keep bidders and the buyer anonymous ... only the seller can see your username." UK Shill policy (2025-11-07): "each bidder is anonymous name (Bidder 1, for example). So only the seller can see a bidder's username."
  - implication: Work out won or lost only from the signed-in user's own status on the item page or in My eBay (Bids/Offers, Purchase history), never from bid-history names.
- **END13** [high] A payment method and address may be required when bidding, and wins are charged automatically. The US charges 24 h after the win, and since September 2026 some buyers can choose 24-hour or 4-day autopay. The unpaid-item limit is 4 calendar days, and eBay Live auctions need immediate payment.
  - evidence: US How bidding works (2026-10-10): "if you win the auction, payment will be taken automatically after 24 hours (or instantaneously if shopping on eBay Live)"; "Starting September 2026, some buyers will begin to see an Autopay at option ... 24-hr autopay ... or 4-day autopay". UK (2025-07-15): "You may be asked to provide a payment method and postage address when making a bid. In this case, if you win the auction, payment will be taken automatically." US Unpaid item policy (Wayback 2026-10-27): "Buyers must pay ... within 4 calendar days"; "In most cases, the buyer's payment method will be charged automatically." VAR 2024-02-22: seller setting "require buyers to provide a payment method before they place a bid"; test pop-ups "Introducing payment preferences" / "Introducing Automatic Checkout".
  - implication: The pre-flight must handle a 'payment preferences' or autopay choice modal that can appear in the bid flow. The won report should say 'payment will be taken automatically' or 'pay within 4 days'. Exclude eBay Live auctions, which have immediate payment and a seller-optional 5 s soft close (VAR 2025-07-07).
- **BUY1** [high] Bids are effectively final for snipes. Retraction is allowed only for listed reasons, and inside the final 12 hours only within 1 hour of placing the bid. US Trading Cards bids can't be retracted at all, and eBay doesn't let buyers cancel orders after winning.
  - evidence: US Retracting a bid (Wayback 2026-10-15): "Bids on items listed in Trading Cards categories, for example, can't be retracted." / "(Note: these options are only applicable for up to 1 hour after making the bid when the listing is ending in less than 12 hours)" / reasons: seller "significantly changed the description"; "You accidentally bid the wrong amount". UK (Wayback 2026-10-03): same note plus "You can't reach the seller". US How bidding works (2026-10-10): "we don’t allow buyers to cancel orders after winning an auction".
  - implication: Typos are unrecoverable. Require a confirm step showing the amount in words and currency, and warn when max is over 3 times the current price or above a user cap (inferred thresholds). Lock edits in the last 2 minutes (Gixen FAQ advises edits "at least 2 minutes before auction end").
- **BUY2** [high] Sellers can block buyers individually or by requirements (country not shipped to, excessive unpaid cancellations, payment method required). A blocked bid shows a 'contact the seller' message, and some rejections only appear after confirming.
  - evidence: UK Blocking a buyer (Wayback 2026-07-12): "block buyers based on certain criteria. For example, buyers living in a country you don't ship to, or buyers with excessive cancelled orders because they didn't pay for items." US How bidding works (2026-10-10): "you'll see a message asking you to contact the seller when you try to place a bid." Gixen statuses: "AUCTION RESTRICTED: REQUIREMENTS NOT MET" (FAQ); "You are not eligible to bid on this item" (forum t=11832/11842/11877, Sept to Oct 2026); moderator 2026-10-05: "errors such as this one happen, after you confirm"; example: "The sale of lasers is subject to restrictions on eBay.com.au".
  - implication: The pre-flight (open the bid layer, enter the amount, stop before confirm) catches most blocks early. Some only appear at the final confirm, so map every distinct error text to a clear user message and record it.
- **BUY3** [high] UK age-restricted categories (bladed items, alcohol, e-cigarettes) require age verification to bid or buy: a credit card check for bladed items (debit cards not accepted) or VerifyMyAge for alcohol.
  - evidence: UK https://www.ebay.co.uk/help/buying/default/age-restricted-items?id=5596 (Wayback 2025-08-20): "To bid or buy certain items on eBay, you must be 18 or older, and you’ll be asked to verify your age on eBay"; "Debit cards may not be used to verify age."; alcohol via "VerifyMyAge".
  - implication: For these categories, have the pre-flight run hours ahead, because a verification prompt at T-5 s means a lost snipe.
- **BUY4** [high] UK Buyer Protection fee for UK-based private sellers (current since 2025-07-17): £0.10 per item + 7% of the price up to £20 + 4% of £20 to £300 + 2% of £300 to £4,000, capped (maximum fee £86.70).
  - evidence: UK https://www.ebay.co.uk/help/buying/paying-items/buyer-protection-fee?id=5594 (Wayback 2026-10-25): "A flat fee of £0.10 per item, and 7% of the item price up to £20, and 4% of any portion of the item price from £20 to £300, and 2% of any portion of the item price from £300 to £4,000"; example £7,500 gives £86.60 + £0.10. History: Wayback 2025-01-20 '£0.75 + 4% up to £300 + 2%'; VAR 2025-07-17: "Starting July 17, 2025, the Buyer Protection Fee will instead be calculated as" the 10p/7% scheme; "Auctions that were started before the 17th will still use the previous fee calculation." The fee includes UK VAT at 20%.
  - implication: Implement fee(p) = 0.10 + 0.07*min(p,20) + 0.04*clamp(p-20,0,280) + 0.02*clamp(p-300,0,3700). Inverse (inferred algebra, matches eBay's 191.83 to 200.20 example): p = (D-0.10)/1.07 if D <= 21.50; (D-0.70)/1.04 if D <= 312.70; (D-6.70)/1.02 if D <= 4086.70; else D-86.70.
- **BUY5** [high] The Buyer Protection fee is already included in displayed prices and in the bids and offers the buyer makes on ebay.co.uk. The seller sees ex-fee amounts. Business sellers and non-UK private sellers charge no fee, and Vehicles, Classified Ads and Property are excluded.
  - evidence: UK BPF page (2026-10-25): "The Buyer Protection fee, if applicable, is already included in the item price that you see when browsing on the site and in any bids or offers you make"; "The auction or offer price you see on the site will always include the Buyer Protection fee."; "Buyer Protection is included at no cost to the buyer when purchasing from business sellers, or from private sellers based outside the UK."; seller identification: "From the website, you'll see 'Private' or 'Business' next to the seller's username." UK seller page id=5595 (Wayback 2025-11-28): "The seller sees the auction or offer price without the fee".
  - implication: On ebay.co.uk the user's max is already the most they pay for the item itself. Don't add the fee again. Detect private versus business from the item page to explain the fee portion. Gixen's moderator claimed (2025-06-02) that bidding via other sites avoids the fee; that is unverified (open question).
- **BUY6** [medium] Buyer Protection fees also exist on eBay Australia, Italy and France, and other sites display prices differently for the same listing.
  - evidence: UK BPF page (2026-10-25): "Buyer Protection fees may also apply to purchases completed on other eBay sites ... Australia | Italy | France". Gixen forum t=11826 (2026-09-20): "On ebay.fr it's wrong ... because of buyer protection add on ebay.fr"; moderator: "Ebay displays prices differently on ebay.co.uk than they do for exactly the same auction on other sites, like ebay.com."
  - implication: Fee-inclusive pricing and minimum-bid quirks are not UK-only. Always bid on the same site (domain) the price was read from, and record that domain per snipe.
- **BUY7** [high] UK VAT on imports: eBay collects VAT at checkout on orders up to £135 sent from outside the UK, calculated on item plus postage. Above £135, VAT and duties are paid at the border. eBay International Shipping may offer import-charge prepayment, and combined postage isn't available for auctions.
  - evidence: UK Paying tax on eBay purchases https://www.ebay.co.uk/help/buying/paying-items/paying-tax-ebay-purchases?id=4771 (Wayback 2026-04-20): "prices on eBay.co.uk are shown inclusive of VAT"; "VAT is calculated on the order total including item price, shipping and any additional costs such as insurance."; collects on "Orders up to £135 sent from outside the UK"; "VAT on purchases valued at over £135 will be payable directly to the UK customs authorities by the buyer." UK International purchases (Wayback 2026-10-06): "Listings may also include an estimate of customs and import charges ... aren't final until you pay"; "Combined postage is also not available for auction-style listings." Gixen t=11633 (2025-11-15): a '£100 win became £120' case was import VAT.
  - implication: For non-UK sellers, the total should add 20% VAT (standard rate; reduced rates exist) on (bid + postage) when at or below £135. Above £135, flag 'VAT + duty + carrier fee due on delivery (estimate)'.
- **BUY8** [medium] Bid amounts are always in the listing's currency.
  - evidence: Gixen FAQ (fetched 2026-11-04): "Bid amounts are always in the currency in which the auction was listed. Thus, if the current bid in the auction shows as GBP 1.50, then entering a bid of 3.00 in Gixen would mean that Gixen would bid GBP 3.00."
  - implication: For ebay.com or ebay.de items, store the max in the listing currency and show a GBP estimate separately. Never convert at fire time.
- **OFF1** [high] Default snipe offsets in established tools range from 3 to 10 seconds.
  - evidence: Gixen FAQ: "How close are your snipes to the end of the auction? 5 seconds or less."; mirror offsets "3, 6 (default), 8, 10, 12 or 15 seconds"; Play review 2025-08-24: "The default bid (without subscription) is six seconds before the end"; Gixen owner 2026-09-05: "I always advocated for 8 second offset as optimal". eSnipe/AuctionStealer help: "We recommend using either a 3 or 5-second buffer." EZsniper FAQ 2023-11-24: "Our default is 4 to 5 seconds before the end of the auction, and we do not suggest a bidding interval any shorter". PerfectSniper: minimum 3 s, "the optimal time to fire a shot is 7 seconds". Myibidder forum 2013-06-03: "All mobile apps offer 3 seconds as the lowest snipe time." esniper man page: "default value is 10 seconds". Biet-O-Matic BE (browser extension, repo 2021-01-22): default 5 s (release notes) or 10 s (manual). JBidwatcher (2018): snipemilliseconds default 30000. Auction Sniper community snippet (undated): "I have 12 seconds as the default lead time".
  - implication: Default the on-device offset to about 6 to 7 s, user-adjustable 3 to 15 s. Below 3 s, mobile latency makes late bids likely; above about 10 s, humans have time to react.
- **OFF2** [high] The chosen offset balances rival reaction time against the risk of the bid arriving late. Ties and close calls favour the earlier bid, and Sunday evenings bring eBay load spikes.
  - evidence: PerfectSniper: "usually it takes less than a second to fire a shot"; "scheduling your Sniper to shoot 1 second before the end of the auction is not only risky but also can decrease your chances of winning". Reddit r/Ebay (thread b4l5cd, undated snippet): "For higher-priced items, we recommend a snipe lead time of of 7-10 seconds ... before other snipers using a 3-second lead time, and you will win the 'close calls.'" Gixen moderator 2026-10-19: "Apparently Ebay had a bit of a wobble for a minute or so ... Sunday evening is definitely the most busy time for Ebay."; "typically when there is a problem - there is a problem, nothing helps." Myibidder 2013: shorter times mean "a higher risk for your snipe to be late due to possible networks lags".
  - implication: Offer two presets: 'safe' (8 to 10 s, wins close calls against 3 s snipers at equal amounts) and 'tight' (4 to 5 s). Record the measured fire-to-response time for every bid so the user can tune it on their own network.
- **OFF3** [medium] Extended Bidding changes the offset calculus: the Gixen owner suggests about 1 s may now be best, Gixen uses 6 s on its main server and 1 s on its mirror, and users asked for 61 to 70 s offsets to avoid triggering an extension.
  - evidence: Gixen forum t=11808 2026-09-05: "auction time extensions may make 1-second offset as the best choice"; 2026-09-07 user: "could you add an option for 65s or 70s lead time so bids don't trigger the extension?"; 2026-09-06: "Or just bid at 61 seconds and be done with it." Gixen blog 2026-09-23: mirror "will bid 1 second before auction end"; main "default to 6 seconds".
  - implication: For extendedBidding listings, offer: (a) the normal 6 s full-max snipe (the extension follows, and proxy bidding defends the max); (b) 'T-61 s' so the bid doesn't itself trigger a reset (inferred: a rival bidding in the last minute still extends). Avoid sub-2 s offsets on a phone given clock and network jitter.
- **OFF4** [medium] Client-side precedent: stage the bid early and only confirm at fire time, so the time-critical step is a single request.
  - evidence: Biet-O-Matic BE manual (github sweitzel/Biet-O-Matic-BE, last commit 2021-01-22): "one minute before the auction ends BE opens the article in a new browser tab"; "starts the bidding process 30 seconds before the auction ends"; "The maximum bid is sent - This opens a new sub window (modal), in which eBay expects a confirmation of the bid - BE waits until the bidding time to confirm the bid"; keeps "at least 10 seconds between two auctions". eBay community guide (2023-02-23 snippet): "click 'Place bid', then sit at that Review screen until there's 3 seconds left and finally hit 'Confirm bid'". eBay AU community 2025-06-03 confirms the current 'review bid' then 'confirm bid' flow.
  - implication: Recommended flow (inferred): reload the item at T-60 to 90 s, open the bid layer and fill the max at T-20 to 30 s (catching modals or verification), then confirm at T-offset. Space snipes at least 10 s apart, or run them sequentially in one WebView.
- **OFF5** [medium] Latency budget: firing takes about 1 s on a good server link; detecting extensions takes extra calls; a phone adds variable radio and clock error (inferred).
  - evidence: PerfectSniper: "usually it takes less than a second to fire a shot". Gixen blog 2026-09-23: extension detection "requires 3 calls to eBay instead of one". Android clock may be about 2 s off (END11).
  - implication: Budget about 1 to 3 s for the confirm round-trip on mobile data, plus up to 2 s of clock error if not using network time. With network time and pre-staging, 5 to 7 s keeps a safe margin. Measure on the user's Pixel 9a and adapt.

## Open questions
- Is Extended Bidding active broadly for UK-listed (not AU-listed) auctions on ebay.co.uk as of November 2026, or only in tested categories? The UK help text after August 2026 isn't archived. Snippets show the label on some ebay.co.uk item pages, but those items may be AU-listed.
- Does a bid that doesn't change the high bidder (it only raises the price) still reset the 60-second clock? eBay's text says 'any bid'; one 2026-09-28 anecdote suggests not.
- When the UK Buyer Protection fee applies, is the minimum next bid computed as fee-inclusive current price plus increment (Gixen moderator: GBP 205.20) or as fee(ex-fee price plus increment) (my algebra: GBP 205.40)? eBay's displayed minimum has been misreported at least once (Gixen 2026-11-01).
- Does bidding on a UK private seller's auction through ebay.com (instead of ebay.co.uk) avoid or change the Buyer Protection fee? The Gixen moderator claimed so in June 2025; unverified for 2026. Fee-inclusion semantics per site need a live check from the phone.
- What exactly do the 2026 'standard security steps' for first-time or higher-value bidders look like, and does opening the bid layer (before Confirm) trigger them? If not, the pre-flight can't surface them early.
- Is per-auction phone verification (July 2025) still in use in 2026, and once completed for an auction, does it carry over to a later automated bid on the same auction from the same WebView session?
- For UK private sellers using Simple Delivery, can the postage shown on an auction change with the final price band (eBay changed rates for listings priced £75 to £750 from 2026-09-01)?
- Can an Android WebView in a sideloaded app sign in to eBay with passkeys? Google sign-in is blocked in embedded WebViews; passkeys likely need WebAuthn support that eBay's asset links won't cover. This needs testing on the Pixel 9a.
- How does eBay's bot management (Akamai-style cookies, behaviour telemetry in the global header) treat programmatic clicks inside a real WebView? No public evidence found either way.
- Is there an official eBay help statement of the tie rule (earliest of equal maximums wins)? Only community posts and third-party docs were found.
- How long does an eBay 'stay signed in' session last in a WebView before sign-in or a captcha is forced again? Reports range from every 15 minutes (2021) to weeks; none are authoritative.

## Report
# eBay rules and mechanics that affect an on-device sniper (research date 2026-11-04)

## 0. Method, constraints, local copies

- No request was sent to any eBay marketplace host from this machine. eBay help, policy and developer pages were read from **raw Wayback Machine snapshots**, fetched as `https://web.archive.org/web/<timestamp>id_/<original-url>`. The snapshot timestamp is the "as of" date of the eBay text.
- One direct WebFetch to www.ebay.com (`https://www.ebay.com/help/buying/bidding/bidding-basics?id=4003`) returned only an eBay bot-verification page: "To keep eBay a safe place to buy and sell, we will occasionally ask you to verify yourself." That used 1 of the 5 allowed direct fetches; no more were made.
- Non-eBay sources (Gixen blog and forum, Value Added Resource (VAR), stampboards, collectors.com, etc.) were fetched live on 2026-11-04. Their post dates are given below.
- Local copies:
  - `/var/tmp/sniper-research/rules/wayback/<site>_<topic>_<timestamp>.html`, with some `.txt` extractions.
  - `/var/tmp/sniper-research/rules/web/<name>/page.html|page.txt`.
- Helper scripts are in `/var/tmp/sniper-research/rules/tools/`: `avail.sh` (Wayback availability), `wb.sh` (raw snapshot download), `html2text.py`, `phpbb_posts.py`, and `ysearch.py`.
- **Search tool note for other agents.** `/var/tmp/sniper-research/tools/websearch.py` (Bing) returns only eBay homepages for any query containing "ebay". `/var/tmp/sniper-research/rules/tools/ysearch.py` (Yahoo HTML) returns real results.
- The Wayback CDX API was flaky ("Temporarily Offline"). The availability API (`https://archive.org/wayback/available?url=...&timestamp=...`) worked.

---

## 1. Official stance on sniping, User Agreement, robots.txt, APIs

### 1.1 Bid-sniping help page: US and UK say the same thing

**US**: `https://www.ebay.com/help/buying/bidding/bid-sniping?id=4224` (Wayback 2026-07-17; identical 2024-08-12 at `/auctions-bidding/bid-sniping?id=4224`).

> "Waiting until the last few seconds of an auction to make a winning bid is known as bid sniping. This tactic is used to try to prevent other bidders from having a chance to place a higher bid before the auction ends."
>
> "Bid sniping—including the use of software that places bids for you—is allowed on eBay, but it doesn’t guarantee you’ll win an auction. Other members may be using the same tactic, may have set up automatic bidding, or may simply react quickly and place a higher bid before the auction ends."
>
> "## Automated bid sniping software — Many websites and software packages offer automated bidding based on the end time of an eBay auction. They try to automatically place your bid just before the auction ends, and some try to synchronize with eBay servers to ensure your bid wins."
>
> "When you sign up with third-party providers that offer automated bidding, you should make sure they are trustworthy before entrusting them with any personal data, like your eBay password. Remember, you are responsible for any information you give to third-party services."

**UK**: `https://www.ebay.co.uk/help/buying/auctions-bidding/bid-sniping?id=4224&ra=true` (Wayback 2025-10-18; same 2024-10-07 at `/bidding/bid-sniping?id=4224`). The wording is the same with UK spelling: "Bid sniping – including the use of software that places bids for you – is allowed on eBay, but it doesn't guarantee you'll win an auction." The page's "What you need to know" box repeats this sentence.

The **US Tips for winning auctions** page (Wayback 2025-10-09) also advises: "Bide your time. You stand a greater chance of getting the item by placing your highest bid in the closing seconds" and "Try bidding an uneven amount. Thinking of spending $10.50? Try $10.63 instead."

### 1.2 User Agreement clauses on automated access

**US UA**: `https://www.ebay.com/help/policies/member-behaviour-policies/user-agreement?id=4259` (Wayback 2026-10-26).

- Effective date: "For users who agreed to a prior version of our User Agreement, this agreement is effective as of June 28, 2026 ... The previous amendment to this User Agreement was effective for all users on February 20, 2026."
- Contracting entity: "eBay (UK) Limited, 1 More London Place, London, SE1 2AF, United Kingdom, if you reside in the United Kingdom".
- Section 3 "Using eBay": "you will not:"
  - "use any robot, spider, scraper, data mining tools, data gathering and extraction tools, or other automated means (including, without limitation buy-for-me agents, LLM-driven bots, or any end-to-end flow that attempts to place orders without human review) to access our Services for any purpose, except with the prior express permission of eBay;"
  - "circumvent any technical measures used to provide our Services;"
  - "interfere with the functioning of our Services, such as by imposing an unreasonable or disproportionately large load on our infrastructure;"
  - "share your log in credentials with any third parties. If you require that authorized third parties (employees, agents, etc.) have access to your account we offer a Multi-User Account Access program for that purpose;"
- History: cxnetwork.com (2026-01-28) reported "Taking effect from February 20, the new policy will prohibit unauthorized third-party AI-powered autonomous tools, including buy-for-me agents, LLM-driven bots, or any other tool that attempts to place orders without human review." It quotes eBay: "Unauthorized agentic AI tools and bots are not permitted to access or take actions on our platform."

**UK UA**: `https://www.ebay.co.uk/help/policies/member-behaviour-policies/user-agreement?id=4259` (Wayback 2026-10-26).

- Effective date: "This User Agreement is effective upon acceptance for those users who accept it through registration of an eBay account, and from 10 August 2026 for existing users. The previous amendment to this User Agreement was effective for all users on 22 July 2025."
- Prohibited acts:
  - "use any robot, spider, scraper, data mining tools, data gathering and extraction tools, or other automated means to access our Services for any purpose, except with the prior express permission of eBay;"
  - "bypass our robot exclusion headers, interfere with the working of our Services, or impose an unreasonable or disproportionately large load on our infrastructure;"
  - "circumvent any technical measures we use to provide the Services."
  - "share your log in credentials with any third parties."
- **The UK text has no "buy-for-me agents / LLM-driven bots / end-to-end flow" wording.**
- Also in the UK UA: "Bid update and other notification functionality in eBay's Services may not occur in real time." In section 4 "Abusing eBay", eBay "may limit, suspend, or terminate your user account(s) ... cancel bids ..." if it thinks "you are acting inconsistently with the letter or spirit of this User Agreement".

**Interpretation (inferred).**
- The help page's explicit allowance for "software that places bids for you" is the best evidence that a sniper is acceptable to eBay.
- The US "end-to-end flow ... without human review" clause is aimed at agentic checkout.
- A snipe that the user personally sets up (item and maximum) is reviewed by a human, but placed automatically.
- To stay on the safe side:
  1. Every snipe is created and confirmed by the user.
  2. The app never auto-raises a maximum, never buys, and never pays.
  3. No scraping beyond the user's own sniped items.
  4. Low request volume.
  5. Never bypass challenges.

### 1.3 robots.txt "Robots & Agent Policy"

`https://www.ebay.co.uk/robots.txt` (Wayback 2026-10-07, header `# v28_UK_August_2026`) and `https://www.ebay.com/robots.txt` (Wayback 2026-05-15, `# v26.2_COM_April_2026`) contain:

```
# The use of robots or other automated means to access the eBay site
# without the express permission of eBay is strictly prohibited.
...
# Robots & Agent Policy
#
# Checkouts are strictly for human users.
# * Automated scraping, buy-for-me agents, LLM-driven bots, or any
#   end-to-end flow that attempts to place orders without human review
#   is strictly prohibited.
# * Unauthorized use of automated agents in checkout may result in
#   legal action under our User Agreement: https://www.ebay.com/help/policies/member-behaviour-policies/user-agreement?id=4259
# * Approved enterprise integrations must use our official API and
#   comply with our API License Agreement: https://developer.ebay.com/join/api-license-agreement
```

Selected `User-agent: *` disallows (UK): `/b/*DO-NOT-BID`, `/bfl/` (bid history lives at `/bfl/viewbids/<itemId>`, as seen in a Gixen forum link), `/itm/*action=BESTOFFER`, `/itm/addToCart`, `/itm/fetchmodules`, `/itm/watch/`, `/signin/` (with `Allow: /signin/$`), `/ws/`.

### 1.4 Official APIs are closed to us

**PlaceOffer** (Trading API): `https://developer.ebay.com/Devzone/xml/docs/Reference/eBay/PlaceOffer.html` (Wayback 2026-07-15).

> "The automatic and/or scheduled use of PlaceOffer is prohibited. An example is sniping, which is prohibited. For each PlaceOffer call made by your application, the user first must take an explicit action."
>
> "The application shall not in any way, shape, or form collect the eBay username and password values ..."
>
> "Note: eBay is no longer accepting applications."

The same page has useful guidance:

> "For applications that offer status alerts for activities such as Outbid, please do not use GetItem for frequent item status checks."
>
> "Your application should gradually increase polling frequency as the ending time approaches. Making three GetItemStatus API calls each minute during the last three minutes of an auction should be the maximum frequency."
>
> "A BotBlock handle should be implemented wherever possible because eBay may ask users to respond to a CAPTCHA challenge from time to time."

Errors to expect: "Bid not above minimum", "Auction has ended", "User has been outbid", "User attempts to lower previous bid". Response fields: `MinimumToBid`, `ReserveMet`, `SuggestedBidValues`, `HighBidder`.

**Buy Offer API** (`placeProxyBid`), overview (Wayback 2026-05-05): "This is a (Limited Release) API available only to select developers approved by business units." It also notes: "If an item offers both buying options, after the first bid is placed, the Buy It Now option is removed and the item is sold to the highest bidder."

**How the incumbents bid.**
- Gixen FAQ (live 2026-11-04): "Gixen used to require this [credentials], but this changed back in 2017. Now you will be asked to authorize Gixen on eBay to be able to bid for you ... eBay authorizations automatically expire after 18 months".
- EZsniper FAQ (2023-11-24): "it allows us to bid for you using the eBay bidding API ... eBay requires you to re-link every 18 months".
- Inferred: they are grandfathered API users. Our WebView approach looks like a user in a browser, which has a precedent in the Biet-O-Matic BE extension (see section 7).

---

## 2. Account restrictions in practice and low-risk operation

### 2.1 What eBay documents as triggers

**Buying limits and restrictions**: US `https://www.ebay.com/help/buying/buying-limits-restrictions/buying-limits-restrictions?id=4012` (Wayback 2026-02-11). The UK page `/help/buying/buying-limits/buying-restrictions?id=4012` (Wayback 2025-04-24) is the same.

> "We may limit your ability to bid on auctions or make a Best Offer if:
> * You are bidding on an expensive item
> * We need to verify your identity
> * You need to add a payment method to your account
> * You have a history of canceled orders for not paying on time
> * You've suddenly and significantly increased your bidding and buying activity
> * You have several items awaiting payment"

Buying restrictions apply if: "You have multiple accounts, and one or more has a buying restriction already placed on it / You are violating our Abusive buyer policy / Your account is under review for other policy violations". Advice: "Establish a buying history by purchasing items that require immediate payment".

**Seller News, September 2026** (US, `https://www.ebay.com/sellercenter/news/2026-september`, Wayback 2026-10-27), "Trust and protection":

> "eBay may verify additional buyer details before auctions to help keep bidding fair and reliable."
>
> "Buyers with repeat unpaid items across multiple accounts will be blocked from bidding on auctions."

**Unpaid item policy**, US (Wayback 2026-10-27): "Buyers must pay for the items they purchase on eBay within 4 calendar days." / "Buyers with multiple unpaid items will have consequences applied to their account and any other linked accounts, including restrictions on buying, bidding, and making offers."

An older error message (StackExchange 2021-11-01 snippet): "Currently, your account has reached the limit for bidding and buying. If purchasing using web browser, you can increase this limit by providing additional verification".

### 2.2 Verification friction at bid time

1. **Phone verification per auction (from July 2025).** VAR 2025-07-13 (`/ebay-auction-bidding-phone-number-verification/`) quotes eBay staff: "When asked to verify by phone it will give the option of sms if you have a mobile number on your account and call/sms for a landline. When you complete the verification once you won't need to do it again for the same auction. The bid goes ahead as normal. However you may be prompted to verify on future auctions. We are unable to share any specific criteria details due to security reasons that could allow circumvention." eBay UK staff gave the aim as "enhancing the overall experience for buyers and sellers by reducing sniping, shill bidding and unpaid item situations." Buyers reported codes arriving too late: "it took about a minute to get and by then it was too late". EcommerceBytes 2025-07-22 snippet: "Phone verification has been introduced to the bidding process."
2. **First-time and high-value checks (autumn 2026).** US Automatic bidding (Wayback 2026-10-19): "Tip — If you are placing high bids for the first time on eBay, we may ask you to verify your identity or take other standard security steps to keep auctions safe and reliable for both buyers and sellers. See How bidding works for more information." A Yahoo snippet of `https://www.ebay.com/help/buying/bidding/bidding?id=4003` (seen 2026-11-04, absent from the 2026-10-10 snapshot) reads: "When bidding in an auction for the first time on eBay, or placing bids on higher-value items, you may be asked to verify your information with some standard security steps. See the FAQ below for more information."
3. **Payment preferences and autopay.** UK How bidding works (Wayback 2025-07-15): "You may be asked to provide a payment method and postage address when making a bid. In this case, if you win the auction, payment will be taken automatically." VAR 2024-02-22 (modified 2024-06-10) reported a seller setting "require buyers to provide a payment method before they place a bid" and test pop-ups "Introducing payment preferences / You'll confirm your payment method and shipping address" and "Introducing Automatic Checkout ... Before you bid, set your payment preferences".
4. **Age verification (UK).** Age-restricted items page (Wayback 2025-08-20): "To bid or buy certain items on eBay, you must be 18 or older, and you’ll be asked to verify your age on eBay". Bladed items need a credit card check ("Debit cards may not be used to verify age."). Alcohol uses "VerifyMyAge".
5. **Errors after confirm.** Gixen moderator (forum t=11842, 2026-10-05): "This is where errors such as this one happen, after you confirm." Example from eBay: "The sale of lasers is subject to restrictions on eBay.com.au. These restrictions apply to the purchase of these items from other eBay sites." Moderator 2026-09-27: "You don't have to actually place a bid these days to find out... if you can get to the point of entering an amount it's likely your bid will be accepted."

### 2.3 Captchas and bot challenges

- The WebFetch challenge text above (2026-11-04).
- This machine's IP is blocked with HTTP 403. An earlier agent's request got an eBay "Error Page" with reference `0.566bdc17.1793760840.cb1980e`, and its cookie jar holds `bm_so` and `bm_s` (file `/var/tmp/sniper-research/ebay-pages/cj.txt`). The `bm_*` names match Akamai Bot Manager (**inferred**).
- eBay's global header, in every archived page checked from 2024 to 2026, contains `risk={behavior_collection_interval:JSON.parse("{\"2500857\":5000,\"2507978\":5000,\"default\":15000}")...}`. This looks like behavioural telemetry sampled every 5 to 15 s (**inferred** meaning).
- Ordinary users hit captchas too:
  - Reddit r/Ebay 2025-02-08 "Ebay Catchpa Loop Fix".
  - Facebook resellers group 2026-06-01: "Lately I've been getting this verification Captcha from Ebay."
  - eBay UK community "Cannot make purchases due to captcha" (undated): "the page reloads and displays a captcha for 'security' ... This repeats endlessly".
  - Reddit r/Flipping (ID nr24tn, about 2021): "eBay is requiring me to sign in and solve Captcha's every 10-15 minutes".
- PlaceOffer docs: CAPTCHA "from time to time" even for API bidders.

### 2.4 Reports specific to sniping tools

- No credible 2024 to 2026 report was found of eBay suspending an account for using a sniper.
- One Auction Sniper community thread is titled "My account suspended by eBay for using Auction Sniper!!" (undated). It quotes the generic "You have been suspended from eBay because our records indicate your account was involved in activities that violate our policies." The forum is offline ("This Crowdstack Has Been Unplugged") and not archived, so the cause is unknown.
- Recent Gixen failures are bid rejections, not bans: "You are not eligible to bid on this item" (t=11832, 11842, 11877; Sept to Oct 2026), linked to order cancellations, restricted categories or eBay bugs.
- sellerchamp.com (2023-06-04) notes that suspensions for unusual activity can follow "multiple logins from different locations or IP addresses".

### 2.5 Practical low-risk operating rules (inferred, anchored to the sources above)

1. **One device, one session, one IP.** Never proxy eBay traffic through a server, and never share cookies (UA credential clause; unusual-login risk).
2. **Use the real WebView.** JavaScript on, eBay's own scripts running, default Android WebView UA (Chrome-based). No UA spoofing. No direct HTTP replay of bid endpoints outside the WebView (bot-manager cookies and telemetry).
3. **Polling budget, capped at eBay's own API guidance** ("three ... calls each minute during the last three minutes ... should be the maximum"):

   | Time to end | Check rate |
   |---|---|
   | When the snipe is added | once |
   | Until T-1h | about hourly |
   | T-1h to T-10m | every 10 min |
   | T-10m to T-3m | every 1 to 2 min |
   | Last 3 min | at most every 20 s |
   | Extended Bidding rounds | one check per round |

   Never loop on `/bfl/viewbids/` (robots disallow).
4. **Challenge handling.** On any captcha, "verify yourself", sign-in, phone-code or age-verification page: stop, raise a high-priority notification, show the WebView. Never solve or bypass (UA: "circumvent any technical measures").
5. **One full-max bid per snipe** by default. Avoid ladders of small bids, which match eBay's shill signals: "multiple bids in short, deliberate intervals, or bidding several times in small amounts".
6. **Spread out bursts.** Space snipes at least 10 s apart (Biet-O-Matic practice). Warn when a new account schedules many snipes ("suddenly and significantly increased your bidding").
7. **Payment hygiene.** Remind the user that wins are charged automatically or must be paid within 4 days; unpaid items lead to bidding blocks.

---

## 3. Proxy bidding mechanics

- **Mechanism.** US Automatic bidding (Wayback 2026-10-19): "enter the maximum amount you’d like to pay for the item and select Place bid. We’ll bid in increments on your behalf to keep you in the lead but only up to your limit." / "When someone else places a bid, we’ll place a slightly higher bid on your behalf. The amount the bid increases by is known as a bid increment." / "Occasionally you’ll see bids increase by less. This means that someone else placed a bid slightly higher than your automatic bid amount."
- **Price formula.** The Gixen FAQ: "The winning bid amount will be the second-highest bid plus up to one increment". The legacy eBay overview example (2016): Tom max $12, Laura max $15, "Tom's bid is raised to his maximum of $12.00. Laura's bid is now $12.50." Combined: `price = max(startPrice, min(winnerMax, secondMax + increment(at secondMax level)))`, then raised to the reserve when `winnerMax >= reserve`. The increment is "determined based on the current high bid on the item" (legacy bid-increments page, Wayback 2016-01-02).
- **Beating by less than one increment.** Legacy help: "You can be outbid by less than a full increment. The winning bid needs only to be higher than the next highest maximum bid by one cent." Example: a $20.01 bid beats a $20.00 maximum and wins at $20.01.
- **Minimum-bid rule.** Browse API `minimumPriceToBid` (Wayback 2026-03-12): "to place a bid it must be equal to or greater than this amount. If the auction hasn't received any bids, the minimum bid price is the same as the starting bid. Otherwise, the minimum bid price is equal to the current bid plus the bid increment."
  - Bids below it are rejected. Gixen's status is "BID UNDER ASKING PRICE"; PlaceOffer's error is "Bid not above minimum".
  - Example (Gixen t=11405, 2025-01-10): a bid of $107.55 was rejected because the current price was $106.50 and the increment had become $2.50 above $100, so the minimum was $109.00.
- **What happens if the max is below current + increment.** The bid cannot be placed at all. The app should flag "price has passed your maximum" as soon as `max < minimumToBid`. That is also when to send the "price passed your maximum" notification.
- **Ties.** The earlier bid wins:
  - eBay Canada community (2024-01-04 snippet): "if the same maximum bid is made by multiple users, the bid placed first takes precedence".
  - esniper man page: "In case two bids are within one bid increment of each other, the first bid placed wins."
  - PerfectSniper: "if two people bid a similar amount (or the same amount) then the person who bids the first will win".
  - Reddit r/Ebay (thread 199erxs, about Jan 2024): "if both bids are the same, the first bid wins (automatic wins, sniper loses)".
  - No current eBay help page states the tie rule outright. The legacy overview says "If another bidder places the same maximum bid or higher, we notify you so you can place another bid."
- **Reserve.** US reserve page (Wayback 2026-07-12): "Unless they state their reserve price in the listing, you won't know what it is until you either meet it or bid above it. If you bid below the reserve price, you'll see a 'Reserve not met' message. This means that even if you're the highest bidder at the end of the auction, you won't win the item." / "Sellers can lower their reserve price during the auction or make a Second Chance Offer". The DE example (Wayback 2026-04-21): start EUR 1,00, reserve EUR 100,00; Bieter A bids 1,50, so the reserve is not met; Bieter B's max of 150,00 "Das aktuelle Gebot wird auf EUR 100,00 erhöht". The legacy rule: "if your maximum bid is at least the reserve price, we automatically increase your bid to meet the reserve".
- **Second Chance Offer.** UK (Wayback 2025-11-06): "you'll be able to buy the item at the amount you offered in your last bid"; window "1, 3, 5, or 7 days".
- **Shill bidding.** UK policy (Wayback 2025-11-07):
  - "Shill bidding is when sellers – or someone they know – place a bid on their item to drive the price up."
  - "People who know the seller ... can't bid on that seller's items, but they can use options like Buy It Now".
  - Detection signals: "Bidding patterns that show no advantage to the bidder but significantly increase the item's price may suggest shill bidding. Other questionable patterns include multiple bids in short, deliberate intervals, or bidding several times in small amounts even when other bids haven't been placed on an item."
  - Bidder names are anonymised: "each bidder is anonymous name (Bidder 1, for example)".

---

## 4. Bid increment tables (fallback only; read eBay's displayed minimum first)

| Site (source, snapshot) | Table |
|---|---|
| **ebay.co.uk** `/help/buying/bidding/automatic-bidding?id=4014` (2025-12-09) | £0.01–0.99: 0.05 · 1.00–4.99: 0.20 · 5.00–14.99: 0.50 · 15.00–59.99: 1.00 · 60.00–149.99: 2.00 · 150.00–299.99: 5.00 · 300.00–599.99: 10.00 · 600.00–1,499.99: 20.00 · 1,500.00–2,999.99: 50.00 · 3,000.00+: 100.00 |
| **ebay.com** same path (2026-10-19); **ebay.ca** (2026-06-11) and **ebay.com.au** (2025-08-19) identical | $0.01–0.99: 0.05 · 1.00–4.99: 0.25 · 5.00–24.99: 0.50 · 25.00–99.99: 1.00 · 100.00–249.99: 2.50 · 250.00–499.99: 5.00 · 500.00–999.99: 10.00 · 1000.00–2499.99: 25.00 · 2500.00–4999.99: 50.00 · 5000.00+: 100.00 |
| **ebay.de** (2026-04-21); **ebay.at** (2026-04-21) and **ebay.ch** (2026-06-09, shown in EUR) identical | EUR 1,00–49,99: 0,50 · 50,00–499,99: 1,00 · 500,00–999,99: 5,00 · 1.000,00–4.999,99: 10,00 · über 5.000,00: 50,00 |
| **ebay.fr** (2024-11-09), **ebay.it** (2026-05-17), **ebay.es** (2026-05-18) | EUR 0,01–49,99: 0,50 · 50,00–499,99: 1,00 · 500,00–999,99: 5,00 · 1.000,00–4.999,99: 10,00 · 5.000,00+: 50,00 |
| **ebay.nl** (2026-04-11) | EUR 0,01–0,99: 0,05 · 1,00–4,99: 0,20 · 5,00–14,99: 0,50 · 15,00–59,99: 1,00 · 60,00–149,99: 2,00 · 150,00–299,99: 5,00 · 300,00–599,99: 10,00 · 600,00–1499,99: 20,00 · 1500,00–2999,99: 50,00 · 3000,00+: 100,00 |
| **ebay.pl** (2024-12-14) | 1,00–4,99 zł: 0,25 · 5,00–24,99: 0,50 · 25,00–99,99: 1,00 · 100,00–249,99: 2,50 · 250,00–499,99: 5,00 · 500,00–999,99: 10,00 · 1000–2499,99: 25,00 · 2500–4999,99: 50,00 · 5000+: 100 |

**Caveats.**
- US help (Wayback 2025-04-12): "We’re currently testing out new bid increments in order to make the bidding process less complex for both buyers and sellers. As a result, you may experience a difference in your bid increments from the chart below." VAR 2025-03-20 reported forced $1 increments on items under $25 for some accounts.
- The legacy note says eBay "may, from time to time, change (increase or decrease) these increments ... on all or parts of our site in order to test new features".
- **Buyer Protection fee effect.** For UK private sellers (and AU, IT, FR equivalents), displayed prices include the fee while increments are applied to the ex-fee price:
  - Gixen moderator 2025-03-08: increments "are based on the auction price once buyer fees are taken off".
  - So displayed steps are larger. In the 4% band, a £5 step shows as about £5.20 (**inferred**).
  - Gixen owner 2026-11-01 (eBay AU): "the increment was not really 1.00, but 1.06 in this price range" and "eBay misreported the next minimum required increment".
  - Rule for the app: `minimumToUse = max(minimumDisplayedByEbay, ownComputation)`.

---

## 5. Auction end mechanics

### 5.1 Extended Bidding (soft close): the major 2025–2026 change

| Date | Event | Source |
|---|---|---|
| ~2025-07 | Trading Cards test, US/UK/CA/AU help pages: "We're currently testing extending auction durations by 2 minutes when a bid is placed in the last 2 minutes of an auction. This test will apply to select items in the Trading Cards category. Please make sure you're on the latest version of the app to see this feature." | UK How bidding works (Wayback 2025-07-15); VAR 2025-07-07 (banner absent from the 2025-06-21 archive) |
| 2025 | eBay Live already offers sellers an optional 5 s soft close ("adds an extra 5 seconds if a bid is placed in the last 5 seconds") | VAR 2025-07-07 |
| early 2026 | Vehicles added to the pilot; US help: "We're testing extended bidding on select Trading cards and Vehicles auctions. If a bid is placed in the final minute of an auction, the remaining time will reset to 60 seconds until all bidding stops." | VAR 2026-08-03; Yahoo snippet of US help `bidding-basics?id=4003` |
| 2026-08-03 | AU announcement: "From 17 August [later edited to 'mid-August'], we’re rolling out Extended Bidding ... When a bid is placed during the final minute of an auction, the remaining time will reset to 60 seconds until all bidding stops. The auction continues until no more bids are placed during the final minute" ... "Extended Bidding applies to all auction items listed in Australia, including when those listings are seen by buyers in other markets." Also: "During our pilot, we saw stronger bidding activity and, in many cases, higher final sale prices on high-demand listings." | AU community post (Wayback 2026-10-21); stampboards t=110407 (2026-08-05 copy) |
| 2026-08 | "eBay has updated bidding policy help pages for the US, UK, Canada and Germany to remove mention of specific categories" | VAR 2026-08-03, modified 2026-08-23 |
| 2026-09-02 | US collectors notified: "A bid in the last 60 seconds extends the bidding for another 60 seconds." | collectors.com 1125373 |
| 2026-09-05 | Gixen: "rolled out on eBay Motors, and it looks like eBay has started testing it on eBay Australia" | Gixen forum t=11808 |
| 2026-09-06 | UK sighting: "This popped up just now for me on the UK site" | stampboards, post by Rog 2026-09-06T23:57Z |
| 2026-09-08 | Gixen owner: "It looks like eBay reverted time extensions on eBay Australia (my guess). Bids are no longer extending end time" | Gixen forum t=11811 |
| 2026-09-14 | "eBay Australia has started this today September 14th" | Gixen forum t=11808 |
| 2026-10-10 | US help: "We're testing extended bidding on auction listings in some categories. Any bid placed in the final 60 seconds resets the clock to 60 seconds. The auction ends when the clock runs out with no new bids." | `https://www.ebay.com/help/buying/bidding/bidding?id=4003` (Wayback 2026-10-10) |
| late Oct 2026 | "The policy is now mandatory for all eBay Australia auctions ... and is being tested in selected U.S. categories ... Extended Bidding is not an option that individual sellers can simply turn on or off." | maestronet forum thread 365970 (about 2026-10-30) |
| 2026-11-03 | "Extended Bidding Ing is Definitely ON in Australia" | Gixen forum t=11811 |

**Item-page label (detection signal).** Seen in 2026 search snippets for UK, US, CA and AU item pages:

> "Extended bidding Bids placed in the last 1 minute reset the time to 60 seconds."

Variants end with "Learn more" or "The highest bid wins when time runs out." Examples: `https://www.ebay.co.uk/itm/287661551361`, `https://www.ebay.co.uk/itm/267819003587`, `https://www.ebay.com/itm/147663231815`, `https://www.ebay.com.au/itm/257823150682`.

**Effects and failure reports.**
- Gixen blog 2026-09-23 (`https://www.gixen.com/blog/ebay-auction-time-extensions/`):
  - "These extend the auction end time 1 minute from the time of the last bid."
  - "Gixen now wins around 10% less auctions. Gixen users still win far under their maximum bid amount".
  - Changes: "1. Gixen will do the bidding incrementally ... Gixen will wait the maximum of 12 extensions ... before submitting user's maximum bid. 2. Gixen Mirror ... will bid 1 second before auction end ... 3. For extended auctions Gixen main server will default to 6 seconds. This is necessary in order to detect whether or not an auction extends, which requires 3 calls to eBay instead of one."
- Failure reports:
  - t=11811 2026-09-08: max $12.50 lost; sold for $6.56.
  - t=11890 2026-11-03: max AU$217.79 lost; Gixen stopped bidding at AU$155.85; sold for AU$158.35.
  - t=11854 2026-10-13 and 2026-11-03: outcome mis-reported, "This is a result of a eBay bug. I contacted eBay".
  - t=11879 2026-11-01: rejected because the minimum was misreported with the Buyer Protection fee.
- Moderator 2026-09-21: "until the maximum submitted by Gixen is outbid there's not even any way to know whether your bid will eventually win, or not."
- Moderator 2026-09-21 on groups: with extensions "there's an increased possibility of winning more items than are intended by the grouping".
- Bug report (collectors.com 1125884, 2026-09-28): "instead of adding 60 seconds to the end, it added 60 minutes."
- Unanswered question in the same thread: "Does it only reset if a new bid leader is made?"

**Strategy options for the app (inferred).**
- (A) **Default.** One full-max bid at T-offset (about 6 s). Proxy bidding then defends the max through any extensions. Keep monitoring until the page says the listing ended.
- (B) **T-61 s.** A single bid at T-61 s so the user's bid doesn't itself reset the clock. Rivals still get about 60 s either way.
- (C) **Incremental ladder (Gixen-style).** Not recommended: fragile, needs many more requests, and looks like shill patterns.
- **Results.** Report won or lost only after an explicit "ended" state, plus one confirmation check about 1 to 2 minutes later.

### 5.2 Early endings

**Seller ends early.** UK `https://www.ebay.co.uk/help/selling/listings/creating-managing-listings/canceling-listing?id=4146` (Wayback 2026-07-14). The table, parsed with rowspans:

- Fixed price: always.
- Auction, 12 or more hours left: no bids, yes; any bids, "Yes, you may end the listing but ... Private sellers: 5% of the highest bid, up to a maximum fee of £20; Business sellers: a final value fee based on the highest bid. Alternatively, you can sell the item to the highest bidder."
- Auction, fewer than 12 hours left: no bids, yes; bids with reserve not met, yes (with fee) or sell; bids with reserve met or no reserve, "No, you can only sell the item to the highest bidder".

The US page (Wayback 2026-06-10) has the same structure, with "Yes, you may end the listing by cancelling all bids" for 12+ hours with the reserve met. Trading API guide (Wayback 2026-10-14): "If you specify SellToHighBidder, the listing ends as a successful auction". The shill policy (UK 2025-11-07) agrees: "You can end a listing early unless there's already a bid that exceeds your reserve price and your listing is within 12 hours of its end time."

**Buy It Now.**
- US (Wayback 2026-10-13): "In most categories, the Buy It Now price must be at least 30% higher than the auction price. When someone bids, the Buy It Now option usually disappears; however, in the case of reserve price listings, the Buy It Now option remains until the reserve price is met. In some categories, the Buy It Now option may continue to be available after the first bid for a limited time."
- UK (Wayback 2025-10-10): "at least 40% higher" ... "The Buy It Now price is available until someone bids on the item or until the reserve price is met. In certain categories, Buy It Now disappears only after 50% of the Buy It Now price is reached through bidding: Parts & Accessories on eBay Motors, Tickets, Clothing, Shoes & Accessories, Cell Phones & PDAs".

**Best Offer on auctions.** Trading API `BestOfferDetailsType` (Wayback 2026-07-11): "Note: The Best Offer feature is now available for auction listings on the following sites: US, Canada, UK, Germany, Australia, France, Italy, and Spain. However, sellers must choose between offering Best Offer or Buy It Now on an auction listing, as both features cannot be enabled on the same auction listing. If an auction listing is enabled with Best Offer, this feature will no longer be applicable once the listing receives its first qualifying bid." Real case (Gixen t=11865, 2026-10-21, eBay AU): an auction sold before its end because "The seller accepted a Best Offer, that's allowed on Ebay."

**Removal by eBay.** US How bidding works FAQ (2026-10-10): "The seller may have ended the listing early – although this is something we strongly discourage. Or, if the seller breached one of our policies, we may have had to cancel the listing."

### 5.3 End-time precision, time zones, clocks

- Browse API `itemEndDate` (2026-03-12): "returned in UTC format (yyyy-MM-ddThh:mm:ss.sssZ)", so millisecond precision.
- GeteBayOfficialTime (Wayback 2025-01-20): "If you are using this call just to get the time, eBay uses UTC so any internet time server can be used to keep the client in sync with UTC time."
- DST trap (Gixen t=11883, 2026-11-02): "It started at: 25 Oct 2026 at 6:00:00pm PDT Finished at: Nov 1, 2026 at 6:00PM PST with a duration of: 7 days ... a swap over in the listing of the bid times on Nov 1, from PDT to PST".
- Android clock (AOSP time-detection docs, fetched 2026-11-04): network (NTP) is prioritised over telephony (NITZ). A suggestion within "a couple of seconds" doesn't change device time by default (sample `systemClockUpdateThresholdMillis=2000`). `SystemClock.currentNetworkTimeClock()`: "Added in API level 33 ... synchronized using a remote network source outside the device ... performs no blocking network operations and returns values based on a recent successful synchronization event; it will either return a valid time or throw."
- The Pixel 9a runs Android 15 or later, so API 33+ is available (**inferred**).
- Design: store end times as UTC epoch milliseconds, re-read them at T-10 min and T-60 s, and schedule against the network clock plus `elapsedRealtime`.

### 5.4 Private listings, identity, outcome detection

US How bidding works (2026-10-10): "Sellers sometimes create private listings that keep bidders and the buyer anonymous ... There will be a note on these listings stating that they're private, and that only the seller can see your username." Usernames in bid history are anonymised in general. Outcomes must therefore come from the signed-in user's own item-page status or My eBay (Bids/Offers, Purchase history).

### 5.5 Payment after a win

- US (2026-10-10): "payment will be taken automatically after 24 hours (or instantaneously if shopping on eBay Live), or you can choose to manually check out immediately"; "Starting September 2026, some buyers will begin to see an Autopay at option after you place a bid on items that are eligible for combined shipping. You can choose 24-hr autopay ... or 4-day autopay".
- "Why can't I cancel my order after winning an auction? ... we don’t allow buyers to cancel orders after winning an auction."
- Unpaid policy: "Auction bids are binding, and bids can only be retracted under exceptional circumstances." / "For eBay Live auctions, payment is charged immediately".
- Gixen moderator (t=11863, 2026-10-20): "If you do have an automatic payment method set up already, Ebay will use it ... If you don't have an automatic payment set up you do indeed need to pay manually."

---

## 6. Retraction, buyer requirements, limits, extra steps, UK total cost

### 6.1 Retraction

- US `https://www.ebay.com/help/buying/bidding/retracting-bid?id=4013` (Wayback 2026-10-15): "Bids on items listed in Trading Cards categories, for example, can't be retracted." / "You can retract a bid if (Note: these options are only applicable for up to 1 hour after making the bid when the listing is ending in less than 12 hours): The seller significantly changed the description of the item / You accidentally bid the wrong amount. For example, you meant to bid $20, not $200." / "Tip — When bidding, always double-check that you've entered the correct amount – especially when making a last minute bid."
- UK (Wayback 2026-10-03) lists the same reasons plus "You can't reach the seller".
- Practical effect: a snipe can't be undone.

### 6.2 Seller buyer requirements and blocks

- UK Blocking a buyer (Wayback 2026-07-12): "They'll be unable to place bids or buy from you until you remove them from the list." Requirements can block "buyers living in a country you don't ship to, or buyers with excessive cancelled orders because they didn't pay for items."
- US buying limits page: "If a seller blocked you from bidding on their listings, you'll receive a notification when you try to place a bid. You also won't be able to bid on that seller's items using another account."
- How bidding works FAQ: "you'll see a message asking you to contact the seller when you try to place a bid."
- Gixen statuses seen: "AUCTION RESTRICTED: REQUIREMENTS NOT MET", "You are not eligible to bid on this item".

### 6.3 Extra steps that can appear in the bid flow (all must be caught by the pre-flight)

1. **Normal flow.** Place bid, then enter the maximum, then Review bid, then Confirm bid (eBay AU community 2025-06-03 "review bid button ... confirm bid button"; legacy help "Review your bid, and then click the Confirm Bid button").
2. **Payment preferences or autopay choice** (VAR 2024; US help Sept 2026 "Autopay at").
3. **Per-auction phone code** (2025).
4. **"Standard security steps"** for first-time or higher-value bids (2026).
5. **Age verification** (UK categories).
6. **Sign-in again, or captcha.**
7. **"Contact the seller"** (buyer requirements).
8. **Category or cross-border restriction errors** shown after confirm.

### 6.4 UK Buyer Protection fee (private sellers)

**Formula** (current): `https://www.ebay.co.uk/help/buying/paying-items/buyer-protection-fee?id=5594` (Wayback 2026-10-25).

> "When purchasing from UK-based private sellers, the Buyer Protection fee is calculated as: A flat fee of £0.10 per item, and 7% of the item price up to £20, and 4% of any portion of the item price from £20 to £300, and 2% of any portion of the item price from £300 to £4,000. There is a cap on the total fee amount you'll ever pay. Any portion of the item price over £4,000 will not incur any additional fee. When purchasing multiple quantities within a listing, the flat fee is only charged once."

eBay's examples: £5 gives £0.35 + £0.10, shown as £5.45; £20 gives £21.50; £1,500 gives £36.60 + £0.10 = £1,536.70; £7,500 gives £86.60 + £0.10 = £7,586.70. "The Buyer Protection fees displayed on this page are inclusive of UK VAT at 20%".

**History.**
- Launched February 2025 (phased).
- Wayback 2025-01-20: "A flat fee of £0.75 per item, and 4% of the item price up to £300, and 2% ... £300 to £4,000".
- 2025-06-11: "A flat fee of up to £0.75".
- VAR 2025-07-17: "Starting July 17, 2025, the Buyer Protection Fee will instead be calculated as" the 10p/7%/4%/2% scheme; "any Active Offers or Auctions that were started before the 17th will still use the previous fee calculation".
- 2025-11-06: "up to £0.10".
- 2026-07-10 and 2026-10-25: "£0.10".

**Included in bids.**
- "The Buyer Protection fee, if applicable, is already included in the item price that you see when browsing on the site and in any bids or offers you make, so there are no surprise fees at checkout."
- FAQ: "How is this fee calculated for Auctions and Offers where the final selling price isn't known? The auction or offer price you see on the site will always include the Buyer Protection fee. As the Buyer Protection fee is variable, the amount that you actually pay is calculated as part of the final auction selling price".
- Seller page id=5595 (Wayback 2025-11-28): "If a buyer bids on an auction or makes an offer on a listing, the Buyer Protection fee is included in their auction or offer amount ... The seller sees the auction or offer price without the fee".

**Scope.** "Buyer Protection is included at no cost to the buyer when purchasing from business sellers, or from private sellers based outside the UK." Excluded: "Cars, Motorcycles & Vehicles, Classified Ads and Property". How to tell: "From the website, you'll see 'Private' or 'Business' next to the seller's username." Other sites: "Australia | Italy | France" also have fee pages.

**Display quirks.**
- Bid-history pages show ex-fee amounts (Gixen t=11633, 2025-11-15; t=11857, 2026-10-15, where the main page showed "incl. GBP 8.37 Buyer Protection fee").
- The API price field is ex-fee: `price":{"value":"200.20","currency":"GBP"}` as reported by Gixen.
- The Gixen moderator claimed on 2025-06-02 that the same UK private-seller listing on ebay.com shows no fee ("They don't charge that premium via other sites"). This is unverified for 2026.

**Inverse mapping** (inferred algebra from the published formula; matches eBay's ex-fee 191.83 versus displayed 200.20 example). For a displayed fee-inclusive amount D:

```
p = (D - 0.10) / 1.07   if D <= 21.50
p = (D - 0.70) / 1.04   if D <= 312.70
p = (D - 6.70) / 1.02   if D <= 4086.70
p = D - 86.70           otherwise
```

### 6.5 Postage, VAT, import (UK buyer)

- **VAT** (`/help/buying/paying-items/paying-tax-ebay-purchases?id=4771`, Wayback 2026-04-20): "prices on eBay.co.uk are shown inclusive of VAT"; "VAT is calculated on the order total including item price, shipping and any additional costs such as insurance"; eBay collects on "Orders up to £135 sent from outside the UK" and "Orders of any value where the item is located in the UK, but the seller is not a UK seller"; "VAT on purchases valued at over £135 will be payable directly to the UK customs authorities by the buyer." Also: "eBay has introduced a delivery fee for certain items purchased on eBay.co.uk, and charges VAT on the eBay delivery fee where the fee applies."
- **International postage** (Wayback 2026-10-06): "Listings may also include an estimate of customs and import charges ... The charges you see on the listing aren't final until you pay"; with eBay International Shipping "You may be able to choose to pay for customs charges, import fees and taxes either at checkout or when your item arrives"; "Combined postage is also not available for auction-style listings."
- **Simple Delivery.** Rates for UK private sellers depend on price bands ("New Simple Delivery rates for Private Sellers will apply to listings priced between £75 and £750", ChannelX 2026-08-17). Whether the postage changes with an auction's final price is unverified.

### 6.6 "Most you would pay in total" (inferred formula)

- **UK private seller on ebay.co.uk:** `max (already includes Buyer Protection fee) + postage shown`.
- **UK business seller:** `max + postage` (VAT already inside UK prices).
- **Non-UK seller, item value ≤ £135:** `(max + postage) × 1.20` (standard rate, collected at checkout), plus any eBay International Shipping charges shown.
- **Non-UK seller, item value > £135:** `max + postage`, plus VAT + duty + carrier fee due on delivery (estimate and warn).
- **Non-GBP listing:** bids are in the listing currency (Gixen FAQ), so show a GBP estimate and a card/FX caveat.

---

## 7. Recommended bid offset and latency

| Tool | Default / range | Source (date) |
|---|---|---|
| Gixen (free main) | "5 seconds or less"; users report a 6 s default | FAQ (undated, (c) 2026); Play review 2025-08-24 |
| Gixen Mirror | 3, 6 (default), 8, 10, 12, 15 s | FAQ |
| Gixen owner's view | "I always advocated for 8 second offset as optimal"; with extensions "1-second offset as the best choice" | forum t=11808 2026-09-05 |
| Gixen under extensions | main 6 s, mirror 1 s | blog 2026-09-23 |
| eSnipe / Bid Sniper (AuctionStealer) | "We recommend using either a 3 or 5-second buffer." | help article (undated) |
| EZsniper | "default is 4 to 5 seconds ... we do not suggest a bidding interval any shorter" | FAQ 2023-11-24 |
| PerfectSniper | minimum 3 s, "optimal ... 7 seconds", "usually it takes less than a second to fire a shot" | help (undated, (c) 2005–2026) |
| Myibidder | "All mobile apps offer 3 seconds as the lowest snipe time." | forum 2013-06-03 |
| esniper | "default value is 10 seconds" | man page (repo 2018) |
| Biet-O-Matic BE (client-side browser extension) | default 5 s (release notes) or 10 s (manual); tab reload at T-60 s; process starts at T-30 s; confirm at bid time; ≥10 s between auctions | github sweitzel/Biet-O-Matic-BE (last commit 2021-01-22) |
| JBidwatcher | snipemilliseconds 30000 | cyberfox/jbidwatcher (2018) |
| Auction Sniper | "I have 12 seconds as the default lead time" | community snippet (undated); Play review "three seconds to go" |

**Why these values.**
- Late enough that a human can't react: "prevent a manual bidder to update their bid" (esniper).
- Early enough to survive latency and load: "Shorter intervals run a greater risk of arriving after the auction closes" (EZsniper). Sunday evening load: "Ebay had a bit of a wobble for a minute or so" (Gixen moderator 2026-10-19).
- Tie rule: at equal maximums the earlier snipe wins, which is the "close calls" argument for 7 to 10 s on high-value items (Reddit b4l5cd snippet).

**Under Extended Bidding.** Gixen added 2 extra calls for detection, and users asked for 61 to 70 s offsets "so bids don't trigger the extension". See 5.1 for the strategy options.

**Latency budget for the phone (inferred).**
- The final confirm is about 1 request (PerfectSniper: under 1 s server-side), plus mobile radio wake-up and RTT, about 0.5 to 2 s.
- Clock error can reach about 2 s if `System.currentTimeMillis()` is used.
- With `currentNetworkTimeClock()` and pre-staging (reload T-60 to 90 s; fill the bid layer T-20 to 30 s; confirm at T-offset), a 5 to 7 s default keeps a margin.
- Log fire-to-response durations for each snipe and suggest an offset from them.

---

## 8. Design checklist for the app (inferred, numbered)

1. Detect per listing, on every check:
   - auction vs fixed price;
   - Extended Bidding label;
   - reserve met/not met;
   - BIN present;
   - Best Offer on auction;
   - private vs business seller (for the fee);
   - currency and site domain;
   - eBay Live (exclude).
2. Monitor gently (section 2.5, item 3). Notify the user when `minimumToBid > max` ("price passed your maximum") or when the listing has ended early (seller end, BIN, accepted Best Offer, removal).
3. Pre-flight at T-10 min, and hours earlier for high-value or first snipes. Load the item in the WebView, confirm sign-in, open the bid layer, enter the max, and stop before Confirm. Escalate any interstitial to the user.
4. Fire sequence:
   - Reload at T-60 to 90 s.
   - Re-read the end time (UTC) and the minimum.
   - Fill the bid at T-20 to 30 s.
   - Confirm at T-offset (default 6 to 7 s; 3 to 15 s range).
   - Use the network-time clock.
5. After firing, parse the response:
   - "highest bidder";
   - "outbid";
   - below minimum;
   - not eligible;
   - verification;
   - ended.

   If Extended Bidding is on, keep following until "ended". Re-confirm 1 to 2 minutes later before notifying won or lost.
6. Never auto-raise, never auto-buy, never automate checkout or payment. Explain autopay and the 4-day payment rule after a win.
7. Guard against typos: show the amount in words and currency, warn at more than 3× the current price, lock edits in the last 2 minutes.
8. Total-cost display per section 6.6, with the fee portion shown for private sellers.

---

## 9. Source index (date = snapshot date or post date)

**eBay help, policy and developer pages (raw Wayback snapshots, local files in `/var/tmp/sniper-research/rules/wayback/`)**
- US bid sniping 2026-07-17 / 2024-08-12; UK 2025-10-18 / 2024-10-07.
- US How bidding works `bidding?id=4003` 2026-10-10; UK `bidding?id=4003` 2025-07-15.
- US automatic bidding 2026-10-19 / 2025-07-13 / 2025-04-12 (increment test note); UK 2025-12-09; DE and AT 2026-04-21; CH 2026-06-09; FR 2024-11-09; IT 2026-05-17; ES 2026-05-18; NL 2026-04-11; PL 2024-12-14; CA 2026-06-11; AU 2025-08-19.
- US retracting 2026-10-15 / 2025-08-17; UK 2026-10-03 / 2024-08-02.
- US reserve 2026-07-12; US tips 2025-10-09; UK Second Chance 2025-11-06.
- US UA 2026-10-26; UK UA 2026-10-26; UK robots 2026-10-07; US robots 2026-05-15.
- UK Buyer Protection fee 2026-10-25 / 2026-07-10 / 2025-11-06 / 2025-06-11 / 2025-01-20; UK seller fee page 2025-11-28.
- UK tax 2026-04-20; UK international postage 2026-10-06; UK age-restricted 2025-08-20.
- UK shill policy 2025-11-07; US buying limits 2026-02-11; UK 2025-04-24; US unpaid 2026-10-27.
- UK ending listing 2026-07-14; US 2026-06-10; US Selling with BIN 2026-10-13; UK 2025-10-10; UK Best Offer (buyer) 2026-11-03; UK blocking buyers 2026-07-12.
- US sign-in 2026-09-11; UK 2026-04-18; US Seller News Sept 2026 (snapshot 2026-10-27).
- AU Extended Bidding announcement (snapshot 2026-10-21).
- Developer docs: PlaceOffer 2026-07-15; Offer API overview 2026-05-05; placeProxyBid 2026-05-09; BestOfferDetailsType 2026-07-11; Browse getItem 2026-03-12; GeteBayOfficialTime 2025-01-20; End early guide 2026-10-14.
- Legacy pages.ebay.com bid-increments 2016-01-02 and bidding-overview 2016-01-23 (old; for proxy examples only).

**Third-party sources (live 2026-11-04, local in `/var/tmp/sniper-research/rules/web/`)**
- Gixen blog 2026-09-23; Gixen FAQ (undated).
- Gixen forum threads 11808, 11811, 11837, 11843, 11883, 11890, 11879, 11854, 11857, 11826, 11865, 11863, 11877, 11842, 11832, 11405, 11633, 11601, 11623, 11536, 11468 (2025-01 to 2026-11).
- VAR 2025-03-20, 2025-07-07, 2025-07-13, 2025-07-17, 2024-02-22, 2026-08-03, 2026-09-03.
- stampboards t=110407 (2026-08-05 to 2026-09-13); collectors.com 1125373 (2026-09-02) and 1125884 (2026-09-28); maestronet 365970 (late Oct 2026); ozbargain 974470 (2026-09-10).
- cxnetwork 2026-01-28; niceggie 2026-09-10; ChannelX 2026-08-17.
- EZsniper FAQ 2023-11-24; AuctionStealer/eSnipe help; PerfectSniper help; Myibidder forum 2013-06-03.
- esniper man page (GitHub yhfudev/esniper, 2018); Biet-O-Matic BE docs (GitHub, 2021); JBidwatcher (GitHub, 2018).
- AOSP time detection; Android SystemClock reference.
- Search snippets (Yahoo, seen 2026-11-04): eBay Canada community 2024-01-04 (tie rule); EcommerceBytes 2025-07-22; Reddit threads b4l5cd and 199erxs; eBay AU community 2025-06-03; eBay community 2023-02-23; item-page Extended Bidding labels; StackExchange 2021-11-01.
