# Design direction for a best-in-class modern Android (Pixel 9a, Android 16/17) eBay auction sniper and deal tracker: Material 3 Expressive, typography, color, adjacent-domain patterns, per-screen recommendations, motion, and 3 visual style options

## Summary
Material 3 Expressive (M3E, announced 2025-05-13) adds components, spring-based motion, emphasized typography, a 35-shape library and richer color on top of Material 3. Google says it is not a new version ("this isn't M4"). Its research claims are 46 studies, more than 18,000 participants, and key UI elements found up to 4x faster. Its own rule is to keep to one or two "hero moments" per product.

In Compose, the expressive APIs are only in androidx.compose.material3 1.5.0 (beta02, 2026-10-21). Stable 1.4.0 removed them, and Compose BOM 2026.09.00 still maps material3 to 1.4.0, so the app must pin 1.5.0-beta02 explicitly. In the 1.5.0-beta01 API, ButtonGroup, ToggleButton, SplitButtonLayout, the floating toolbars, FloatingActionButtonMenu, the wavy progress indicators, MotionScheme, MaterialExpressiveTheme, the expressive list items and the emphasized type styles need no opt-in. Only LoadingIndicator and MaterialShapes (toShape/toPath) still need @ExperimentalMaterial3ExpressiveApi.

Recommended typeface: bundle Google Sans Flex. It has been open source (OFL) since November 2025 and has ROND, opsz, slnt, wdth and wght axes. Its digits are proportional by default, so every ticking or column-aligned number needs fontFeatureSettings = "tnum". Material's typography guidance says the same for clocks. Variable fonts cannot be loaded through Google downloadable fonts, so the file goes in res/font.

Use wallpaper dynamic color for the app's surfaces and actions. Keep a small fixed set of state colors (good, caution, danger, info, neutral), always shown with an icon and a text label. I measured that no 4-hue state set clears the full color-blindness target. Red is reserved for failures, never for "time is short". Android 17's Live Update semantic colors (green safe, orange caution, red danger, blue info) give the same meanings.

For countdowns, show calm relative-plus-absolute times ("in 2 h 14 m, bids at 21:43:52"). Precision increases only near the end, and the single hero moment is the final minute, the bid, then the result. A Live Update (promoted ongoing notification with a chronometer countdown chip) appears only in the final few minutes, as Google's "imminent events" rule allows. Results are posted as normal notifications.

Permission asks happen in context, and a "Ready to snipe" screen checks each one with a Fix button that opens the right Settings screen. For a sideloaded build, USE_EXACT_ALARM is granted automatically, which removes one prompt.

Three style options are given:
- O1 Pixel-native Expressive.
- O2 Departure-board instrument.
- O3 Auction catalogue (editorial).

The recommendation is O1 as the base, plus O2's monochrome-until-exception precision for times and prices, plus O3's photo-forward hero and photo-derived color on item detail and result screens only. Dribbble, Behance and Mobbin were not usefully reachable. Live Dribbble pages came back empty, Behance and Mobbin returned 403, and an archived Dribbble "auction" tag page is mostly NFT concepts.

