# Publishing Kestrel on Google Play

Kestrel ships as two builds of the same app (`app.kestrel`, one version line); `docs/ARCHITECTURE.md` section 10
explains how they differ. This page covers the Google Play one: what the repository already holds, the steps only
the Play Console account owner can take, and the answers for Play Console's forms.

## What the repository holds

- **The Play build.** `./gradlew bundlePlay` writes `app/build/outputs/bundle/play/app-play.aab`, signed with the
  upload key from `../kestrel-signing/upload.properties` (or the `KESTREL_UPLOAD_*` environment variables).
  `tools/play/check-bundle.sh <bundle> <versionName>` checks the package, the version, Play's permission rules and the
  signature.
- **The release workflow** (`.github/workflows/google-play.yml`). Publishing a GitHub release builds the bundle from
  its tag, checks it, keeps it with the run for 30 days and uploads it to a Play testing track: `internal` by default,
  or the track in the `PLAY_TRACK` repository variable (`alpha` is closed testing). It can also be run by hand on any
  `v*` tag (Actions › Google Play › Run workflow), with or without the upload. It never releases to production.
- **The secrets**, in the `google-play` environment (only runs from `main` or a `v*` tag get them):
  `KESTREL_UPLOAD_KEYSTORE_BASE64`, `KESTREL_UPLOAD_STORE_PASSWORD`, `KESTREL_UPLOAD_KEY_ALIAS` and
  `KESTREL_UPLOAD_KEY_PASSWORD` are set. `PLAY_SERVICE_ACCOUNT_JSON` comes from step O7. Until it exists, the workflow
  builds and checks the bundle and says it did not upload.
- **The store listing** in `fastlane/metadata/android/en-GB/`: name, short and full description, release notes per
  version code (`changelogs/<versionCode>.txt`), the 512 px icon, the feature graphic and six phone screenshots.
  `python3 tools/play/store_images.py fastlane/metadata/android/en-GB/images` redraws the images from the screenshot
  tests.
- **The privacy policy**: [PRIVACY.md](../../PRIVACY.md), linked from Settings › About. Its URL for Play Console is
  `https://github.com/ivallesp/kestrel/blob/main/PRIVACY.md`.

## Steps for the account owner

- **O1. Developer account.** At play.google.com/console/signup, with a personal Google account, choose *Personal*.
  Pay the US$25 one-time fee and upload a government ID when asked. Then install the Google Play Console app on an
  Android phone and verify the phone from it.
- **O2. Create the app.** Play Console › Create app: name *Kestrel*, default language *English (United Kingdom)*,
  *App*, *Free*. Accept the declarations.
- **O3. App signing: keep the GitHub build's key.** This is decided at the first upload and cannot be undone later.
  Choosing the release key lets the Play build install over a sideloaded Kestrel and keep its snipes; a key that
  Google generates cannot. In the first release (O4), choose to use your own app signing key, *Export and upload a key
  from Java keystore*. Download Google's PEPK tool and the encryption public key the page offers, then run
  `java -jar pepk.jar --keystore=kestrel-release.jks --alias=kestrel --output=kestrel-signing-key.zip --include-cert
  --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem` and upload the zip. Upload the upload key's
  certificate as well, exported with `keytool -exportcert -rfc -keystore kestrel-upload.jks -alias upload -file
  upload_certificate.pem`.
- **O4. First release, by hand.** Test and release › Internal testing › Create new release. Upload the Play bundle
  (`app-play.aab`, from `./gradlew bundlePlay` or the release workflow's run) and paste that version's
  `changelogs/<versionCode>.txt`. The API cannot make an app's first upload, so the workflow takes over only from the
  second release.
- **O5. Store listing.** Grow › Store presence › Main store listing: copy the texts and upload the images from
  `fastlane/metadata/android/en-GB/`. Category *Shopping*. Contact email: the address you want shown publicly.
  Website: `https://github.com/ivallesp/kestrel`.
- **O6. App content.** Fill in each form with the answers below.
- **O7. Upload access for the workflow.**
  1. In Google Cloud Console (the project in Play Console › Account details, or a new one), enable the *Google Play
     Android Developer API*.
  2. IAM & Admin › Service accounts › Create service account, named for example `kestrel-github`, with no roles.
  3. Open it › Keys › Add key › Create new key › JSON. The key file downloads.
  4. Play Console › Users and permissions › Invite new users: the service account's email, with app permissions for
     Kestrel: *View app information* and *Release apps to testing tracks*. Not production.
  5. GitHub › ivallesp/kestrel › Settings › Environments › google-play › Add environment secret
     `PLAY_SERVICE_ACCOUNT_JSON`, and paste the whole file. Delete the downloaded file afterwards. Never send it by
     chat or email.
- **O8. Closed test.** New personal accounts must run a closed test with at least 12 testers, opted in for 14 days in a
  row, before they can apply for production. Test and release › Closed testing › create the track, add the testers'
  emails (or a Google Group) and share the opt-in link. Set the repository variable `PLAY_TRACK` to `alpha` so that
  each GitHub release reaches them.
- **O9. Production.** After the 14 days: Dashboard › Apply for production. Once approved, promote a tested release to
  production in Play Console. Nothing does that automatically.

## Answers for Play Console's forms

- **Privacy policy:** `https://github.com/ivallesp/kestrel/blob/main/PRIVACY.md`.
- **Ads:** no ads.
- **App access:** bidding needs the user's own eBay account, signed in on eBay's own page inside the app; Kestrel has
  no account of its own. Google asks for working sign-in details for any part behind a login: either give the
  reviewers an eBay account made for them, or explain that every feature works with any eBay account.
- **Content rating** (IARC questionnaire, category *Utility, Productivity, Communication or Other*): no violence,
  sexual content, strong language, controlled substances or gambling; users cannot talk to each other in the app;
  no location shared; no digital purchases. Expect the lowest rating.
- **Target audience:** 18 and over (eBay requires its members to be 18 or older).
- **Data safety:** Kestrel collects and shares no user data. It has no server and sends nothing to its developer or to
  third parties; what the user does on eBay's pages goes to eBay, as in a browser. Check this declaration yourself
  before submitting it: it is the account owner's statement.
- **Financial features, health, news, government:** none.
- **Foreground service** (type *special use*). Description: "Kestrel runs a foreground service for the last two
  minutes before an auction that the user armed ends. It places the user's one bid at the second the user chose,
  then follows the auction to its result, and stops." User impact if deferred or interrupted: "The bid would be late
  or missing, and the user would lose the auction." Video: record the phone's screen while a dry run or a snipe
  reaches its final minutes (the countdown notification shows), upload it unlisted (YouTube or Drive) and give the
  link.
- **Exact alarms:** the Play build uses `SCHEDULE_EXACT_ALARM`, which the user allows, so there is no declaration.

## After setup

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, add
   `fastlane/metadata/android/en-GB/changelogs/<versionCode>.txt`, commit and tag `v<versionName>`.
2. Build the GitHub APK (`./gradlew assembleRelease`) and publish the GitHub release with it.
3. The workflow uploads the Play bundle to the testing track. Promote it in Play Console when it is ready.
