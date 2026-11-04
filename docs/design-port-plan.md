Base project: copy /var/tmp/kestrel-design/c3-catalogue to a new project (for example /var/tmp/kestrel-design/final). Keep the namespace and applicationId app.kestrel and set rootProject.name to "kestrel". Work through P1 to P16 in order. After each step, re-record screenshots with `./gradlew --offline recordRoborazziDebug` and run `spotlessCheck`.

P1. Build files (c3 app/build.gradle.kts).
- Remove the ksp and room plugins.
- Remove these dependencies: room, datastore, work, webkit, glance, coil, okhttp, kotlinx-datetime, material3-adaptive-navigation3, lifecycle-viewmodel-navigation3.
- Remove the BOM `libs.androidx.compose.material3` line, since the explicit material3:1.5.0-beta02 line stays.
- Remove the test dependencies turbine, mockk, mockwebserver, work-testing and coroutines-test unless a test uses them.
- Keep compose ui/tooling, activity-compose, core-ktx, splashscreen, lifecycle-runtime-compose, navigation3-runtime/ui and kotlinx-serialization-json, with the serialization plugin (as in c2 app/build.gradle.kts).
- WebKit and WorkManager come back with the data layer, not in this UI pass.

P2. Theme, kept from c3 ui/theme/: Color.kt, StatusColors.kt, Shape.kt, Theme.kt, Type.kt. Changes:
- Color.kt:
  - set DarkCatalogueColors.plate to #B0A898;
  - add the photo-dim constant #E0E0E0 for dark scene photos.
- StatusColors.kt:
  - change the Won icon from check_circle_fill to trophy_fill;
  - change the Ended icon from do_not_disturb_on to flag;
  - add flag.xml and trophy_fill.xml from Material Symbols Rounded.
- Type.kt:
  - change the Sans cut to opsz 16 with ROND 0 and wdth 100;
  - copy the sans tracking values from c1 ui/theme/Type.kt (0.1, 0.15, 0.25, 0.1, 0.3, 0.4);
  - add stateWord, stateWordLarge, countdownUnit and countdownMedium;
  - keep tnum only on serif figure styles.
- Theme.kt: add `LocalReduceMotion`, read from Settings.Global.ANIMATOR_DURATION_SCALE.

P3. Formatters, kept from c3 ui/format/Formatters.kt and its test.
- In `countdown()`, emit U+202F between number and unit, following c1 ui/model/TimeText.kt `remaining()`.
- Add these formatters: `armSummary(max, bidAt)`, `absolute(now, instant)` (seconds only within 1 h), `zoneOverline(now)` ("SUNDAY 9 NOVEMBER · TIMES IN GMT"), and the receipt time with tenths.
- Merge c2 ui/components/Figure.kt `keepTogether()` into `forDisplay()`.
- Extend FormattersTest:
  - U+202F output;
  - the seconds rule;
  - BST in summer;
  - every fixture weekday;
  - the bidIncrement cases £64 → £2 and £2,150 → £50.

P4. Model, kept from c3 ui/model/Lot.kt: Money, LotPhoto, SnipeGroup, Lot, bidIncrement.
- Replace c3's five-case `SnipeStatus` with the sealed interface from spec 6.1:
  - the names and labels come from c2 ui/model/Models.kt `SnipeStatus`;
  - the payloads come from c3 (overBy, paid, soldFor, endedAt), plus sentAt, repliedAt, ebayText, newEnd and reason.
- Each case exposes `family` and `isFailure`.
- Add `advance(event)` for the ladder.
- Add StatusTransitionsTest covering every allowed and forbidden edge.

P5. Components, kept from c3 ui/components/Catalogue.kt and Money.kt.
- LotPlate: no multiply for dark scene photos; apply the 88 % dim.
- SmallCaps: add the debug `require` from rule SC2.
- StatusLabel and StatusChip: switch to sentence-case stateWord with 18 dp glyphs and the detail suffix.
- SectionHead: add `semantics { heading() }`.
- LedgerRow: add the total variant.
- Turn FigureStack into a `MoneyBlock` grid with shared first and last baselines and the optional sub-line.
- HeadroomRule: animate the fill with defaultEffectsSpec.
- Port from c2:
  - ui/components/Figure.kt (Figure, tabularText);
  - the `FlapDigits` logic from ui/components/Countdown.kt, as `RollingDigits`. Use MotionScheme fastSpatialSpec and fastEffectsSpec, clip each cell, and merge semantics. Drop the tiles and the split line.
- Port from c1:
  - ui/components/Timeline.kt (solid past rail, haloed now dot, dashed future), restyled with c3 tokens and a 96 dp time column;
  - `BottomFade` from ui/components/Layout.kt.

P6. Home, from c3 ui/screens/home/HomeScreen.kt, LotCards.kt, EmptyCatalogue.kt and HomeUiState.kt.
- Header: the overline with the zone, and the account button at 16 dp.
- HomeDock: 16 dp padding, the pill states from spec 3.8 with an inner Fix, Review or Sign in button, and "Add" at 56 dp.
- Add the offline banner and pull-to-refresh with PullToRefreshDefaults.LoadingIndicator.
- NextUpCard:
  - plate 128 dp with the StateChip overlay;
  - remove the hairline and "Next up";
  - add the at-most sub-line;
  - add the Attention, Bidding and sign-in variants.
- LotRow:
  - plate 72 dp, padding 16 dp;
  - sentence-case state line;
  - postage in the price meta;
  - independent flags.