## Findings
- **DD1** [high] Material 3 Expressive is an expansion of Material 3, not a new major version; Compose implements it and Google positions it as complementing Android 16 system UI.
  - evidence: m3.material.io 'Start building with Material 3 Expressive' (content JSON via https://m3.material.io/_dsm/content/m3/2026-10-13_06-10-19/dd671163-5c9d-42c2-9737-048c191ed507.json, updatedTimestamp 2026-07-16; original post 2025-05-13): "M3 Expressive isn't a new version of the system. We're not deprecating M3, and this isn't 'M4.'" developer.android.com/develop/ui/compose/designsystems/material3 (accessed 2026-11-04): "M3 Expressive complements the Android 16 visual style and system UI." blog.google launch post 2025-05-13 (Mindy Brooks).
  - implication: Build a normal M3 Compose theme and adopt expressive pieces incrementally; the app will look at home next to the Pixel 9a system UI.
- **DD2** [high] Google's M3E research: 46 studies, 18,000+ participants, key UI elements spotted up to 4x faster; but breaking familiar patterns or removing text labels hurt usability.
  - evidence: m3.material.io building-with-m3-expressive (2026-07-16): "46 studies with more than 18,000 participants", "participants spotting key UI elements up to four times faster in expressive screens". design.google/library/expressive-material-design-google-research (undated page, 2025): "No amount of expressive design will beat basic functionality"; removing text labels from email actions "resulted in decreased usability"; larger Send button spotted "four times faster".
  - implication: Make the Arm snipe button and the countdown visually dominant, keep text labels on all actions, keep standard navigation and list patterns.
- **DD3** [high] M3E defines seven expressive tactics and explicitly limits hero moments to one or two per product.
  - evidence: m3.material.io building-with-m3-expressive (2026-07-16): tactics '1. Use a variety of shapes', '2. Apply rich and nuanced colors', '3. Guide attention with typography', '4. Contain content for emphasis', '5. Add fluid and natural motion', '6. Leverage component flexibility', '7. Combine tactics to create hero moments'; "Stick to one or two hero moments in your product; too many moments can be overwhelming or distracting."
  - implication: Spend expressive effort on one sequence (final minute, bid, result) and optionally the arming confirmation; everything else stays calm.
- **DD4** [high] Expressive Compose APIs are not in stable Material3 1.4.0; they ship in 1.5.0 (latest 1.5.0-beta02, 2026-10-21), and the current Compose BOM still maps material3 to 1.4.0.
  - evidence: developer.android.com/jetpack/androidx/releases/compose-material3 (accessed 2026-11-04): 1.4.0 released 2025-09-24; 1.4.0-beta01 (2025-07-30): "All public APIs tagged with ExperimentalMaterial3ExpressiveApi ... have been removed, please switch to 1.5.0-alpha"; 1.5.0-beta01 2026-10-07, 1.5.0-beta02 2026-10-21. Google Maven compose-bom-2026.09.00.pom: material3 1.4.0, ui/foundation/animation/runtime 1.12.1.
  - implication: Declare implementation("androidx.compose.material3:material3:1.5.0-beta02") explicitly on top of the BOM; accept beta status for a personal app.
- **DD5** [high] In the 1.5.0-beta01 API surface, most expressive components need no opt-in; only LoadingIndicator/ContainedLoadingIndicator, MaterialShapes (+ RoundedPolygon.toShape/Morph.toPath) and DropdownMenuItemChecked/Selected remain @ExperimentalMaterial3ExpressiveApi.
  - evidence: github.com/androidx/androidx compose/material3/material3/api/1.5.0-beta01.txt (androidx-main, read 2026-11-04): ButtonGroup, ToggleButton, SplitButtonLayout, HorizontalFloatingToolbar, VerticalFloatingToolbar, FloatingActionButtonMenu, ToggleFloatingActionButton, CircularWavyProgressIndicator, LinearWavyProgressIndicator, MotionScheme, MaterialExpressiveTheme, expressiveLightColorScheme, ShortNavigationBar, WideNavigationRail, MediumFlexibleTopAppBar, LargeFlexibleTopAppBar, FlexibleBottomAppBar, AppBarRow, SegmentedListItem, Typography *Emphasized styles carry no experimental annotation; LoadingIndicator lines carry @ExperimentalMaterial3ExpressiveApi. Release notes 1.5.0-alpha19 (2026-05-06): "Revert MaterialShapes and LoadingIndicator promotions to stable."
  - implication: Keep @OptIn(ExperimentalMaterial3ExpressiveApi::class) confined to the loading/bid-in-flight and decorative-shape composables.
- **DD6** [high] MotionScheme has two presets: expressive (overshoot, for prominent elements and hero interactions) and standard (minimal bounce, utilitarian); each exposes fast/default/slow spatial and effects specs backed by springs with fixed token values.
  - evidence: androidx MotionScheme.kt KDoc (androidx-main): "The expressive scheme is Material's recommended motion scheme for prominent UI elements and hero interactions"; "The standard scheme is Material's basic motion scheme for utilitarian UI elements and recurring interactions." ExpressiveMotionTokens.kt: default spatial damping 0.8 / stiffness 380, fast spatial 0.6/800, slow spatial 0.8/200, effects damping 1.0 with stiffness 1600/3800/800. StandardMotionTokens.kt: spatial damping 0.9 with 700/1400/300. m3.material.io Motion physics system (2026-07-17): spatial springs "overshoot", effects springs for color/opacity "where there shouldn't be any overshoot".
  - implication: Use MaterialExpressiveTheme(motionScheme = MotionScheme.expressive()); drive custom animations from MaterialTheme.motionScheme.*Spec(); wrap utilitarian areas in MaterialTheme(motionScheme = MotionScheme.standard()) (LocalMotionScheme was removed in 1.5.0-alpha27).
- **DD7** [high] Compose animations automatically respect the system animator duration scale (including 'Remove animations').
  - evidence: androidx compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/WindowRecomposer.android.kt (androidx-main): readAnimationScale() reads Settings.Global.ANIMATOR_DURATION_SCALE and observes changes.
  - implication: Reduced motion works without extra code, but every state must still be readable without its animation (text labels, icons).
- **DD8** [high] MaterialShapes provides 35 named shapes, and Material's shape guidance says shapes are decorative, not semantic, should be used sparingly, and not on text-heavy containers.
  - evidence: material3 1.5.0-beta01 API MaterialShapes.Companion: Arch Arrow Boom Bun Burst Circle ClamShell Clover4Leaf Clover8Leaf Cookie12Sided Cookie4Sided Cookie6Sided Cookie7Sided Cookie9Sided Diamond Fan Flower Gem Ghostish Heart Oval Pentagon Pill PixelCircle PixelTriangle Puffy PuffyDiamond SemiCircle Slanted SoftBoom SoftBurst Square Sunny Triangle VerySunny. m3.material.io Shape (2026-07-17): "Shape is versatile, not semantic Avoid making shapes literal or assigning a specific function or meaning to a single shape"; "Use abstract shapes sparingly"; "Avoid applying unconventional shapes to text-heavy containers."; "Shape morphing should respond to user interaction".
  - implication: Do not encode snipe states as shapes; use shapes for photo crops in the hero/result, celebration, empty states and loading only.
- **DD9** [high] The M3E loading indicator is for short (<5 s) indeterminate waits, is never decorative, and should not be used when a process becomes determinate.
  - evidence: m3.material.io Loading indicator (2026-07-29): "Always reflect an ongoing process and are never simply decorative", "Not used for processes that transition from indeterminate to determinate", "designed to show progress that loads in under five seconds". MDC-Android docs/components/LoadingIndicator.md (2025-08-29): "It should replace most uses of the indeterminate circular progress indicator."
  - implication: Use LoadingIndicator for the item-preview fetch and the bid-in-flight moment; use a determinate (wavy) indicator for time remaining.
- **DD10** [high] Wavy progress is meant for longer processes and should not be repeated on every item; the default amplitude function draws a flat line below 10% and above 95% progress.
  - evidence: m3.material.io Progress indicators (2026-07-29): "The wavy shape can make longer processes feel less static"; "Don't add progress indicators to every activity." androidx WavyProgressIndicator.kt: WavyProgressIndicatorDefaults.indicatorAmplitude returns flat when progress <= 0.1f || progress >= 0.95f.
  - implication: One CircularWavyProgressIndicator on the next-snipe hero only; list rows show text times, not rings. Supply a custom amplitude if the wave should calm down near the end.
- **DD11** [high] Floating toolbars must not appear together with a navigation bar; a FAB menu holds 2-6 related items, must not open from an extended FAB, and must not be paired with a floating toolbar or navigation rail.
  - evidence: m3.material.io Toolbars (2026-07-28): "Don't show at the same time as a navigation bar". m3.material.io FAB menu (2026-07-17): "FAB menus can contain 2-6 items", "Don't open a FAB menu from an extended FAB or any other component.", "When a FAB is paired with other components, like the floating toolbar or navigation rail, don't use the FAB menu."
  - implication: Home: ShortNavigationBar plus an Extended FAB 'New snipe' (opens the add sheet, no FAB menu). Item detail (no nav bar): HorizontalFloatingToolbar with a FAB.
- **DD12** [high] Connected button groups replace segmented buttons; buttons come in five sizes with fixed heights XS 32, S 40, M 56, L 96, XL 136 dp (FAB 56/80/96 dp).
  - evidence: m3.material.io Button groups (2026-09-16): "Connected button groups replace the segmented button". androidx tokens ButtonXSmallTokens..ButtonXLargeTokens ContainerHeight 32/40/56/96/136 dp; FabBaselineTokens/FabMediumTokens/FabLargeTokens 56/80/96 dp. API: ButtonGroupDefaults.connectedLeadingButtonShapes()/connectedMiddleButtonShapes()/connectedTrailingButtonShapes(), ButtonGroupDefaults.ConnectedSpaceBetween.
  - implication: Bid-offset presets and history filters as connected ToggleButton groups; Arm snipe as a Medium (56 dp) or Large (96 dp) button.
- **DD13** [medium] Split buttons pair a primary action with a menu; the trailing button must always be a menu icon; Compose exposes SplitButtonLayout with SplitButtonDefaults.LeadingButton/TrailingButton (plus Tonal/Outlined/Elevated variants).
  - evidence: m3.material.io Split buttons (2026-09-08): "The trailing button should always have a menu icon." material3 1.5.0-beta01 API: SplitButtonLayout(leadingButton, trailingButton, modifier, spacing), SplitButtonDefaults.LeadingButton/TrailingButton/TonalLeadingButton/..., leadingButtonShapesFor(buttonHeight). Release notes 1.5.0-alpha25 mention deprecating SplitButtonLayout for SplitButton, but beta01 still exposes only SplitButtonLayout.
  - implication: 'Arm snipe' leading action with a menu (Watch only, Arm and open listing). Re-check the API name at implementation time because of the alpha25/beta01 discrepancy.
- **DD14** [high] Expressive (segmented) list items are Material's recommended list style since December 2025, with selection shape morphing; Compose exposes SegmentedListItem and ListItemDefaults.segmentedShapes(index, count).
  - evidence: m3.material.io Lists (2026-08-06): "December 2025 Variants: Added expressive list Recommended for new designs"; "Unselected corner radius: 4dp inner, 16dp outer Selected corner radius: 16dp". material3 1.5.0-beta01 API: SegmentedListItem(shapes, ...), ListItemDefaults.segmentedShapes(int index, int count), segmentedColors(). 9to5google 2025-11-17: Gmail and Messages place lists "in a container".
  - implication: Snipe rows grouped by end-time bucket in segmented containers; members of a snipe group shown as one connected segment block.
- **DD15** [high] M3E added corner tokens largeIncreased 20 dp, extraLargeIncreased 32 dp, extraExtraLarge 48 dp (extraLarge stays 28 dp), and Material recommends optical roundness for nested shapes (outer radius minus padding equals inner radius).
  - evidence: androidx tokens/ShapeTokens.kt: CornerLargeIncreased 20.dp, CornerExtraLarge 28.dp, CornerExtraLargeIncreased 32.dp, CornerExtraExtraLarge 48.dp; Shapes class has largeIncreased, extraLargeIncreased, extraExtraLarge. m3.material.io Shape (2026-07-17): "Outer radius - padding = inner radius For example: 48dp - 14dp = 34dp".
  - implication: Hero card at 28 dp, growing to 48 dp in the final minute (shape morph tied to the time change); thumbnails inside use computed inner radii.
- **DD16** [high] Compose shared-element transitions are stable since animation 1.10.0 (2025-12-03; latest stable 1.12.1, 2026-09-09); apps targeting API 36 get predictive back system animations by default and cannot opt out of edge-to-edge.
  - evidence: developer.android.com/jetpack/androidx/releases/compose-animation: 1.10.0-alpha05 (2025-10-08) "Shared transition APIs are now stable."; 1.12.1 2026-09-09. developer.android.com/about/versions/16/behavior-changes-16: "the predictive back system animations (back-to-home, cross-task, and cross-activity) are enabled by default"; windowOptOutEdgeToEdgeEnforcement "is deprecated and disabled".
  - implication: Use SharedTransitionLayout + Modifier.sharedBounds for list-to-detail photo/title; design full-bleed hero images under the status bar; never intercept back without PredictiveBackHandler.
- **DD17** [high] Google Sans Flex is free under the OFL (public release reported 2025-11-18); the google/fonts file is GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf v5.000 with ROND 0-100, opsz 1-144 (default 18), slnt -10-0, wdth 25-151, wght 1-1000; the current file has no GRAD axis although articles describe six axes.
  - evidence: github.com/google/fonts ofl/googlesansflex METADATA.pb (license OFL) and commit 2026-10-09 'Adding latest GSF v5.000 (#11097)', commit 2026-09-10 'remove grade'; fontTools fvar inspection 2026-11-04. 9to5google 2025-11-18 (Ben Schoon): "available under the Open Font License", base of the 'Rounded' variant in M3E. design.google/library/google-sans-flex-font: "this year - 2025 - Google decided to make Google Sans and Google Sans Flex open-source" (six axes incl. grade). TRADEMARKS.md: the names may not be used in a product name.
  - implication: Bundle the 3.9 MB TTF (subset later if size matters); use ROND for the rounded expressive look; do not put 'Google' in the app's name.
- **DD18** [high] Google Sans Flex digits are proportional by default; its tnum feature maps all ten digits to equal 1290-unit glyphs, zero gives a slashed zero, and the font lacks the glyphs for arrows and triangles (U+2191, U+2193, U+2192, U+25B2, U+25BC).
  - evidence: fontTools inspection of GoogleSansFlex.ttf v5.000 (2026-11-04): default digit advances [1290,735,1052,1070,1186,1126,1114,991,1102,1114]; GSUB tnum maps digits to zero.tf..nine.tf, all 1290; features include tnum, pnum, lnum, zero, frac, ss01, ss02; cmap missing the arrow/triangle code points; has pound, euro, U+2212 minus, U+202F, U+2009.
  - implication: Every countdown, live price and column of numbers must set fontFeatureSettings = "tnum"; draw up/down deltas with icons, not Unicode arrows (they would fall back to another font).
- **DD19** [high] Roboto and Roboto Flex use tabular digits by default (Roboto Flex only offers pnum to switch to proportional), and Material's default typeface tokens map to FontFamily.SansSerif; Material says Roboto Flex is not yet part of the M3 type scale.
  - evidence: fontTools inspection 2026-11-04: Roboto[wdth,wght] v3.015 and RobotoFlex v3.200 digit advances all equal (1151/1156). androidx tokens/TypefaceTokens.kt: Brand = FontFamily.SansSerif, Plain = FontFamily.SansSerif. m3.material.io Typography (2026-08-28): Roboto Flex "is not yet part of the M3 typescale".
  - implication: If the team prefers Roboto Flex, ticking numbers are already tabular; static display numbers can opt into "pnum" for tighter optics.
- **DD20** [high] Variable fonts cannot be loaded through Compose downloadable Google Fonts; they must be bundled in res/font and configured with FontVariation (weight, width, slant, italic, grade, opticalSizing, custom Setting), which is no longer experimental; Material3 1.5 adds a Typography(fontFamily = ...) constructor that also sets emphasized styles.
  - evidence: developer.android.com/develop/ui/compose/text/fonts (accessed 2026-11-04): "Variable fonts are not currently supported via downloadable fonts."; ui-text-google-fonts 1.12.1; "Google Fonts takes several months to make new fonts available on Android." androidx compose/ui/ui-text/api/current.txt: FontVariation weight/width/slant/italic/grade/opticalSizing/Setting without experimental annotation. material3 1.5.0-beta01 API: ctor Typography(FontFamily fontFamily, TextStyle? displayLarge, ..., displayLargeEmphasized, ...).
  - implication: One bundled Google Sans Flex file, several Font(R.font.google_sans_flex, variationSettings = ...) configurations (text, rounded display), passed through Typography(fontFamily = ...).
- **DD21** [high] Material's typography guidance: use tabular figures where values change often (clocks); use emphasized styles for selection, actions, headlines; Material components do not apply emphasized styles by default; display styles are for short important text or numerals.
  - evidence: m3.material.io Typography (2026-08-28): "Use tabular figures (also known as monospaced numbers) rather than proportional digits in tables or places where values may change often, such as clocks."; "Material recommends using emphasized styles for selection, actions, headlines, and other editorial treatments."; "Material components don't use emphasized type styles by default."; display styles "are reserved for short, important text or numerals"; "avoid using [wider styles] for areas with limited space, such as in a top app bar". MDC docs/theming/Typography.md (2025-04-30) emphasized table (e.g. displayLarge Medium 57sp, titleMedium Bold 16sp).
  - implication: Hero countdown = displayLargeEmphasized + tnum; Arm button, selected filters and state labels use emphasized label styles explicitly.
- **DD22** [medium] A data-viz method used here recommends proportional figures for big static numbers (hero figures, stat-tile values) and tabular only for columns, one hero figure per view, a 2 px sparkline with an 8 px end dot and ~10% area wash, and a stat tile of label, value, signed delta and optional sparkline.
  - evidence: dataviz skill references marks-and-anatomy.md (bundled agent skill, read 2026-11-04): "Proportional figures for big numbers; tabular only in columns"; "Hero figure ... Exactly one per view"; Line "2px"; Marker "r >= 4"; Area fill "~10% opacity"; stat tile contract label/value/delta/trend.
  - implication: Two numeral styles: tnum for anything that ticks or aligns (countdowns, live price, lists), default pnum for static results (Won at 44.00, stats tiles).
- **DD23** [high] An actively maintained open-source M3E timer app (nsh07/Tomato, commit 2026-10-18) already uses these exact patterns: MaterialExpressiveTheme with dynamic color and a black theme, bundled Google Sans Flex with ROND 100 for display styles, a tnum timer text with TextAutoSize.StepBased, CircularWavyProgressIndicator, motionScheme specs, and a Live Update ProgressStyle with setShortCriticalText.
  - evidence: /var/tmp/sniper-research/src/nsh07_Tomato/repo (git clone --depth 1, HEAD 98fa69e 2026-10-18): shared/.../ui/theme/Type.kt FontVariation.Setting("ROND", 100f); ui/timerScreen/TimerMainPane.kt fontFeatureSettings = "tnum", letterSpacing -2.6.sp, autoSize = TextAutoSize.StepBased, CircularWavyProgressIndicator(stroke 16.dp, wavelength 60.dp, gapSize 8.dp); androidApp/.../service/TimerService.kt NotificationCompat.ProgressStyle + addProgressSegment + setWhen + setShortCriticalText; AndroidManifest USE_EXACT_ALARM, POST_PROMOTED_NOTIFICATIONS.
  - implication: Proven reference for our countdown typography, ring styling and Live Update code; read only, do not copy GPL code verbatim.
- **DD24** [high] Among serif candidates on Google Fonts, Newsreader has tabular digits by default (with tnum/pnum), while Fraunces and Instrument Serif have no tnum feature.
  - evidence: fontTools inspection 2026-11-04 of google/fonts ofl/newsreader Newsreader[opsz,wght].ttf (digits all 1100 units; features pnum, tnum), ofl/fraunces Fraunces[SOFT,WONK,opsz,wght].ttf (proportional, no tnum), ofl/instrumentserif InstrumentSerif-Regular.ttf (proportional, no tnum).
  - implication: If the editorial style (O3) is chosen, use Newsreader for prices, or keep all numbers in the sans.
- **DD25** [high] Material distinguishes user-generated dynamic color (from the wallpaper, Android 12+) from content-based color (from in-app imagery), and says content-based color is best for contained elements next to the source image.
  - evidence: m3.material.io Dynamic color (2026-07-17): "Content-based color is best used for contained screen elements adjacent to the source image". developer.android.com Compose M3 page: "Dynamic color is available on Android 12 and above" with dynamicLightColorScheme/dynamicDarkColorScheme. MDC docs/theming/Color.md: DynamicColorsOptions.Builder().setContentBasedSource(bitmap).
  - implication: Wallpaper color for the app chrome; optional photo-derived scheme only on the item detail header and the result screen.
- **DD26** [high] MDC-Android provides harmonization helpers that shift a reserved color toward the theme primary: MaterialColors.harmonize(colorToHarmonize, colorToHarmonizeWith), harmonizeWithPrimary(context, color), and getColorRoles(color, isLightTheme) for accent/container roles.
  - evidence: material-components-android lib/java/com/google/android/material/color/MaterialColors.java (master, read 2026-11-04) lines 264-303; docs/theming/Color.md: "Color harmonization solves the problem of 'How do we ensure any particular Reserved color (eg. those used for semantic or brand) looks good next to a user's dynamically-generated color?'"
  - implication: Harmonize only container tints, and re-validate color-blind separation after harmonizing because it moves hues closer together.
- **DD27** [high] Android 17 (API 37) adds semantic coloring for Live Updates: green = safe, orange = caution, red = danger, blue = informational, via Notification.SEMANTIC_STYLE_* and Notification.createSemanticStyleAnnotation(); the official sample guards it with Build.VERSION_CODES.CINNAMON_BUN.
  - evidence: developer.android.com/about/versions/17/features 'Live Update - Semantic color API' (accessed 2026-11-04): "Green: Associated with safety", "Orange: For designating caution", "Red: Generally indicates danger, stop", "Blue: Neutral color for content that is informational". android/platform-samples samples/user-interface/live-updates/.../SnackbarNotificationManager.kt (last commit 2026-07-09).
  - implication: Use the same meanings in-app: leading/won = safe, over max/outbid = caution, bid failed/session expired = danger, armed/info = informational; never red for 'ending soon'.
- **DD28** [high] A naive Material-tone state palette fails color-blind separation (danger vs caution delta E 1.8 under deuteranopia in light mode); a tuned set reaches the legal floor only with secondary encoding.
  - evidence: Validator from the bundled dataviz skill (scripts/validate_palette.js, run 2026-11-04, OKLab x100, Machado 2009 severity 1.0). Light #146C2E/#8A5100/#B3261E/#6750A4: CVD fail 1.8, normal 11.2. Tuned light good #005F52, caution #9C6300, danger #B3105F, info #4057B8: CVD floor 7.5 (protan), normal-vision 18.4 (pass), min contrast 4.75:1 on #FEF7FF. Tuned dark #55D6BE/#E9B949/#F27C9E/#8FB3FF: CVD floor 6.4 (deutan), normal 16.3 (pass), min contrast 7.22:1 on #141218.
  - implication: State color must always be paired with an icon and a text label; use the tuned hexes as the fixed semantic set; never rely on green vs red alone.
- **DD29** [high] Carbon's status-indicator guidance: do not rely on color alone, use at least two of color, shape or symbol, keep to about five or six statuses, and give differential indicators a sign, caret or arrow.
  - evidence: carbondesignsystem.com/patterns/status-indicator-pattern/ (last updated 2026-08-12): "Relying solely on color to convey status is insufficient"; "having more than five or six indicators can overwhelm users"; differential indicators "must have either a '+' or '-' sign, a caret, or an arrow icon".
  - implication: Collapse snipe states into at most six visible families; price deltas always carry a sign plus an icon.
- **DD30** [high] eBay's own design system encodes 'time-sensitive' signals with its attention (red) color.
  - evidence: github.com/eBay/skin src/sass/signal/signal.scss (repo archived, last push 2025-04-28): .signal--time-sensitive uses color-foreground-attention; src/tokens/evo-light.css --color-foreground-attention: var(--color-red-600) = #d50b0b. eBay/evo-web (HEAD 429e478, 2026-11-03) keeps four signal types: "trustworthy, recent, time-sensitive & neutral".
  - implication: The user will see eBay's red 'ending soon' cues inside the WebView; our app deliberately uses calm neutral/primary for time and reserves red for failures, and avoids mimicking eBay branding.
- **DD31** [high] Live Updates must be Standard/BigText/Call/Progress/Metric style, ongoing, titled, non-colorized, not a group summary, on a non-MIN channel, request promotion, and declare POST_PROMOTED_NOTIFICATIONS; they are for ongoing, user-initiated, time-sensitive activities, and events about to start may use them if triggers are tuned to be imminent.
  - evidence: developer.android.com/develop/ui/views/notifications/live-update (accessed 2026-11-04): requirements list; "Use Live Updates for activities that are ongoing, user-initiated, and time-sensitive."; "events that are about to start can use a Live Update"; "apps must tune their triggers to only appear when the activity is imminent"; "Don't repost Live Updates that the user dismissed. Use setDeleteIntent"; APIs Notification.hasPromotableCharacteristics(), NotificationManager.canPostPromotedNotifications(), Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS.
  - implication: Post the Live Update only in the final window (default about 10 minutes, inferred), end it at the result, respect dismissal, and add an Unpin/Hide action.
- **DD32** [high] The Live Update status-bar chip shows a countdown from setWhen when it is at least 2 minutes away ('5min'), can show a ticking timer via setUsesChronometer + setChronometerCountDown, is at most 96 dp wide, and shows short critical text fully only under 7 characters.
  - evidence: developer.android.com Live Updates page, 'When time' and 'Status chip appearance' sections (accessed 2026-11-04): "If the current time is 10:05 am and the when time is set to 10:10 am, then the chip says 5min"; "The Chronometer timer is shown in the chip as long as it is positive"; "The chip has a maximum width of 96dp"; "If less than 7 characters, show the whole text."
  - implication: setWhen(bidAtMillis) with chronometer countdown for the final window; setShortCriticalText("Bidding") during the bid and "Won"/"Lost" briefly at the end.
- **DD33** [high] Notification.ProgressStyle (API 36) supports colored segments, points, tracker/start/end icons, indeterminate start and styled-by-progress; the official sample uses short critical text ('Placing', 'Prepping', 'Arrived'), an indeterminate first state, a chronometer countdown and an IMPORTANCE_DEFAULT channel.
  - evidence: developer.android.com/reference/android/app/Notification.ProgressStyle (Added in API level 36): addProgressSegment, addProgressPoint, setProgressTrackerIcon, setProgressStartIcon, setProgressEndIcon, setProgressIndeterminate, setStyledByProgress; class doc: suggested for "food being delivered". android/platform-samples live-updates SnackbarNotificationManager.kt (2026-07-09): NotificationCompat.ProgressStyle, setRequestPromotedOngoing(true), setUsesChronometer(true), setChronometerCountDown(true).
  - implication: Model the final window as segments (monitoring, final minute) with a point at the bid moment and a gavel/target tracker icon; start indeterminate while checking the session.
- **DD34** [high] Notification.MetricStyle (API 37, Android 17) shows up to 3 metrics (value + label of 10 characters or fewer), each with an optional semantic style; the critical metric may be shown in the status-bar chip; the style ignores the large icon.
  - evidence: developer.android.com/reference/android/app/Notification.MetricStyle and Notification.Metric (Added in API level 37): "A notification style which shows up to 3 metrics when expanded"; label "should be 10 characters or fewer"; value types FixedDate, FixedFloat, FixedInt, FixedText, FixedTime, TimeDifference; semanticStyle SEMANTIC_STYLE_INFO/SAFE/CAUTION/DANGER; setCriticalMetric(index).
  - implication: On Android 17 the final-window Live Update can show 'Bids in' (countdown), 'Price' (SAFE/CAUTION vs max) and 'Your max'; fall back to ProgressStyle on 36.
- **DD35** [high] Notification copy rules: title under 29 characters, collapsed body under 40, expanded under 80, 1-2 short buttons; name the day of the week instead of 'today/tomorrow'; no emoji on bad news; large icon square unless it shows a person; do not disguise unimportant notifications as urgent.
  - evidence: m3.material.io Content design > Notifications (2026-07-17): "Title: <29 chars Collapsed body: <40 chars Expanded body: <80 chars"; "Be day-specific ... About 20% of users don't see notifications on the day they're sent"; "don't use emoji to accentuate bad news". developer.android.com/design/ui/mobile/guides/home-screen/notifications: "Large icons must be circular when showing a person, but square in all other cases."; "When an unimportant notification is disguised as urgent, it can produce unnecessary alarm."
  - implication: Templates like 'Won · Canon AE-1' / 'Paid £85.00, £35 under your max'; 'Ends Sun 21:43' rather than 'tomorrow'; item photo as square large icon.
- **DD36** [high] Android's notification design guide cites Google Clock's running-timer notification as the model for letting users act from the notification (pause, add a minute).
  - evidence: developer.android.com/design/ui/mobile/guides/home-screen/notifications (accessed 2026-11-04): "The Google Clock app shows a timer running, but it lets the user pause or add a minute directly from the notification."
  - implication: Countdown notification actions: 'Raise max' (inline numeric reply, inferred) and 'Cancel snipe'; tap opens the item detail.
- **DD37** [high] Apple's Live Activities guidance (cross-platform best practice): only for activities with a defined start and end, update only when content changes, alert only for essential updates, end immediately when the event ends, keep result visible briefly (15-30 minutes usually adequate), avoid sensitive info on the lock screen.
  - evidence: developer.apple.com/tutorials/data/design/human-interface-guidelines/live-activities.json (accessed 2026-11-04): "Alert people only for essential updates that require their attention."; "Always end a Live Activity immediately when the task or event ends"; "In most cases, 15 to 30 minutes is adequate."; "a sports app might use numeric content transitions for score changes".
  - implication: One alert at the result, not during the countdown; replace the ongoing Live Update with a dismissible result notification; consider VISIBILITY_PRIVATE public text.
- **DD38** [high] Flighty (Apple Design Award 2023 winner, Interaction; iOS/macOS only) is praised for status at a glance using airport conventions, and pairs relative with absolute times in status lines.
  - evidence: developer.apple.com/design/awards/2023/: "a look that mirrors time-honored airport design conventions", "With key information where you need it most", "incredible Live Activities and Dynamic Island integration". flighty.com (accessed 2026-11-04) sample line "Taxiing for 13m, for take off at 5:34PM." and "Delayed 45m"; en.wikipedia.org/wiki/Flighty: iOS and macOS.
  - implication: Always show 'in 2 h 14 m' together with 'bids at 21:43:52 Sun'; express changes as deltas from the plan ('£4.50 above your max').
- **DD39** [medium] Calm-technology principles and NN/g's deceptive-pattern checklist support designing urgency that informs without pressure.
  - evidence: calmtech.com (Amber Case, principles undated): "Technology should require the smallest possible amount of attention", "Technology should inform and create calm", "Technology should make use of the periphery", "Technology should work even when it fails". nngroup.com/articles/deceptive-patterns/ (Maria Rosala, 2023-12-01): "Are users rushed into making a decision?"
  - implication: Default to calm, peripheral status (chip, list text); escalate precision only near the end; no flashing, no red timers, no alarmist copy.
- **DD40** [high] Android haptics guidance: less is more; prefer HapticFeedbackConstants/clear haptics, match importance to strength, avoid buzzy or legacy one-shot vibrations; Compose HapticFeedbackType includes Confirm, Reject, SegmentTick, SegmentFrequentTick, ToggleOn, ToggleOff, GestureThresholdActivate, LongPress.
  - evidence: developer.android.com/develop/ui/views/haptics/haptics-principles: "When it comes to haptic feedback on mobile devices, less is more."; "Given the choice of buzzy haptics or no haptics for touch feedback, choose no haptics." androidx compose/ui/ui/api/current.txt HapticFeedbackType getters.
  - implication: Confirm on arming and on a won result, Reject on a failed bid; no per-second ticking haptics.
- **DD41** [high] Exact timing permissions: USE_EXACT_ALARM is granted automatically and cannot be revoked (limited by Play policy, irrelevant for sideloading); SCHEDULE_EXACT_ALARM is denied by default for fresh installs targeting 13+; setAlarmClock alarms are never adjusted and leave low-power modes; allow-while-idle alarms fire at most once per nine minutes in Doze.
  - evidence: developer.android.com/develop/background-work/services/alarms/schedule: USE_EXACT_ALARM "Granted automatically", "Cannot be revoked by the user"; "The SCHEDULE_EXACT_ALARM permission is not pre-granted to fresh installs of apps targeting Android 13"; setAlarmClock "the system never adjusts their delivery time"; ACTION_REQUEST_SCHEDULE_EXACT_ALARM; canScheduleExactAlarms(). developer.android.com/training/monitoring-device-state/doze-standby: "Neither setAndAllowWhileIdle() nor setExactAndAllowWhileIdle() can fire alarms more than once per nine minutes, per app."
  - implication: For the sideloaded build, declare USE_EXACT_ALARM so onboarding skips the alarms prompt; readiness check still verifies alarms are registered; final-window monitoring cadence must respect Doze limits (technical agent).
- **DD42** [high] Other background readiness hazards have documented checks and settings intents: battery optimization (isIgnoringBatteryOptimizations, ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS), restricted standby bucket (getAppStandbyBucket() == STANDBY_BUCKET_RESTRICTED), and hibernation after a few months unused, which stops background alerts and is not reset by scheduling alarms.
  - evidence: doze-standby page: isIgnoringBatteryOptimizations(), ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. developer.android.com/about/versions/12/behavior-changes-all: restricted bucket check. developer.android.com/topic/performance/app-hibernation: "Your app can't run jobs or alerts from the background"; non-examples of usage include "Schedules alarms"; Android 13+ option "Pause app activity if unused"; PackageManagerCompat.getUnusedAppRestrictionsStatus(), IntentCompat.createManageUnusedAppRestrictionsIntent() with startActivityForResult().
  - implication: The Ready to snipe screen lists each hazard with a one-tap Fix; especially 'Pause app activity if unused' for a rarely opened personal app.
- **DD43** [high] Notification permission should be requested in context after the user understands the value; Android 13+ installs start with notifications off.
  - evidence: developer.android.com/develop/ui/views/notifications/notification-permission: "Before you ask users to grant any permissions, let them familiarize themselves with your app."; examples include "The user submits an order for food delivery."; "notifications are off by default". Android notification design guide: "Describe the benefits notifications provide, and the result of not granting permissions"; "Don't show the notification permission dialog, if the user has dismissed the UI."
  - implication: Ask for notifications when the user arms the first snipe ('Get told when you win or get outbid'), not at first launch.
- **DD44** [high] The app can detect a URL on the clipboard without reading it (no 'pasted from your clipboard' toast) via getPrimaryClipDescription(), which can classify URLs with getConfidenceScore().
  - evidence: developer.android.com/about/versions/12/behavior-changes-all 'Clipboard access notifications': toast on getPrimaryClip(); "When your app calls this method [getPrimaryClipDescription], the system doesn't show a toast message."; "Different classifications of text, such as URLs, using getConfidenceScore()".
  - implication: Show a 'Paste eBay link' suggestion chip only when a URL is present; read the clip only after the tap.
- **DD45** [high] Web links to a domain open in only one app; on Android 12+ unverified links open in the browser unless the user adds the domain under 'Open by default', so the eBay app (if installed) will keep eBay links.
  - evidence: developer.android.com/training/app-links/verify-android-applinks: "On a given device, only one app at a time can be associated with a particular domain."; ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, 'Add link'. developer.android.com/about/versions/12/behavior-changes-all: "a generic web intent resolves to an activity in your app only if your app is approved for the specific domain".
  - implication: Primary entry points are the share sheet (ACTION_SEND text/plain) and paste; link capture is an optional advanced setting.
- **DD46** [high] Empty states should communicate system status, teach in context and offer direct paths to the key task.
  - evidence: nngroup.com/articles/empty-state-interface-design/ (Kate Kaplan, 2021-09-19): headings "Use Empty States to Communicate System Status", "Use Empty States to Provide Learning Cues", "Use Empty States to Provide Direct Pathways for Key Tasks".
  - implication: Empty home: 'No snipes yet' + 'Share any eBay listing to this app' explainer + Paste link button; loading state never shows 'no results'.
- **DD47** [medium] Pixel 9a: 6.3-inch OLED, 1080x2424 (about 422 ppi), 60-120 Hz, Tensor G4, 8 GB, launched on Android 15 (2025-04-10), now on Android 17 (Android 17 released around 2026-06-16).
  - evidence: en.wikipedia.org/wiki/Pixel_9a (accessed 2026-11-04): "Current: Android 17". androidcentral.com 'Android 17 is finally here' (search snippet dated 2026-06-16). Compact width about 411 dp at default display size is inferred from 1080 px at 420 dpi.
  - implication: Design for compact width (about 360-430 dp), smooth 120 Hz motion, OLED-friendly dark theme, and target the Android 16 ProgressStyle plus Android 17 MetricStyle/semantic colors.
- **DD48** [medium] Google's own 2025 M3E app redesigns use list containers (Gmail, Messages), a wavy progress indicator for backup (Photos), a FAB menu for creation (Calendar), a contained loading indicator and floating toolbar (Fitbit), and shape-morphing tabs (Google Home); Clock 8.1 got a shorter bottom bar, new font and rounded-square FAB.
  - evidence: 9to5google.com/2025/11/17/google-material-3-expressive-redesign/ (Abner Li, 2025-11-17).
  - implication: These patterns are already familiar on the user's Pixel; reuse them rather than invent new ones.
- **DD49** [medium] Competitor sniper apps' screenshots show the design baseline to beat: equal-weight label:value forms (Gixen 'Status (main): SCHEDULED', 'Offset (main): 8'), tiny colored time badges and side stripes on a dark skeuomorphic list (bidbag), a bare table-like import form (Baytomat), and an account/time-zone sign-up gate (Auction Sniper).
  - evidence: /var/tmp/sniper-research/screens/com.gixen.gixen/shot01.png, com.bidbag.mobile/shot01.png, com.baytomat2/shot02.jpg, com.auctiva.auctionsniper/shot02.png (Play listing screenshots collected 2026-11-04 by another agent; capture dates unknown).
  - implication: Lead with hierarchy (one big number per item), plain-language states, no account sign-up, device time zone, and a single dominant action.
- **DD50** [high] Dribbble, Behance and Mobbin were not usable references from this environment; an archived Dribbble 'auction' tag page (2024-03-05) is dominated by NFT and real-estate auction concepts.
  - evidence: WebFetch dribbble.com/search/auction-app and /tags/auction-app returned empty bodies; behance.net search and mobbin.com returned HTTP 403 (2026-11-04). web.archive.org/web/20240305090112/https://dribbble.com/tags/auction listed shots like 'NFT Auction Page-Light Version', 'Auction Mobile IOS App' (Purrweb), 'Art Auction App', 'Auction House - Artwork Bidding'.
  - implication: Base the direction on platform guidance and proven adjacent apps; treat 'what makes good auction shots good' as inferred principles.

## Open questions
- Does the user prefer wallpaper-derived dynamic color, or a fixed brand palette that stays the same whatever the wallpaper?
- Light, dark or follow-system by default? Does the user want a pure-black option on the Pixel 9a OLED?
- Does the user own a Wear OS watch? Live Updates bridge to some watches, which would make a watch-friendly countdown worth designing.
- How many snipes are usually live at once? A few favours the roomy O1/O3 layouts; dozens favours O2-style density.
- Should the screens show technical detail (the bid log with millisecond timings, the measured clock offset and latency), or hide it under a diagnostics view?
- Is Material3 1.5.0-beta02 acceptable for a personal app, or should we wait for 1.5.0 stable? LoadingIndicator and MaterialShapes are still experimental.
- How many minutes before the bid should the final-countdown Live Update start: 5, 10, 15 or 30? Default proposed: about 10.
- Which exact-alarm permission will the technical design use? USE_EXACT_ALARM removes an onboarding step for the sideloaded build; SCHEDULE_EXACT_ALARM needs a prompt and a readiness row.
- Should result notifications (won/lost/failed) break through Do Not Disturb, for example via an alarm category or channel settings?
- Should we bundle the full 3.9 MB Google Sans Flex file or a Latin-only subset? Or use Roboto Flex, whose digits are tabular by default and which keeps a grade axis?
- Is a home-screen 'Next snipe' widget wanted? It needs RemoteViews Chronometer interop because Glance has no chronometer.
- Which SplitButton API is final in 1.5: SplitButtonLayout (present in the beta01 API) or SplitButton (named in the alpha25 release note)? Verify at build time.

## Report
# Design direction: best-in-class Android eBay sniper for a Pixel 9a (research date 2026-11-04)

Scope: visual and interaction design direction only. Technical feasibility of bidding, time sync, monitoring cadence and eBay rules belong to other agents; where this report depends on them it says so. No eBay host was contacted. Items marked **(inferred)** are my design judgement or unverified recollection, not something I read.

---

## 0. Recommendation in one screen

1. **Base system:** Material 3 Expressive in Compose. Pin `androidx.compose.material3:material3:1.5.0-beta02`. The Compose BOM `2026.09.00` still maps material3 to `1.4.0`, which has no expressive APIs.
2. **Typeface:** bundle **Google Sans Flex** (OFL).
   - Rounded (`ROND` 100) semibold for display numerals and headlines.
   - `ROND` 0 for body text.
   - Every number that ticks or aligns gets `fontFeatureSettings = "tnum"`.
3. **Color:**
   - Wallpaper dynamic color for surfaces and actions.
   - A **fixed, validated semantic state set** (good, caution, danger, info, neutral), always with an icon and a label.
   - Red only for failures, never for "ending soon".
4. **One hero moment:** the final minute, then the bid, then the result. Everything else is calm and uses standard, quiet motion.
5. **Notifications:**
   - A Live Update (promoted ongoing, chronometer countdown chip) only in the final window, about 10 minutes **(inferred default)**.
   - Results as normal notifications.
   - On Android 17, use semantic colors and `MetricStyle`.
6. **Trust surfaces:** a "Ready to snipe" check with one-tap fixes, a visible plan ("Bids £48.00 at 21:43:52, 6 s before end") and a per-snipe bid log.
7. **Style:** **O1 Pixel-native Expressive** as the base, plus O2's "monochrome until something needs attention" precision for times and prices, plus O3's photo-forward hero and photo-derived color on item detail and result only.

---

## 1. Platform baseline (verified)

| Item | Value | Source (date) |
|---|---|---|
| Device | Pixel 9a: 6.3-inch OLED, 1080 x 2424, about 422 ppi, 60-120 Hz, Tensor G4, 8 GB. Launched on Android 15 (2025-04-10); "Current: Android 17" | en.wikipedia.org/wiki/Pixel_9a (accessed 2026-11-04) |
| Android 17 | Released around 2026-06-16 | androidcentral.com "Android 17 is finally here" (search snippet dated 2026-06-16) |
| Width class | About 411 dp at default display size **(inferred: 1080 px / 2.625)**. Design for 360-430 dp | n/a |
| Compose | ui/foundation/animation/runtime 1.12.1 stable. Shared transitions stable since animation 1.10.0 (2025-12-03) | Google Maven `compose-bom-2026.09.00.pom`; compose-animation release notes |
| Material3 | 1.4.0 stable (2025-09-24), no expressive APIs. **1.5.0-beta02 (2026-10-21)** carries M3E | developer.android.com/jetpack/androidx/releases/compose-material3 |
| Shapes lib | `androidx.graphics:graphics-shapes:1.1.0` (2025-10-22) for `RoundedPolygon`/`Morph` | developer.android.com/jetpack/androidx/releases/graphics |
| targetSdk 36 behavior | Edge-to-edge cannot be opted out. Predictive back system animations are on by default; `onBackPressed` is not called | developer.android.com/about/versions/16/behavior-changes-16 |
| Android 16 / 17 notification APIs | `Notification.ProgressStyle` (API 36). `Notification.MetricStyle` and semantic colors (API 37) | developer.android.com references |

From the Material3 release notes:
- 1.4.0-beta01 (2025-07-30): "All public APIs tagged with `ExperimentalMaterial3ExpressiveApi` or `ExperimentalMaterial3ComponentOverrideApi` have been removed, please switch to `1.5.0-alpha` to continue enjoying these features."
- An m3.material.io post (updated 2026-07-08) says: "When 1.4.0 goes to stable, the Material Expressive APIs will move to the next alpha (1.5.0-alphaX) ... The APIs will go stable in the 1.5.0 release."

---

## 2. Material 3 Expressive (M3E)

### 2.1 What it is (quotes)

- developer.android.com/develop/ui/compose/designsystems/material3 (accessed 2026-11-04): "Jetpack Compose offers an implementation of Material You and Material 3 Expressive, the next evolution of Material Design." It also says: "It also supports Material You personalization features like dynamic color. M3 Expressive complements the Android 16 visual style and system UI."
- m3.material.io "Start building with Material 3 Expressive" (posted 2025-05-13, content updatedTimestamp 2026-07-16; read via `https://m3.material.io/_dsm/content/m3/2026-10-13_06-10-19/dd671163-5c9d-42c2-9737-048c191ed507.json`):
  - "Material 3 Expressive is an evolution of the Material 3 design system. It's a set of new features, updated components, and design tactics for creating emotionally impactful UX."
  - "M3 Expressive isn't a new version of the system. We're not deprecating M3, and this isn't 'M4.'"
  - "Fourteen new or updated components". Listed as: App bars, Button groups (new), Common buttons, Extended FAB, FAB menu (new), FABs, Icon buttons, Loading indicator (new), Navigation bar, Navigation rail, Progress indicators, Sliders, Split button (new), Toolbars (new).
  - Expressive styles:
    - Motion physics: "Spatial springs mirror the physics of how objects actually move" and "Effects springs create seamless transitions for color and opacity changes".
    - Emphasized typography: "draws attention to important actions".
    - Shapes: "35 shapes", with "A built-in shape-morph animation".
    - Color: "Vibrant color schemes".

### 2.2 Research claims and their limits

- m3 blog: "46 studies with more than 18,000 participants"; "participants spotting key UI elements up to four times faster in expressive screens"; "Expressive designs are preferred by people of all ages."
- design.google "Expressive Design: Google's UX Research" (undated page, 2025):
  - "The fundamental parts of expressive design are the use of color, shape, size, motion, and containment."
  - "No amount of expressive design will beat basic functionality."
  - Removing text labels from email actions "resulted in decreased usability".
  - "By making the Send button larger and more prominent, participants were able to spot the button four times faster."

### 2.3 The seven tactics (m3 blog, verbatim headings) and what they mean for us

| Tactic | Our use |
|---|---|
| "1. Use a variety of shapes". Note: "Smaller shapes can result in essential actions looking less important." | Large rounded hero card. Pill-shaped primary buttons. Square-ish thumbnails. |
| "2. Apply rich and nuanced colors" | `primaryContainer` hero. Tonal containers for groups. Semantic colors only for state. |
| "3. Guide attention with typography" | Emphasized display numerals for the countdown. Emphasized labels on the main action. |
| "4. Contain content for emphasis" | Segmented list containers per end-time bucket. Grouped snipes in one container. |
| "5. Add fluid and natural motion" | Spring-driven state changes. Digit roll in the final minutes only. |
| "6. Leverage component flexibility" | Toolbar or FAB depending on screen. Flexible top app bar with subtitle. |
| "7. Combine tactics to create hero moments" | **One** hero sequence: the final minute, the bid, the result. |

Hero rule, verbatim: "Hero moments are brief, delightful, surprising, and unexpected. Stick to one or two hero moments in your product; too many moments can be overwhelming or distracting."

### 2.4 Component inventory (Compose API names; status read from `compose/material3/material3/api/1.5.0-beta01.txt` in the androidx GitHub mirror, 2026-11-04)

| Component | Compose API | Opt-in needed? | Material guidance (quote, page date) | Where in our app |
|---|---|---|---|---|
| Expressive theme | `MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography, content)`, `expressiveLightColorScheme()` (KDoc: "The default color scheme for MaterialExpressiveTheme. For dark mode, use darkColorScheme.") | No (promoted 1.5.0-alpha18) | n/a | App root |
| Motion | `MotionScheme.expressive()`, `MotionScheme.standard()`, `MaterialTheme.motionScheme.{fast,default,slow}{Spatial,Effects}Spec<T>()` | No (1.5.0-alpha15) | "Expressive is Material's opinionated motion scheme, and should be used for most situations, particularly hero moments and key interactions." (Motion physics, 2026-07-17) | Everywhere; standard inside settings/lists |
| Button groups | `ButtonGroup(overflowIndicator, ...)` with `ButtonGroupScope.clickableItem/toggleableItem/customItem`, `Modifier.animateWidth`; connected rows via `ToggleButton` + `ButtonGroupDefaults.connectedLeadingButtonShapes()/connectedMiddleButtonShapes()/connectedTrailingButtonShapes()`, `ButtonGroupDefaults.ConnectedSpaceBetween` | No (1.5.0-alpha22) | "Connected button groups replace the segmented button" (2026-09-16) | Bid-offset presets; History filter; site picker |
| Toggle buttons | `ToggleButton`, `FilledTonalToggleButton`, `OutlinedToggleButton`, `ElevatedToggleButton`, `ToggleButtonDefaults.shapesFor(...)` | No (1.5.0-alpha19) | n/a | Inside connected groups |
| Buttons (sizes) | `ButtonDefaults.ExtraSmall/Small/Medium/Large/ExtraLargeContainerHeight`, `ButtonDefaults.shapesFor(height)`, `contentPaddingFor`, `textStyleFor`. Heights 32/40/56/96/136 dp (tokens) | No (1.5.0-alpha19) | n/a | "Arm snipe" at 56 or 96 dp |
| Split button | `SplitButtonLayout(leadingButton, trailingButton, ...)` + `SplitButtonDefaults.LeadingButton/TrailingButton` (Tonal/Outlined/Elevated variants), `leadingButtonShapesFor(dp)` | No (1.5.0-alpha20). alpha25 notes say "Deprecated SplitButtonLayout Api to use SplitButton instead", but beta01 exposes no `SplitButton(`, so re-check | "The trailing button should always have a menu icon." (2026-09-08) | "Arm snipe ▾" (Watch only / Arm and open listing) |
| FABs | `ExtendedFloatingActionButton`, `MediumExtendedFloatingActionButton`, `LargeExtendedFloatingActionButton`; FAB 56/80/96 dp | No | n/a | "New snipe" Extended FAB on Home |
| FAB menu | `FloatingActionButtonMenu(expanded, button, ...)`, `ToggleFloatingActionButton` | No (1.5.0-alpha19) | "FAB menus can contain 2-6 items"; "Don't open a FAB menu from an extended FAB" (2026-07-17) | **Not used** (we use an Extended FAB) |
| Floating toolbar | `HorizontalFloatingToolbar(expanded, floatingActionButton, ...)`, `VerticalFloatingToolbar`, `FloatingToolbarDefaults.ScreenOffset`, `vibrantFloatingToolbarColors()`, `exitAlwaysScrollBehavior()` | No (1.5.0-alpha22) | "Don't show at the same time as a navigation bar"; "Use the vibrant color style for greater emphasis" (2026-07-28) | Item detail actions |
| Loading indicator | `LoadingIndicator(...)`, `ContainedLoadingIndicator(...)` (morphs `RoundedPolygon` list) | **Yes** (`@ExperimentalMaterial3ExpressiveApi`; promotion reverted in alpha19) | "never simply decorative"; "Not used for processes that transition from indeterminate to determinate" (2026-07-29) | Preview fetch; bid in flight; pull-to-refresh |
| Wavy progress | `CircularWavyProgressIndicator(progress, ..., amplitude, wavelength, waveSpeed)`, `LinearWavyProgressIndicator` | No (1.5.0-alpha18) | "The wavy shape can make longer processes feel less static"; "Don't add progress indicators to every activity." (2026-07-29) | Hero ring only; add-flow stepper |
| Shapes | `MaterialShapes.*` (35), `RoundedPolygon.toShape()`, `Morph.toPath(progress)` | **Yes** | "Shape is versatile, not semantic"; "Use abstract shapes sparingly" (2026-07-17) | Photo crops, result celebration, empty states |
| Lists | `SegmentedListItem(shapes, ...)`, `ListItemDefaults.segmentedShapes(index, count)`, `segmentedColors()` | No (1.5.0-alpha23) | "Added expressive list Recommended for new designs" (Dec 2025; page 2026-08-06) | All lists |
| App bars | `MediumFlexibleTopAppBar(title, subtitle, ...)`, `LargeFlexibleTopAppBar`, `TwoRowsTopAppBar`, `AppBarRow`, `FlexibleBottomAppBar` | No (1.5.0-alpha23) | Docked toolbar replaces bottom app bar | Home top bar with subtitle "3 armed · next in 2 h 14 m" |
| Navigation | `ShortNavigationBar`, `WideNavigationRail` | No | n/a | Snipes / History |
| Carousel | `HorizontalMultiBrowseCarousel`, `HorizontalCenteredHeroCarousel(state, preferredItemWidth, ...)` (renamed param in 1.5.0-beta01) | No | n/a | Item photos on detail |
| Typography | `Typography(fontFamily = ..., ...)` plus 15 `*Emphasized` styles | No | "Material components don't use emphasized type styles by default." (2026-08-28) | See section 3 |
| Navigation transitions (upcoming) | `FadeThroughNavigationTransition`, `HorizontalSharedAxisNavigationTransition` with `predictiveBackTransform` | Only in androidx-main after beta02 (commit 2026-10-29) | n/a | Later; use shared bounds now |

Release-note promotions (1.5.0 alphas):
- motion scheme: alpha15, 2026-02-25
- `materialExpressTheme`, `expressiveLightColorScheme` and wavy progress: alpha18, 2026-04-22
- toggle buttons, menus, FAB and FAB menu, buttons: alpha19, 2026-05-06
- `SplitButton`: alpha20, 2026-05-19
- `FloatingToolbar` and `ButtonGroup`: alpha22, 2026-06-17
- list items, top app bars and flexible bottom app bar: alpha23, 2026-07-01

### 2.5 Motion physics

Quotes:
- m3.material.io Motion physics system (2026-07-17): "Expressive is Material's opinionated motion scheme, and should be used for most situations, particularly hero moments and key interactions." "Standard feels more functional with minimal bounce, and should be used for utilitarian products."
- Same page, "Level 3: Swap the default motion scheme per element". In Compose 1.5 the way to do this is a nested `MaterialTheme(motionScheme = MotionScheme.standard()) { ... }`, because `LocalMotionScheme` was removed in 1.5.0-alpha27. The `MaterialTheme(colorScheme, motionScheme, shapes, typography, content)` signature exists in the beta01 API.
- KDoc (androidx MotionScheme.kt): "The expressive scheme is Material's recommended motion scheme for prominent UI elements and hero interactions."

Spring tokens (androidx `tokens/ExpressiveMotionTokens.kt`, `StandardMotionTokens.kt`):

| Token | Expressive (damping / stiffness) | Standard |
|---|---|---|
| Fast spatial | 0.6 / 800 | 0.9 / 1400 |
| Default spatial | 0.8 / 380 | 0.9 / 700 |
| Slow spatial | 0.8 / 200 | 0.9 / 300 |
| Fast effects | 1.0 / 3800 | 1.0 / 3800 |
| Default effects | 1.0 / 1600 | 1.0 / 1600 |
| Slow effects | 1.0 / 800 | 1.0 / 800 |

Web curve equivalents (same m3 page):
- Expressive fast spatial `0.42, 1.67, 0.21, 0.90`, 350 ms
- Expressive default spatial `0.38, 1.21, 0.22, 1.00`, 500 ms
- Expressive slow spatial `0.39, 1.29, 0.35, 0.98`, 650 ms

Usable for prototypes.

Reduced motion: Compose reads `Settings.Global.ANIMATOR_DURATION_SCALE` in `WindowRecomposer.android.kt`, so "Remove animations" works automatically.

### 2.6 Shape

- Library (beta01 API, 35 shapes): Arch, Arrow, Boom, Bun, Burst, Circle, ClamShell, Clover4Leaf, Clover8Leaf, Cookie12Sided, Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided, Diamond, Fan, Flower, Gem, Ghostish, Heart, Oval, Pentagon, Pill, PixelCircle, PixelTriangle, Puffy, PuffyDiamond, SemiCircle, Slanted, SoftBoom, SoftBurst, Square, Sunny, Triangle, VerySunny.
- Corner tokens: extraSmall 4, small 8, medium 12, large 16, **largeIncreased 20**, extraLarge 28, **extraLargeIncreased 32**, **extraExtraLarge 48** dp, full (androidx `ShapeTokens.kt`).
- Guidance (m3 Shape, 2026-07-17):
  - "Shape morphing should respond to user interaction".
  - Use shape morph for "Actions in progress" and "Changes in the environment, like sound, temperature, or time of day".
  - "Avoid applying unconventional shapes to text-heavy containers."
  - "Use abstract shapes on imagery and decorative UI".
  - Optical roundness: "Outer radius - padding = inner radius".
- Decision: **states are not encoded as shapes.** Shapes appear on:
  - the hero photo crop (for example `Cookie9Sided` when armed **(inferred aesthetic choice)**);
  - the won celebration (morph Circle to `SoftBurst`);
  - the empty-state art;
  - the loading or bidding indicator.

### 2.7 Typography in M3E

From m3.material.io Typography (2026-08-28):
- "M3 type scale has 30 type styles: 15 baseline and 15 emphasized".
- "Use tabular figures (also known as monospaced numbers) rather than proportional digits in tables or places where values may change often, such as clocks." "Use tabular numbers to prevent layout shifting when values change, such as in a clock UI".
- Display styles "are reserved for short, important text or numerals".
- "Material recommends using emphasized styles for selection, actions, headlines, and other editorial treatments." Emphasized styles work well with "Buttons (for primary actions)", "Extended FAB", "Selected list items".
- "The brand typeface is used for larger type styles, like Headline and Display ... The plain typeface is used for smaller type styles". "Avoid changing the type size".
- "Since wider styles take up more space, avoid using them for areas with limited space, such as in a top app bar".

Emphasized defaults from MDC-Android `docs/theming/Typography.md` (2025-04-30): displayLarge Medium 57sp, headlineLarge Medium 32sp, titleMedium Bold 16sp, labelLarge Bold 14sp, and so on. Its definition: "Emphasized styles are used to create hierarchy and are recommended for showing selection, actions, headlines, or other editorial treatments."

### 2.8 Color in M3E

- m3 blog: "Vibrant color schemes ... can be used to sharpen hierarchy and clarify key actions."
- m3 Dynamic (2026-07-17): user-generated color (wallpaper) versus content-based color. "Content-based color is best used for contained screen elements adjacent to the source image".

---

## 3. Typography for this app

### 3.1 Fonts measured (fontTools on google/fonts files, 2026-11-04)

| Font | License | Axes (min, default, max) | Default digits | Numeric features | Missing glyphs we may need | File |
|---|---|---|---|---|---|---|
| Google Sans Flex v5.000 | OFL (public since 2025-11-18; google/fonts commit 2026-10-09) | opsz 1, 18, 144; wdth 25, 100, 151; wght 1, 400, 1000; **ROND 0, 0, 100**; slnt -10, 0, 0. No GRAD in current file (commit 2026-09-10 "remove grade") | **Proportional** | tnum (all digits become 1290 units), pnum, lnum, zero (slashed), frac, sups, subs, ss01, ss02 | U+2191, U+2193, U+2192, U+25B2, U+25BC | 3.9 MB |
| Google Sans v14 | OFL | opsz 17-18, wght 400-700, GRAD -50 to 200 | Proportional | tnum, pnum, case, zero, ss01-ss08 | n/a | 5.0 MB |
| Roboto Flex v3.200 | OFL | 13 axes incl. GRAD, XTRA, YTFI | **Tabular** | pnum only | U+25B2, U+25BC, U+202F | 1.8 MB |
| Roboto v3.015 | OFL | wght 100-900, wdth 75-100 | Tabular | tnum, pnum, onum | arrows, U+202F | 0.5 MB |
| Google Sans Code v6.001 | OFL | wght 300-800 | Monospace | n/a | U+202F, U+2009 | 0.13 MB |
| Newsreader v1.003 | OFL | wght 200-800, opsz 6-72 | Tabular | tnum, pnum | arrows, U+202F, U+2009 | 0.45 MB |
| Fraunces v1.000 / Instrument Serif | OFL | n/a | Proportional | **no tnum** | n/a | n/a |

Facts that drive the design:
- design.google says Google Sans Flex offers "six different design axes: weight, width, optical size, slant, grade, and roundedness". The shipped v5.000 file has five (no grade). **Use Roboto Flex if a grade axis is needed for dark-mode tuning.**
- 9to5google (2025-11-18) says Google Sans Flex is the "base of the 'Rounded' variant" used in M3E. That is `ROND`.
- Trademark (`TRADEMARKS.md`): "Google Sans Flex" may not be used in a product name.
- Compose fonts page: "Variable fonts are not currently supported via downloadable fonts." So bundle the file in `res/font`.
- `FontVariation.weight/width/slant/italic/grade/opticalSizing(textSize)/Setting(tag, value)` are not experimental in the current ui-text API.

### 3.2 Proposed type ramp (Google Sans Flex; sizes stay at M3 defaults)

| Role | Style | Variation | Features | Use |
|---|---|---|---|---|
| Hero countdown | `displayLargeEmphasized` (57sp; auto-shrink via `TextAutoSize.StepBased`) | wght 600-650, ROND 100 | `tnum` | Next-snipe hero, final-minute screen |
| Hero sub-number | `displaySmall` | wght 500, ROND 100 | `tnum` | Live current price on detail |
| Static result figure | `displayMediumEmphasized` | wght 600, ROND 100 | default (`pnum`) | "Won at £44.00", stats hero |
| Headlines | `headlineSmall` / `headlineMediumEmphasized` | wght 500-600, ROND 60-100 **(inferred)** | n/a | Screen titles, group names |
| Titles | `titleMedium` | wght 500, ROND 0 | n/a | Item titles (2 lines max) |
| Body | `bodyMedium` | wght 400, ROND 0, opsz auto | n/a | Descriptions, explanations |
| Labels / chips | `labelLargeEmphasized` for primary actions and selected states; `labelMedium` otherwise | wght 600/500 | n/a | Buttons, state chips |
| Numbers in lists | `titleMedium` / `labelLarge` | wght 500 | `tnum` | Time-left column, price/max column |

Numeral rules:
1. `tnum` for anything that ticks or aligns: countdowns, live price, list columns. Material says so for "places where values may change often, such as clocks".
2. Default proportional digits for big static values: the dataviz guidance says "tabular-nums gives every digit the width of a 0, so a number like 121 looks loose at display sizes".
3. Use a slashed zero (`zero`) only in the bid log or monospace contexts **(inferred)**.
4. Draw up and down deltas with Material Symbols icons, not Unicode arrows; they are missing in Google Sans Flex and Android would substitute a fallback glyph.
5. Format money with the device locale and the listing currency. For a non-GBP item show "US $48.00" with "≈ £37.10" in secondary text **(inferred; formatter API choice belongs to the implementer)**.
6. Times follow the device 12/24-hour setting (`DateFormat.is24HourFormat`, standard API, not re-verified). Show seconds only for the bid moment and in the final 10 minutes.

---

## 4. Color

### 4.1 Dynamic color or brand color

| Option | Pros | Cons |
|---|---|---|
| Wallpaper dynamic color (`dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`, Android 12+) | Feels native on the Pixel 9a. Personal. Tone system keeps contrast. Matches the Live Update and system UI | Hue is unpredictable. A red or orange wallpaper can make primary look like "danger" or "caution" |
| Fixed brand seed (Material Theme Builder) | Predictable identity. Can be chosen to sit between the semantic hues | Less native. One more theme to maintain |

**Decision:**
- Default to wallpaper color for chrome (surfaces, primary actions, the armed state).
- Keep the **semantic state colors fixed** (section 4.2).
- Offer a "Brand" option in Settings.
- If harmonizing semantic container tints with `MaterialColors.harmonize(...)`, re-validate them (harmonizing pulls hues together).

For a brand seed, choose a hue between blue (about 228°) and rose (about 335°), for example plum or violet around 280°, so it does not impersonate a state **(inferred)**.

### 4.2 Validated semantic palette (text/icon colors on M3 baseline surfaces)

Validator: the bundled dataviz `validate_palette.js`, run 2026-11-04, OKLab ΔE x100, Machado 2009 color-vision simulation.

| Role | Light (on `#FEF7FF`) | Dark (on `#141218`) | Meaning |
|---|---|---|---|
| good | `#005F52` | `#55D6BE` | Leading, won |
| caution | `#9C6300` | `#E9B949` | Over your max, outbid |
| danger | `#B3105F` | `#F27C9E` | Bid failed, session expired |
| info | `#4057B8` (or dynamic `primary`) | `#8FB3FF` | Armed, reserve not met |
| neutral | `onSurfaceVariant` `#49454F` | `#CAC4D0` | Lost, ended, watching |

Measured results:
- Light: color-blind floor 7.5 (protan, danger vs good), normal-vision 18.4 (pass), minimum contrast 4.75:1.
- Dark: color-blind floor 6.4 (deutan), normal-vision 16.3 (pass), minimum contrast 7.22:1.
- A naive Material-tone set (`#146C2E/#8A5100/#B3261E/#6750A4`) **failed**: danger vs caution ΔE 1.8 under deuteranopia, 11.2 under normal vision.

Conclusion: with four hues, the color-blind target (ΔE 8 or more) is not reachable. Color must always come with an icon and a text label. This matches Carbon (updated 2026-08-12): use "at least two of the following elements: color, shape, or symbol".

For containers (chips, tinted cards), derive roles per seed with `MaterialColors.getColorRoles(color, isLightTheme)`, which gives accent, onAccent, accentContainer and onAccentContainer (MDC `MaterialColors.java`).

### 4.3 State model (at most about 6 visible families, per Carbon)

Icons are Material Symbols names written from memory **(inferred)**.

| State | Trigger | Color | Icon | Label (examples) | A17 Live Update semantic |
|---|---|---|---|---|---|
| Watching | Tracked, no snipe armed | neutral | `visibility` | "Watching" | none |
| Armed | Snipe scheduled | primary/info | `schedule` | "Armed · £48 at -6 s" | INFO |
| Over your max | Current price at or above max before end | caution | `trending_up` | "£52 is over your £48 max" | CAUTION |
| Bidding | Final seconds, bid in flight | primary, emphasized | LoadingIndicator | "Bidding…" | INFO |
| Leading | Our bid is highest, not ended | good | `arrow_upward` | "You're the highest bidder" | SAFE |
| Outbid | Higher bid after ours, not ended | caution | `arrow_downward` | "Outbid at £50" | CAUTION |
| Won | Ended, we won | good | `emoji_events` | "Won · £44.00 (£4 under max)" | SAFE |
| Lost | Ended, someone else won | **neutral (not red)** | `close` | "Sold for £52 · your max £48" | none |
| Ended, no bid | Cancelled, over max at end, or seller ended | neutral, muted | `do_not_disturb_on` | "Ended · no bid placed" | none |
| Failed / needs action | Session expired, network error, verification | danger | `error` | "Bid failed · sign in to eBay" | DANGER |

Android 17 meanings, verbatim (developer.android.com/about/versions/17/features):
- "Green: Associated with safety."
- "Orange: For designating caution and marking physical hazards."
- "Red: Generally indicates danger, stop."
- "Blue: Neutral color for content that is informational and should stand out from other content."

eBay's own Skin/Evo maps "time-sensitive" to attention red (`#d50b0b`, archived Skin tokens, 2025-04). We deliberately do not.

### 4.4 Dark theme

- Follow the system by default. Offer "Pure black" for OLED (Tomato offers the same option). MDC notes dark themes give "improved battery power conservation for devices with OLED screens".
- Validate dark semantic colors against the dark surface (done above). Do not invert light colors.
- For a final-minute full-screen countdown, use a black background and shift the text position slightly every minute to protect the OLED. Tomato's `AlwaysOnDisplay.kt` increments position every minute **(inferred adaptation)**.

---

## 5. Patterns from adjacent domains

### 5.1 Live countdowns

- **Android Live Updates** (developer.android.com, accessed 2026-11-04):
  - Requirements: Standard/BigText/Call/Progress/Metric style; `POST_PROMOTED_NOTIFICATIONS`; `setRequestPromotedOngoing`; ongoing; `contentTitle`; no `RemoteViews`; not a group summary; not colorized; channel not `IMPORTANCE_MIN`.
  - Criteria: "ongoing, user-initiated, and time-sensitive". "events that are about to start can use a Live Update". "apps must tune their triggers to only appear when the activity is imminent". Inappropriate uses include "alerts, upcoming calendar events".
  - Empty states: "When an ETA is calculating or not yet available, don't show an empty state. Provide active placeholder text such as 'Thinking...'"
  - Chip: when-time countdown ("5min"), chronometer, "maximum width of 96dp", "If less than 7 characters, show the whole text."
- **Official sample** (android/platform-samples live-updates, 2026-07-09): states "Placing" (indeterminate), "Prepping" (25%), en route with `setWhen(now + 10 min)` and chronometer countdown, "Arrived"; `ProgressStyle.Point`/`Segment` with colors; tracker icon.
- **Google Clock** (Android notification design guide): "shows a timer running, but it lets the user pause or add a minute directly from the notification." 9to5google (2025-11-17): Clock 8.1 "shorter bottom bar and a new font" plus a rounded-square FAB.
- **Apple Live Activities** (HIG): "Alert people only for essential updates that require their attention."; "Always end a Live Activity immediately when the task or event ends"; "In most cases, 15 to 30 minutes is adequate."; "a sports app might use numeric content transitions for score changes or fade a timer in and out when it reaches zero."
- **Flighty** (ADA 2023 Interaction; iOS only): "a look that mirrors time-honored airport design conventions", "key information where you need it most". Status lines pair relative and absolute time: "Taxiing for 13m, for take off at 5:34PM."
- **Delivery tracking:** staged progress (placed, preparing, en route, arrived). Our analogue is armed, final check, bidding, result.

### 5.2 Urgency without anxiety (rules for this app)

1. **Precision grows with proximity, not color.**

   | Time left | Primary text | Secondary text |
   |---|---|---|
   | More than 7 days | "Sat 15 Nov" | "in 11 days" |
   | 1-7 days | "Tue 21:43" | "in 2 d 4 h" |
   | 1-24 h | "in 5 h 12 m" | "21:43" |
   | 10-60 min | "in 42 min" | "21:43:52" |
   | Under 10 min | ticking "9:41" (tnum) | n/a |
   | Under 60 s | hero takeover | n/a |

   **(inferred thresholds)**
2. **Never turn the timer red** because time is short. Red only for failures. "Ending soon" is shown by position (top of list, hero), size and precision. Basis: Android 17 semantics; Carbon.
3. **Show the plan and readiness next to the countdown**: "Bids £48.00 at 21:43:52 · 6 s before end · Ready". Calm tech: "Technology should inform and create calm".
4. **Periphery first:** list text, a status-bar chip in the final window only, a single result alert. Calm tech: "make use of the periphery"; Apple: alert only for essential updates.
5. **Motion only when meaningful:** digit roll only under 10 minutes; no pulsing or flashing.
6. **Factual copy:** "Ends Sun 21:43", not "Hurry!". NN/g's checklist asks "Are users rushed into making a decision?" M3: "Jokes aren't appropriate for notifications".
7. **Losing is neutral:** "Sold for £52 · your max £48" in neutral gray, no shake, no red.
8. **Failure is actionable:** danger color plus one clear action ("Sign in to eBay"), sent as a notification only when the user can fix it. Android design guide: don't notify "To announce error states the app may recover from without user interaction".

### 5.3 Finance-style price display (Android 17 MetricStyle, Carbon and dataviz sources; trading-app specifics inferred)

- **Headline price:** large live current price (`tnum`), with the delta since the snipe was added as a signed value plus an icon ("+£4.50 since Tue"). Carbon: differential indicators "must have either a '+' or '-' sign, a caret, or an arrow icon".
- **Headroom meter** instead of a red or green number:
  - Fill = current / your max.
  - Track = a lighter step of the same ramp (dataviz "Meter").
  - Fill color: primary below about 80% of max, caution from about 80% to 100%, caution plus label at or above 100% ("Over your max") **(inferred thresholds)**.
- **Price history sparkline** (item detail):
  - Prices move in discrete bid steps, so use a **step line**, 2 px, with an 8 px end dot ringed in the surface color and a 10% area wash.
  - A thin reference line for "Your max" with a direct label.
  - Label only the current value and your max; no axes in the compact form; tap to expand into a chart with time axis, tooltip and bid list. Spec from the dataviz guidance; step choice inferred.
- **Stat tiles** (History): label, value (static, proportional digits), optional signed delta versus last month, optional 12-point trend. One hero figure per view (for example "Saved £214 under your max").
- **Alerts:** threshold alerts ("Price passed your max", "Outbid") are one-shot, include the numbers, and offer "Raise max" **(inferred from common trading-app alert patterns; no trading-app source fetched)**.

### 5.4 Lists and detail

- **Expressive segmented lists:** `SegmentedListItem` + `ListItemDefaults.segmentedShapes(index, count)`.
- Sticky headers by end-time bucket (`LazyListScope.stickyHeader`).
- `Modifier.animateItem()` when items move between buckets as time passes (foundation API).
- List to detail: `SharedTransitionLayout` + `Modifier.sharedBounds` on photo and title (stable since animation 1.10.0).
- Predictive back is on by default for targetSdk 36.
- Swipe actions (cancel snipe) with `SwipeToDismissBox` plus an undo snackbar. Note: m3 Lists says "Swipe-to-reveal interactions are only available on Android Views" in its spec, so treat Compose swipe as custom **(inferred)**.
- Do not put progress rings on every row (m3 Progress: "Avoid applying progress indicators to every button in a list").

### 5.5 Empty states (NN/g, 2021-09-19)

Three jobs: communicate status, teach in context, give direct pathways.
- Home empty: decorative `MaterialShapes` art (for example `Cookie9Sided` holding a link icon) **(inferred)**, "No snipes yet", "Share any eBay listing to Sniper, or paste a link", buttons [Paste link] and [How sharing works].
- History empty: "Your results will appear here after your first auction ends."
- While loading, never show "nothing here". Show skeletons or the loading indicator.

### 5.6 Permission onboarding done gracefully

| Need | Check | Ask moment | Fix intent / API | Copy (draft) |
|---|---|---|---|---|
| Notifications | `NotificationManagerCompat.areNotificationsEnabled()` plus channel importance | When the user arms the first snipe | `ActivityResultContracts.RequestPermission()` with `POST_NOTIFICATIONS`; later `Settings.ACTION_APP_NOTIFICATION_SETTINGS` (standard API, not re-verified) | "Get told when you win, lose or get outbid." |
| Exact timing | `AlarmManager.canScheduleExactAlarms()` | Only if using `SCHEDULE_EXACT_ALARM` | `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` | "Sniper needs to wake at the exact second to bid." With `USE_EXACT_ALARM` (sideloaded) the row is always green: "Granted automatically", "Cannot be revoked by the user" |
| Battery | `PowerManager.isIgnoringBatteryOptimizations(pkg)` | Readiness check, after the first snipe | `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) or `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` | "Unrestricted battery keeps snipes on time while the phone sleeps." |
| Restricted bucket / background | `UsageStatsManager.getAppStandbyBucket() == STANDBY_BUCKET_RESTRICTED` | Readiness check | App info (`ACTION_APPLICATION_DETAILS_SETTINGS`) | "Android has restricted Sniper in the background." |
| Hibernation | `PackageManagerCompat.getUnusedAppRestrictionsStatus()` | Readiness check (advisory) | `IntentCompat.createManageUnusedAppRestrictionsIntent()` via `startActivityForResult` | "Turn off 'Pause app activity if unused' so long-running snipes stay armed." |
| Live countdown | `NotificationManager.canPostPromotedNotifications()` | Readiness (optional) | `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` | "Show the final countdown in the status bar." |
| Link capture (optional) | `DomainVerificationManager.getDomainVerificationUserState()` | Settings > Advanced | `ACTION_APP_OPEN_BY_DEFAULT_SETTINGS` plus `package:` URI | "Open eBay links in Sniper (only if the eBay app doesn't claim them)." |

Principles quoted:
- "Before you ask users to grant any permissions, let them familiarize themselves with your app."
- "In your app's UI, explain to the user why your app needs to schedule exact alarms."
- "Display a UI that explains to the user why they need to disable hibernation for your app."
- "Don't show the notification permission dialog, if the user has dismissed the UI."

### 5.7 Notifications content rules (to apply verbatim)

Title under 29 characters, collapsed body under 40, expanded under 80, 1-2 buttons of 1-2 words. Be day-specific. Item photo as a **square** large icon. "Reserve setting the background color of a notification for high priority notifications such as navigation, ongoing call" (and Live Updates must not be colorized anyway).

### 5.8 Haptics

"less is more". `HapticFeedbackType.Confirm` on arm and on a won result; `Reject` on a failed bid; `SegmentTick` when snapping the offset slider. No per-second ticks.

### 5.9 Accessibility

- Countdown: wrap the digit-roll in `Modifier.clearAndSetSemantics { contentDescription = "Ends in 2 hours 14 minutes" }`. Announce only milestones (10 min, 1 min, result) with `LiveRegionMode.Polite`, never every second **(inferred policy)**.
- Large fonts: `TextAutoSize.StepBased(maxFontSize = ...)` for the hero, no fixed heights. Test at 200% scale.
- Color never alone (section 4.2).
- Targets at least 48 dp; buttons per the M3E size tokens.

---

## 6. Screen recommendations

### 6.1 Information architecture

- `ShortNavigationBar`: **Snipes**, **History**. Settings sits in the top bar overflow; the readiness pill sits in the top bar.
- Detail screens are pushed destinations without the nav bar, so they can use a floating toolbar (M3: toolbar must not coexist with a nav bar).
- Entry points: Share sheet (`ACTION_SEND` text/plain), Paste suggestion chip, item-number entry, and in-app eBay browsing (WebView, other agent).

### 6.2 Home

```
┌───────────────────────────────────────┐
│ Snipes                   (●) Ready  ⋮ │  LargeFlexibleTopAppBar (title + subtitle)
│ 3 armed · next in 2 h 14 m            │  readiness pill = icon + label
│ ╭───────────────────────────────────╮ │  Hero card: primaryContainer, 28 dp radius
│ │ [photo]   Fender Player Strat SSS │ │  photo crop: rounded square (decorative shape
│ │  ◜wavy◝   Sunburst · Leeds        │ │  only when armed); CircularWavyProgressIndicator
│ │  2:14:09                          │ │  displayLargeEmphasized + tnum (ROND 100)
│ │  Bids £48.00 Sun 21:43:52 (-6 s)  │ │  plan, always visible
│ │  Now £31.50 · 7 bids              │ │  tnum
│ │  ▓▓▓▓▓▓▓▓░░░░  £16.50 headroom    │ │  meter, same-ramp track
│ ╰───────────────────────────────────╯ │
│ Ending within an hour                 │  stickyHeader
│ ┌───────────────────────────────────┐ │  SegmentedListItem block
│ │[img] Canon AE-1 body   £85 · £120 │ │  price · max (tnum, right-aligned)
│ │      ⏱ Armed              42 min  │ │  state chip (icon + label) · time (tnum)
│ ├───────────────────────────────────┤ │
│ │[img] LEGO 10497        £96 · £90  │ │
│ │      ↗ Over your max      21:58   │ │  caution icon + label, no red
│ └───────────────────────────────────┘ │
│ Sunday 9 Nov                          │
│ ...                                   │
│                       ╭─────────────╮ │  ExtendedFloatingActionButton "New snipe"
│                       │ + New snipe │ │  (no FAB menu: not allowed from extended FAB)
├───────────────────────────────────────┤
│   Snipes            History           │  ShortNavigationBar
└───────────────────────────────────────┘
```

Details:
- Optional filter chips under the top bar: All · Armed · Watching · Needs attention.
- Snipe groups appear as one segmented block with a header "Strats · win 1 of 3".
- "Last checked 3 min ago" as quiet supporting text in the hero. Pull-to-refresh uses the loading indicator.
- Rows keep a consistent 1:1 thumbnail with `ContentScale.Fit` on a `surfaceContainerHigh` tile, because eBay photos vary **(inferred)**.

### 6.3 Add-snipe flow (bottom sheet, expanding to full screen)

1. **Source:** share intent lands directly on step 2. Otherwise:
   - a paste field with a "Paste ebay.co.uk/itm/2564..." `SuggestionChip`, shown only when `getPrimaryClipDescription()` reports a URL;
   - item-number entry;
   - a "Browse eBay" link.
2. **Preview:**
   - `ContainedLoadingIndicator` while fetching (under 5 s). If it takes longer, show the placeholder text "Checking the listing…" (Live Updates guidance on non-empty states).
   - The card shows: photo carousel; title; seller and feedback; condition; current price and bids; end time as absolute and relative ("Ends Sun 9 Nov 21:43:58 · in 2 d 4 h"); postage; location/import note; currency with approximate GBP.
3. **Your max:**
   - A large numeric entry (`displayMedium`, tnum, currency prefix in lighter weight), decimal keypad.
   - Helper chips: "Minimum £32.00" (current + increment, other agent supplies increments), "+10%", "Round to £50".
   - Inline validation: "At least £32.00".
   - Total-cost line: "If you win at your max: £48.00 + £3.95 postage = £51.95". Fees and import charges come from another agent.
4. **When to bid:**
   - Connected ToggleButton group: 3 s · 6 s · 10 s · Custom (slider).
   - Helper text based on measured on-device latency, for example "6 s suits your connection (last checks 0.6-1.1 s)" **(inferred feature; latency measurement by technical agent)**.
   - Group picker: "None / Strats (win 1 of 3)".
5. **Confirm:**
   - `SplitButtonLayout`: leading "Arm snipe · £48.00" (Large or Medium, `labelLargeEmphasized`); trailing menu with "Watch only" and "Arm and open listing".
   - On success: sheet collapses, the row animates into its time bucket (`animateItem`), haptic Confirm, snackbar "Armed. Bids Sun 21:43:52".
6. First-time only: the in-context notification ask appears after the first arm.

### 6.4 Item detail

```
[ HorizontalCenteredHeroCarousel / single photo, sharedBounds from row ]
Fender Player Strat SSS                         titleLarge, 2 lines
Seller 99.8% · Used · Leeds                     bodyMedium, onSurfaceVariant
╭ Countdown ───────────────────────────────╮
│ 2 d 4 h            Ends Sun 9 Nov 21:43:58 │ display/headline + absolute time
│ Bids £48.00 at 21:43:52 · 6 s before end   │ plan
╰────────────────────────────────────────────╯
Price
 £31.50   +£4.50 since Tue · 7 bids          tnum; icon delta
 ▁▁▁▁▂▂▂▂▃▃▃▃▃━━━━ (step sparkline) — max line labelled "Your max £48"
Your snipe
 Max £48.00 · Offset 6 s · Group Strats (1 of 3)    [Edit]
 Total if won at max £51.95
Timeline (bid log)
 Tue 14:02  Added at £27.00
 Thu 09:10  Price £31.50 (7 bids)
 Sun 21:43:52.0 Bid sent · 21:43:52.9 Confirmed     (after the fact)
[ HorizontalFloatingToolbar: Edit max · Offset · Open on eBay · ⋮ ]  + FAB (Edit max)
```

- Content-based color option: derive a scheme from the photo for the header container only (m3: "contained screen elements adjacent to the source image"). Use MDC `setContentBasedSource(bitmap)`, or a seed via color utilities.
- Danger actions ("Cancel snipe") sit in the overflow, with a confirmation snackbar plus undo.

### 6.5 Notifications and the Live Update

Channels **(inferred names/importance)**:

| Channel | Importance | Content |
|---|---|---|
| Results | HIGH | Won / Lost / Ended |
| Problems | HIGH | Bid failed, session expired before end |
| Price alerts | DEFAULT | Price passed your max, Outbid (before end) |
| Final countdown | DEFAULT | Live Update only (not MIN) |

Final-window Live Update (from about T-10 min to the result):
- Title: "Bidding £48.00 at 21:43" (24 characters).
- Text: "Fender Strat · now £31.50" (under 40).
- Large icon: square item photo.
- `setWhen(bidAt)` + `setUsesChronometer(true)` + `setChronometerCountDown(true)`.
- `ProgressStyle` segments [monitoring | final minute], a point at the bid moment, tracker icon (gavel), `setStyledByProgress(true)`.
- Actions: "Raise max" (inline number reply **(inferred)**), "Cancel".
- `setDeleteIntent` to honor dismissal.
- During the bid: `setShortCriticalText("Bidding")` with the progress indeterminate.
- Then replace it with a normal, dismissible result notification:
  - Won: "Won · Canon AE-1" / "£85.00, £35 under your max".
  - Lost: "Sold for £131 · LEGO 10497" / "Your max was £90".
  - Failed: "Bid failed · sign in needed" / "eBay session expired at 21:40".
- Android 17: `Notification.MetricStyle` with metrics `TimeDifference` "Bids in", `FixedText` "Price" (SAFE below max, CAUTION at or above), `FixedText` "Your max"; `setCriticalMetric(0)`. Check the exact factory signatures.
- Lock screen: consider `VISIBILITY_PRIVATE` with a public version "Snipe in 4 min" **(inferred)**.

### 6.6 History and stats

- Connected filter group: All · Won · Lost · Ended.
- KPI row of stat tiles (static digits): "Won 12 of 18 (67%)", "Spent £1,284", "Saved £214 under your max" (one hero).
- Month sections. Rows show the outcome icon and label, final price, "max £120", and a "£35 under" or "£41 over" delta with sign plus icon.
- Optional per-month bar chart of spend (single series, no legend; dataviz rules).

### 6.7 Settings

- eBay account: signed-in user, site (ebay.co.uk default), sign out / clear session.
- Defaults: bid offset, final-countdown window (5/10/15/30 min), monitoring mode (from technical agent).
- Notifications: per-channel deep links.
- Appearance: Theme (System/Light/Dark), Pure black, Color (Wallpaper/Brand), Haptics.
- Advanced: open-by-default links, diagnostics export (bid logs), about and licenses (OFL font notice).

### 6.8 Ready-to-snipe check

```
      ╭──────────╮
      │  ✓ morph │   decorative shape morph (Cookie9Sided → Circle) on recheck
      ╰──────────╯
   Ready to snipe            or: "2 things need attention"
✓ eBay session      Signed in as ivan_v · ebay.co.uk · checked 3 min ago
✓ Notifications     Results and alerts allowed
✓ Exact timing      Granted automatically
! Battery           Optimised: snipes may fire late             [Fix]
✓ Background        Not restricted
! Unused-app pause  On: Android may pause Sniper after months    [Fix]
✓ Final countdown   Allowed in status bar
✓ Clock             0.3 s behind eBay, corrected  (from technical agent)
✓ Connection        Wi-Fi · eBay responded in 220 ms (from technical agent)
[ Run check again ]
```

- Rows use icon, label and semantic color. Blocking issues sort first.
- The readiness pill on Home mirrors the overall state.
- Re-run on resume and before each final window **(inferred)**.

### 6.9 Optional surfaces

- **Home-screen widget "Next snipe"** via Glance. Glance has no Chronometer composable (`glance-appwidget` API, androidx-main), so embed `AndroidRemoteViews` with a RemoteViews `Chronometer` countdown (RemoteViews countdown API from memory, verify).
- **Final-minute full-screen view:** opt-in "Watch it live", black, with the burn-in shift described in 4.4.
- **Wear OS:** Live Updates "are bridged to some connected Wear OS devices" (Live Updates doc).

---

## 7. Motion spec

| Moment | Motion | Spec | Haptic |
|---|---|---|---|
| Countdown under 10 min | Per-digit vertical slide plus fade (`AnimatedContent`), tnum so width is stable | `fastSpatialSpec<IntOffset>()` + `fastEffectsSpec<Float>()` | none |
| T-60 s takeover | Hero card grows (28 to 48 dp radius morph); wavy ring amplitude calms toward the end (custom `amplitude`) | `defaultSpatialSpec()` expressive | none |
| Bid in flight | `LoadingIndicator` polygons morph inside the hero | built-in | none |
| Bid confirmed / leading | Indicator morphs to a check (`Morph`) | `slowSpatialSpec()` | Confirm |
| Won | Photo crop Circle to `SoftBurst`, one burst of small shapes, then settle | expressive slow spatial | Confirm |
| Lost | Fade to neutral, no shake | standard effects | none |
| Failed | Static danger state, then action | standard | Reject |
| Arm snipe | Button pressed-shape morph (`ButtonDefaults.shapes()`), sheet collapse, row inserts (`animateItem`) | fast spatial | Confirm |
| Time passes | Rows move between buckets | `animateItem()` default | none |
| List to detail | `sharedBounds` photo and title | default spatial | none |
| Back | Predictive back (system default at targetSdk 36) | n/a | none |

All of this is off automatically under "Remove animations", because Compose honors the animator scale.

---

## 8. Three visual style options

### O1 Pixel-native Expressive (recommended base)

- **Color:** wallpaper dynamic color, fixed semantic set, Brand fallback.
- **Type:** Google Sans Flex. ROND 100 semibold for display and headlines; ROND 0 for text; tnum numerals.
- **Shape:** 28 dp hero, segmented lists (4/16 dp), pill buttons, decorative MaterialShapes only on imagery, result and empty states.
- **Motion:** `MotionScheme.expressive()` app-wide; standard inside Settings.
- **Pros:**
  - Looks like it belongs on a 2026 Pixel (system UI, Live Updates, Google apps use the same patterns).
  - Least custom code.
  - Accessible tone system.
  - Personal color.
- **Cons:**
  - Can read as a generic Google app.
  - Wallpaper hue may sit near a semantic hue.
  - Depends on Material3 1.5 beta; LoadingIndicator and MaterialShapes still experimental.

### O2 Departure board (instrument)

- **Color:** dark-first near-black (`#0F1113` surface, `#181B1F` containers), gray text tiers, **monochrome until something needs attention**. Semantic colors appear only on exceptions; armed is white or high-emphasis neutral.
- **Type:**
  - Google Sans Flex narrow (`wdth` about 87) for dense rows.
  - Google Sans Code (monospace) or tnum for all times and prices.
  - A T-minus column aligned on the left.
- **Layout:** time-axis list like an airport board (T- time · item · price/max · state). Optional 24-hour timeline strip with ticks for each snipe. Bid log visible.
- **Motion:** standard scheme, split-flap style digit changes, no bounce.
- **Pros:**
  - Maximum glanceability and precision; scales to many snipes.
  - Distinctive.
  - Fits Flighty's praised "airport design conventions".
  - Power-friendly on OLED.
  - Likely to suit an analytical user **(inferred)**.
- **Cons:**
  - Can feel cold or stressful.
  - Dark-only is harsh in daylight.
  - More custom components.
  - Dense text degrades at large font scales.
  - Less "native Pixel".

### O3 Auction catalogue (editorial)

- **Color:** warm paper light theme (`#FAF7F2` surface, `#1F1A17` ink), plum accent around 280° hue (for example `#6B3F69`, away from all state hues), warm charcoal dark theme. Photo-derived content color on detail and result.
- **Type:**
  - Newsreader (OFL, **tabular by default**, opsz 6-72) for titles and prices.
  - Google Sans Flex for UI, labels and body. Avoid Fraunces and Instrument Serif for numbers (no tnum).
- **Layout:** large 1:1 or 4:5 photo cards, "lot" language ("Lot ends Sun 21:43"), 2-column grid option, a catalogue-style result ("Yours for £44.00").
- **Motion:** slow, soft fades and shared-bounds photo transitions, minimal bounce.
- **Pros:**
  - The item is the hero.
  - Calmest emotional tone.
  - Most different from every competitor.
  - Good for collectibles.
- **Cons:**
  - Low density.
  - eBay photo quality varies (white backgrounds, clutter).
  - Two typefaces to tune.
  - Less tool-like for fast scanning.
  - Fights dynamic color.

### Blend (recommended)

**O1** structure and components, plus:
- From **O2**: monochrome-until-exception, a tnum time column, the bid log, "Ready" status.
- From **O3**: photo-forward hero and content-derived color on detail and result only.

Reason: the user's complaint is design quality. O1 gives polish and consistency with the least custom code on a Pixel 9a. The O2 and O3 traits give the app its own identity, the precision the user cares about, and warmth at the emotional moments (win or loss).

---

## 9. Dribbble, Behance, Mobbin

- Dribbble search and tag pages returned empty bodies through WebFetch. Behance and Mobbin returned HTTP 403 (2026-11-04).
- The Wayback snapshot of `dribbble.com/tags/auction` (2024-03-05) lists mostly NFT and real-estate concepts, for example "NFT Auction Page-Light Version", "Auction Mobile IOS App" (Purrweb UI/UX Agency), "Art Auction App", "Auction House - Artwork Bidding", "Coopang - Fish Auction Dashboard". Shot descriptions were empty.
- What makes good auction shots good **(inferred, not verified visually)**:
  - one dominant time-left and current-bid block near the photo;
  - a single primary CTA;
  - bid history as a list with your bids highlighted;
  - big imagery.
- Common flaws to avoid:
  - red countdown everywhere;
  - several CTAs of equal weight;
  - decorative charts without values.

---

## 10. Code sketches (untested; API names verified against 1.5.0-beta01 / ui 1.12 API files unless noted)

```kotlin
// build.gradle.kts
implementation(platform("androidx.compose:compose-bom:2026.09.00"))
implementation("androidx.compose.material3:material3:1.5.0-beta02") // overrides BOM 1.4.0 (no expressive APIs)
implementation("androidx.graphics:graphics-shapes:1.1.0")           // RoundedPolygon/Morph for custom morphs
```

```kotlin
// Type.kt: one bundled variable file, several configurations
val SansText = FontFamily(
    Font(R.font.google_sans_flex, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.google_sans_flex, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.google_sans_flex, FontWeight.Bold,   variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
val SansRounded = FontFamily(
    Font(R.font.google_sans_flex, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(620), FontVariation.Setting("ROND", 100f))),
)
private val base = Typography(fontFamily = SansText) // 1.5 ctor also sets *Emphasized styles
val SniperTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = SansRounded),
    displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = SansRounded),
    displayMedium = base.displayMedium.copy(fontFamily = SansRounded),
    headlineMedium = base.headlineMedium.copy(fontFamily = SansRounded),
)
fun TextStyle.ticking() = copy(fontFeatureSettings = "tnum")   // countdowns, live prices, list columns
```

```kotlin
// Theme.kt
@Composable
fun SniperTheme(darkTheme: Boolean = isSystemInDarkTheme(), wallpaper: Boolean = true, pureBlack: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var scheme = when {
        wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> BrandDark
        else -> BrandLight
    }
    if (darkTheme && pureBlack) scheme = scheme.copy(surface = Color.Black, background = Color.Black)
    CompositionLocalProvider(LocalStateColors provides if (darkTheme) DarkStateColors else LightStateColors) {
        MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = SniperTypography, content = content)
    }
}
// LightStateColors: good #005F52, caution #9C6300, danger #B3105F, info #4057B8
// DarkStateColors:  good #55D6BE, caution #E9B949, danger #F27C9E, info #8FB3FF
```

```kotlin
// Countdown digits (final 10 minutes): tnum + per-digit roll, spoken once per milestone
@Composable
fun CountdownText(text: String, spoken: String, style: TextStyle, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    val slide = motion.fastSpatialSpec<IntOffset>()
    val fade = motion.fastEffectsSpec<Float>()
    Row(modifier.clearAndSetSemantics { contentDescription = spoken }) {
        text.forEachIndexed { index, char ->
            key(index) {
                AnimatedContent(targetState = char, transitionSpec = {
                    (slideInVertically(slide) { -it } + fadeIn(fade)) togetherWith (slideOutVertically(slide) { it } + fadeOut(fade))
                }, label = "digit") { Text(it.toString(), style = style.ticking()) }
            }
        }
    }
}
// Tick on wall-clock second boundaries: delay(1000 - System.currentTimeMillis() % 1000) (inferred approach)
```

```kotlin
// Final-window Live Update (NotificationCompat names as used in android/platform-samples, 2026-07-09)
val style = NotificationCompat.ProgressStyle()
    .setStyledByProgress(true)
    .setProgressSegments(listOf(
        NotificationCompat.ProgressStyle.Segment(windowMs - 60_000).setColor(primaryArgb),
        NotificationCompat.ProgressStyle.Segment(60_000).setColor(tertiaryArgb)))
    .setProgressPoints(listOf(NotificationCompat.ProgressStyle.Point(windowMs - offsetMs).setColor(primaryArgb)))
    .setProgress(elapsedInWindowMs)
val notification = NotificationCompat.Builder(context, CHANNEL_FINAL_COUNTDOWN)
    .setSmallIcon(R.drawable.ic_stat_gavel)
    .setContentTitle("Bidding £48.00 at 21:43")
    .setContentText("Fender Strat · now £31.50")
    .setLargeIcon(squareThumbnail)
    .setOngoing(true)
    .setRequestPromotedOngoing(true)            // + <uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS"/>
    .setWhen(bidAtMillis).setUsesChronometer(true).setChronometerCountDown(true)
    .setStyle(style)
    .addAction(raiseMaxAction).addAction(cancelAction)
    .setDeleteIntent(dismissedIntent)
    .build()
```

---

## 11. Inferred items and risks

- Thresholds (10-minute window, precision steps, 80% headroom), copy, channel names, Material Symbols names, the shape choices for hero and celebration, Pixel 9a dp width, the zero-padding choice, the remote-input "Raise max" action, and the AOD burn-in shift are design inferences.
- `SplitButtonLayout` vs `SplitButton`: the release notes and the beta01 API disagree. Verify at build time.
- LoadingIndicator and MaterialShapes remain experimental; their APIs may change before 1.5.0 stable.
- Google Sans Flex adds 3.9 MB (subsetting is possible; tool choice is up to the builder). The current file has no GRAD axis.
- Using `CATEGORY_ALARM` could interact with Do Not Disturb "Alarms" exceptions **(inferred)**. Decide deliberately (results may deserve it; the Live Update should not).
- Android 17 Advanced Protection Mode blocks sideloading installs when enabled (features page). This is relevant to app updates, not UI. Mention it in readiness only if detected via `AdvancedProtectionManager` **(inferred)**.
- No trading-app primary source was retrievable (search tool weak; Apple support page truncated). Finance patterns rely on Carbon, the dataviz guidance and Android 17 MetricStyle.

---

## 12. Sources (all accessed 2026-11-04 unless stated)

- developer.android.com/jetpack/androidx/releases/compose-material3 (1.4.0 2025-09-24; 1.5.0-beta02 2026-10-21)
- github.com/androidx/androidx androidx-main: `compose/material3/material3/api/1.5.0-beta01.txt`, `current.txt` (HEAD commit 2026-10-29); tokens `ExpressiveMotionTokens.kt`, `StandardMotionTokens.kt`, `ShapeTokens.kt`, `Button*Tokens.kt`, `Fab*Tokens.kt`, `TypefaceTokens.kt`, `PaletteTokens.kt`; `MotionScheme.kt`, `WavyProgressIndicator.kt`, `ColorScheme.kt`; `compose/ui/ui/src/androidMain/.../WindowRecomposer.android.kt`; `compose/ui/ui-text/api/current.txt`; `glance/glance-appwidget/api/current.txt`
- Google Maven `androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom`
- developer.android.com/jetpack/androidx/releases/compose-animation (1.10.0 2025-12-03; 1.12.1 2026-09-09); /releases/graphics (graphics-shapes 1.1.0 2025-10-22)
- developer.android.com/develop/ui/compose/designsystems/material3; /develop/ui/compose/text/fonts
- m3.material.io content JSON (carbonVersion 2026-10-13_06-10-19): building-with-m3-expressive (2026-07-16), Adding Motion Physics with Jetpack Compose (Rebecca Franks et al., 2026-07-08), Motion physics system (2026-07-17), Typography (2026-08-28), Shape (2026-07-17), Dynamic color (2026-07-17), Content design: Notifications (2026-07-17), Button groups (2026-09-16), Split buttons (2026-09-08), Toolbars (2026-07-28), Loading indicator (2026-07-29), Progress indicators (2026-07-29), Lists (2026-08-06), FAB menu (2026-07-17)
- design.google/library/expressive-material-design-google-research (2025, undated page); design.google/library/google-sans-flex-font (2025); design.google/library/design-notes-material-3-expressive-liam-spradlin
- blog.google Material 3 Expressive launch (2025-05-13, Mindy Brooks)
- 9to5google: 2025-11-17 M3E redesign roundup (Abner Li); 2025-11-18 Google Sans Flex release (Ben Schoon); 2025-12-19 Google Sans Flex history
- github.com/google/fonts ofl/googlesansflex (v5.000 commit 2026-10-09), ofl/googlesans, ofl/robotoflex, ofl/roboto, ofl/googlesanscode, ofl/newsreader, ofl/fraunces, ofl/instrumentserif; local copies in /var/tmp/sniper-research/src/fonts-google/
- github.com/material-components/material-components-android docs (ButtonGroup.md 2025-08-29, LoadingIndicator.md 2025-08-29, SplitButton.md 2025-07-30, FloatingToolbar.md 2026-04-17, theming/Motion.md 2025-01-07, theming/Typography.md 2025-04-30, theming/Color.md 2024-08-27, theming/Dark.md 2024-05-16); MaterialColors.java
- developer.android.com/develop/ui/views/notifications/live-update; /reference/android/app/Notification.ProgressStyle (API 36); Notification.MetricStyle and Notification.Metric (API 37); /about/versions/17/features; /about/versions/16/behavior-changes-16; /about/versions/12/behavior-changes-all
- github.com/android/platform-samples samples/user-interface/live-updates (2026-07-09)
- developer.android.com: /develop/background-work/services/alarms/schedule; /training/monitoring-device-state/doze-standby; /topic/performance/app-hibernation; /develop/ui/views/notifications/notification-permission; /develop/ui/views/notifications/time-sensitive; /design/ui/mobile/guides/home-screen/notifications; /develop/ui/views/haptics/haptics-principles; /training/app-links/verify-android-applinks; /design/ui/wear/guides/get-started/design-language (2025-05-20)
- github.com/nsh07/Tomato (HEAD 98fa69e, 2026-10-18), cloned to /var/tmp/sniper-research/src/nsh07_Tomato
- Apple HIG Live Activities JSON; Apple Design Awards 2023 (Flighty); flighty.com; en.wikipedia.org/wiki/Flighty; en.wikipedia.org/wiki/Pixel_9a
- calmtech.com (Amber Case); nngroup.com deceptive patterns (2023-12-01), empty states (2021-09-19); carbondesignsystem.com status indicators (2026-08-12)
- github.com/eBay/skin (archived, 2025-04-28), github.com/eBay/evo-web (2026-11-03)
- web.archive.org Dribbble tag "auction" snapshot 2024-03-05
- Competitor screenshots: /var/tmp/sniper-research/screens/* (collected 2026-11-04 by another agent)
- Bundled dataviz skill references and `validate_palette.js` (run 2026-11-04)