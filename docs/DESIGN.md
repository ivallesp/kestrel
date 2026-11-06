# Kestrel final design specification

Version 1.0, 4 Nov 2026.
- **Target:** Pixel 9a (411 x 923 dp, 6.3 inch OLED, gesture navigation), Android 17, Compose BOM 2026.09.00 with material3 1.5.0-beta02.
- **Base concept:** C3 Catalogue. Grafts come from C1 Pixel Native and C2 Instrument.
- **Copy:** British English throughout.
- **Fixture instants** are in 2025, because the brief's weekdays (Sun 9 Nov, Mon 10 Nov and so on) follow the 2025 calendar. No screen ever shows the year.

---

## 0. Decision

**D1. Base: C3 Catalogue.** It is the judges' consensus. Two of three judges chose it (clarity 8.5, truthfulness 8), and it has the highest total: 24, against 23 for C1 and 21 for C2. After opening all 35 renders I agree, for five reasons.
1. **Clearest money and time on every card.** One big number against the max, a headroom rule, postage on every lot, and relative plus absolute time on every row.
2. **Safest actions.** The dock never covers a price, and the only destructive action sits in a split-button menu with undo.
3. **Best result screen.** It fits the receipt on one screen and quotes eBay word for word.
4. **Colour keeps its meaning.** The paper-and-ink chrome leaves colour to the state families, so no state colour ever competes with a wallpaper tint.
5. **Most correct code on time and money.** It uses Instant with Europe/London, has an eBay UK increment table and has the only unit tests. It also already uses SegmentedListItem, SplitButtonLayout, connected ToggleButtons, LinearWavyProgressIndicator and MaterialShapes.

**D2. What changes in C3.**
- **Fixable faults.** The visual judge's objections are typography and density faults, and each one is fixed by a rule in section 7.
- **"Not Pixel-native".** This is answered with:
  - Material 3 Expressive components and MotionScheme.expressive();
  - Google Sans Flex for all UI text;
  - predictive back and edge-to-edge;
  - the themed launcher icon and Live Updates.
- **Declined: wallpaper-tinted chrome** (MF16). Any wallpaper accent would either take over the Armed slate (so Armed would stop meaning one thing) or add a hue competing with six fixed state families.

**D3. Grafts.**
- **From C1 Pixel Native:**
  - the launcher icon;
  - tabular figures only where numbers tick or align, with narrow no-break spaces in units;
  - heading semantics and one spoken sentence per countdown;
  - the S2 arm summary and the S5 deadline sentence;
  - the worst case with postage on the hero and S3;
  - dry-run evidence ("nothing was sent");
  - a separate "Won, confirmed" receipt row;
  - "Bid not sent" kept apart from "Bid failed";
  - real text-field semantics.
- **From C2 Instrument:**
  - the full status model;
  - the S2 sum row;
  - the S3 times column (Bids, Ends, Now);
  - S5 "Check again" and the re-check footer;
  - the chronometer-driven Live Update chip;
  - the Navigation3 shell;
  - the Figure helper and the per-digit roll;
  - the S6 domain name and password tip.

---

## 1. Direction and principles

### Direction
Kestrel is set out like an auction-house catalogue: each snipe is a lot with its photograph printed on warm paper, its title and money in Newsreader serif, and every other word in quiet Google Sans Flex. The page stays paper and ink, so any colour on screen is a state, and every state also carries an icon and a plain word. Each card answers three questions in the same order: what it is, what it costs against your max including postage, and exactly when it ends and when Kestrel bids. The app is calm by default and precise near the end: seconds appear only in the last hour, nothing blinks or turns red, and the two hero moments are the final minute and the result. Status words are only as strong as their evidence, so "Bid sent" is not "In the lead", and "In the lead" is not "Won".

### Principles
- **P1. Paper and ink; colour means state.** Chrome, actions and decoration are neutral. Red appears only when a bid has failed.
- **P2. One big number per lot.** Show the current price against your max, the headroom in words and as a rule, and postage on every card.
- **P3. Time is relative plus absolute.**
  - Show the zone near the end.
  - Show seconds only in the last hour (except the arm summary and receipts).
  - Use day names with dates. Never say "today" for another day, and never say "tomorrow" or "yesterday".
- **P4. Honest ladder.** Every label comes from a typed status. eBay's words are quoted, never paraphrased into a stronger claim.
- **P5. Calm motion.** Motion explains a change once. Nothing loops except the final-minute wave and a loading indicator.
- **P6. Nothing covers money or time.** No floating button, a docked bar, real insets, and a fade above the gesture handle.
- **P7. Safe actions.** Destructive actions live in menus and offer undo. The max stays editable until 3 s before the bid.
- **P8. Accessible by construction.**
  - Text at 4.5:1 or better in both themes.
  - Touch targets of 48 dp or more.
  - Colour never alone.
  - One spoken sentence per fact.

---

## 2. Tokens

### 2.1 Colour roles (fixed brand palette, no dynamic colour)
Code: `ui/theme/Color.kt` (`KestrelLightColors`, `KestrelDarkColors`, `CatalogueColors`). The screenshot palette and the phone palette are identical.

| Role | Light | Dark | Use |
|---|---|---|---|
| background, surface (page) | #F5F0E7 | #15130F | screen background |
| catalogue.card | #FFFCF7 | #1E1B17 | lot cards, segments, receipt cards |
| surfaceContainerLowest | #FFFCF7 | #100E0B | (no direct use) |
| surfaceContainerLow | #FAF6EF | #1C1915 | dock, bottom sheets |
| surfaceContainer | #F0EADF | #211E19 | menus |
| surfaceContainerHigh | #EAE3D7 | #2B2722 | odd-pence card, icon circles, offline banner |
| surfaceContainerHighest | #E4DCCE | #36322C | pressed segment |
| surfaceBright / surfaceDim | #FBF8F2 / #E2DACB | #3A362F / #15130F | (no direct use) |
| onSurface (ink) | #1E1B16 | #EDE6D9 | text, ticks, quote rule |
| onSurfaceVariant (ink soft) | #5C5549 | #BCB2A3 | secondary text |
| primary / onPrimary | #1E1B16 / #FBF7F0 | #EDE6D9 / #1E1B16 | filled buttons, checked toggles, switches, focus, LoadingIndicator |
| primaryContainer / onPrimaryContainer | #E7DFD2 / #1E1B16 | #3A352E / #EDE6D9 | (no direct use) |
| secondary | #5C5549 | #BCB2A3 | (no direct use) |
| secondaryContainer / onSecondaryContainer | #E9E1D3 / #1E1B16 | #37322B / #EDE6D9 | tonal buttons, unchecked toggles, group header, empty-state badge |
| tertiary / tertiaryContainer / onTertiaryContainer | #2D5275 / #DEE7F0 / #16324D | #A9C6E4 / #1F3348 / #D6E6F6 | mirrors Armed; screens never read it, they use the state palette |
| outline | #8C8375 | #8F8678 | outlined buttons, remaining-time track, dashed plate, future timeline dots |
| outlineVariant (= catalogue.rule) | #D9D0C1 | #3F3A33 | hairlines, headroom track, dock top rule |
| inverseSurface / inverseOnSurface / inversePrimary | #2F2B25 / #F4EFE6 / #D9CFBF | #EDE6D9 / #2F2B25 / #1E1B16 | snackbar |
| error / onError / errorContainer / onErrorContainer | #A3261E / #FFFFFF / #F9DEDA / #5C130E | #FFB4A8 / #5C130E / #6B1D16 / #FFDAD4 | failures only (identical to the Failed tone) |
| scrim | #000000 | #000000 | sheets at 32 %, S3 band top gradient at 45 % |
| catalogue.plate | #EEE7DA | #B0A898 | paper behind photographs (dark changed from C3's #C9C0B0) |

Measured contrast (WCAG):

| Pair | Light | Dark |
|---|---|---|
| ink on page | 15.1:1 | 15.0:1 |
| ink soft on page | 6.5:1 | 8.9:1 |
| ink soft on card | 7.2:1 | 8.2:1 |
| ink soft on surfaceContainerHigh | 5.8:1 | 7.1:1 |
| outline on page (graphics only, needs 3:1) | 3.3:1 | 5.2:1 |
| onPrimary on primary | 16.1:1 | 13.8:1 |

### 2.2 State families (fixed; never change with the theme seed or the wallpaper)
Code: `ui/theme/StatusColors.kt`.
- Icons are Material Symbols Rounded, vendored as vector XML.
- **Accent** is for icons and words on paper. **Container** is for soft fills. **On-container** is for text on that fill.

| Family | Members | Icon | Light accent / container / on-container | Dark accent / container / on-container | Example words |
|---|---|---|---|---|---|
| Watching | no max yet | visibility (outline) | #5E5446 / #ECE5D9 / #3A3329 | #D2C7B5 / #34302A / #EDE6D9 | "Watching · no max yet" |
| Armed | scheduled bid | schedule (outline) | #2D5275 / #DEE7F0 / #16324D | #A9C6E4 / #1F3348 / #D6E6F6 | "Armed · max £45.00" |
| Attention | over max, sign-in needed, verification needed, dry run failed, offline near a bid | warning (filled) | #8A5200 / #F7E6C6 / #4D2E00 | #F0BE6A / #45310F / #FCE3B8 | "£250.00 over your £1,900.00 max" |
| Bidding | final minute, bid sent, in the lead, outbid, no reply, extended, reading result | gavel (outline) | #5A3D92 / #E8E0F5 / #2F1C57 | #CDBAF3 / #362A52 / #EADFFF | "Final minute · bids in 30 s" |
| Won | won | trophy (filled) | #2D6A40 / #DAEEDD / #133A20 | #96D0A3 / #1C3925 / #CDEFD4 | "Won · Sat 8 Nov" |
| Ended | lost, ended early, cancelled | flag (outline) | #67625A / #E7E3DC / #3B3732 | #BDB7AD / #2E2B27 / #E7E2DA | "Lost · Fri 7 Nov" |
| Ended, failure tone (the only red) | bid not sent, bid failed | error (filled) | #A3261E / #F9DEDA / #5C130E | #FFB4A8 / #6B1D16 / #FFDAD4 | "Bid not sent" |

**Contrast (computed):**
- **Light:**
  - accents on card 5.91 to 8.15:1;
  - accents on page 5.33 to 7.35:1;
  - accents on their own container 4.73 to 6.52:1 (lowest: Ended);
  - on-container on container 9.23 to 11.52:1.
- **Dark:**
  - accents on card 8.61 to 10.27:1;
  - accents on page 9.31 to 11.11:1;
  - accents on container 6.81 to 7.85:1;
  - on-container on container 8.96 to 10.92:1.

**A passed readiness check** uses check_circle (filled) in the Won accent. The Won family glyph is the trophy, because a passed check is not a win.

### 2.3 Colour usage rules
- **U1. State colours only on state elements:** state label, state chip, state card, headroom fill, key timeline dots and notification accent. Never on decoration, links or neutral cards. The group header and the odd-pence card are neutral.
- **U2. Actions are ink.** Primary buttons, checked toggles, switches, focus lines and the cursor all use primary. There is no blue chrome.
- **U3. Red only for failures.** It is used for the failure tone and nothing else, never for "ending soon", validation or low headroom. Field validation uses the Attention family.
- **U4. Never colour alone.** Every coloured state has its glyph and its words. A greyscale screenshot must still read correctly.

### 2.4 Photo plates
Code: `LotPlate` in `ui/components/Catalogue.kt`.

**Processing:**
- **Cutout** (studio shots on white, PNG cut-outs):
  1. a colour matrix x1.04 on RGB, which lifts near-whites to white;
  2. then drawn in an offscreen layer (CompositingStrategy.Offscreen);
  3. then multiplied onto the plate colour (`drawRect(plate, BlendMode.Multiply)`).
  This applies in both themes. ContentScale.Fit with an inset.