- RaiseMaxButton:
  - menu items "Raise to £2,200.00, the next bid" and "Stop sniping";
  - Undo snackbars.
- GroupHeaderRow: switch to secondaryContainer.
- EmptyCatalogue: neutral badge colour and the new copy.
- HomeUiState: bucket rules and hero selection from spec S1, plus the "See all finished snipes" link.

P7. Add snipe, from c3 ui/screens/add/AddSnipeScreen.kt.
- Keep the header, facts card, ruled max field, odd-pence card, connected toggles (already Role.RadioButton) and group row.
- Replace the "If you win, at most" leader with the SumRow, ported from the sum row in c2 ui/screens/add/AddSnipeScreen.kt and restyled in serif.
- Add the pinned arm-summary bar from c1 ui/add/AddSnipeScreen.kt.
- Max field: hoist the TextFieldState, add semantics and the error state, wire "Use £90.37", and remove `remember(maxText)` rebuilding.
- Make the odd-pence card neutral.
- Add the extended-bidding hint.
- Add the S2 states: link entry, fetching with ContainedLoadingIndicator, fetch failed, not an auction, ended, already sniped, empty max, too low, signed out.

P8. Live, from c3 ui/screens/live/LiveLotScreen.kt.
- Keep the photo band, the sheet, the LinearWavyProgressIndicator FinalMinuteTrack and the BidLine.
- Change the track to an outline-colour 2 dp trackStroke.
- Add the times column, ported from c2 ui/screens/live/LiveScreen.kt and its ReadoutGrid in ui/components/Readout.kt. Its labels are driven by status (BIDS, then SENT).
- Add the checks evidence copy from c1 ui/live/LiveScreen.kt.
- Add the Edit max row with the deadline, the timeline with "Now", BottomFade and all status variants from spec S3.
- New file EditMaxSheet.kt for the edit, raise and set variants.

P9. Result, from c3 ui/screens/result/ResultScreen.kt.
- Keep ResultPlate, the Cookie12Sided seal (change its glyph to the trophy and add the one-time stamp), the paid card, the LedgerRow total, the pull-quote and the receipt.
- Change the copy to "Won for" and "counted".
- Change the Lost headline to displaySmall.
- Make the timing line sentence case.
- Separate the "Auction ended" and "Won confirmed" receipt rows.
- Add the Bid not sent, Bid failed, Ended early and Cancelled variants.

P10. Ready, from c3 ui/screens/ready/ReadyScreen.kt.
- Keep the sections with counts and the rows.
- Add the deadline sentence (copy from c1 ui/ready/ReadinessScreen.kt).
- Add the "Check again" top-bar action and the cadence footer from c2 ui/screens/ready/ReadyScreen.kt.
- Live Updates reads "Off · optional" in neutral.
- Add the not-applicable dry-run row.

P11. Sign-in, from c3 ui/screens/signin/SignInIntroScreen.kt.
- Keep the Arch and Cookie9Sided lock badge layout.
- Replace the hovering-bird drawable with the c1 launcher foreground on the launcher background gradient.
- Remove "One-time setup".
- Use the copy from spec S6, which includes c2's domain and password tip.
- Remove any open-in-new glyph.
- New file SignInBrowserScreen.kt: the host bar, the flat progress bar and the failure sheets. UI only.

P12. Brand assets from c1.
- Copy:
  - app/src/main/res/drawable/ic_launcher_background.xml;
  - ic_launcher_foreground.xml;
  - ic_launcher_monochrome.xml;
  - res/mipmap-anydpi-v26/ic_launcher.xml;
  - design/kestrel_icon.py.
- Derive res/drawable/ic_notification.xml from the monochrome head: 24 dp viewport, white fill, eye hole kept.
- Port c1 ui/brand/IconBoard.kt into the c3 test IconBoard for S7, re-themed with the c3 tokens.
- Delete c3 tools/icon/ and the old bird drawables.

P13. App shell.
- Port c2 ui/KestrelApp.kt (Navigation3 NavDisplay with @Serializable NavKeys).
- Keys: Home, Add(link?), Lot(id), Result(id), Ready, SignIn, SignInBrowser, Group(id), Settings, History.
- Add the transition specs from spec 2.10 and the shared plate through SharedTransitionLayout.
- MainActivity calls KestrelApp() instead of HomeScreen.

P14. New screens, built only from existing components: GroupScreen.kt, SettingsScreen.kt, HistoryScreen.kt and the group picker sheet.

P15. Fixture, kept from c3 ui/fake/FakeCatalogue.kt and FakeScreens.kt.
- Keep the 2025 instants.
- Apply Appendix A:
  - the S5 time is 18:13:52;
  - the won receipt adds the 18:21:46.2 confirmed row and the "nothing was sent" details;
  - the lost receipt adds 19:41:05.3;
  - add the S3 variant states (bid sent, in the lead, extended Walkman, captcha) and the S4 bid-not-sent state;
  - add the readiness pill states.
- Every label is derived from the Instants. There are no literal day strings.

P16. Tests.
- Keep c3 test DeviceChrome.kt (40 dp status bar, 24 dp gesture bar).
- Extend ScreenshotTests.kt to the Q30 render set. Use `mainClock.autoAdvance = false` and advanceTimeBy for the wave and the seal, and pass darkTheme explicitly.
- Keep FormattersTest. Add StatusTransitionsTest, CopyTest (curly quotes, no uppercase eBay, notification titles ≤ 28 characters, state words from the status model only) and a touch-target UI test.
- Do not port c1 PhoneFrame.kt or c2 DeviceFrame.kt; one harness is enough.