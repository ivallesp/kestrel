# Kestrel

Kestrel bids on your eBay auctions in the last seconds, from your own phone, through your own eBay session.

- You share a listing from the eBay app (or paste a link), set your maximum and tap **Arm snipe**.
- About 10 minutes before the end, Kestrel does a **dry run**. It opens eBay's bid panel, types your maximum and closes it
  without bidding. Sign-in, captcha or phone-verification problems therefore show up while you can still fix them.
- At the end it submits your maximum once, 6 seconds before the end (you can choose 3 to 10 s). eBay's proxy bidding
  means you pay one step above the next bidder, never more than your maximum.
- It follows the auction to its real end, including eBay's **Extended bidding** (a late bid resets the clock to 60 s).
  It then tells you **Won** or **Lost**, with eBay's own words and a timeline of what happened.
- If the price passes your maximum before the end, you get a notification with **Raise max**.

Nothing runs on a server. Your eBay session lives only in the app's WebView on the phone.

## Install (Pixel 9a)

1. Take the signed `Kestrel-<version>.apk` and get it onto the phone (USB, Drive, or
   `adb install -r Kestrel-<version>.apk`).
2. Open it and allow installs from that source when Android asks.
3. Updates install over the old version and keep your snipes, because every build is signed with the same key
   (`../kestrel-signing/`, outside the repository and backed up separately; losing it means reinstalling from scratch).

## First run

1. **Sign in to eBay** on eBay's own page inside the app. Use your email or username and your eBay password.
   Two-step codes (SMS or an authenticator app) work. "Continue with Google", Facebook or Apple will not work inside an
   app, because those providers block embedded browsers. If you normally use Google, set an eBay password first.
2. **Allow notifications.** This is how you hear "Won", "Lost" and "Price passed your max".
3. On **Ready to snipe**, fix anything that is not green:
   - Battery: **Unrestricted**. This is the most important item; it keeps alarms exact in deep sleep.
   - Live Updates: optional. They give the status-bar countdown during the final minutes.

## Your first snipe: test it on something cheap

The bid panel of a signed-in ebay.co.uk account could not be captured while Kestrel was built: eBay blocks the build
machine, and nothing touched eBay from there. So the first real check of the bid flow happens on your phone.

1. Add a cheap auction that ends within a day or two.
2. Wait for the dry run (10 minutes before the end), or start one from the snipe's screen.
3. Look at the snipe's timeline. A passed dry run means sign-in, the bid panel and typing the amount all worked on your
   account. If it fails, the timeline holds eBay's page report: copy it and send it over, and the selectors can be fixed.

## What Kestrel will never do

- Bid above your maximum, change it by itself, buy anything or pay for anything.
- Press eBay's bid button more than once per snipe.
- Solve a captcha or a verification step. It stops, notifies you and opens eBay's page for you.
- Send your session or anything else off the phone. The only outside call is to time.google.com, to check the clock.
- Check listings more often than a person would: about every few hours far from the end, more often in the last hour.

## Limits

- The phone does the bidding. It must be on, online and have battery left at the end time. Ready to snipe and the
  notifications tell you when something is at risk.
- eBay changes its pages from time to time. The daily and pre-end dry runs catch that before an auction is lost.
- Extended bidding gives other bidders a chance to answer a late bid. Kestrel keeps following the auction to the end.
- Kestrel signs in on ebay.co.uk only. A link on another eBay site (ebay.com, ebay.de) can be watched but not sniped.
  Share foreign listings from the eBay UK app instead: they open on ebay.co.uk, where the snipe works.

## Build from source

```bash
export TMPDIR=/var/tmp
./gradlew assembleRelease            # app/build/outputs/apk/release/app-release.apk (signed if ../kestrel-signing exists)
./gradlew testDebugUnitTest          # JVM tests: parsing, engine, platform, UI screenshots
(cd tools/jstest && npm ci && npm test)  # page-helper tests against archived eBay pages
./gradlew recordRoborazziDebug       # re-record UI screenshots (app/src/test/screenshots)
```

Docs: `docs/ARCHITECTURE.md` (how bidding works and the safety rules), `docs/DESIGN.md` (the design system and every
screen), `docs/research/` (eBay's 2026 bid flow, Android timing, eBay rules, competitor teardown).