- **Scene** (photos with their own background):
  - light theme: the same multiply;
  - dark theme: no multiply, drawn at 88 % brightness (ColorFilter multiply #E0E0E0).
  ContentScale.Crop with BiasAlignment(focusX, focusY), each from -1 to 1.

**Sizes:**

| Where | Size | Corners | Cutout inset |
|---|---|---|---|
| Rows | 72 x 72 dp | 10 dp | 8 dp |
| S2 header | 104 x 104 dp | 10 dp | 12 dp |
| Hero | 379 x 128 dp | 20 dp top, 4 dp bottom | (scene) |
| S3 band | full width x 232 dp including the status bar | none (the sheet covers the bottom 28 dp) | (scene) |
| S4 won | 379 x 176 dp | 20 dp | 18 dp |
| S4 lost | 379 x 136 dp | 20 dp | 18 dp |
| Sheets | 40 x 40 dp | 8 dp | (as above) |

**Fixture styles:** canon_ae1 is Scene (focusY -0.25) and polaroid is Scene (focusY -0.2). All others are Cutout.

### 2.5 Type
**Font files (OFL, licence in `licenses/`):**
- `res/font/newsreader.ttf`: variable, wght 200 to 800, opsz 6 to 72, lining figures with tnum.
- `res/font/google_sans_flex.ttf`: variable, wght 1 to 1000, wdth 25 to 151, opsz, ROND 0 to 100, slnt.

**Cuts.** Each cut is a FontFamily with one `Font` per weight and `FontVariation.Settings` pinned. Android does not apply opsz on its own.

| Cut | Font and axes | Weights |
|---|---|---|
| SerifDisplay | Newsreader, opsz 60 | 400, 500 |
| SerifHeadline | Newsreader, opsz 28 | 400, 500, 600 |
| SerifText | Newsreader, opsz 16 | 500, 600, 700 |
| Sans | Google Sans Flex, wdth 100, ROND 0, opsz 16 | 400, 500, 600, 700 |

**Material roles** (size / line height in sp, weight, tracking in sp):

| Role | Cut | Spec | Use |
|---|---|---|---|
| displayLarge | SerifDisplay | 72/76, 400, -1.5 | reserved |
| displayMedium | SerifDisplay | 52/56, 400, -0.8 | "You won" |
| displaySmall | SerifDisplay | 40/46, 400, -0.4 | "Outbid", "Bid not sent", "Ended early" |
| headlineLarge | SerifHeadline | 34/40, 400, -0.3 | screen titles |
| headlineMedium | SerifHeadline | 28/34, 400, -0.2 | empty-state titles |
| headlineSmall | SerifHeadline | 24/30, 500, -0.1 | S2 lot title, sheet titles |
| titleLarge | SerifText | 20/26, 500, 0 | eBay's quoted reply |
| titleMedium | Sans | 16/22, 600, 0.1 | S5 and settings row names, hero time line 1 |
| titleSmall | Sans | 14/20, 600, 0.1 | card sub-heads |
| bodyLarge | Sans | 16/24, 400, 0.1 | body |
| bodyMedium | Sans | 14/20, 400, 0.15 | secondary text, row facts |
| bodySmall | Sans | 12/16, 400, 0.25 | captions |
| labelLarge | Sans | 14/20, 600, 0.1 | buttons |
| labelMedium | Sans | 12/16, 600, 0.3 | small labels, scale labels |
| labelSmall | Sans | 11/16, 600, 0.4 | rare |

Emphasized variants add 100 weight: serif 400 to 500 and 500 to 600; sans 400 to 500 and 600 to 700.

**Catalogue styles** (`KestrelTheme.type`):

| Style | Cut | Spec | Use |
|---|---|---|---|
| countdown | SerifDisplay | 88/88, 400, -2.0, tnum, LineHeightStyle trim both | S3 seconds, final minute only |
| countdownUnit | SerifHeadline | 32/36, 400, -0.2 | "s left" |
| countdownMedium | SerifDisplay | 40/44, 400, -0.4, tnum | S3 above 60 s, for example "13 m 07 s" |
| priceHero | SerifDisplay | 40/44, 500, -0.4, tnum | hero current price, max field, S4 amount |
| priceLarge | SerifHeadline | 28/32, 500, -0.2, tnum | your max, S2 facts |
| priceRow | SerifText | 21/26, 600, 0, tnum | row prices; sum-row total uses weight 700 |
| lotTitle | SerifHeadline | 22/27, 500, -0.1, LineBreak.Heading | hero, S3 and S4 titles |
| lotTitleSmall | SerifText | 17/22, 500, 0, LineBreak.Heading | row titles |
| sectionHead | SerifText | 13/16, 600, +0.16 em, uppercase | "ENDING SOON" |
| smallCaps | Sans | 11/16, 600, +0.12 em, uppercase | data labels and overlines |
| stateWord | Sans | 14/20, 700, 0.1 | state words in rows, hero chip, dock, S3 chip |
| stateWordLarge | Sans | 16/22, 700, 0.1 | S4 status line, S5 header chip |

### 2.6 Figures and spacing characters (tabular-figure rules)
- **T1. Serif figure styles carry tnum in the style.** These are priceHero, priceLarge, priceRow, countdown and countdownMedium. They only ever get figure strings, never words.
- **T2. No Google Sans style carries tnum in its TextStyle.** Google Sans Flex's tnum widens the word space, which is the C3 fault.
- **T3. Figures-only sans strings go through `Figure`** (ported from C2 `ui/components/Figure.kt`). This covers clock times, ledger values, timeline times, counts and the S3 time column. `Figure` sets tnum on the runs of digits, colons, points and commas only, so the spaces between figures and their units keep their natural width, and replaces U+0020 with U+00A0 (adding U+200B after each one when the text may wrap).
- **T4. Sentences with a ticking or aligned figure** ("Ends in 13 m 07 s", "Kestrel bids £120.00 in 30 s") use an AnnotatedString:
  - put `SpanStyle(fontFeatureSettings = "tnum")` on the figure run only;
  - the span sets nothing else (no weight, no family);
  - a render test confirms that the variable axes survive in that span (see R4).
- **T5. Number-unit pairs use U+202F (narrow no-break space).** Examples: "13 m", "6 s", "0.8 s", "4 d 1 h". Groups are separated by a normal space, which becomes U+00A0 inside tnum runs. `Duration.countdown()` emits U+202F.
- **T6. Amounts never break.** "£2,150.00" has no spaces. Static prices inside running sentences keep proportional figures.
- **T7. Titles keep model numbers whole.** `String.forDisplay()` merges C3 and C2:
  - a no-break space before "f/";
  - U+2060 after "/" and after "-" between letters or digits.
  This keeps "50mm f/1.8", "MZ-N707" and "DMG-01" whole.
- **SC1. Where small caps go.** smallCaps is used only for data labels above figures ("CURRENT · 14 BIDS", "YOUR MAX", "POSTAGE", "WON FOR", "SOLD FOR", "TODAY · GMT"), overlines and section heads. 32 characters at most.
- **SC2. What small caps may not contain.** Never units, sentences, state words or the word eBay. `SmallCaps` asserts this in debug builds: `require(!Regex("\\d\\s?[smhd]\\b").containsMatchIn(text) && !text.contains("eBay"))`.
- **SC3. Quotes and apostrophes are curly everywhere** (’ “ ”). A unit test scans all strings for ' and ".

### 2.7 Shape
- **Scale:** extraSmall 4, small 8, medium 12, large 20, extraLarge 28 dp, full pill.
- **Cards and segmented groups:**
  - outer corners 20 dp, inner corners 4 dp, 2 dp gaps;
  - built with `ListItemDefaults.segmentedShapes(index, count)` and `ListItemDefaults.SegmentedGap`;
  - pressed segments use the built-in morph.
- **The hero** is the first segment of its bucket.
- **Plates:** see 2.4.
- **Sheets and the S3 sheet:** 28 dp top corners.
- **Dock readiness pill:** 20 dp.
- **Buttons and chips:** full pill with the Expressive press morph (`ButtonDefaults.shapes()`).
- **Connected toggles:** `ButtonGroupDefaults.connectedLeading/Middle/TrailingButtonShapes()`.
- **MaterialShapes** are used for decoration only:
  - Cookie12Sided: the WON seal;
  - Arch: the S6 frontispiece;
  - Cookie9Sided: the S6 lock badge and the S1-empty share badge.

### 2.8 Spacing
- **Grid:** 4 dp base. Steps 2, 4, 8, 12, 16, 20, 24, 32.
- **Columns:**
  - column A at 16 dp from the screen edge: screen titles, section heads, card edges, dock content, free paragraphs;
  - column B at 32 dp: everything inside cards.
  - End edges are symmetric (395 and 379 dp).
- **Card inset:** 16 dp. Row padding: 12 dp top and bottom, 16 dp at the start and end. Plate to text: 12 dp.
- **Section heads:** 12 dp above the first, 24 dp above the others, 8 dp below.
- **Gaps:** 2 dp between segments, 12 dp between blocks in a section.
- **Touch targets:**
  - 48 dp minimum, with `minimumInteractiveComponentSize`;
  - screen primary buttons are 56 dp;
  - inline buttons are 40 dp visual with 48 dp touch.
- **Bottom of screen:**
  - lists end 24 dp above the dock;
  - screens without a dock end with the navigation-bar inset plus 32 dp;
  - a 28 dp page-colour fade (`BottomFade`, from C1) sits above the gesture handle.
- **Screenshot harness** (C3 `DeviceChrome.kt`): status bar 40 dp, gesture bar 24 dp. The app itself always reads `WindowInsets`.

### 2.9 Elevation
- **Flat by default.** Hierarchy comes from tonal layers (page, then card) and 1 dp hairlines. Cards and the dock have no shadows.
- **Dock:** surfaceContainerLow with a 1 dp top rule.
- **Bottom sheets:** surfaceContainerLow with a scrim of #000000 at 32 %.
- **Menus:** the default DropdownMenu (surfaceContainer with its default shadow) plus a 1 dp edge in the outline colour, so a menu stands off the page and the cards even where no shadow is drawn (Q12).
- **Dialogs:** one style (`KestrelDialog`): surfaceContainerLow like the sheets, 24 dp from each side of the screen, and the page dimmed to the sheets' 32 %, not the platform's 60 %.
- **Snackbar:** the default.
- **S3 sheet over the photo:** no shadow; its 28 dp corners do the work.

### 2.10 Motion
`MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`. Every animation takes its spec from `MaterialTheme.motionScheme`.

| Element | Transition | Spec |
|---|---|---|
| Screen push and pop (NavDisplay) | in: slideInHorizontally from 1/8 width, plus fadeIn. Out: fadeOut, plus slideOutHorizontally to -1/12. Pop reverses. | slide defaultSpatialSpec; fade-in defaultEffectsSpec; fade-out fastEffectsSpec |
| Predictive back | scale 1 to 0.92, plus fade | defaultEffectsSpec |
| Lot to detail (S1 hero or row to S3 band; S1 Done row to S4 plate) | shared element on the plate, `sharedElement(key = "plate-$id")`, clipped to the plate shape | boundsTransform defaultSpatialSpec |
| S3 sheet | rises 48 dp and fades in, together with the plate | defaultSpatialSpec, defaultEffectsSpec |
| Countdown digits | only changed digits roll: new digit slides in from -50 % of the cell, old one slides to +50 %; cell clipped | slide fastSpatialSpec; fade fastEffectsSpec |
| Price change | AnimatedContent: new figure slides up 8 dp and fades in, old fades out; then "Up £8.00 from £88.00" fades in | slide defaultSpatialSpec; fades defaultEffectsSpec and fastEffectsSpec; caption slowEffectsSpec |
| Headroom fill | animateFloatAsState | defaultEffectsSpec (no overshoot) |
| State change (row, chip, card) | animateColorAsState on the container; Crossfade on the glyph | defaultEffectsSpec |
| List insert, remove, reorder | `Modifier.animateItem` | fade-in defaultEffectsSpec, placement defaultSpatialSpec, fade-out fastEffectsSpec |
| Final-minute wave | amplitude 0 to full at 60 s left; waveSpeed = wavelength / 2 per second; progress steps once a second; amplitude goes to 0 at Bid sent | amplitude slowEffectsSpec, flattening defaultEffectsSpec; progress `ProgressIndicatorDefaults.ProgressAnimationSpec` |
| WON seal | stamps once: scale 1.15 to 1, rotation -16° to -10°, alpha 0 to 1. Plays on the first open only (persist `sealShown`). | defaultSpatialSpec; alpha fastEffectsSpec |
| Win celebration (3.21) | one 7.4 s scene on the first open of a win, then the seal stamps; tap skips to the end; the Settings preview can play again | frame clock, scene drawn per frame; words fade in and rise 16 dp over 600 ms |
| Lost and failed results | content fades in, nothing moves | defaultEffectsSpec |
| Split-button chevron | rotates 180° while the menu is open | fastSpatialSpec |
| Buttons, toggles, segments | built-in shape morphs | defaults |
| Dock pill state | AnimatedContent with SizeTransform | fades defaultEffectsSpec and fastEffectsSpec; size defaultSpatialSpec |

**Never animated:**
- money counting up or down;
- colour pulses;
- blinking, flashing or shaking;
- a countdown changing colour;
- confetti;
- any looping animation other than the final-minute wave and LoadingIndicator;
- the theme switch;
- anything on Lost, Ended or Failed screens beyond one fade.

**Reduced motion.** `LocalReduceMotion` is true when `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`. When it is true:
- enter and exit transitions become None;
- shared elements cut instantly;
- the wave has amplitude 0 and speed 0 (a flat linear track);
- digits swap without rolling;
- the seal appears at -10° with no stamp;
- the win celebration opens on its last frame (the kestrel on the post, every word shown);
- LoadingIndicator is replaced by a static hourglass_top icon with the same words.

### 2.11 Iconography
- **Source.** Material Symbols Rounded, weight 400, 24 dp grid, vendored as vector XML from google/material-design-icons (`symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml` and `_fill1_24px`). Do not add material-icons-extended.
- **Filled glyphs** are used only for warning, error, trophy, check_circle and battery_alert.
- **Sizes:** 16 dp in flags, 18 dp in state labels and chips, 20 to 24 dp in rows, 26 dp in the seal.
- **Names:**
  - **States:** visibility, schedule, warning, gavel, trophy, flag, error, check_circle.
  - **Chrome:** arrow_back, close, more_vert, share, add, content_paste, expand_more, chevron_right, edit, arrow_upward, refresh, open_in_new (only for "Open on eBay" menu items), do_not_disturb_on (the "Stop sniping" menu item).
  - **Facts:** timer, link, storefront, payments, info, mail, trending_up, sync, cloud_off, hourglass_top.
  - **Checks:** account_circle, notifications, alarm_on, battery_alert, battery_full, update (Live Updates), task_alt (dry run).
  - **Sign-in:** lock, smartphone, alternate_email, sms, person.
- **Launcher icon.** C1's kestrel portrait (2.12).
- **Notification small icon.** `ic_notification`, the monochrome kestrel head with a plain round eye hole, 24 dp, white on transparent.

### 2.12 Launcher icon (taken from C1 unchanged)
- **Foreground:**
  - slate-blue crown #5A6EA6;
  - cream cheek #FFF6EC;
  - ink #252D4B moustache stripe and hooked beak;
  - marigold #EC9F36 eye ring and cere;
  - chestnut #C0582A back;
  - white eye highlight.
- **Background:** a vertical gradient from #FFE9D3 to #F7C09A.
- **Monochrome layer:** the head silhouette with a plain round eye hole, with no concentric rings so it never reads as a target.
- **Safe zone:** the art sits inside the 66 dp circle of the 108 dp canvas.
- **Never:** no crosshairs, targets or eBay colours.
- **Source:** `design/kestrel_icon.py` regenerates the layers.

---

## 3. Components

### 3.1 State label and state chip
**StateLabel** (rows, hero body, S4 and S5 lines). Content in order:
1. the family glyph at 18 dp in the accent;
2. 6 dp;
3. the state words in stateWord (14/20, 700) in the accent;
4. optionally " · " plus detail in bodyMedium, ink soft.

Rules:
- Height 20 dp, one line.
- Only the detail may ellipsise. If the line is too long, the detail wraps to a second line; the state words never truncate.
- The S4 status line uses stateWordLarge.
- Some states are a whole sentence in the accent, such as "£250.00 over your £1,900.00 max". These have no detail part.

**StateChip.** At most one per screen. Used on the hero plate, in the S3 sheet header and in the S5 header.
- Pill, minimum height 32 dp, padding 10 dp at the start and 12 dp at the end.
- Glyph 18 dp in the accent, then 6 dp, then stateWord in on-container, on the family container.
- On the hero plate it sits 12 dp in from the top-start corner.
- Semantics: "Status: Armed".

### 3.2 Money block
A grid with two columns across the container width.
1. **Row 1, labels.** Start label and end label in smallCaps, ink soft, on one shared first baseline. Examples: "CURRENT · 14 BIDS" and "YOUR MAX".
2. **2 dp gap.**
3. **Row 2, figures.** Start figure priceHero (40/44); end figure priceLarge (28/32), end-aligned. They share one last baseline.
4. **Row 3 (optional, end-aligned).** bodySmall, ink soft: "At most £126.50 with postage". It shows on the hero, S3 and the edit sheets.
5. **8 dp, then the HeadroomRule.** 12 dp tall: a 4 dp track in the rule colour, a fill in the family accent, and a 2 x 12 dp ink tick at the max.
6. **2 dp, then the caption row.** bodyMedium, ink soft. Start: "£32.00 below your max". End: "Postage £6.50". The end caption takes the width it needs and the start caption the rest; "your max" never breaks. From font scale 1.3 the two captions stack (the end one end-aligned).

Variants:
- **Over max:**
  - the fill covers the whole track in the Attention accent;
  - the tick sits at max divided by current;
  - the start caption reads "£250.00 over your max" in the Attention accent, weight 600.
- **Without a rule:**
  - S2 facts: "CURRENT · 7 BIDS" against "POSTAGE";
  - S4 lost: "SOLD FOR" against "YOUR MAX".

Height (hero): 78 dp grid, plus 8, plus 12 rule, plus 2, plus 20 caption = 120 dp.

Semantics: one merged node, for example "Current bid £88.00, 14 bids. Your max £120.00, at most £126.50 with postage. £32.00 below your max."

### 3.3 Time block (hero)
- **Content, in order:**
  1. timer 22 dp in ink;
  2. 12 dp;
  3. a column with two lines:
     - line 1 in titleMedium 600 with a tnum span on the figure run: "Ends in 13 m 07 s";
     - line 2 in bodyMedium, ink soft: "Bids at 18:21:34, 6 s before";
  4. an end-aligned column:
     - line 1 is a Figure in titleMedium 600: "18:21:40";
     - line 2 in bodyMedium, ink soft: "Today · GMT".
- **Height:** 42 dp: line 2 is always one line (maxLines 1, ellipsis), and the timer sits centred on line 1, never on the whole block.
- **Over one hour:** "Ends in 2 h 53 m", "Bids at 21:02, 6 s before", "21:02", "Today · GMT".
- **Another day:** "Ends in 1 d 2 h", "Bids Mon 10 Nov at 20:10", "20:10", "Mon 10 Nov · GMT".
- **Ticking:** once a second inside the hour (per-digit roll), once a minute otherwise, aligned to the wall clock.
- **Semantics:** "Ends in 13 minutes 7 seconds, at 18:21:40 GMT today. Kestrel bids at 18:21:34, 6 seconds before the end." It is not a live region.

### 3.4 Hero card ("next up")
- **Which lot:** the earliest-ending lot whose status family is Armed, Bidding or Attention. It always opens its bucket, so end-time order holds. If no lot qualifies, there is no hero.
- **Container:** card colour, first-segment shape. Tap opens S3.
- **Content, in order:**
  1. **plate** (379 x 128 dp) with the StateChip overlaid;
  2. **body**, with padding 16 dp on the sides, 12 dp at the top and 16 dp at the bottom:
     - title, lotTitle, up to 2 lines;
     - 8 dp;
     - money block (120 dp);
     - 12 dp;
     - time block (42 dp).
- **Height (fixture):** 128 + 12 + 27 + 8 + 120 + 12 + 42 + 16 = 365 dp.
- **No** "Next up" label and no hairline.
- **Variants:**
  - **Attention:**
    - the chip is the Attention one ("Over your max");
    - the rule shows the overflow;
    - after the time block: 12 dp, then the "Raise max" split button (40 dp).
  - **Bidding:**
    - the chip shows "Final minute", "Bid sent", "In the lead", "Outbid" or "Extended";
    - line 2 of the time block becomes one short bid line (never the 6.1 detail): "Kestrel bids £120.00 in 30 s"; "Bid sent · waiting for eBay" with a 20 dp LoadingIndicator in the Bidding accent; "In the lead · not won until 18:21:40"; "Outbid · another max is higher"; "Bid sent · no reply yet"; "Kestrel is still following" (Extended); "Reading eBay’s result…" with the LoadingIndicator.
    - the hero chip of an extended lot reads "Extended"; the time block already shows the new end.
    - after the bid, an overflow past the max is drawn in the Bidding accent: Attention means the user can still act.
  - **Sign-in needed:** an Attention chip "Sign-in needed"; line 2 reads "Sign in before 18:21:34 to keep this bid".
  - **Other attention cases** (over max, captcha, phone check, dry run failed, offline, can't reach) keep "Bids at 18:21:34, 6 s before": their chip and button say what is wrong. While offline the price label reads "LAST READ 18:02 · 14 BIDS".

### 3.5 Lot row (snipe row)
- **Container:** SegmentedListItem (C3 `LotRow`) in the card colour. Padding 12 dp top and bottom, 16 dp at the start and end, top-aligned.
- **Leading:** plate, 72 dp. Then 12 dp.
- **Text column, in order:**
  1. **StateLabel** (20 dp).
  2. 4 dp, then the **title** in lotTitleSmall: up to 2 lines, balanced, `forDisplay()`.
  3. 4 dp, then the **price line** (26 dp), a FlowRow:
     - the price in priceRow;
     - 8 dp;
     - the meta in bodyMedium, ink soft: "5 bids · £3.49 postage".
     If the meta does not fit, it wraps under the price.
  4. 2 dp, then the **time line** in bodyMedium, ink, with tnum spans: "Mon 10 Nov, 20:10 · in 1 d 2 h". Inside the hour: "Today 18:21:40 GMT · in 13 m 07 s".
  5. **Flags**, each in bodySmall, ink soft, with a 16 dp icon and 2 dp above:
     - link: "Portable audio: win 1 of 2", only when the row is not inside its group block;
     - timer: "Extended bidding: Kestrel's bid will reset the clock to 60 s.", up to 2 lines;
     - sync: "Couldn't check at 18:05 · retrying".
     Flags render independently of each other.
  6. **Action** (optional): 8 dp above, then a 40 dp button with 48 dp touch.
     - Over max: split button "Raise max".
     - Watching: outlined "Set a max".
     - Sign-in needed: Attention-tonal "Sign in".
     - Verification needed: Attention-tonal "Open eBay page".
     - Dry run failed: Attention-tonal "Fix".
- **Ended rows:**
  - price line: "Won for" in bodyMedium, then "£34.00" in priceRow, then " + £3.10 postage";
  - Lost rows instead: "Sold for £79.00";
  - second line in bodyMedium, ink soft: "£11.00 under your £45.00 max" or "Your max was £65.00";
  - no actions.
- **Heights (fixture):**
  - armed with a one-line title: 122 dp;
  - Omega (two-line title plus action): 192 dp.
- **Semantics:**
  - mergeDescendants, with click label "Open snipe";
  - row actions are exposed as custom accessibility actions ("Raise max", "Stop sniping");
  - description example: "Omega Speedmaster Pre-moon 1960s. Needs attention: £250.00 over your £1,900.00 max. Current bid £2,150.00, 31 bids, postage £12.95. Ends today at 21:02, in 2 hours 53 minutes."

### 3.6 Group header (in the list)
- **Position:** the first segment of a group block. It is used only when the group's lots sit next to each other in end-time order. Otherwise each row carries the group flag instead.
- **Container:** secondaryContainer. Padding 16 dp on the sides, 10 dp top and bottom.
- **Content:**
  - link 20 dp;
  - titleSmall 600: "Portable audio: win 1 of 2";
  - bodySmall: "Once one is won, Kestrel cancels the other." (or "the rest" for more than two lots);
  - chevron_right 20 dp.
- **Height:** 56 dp. Tap opens the Group screen.

### 3.7 Section head
- **Content:**
  1. sectionHead text in ink;
  2. 12 dp;
  3. a 1 dp rule in the rule colour, filling the space;
  4. 12 dp;
  5. the count in labelMedium, ink soft: "2 lots" or "1 lot".
- **Semantics:** `heading()`.
- **Edges:** 16 dp to 395 dp.

### 3.8 Dock and readiness pill (S1 only)
**Dock:**
- Surface in surfaceContainerLow with a 1 dp top rule.
- Row padding 16 dp at the sides and 8 dp top and bottom, plus the navigation-bar inset below.
- Height 72 dp plus the inset.
- It never floats, and content never scrolls under it.
- Content: the readiness pill (weight 1), 8 dp, then the Add button.

**Add button:**
- Filled ink, 56 dp tall: add 20 dp, 8 dp, then "Add" in labelLarge.
- contentDescription "Add snipe".
- From font scale 1.2 it is the add glyph (24 dp) alone in a 56 dp circle, still read as "Add snipe", so line 1 of the pill keeps its width.
- Hidden in the empty state.

**Readiness pill:**
- Surface, tap opens S5. 20 dp corners, minimum height 56 dp.
- Padding 12 dp at the start; 8 dp at the end when it holds a button, 12 dp when it does not.
- Content, in order:
  1. leading icon, 22 dp;
  2. 10 dp;
  3. two lines of text: line 1 in stateWord (never truncated), line 2 in bodySmall (ellipsised);
  4. an optional trailing filled ink button: 40 dp visual, 48 dp touch.

| Situation | Container, icon | Line 1 | Line 2 | Button |
|---|---|---|---|---|
| All good | surfaceContainerLow with a 1 dp rule border; check_circle in the Won accent | "Ready to snipe" | "All checks passed at 18:06" | none |
| One problem | Attention container; the check's own icon (battery_alert filled) in the accent | "Battery restricted" | "A bid could go out late" | "Fix" opens that exact system page |
| Several problems | Attention container; warning | "2 things need attention" | "Signed out · battery restricted" (most serious first: signed out, offline, then the battery and the rest) | none: a chevron, because the pill itself opens S5 |
| Signed out | Attention container; account_circle | "Signed out of eBay" | "Snipes are paused" | "Sign in" opens S6 |
| Offline with a bid within 1 h | Attention container; cloud_off | "Phone offline" | "The 18:21:34 bid can't go out" | none |
| Re-checking | unchanged | unchanged | "Checking…" | unchanged |
| First check | surfaceContainerLow with a rule border; hourglass_top in ink soft | "Checking this phone" | "This takes a few seconds" | none |
| Old pass (more than 30 min) | as first check, info glyph | "Ready to snipe" | "Last checked Sat 8 Nov at 21:40" (never "passed"), while Kestrel checks again | none |

Line 1 never wraps or truncates (maxLines 1); it fits beside Add at font scale 1.0 and 1.3.

### 3.9 Readiness row (S5)
- **Container:** SegmentedListItem in the card colour, 16 dp padding.
- **Leading:** a 40 dp circle with a 20 dp glyph.
  - Problems: Attention container with the glyph in the Attention accent.
  - Others: surfaceContainerHigh with the glyph in ink soft.
- **Headline:** the check name in titleMedium 600.
- **State line:**
  - problems: warning 16 dp plus the stateWord in the Attention accent, in Android's own words ("Optimised" for the battery, "Restricted" for background activity);
  - optional checks: "Off · optional" in labelLarge, ink soft, with no glyph.
- **Detail:** bodyMedium, ink soft, up to 3 lines, at the full width of the text column. A two-part detail ("0.04 s off Google time · synced 1 min ago") stays on one line when it fits, otherwise its parts take a line each without the dot.
- **While a check runs:** every row keeps its place and its state line reads "Checking…" in ink soft; before the first check the rows are the checks themselves, each "Checking…".
- **Trailing:**
  - problem: a filled ink "Fix" button (40 dp), at the end of the name's line, so the detail runs under it;
  - optional: a tonal "Turn on" button (40 dp), placed the same way;
  - passed: check_circle 24 dp in the Won accent, contentDescription "Passed";
  - not applicable: info 24 dp in ink soft.

### 3.10 Timeline row (S3 timeline, S4 receipt)
- **Columns:**
  - time, 96 dp wide: a Figure in bodyMedium, ink soft. Receipts use tenths ("18:21:33.9").
  - rail, 24 dp.
  - text, the remaining width.
- **Dots:**
  - past events: 8 dp filled in ink soft;
  - key past events take their family accent: Bid sent in Bidding, Won confirmed in Won, failures (eBay's refusal, Bid not sent) in the Failed tone;
  - now: 10 dp filled in the Bidding accent on a 20 dp halo in the Bidding container, with "Now" in labelMedium 700 above the time and no title of its own (the chip and the card above say the status);
  - future: a 10 dp ring with a 2 dp stroke in the outline colour.
- **Rail:** 2 dp wide, drawn behind each whole row, so it never breaks where a row grows.
  - Solid in the rule colour between past events.
  - Dashed (4 dp on, 4 dp off) in the outline colour from now up to the future events.
- **Text:**
  - title in bodyLarge, ink (future events in ink soft);
  - detail in bodyMedium, ink soft, up to 2 lines.
- **Spacing:** 8 dp above and below each row; minimum row height 40 dp.
- **Order:** newest at the top. In S3, future events sit above "Now".
- **Semantics:** one node per row, for example "18:21:33.9, bid of £120.00 sent, 6.1 seconds before the end".

### 3.11 Ledger row and sum row
**LedgerRow:**
- Content: the label in bodyLarge, then a dotted leader, then the value as a Figure in bodyLarge.
- The leader is made of 1 dp dots every 5 dp in ink soft at 55 %, sitting on the baseline.
- **Total variant:** 4 dp extra space above; label and value in titleMedium 700.

**SumRow** (S2 and the edit sheets):
- Three columns with weights 1, 0.9 and 1.3:
  - "YOUR MAX" over "£90.00" in priceRow;
  - "POSTAGE" over "£8.95" in priceRow;
  - "YOU PAY AT MOST" over "£98.95" in priceRow at weight 700. From font scale 1.3 the label reads "AT MOST", so every label stays on one line.
- The operator gutters ("+" and "=") are 24 dp wide, in titleMedium, ink soft, sitting on the value baseline.
- Labels share one baseline; values share one baseline. Figures keep 6 dp clear of the operators and step their size down first ("US$238.50").
- While the max is empty or too low, the max and total read "—".
- Below: 8 dp, then bodySmall, ink soft: "Usually less: eBay charges one step above the next bidder, never more than your max."

### 3.12 Final-minute scale (S3 only; the app's only wavy element)
- **Indicator:** `LinearWavyProgressIndicator(progress = elapsed / 60)` at full width, 24 dp tall.
  - Wave: 5 dp stroke in the Bidding accent, wavelength 28 dp.
  - Remaining-time track: 2 dp stroke in the outline colour, never the container.
  - The default stop indicator, in ink, marks the end.
- **Bid tick:** 3 x 24 dp in ink, at width x (60 - lead) / 60.
- **Labels (labelMedium):** start "Final minute"; "Bid", end-aligned 6 dp before the tick; end "End".
- **Bid sent:** the amplitude flattens to 0.
- **Extended:** the scale re-bases to the 60 s before the new end, and the start label reads "Extended".
- **Ended:** from the end on the start label reads "Ended".
- **Semantics:** progressBarRangeInfo, plus "Final minute: 36 seconds left. Kestrel bids in 30 seconds."

### 3.13 Bid card (S3)
- **Container:** a Surface in the Bidding container, 20 dp corners, 16 dp padding.
- **Content:** gavel 24 dp in the Bidding accent, 12 dp, then two lines:
  - line 1 in titleMedium 700, on-container;
  - line 2 in bodyMedium, on-container.

| Status | Glyph | Line 1 | Line 2 |
|---|---|---|---|
| Final minute | gavel | "Kestrel bids £120.00 in 30 s" | "At 18:21:34, 6 s before the end" |
| Bid sent | LoadingIndicator 24 dp | "Bid sent at 18:21:33.9" | "Waiting for eBay's reply" |
| In the lead | gavel | "In the lead · eBay replied in 0.8 s" | "“Your current bid puts you in the lead.” Not won until it ends." |
| Outbid | gavel | "Outbid · eBay replied in 0.7 s" | eBay's sentence alone (Kestrel's own words only when eBay gave none) |
| No reply (5 s after sending) | gavel | "No reply from eBay yet" | "Kestrel will read the result when the auction ends." |
| Extended | gavel | "Extended · now ends 19:56:42" | "Kestrel's bid at 19:55:42 reset the clock to 60 s. Kestrel is still following. You’re in the lead so far." (the last sentence only when eBay's reply said so; another bidder's reset reads as 6.1) |
| Reading result | LoadingIndicator | "Ended · reading the result" | "Checking the listing page" |
| Attention (captcha) | warning | "eBay wants a quick check" | "Solve it before 18:21:34 so Kestrel can bid. Kestrel never solves these for you." plus the "Open eBay page" button |
| Bid not sent | error | "Bid not sent" | "eBay signed you out at 18:21:20. Nothing was bid." plus the "Sign in" button |
| Needs you | warning | "Look at the listing on eBay" | the engine's explanation, plus "Open eBay page" |
| Phone offline | warning | "The 18:21:34 bid can’t go out" | "Kestrel bids once the phone reconnects." (the banner above says the phone is offline) |

The Attention row uses the Attention container. The "Bid not sent" row uses the Failed container.

### 3.14 Pull-quote (S4)
- **Content:** a 3 dp ink bar the full height of the quote, 16 dp, then a column:
  - the timing line in labelMedium, ink soft, sentence case: "Sent 6.1 s before the end · reply in 0.8 s";
  - 4 dp;
  - the quote in titleLarge (serif 20/26), ink, with curly quotes.
- **Semantics:** "eBay replied in 0.8 seconds: Your current bid puts you in the lead."

### 3.15 Won seal
- **Shape:** MaterialShapes.Cookie12Sided, 96 dp.
- **Fill:** the Won accent.
- **Content:** trophy 26 dp, then "WON" in sectionHead. Both are in the card colour in light theme and in #15130F in dark theme.
- **Placement:** rotated -10°, anchored to the plate's bottom-end corner, offset -14 dp horizontally and +12 dp vertically: it breaks the plate's edge like a stamp but ends above the status line, which keeps its whole width.

### 3.21 Win celebration
Asked for by the owner on 6 Nov: the one moment where motion is a celebration rather than an explanation (it still plays
once and never loops, so P5 holds everywhere else).
- **When:** the first open of a won result (the same moment the WON seal would stamp), over S3 or S4; the seal waits
  and stamps as the page comes into view. Settings > About > "Watch the win celebration" plays a preview with no lot,
  no amount and nothing bid.
- **Scene** (top 47 % of the screen, 38 % at text scale above 1.3; drawn, not a photo): a morning sky (dusk in the
  dark theme), sun, hills, a fence and a lot tag in the grass. The launcher icon's kestrel hovers with its head
  perfectly still while its body rides the wingbeats (the bird's own trick and Kestrel's: wait, then strike at the
  last moment), folds and stoops onto the tag, carries it up, turns and lands on the post and turns round on it.
- **The celebration** (added on 6 Nov when the owner asked for more): on the post the kestrel throws its wings up over
  its prize in a V (a raptor's mantle, with a little overshoot and a shiver) and calls "kee", "kee", "kee!" in serif
  italic rising from the beak; the sun breaks into 18 rays that turn a few degrees once and soften but stay; the
  meadow blooms in a wave from the post outwards (30 poppies, cornflowers, daisies and buttercups, each opening with
  an overshoot); nine kestrel feathers drift down and rest in the grass. Then it folds its wings, settles and bobs
  its head once. "You won" jumps once at the strike and once at the mantle. A firm haptic tick at the strike and a
  light one for each "kee". 7.4 s in all; nothing loops and the last frame keeps the flowers, rays and feathers.
- **Page** (rises over the scene's lower edge, 28 dp corners), in beats with the scene (more festive words asked for
  on 6 Nov): from 0.3 s, trophy and "YOU WON" in the Won accent (the plain fact) over the title (lotTitle, two lines);
  at the strike a cheer in displayMedium unfolds above the title with an overshoot ("What a catch!", "Clean
  strike!", "Snatched it!" or "Bagged it!", one per lot) and jumps again at the mantle; then "Won for £101.00" in
  priceRow and "£19.00 under your £120.00 max" in titleMedium bold, Won accent; at the mantle "Struck 6.1 s before
  the end. It's yours." in titleMedium; the coffee note at 6.3 s, once the celebration settles.
- **Sound** (asked for on 6 Nov): one 7.4 s track, res/raw/celebration.wav, synthesised by
  tools/sound/celebration_sound.py from sine waves and noise (no third-party audio), timed to the scene: wind and
  hover wingbeats, a fold, a rising whoosh, a thump and a bell at the strike, deeper wingbeats, a knock on the post,
  a five-bell arpeggio and three "kee" calls with the mantle, a pluck per flower and a warm chord that fades. It
  plays at media volume and ducks other audio; reduced motion, skipping and closing silence it. Settings >
  Appearance > "Sound for a win" turns it off. The preview shows PREVIEW and "This plays the first time you open a win. Nothing was bid."
- **Coffee note** (card colour, 16 dp): coffee glyph and "NO ADS · NO FEES", then "Kestrel hunts for you alone: no ads,
  no fees, nothing sold. If it caught you a good one, you can buy me a coffee.", a tonal "Buy me a coffee" and "Opens
  ko-fi.com/ivallesp". The button opens the owner's Ko-fi page (`CoffeeLink.url`, set on 6 Nov) in the browser; if
  nothing can open it, the line says so. No payment happens in Kestrel.
- **Actions** (docked, always in reach): "See the receipt" closes it; the preview has "Play again" and "Done". Back and
  the close button close it; a tap on the scene skips to the end.

### 3.16 Bottom sheet
- **Container:** ModalBottomSheet in surfaceContainerLow, 28 dp top corners.
- **Drag handle:** 32 x 4 dp in the outline colour.
- **Scrim:** 32 % black.
- **Padding:** 16 dp at the sides, 8 dp at the top, 16 dp at the bottom plus the IME and navigation insets.
- **Title:** headlineSmall.
- **Sheets with a text field:** `skipPartiallyExpanded = true`; the content scrolls and the primary button stays pinned above the keyboard (imePadding).

### 3.17 Buttons

| Kind | Colours | Height | Use |
|---|---|---|---|
| Primary (Button) | primary / onPrimary (ink on paper; paper on ink in dark) | 56 dp for screen primaries and the dock Add; 40 dp inline | Arm snipe, Continue to eBay, Paste eBay link, Save max, Fix |
| Tonal | secondaryContainer / onSecondaryContainer | 40 or 48 dp | Turn on, Watch without a max, Undo cancel |
| State tonal | family container / on-container | 40 dp | Raise max, Open eBay page, Sign in (on attention rows) |
| Outlined | 1 dp outline, ink text | 40 or 48 dp | Set a max, Edit max, Add a snipe to this group, Use (odd pence: a tonal button would be the same paper as its card) |
| Text | ink text | 48 dp | Not now, Cancel, Check again, View listing, See all finished snipes |
| Split | `SplitButtonLayout` with `TonalLeadingButton` and `TonalTrailingButton` in Attention tonal colours | 40 dp | Raise max (see the menu below) |
| Connected toggles | ButtonGroup with ToggleButton. Unchecked: secondaryContainer. Checked: primary with a check 18 dp. `Role.RadioButton` | 48 dp | lead time 3 s / 6 s / 10 s; theme |
| Icon button | 48 dp. Over photos: a circle of #000000 at 40 % with a white icon | 48 dp | back, close, overflow, share |

**Raise max menu, in order:**
1. "Raise to £2,200.00, the next bid";
2. a divider;
3. "Stop sniping" with the do_not_disturb_on icon.

**Rules:**
- Labels are sentence case in labelLarge, never all caps.
- Destructive actions are never buttons on a surface. They are menu items with an undo snackbar. The one exception is sign-out, which uses a confirm dialog.

### 3.18 Max field
- **Field:** `BasicTextField` with a `TextFieldState` hoisted into the screen state and keyed by the lot id, so it is never rebuilt on recomposition.
- **Keyboard:** `KeyboardType.Decimal`, `ImeAction.Done`.
- **Input filter:** digits and one ".", at most 2 decimals. "£" and "," are stripped. On blur the value formats to 2 decimals. The field shows thousands separators ("2,200.00", an output transformation); the state keeps the digits. An empty field keeps the amount's line height, so the underline never moves on the first keystroke.
- **Layout, in order:**
  1. the label "YOUR MAX BID" in smallCaps;
  2. 4 dp;
  3. a row:
     - "£" in priceHero, ink soft;
     - the amount in priceHero, ink;
     - a 2 dp ink cursor;
     - end-aligned on the last baseline: "At least £66.08" in bodyMedium, ink soft (hidden while the error line shows, which says the same with its reason).
  4. the underline.
- **Underline:** 2 dp ink when focused, 1 dp outline when not, 2 dp Attention accent when the value is too low.
- **Error line:** warning 16 dp plus bodyMedium in the Attention accent: "Too low: eBay needs at least £66.08".
- **Read-only** (the edits locked): the amount in ink soft, no underline, no "At least".
- **The minimum** is the engine's (`BidIncrements.minimumNextBid`): for a UK private seller it is the higher of the two readings of eBay's rule, the increment on the displayed price (£66.00) or the increment on the item price with its Buyer Protection on top (£60.87 + £2.00 + £3.21 = £66.08), so a max the UI accepts is never one eBay refuses.
- **Semantics:**
  - contentDescription "Your max bid";
  - stateDescription = the value;
  - `error("Too low: eBay needs at least £66.08")`.
- **"Use £90.37"** writes into the TextFieldState and puts the cursor at the end.

### 3.19 Empty states
**S1-empty:**
1. **Dashed empty plate**:
   - full width, 160 dp tall, 20 dp corners;
   - a 1.5 dp dashed outline (6 dp on, 4 dp off) in the outline colour;
   - centred: a Cookie9Sided 56 dp badge in secondaryContainer with share 24 dp; 12 dp below it, "YOUR FIRST LOT APPEARS HERE" in smallCaps.
2. 24 dp, then "Nothing on the list yet" in headlineMedium.
3. 8 dp, then bodyLarge, ink soft: "Kestrel bids for you in the last seconds of an eBay auction, up to the maximum you set."
4. 24 dp, then three steps, 20 dp apart. Each step has a numeral in SerifDisplay 400 40/44 in a 40 dp column, 16 dp, then a title in titleMedium and a detail in bodyMedium.
5. 28 dp, then the primary "Paste eBay link" (content_paste, 56 dp, full width).
6. 12 dp, then bodySmall centred: "Kestrel never bids above your max."

From font scale 1.3 the dashed plate (decoration) is left out, so "Paste eBay link" shows above the dock without scrolling.

**History empty:** "No finished snipes yet" in headlineSmall, then "When an auction ends, its result and receipt appear here."

**Group with no lots:** "No snipes in this group yet", "Add two or more snipes. Once one is won, Kestrel cancels the rest.", and the outlined button "Add a snipe to this group".

### 3.20 Snackbar, banners and loading
- **Snackbar:**
  - M3 default: inverseSurface, bodyMedium message, action in inversePrimary;
  - undo snackbars last 10 s;
  - it sits above the dock.
- **Offline banner** (S1 and S3):
  - surfaceContainerHigh, 20 dp corners, 16 dp padding;
  - cloud_off 20 dp;
  - title in titleSmall, detail in bodySmall;
  - full width at the 16 dp margins, directly under the header.
- **LoadingIndicator** (M3 Expressive) appears in exactly three places:
  - S2 while fetching the listing (ContainedLoadingIndicator, 48 dp);
  - waiting for eBay after a bid and reading the result (LoadingIndicator, 24 dp, in the Bidding accent);
  - S1 pull-to-refresh (`PullToRefreshDefaults.LoadingIndicator`).
- **Never** a skeleton, and never a spinner anywhere else.

---

## 4. Screens

### S1 Home
- **Now:** Sun 9 Nov 18:08:33 GMT.
- **Status bar:** dark icons on paper in light theme, light icons in dark theme.

**Layout, top to bottom (dp from the top of the screen):**
1. **Header** (70 dp: 8 top, 16 overline, 2, 40 title, 4 bottom).
   - Overline in smallCaps, ink soft: "SUNDAY 9 NOVEMBER · GMT". The zone is computed (BST in summer). One format on every day: the longest ("WEDNESDAY 24 SEPTEMBER · BST") is 28 characters.
   - Title "Snipes" in headlineLarge at column A.
   - Account button: a 48 dp circle in the card colour with a 1 dp rule border and person 24 dp. Its right edge is at 395 dp. contentDescription "Settings and eBay account, oakshelf".
2. **Section head** "ENDING SOON", "2 lots" (12 above, 20, 8 below).
3. **Ending soon block** (segmented):
   - hero Canon (365 dp);
   - 2 dp;
   - Omega row (192 dp).
4. **Section head** "THIS WEEK", "4 lots" (24 above).
5. **Group block:**
   - group header "Portable audio: win 1 of 2";
   - MiniDisc row;
   - Walkman row.
6. 12 dp, then the **block** of Polaroid and Game Boy.
7. **Section head** "DONE", "2 lots".
8. **Done block:** Game & Watch, then N64.
9. 8 dp, then a text button "See all finished snipes", which opens History.
10. 24 dp above the dock.

**Fold acceptance (light and dark, font scale 1.0).** At rest the screen shows the header, the hero, the whole Omega row, the "THIS WEEK" head and the whole group header above the dock (group header bottom at 817 dp, dock top at 827 dp).

**Content (fixture):**
- **Hero:**
  - chip "Armed";
  - title "Canon AE-1 Program + 50mm f/1.8";
  - "CURRENT · 14 BIDS" "£88.00" against "YOUR MAX" "£120.00", with "At most £126.50 with postage";
  - rule at 73 % in the Armed accent;
  - "£32.00 below your max" and "Postage £6.50";
  - "Ends in 13 m 07 s" with "Bids at 18:21:34, 6 s before", and "18:21:40" with "Today · GMT".
- **Omega:**
  - "£250.00 over your £1,900.00 max";
  - "Omega Speedmaster “Pre-moon” 1960s";
  - "£2,150.00", "31 bids · £12.95 postage";
  - "Today 21:02 · in 2 h 53 m";
  - Raise max split button.
- **MiniDisc:** "Armed · max £45.00", "Sony MiniDisc Walkman MZ-N707", "£24.00", "5 bids · £3.49 postage", "Mon 10 Nov, 20:10 · in 1 d 2 h".
- **Walkman:** "Armed · max £40.00", "Sony Walkman WM-FX421", "£18.50", "3 bids · £3.20 postage", "Tue 11 Nov, 19:55 · in 2 d 1 h", plus the flag "Extended bidding: Kestrel's bid will reset the clock to 60 s."
- **Polaroid:** "Watching · no max yet", "Polaroid SX-70 Land Camera", "£61.00", "9 bids · £7.50 postage", "Thu 13 Nov, 19:30 · in 4 d 1 h", plus "Set a max".
- **Game Boy:** "Armed · max £72.37", "Nintendo Game Boy DMG-01, boxed", "£46.00", "11 bids · £4.10 postage", "Sun 16 Nov, 21:43 · in 7 d 3 h".
- **Game & Watch:** "Won · Sat 8 Nov", "Game & Watch Donkey Kong II", "Won for £34.00 + £3.10 postage", "£11.00 under your £45.00 max".
- **N64:** "Lost · Fri 7 Nov", "Nintendo 64 console", "Sold for £79.00", "Your max was £65.00".
- **Dock:** pill "Battery restricted" / "A bid could go out late" with Fix, then "Add".

**Buckets (Europe/London):**
- **Ending soon:** ends within 24 h.
- **This week:** ends within the next 7 calendar days and is not in Ending soon.
- **Later:** beyond that.
- **Done:** ended in the last 30 days, newest first. Older results live only in History.
- **Order:** within a bucket, rows sort by end time.

**Behaviour:**
- **Taps:**
  - hero or an open row opens S3 (shared plate);
  - a Done row opens S4;
  - the group header opens the Group screen;
  - the pill opens S5;
  - Fix opens the exact system page, and Kestrel re-checks when the user returns;
  - the account button opens Settings.
- **Add:**
  - if the clipboard holds an eBay listing URL, S2 opens fetching it;
  - otherwise S2 opens in its link state;
  - the clipboard is read only on that tap.
- **Live updates:**
  - prices update in place (price-change motion);
  - countdowns tick per 3.3;
  - when a lot enters its final minute, the hero switches to its Bidding variant.
- **Pull-to-refresh:** refreshes all open lots now.
- **"Raise max":**
  - the leading half opens the raise sheet;
  - the menu's first item sets £2,200.00 at once with the snackbar "Max raised to £2,200.00" and Undo; the row reads "Armed · max £2,200.00" under it;
  - "Stop sniping" sets the lot to Watching with "Stopped sniping Omega Speedmaster" and Undo.

**States:**
- **Empty:** 3.19. The dock shows only the pill: "Ready to snipe" / "All checks passed at 18:06". There is no dry-run claim, because nothing is armed.
- **Loading:**
  - the list is local-first, so there is no full-screen loader;
  - a cold start shows the splash, then cached data;
  - refresh is silent except for pull-to-refresh.
- **Offline:**
  - a banner under the header: "Offline since 18:02" / "Prices are from 18:02. Kestrel can't bid until the phone reconnects.";
  - each price meta line gains " · as of 18:02";
  - if a bid is due within 1 h, the pill switches to the offline state.
- **Error:**
  - a failed check adds the sync flag "Couldn't check at 18:05 · retrying";
  - after three failures inside the final hour the lot turns Attention, "Can't reach the listing", with a Needs-you alert;
  - never red.
- **Signed out:**
  - the pill reads "Signed out of eBay" / "Snipes are paused" with "Sign in";
  - armed rows read "Sign-in needed · paused";
  - the hero shows its sign-in variant.
- **Attention:** the Omega row as specified. Other Attention states swap the action as in 3.5.
- **Finished rows** all name their day: "Won · Sat 8 Nov", "Bid not sent · Sun 9 Nov", "Ended early by the seller · Wed 12 Nov".
- **Extended bidding:**
  - the flag on the row;
  - during an extension the lot's state line reads "Extended · now ends 19:56:42" in the Bidding family.
- **Bidding in the list:** a non-hero lot in its final minute shows "Final minute · bids in 30 s" and a ticking time line.

### S2 Add snipe (full screen, not a sheet; it is a money decision and the keyboard is open)
- **Now:** 18:09:10.
- **Listing:** Sony Alpha A700 body.

**Layout:**
1. **Top bar** (64 dp):
   - close;
   - title "New snipe" in titleMedium 600;
   - end: text button "View listing", which opens the listing in Kestrel's own in-app browser (the same WebView used for sign-in).
2. **Lot header:**
   - plate 104 dp;
   - 12 dp;
   - a column:
     - title in headlineSmall, up to 2 lines: "Sony Alpha A700 body";
     - storefront 18 dp plus "camerashed_uk" in titleSmall 600;
     - "Private seller · 99.4% positive · 812 ratings" in bodySmall.
3. **Facts card** (16 dp padding):
   - money grid without a rule: "CURRENT · 7 BIDS" "£64.00" against "POSTAGE" "£8.95";
   - "Includes £3.13 Buyer Protection (private seller)." in bodySmall;
   - a hairline (12 dp above and below);
   - schedule 20 dp plus "Ends Thu 13 Nov, 20:04" in bodyLarge, and at the end "in 4 d 1 h" in titleSmall 700. No seconds, because the end is more than 1 h away.
4. **Max field** (3.18) showing 90.00, with "At least £66.08".
5. **Sum row** (3.11): £90.00 + £8.95 = £98.95, with the proxy sentence under it.
6. **Odd-pence card:**
   - surfaceContainerHigh, 20 dp corners;
   - "Make it £90.37" in titleSmall 700;
   - "Odd pence beat bidders who stop at £90.00." in bodySmall;
   - an outlined "Use" button (40 dp).
   The card is hidden once the max has non-zero pence, and when Settings "Suggest odd pence" is off. A listing in another currency says "odd cents".
7. **Bid timing:**
   - row: "Bid timing" in titleSmall 600, and at the end "6 s before the end" in bodyMedium, ink soft;
   - 8 dp, then connected toggles 3 s / 6 s / 10 s (48 dp);
   - 8 dp, then bodySmall: "6 s suits most connections. Choose 10 s on a weak signal."
   On an extended-bidding listing the hint reads: "This listing uses extended bidding: any bid in the last 60 s, Kestrel's too, resets the clock to 60 s."
8. **Group row:**
   - a card segment with link 24 dp;
   - "Add to a group" in titleMedium;
   - "Optional. Win one of several; the rest cancel." in bodyMedium;
   - chevron. It opens the group picker sheet.
   Once chosen: "Portable audio: win 1 of 2" with "Change".
9. **Pinned bottom bar** (page colour; top rule and a 28 dp page-colour fade above it while content is under it; a 1 dp rule under the top bar once the page scrolls):
   - the arm summary in bodyMedium with tnum spans: "Bids £90.00 on Thu 13 Nov at 20:04:12 GMT". This is the exact contract, so it is one of the two places that show seconds far from the end;
   - 8 dp;
   - the primary "Arm snipe" with schedule 20 dp, 56 dp, full width.

**Behaviour:**
- The summary recomputes on every max or lead-time change.
- "Arm snipe" is enabled when max is at least the minimum.
- After arming:
  - S2 closes;
  - the new row animates into S1;
  - snackbar: "Armed: bids Thu 13 Nov at 20:04:12".
- Closing discards silently, because nothing was armed.

**States:**
- **Link entry** (Add without a clipboard link):
  - a field "eBay listing link" with a tonal "Paste" button;
  - hint: "Or share a listing from the eBay app: tap Share, then Kestrel."
- **Fetching:**
  - the lot header area shows ContainedLoadingIndicator 48 dp with "Getting the listing from eBay…" and the host "ebay.co.uk/itm/…" in bodySmall (no scheme, item number or query);
  - nothing else shows until the listing arrives, except the disabled "Arm snipe": the sections need the listing's minimum, postage and end, and 3.20 allows no skeleton.
- **Fetch failed:**
  - "Couldn't open this listing." plus a reason ("eBay didn't respond", "That link isn't an eBay listing");
  - tonal "Try again", text "Paste a different link".
- **"Paste a different link"** sits in one place on every page that cannot go on: a text button on its own line under the message and its actions, its words on column A.
- **Not an auction:** "This is a Buy it now listing, so there's nothing to snipe." with "Open on eBay".
- **Already ended:** "This auction has already ended."
- **Already sniped:** "You already have a snipe on this listing." with "Open snipe".
- **Empty max:**
  - the primary becomes the tonal "Watch without a max";
  - the summary reads "No max yet: Kestrel will only watch the price."
- **Too low:**
  - the field error (the "At least" hint gives way to it);
  - "Arm snipe" disabled;
  - the sum row reads "—" for the max and the total;
  - the summary says what to type: "Set at least £66.08 to arm".
- **Signed out:**
  - the preview works;
  - "Arm snipe" first opens S6, with the line "Sign in to arm this snipe."

### S3 Lot detail (the final minute is hero moment one)
- **Now:** 18:21:04.
- **Lot:** Canon. 36 s left.
- **Status bar:** light icons over a scene photo and in dark theme; dark icons over a light-theme cutout plate, which has no top gradient (white icons would be about 2.6:1 on the paper).

**Layout:**
1. **Photo band**, 232 dp including the status bar:
   - scene crop with focusY -0.25;
   - dark theme at 88 %;
   - a top gradient of black from 45 % to 0 over 96 dp.
   - back and overflow buttons: 48 dp circles at 8 dp from the edges, top = status inset + 8 dp.
   - **overflow menu:** "Open on eBay"; "Test bidding now" while a dry run fits; the group items (below); "Stop sniping" (disabled after Bid sent, with the label "Bid already sent"); a divider, then "Delete" with the delete glyph while no bid window runs (its snackbar offers Undo).
   - **group items** (added 6 Nov), only while the lot may change group (not finished, bid window not open, the
     data layer's own rule): "Add to a group" (add glyph) for an armed lot outside any group; "Move to another
     group" (edit glyph) and "Remove from <group>" (close glyph) for a lot in one; a watching lot in a group can only
     leave it. Adding or moving opens the **group sheet**: one line on the rule ("Once one lot of a group is won,
     Kestrel cancels the rest. This lot keeps its max and bid time." or "Now in <group>…"), the other groups as
     segmented rows ("2 lots"; "Already won one" disabled; "Ends close to a lot in it" when within 3 minutes), then
     NEW GROUP with a name field and "Start the group". Every change keeps the max, lead time, schedule and
     timeline (the timeline says "Moved from the group "A" to "B".") and its snackbar offers Undo.
   - **once the sheet is scrolled up** a page-coloured bar takes over, with a 1 dp rule, the short title and the countdown ("Canon AE-1" over "36 s left").
2. **Sheet** from y = 204 dp: page colour, 28 dp top corners, 16 dp side padding, 16 dp top padding.
   1. StateChip "Final minute" (Bidding).
   2. 12 dp, then the title in lotTitle.
   3. 12 dp, then the **countdown row**:
      - start: "36" in countdown, 8 dp, "s left" in countdownUnit, baseline-aligned. A partial second counts as a whole one, so it never reads "0 s left" before the end; from the end on it reads "Ended" in countdownUnit, and once the bid failed "Ends 18:21:40";
      - end: the times column (from C2), as wide as its widest row and ending on the 395 dp edge:
        - "TODAY · GMT" in smallCaps;
        - three 24 dp rows, each a smallCaps label in a 44 dp column plus a Figure in titleMedium 600: "BIDS 18:21:34", "ENDS 18:21:40", "NOW 18:21:04".
      - "NOW" ticks.
      - After the bid is sent, "BIDS" becomes "SENT 18:21:33.9". Before then no label says "sent".
   4. 20 dp, then the final-minute scale (3.12).
   5. 20 dp, then the bid card (3.13).
   6. 20 dp, then the money block:
      - "CURRENT · 16 BIDS" "£96.00" against "YOUR MAX" "£120.00", with "At most £126.50 with postage";
      - the rule in the Bidding accent;
      - captions: trending_up 16 dp plus "Up £8.00 from £88.00", and "£24.00 below your max".
   7. 16 dp, then the **edit row**: outlined "Edit max" (edit icon, 48 dp), 12 dp, then "Until 18:21:31, 3 s before the bid" in bodyMedium, ink soft.
   8. 24 dp, then the **checks card** (card colour, 16 dp padding):
      - "CHECKS PASSED" in smallCaps;
      - three rows, each check_circle 20 dp in the Won accent plus bodyMedium:
        - "Signed in as oakshelf · checked 18:20:31";
        - "Dry run at 18:11:40 · nothing was sent";
        - "Clock 0.04 s off Google time".
   9. 24 dp, then "TIMELINE" and the timeline on the page:
      - **future:**
        - "18:21:40 Auction ends · Kestrel keeps following if it extends";
        - "18:21:34 Kestrel bids £120.00 · your max, 6 s before the end".
      - **now:** "Now 18:21:04".
      - **past:**
        - "18:20:51 Price rose to £96.00 · 16 bids";
        - "18:20:40 Final minute · checking every 2 s";
        - "18:11:40 Dry run passed · bid panel opened, £120.00 typed, nothing sent";
        - "09:14:02 Armed · £120.00 max, 6 s lead".
   10. The bottom fade.

**Behaviour:**
- **Edit max:**
  - opens the edit sheet, and is enabled until bid time minus 3 s;
  - after that the button is disabled and the text reads "Locked at 18:21:31: Kestrel bids £120.00 at 18:21:34".
- **Result:** when the result is known, S3 crossfades into S4 in place.
- **TalkBack:** a polite live region announces only:
  - "30 seconds to the bid";
  - "10 seconds to the bid";
  - "Bid sent";
  - eBay's reply;
  - the result.

**States (same screen, driven by status):**
- **Armed, more than 1 h away** (for example the Game Boy):
  - countdownMedium "7 d 3 h" plus "left" (the unit letters in headlineMedium, the figures in countdownMedium);
  - the times column headed "SUN 16 NOV · GMT" with "ENDS 21:43" alone and "Bids 6 s before" under it (without seconds "BIDS" and "ENDS" would read the same);
  - no scale;
  - no bid card. Instead a quiet line: "Dry run about 21:33, 10 min before the end."
- **Under 1 h:** countdownMedium "13 m 07 s"; the times include seconds and the NOW row.
- **Final minute:** as specified above.
- **Bid sent:** at 18:21:34.4. Countdown "6 s left" (5.6 s, rounded up); the wave is flat; bid card "Bid sent"; times show "SENT 18:21:33.9".
- **In the lead:** at 18:21:35.2. Chip "In the lead"; bid card with the quote; timeline row "18:21:34.7 eBay replied: in the lead".
- **Outbid:** chip "Outbid"; bid card "Outbid".
- **No reply:** bid card "No reply from eBay yet".
- **Extended** (the Walkman, Tue 11 Nov at 19:55:58):
  - chip "Extended" (the card's first line says "Extended · now ends 19:56:42");
  - countdown "44 s left", re-based;
  - timeline row "19:55:42 Kestrel's bid reset the clock to 60 s · now ends 19:56:42";
  - an explanation under the bid card in bodySmall: "This listing uses extended bidding. Any bid in the last 60 s, including Kestrel's, resets the clock to 60 s, so others get a minute to respond. Your max still caps what you pay."
- **Reading result:** bid card "Ended · reading the result".
- **Attention: captcha** (at 18:12:05):
  - chip "eBay wants a quick check" (Attention);
  - the captcha bid card with "Open eBay page", which opens the WebView at the challenge;
  - countdownMedium "9 m 35 s".
- **Attention: sign-in or dry run:** the same pattern with its own copy (section 6).
- **Offline:** the offline banner under the chip says it once ("Offline since 18:02" / "Prices are from 18:02."); the chip reads "Bid at risk" and the Attention card "The 18:21:34 bid can’t go out" / "Kestrel bids once the phone reconnects."; the price label reads "LAST READ 18:02 · 16 BIDS".
- **Bid not sent:** the red bid card, chip "Bid not sent" (Failed tone), and "Sign in"; the countdown gives way to "Ends 18:21:40".
- **Attention: dry run met a sign-in page:** the action reads "Sign in" (not "Fix") and opens S6.

### Edit-max sheet (also used for Raise max and Set a max)
- **Fixture:** Canon at 18:21:12.

**Content, in order:**
1. **Title** "Edit your max" in headlineSmall.
2. **Lot line:**
   - plate 40 dp;
   - "Canon AE-1 Program + 50mm f/1.8" in titleSmall, one line;
   - "£96.00 now · ends 18:21:40 GMT" in bodySmall; the zone is always there ("ends Thu 13 Nov, 19:30 GMT").
3. 16 dp, then the **max field** prefilled with 120.00, with "At least £98.00".
4. 12 dp, then the **sum row** £120.00 + £6.50 = £126.50.
5. 12 dp, then the **deadline line**: timer 16 dp plus bodyMedium "Change it until 18:21:31, 3 s before the bid."
6. 16 dp, then the primary **"Save max"** (56 dp, full width), then a text button "Cancel".

**Behaviour:**
- **Save:**
  - closes the sheet;
  - the money block animates;
  - snackbar "Max changed to £130.00" with Undo (available until the deadline).
- **When the deadline passes during editing:**
  - the field becomes read-only;
  - the Attention warning line "The bid window is open, so this can’t change now. Kestrel bids £120.00 at 18:21:34." appears, the same line and style as a save the data layer refused;
  - Save is disabled and Cancel becomes "Close".

**Variants:**
- **Raise max** (Omega at 18:08:40):
  - title "Raise your max";
  - prefilled £2,200.00 and selected;
  - "At least £2,200.00";
  - sum row £2,200.00 + £12.95 = £2,212.95;
  - arm line "Bids at 21:02:09 GMT", with the timer glyph like every time-to-bid line (schedule stays on "Arm snipe" only);
  - primary "Raise max".
- **Set a max** (Polaroid):
  - title "Set your max";
  - empty field, "At least £63.00";
  - the odd-pence card;
  - lead-time toggles;
  - primary "Arm snipe".

### S4 Result (hero moment two)
**Won** (now 18:22:05; Canon):
1. **Top bar** (56 dp): close; at the end, share and overflow ("Open on eBay").
   - Share sends a text receipt: title, "Won for £101.00", total, end time, eBay's reply.
2. **Plate** 176 dp, with the WON seal stamping once.
3. 16 dp, then the **status line** in stateWordLarge: trophy plus "Won", then " · ended today, 18:21:40 GMT" ("today" on the day itself, the date on any other; seconds only within the hour of now). The seal ends above it, so it runs the whole width on one line.
4. 4 dp, then **"You won"** in displayMedium.
5. 2 dp, then the **title** in lotTitle.
6. 16 dp, then the **paid card** (card colour, 16 dp padding):
   - start: "WON FOR" in smallCaps over "£101.00" in priceHero;
   - end, with its last baseline on the figure's baseline: "£19.00 under" in titleMedium 700, Won accent, over "your £120.00 max" in bodyMedium;
   - 12 dp, a hairline, 12 dp;
   - ledger: "Final price £101.00", "Postage £6.50", Total "£107.50" (total style);
   - 12 dp, then info 18 dp plus bodySmall: "eBay takes payment automatically from your saved method. If it can't, you have 4 days to pay."
7. 20 dp, then the **pull-quote**: "Sent 6.1 s before the end · reply in 0.8 s", “Your current bid puts you in the lead.”
8. 24 dp, then **"RECEIPT"** and the timeline, newest first, in tenths:
   - "18:21:46.2 Won at £101.00 · confirmed on the listing page" (Won dot);
   - "18:21:40.0 Auction ended · on time, no extension";
   - "18:21:34.7 eBay replied in 0.8 s · in the lead";
   - "18:21:33.9 Bid of £120.00 sent · 6.1 s before the end" (Bidding dot);
   - "18:20:40.0 Final minute · checking every 2 s";
   - "18:11:40.2 Dry run passed · nothing was sent";
   - "09:14:02.0 Armed with a £120.00 max, 6 s lead".

**Won fold acceptance.** The RECEIPT head and its first row are visible without scrolling.

**Lost** (now Fri 7 Nov 19:42:10; N64):
1. **Plate** 136 dp, with no seal.
2. **Status line:** flag plus "Lost", then " · ended Fri 7 Nov, 19:41:00 GMT" (Ended family).
3. **"Outbid"** in displaySmall, one step below "You won".
4. **The title.**
5. **Sold card:**
   - "SOLD FOR" "£79.00" against "YOUR MAX" "£65.00", labels on one baseline;
   - a hairline;
   - info 20 dp plus "Outbid by someone with a higher max" in titleMedium 600;
   - in bodyMedium: "Your £65.00 bid reached eBay 6.0 s before the end and counted. Their max was higher, so eBay's proxy bidding kept them ahead. You pay nothing."
6. **Second Chance Offer card** (surfaceContainerHigh):
   - mail 20 dp plus "If the winner doesn't pay" in titleMedium;
   - "The seller may offer it to you at your max as a Second Chance Offer. Accept it only in eBay Messages; offers by email are often scams."
7. **Pull-quote:** "Sent 6.0 s before the end · reply in 0.9 s", “You’ve been outbid. Another bidder’s maximum is higher.”
8. **Receipt:**
   - "19:41:05.3 Lost · sold for £79.00, confirmed on the listing page";
   - "19:41:00.0 Auction ended · on time, no extension";
   - "19:40:54.9 eBay replied in 0.9 s · outbid";
   - "19:40:54.0 Bid of £65.00 sent · 6.0 s before the end";
   - "19:31:02.4 Dry run passed · nothing was sent";
   - "Wed 5 Nov 21:12:44 Armed with a £65.00 max, 6 s lead".

**Other variants:**
- **Bid not sent** (red; Canon at 18:22:05):
  - plate 136 dp;
  - status line: error plus "Sun 9 Nov, 18:21:34 GMT" ("Today, 18:21:34 GMT" on the day), the glyph in the Failed tone. The headline below names the state, so the line gives only the moment; the same holds for Bid failed, Ended early, Cancelled and Cancelled by group;
  - "Bid not sent" in displaySmall;
  - a Failed-container card: "eBay signed you out at 18:21:20, 14 s before the bid. Nothing was bid." with the primary "Sign in";
  - receipt:
    - "18:21:40.0 Auction ended · sold for £104.00 to another bidder";
    - "18:21:34.0 Bid not sent · signed out";
    - "18:21:20.3 eBay ended the session";
    - "18:11:40.2 Dry run passed".
- **Bid failed** (red): like Bid not sent, but the card quotes eBay: "eBay didn't accept the bid: “…”. Nothing was bid." When eBay's words ask to try again, "The auction has ended, so there is nothing to retry." follows. The refusal's receipt dot is in the Failed tone.
- **Ended early** (Ended family):
  - "Ended early";
  - "The seller ended the listing before its end time. Nothing was bid.";
  - the receipt ends with "Seller ended the listing · Before its end time" at the moment it ended.
- **Cancelled by group** (Ended family):
  - "Cancelled";
  - "Kestrel cancelled this because you won Sony MiniDisc Walkman MZ-N707 in Portable audio.";
  - a tonal "Undo cancel" (40 dp) while the auction runs. It arms the listing again on its own, with the old max and lead, outside the group (whose rule would cancel it again), and opens its S3 with "Sniping Sony Walkman again, on its own". Receipt times are in tenths on the day of the cancel.
- **Result not known** (Ended tone): "Kestrel sent your £120.00 bid 6.1 s before the end, but couldn’t read the result on eBay’s listing page yet. It keeps checking the page, and you can look on eBay too." with "Open on eBay"; eBay's reply is a receipt row, not a pull-quote.
- **The 136 dp plate** lifts a scene's focus (focusY - 0.35), so the top of the item stays in the crop.

### S5 This phone
- **Now:** 18:13:52, after the 18:11:40 dry run.

**Layout:**
1. **Top bar:** back; at the end, a text button "Check again" with refresh. While checking it reads "Checking…" and is disabled. There is no spinner.
2. **Overline** in smallCaps: "CHECKED TODAY 18:13" ("CHECKING NOW" before the first check).
3. **"This phone"** in headlineLarge: a neutral title, so the page never says ready while the chip below says otherwise.
4. 8 dp, then the **StateChip** (Attention) "1 thing needs attention".
5. 12 dp, then bodyLarge: "Fix it before 18:21:34, when Kestrel bids on your Canon AE-1 Program."
6. 4 dp, then bodyMedium, ink soft: "Kestrel bids from this phone, so it has to be on, online and free to act at the exact second."
7. **"NEEDS ATTENTION", 1:** Battery:
   - "Optimised" (Android's word for this setting);
   - "Android may pause Kestrel when the screen is off, so a bid could go out late.";
   - Fix, which opens the app's battery page.
8. **"RECOMMENDED", 1:** Live Updates:
   - "Off · optional";
   - "Shows the countdown in the status bar and on the lock screen.";
   - "Turn on", which opens the Live Updates page.
9. **"ALL GOOD", 8:** every check the platform runs, so a passing one is evidence too:
   - eBay account: "Signed in as oakshelf · checked 2 min ago";
   - Notifications: "On";
   - Exact timing: "Allowed, so bids leave on the second";
   - Background activity: "Not restricted";
   - Pause if unused: "Off, so Kestrel stays armed even if you don’t open it";
   - Phone clock: "0.04 s off Google time · synced 1 min ago";
   - Internet: "Connected (Wi-Fi)";
   - Dry run: "Passed today at 18:11 on the Canon AE-1 · nothing was sent".
10. **Footer** in bodySmall, ink soft: "Kestrel runs these checks whenever you open this page, and does a dry run about 10 minutes before each bid." (The platform has no timed re-check; S1 re-checks when it is shown and every 15 minutes while it is open.)

**Behaviour:**
- When the user returns from a system page, Kestrel re-checks.
- A fixed row moves into "ALL GOOD" (animateItem) and the counts update.

**States:**
- **All good:** chip "Ready to snipe" (Won glyph check_circle); the deadline line is hidden.
- **Checking:** "Check again" reads "Checking…"; every row stays, with "Checking…" as its state line (3.9).
- **Nothing armed:**
  - the Dry run row reads "Runs about 10 minutes before each bid" with the info glyph;
  - it is not counted as a pass.
- **Notifications:** "Alerts can sound in Do Not Disturb" is added only when Do Not Disturb access is granted.

### S6 Sign-in intro
- **Now:** 18:05.

**Layout:**
1. **Close.**
2. **Frontispiece**, centred:
   - a MaterialShapes.Arch 144 x 168 dp filled with the launcher background gradient;
   - C1's kestrel portrait (the launcher foreground) fills the arch width;
   - a Cookie9Sided 56 dp ink badge with a white lock 24 dp, overlapping the arch's bottom-end corner by 12 dp.
3. 24 dp, then **"Sign in to eBay"** in headlineLarge.
4. 8 dp, then bodyLarge, ink soft: "Kestrel bids with your own eBay account, from this phone. eBay may ask you to sign in again now and then."
5. 20 dp, then **four promises**. Each is a 44 dp circle in surfaceContainerHigh with a 24 dp glyph, 16 dp, then a title in titleMedium 600 and a detail in bodyMedium, with 16 dp between promises.
   1. storefront: "You sign in on eBay's own page" / "The next screen is signin.ebay.co.uk, shown inside Kestrel. Your password goes to eBay, not to Kestrel."
   2. smartphone: "Your session stays on this phone" / "It is kept here and never sent anywhere else. Sign out any time in Settings."
   3. alternate_email: "Use your email or username and password" / "Google and Facebook buttons don't work inside apps. No eBay password yet? Set one on ebay.co.uk first."
   4. sms: "2-step codes are fine" / "Enter the code from your texts or authenticator app as usual."
6. **Pinned bottom:**
   - primary "Continue to eBay" (56 dp, no open-in-new glyph);
   - text button "Not now";
   - bodySmall centred: "Kestrel isn't made by or affiliated with eBay."

**Sign-in page (in-app browser):**
- **Top bar:**
  - close;
  - lock 16 dp plus "signin.ebay.co.uk" in titleSmall, read-only, always showing the current host;
  - a flat (non-wavy) LinearProgressIndicator under the bar while pages load.
- **Success:** a sheet "Signed in as oakshelf", then back to where the user came from.
- **Failures:**
  - **Google or Facebook buttons:** if the page navigates to accounts.google.com or facebook.com, Kestrel stops and shows "Google sign-in doesn't work inside apps. Use your eBay email or username and password." with "Back to eBay's page".
  - **Closed before finishing:** "Sign-in didn't finish."
  - **Network failure:** "Couldn't reach eBay. Check the connection and try again."

### S7 Launcher icon board
- **Base:** C1's IconBoard, re-themed on paper.

**Content, in order:**
1. **Overline** "LAUNCHER ICON · ADAPTIVE".
2. **"Kestrel"** in headlineLarge.
3. **One line:** "A kestrel watching, not a target."
4. **The adaptive icon** at 200 dp in a circle mask.
5. **Mask row:** circle, squircle, rounded square and cookie at 64 dp, with labels.
6. **Layer row:** background, foreground and monochrome, each 108 dp, with the 66 dp safe-zone circle dashed in the outline colour.
7. **Themed icons:** a light card and a dark card, each showing the standard icon and the themed one.
8. **Small sizes:** 48, 36 and 24 dp, plus a status-bar strip showing ic_notification and a Live Update chip "0:36" on the Bidding accent.

### Group screen
- **Now:** 18:08:40.

**Layout:**
1. **Top bar:**
   - back;
   - title "Portable audio" in titleLarge;
   - overflow, always there: "Rename" (a sheet with the name field prefilled and a primary "Save"); "Ungroup", which keeps both snipes armed on their own and offers Undo, only while a member is open.
2. **Rule card** (secondaryContainer, 20 dp corners):
   - link 24 dp plus "Win 1 of 2" in headlineSmall;
   - "Once one is won, Kestrel cancels the other." in bodyMedium;
   - bodySmall: "They end 23 h 45 m apart, so Kestrel will know the first result in time."
3. **"IN THIS GROUP", "2 lots":** the MiniDisc and Walkman rows (3.5) without the group flag.
4. 12 dp, then the outlined "Add a snipe to this group", which opens a picker of armed snipes.

**Rules:**
- **Bid times within 2 min of each other:** the rule card turns Attention: "MiniDisc and Walkman bid 2 min apart. Kestrel may not know the first result in time, so it may skip the later one." (the words that tell the titles apart, the gap in whole minutes). Kestrel never risks a second win (ARCHITECTURE section 8): while the first result is unknown it skips the later bid.
- **After a win:**
  - the card turns Won: "Won 1 of 2 · Sony MiniDisc Walkman MZ-N707 for £31.00";
  - the other row reads "Cancelled · you won another in Portable audio" with a tonal "Undo cancel" while it runs (S4 "Cancelled by group").

**Group picker sheet** (from S2):
- title "Add to a group";
- radio list: "No group" (default), "Portable audio · 2 snipes", "New group…" (adds a name field);
- the Group screen's "Add a snipe to this group" sheet lists each candidate with its state, "Ends Sun 9 Nov, 18:21" and, when it ends close to a member, "Ends close to a snipe in this group" in the Attention accent; its 40 dp plates have a 1 dp rule edge.
- primary "Done".

### Settings
Opened from the S1 account button.

**Layout:**
1. **Top bar:** back. Then "Settings" in headlineLarge in the content.
2. **"ACCOUNT":**
   - account_circle;
   - "oakshelf" in titleMedium;
   - "Signed in on this phone since Sun 2 Nov" (the date when the phone signed in through Kestrel; without it, "Signed in on this phone");
   - a tonal "Sign out" button, which opens a dialog:
     - title "Sign out of eBay?";
     - text "Your 5 armed snipes pause until you sign in again. Kestrel keeps your list.";
     - buttons "Sign out" (text, ink) and "Cancel".
3. **"BIDDING":**
   - "Default lead time" / "How long before the end Kestrel bids", then connected toggles 3 s / 6 s / 10 s;
   - "Suggest odd pence" / "Offer £90.37 instead of £90.00", with a Switch (checked track primary, thumb onPrimary).
4. **"ALERTS":**
   - "Price passed your max" / "One alert per listing when the price goes past your max", with a Switch, shown on and disabled until the alerts can be turned off;
   - "Notification settings" / "Live Updates, sounds and lock screen", with a chevron to the system page.
5. **"THIS PHONE":** "Phone checks", whose StateLabel reads "1 thing needs attention" (or "All checks passed at 18:06"). It opens S5 ("This phone").
6. **"APPEARANCE":** "Theme", with connected toggles System / Light / Dark; "Sound for a win" / "The celebration's
   bells and kestrel calls, at media volume", with a Switch (3.21).
7. **"HELP":** "How Kestrel bids" (a sheet: one bid at the end, a dry run first, you stay in charge, from this phone) and eBay's own words on sniping as a pull-quote.
8. **"ABOUT":**
   - "Kestrel 1.0 (1)" / "Kestrel isn't made by or affiliated with eBay.";
   - "Watch the win celebration" / "What plays the first time you open a win. Nothing is bid." (3.21);
   - "Buy me a coffee" / "Kestrel has no ads and no fees. Opens ko-fi.com/ivallesp." (opens the Ko-fi page in the
     browser; a snackbar explains if nothing can open it);
   - "Licences", listing the fonts (OFL) and the open-source software (Apache 2.0), each licence and holder on its own line. Kestrel ships no photos: listing photos come from eBay. "Read the font licence" shows both fonts' copyright lines, then the shared licence once, its section names in small caps.

**Rows:** SegmentedListItem in the card colour, 16 dp padding.

### History
Opened from "See all finished snipes".

**Layout:**
1. **Top bar:** back. Then "History" in headlineLarge.
2. **Summary card:** "Won 1 of 2 in November" in titleMedium, "£11.00 under your max in total" in bodyMedium. Results over several months name when they start: "Won 2 of 4 since December 2024".
3. **Month section heads,** for example "NOVEMBER", "2 lots". The year appears only when it is not the current one: "DECEMBER 2024".
4. **Done rows** (3.5), newest first. A tap opens S4.

**Empty:** 3.19.

---

## 5. Notifications
**Shared rules:**
- **Small icon:** ic_notification.
- **Titles:** 28 characters or fewer (counts in brackets below). Long item names shorten to the first two words, or a nickname, at most 12 characters ("Canon AE-1").
- **Times with seconds always carry the zone.**
- **No emoji.** Red only for failures.
- **Accent** (`setColor`, never colorized): the family accent.

**Channels:**

| Id | Name | Importance | Sound | Use |
|---|---|---|---|---|
| live | Live snipes | DEFAULT | none, no vibration | Live Update during the final minutes |
| needs_you | Needs you | HIGH (heads-up) | one sound | captcha, sign-in, phone check, dry run failed, offline near a bid, can't reach the listing |
| price | Price passed your max | HIGH | one sound per event | over-max alerts with inline Raise max |
| results | Results | DEFAULT | sound for Won and failures; silent for Lost and Ended | results |
| readiness | Readiness | LOW | none | battery and permission changes, once per change |

No channel uses full-screen intents.

### 5.1 Live Update (Android 16 and later)
**When:**
- Post one ongoing notification (`setOngoing(true)`, `setRequestPromotedOngoing(true)`) when the dry run passes, about 10 min before the end.
- If the dry run is skipped, post it 10 min before the end anyway.
- Visibility: PUBLIC.

**Status-bar chip:** a system-ticked countdown to the end:
- `setWhen(endsAt)`;
- `setShowWhen(true)`;
- `setUsesChronometer(true)`;
- `setChronometerCountDown(true)`.

It shows "9:58" and later "0:36". Do not set shortCriticalText. When the end moves, `when` moves with it.

**ProgressStyle:**
- **Track:** 600 s, from the end minus 10 min to the end.
- **Segments:**
  - 0 to 540 in the outline colour #8C8375;
  - 540 to 594 in the Bidding accent #5A3D92;
  - 594 to 600 in the Bidding on-container #2F1C57.
- **Points:** final minute at 540 (#8C8375), the bid at 594 (#5A3D92).
- **Progress:** elapsed seconds; `setStyledByProgress(true)`.
- **Tracker icon:** the kestrel glyph.
- **Update cadence:**
  - every 15 s before the final minute;
  - every 2 s inside it;
  - immediately on every status change.

**Actions:**
- "Edit max" opens S3 with the edit sheet. It is removed 3 s before the bid.
- "Open" opens S3.
- There is no cancel action.

**Copy by phase** (same notification id):

| Phase | Title | Text |
|---|---|---|
| Before the final minute | "Canon AE-1 ends 18:21:40" (24) | "£96.00 now · your max £120.00 · bids 18:21:34" |
| Final minute | "Canon AE-1 ends 18:21:40" (24) | "Kestrel bids £120.00 at 18:21:34 · £96.00 now" |
| Bid sent | "Bid sent: £120.00" (17) | "Waiting for eBay's reply · ends 18:21:40" |
| In the lead | "In the lead: Canon AE-1" (23) | "eBay: “Your current bid puts you in the lead.” Not won until 18:21:40." |
| Outbid | "Outbid: Canon AE-1" (18) | "eBay: “…” Your max was £120.00. Ends 18:21:40." |
| Extended | "Extended: ends 18:22:40" (23) | "A late bid reset the clock to 60 s. Kestrel is still following." |
| Reading result | "Ended: reading the result" (25) | "Kestrel is checking the listing page." |

When the result lands, cancel the Live Update and post the result notification.

### 5.2 Price passed your max (channel price)
- **Accent:** Attention #8A5200.
- **Title:** "Omega: over your max" (20).
- **Text:** "Now £2,150.00, £250.00 over your £1,900.00 max. Ends today 21:02 GMT."
- **Action "Raise max":** an inline reply.
  - RemoteInput label "New max (£)", hint "£2,200.00 or more". The minimum comes from `bidIncrement()`.
  - Choices: "£2,200.00" and "£2,250.00".
  - **Accepted:** the same notification updates to "Max raised to £2,250.00 · bids 21:02:09 GMT" and drops the reply action.
  - **Too low:** "That's below the next bid of £2,200.00. Try again." The reply action stays.
  - **Not a number:** "Type an amount, like 2250 or 2250.37."
- **Action "Stop sniping":**
  - sets the lot to Watching;
  - the notification updates to "Stopped sniping. Set a max again in Kestrel.";
  - the app shows an Undo snackbar the next time it opens.
- **Frequency:** at most one alert per lot each time the price passes the max. No alert for each new bid.

### 5.3 Results (channel results)

| Case | Title | Text | Actions |
|---|---|---|---|
| Won (accent #2D6A40) | "You won the Canon AE-1" (22) | BigText: "Won for £101.00, £19.00 under your max. Total with postage £107.50. eBay takes payment automatically." | "View receipt", "Open on eBay" |
| Lost (silent, accent #67625A) | "Lost: Nintendo 64 console" (25) | "Sold for £79.00. Your max was £65.00. Outbid by someone with a higher max." | "View receipt" |
| Group win | "Won 1 of 2: MiniDisc" (20) | "Kestrel cancelled your Walkman snipe in Portable audio." | "Undo cancel" (only while that auction runs) |
| Bid not sent (red #A3261E) | "Bid not sent: Canon AE-1" (24) | "eBay signed you out at 18:21:20. Nothing was bid." | "Sign in" |
| Bid failed (red) | "Bid failed: Canon AE-1" (22) | "eBay didn't accept the bid: “…”. Nothing was bid." | "View receipt" |
| Ended early | "Ended early: Game Boy" (21) | "The seller ended the listing. Nothing was bid." | "View" |

### 5.4 Needs you (channel needs_you)
- **Look:** heads-up, Attention accent, warning glyph.
- **Repeats:** if unresolved, it repeats once 5 min before the bid and once 2 min before it. The wording stays the same, with the time left added ("6 min left to fix it").
- **Never** full-screen.

| Case | Title | Text | Action |
|---|---|---|---|
| Captcha | "eBay wants a quick check" (24) | "Solve it before 18:21:34 so Kestrel can bid on your Canon AE-1. Kestrel never solves these for you." | "Open eBay page" (in-app, at the challenge) |
| Sign-in | "Sign in to keep bidding" (23) | "eBay signed you out. 5 snipes are paused. Next bid: Canon AE-1 at 18:21:34." | "Sign in" |
| Phone check | "eBay wants to verify you" (24) | "eBay asked for a code by text. Enter it on eBay's page; Kestrel will retry the dry run." | "Open eBay page" |
| Dry run failed | "Dry run failed: Canon AE-1" (26) | "At 18:11:40 eBay showed a sign-in page instead of the bid panel. Fix it before 18:21:34." | "Fix" |
| Offline near a bid | "Phone offline" (13) | "Kestrel can't bid on the Canon AE-1 at 18:21:34 unless the phone reconnects." | none |
| Can't reach listing | "Can't reach the listing" (23) | "Kestrel couldn't check the Canon AE-1 three times. It will keep trying until 18:21:34." | "Open" |

### 5.5 Readiness (channel readiness)
- **Title:** "Battery restricted" (18).
- **Text:** "Android may hold back a bid. Tap to let Kestrel run unrestricted."
- **Tap:** opens S5.

---

## 6. Copy deck (British English, curly quotes, £ on every amount, 24-hour clock)

### 6.1 Status model and words
Code: `ui/model/SnipeStatus.kt`.
- **One rule:** each case maps to one family, one list label, one detail line and one optional action. Screens never hard-code state words.
- **Allowed transitions:**
  - Watching ↔ Armed;
  - Armed → Attention cases → Armed;
  - Armed → FinalMinute → BidSent → (InTheLead | Outbid | NoReply);
  - from InTheLead, Outbid or NoReply → Extended (repeatable) → ReadingResult → (Won | Lost);
  - FinalMinute or BidSent → (BidNotSent | BidFailed);
  - Armed → (EndedEarly | Cancelled).
- **Won** needs eBay's result on the listing page. It is never inferred from "In the lead".

| Status | Family | List label | Detail | Action |
|---|---|---|---|---|
| Watching | Watching | "Watching · no max yet" | "Watching. Set a max to snipe it." | "Set a max" |
| Armed | Armed | "Armed · max £45.00" | "Armed · bids at 18:21:34, 6 s before the end" | "Edit max" |
| OverMax | Attention | "£250.00 over your £1,900.00 max" | "The price is £2,150.00, £250.00 over your max. Raise it or stop sniping." | "Raise max" |
| SignInNeeded | Attention | "Sign-in needed · paused" | "eBay signed you out. Sign in before 18:21:34 so Kestrel can bid." | "Sign in" |
| Captcha | Attention | "eBay wants a quick check" | "Solve it on eBay's page before 18:21:34. Kestrel never solves these for you." | "Open eBay page" |
| PhoneCheck | Attention | "eBay wants to verify you" | "eBay asked for a code by text. Enter it on eBay's page; Kestrel will retry the dry run." | "Open eBay page" |
| DryRunFailed | Attention | "Dry run failed" | "At 18:11:40 eBay showed a sign-in page instead of the bid panel. Fix it before 18:21:34." | "Sign in" when the dry run met a sign-in page, otherwise "Fix" |
| OfflineNearBid | Attention | "Phone offline · bid at risk" | "Kestrel can't bid at 18:21:34 unless the phone reconnects." | none |
| Unreachable | Attention | "Can't reach the listing" | "Kestrel couldn't check this listing three times. It keeps trying." | "Open on eBay" |
| NeedsYou | Attention | "Needs you · open the listing" | the engine's explanation, or "Kestrel stopped and needs you to look at this listing on eBay." | "Open eBay page" |
| FinalMinute | Bidding | "Final minute · bids in 30 s" | "Kestrel bids £120.00 in 30 s, at 18:21:34, 6 s before the end." | "Edit max" (until 18:21:31) |
| BidSent | Bidding | "Bid sent · waiting for eBay" | "Bid sent at 18:21:33.9 · waiting for eBay's reply" | none |
| InTheLead | Bidding | "In the lead · ends 18:21:40" | "eBay accepted your bid: “Your current bid puts you in the lead.” Not won until it ends." | none |
| Outbid | Bidding | "Outbid · ends 18:21:40" | "eBay replied: “…”" (without a quote: "Another bidder’s max is higher than yours.") | none |
| NoReply | Bidding | "Bid sent · no reply yet" | "No reply from eBay yet. Kestrel will read the result when the auction ends." | none |
| Extended | Bidding | "Extended · now ends 19:56:42" | "A late bid reset the clock to 60 s. Kestrel is still following." | none |
| ReadingResult | Bidding | "Ended · reading the result" | "Reading eBay's result…" | none |
| Won | Won | "Won · Sat 8 Nov" | "You won · won for £101.00, £19.00 under your £120.00 max" | "View receipt" |
| Lost | Ended | "Lost · Fri 7 Nov" | "Outbid · sold for £79.00. Your max was £65.00." | "View receipt" |
| EndedEarly | Ended | "Ended early by the seller · Wed 12 Nov" | "The seller ended the listing early. Nothing was bid." | none |
| CancelledByYou | Ended | "Cancelled · Sat 8 Nov" | "You stopped this snipe. Nothing was bid." | "Undo" (snackbar) |
| CancelledByGroup | Ended | "Cancelled · you won another in Portable audio" | "Kestrel cancelled this because you won Sony MiniDisc Walkman MZ-N707." | "Undo cancel" (while it runs) |
| BidNotSent | Ended, failure (red) | "Bid not sent · Sun 9 Nov" | "eBay signed you out at 18:21:20. Nothing was bid." | "Sign in" |
| BidFailed | Ended, failure (red) | "Bid failed · Sun 9 Nov" | "eBay didn't accept the bid: “…”. Nothing was bid." | "View receipt" |

The hero's line 2 is never the detail: 3.4 lists its one-line words. Chips are short: "Extended", "Bid at risk" (offline near a bid), "Needs you".

### 6.2 Readiness words
| Situation | Words |
|---|---|
| Ready | "Ready to snipe" / "All checks passed at 18:06" |
| One problem (dock) | "Battery restricted" / "A bid could go out late" / "Fix" |
| Several problems | "2 things need attention" / "Signed out · battery restricted" / a chevron |
| S5 header | "1 thing needs attention" / "Fix it before 18:21:34, when Kestrel bids on your Canon AE-1 Program." |
| S5 rows | "Optimised", "Off · optional", "Signed in as oakshelf · checked 2 min ago", "On", "Allowed, so bids leave on the second", "0.04 s off Google time · synced 1 min ago", "Passed today at 18:11 on the Canon AE-1 · nothing was sent", "Runs about 10 minutes before each bid" |

### 6.3 Key sentences
- **S1 overline:** "SUNDAY 9 NOVEMBER · GMT".
- **S1 Done link:** "See all finished snipes".
- **S1 extended flag:** "Extended bidding: Kestrel's bid will reset the clock to 60 s."
- **S1 offline banner:** "Offline since 18:02" / "Prices are from 18:02. Kestrel can't bid until the phone reconnects."
- **S1-empty steps:**
  1. "Share a listing" / "In the eBay app or your browser, tap Share, then Kestrel."
  2. "Set your maximum" / "You see the most you could pay, with postage."
  3. "Kestrel bids at the end" / "From this phone, 6 s before the auction closes."
- **S2:**
  - "Includes £3.13 Buyer Protection (private seller)."
  - "At least £66.08"
  - "Usually less: eBay charges one step above the next bidder, never more than your max."
  - "Odd pence beat bidders who stop at £90.00."
  - "6 s suits most connections. Choose 10 s on a weak signal."
  - "Bids £90.00 on Thu 13 Nov at 20:04:12 GMT"
- **S3:**
  - "Kestrel bids £120.00 in 30 s"
  - "Until 18:21:31, 3 s before the bid"
  - "Dry run at 18:11:40 · nothing was sent"
  - "Clock 0.04 s off Google time"
- **S4:**
  - "eBay takes payment automatically from your saved method. If it can't, you have 4 days to pay."
  - "Your £65.00 bid reached eBay 6.0 s before the end and counted. Their max was higher, so eBay's proxy bidding kept them ahead. You pay nothing."
- **Snackbars:**
  - "Max raised to £2,200.00" (Undo)
  - "Stopped sniping Omega Speedmaster" (Undo)
  - "Armed: bids Thu 13 Nov at 20:04:12"
  - "Max changed to £130.00" (Undo)
  - "Ungrouped Portable audio" (Undo)
  - "That link isn't an eBay listing."

### 6.4 Copy rules
- **Spelling and words:** British spelling ("cancelled", "colour"). Use "postage", never "shipping".
- **Numbers:**
  - "£" on every amount, with 2 decimals and thousands separators;
  - no negative amounts: use "over", "under", "above" or "below".
- **Days and times:**
  - day names with dates ("Mon 10 Nov");
  - "Today" only for today;
  - never "tomorrow" or "yesterday".
- **Seconds:** only within 1 h of an event, in the arm summary and in receipts.
- **Zones:** GMT or BST, computed for each instant.
- **Tone:** no emoji, no exclamation marks.
- **Quoting eBay:** eBay's text is always quoted exactly, in curly quotes.
- **Forbidden words:** "accepted" appears only in the in-the-lead detail; "You paid" is never used.

---

## 7. Must-fix rules (every judge must-fix that touches the final design, as a concrete rule)
Sources: VJ = visual judge, CJ = clarity judge, TJ = truthfulness judge. The concept in brackets is the one the item was raised against.

**Typography**
- **MF1. Tabular figures** (VJ, C3): no tnum on Google Sans sentences. Follow T2 to T4, and QA Q4 must pass.
- **MF2. Body tracking** (VJ, C3): body tracking is only as in 2.5 (0.1, 0.15 and 0.25 sp). Drop C3's 0.15 to 0.3 sp.
- **MF3. Units in small caps** (VJ, CJ, TJ on C3): units are never uppercase.
  - SmallCaps rejects digit-plus-unit strings and "eBay" (SC2).
  - The S4 timing line is sentence case: "Sent 6.1 s before the end · reply in 0.8 s".
- **MF4. Apostrophes and brand case** (VJ, CJ on C2): use curly ’ “ ” only, and never uppercase "eBay". A unit test scans every string resource and copy constant.
- **MF5. Totals** (VJ, C2): totals outweigh their parts. The ledger Total and the sum-row result are weight 700.
- **MF6. Small caps** (VJ, C2): small caps only per SC1. Rows carry none.
- **MF7. Result headlines** (VJ, C1): the Lost and failure headlines are displaySmall (40), one step below "You won" (displayMedium 52).

**Layout and density**
- **MF8. S1 density** (VJ, CJ, TJ on C3):
  - hero plate 128 dp;
  - state chip on the plate;
  - no hairline or "Next up" label in the hero;
  - acceptance per S1 (This week head and group header visible above the dock at rest).
- **MF9. Side-by-side figures** (VJ, C3): labels share one baseline and figures share another (money grid, 3.2).
- **MF10. Dock alignment** (VJ, C3): the dock row has 16 dp side padding, so the pill starts at 16 dp and Add ends at 395 dp.
- **MF11. Account button** (VJ, C1): its right edge is at 16 dp from the screen edge. It uses the person glyph, never a lone initial.
- **MF12. Gesture handle** (VJ, CJ on C2): nothing sits under it.
  - Every scrolling screen without a dock gets BottomFade.
  - Content ends at the inset plus 32 dp.
- **MF13. No FAB** (CJ and TJ on C1): no floating action button anywhere. The dock never overlaps content.

**Colour and dark mode**
- **MF14. Dark brightness** (VJ, CJ on C3):
  - dark plate #B0A898;
  - dark scene photos at 88 %;
  - S3 band top scrim at 45 %.
  - No surface or plate may be brighter than onSurface text.
- **MF15. Remaining-time track** (VJ, C1): it uses the outline colour (3.3:1 light, 5.2:1 dark), never a container tint.
- **MF16. Pixel-native** (VJ, C3):
  - **Adopted:** M3 Expressive theme, motion and components; Google Sans Flex UI; predictive back; edge-to-edge; the themed icon; Live Updates.
  - **Declined:** wallpaper-tinted chrome. The slate is the Armed state colour, and a wallpaper accent would compete with the six fixed state families. Recorded as risk R1.
- **MF17. Neutral decoration** (C3 review): state colours never decorate neutral UI. The group header and the odd-pence card use neutral roles (U1).

**State honesty**
- **MF18. State words** (CJ, C3): state reads as a 14 sp (rows) or 16 sp (S4, S5) sentence-case word in the family accent with its glyph. Never 11 sp caps.
- **MF19. Status model** (TJ, C3):
  - the full status model of 6.1 in `SnipeStatus`;
  - every chip, label, bid card, scale behaviour and receipt row reads from it;
  - an `advance(event)` function enforces the transitions;
  - unit tests cover every edge;
  - no state word is hard-coded in a screen.
- **MF20. No early claims** (TJ, C2): no label claims a step before it happens. The S3 column says "BIDS 18:21:34" until BidSent, then "SENT 18:21:33.9".
- **MF21. "Accepted"** (CJ, C3): S4-lost says "counted". "Accepted" appears only in the in-the-lead detail.
- **MF22. "You paid"** (TJ, all three): S4 says "Won for £101.00", the ledger total is £107.50 and the payment note stays. "You paid" is banned.
- **MF23. Receipt rows** (TJ, graft from C1): the receipt separates "Auction ended · on time, no extension" from "Won at £101.00 · confirmed on the listing page".
- **MF24. Failure copy** (TJ, graft from C1):
  - "Bid not sent" means the bid never left the phone;
  - "Bid failed" means eBay rejected it;
  - both use the red tone, with their own copy.
- **MF25. No empty dry-run claim** (TJ, all three):
  - the S1-empty pill says "All checks passed at 18:06";
  - with nothing armed, the S5 dry-run row says "Runs about 10 minutes before each bid" and is not counted.
- **MF26. Sign-in promise** (TJ, all three): no "You only do this once" or "One-time setup". S6 says eBay may ask again.
- **MF27. Do Not Disturb** (TJ, C2): "Alerts can sound in Do Not Disturb" appears only when Do Not Disturb access is granted.

**Time and money**
- **MF28. Arm summary** (CJ, TJ on C3): S2 shows it directly above "Arm snipe", live: "Bids £90.00 on Thu 13 Nov at 20:04:12 GMT". No "a few seconds" footer.
- **MF29. Seconds** (TJ, C2): seconds appear only within 1 h of an event, plus the arm summary and receipts. S2 says "Ends Thu 13 Nov, 20:04".
- **MF30. Day labels** (TJ, CJ on C1):
  - every day and time label is formatted from an Instant in Europe/London;
  - no day string is hard-coded ("Fri 6 Nov" was a Thursday);
  - a fixture test asserts every weekday.
- **MF31. S5 render time** (TJ, C1): S5 renders at 18:13:52, after the 18:11:40 dry run. No screen states a past event with a future time.
- **MF32. Minimum bid** (TJ, C1): every minimum comes from `bidIncrement()`. The Omega minimum is £2,200.00 in the hint, the error, the menu and the notification, and a test covers it.
- **MF33. Extended bidding** (TJ, C3 and C2):
  - the new end = the late bid's time plus 60 s;
  - the row flag reads "Extended bidding: Kestrel's bid will reset the clock to 60 s.";
  - S2 and S3 carry the explanation;
  - never "adds 60 s".
- **MF34. Flags** (TJ, C1): they render on their own. An ungrouped extended lot still shows its flag, and a row-model test covers it.
- **MF35. Odd pence** (TJ, C2): the copy is "Odd pence beat bidders who stop at £90.00." Never "tie", and never a hard-coded amount.
- **MF36. Worst case** (CJ, graft from C1): the worst case with postage shows on the hero, in S2, on S3 and in the edit sheets.
- **MF37. Postage** (CJ, TJ on C2): postage shows on every open row.
- **MF38. Result header** (CJ, C2): result screens show day, time and zone in the status line.

**Actions**
- **MF39. Destructive actions** (CJ, TJ on C2): no destructive button sits beside an edit. "Stop sniping" lives in the Raise max menu and the S3 overflow, with a 10 s Undo.
- **MF40. Dock Fix** (CJ, TJ on C3):
  - the dock's Fix is a real filled button;
  - a single problem shows its consequence ("A bid could go out late");
  - the pill also shows when the checks last ran.
- **MF41. Continue to eBay** (TJ, CJ on C1, and TJ on C2):
  - no open-in-new glyph on the button;
  - S6 names signin.ebay.co.uk, says the page opens inside Kestrel, adds the password tip and states "not affiliated with eBay".
- **MF42. Max field** (TJ, C3 and C1): the field has a label, supporting text and an error state. Its state is hoisted and keyed to the lot, and "Use £90.37" updates it (3.18).
- **MF43. S5 trust signals** (CJ, TJ on C3):
  - the deadline sentence;
  - the "Checked today 18:13" overline;
  - "Check again";
  - the re-check footer;
  - counts on the section heads.

**Notifications**
- **MF44. Price alert title** (CJ, TJ on C3): "Omega: over your max", because nothing has been bid.
- **MF45. Live Update chip** (TJ on C1, CJ):
  - a static absolute title;
  - a chronometer chip (setWhen, setUsesChronometer, setChronometerCountDown);
  - no per-second title reposts.
- **MF46. Alert delivery** (TJ, C3 and C1): no full-screen intents. Needs-you alerts repeat once 5 min before the bid and once 2 min before it.

**Accessibility and coverage**
- **MF47. Spoken countdown** (CJ, C2 and the C1 graft):
  - each countdown is one semantic node, for example "36 seconds left. Kestrel bids in 30 seconds.";
  - per-digit Texts use clearAndSetSemantics;
  - section heads and screen titles are headings.
- **MF48. Missing states** (TJ, all three): render and review the states no concept built. These are Bid sent, In the lead, Outbid, Extended, captcha, No reply, Bid not sent, the LoadingIndicator states and reduced motion (Q30).
- **MF49. Launcher icon** (VJ, C3): use C1's kestrel portrait with its monochrome layer. C3's hovering-bird mark is dropped.
- **MF50. Code hygiene** (TJ, C3):
  - remove unused dependencies;
  - replace the Home-only MainActivity with the Navigation3 shell, so every screen is reachable on a phone.

---

## 8. Visual QA checklist (run on every render, light and dark)
- **Q1. Columns.**
  - Card edges at 16 and 395 dp.
  - Text and plates inside cards at 32 dp.
  - Section heads, screen titles and dock content at 16 dp.
- **Q2. No clipping.** No truncated or clipped text at 411 dp with font scale 1.0 and 1.3.
  - Titles may wrap to 2 lines.
  - State words and line 1 of the dock pill never truncate.
- **Q3. Fold.** The S1 fold acceptance holds at 1.0, and S4-won shows the RECEIPT head and its first row.
- **Q4. Word spaces.** Spaces in every string with figures are normal width. Check:
  - "Bids at 18:21:34, 6 s before";
  - "in 4 d 1 h";
  - "Includes £3.13 Buyer Protection";
  - "Ends Thu 13 Nov, 20:04".
  Overlay the same string without tnum to compare.
- **Q5. Spacing characters.** Number-unit pairs use U+202F. Titles keep "f/1.8", "MZ-N707" and "DMG-01" whole.
- **Q6. Case and quotes.** Units are lowercase everywhere. "eBay" is never uppercase. All quotes and apostrophes are curly.
- **Q7. State readability.** Every state shows colour, glyph and words. A greyscale copy of each render still reads.
- **Q8. Red.** Red appears only on Bid not sent and Bid failed (rows, S3, S4, notifications).
- **Q9. Time.**
  - Every open lot shows relative plus absolute time.
  - The zone is on the S1 overline, hero, S3, S4, S2 summary and rows within 1 h.
  - No seconds appear beyond 1 h except the arm summary and receipts.
  - No "tomorrow" or "yesterday".
- **Q10. Money.**
  - "£" with 2 decimals on every amount.
  - Postage on every open row.
  - The worst case on the hero, S2, S3 and the sheets.
  - The minimum bid matches the increment table.
- **Q11. Figure alignment.** Side-by-side labels share a baseline, and so do their figures.
- **Q12. Contrast.** Run `/var/tmp/kestrel-lead/contrast.py` style checks for any new pair: text at 4.5:1 or better, graphics (tracks, ticks, outlines) at 3:1 or better.
- **Q13. Touch targets.** 48 dp or more, asserted in a Compose UI test (`assertTouchHeightIsAtLeast(48.dp)` and the width equivalent) on every clickable.
- **Q14. Edges.** Nothing sits under the gesture handle or the dock at rest. BottomFade is present on non-dock screens.
- **Q15. Dark plates and photos.** Plates are #B0A898 and scene photos 88 %. No surface outshines text. Check black products (N64, Canon) for lost detail.
- **Q16. Neutral decoration.** The group header, the odd-pence card and the empty-state badge use neutral roles.
- **Q17. Motion.**
  - Only changed digits roll.
  - A price change slides once, with no colour flash.
  - The wave appears only in the S3 final minute and flattens at Bid sent.
  - The seal stamps once.
  - With ANIMATOR_DURATION_SCALE at 0 nothing moves.
- **Q18. TalkBack.**
  - Headings are on section heads and titles.
  - Row descriptions are merged, and custom actions exist.
  - Countdowns are spoken once as one sentence.
  - The S3 live region speaks only the listed moments.
- **Q19. State-driven copy.** A test renders each SnipeStatus in a row, the hero and S3, and checks the words against 6.1. No hard-coded state string exists outside the status model.
- **Q20. Notification titles.** Every template is filled with the longest fixture names and stays at 28 characters or fewer (unit test).
- **Q21. Fixture.** Every rendered day label matches its Instant, and the S5 render time is after the dry run.
- **Q22. Destructive actions.** Every destructive action is in a menu with Undo, or in the sign-out dialog.
- **Q23. Insets.** Status-bar icons are dark on paper, light on the S3 scene photo and light in dark theme. Over a light-theme S3 cutout plate they are dark, and the band has no scrim. The S3 band sits under the status bar with the scrim. Offline renders draw the status bar without Wi-Fi and with an empty signal.
- **Q24. Icons.** Filled glyphs only for warning, error, trophy, check_circle and battery_alert. Sizes follow 2.11.
- **Q25. Launcher icon.** It is legible at 24 dp. The monochrome layer is a head with an eye hole and no rings, and themed light and dark look right.
- **Q26. Dock.** Each pill state renders (ready, one problem, several, signed out, offline). The Fix button is 48 dp touch.
- **Q27. S2.** The summary updates with max and lead time, and Arm snipe is disabled below the minimum. The empty field shows "Watch without a max", and the error state uses Attention, not red.
- **Q28. Edit sheet.** It stays above the keyboard and locks itself at the deadline.
- **Q29. Visibility of the max field.** The cursor and underline are visible in dark theme.
- **Q30. Render set.** Recorded with `./gradlew --offline recordRoborazziDebug`. The brief requires light and dark for S1 and S3. This spec requires both themes for all of:
  - **S1:** S1-home, S1-empty, S1-home-scrolled, S1-offline, S1-signed-out.
  - **S2:** S2-add, S2-link, S2-fetching, S2-empty-max, S2-too-low.
  - **S3:** S3-live, S3-bid-sent, S3-in-the-lead, S3-extended, S3-captcha, S3-armed-far, S3-reduced-motion.
  - **Sheets:** edit-max-sheet, raise-max-sheet.
  - **S4:** S4-won, S4-lost, S4-bid-not-sent.
  - **Other screens:** S5-ready, S6-signin, S6-browser, S7-icon, group, settings, history.
  - **Large text:** S1-home and S3-live at font scale 1.3 (light).

---

## Appendix A. Fixture (fake data; instants in 2025, the year is never shown)
**Clock:**
- **Now:** Sun 9 Nov 2025 18:08:33 GMT.
- **Account:** oakshelf, signed in since Sun 2 Nov.
- **Readiness:** battery restricted. Last full check 18:06 for S1; 18:13:52 for S5.

**Lots:**

| Lot | Photo (style) | Current, bids | Postage | Ends | Max | Status | Notes |
|---|---|---|---|---|---|---|---|
| Canon AE-1 Program + 50mm f/1.8 | canon_ae1.jpg (Scene, focusY -0.25) | £88.00, 14 | £6.50 | Sun 9 Nov 18:21:40 | £120.00 | Armed, lead 6 s, bids 18:21:34 | armed 09:14:02; dry run 18:11:40.2 |
| Omega Speedmaster “Pre-moon” 1960s | speedmaster.jpg (Cutout) | £2,150.00, 31 | £12.95 | Sun 9 Nov 21:02:15 | £1,900.00 | OverMax £250.00 | next minimum £2,200.00; bids 21:02:09 |
| Sony MiniDisc Walkman MZ-N707 | minidisc.jpg (Cutout) | £24.00, 5 | £3.49 | Mon 10 Nov 20:10:05 | £45.00 | Armed | group Portable audio, win 1 of 2 |
| Sony Walkman WM-FX421 | walkman.jpg (Cutout) | £18.50, 3 | £3.20 | Tue 11 Nov 19:55:48 | £40.00 | Armed | same group; extended bidding |
| Polaroid SX-70 Land Camera | polaroid.jpg (Scene, focusY -0.2) | £61.00, 9 | £7.50 | Thu 13 Nov 19:30:00 | none | Watching | minimum £63.00 |
| Nintendo Game Boy DMG-01, boxed | gameboy.jpg (Cutout) | £46.00, 11 | £4.10 | Sun 16 Nov 21:43:52 | £72.37 | Armed | in 7 d 3 h |
| Game & Watch Donkey Kong II | gameandwatch.jpg (Cutout) | won for £34.00 | £3.10 | ended Sat 8 Nov 20:14:09 | £45.00 | Won | £11.00 under |
| Nintendo 64 console | n64.png (Cutout) | sold for £79.00, 17 | £6.99 | ended Fri 7 Nov 19:41:00 | £65.00 | Lost | bid sent 19:40:54.0; reply 19:40:54.9 |

**Listings outside the list:**
- **S2 Sony Alpha A700 body** (sony_a700.jpg, Cutout):
  - seller camerashed_uk: private seller, 99.4 % positive, 812 ratings;
  - current £64.00 including £3.13 Buyer Protection, 7 bids;
  - postage £8.95;
  - ends Thu 13 Nov 20:04:18 (in 4 d 1 h at 18:09:10);
  - minimum £66.08 (3.18: the higher reading of eBay's rule with Buyer Protection);
  - max typed 90.00, worst case £98.95, odd pence £90.37;
  - lead 6 s, so it bids at 20:04:12 GMT.

**Live and result moments:**
- **S3 final minute** (18:21:04):
  - current £96.00 (up from £88.00), 16 bids;
  - edit allowed until 18:21:31;
  - signed in, checked 18:20:31;
  - clock 0.04 s off Google time.
- **S4 won** (18:22:05):
  - won for £101.00, total £107.50;
  - bid sent 18:21:33.9 (6.1 s before the end);
  - reply 18:21:34.7 (0.8 s): “Your current bid puts you in the lead.”;
  - won confirmed at 18:21:46.2.
- **S4 lost** (Fri 7 Nov 19:42:10): reply “You’ve been outbid. Another bidder’s maximum is higher.”
- **S3 extended** (Walkman, Tue 11 Nov 19:55:58):
  - Kestrel's bid sent at 19:55:42.0;
  - in the lead at £19.50;
  - new end 19:56:42.
- **eBay UK increments** (`bidIncrement`), by current price:
  - up to £1.00: £0.05;
  - up to £5.00: £0.20;
  - up to £15.00: £0.50;
  - up to £60.00: £1.00;
  - up to £150.00: £2.00;
  - up to £300.00: £5.00;
  - up to £600.00: £10.00;
  - up to £1,500.00: £20.00;
  - up to £3,000.00: £50.00;
  - above that: £100.00.
