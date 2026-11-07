# Contributing to Kestrel

Thanks for helping. Kestrel bids with people's real money, so every change is judged first against the safety rules
below, then against the design in [docs/DESIGN.md](docs/DESIGN.md).

## The safety rules

A change that breaks one of these will not be merged, whatever else it improves. They are explained in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), section 1.

1. The user's maximum is a hard ceiling. Kestrel never bids above it, never raises it by itself, never buys and never
   pays.
2. One press per snipe. A press whose reply was lost is never repeated; the ended page decides what happened.
3. Dry runs never submit. The dry-run code path has no access to the submit step.
4. Kestrel drives eBay's normal pages in a normal WebView, at a person's pace. It never replays eBay's internal
   requests, never changes its user agent and never calls eBay from outside the WebView.
5. Captchas and verification steps go to the user. Kestrel never tries to solve or get around them.
6. Nothing leaves the phone: no server, no analytics, no ads and no third-party calls except the clock check.

## Set up

- JDK 21, the Android SDK (platform android-37.2, build-tools 37.0.0) and Node.js 22.13 or later.
- Point Gradle at the SDK with `ANDROID_HOME` or a `local.properties` file (`sdk.dir=...`). Git ignores
  `local.properties`.
- The JVM tests write about 200 MB of temporary files to `/var/tmp`.

## Build and check

Run all of these before you open a pull request:

```bash
./gradlew spotlessApply                    # format Kotlin and Gradle files (ktlint and the Compose rules)
./gradlew testDebugUnitTest                # JVM tests, including the screenshot tests
./gradlew lintDebug                        # Android lint: add no new warnings
(cd tools/jstest && npm ci && npm test)    # tests of the page helper (assets/js/kestrel.js) in jsdom
```

## Tests never touch eBay

- Every test runs offline, against archived pages in `app/src/test/resources/fixtures/` and
  `tools/jstest/fixtures/`. No test may make a network request.
- Do not point scripts or tests at live eBay. Automated traffic gets accounts and addresses blocked. Check a change to
  the bid flow with a dry run on a phone instead.
- New fixtures come from public archives (such as the Wayback Machine) or are written by hand and marked synthetic.
  Never commit a page captured while signed in: it holds names, addresses, item numbers and session tokens.

## Screens and screenshots

- The screens follow [docs/DESIGN.md](docs/DESIGN.md), including its copy rules. Update it when a screen or its words
  change.
- Screenshot tests (Roborazzi) compare each screen with the image in `app/src/test/screenshots/`. After a deliberate
  change to the UI, record the new images with `./gradlew recordRoborazziDebug`, look at every changed image, and
  commit them with the change.
- Check light and dark themes, large font sizes (130 % and up) and reduced motion. No text may be clipped.

## Commits and pull requests

- One logical change per commit, with its tests and docs.
- The subject is a short imperative sentence, such as "Show the group line on the hero card". The body explains why.
- Only release commits change `versionCode` and `versionName`.

## Licence

Kestrel is licensed under the GNU General Public License, version 3 only ([LICENSE](LICENSE)). By sending a
contribution, you agree that it is licensed under the same terms.

## Reporting problems

- Open an issue with what you did, what you expected and what happened.
- Kestrel can write a problem report (Settings › About › Send a problem report). Read it before you post it
  anywhere public: it lists your recent snipes with their titles, item numbers and timelines.
- Report security problems privately (Security › Report a vulnerability on GitHub), not in a public issue.
