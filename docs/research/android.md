# Android platform mechanics for firing a network action at an exact wall-clock second on a sideloaded app on a Pixel 9a (Android 17, Nov 2026)

## Summary
In November 2026 the Pixel 9a runs Android 17 (API 37). Android 17 went stable in June 2026. The phone most likely has the QPR1 build (API 37.1) from the September Pixel drop, and QPR2 (37.2) is still in beta. Neither Android 16 nor 17 added new foreground-service time limits or exact-alarm restrictions. The Android 17 changes that matter here are per-app memory limits, a new listener-based setExactAndAllowWhileIdle (API 37), Live Update semantic colours and MetricStyle, and, in QPR2, slower handling of broadcasts an app sends to itself.

For a sideloaded app that targets SDK 33 or higher, USE_EXACT_ALARM is a normal permission. It is granted at install, the user cannot revoke it, and it keeps the app in the WORKING_SET standby bucket or better. SCHEDULE_EXACT_ALARM is denied by default on fresh installs.

Alarm sources in AOSP show three rules. An exact alarm gives the app a 10-second window in which it may start a foreground service. Alarms cannot be set less than 5 seconds into the future. When the user sets battery to Unrestricted, every alarm the app sets is exempt from Doze limits.

The foreground-service type to use is specialUse. It has no time limit and may be started from BOOT_COMPLETED. shortService has a 3-minute cap, and both shortService and dataSync are blocked from BOOT_COMPLETED for apps targeting SDK 35+.

A running foreground service keeps network and wake locks during Doze, Data Saver and the Android 15 background-network block. The final seconds must therefore run inside that service, with a partial wake lock and an in-process timer based on elapsedRealtime.

The headless WebView works better than expected. Chromium treats a WebView that was never attached to a window as visible, so its page timers are not throttled. Attaching it and detaching it, calling onPause(), or calling the app-wide pauseTimers() makes the page hidden.

The phone's own clock is synced by SNTP about once a day. Android leaves it alone unless it is off by more than 2 seconds, and the documented worst-case error is about 2.5 seconds. The app should run its own multi-sample SNTP check against time.google.com and fall back to SystemClock.currentNetworkTimeClock().

Sign-in limits: Google and Facebook sign-in are blocked inside WebViews. Passkeys in a WebView need a Digital Asset Links tie between the app and the site, so eBay passkeys should not work in our app (inferred). The user should sign in with password plus a typed one-time code.

The recommended run for one snipe: an exact alarm at about T-120 s, plus a backup alarm, wakes the app. The app starts a specialUse foreground service with a wake lock and a Live Update countdown. It syncs the clock, loads the page in a never-attached WebView, checks the price and session, fires once at T minus the lead time, then confirms and reports. Fallbacks cover a missed alarm, a blocked service start, a lost session, no network, a reboot before unlock, and a crashed WebView renderer.

## Findings
- **V1** [high] Android 17 (API 37, Build.VERSION_CODES.CINNAMON_BUN) went stable in June 2026, and the Pixel 9a is eligible. In Nov 2026 the Pixel 9a runs Android 17, most likely the QPR1 build (API 37.1). QPR2 (API 37.2) is still in beta.
  - evidence: source.android.com/docs/setup/reference/build-numbers: 'CP2A.260605.016 | android-17.0.0_r1 | Android17 | 2026-06-05'. Pixel Update Bulletin June 2026, published June 16, 2026 (source.android.com/docs/security/bulletin/pixel). androidcentral.com 'Android 17 is finally here', 16 Jun 2026. developer.android.com/about/versions/17/qpr1/release-notes: Beta 9, Aug 17 2026, CP31.260623.012, 'reached Platform Stability as of Beta 6'. developer.android.com/about/versions/17/qpr2/release-notes: Beta 7.1, Oct 16 2026, CP41.260831.019. Pixel bulletin September 2026 published Sept 15, 2026. support.google.com/pixelphone/answer/4457705: 'Pixel 8 and later phones will get updates for seven years'. android/platform-samples SnackbarNotificationManager.kt uses Build.VERSION_CODES.CINNAMON_BUN (commit 2026-06-04).
  - implication: Use compileSdk/targetSdk 37 and test on Android 17. That QPR1 is on the device is inferred from the Sept 15 Pixel drop and the QPR cadence; check Settings > About phone. Expect QPR2 (about Dec 2026, inferred) during development.
- **V2** [high] Android 17 adds app memory limits based on device RAM for all apps. A process killed by them has exit reason REASON_OTHER with a description containing 'MemoryLimiter:AnonSwap'.
  - evidence: developer.android.com/about/versions/17/behavior-changes-all, 'App memory limits'. Shell commands: 'am memory-limiter ignore <uid>|none|all', 'am memory-limiter manual <pid> <limit>|max|none', 'am memory-limiter status'. Android 17 Beta 4 release notes (April 16, 2026) say the limits are 'conservative'.
  - implication: A headless WebView rendering a heavy eBay page lives in a non-visible process and could hit the limit (inferred risk). Use one WebView instance, destroy it after each snipe, and log ApplicationExitInfo.getDescription().
- **V3** [high] Android 17 adds AlarmManager.setExactAndAllowWhileIdle(int type, long triggerAtMillis, String tag, Executor executor, OnAlarmListener listener) at API 37. It also adds Intent.ACTION_TIMEZONE_OFFSET_CHANGED for DST and offset changes.
  - evidence: developer.android.com/reference/android/app/AlarmManager: 'Added in API level 37 ... Listener based alarms may be canceled by the Android system whenever the calling process no longer has any components running'. developer.android.com/about/versions/17/features, 'Reduce wake locks with listener support for allow-while-idle alarms'. Android 17 Beta 2 notes: 'ACTION_TIMEZONE_OFFSET_CHANGED triggers specifically on offset changes like DST transitions.'
  - implication: The listener variant can serve as an in-process backup timer while the foreground service is alive. It cannot replace the PendingIntent alarm that wakes the app, and the 5-second minimum lead (AL7) still applies.
- **V4** [high] Android 17 changes for apps targeting 37 that touch this app: lock-free MessageQueue; static final fields can no longer be changed; ACCESS_LOCAL_NETWORK enforced (LAN addresses only); Encrypted Client Hello on; Certificate Transparency on by default; background-activity-launch hardening extended to IntentSender; widget RemoteViews bitmap memory cap of 6*width*height bytes; background audio needs a foreground service with while-in-use capability, or the exact alarm permission plus USAGE_ALARM.
  - evidence: developer.android.com/about/versions/17/behavior-changes-17 (sections: New lock-free implementation of MessageQueue; Local network permission required; Enable CT by default; ECH; Activity Security / BAL hardening; Memory limit widget; Background audio hardening). Android 17 Beta 1 notes: 'Notification Restrictions: Strict size limits enforced on custom notification views'.
  - implication: SNTP to time.google.com and eBay over the internet need no local-network permission. Avoid reflection on MessageQueue. Avoid custom-view notifications, which also disqualify Live Updates. To play a sound from the background, use notification sounds.
- **V5** [high] Android 17 QPR2 (beta, all apps): broadcasts an app sends to itself are delivered inside its own process. They no longer raise process importance, they wait while the process is frozen, and they are lost if the process dies.
  - evidence: developer.android.com/about/versions/17/qpr2/release-notes, 'Optimized delivery of self-broadcasts': 'Don't use self-broadcasts as a timer or keep-alive mechanism ... use AlarmManager for work that must happen at a specific time.' (QPR2 Beta 7.1, Oct 16, 2026)
  - implication: Never use sendBroadcast to oneself to drive the snipe timer. Use AlarmManager PendingIntents (sent by the system) to wake the app and direct calls or flows inside it.
- **V6** [high] Android 16 changes that matter here: JobScheduler and WorkManager runtime quotas now also apply to jobs that start while the app is visible and continue after, and to jobs that run alongside a foreground service. Android 16 also brought Notification.ProgressStyle (API 36), promoted ongoing notifications (Live Updates), MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE, and, for apps targeting 36, scheduleAtFixedRate running at most one missed execution.
  - evidence: developer.android.com/about/versions/16/behavior-changes-all, 'JobScheduler quota optimizations'. developer.android.com/about/versions/16/behavior-changes-16, 'Fixed rate work scheduling optimization'. developer.android.com/about/versions/16/features/progress-centric-notifications. developer.android.com/guide/components/activities/background-starts ('MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE (added in SDK 36)').
  - implication: Price-check jobs that run during a snipe service count against quota. Do not drive timing with ScheduledExecutorService.
- **AL1** [high] USE_EXACT_ALARM has protectionLevel 'normal'. It is granted automatically at install, the user cannot revoke it, it requires targetSdk 33 or higher, and holding it keeps the app in the WORKING_SET standby bucket or better.
  - evidence: AOSP core/res/AndroidManifest.xml lines 5997-6022 (mirror main @1cdfff5, 2025-03-27): protectionLevel="normal"; 'Apps that hold this permission, always stay in the WORKING_SET or lower standby bucket.' developer.android.com/develop/background-work/services/alarms: 'USE_EXACT_ALARM: Granted automatically; Cannot be revoked by the user; Subject to an upcoming Google Play policy'. AlarmManager.java ENABLE_USE_EXACT_ALARM=218533173 @EnabledSince TIRAMISU. AppStandbyController.java lines 1511-1514: shouldGetExactAlarmBucketElevation returns STANDBY_BUCKET_WORKING_SET.
  - implication: For this sideloaded app, declare only android.permission.USE_EXACT_ALARM, with minSdk 33 or higher. The Play policy limits do not apply. The permission also protects the app's job and alarm quotas.
- **AL2** [high] SCHEDULE_EXACT_ALARM (signature|privileged|appop) is denied by default on fresh installs of apps targeting 33+ on Android 14+. The user grants it from 'Alarms & reminders' (Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM). Revoking it stops the app and deletes all its exact alarms. ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED is sent only when it is granted, never when it is revoked.
  - evidence: AlarmManager.java lines 151-182 (javadoc of ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED = 'android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED': 'This broadcast will not be sent when the user revokes the permission'; 'it's allowed to start a foreground service'). Lines 294-312: SCHEDULE_EXACT_ALARM_DENIED_BY_DEFAULT=226439802 since TIRAMISU; SCHEDULE_EXACT_ALARM_DOES_NOT_ELEVATE_BUCKET=262645982 since UPSIDE_DOWN_CAKE. developer.android.com/develop/background-work/services/alarms: 'When the SCHEDULE_EXACT_ALARM permission is revoked for your app, your app stops, and all future exact alarms are canceled.'
  - implication: Do not use SCHEDULE_EXACT_ALARM, because it adds a user step and a way to lose alarms. If minSdk is below 33, declare it with android:maxSdkVersion="32" as AOSP documents.
- **AL3** [high] canScheduleExactAlarms() returns true if the app holds USE_EXACT_ALARM or SCHEDULE_EXACT_ALARM, or is on the power-save allowlist. Calling an exact API without any of these throws SecurityException ('needs to hold SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM').
  - evidence: AlarmManagerService.java lines 2793-2809 (SecurityException text), 2845-2850 (canScheduleExactAlarms = isExemptFromExactAlarmPermissionNoLock || hasScheduleExactAlarmInternal || hasUseExactAlarmInternal), 2668-2676 (exempt if mLocalDeviceIdleController.isAppOnWhitelist).
  - implication: Assert canScheduleExactAlarms() at startup and before each schedule, and surface a hard error in the UI if it is false.
- **AL4** [high] setAlarmClock(AlarmClockInfo, PendingIntent) is always RTC_WAKEUP and gets FLAG_WAKE_FROM_IDLE. While the device is in Doze, AlarmManager moves the end of Doze to a random point 2 to 15 minutes before the next alarm clock. Alarm clocks are exempt from app-standby quotas, from Battery Saver deferral and from the user's background restriction, and they may start a foreground service from the background.
  - evidence: AlarmManager.java setAlarmClock javadoc ('Alarms scheduled via this API will be allowed to start a foreground service even if the app is in the background'). AlarmManagerService.java lines 2724-2728 (alarmClock -> FLAG_WAKE_FROM_IDLE), 759-760 (MIN_DEVICE_IDLE_FUZZ=2 min, MAX_DEVICE_IDLE_FUZZ=15 min), 2304-2333 (adjustIdleUntilTime), 4328-4331 (isExemptFromAppStandby: alarmClock != null), 4141-4144 (exempt from battery saver), 4155-4159 ('Don't defer alarm clocks' for background-restricted apps). doze-standby doc: 'The system exits Doze shortly before those alarms fire.'
  - implication: setAlarmClock is the most reliable wake API. It is the right fallback when the app is not on the battery allowlist or the user has restricted it.
- **AL5** [medium] setAlarmClock has visible side effects. The alarm shows as the device's next alarm clock, and Do Not Disturb schedules with 'alarm can override end time' end at the next alarm clock from any app.
  - evidence: AOSP services/core/java/com/android/server/notification/ScheduleConditionProvider.java lines 163-200 and 242-264: evaluateSubscriptions() uses mAlarmManager.getNextAlarmClock() and cal.shouldExitForAlarm(now), and listens to AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED. AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED javadoc.
  - implication: A 03:00 snipe set with setAlarmClock could end the user's Bedtime DND at 03:00 (inferred from the code path). Make alarm-clock mode a fallback or a user-visible option, not the default.
- **AL6** [high] setExactAndAllowWhileIdle fires during Doze and puts the app on the temporary power exemption list for about 10 seconds (wake locks, network, foreground-service start). AOSP code limits it to 72 per hour for apps targeting S+ that hold an exact-alarm permission, and 7 per hour in compat mode; the docs still say 'once per nine minutes'. If the user set battery to Unrestricted, every alarm the app sets gets FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED and has no Doze limit.
  - evidence: AlarmManagerService.java lines 751-755 (DEFAULT_ALLOW_WHILE_IDLE_COMPAT_QUOTA=7, DEFAULT_ALLOW_WHILE_IDLE_QUOTA=72, 1-hour windows), 728 (DEFAULT_ALLOW_WHILE_IDLE_ALLOWLIST_DURATION=10*1000), 874-889 (TEMPORARY_ALLOWLIST_TYPE_FOREGROUND_SERVICE_ALLOWED, REASON_ALARM_MANAGER_WHILE_IDLE / REASON_ALARM_MANAGER_ALARM_CLOCK), 2729-2738 (isUidPowerSaveUserExempt -> FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED), 4675-4685 (options applied at every delivery). doze-standby doc: 'Neither setAndAllowWhileIdle() nor setExactAndAllowWhileIdle() can fire alarms more than once per nine minutes, per app.' power-details doc: 'While-idle alarms: Limited to 7 per hour'.
  - implication: With USE_EXACT_ALARM and Unrestricted battery, setExactAndAllowWhileIdle is as reliable as setAlarmClock without its UI and DND side effects. Without Unrestricted, budget no more than 7 allow-while-idle alarms per hour to be safe.
- **AL7** [high] AlarmManager will not schedule an app alarm less than 5 seconds into the future (MIN_FUTURITY). Requested times closer than that are moved to now+5 s.
  - evidence: AlarmManagerService.java line 724 'DEFAULT_MIN_FUTURITY = 5 * 1000' and lines 2196-2202: 'Try to prevent spamming by making sure apps aren't firing alarms in the immediate future ... minTrigger = nowElapsed + (UserHandle.isCore(callingUid) ? 0L : mConstants.MIN_FUTURITY)'.
  - implication: The final T-5s firing must not use AlarmManager. Use an in-process Handler or coroutine timer inside the foreground service while holding a wake lock.
- **AL8** [high] Listener-based exact alarms (setExact with OnAlarmListener) need no exact-alarm permission on Android 14+. The system drops them when the app becomes cached, after a 10-second grace period. They only work while the process is alive.
  - evidence: developer.android.com/develop/background-work/services/alarms: 'If the exact alarm is set using an OnAlarmListener object ... the SCHEDULE_EXACT_ALARM permission isn't required.' AlarmManager.java EXACT_LISTENER_ALARMS_DROPPED_ON_CACHED=265195908. AlarmManagerService.java line 766 DEFAULT_CACHED_LISTENER_REMOVAL_DELAY=10_000, lines 2764-2772 (directReceiver -> needsPermission=false, lowerQuota=allowWhileIdle).
  - implication: Listener alarms are acceptable only inside the running foreground service, for example as a backup trigger. Use PendingIntent alarms for anything that must survive process death.
- **AL9** [high] App updates do not cancel alarms, except exact alarms when the app loses its permission. Reboot, force stop and uninstall cancel them. On Android 15+, force stop cancels all PendingIntents, and BOOT_COMPLETED is delivered when the user next launches the app. A 'Stop' from the foreground-service Task Manager keeps alarms and jobs.
  - evidence: AlarmManagerService.java lines 4980-4990 (PACKAGE_ADDED with EXTRA_REPLACING -> CHECK_EXACT_ALARM_PERMISSION_ON_UPDATE) and 4994-5011 ('This package is being updated; don't kill its alarms.'; PACKAGE_RESTARTED removes alarms). Alarms doc: 'By default, all alarms are canceled when a device shuts down.' developer.android.com/about/versions/15/behavior-changes-all, 'Changes to package stopped state'. developer.android.com/develop/background-work/services/fgs/handle-user-stopping: 'Alarms go off at their scheduled time or time window.'
  - implication: Reschedule every pending snipe from the database on BOOT_COMPLETED, MY_PACKAGE_REPLACED and TIME_SET. On startup, check ApplicationStartInfo.wasForceStopped() and ApplicationExitInfo REASON_USER_REQUESTED and warn the user.
- **AL10** [high] RTC and RTC_WAKEUP alarms are re-evaluated when the wall clock changes, so they follow the new wall time. ELAPSED_REALTIME alarms do not move.
  - evidence: AlarmManagerService.java lines 1374-1394 (reevaluateRtcAlarms: 'if (!isRtc(a.type)) return false; return restoreRequestedTime(a)'), lines 4466-4494 (TIME_CHANGED_MASK handling then ACTION_TIME_CHANGED broadcast).
  - implication: Compute alarm times from a true (SNTP-corrected) UTC anchor. Prefer ELAPSED_REALTIME_WAKEUP for setExactAndAllowWhileIdle. setAlarmClock is RTC only, which is fine for a coarse T-120 s wake.
- **FG1** [high] specialUse is the best foreground-service type for a sideloaded app's 1-3 minute 'prepare then bid' window. It has no time limit, needs only FOREGROUND_SERVICE_SPECIAL_USE (normal|appop|instant) plus a PROPERTY_SPECIAL_USE_FGS_SUBTYPE manifest property, and is on the BOOT_COMPLETED allowlist.
  - evidence: developer.android.com/develop/background-work/services/fgs/service-types ('Special use ... <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="explanation_for_special_use"/>; values reviewed when you submit in Play Console'). AOSP AndroidManifest.xml lines 7830-7833 (FOREGROUND_SERVICE_SPECIAL_USE protectionLevel normal|appop|instant). ActivityManagerConstants.java lines 191-197 (DEFAULT_FGS_BOOT_COMPLETED_ALLOWLIST includes FOREGROUND_SERVICE_TYPE_SPECIAL_USE).
  - implication: Declare android:foregroundServiceType="specialUse" and call ServiceCompat.startForeground(..., ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE). Play review is irrelevant when sideloading.
- **FG2** [high] shortService runs for about 3 minutes and then Service.onTimeout() is called. The app must stop within seconds or it is cached and then gets an ANR. shortService cannot be sticky, cannot start other foreground services, and is not on the BOOT_COMPLETED allowlist for apps targeting SDK 35+. Disabling battery optimization does not remove the timeout.
  - evidence: service-types doc, 'Short service' ('Can only run for a short period of time (about 3 minutes)'; 'If a user disables battery optimization for your app, it's still affected by the timeout'; extend only if visible or exempt). ActivityManagerConstants.java line 1070 DEFAULT_SHORT_FGS_TIMEOUT_DURATION=3*60_000, line 1083 PROC_STATE_EXTRA_WAIT 5_000, line 1134 ANR_EXTRA_WAIT 10_000. ActiveServices.java lines 365-379 (FGS_BOOT_COMPLETED_RESTRICTIONS: 'Disables ... BOOT_COMPLETED ... for all types except' location, connectedDevice, remoteMessaging, health, systemExempted, specialUse).
  - implication: shortService fits a 20-60 s price check but is too tight for wake at T-120 s plus confirm and result polling. Use it only for short checks.
- **FG3** [high] For apps targeting SDK 35+, dataSync services may run 6 hours in total per 24 hours and then get Service.onTimeout(int,int). Apps targeting SDK 35+ cannot start dataSync from BOOT_COMPLETED.
  - evidence: developer.android.com/about/versions/15/behavior-changes-15, 'Data sync foreground service timeout behavior' and 'Restrictions on BOOT_COMPLETED broadcast receivers launching foreground services' (dataSync, camera, mediaPlayback, phoneCall, mediaProjection, microphone). ActivityManagerConstants.java line 1105 DEFAULT_DATA_SYNC_FGS_TIMEOUT_DURATION = 6h.
  - implication: dataSync would work for short windows but has no advantage over specialUse and fails for snipes due right after boot.
- **FG4** [high] An exact alarm (setExactAndAllowWhileIdle or setAlarmClock) from an app holding an exact-alarm permission lets it start a foreground service from the background. The window comes from a 10-second temporary allowlist set at delivery.
  - evidence: fgs/restrictions-bg-start doc: 'Your app invokes an exact alarm to complete an action that the user requests.' Alarms doc: 'exact alarms aren't affected by foreground service launch restrictions'. AlarmManagerService.java lines 2773-2777 (idleOptions = mOptsWithFgsForAlarmClock or mOptsWithFgs for exact alarms) and 874-889 (10 s, TEMPORARY_ALLOWLIST_TYPE_FOREGROUND_SERVICE_ALLOWED). Lines 2810-2817: apps allowed only via the battery allowlist get options without FGS ('AlarmClock alarms do not get the temporary allow-list'), but are already exempt.
  - implication: In the alarm receiver's onReceive, call ContextCompat.startForegroundService() right away, within 10 s. Catch ForegroundServiceStartNotAllowedException and fall back to a 'tap to arm' notification.
- **FG5** [high] Other ways to start a foreground service from the background that matter here: the user turned off battery optimization; BOOT_COMPLETED, LOCKED_BOOT_COMPLETED or MY_PACKAGE_REPLACED; TIMEZONE_CHANGED, TIME_CHANGED or LOCALE_CHANGED; the user tapping a notification or widget; SYSTEM_ALERT_WINDOW, which for apps targeting SDK 35+ also needs an overlay window that is visible at that moment.
  - evidence: developer.android.com/develop/background-work/services/fgs/restrictions-bg-start, 'Exemptions from background start restrictions'. behavior-changes-15, 'Restrictions on starting foreground services while an app holds the SYSTEM_ALERT_WINDOW permission'. ActiveServices.java lines 1170-1180: the BOOT_COMPLETED type filter applies only when fgsStartReasonCode == REASON_BOOT_COMPLETED.
  - implication: Unrestricted battery adds a second, independent exemption on top of the alarm exemption. MY_PACKAGE_REPLACED can restart a snipe window that an app update interrupted.
- **FG6** [high] AOSP gives startForeground() 30 s after startForegroundService(), plus 10 s before an ANR. Android 12+ may delay showing a foreground-service notification unless the app requests FOREGROUND_SERVICE_IMMEDIATE.
  - evidence: ActivityManagerConstants.java lines 304-306 and 845-859 (DEFAULT_SERVICE_START_FOREGROUND_TIMEOUT_MS = 30*1000; DEFAULT_SERVICE_START_FOREGROUND_ANR_DELAY_MS = 10*1000). Notification.Builder#setForegroundServiceBehavior (API 31) docs: 'Pass FOREGROUND_SERVICE_IMMEDIATE ... to guarantee that visibility is never deferred.'
  - implication: Call startForeground as the first statement in onStartCommand, with setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE), so the countdown is visible at once.
- **FG7** [high] The systemExempted foreground-service type is technically allowed for any app holding SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM, plus FOREGROUND_SERVICE_SYSTEM_EXEMPTED (normal|instant). The docs describe it as reserved for system integrations.
  - evidence: AOSP core/java/android/app/ForegroundServiceTypePolicy.java lines 542-558 (anyOf SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM, OP_ACTIVATE_VPN). core/java/android/content/pm/ServiceInfo.java lines 395-421. AndroidManifest.xml lines 7801-7804. service-types doc: 'Reserved for system applications and specific system integrations'.
  - implication: There is no advantage over specialUse (no extra exemptions found in ActiveServices), and the type is semantically wrong. Do not use it.
- **FG8** [high] Android 13+ lets the user stop an app from the 'Active apps' Task Manager. That kills the whole app without a callback, but jobs and alarms still run on schedule.
  - evidence: developer.android.com/develop/background-work/services/fgs/handle-user-stopping ('Scheduled jobs execute at their scheduled time. Alarms go off at their scheduled time or time window.'; ApplicationExitInfo REASON_USER_REQUESTED; test with 'adb shell cmd activity stop-app PACKAGE_NAME').
  - implication: A backup alarm (for example T-75 s) recovers a snipe if the user taps Stop during the window. Detect REASON_USER_REQUESTED and treat it as a cancel only if the user confirms.
- **NW1** [high] Doze cuts network access, ignores wake locks and defers jobs and normal alarms. In AOSP, a process with state at or above BOUND_FOREGROUND_SERVICE, which includes an app running its own foreground service, keeps network and partial wake locks in Doze and Battery Saver.
  - evidence: developer.android.com/training/monitoring-device-state/doze-standby ('Suspends network access. Ignores wake locks. Defers standard AlarmManager alarms ...'). NetworkPolicyManager.java lines 188-189 (FOREGROUND_THRESHOLD_STATE = PROCESS_STATE_BOUND_FOREGROUND_SERVICE), 831-838 (isProcStateAllowedWhileIdleOrPowerSaveMode: procState <= FOREGROUND_THRESHOLD_STATE || POWER_RESTRICTED_NETWORK capability). PowerManagerService.java lines 4332-4342 (idle mode disables partial wake locks unless allowlisted, temp-allowlisted, or procState <= PROCESS_STATE_BOUND_FOREGROUND_SERVICE). power-details doc: 'App process is running a foreground service | ... | Network: No restrictions'.
  - implication: Run all snipe network traffic (SNTP, WebView, confirm) inside the foreground service. Do not do it in a plain receiver after the 10 s temporary allowlist expires.
- **NW2** [high] Since Android 15 (all apps), a network request started outside a valid process lifecycle fails with UnknownHostException or another IOException. In AOSP, the background network rule allows process states below TOP_SLEEPING and blocks cached processes after a 2-5 s debounce.
  - evidence: developer.android.com/about/versions/15/behavior-changes-all, 'Background network access restrictions'. NetworkPolicyManager.java line 195 (BACKGROUND_THRESHOLD_STATE = PROCESS_STATE_TOP_SLEEPING), 855-861. NetworkPolicyManagerService.java lines 557-592 (mBackgroundRestrictionDelayMs 5 s; short 2 s / long 20 s).
  - implication: Fire-and-forget network calls after a receiver returns can fail. Keep the foreground service, or a WorkManager job, alive until every request has finished.
- **NW3** [high] 'Unrestricted' battery puts the app on the user power allowlist. It then keeps network and partial wake locks in Doze, its alarms ignore Doze, Battery Saver and app-standby limits, and AppStandbyController gives it STANDBY_BUCKET_EXEMPTED. The app asks via Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (normal), and checks with PowerManager.isIgnoringBatteryOptimizations().
  - evidence: doze-standby doc: 'An app that is partially exempt can use the network and hold partial wake locks during Doze and App Standby.' Manifest.permission reference: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 'Added in API level 23 ... Protection level: normal'. AppStandbyController.java lines 1464-1465 (isNonIdleWhitelisted -> STANDBY_BUCKET_EXEMPTED), 2743-2753 (cache from getFullPowerWhitelistExceptIdle). DeviceIdleController.java lines 3110-3129 (includes mPowerSaveWhitelistUserApps). AlarmManagerService.java lines 2729-2738. Pixel UI (support.google.com/pixelphone/answer/7015477): Apps > See all apps > [app] > App battery usage > Allow background usage > Optimised (other option Unrestricted, label not shown on that page).
  - implication: Make Unrestricted battery a first-run onboarding step. Re-check it before arming each snipe and show a warning if it was revoked. The Play policy that limits this request does not apply to a sideloaded app.
- **NW4** [high] Under Data Saver and per-app background-data restriction, a foreground-service process still counts as foreground and keeps metered network.
  - evidence: NetworkPolicyManager.java lines 868-886 (isProcStateAllowedWhileOnRestrictBackground: procState <= FOREGROUND_THRESHOLD_STATE, or user-initiated-job capability). power-details doc: 'User manually unrestricts app battery ... Unrestricted unless device is in data saver mode'.
  - implication: Data Saver does not need special handling during the snipe service. Price checks run as WorkManager jobs may be blocked on mobile data while Data Saver is on.
- **NW5** [high] Standby-bucket limits (approximate): Active, jobs 20 min per 60 min and alarms unlimited; Working set, jobs 10 min per 4 h and 10 alarms per hour; Frequent, 10 min per 12 h and 2 alarms per hour; Rare, 10 min per 24 h, 1 alarm per hour and network disabled; Restricted, once a day and 1 alarm per day. In AOSP, allow-while-idle alarms and alarm clocks are exempt from the per-bucket alarm quota.
  - evidence: developer.android.com/topic/performance/power/power-details (table 'Resource limits based on app standby bucket'). AlarmManagerService.java lines 735-741 (DEFAULT_APP_STANDBY_QUOTAS 720/10/2/1/0) and 4328-4331 (isExemptFromAppStandby includes FLAG_ALLOW_WHILE_IDLE and alarmClock).
  - implication: USE_EXACT_ALARM (WORKING_SET or better) plus Unrestricted (EXEMPTED) avoids the Rare and Restricted buckets, which would disable background network for price checks.
- **NW6** [medium] If a home-screen widget from the app is placed, AppStandbyController keeps the app in at least STANDBY_BUCKET_ACTIVE.
  - evidence: AOSP AppStandbyController.java lines 1498-1501: 'if (mAppWidgetManager != null && mInjector.isBoundWidgetPackage(mAppWidgetManager, packageName, userId)) { return STANDBY_BUCKET_ACTIVE; }' (mirror main 2025-03-27).
  - implication: A Glance widget showing upcoming snipes is a useful extra protection if the user declines Unrestricted. It is also a useful feature. Device behavior on Android 17 is unverified.
- **WV1** [high] Chromium WebView reports a WebView that was never attached to a window as visible unless onPause() was called. Once attached, it is visible only while attached with a visible window, so detaching it later makes it hidden.
  - evidence: chromium/chromium android_webview/browser/gfx/browser_view_renderer.cc (last commit 2026-10-28) lines 561-569: 'When WebView is not paused, we declare it visible even before it is attached to window to allow for background operations ... return is_paused_ ? false : !was_attached_ || (attached_to_window_ && window_visible_);'. Lines 507-528 set was_attached_=true in OnAttachedToWindow. aw_contents.cc (2026-11-02) line 1168: AwContents::IsVisible returns browser_view_renderer_.IsClientVisible(). AwContents.java (2026-10-31) lines 3976-3988 map this to WebContents Visibility.VISIBLE/HIDDEN.
  - implication: Create a fresh WebView for the snipe inside the service and never add it to a window. Its page visibility stays 'visible' with the screen off, so hidden-page timer throttling does not apply. Do not reuse the sign-in WebView that was on screen.
- **WV2** [high] WebView.pauseTimers() is global: it pauses layout, parsing and JavaScript timers for every WebView in the process. WebView.onPause() does not pause JavaScript but marks the page hidden.
  - evidence: AOSP core/java/android/webkit/WebView.java lines 1368-1373 ('Pauses all layout, parsing, and JavaScript timers for all WebViews. This is a global requests') and 1387-1395 ('Note that this call does not pause JavaScript'). Chromium AwContents.java lines 3063-3101 (pauseTimers -> ContentViewStatics.setWebKitSharedTimersSuspended(true); onPause -> setIsPaused + updateWebContentsVisibility).
  - implication: Never call pauseTimers() in the app, for example in an Activity's onPause, because it would freeze the headless snipe WebView. Never call onPause() on the snipe WebView.
- **WV3** [high] Every method on a WebView must be called on the thread whose Looper created it. For modern targets a wrong-thread call throws RuntimeException('All WebView methods must be called on the same thread'). evaluateJavascript must be called on the UI thread.
  - evidence: WebView.java lines 2615-2635 (checkThread with mWebViewThread = Looper.myLooper(); sEnforceThreadChecking throws) and lines 878-879 (evaluateJavascript: 'This method must be called on the UI thread and the callback will be made on the UI thread').
  - implication: Create the WebView in Service.onCreate or onStartCommand (main thread) and post the fire runnable to the main Handler. Keep the main thread idle during the last seconds so the timer is not delayed.
- **WV4** [high] The WebView renderer process is bound with BIND_IMPORTANT by default (RENDERER_PRIORITY_IMPORTANT, not waived when not visible). The renderer can still die, and WebViewClient.onRenderProcessGone (API 26) must return true or the app crashes.
  - evidence: WebView.java lines 2302-2342 ('The default policy is to set the priority to RENDERER_PRIORITY_IMPORTANT regardless of visibility'). Chromium AwContents.java lines 4198-4218 (updateChildProcessImportance) and line 1098 (default RendererPriority.HIGH).
  - implication: Keep the default renderer priority. Implement onRenderProcessGone to destroy and rebuild the WebView and reload the page if there is still time before T. Otherwise fail with a notification.
- **WV5** [high] WebView data (cookies, storage) belongs to one process. If two processes of the app use WebView, the second must call WebView.setDataDirectorySuffix() and cannot share cookies directly.
  - evidence: WebView.java lines 2045-2075 ('Most applications should simply ensure that all components of the app that rely on WebView are in the same process'; 'login cookies may have to be copied from one process's cookie jar to the other using CookieManager').
  - implication: Run the snipe service in the default process (no android:process). The sign-in WebView and the headless WebView then share one cookie store.
- **WV6** [low] WebView begin-frames, and so requestAnimationFrame, come from a 60 Hz Java-aligned source tied to the Android frame clock. A never-attached WebView also has size 0x0 unless the app calls measure() and layout() on it. Whether rAF runs with the screen off, and how pages lay out at 0x0, is unverified.
  - evidence: chromium android_webview/browser/gfx/begin_frame_source_webview.cc (2026-06-02) lines 127-134 (RootBeginFrameSourceWebView: begin_frame_source_(kNotRestartableId, 60.0f, requires_align_with_java=true)). browser_view_renderer.cc lines 571-578 (parent source attached only when IsClientVisible()). Display-off vsync behavior: inferred, not read.
  - implication: Bid automation should use evaluateJavascript and DOM APIs, not rAF or animations. Give the WebView a phone-sized viewport (measure/layout to 1080x2400, inferred to work while detached), and test the eBay flow with the screen off before relying on it.
- **WV7** [high] The WebView cookie store keeps session cookies across restarts and writes to disk every 30 s, after 512 pending operations, or on flush(). CookieManager.flush() (API 21) blocks and does I/O.
  - evidence: chromium android_webview/browser/cookie_manager.cc (2026-10-09) lines 299-303: CookieStoreConfig(cookie_store_path_, restore_old_session_cookies=true, persist_session_cookies=true). net/extras/sqlite/sqlite_persistent_cookie_store.cc (2026-10-05) lines 1237-1239: kCommitInterval = base::Seconds(30); kCommitAfterBatchSize = 512. CookieManager reference: flush 'Ensures all cookies currently accessible through the getCookie API are written to persistent storage. This call will block the caller until it is done and may perform I/O.'
  - implication: Call CookieManager.getInstance().flush() off the main thread right after sign-in succeeds and after each snipe, or a process kill within 30 s loses the login.
- **WV8** [high] CookieManager.getCookie(url) returns all cookies for the URL, HttpOnly ones included, as 'k=v; k2=v2' without attributes. CookieManagerCompat.getCookieInfo (androidx.webkit 1.6.0) returns full Set-Cookie attribute lines.
  - evidence: chromium cookie_manager.cc lines 651-653: GetCookieListAsyncHelper uses net::CookieOptions::MakeAllInclusive(). CookieManager reference getCookie text. androidx.webkit release notes 1.6.0 (Jan 25, 2023): 'getCookieInfo ... retrieves all attributes for all cookies'.
  - implication: An OkHttp CookieJar can be backed by CookieManager (getCookie in loadForRequest, setCookie per Set-Cookie header in saveFromResponse). Also send WebSettings.getDefaultUserAgent(context). OkHttp's TLS fingerprint differs from WebView's, so doing the bid in the WebView is less likely to trip bot detection (inferred).
- **WV9** [high] androidx.webkit 1.18.0 is stable (Oct 21, 2026). WebViewCompat.startUpWebView with WebViewStartUpConfig became stable in 1.16.0 (May 6, 2026) and does part of WebView start-up in the background. Profile#warmUpRendererProcess is experimental (1.15.0, Dec 17, 2025).
  - evidence: developer.android.com/jetpack/androidx/releases/webkit (latest stable 1.18.0 Oct 21 2026; 1.16.0 'The startUpWebView API and related async startup configuration APIs ... graduated from experimental to stable'; 1.15.0 warmUpRendererProcess).
  - implication: In the T-120 s wake path, call startUpWebView before building the WebView to save part of the cold-start cost (the gain is unmeasured).
- **SI1** [medium] WebAuthn and passkeys in WebView need androidx.webkit 1.12.0+ (stable Sept 18, 2024), WebViewFeature.WEB_AUTHENTICATION, and WebSettingsCompat.setWebAuthenticationSupport(settings, WEB_AUTHENTICATION_SUPPORT_FOR_APP), called on the UI thread. FOR_APP only allows WebAuthn for the app's Digital-Asset-Links-associated site. FOR_BROWSER is for privileged browsers. mediation:'conditional' is not supported.
  - evidence: developer.android.com/reference/androidx/webkit/WebSettingsCompat: 'WEB_AUTHENTICATION_SUPPORT_FOR_APP = 1 ... allows WebAuthn requests for the app in which the WebView is embedded. See Digital Asset Links'; 'WEB_AUTHENTICATION_SUPPORT_NONE ... This is the default'. developer.android.com/identity/sign-in/credential-manager-webview ('associate your app with a website that your app owns using digital asset linking'; 'The WebKit library doesn't support mediation:"conditional" requests'). androidx.webkit release notes 1.12.0 (Sept 18, 2024).
  - implication: eBay's site is not associated with our app's signing key, so eBay passkeys and security keys should fail in our WebView (inferred; eBay's assetlinks.json could not be fetched). Have the user sign in with password plus SMS or authenticator code, and detect a passkey prompt to explain this.
- **SI2** [high] Google blocks OAuth sign-in in embedded webviews, Android WebView included, with error 'disallowed_useragent'. Enforcement began September 30, 2021.
  - evidence: developers.googleblog.com 'Upcoming security changes to Google's OAuth 2.0 authorization endpoint in embedded webviews', June 29, 2021: 'All embedded webviews will be blocked starting on September 30, 2021.'
  - implication: 'Continue with Google' on eBay's sign-in page will fail in the app. If the user's eBay account only uses Google sign-in, they must first set an eBay password.
- **SI3** [high] Meta stopped supporting Facebook Login on Android embedded browsers (WebViews) on October 5, 2021.
  - evidence: developers.facebook.com/blog/post/2021/06/28/deprecating-support-fb-login-authentication-android-embedded-browsers/ (June 28, 2021, updated Aug 27, 2021): 'We will continue to support FB Login authentication on Android embedded browsers until October 5, 2021.'
  - implication: 'Continue with Facebook' on eBay will also fail. Detect navigation to accounts.google.com or facebook.com/login in shouldOverrideUrlLoading and tell the user to use a password.
- **SI4** [high] Android 17's SMS OTP protection (3-hour hold on OTP SMS for non-recipient apps) only affects apps that read SMS programmatically. A code the user reads and types into the WebView is not affected.
  - evidence: developer.android.com/about/versions/17/behavior-changes-all, 'SMS OTP protection' (WebOTP format, all apps); behavior-changes-17, 'OTP protection for standard SMS messages' (target 37).
  - implication: Do not build SMS auto-fill for eBay codes. Let the user type them, or use an authenticator app.
- **TM1** [high] Android syncs the system clock by SNTP about once a day (AOSP default: ntp://time.android.com, 18 h poll, 5 s timeout). It only changes the clock when the new time differs by more than about 2 s, and Google states a maximum theoretical SNTP error of about 2.5 s.
  - evidence: source.android.com/docs/core/connect/time/network-time-detection: config_ntpPollingInterval 64800000; config_ntpServers ntp://time.android.com; config_ntpTimeout 5000; 'Android's default network time sync uses SNTP with a single time query approximately once a day'; 'the maximum theoretical error is approximately 2.5 seconds'. source.android.com/docs/core/connect/time: leaves time unchanged 'to within a couple of seconds', dump shows systemClockUpdateThresholdMillis()=2000. AOSP config.xml lines 2025-2028 (config_timeDetectorAutoUpdateDiffMillis 2000) and 2693-2711.
  - implication: System.currentTimeMillis() can be about 2 s off by design, which is too much for a T-5s snipe. The app must measure its own offset just before each snipe. Pixel overlay values are not public (assumed AOSP defaults).
- **TM2** [high] SystemClock.currentNetworkTimeClock() (API 33) returns the last network (NTP) time result advanced by elapsedRealtime. It never blocks, throws DateTimeException if no network time is available, and cannot be changed by the user.
  - evidence: developer.android.com/reference/android/os/SystemClock#currentNetworkTimeClock(): 'This performs no blocking network operations and returns values based on a recent successful synchronization event'; 'accuracy of the returned times cannot be guaranteed'. AOSP SystemClock.java lines 347-377: time.getUnixEpochTimeMillis() + (elapsedRealtime() - time.getElapsedRealtimeMillis()).
  - implication: Use it as a sanity check and fallback. Its error includes up to about 18-24 h of oscillator drift since the last sync (inferred), so prefer a fresh app-level SNTP measurement.
- **TM3** [high] Google Public NTP (time.google.com, time1-4.google.com) applies a leap smear, and Google warns against mixing smeared and non-smeared servers.
  - evidence: developers.google.com/time/guides: servers 'serve the same time using the same smear'; 'prevent your machine from trying to mix smeared and non-smeared servers'.
  - implication: Run SNTP against time.google.com only. Take 4-8 samples a few hundred ms apart, keep the one with the lowest round-trip time, and anchor true time to elapsedRealtime. This needs no permission beyond INTERNET (public address).
- **TM4** [high] Handler, Thread.sleep and Object.wait use uptimeMillis, which stops in deep sleep. elapsedRealtime keeps counting through sleep.
  - evidence: developer.android.com/reference/android/os/SystemClock class overview ('uptimeMillis() ... stops when the system enters deep sleep'; 'Handler objects also use the uptimeMillis() clock'). Alarms doc: Handler 'APIs rely on system uptime and not real time'.
  - implication: Hold a PARTIAL_WAKE_LOCK (WAKE_LOCK permission) from the T-120 s wake until the result is known, using acquire(timeout). Compute the fire time from an elapsedRealtime anchor, then convert to Handler.postAtTime(uptime + delta).
- **RB1** [high] BOOT_COMPLETED needs RECEIVE_BOOT_COMPLETED and is only delivered after the user has launched the app at least once. BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, TIME_SET and TIMEZONE_CHANGED are exempt from the Android 8 ban on implicit broadcasts in the manifest. ACTION_PACKAGE_REPLACED is not.
  - evidence: Alarms doc, 'Start an alarm when the device restarts' ('this only works if the app has already been launched by the user at least once'). developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions (LOCKED_BOOT_COMPLETED/BOOT_COMPLETED; 'android.intent.action.TIME_SET, ACTION_TIMEZONE_CHANGED'; 'ACTION_PACKAGE_REPLACED are not exempted').
  - implication: Use manifest receivers for BOOT_COMPLETED, TIME_SET and TIMEZONE_CHANGED. For updates, use MY_PACKAGE_REPLACED, which is sent only to the updated app (from Intent docs, not re-fetched).
- **RB2** [high] After a reboot, credential-encrypted storage, where the WebView cookie database lives, stays locked until the user unlocks the phone. Only directBootAware components and device-protected storage (createDeviceProtectedStorageContext()) work before that, triggered by LOCKED_BOOT_COMPLETED.
  - evidence: developer.android.com/privacy-and-security/direct-boot ('Credential encrypted storage ... only available after the user has unlocked the device'; android:directBootAware; ACTION_LOCKED_BOOT_COMPLETED; ACTION_USER_UNLOCKED; UserManager.isUserUnlocked()).
  - implication: A phone that reboots and is not unlocked cannot snipe. Keep a minimal copy of the snipe list in device-protected storage, and post a 'Unlock to arm snipe ending HH:MM' notification from a directBootAware LOCKED_BOOT_COMPLETED receiver.
- **RB3** [medium] Google Play services 25.14 (April 2025) added an automatic restart of phones locked for 3 days in a row.
  - evidence: 9to5google.com/2025/04/14/android-auto-reboot-security/ (April 16, 2025): Play services 25.14 adds a feature where 'your device automatically restarts if locked for 3 consecutive days'.
  - implication: For snipes more than about 3 days away with the phone left locked, expect a reboot into the locked state that blocks the snipe (see RB2). Warn on long-dated snipes.
- **WM1** [high] WorkManager (2.12.0 stable, Sept 23, 2026): PeriodicWorkRequest needs a repeat interval of at least 15 min (MIN_PERIODIC_INTERVAL_MILLIS) and a flex of at least 5 min (MIN_PERIODIC_FLEX_MILLIS). Minimum backoff is 10 s (default exponential, 30 s). setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST or DROP_WORK_REQUEST) uses a standby-bucket quota. Constraints are NetworkType, BatteryNotLow, RequiresCharging, DeviceIdle and StorageNotLow. Jobs do not run during Doze.
  - evidence: developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work ('The minimum repeat interval that can be defined is 15 minutes'; quota policies; backoff 'no less than 10 seconds'). doze-standby doc: 'WorkManager uses JobScheduler internally, so WorkManager tasks don't run.' developer.android.com/jetpack/androidx/releases/work (2.12.0 Sept 23, 2026). androidx work-runtime AndroidManifest.xml (2025-08-01): RescheduleReceiver for BOOT_COMPLETED.
  - implication: Use WorkManager only for slow price checks (every 30-60 min when the end is hours away). Use exact alarms for checks in the final hour and for the snipe itself.
- **NT1** [high] POST_NOTIFICATIONS is a dangerous runtime permission (API 33) and covers foreground-service notifications too. Channels set importance. Android 8.1+ plays at most one notification sound per second per app, and very frequent updates may be dropped.
  - evidence: Manifest.permission reference: POST_NOTIFICATIONS 'Added in API level 33 ... Protection level: dangerous'. developer.android.com/develop/ui/views/notifications/build-notification ('apps can't make a notification sound more than once per second'; 'If you post updates to a notification too frequently ... the system might drop updates').
  - implication: Request POST_NOTIFICATIONS during onboarding. Use separate channels: 'Snipe in progress' (DEFAULT, for the Live Update), 'Price over max / outbid' (HIGH), 'Results' (DEFAULT). Never update the notification every second; use the chronometer.
- **NT2** [high] A notification becomes a Live Update (promoted) when all of these hold: standard, BigTextStyle, CallStyle, ProgressStyle or MetricStyle; POST_PROMOTED_NOTIFICATIONS declared (added 36.1, normal|appops); setRequestPromotedOngoing(true) or EXTRA_REQUEST_PROMOTED_ONGOING; ongoing; contentTitle set; no custom content view; not a group summary; not colorized; channel above IMPORTANCE_MIN. The checks are Notification.FLAG_PROMOTED_ONGOING, Notification.hasPromotableCharacteristics() and NotificationManager.canPostPromotedNotifications(), and Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS opens the user setting.
  - evidence: developer.android.com/develop/ui/views/notifications/live-update (requirements list, 'Promotion characteristics', usage criteria: 'Inappropriate uses: Ads, promotions, chat messages, alerts, upcoming calendar events'; 'events that are about to start can use a Live Update'). Manifest.permission reference: POST_PROMOTED_NOTIFICATIONS 'Added in version 36.1 ... required in addition to POST_NOTIFICATIONS. Protection level: normal|appops'. Notification.Builder reference: setRequestPromotedOngoing 'Added in version 36.1'. android/platform-samples live-updates AndroidManifest.xml and SnackbarNotificationManager.kt (2026-06-04/07-09).
  - implication: Make the snipe service's notification a Live Update only from the T-120 s wake (or the last few minutes), not for days ahead. Check canPostPromotedNotifications() and deep-link to the setting if it is false.
- **NT3** [high] Chip content order for a promoted notification: setShortCriticalText (API 36, suggested 7 characters or fewer) wins; then a MetricStyle critical metric; then time from 'when'. With setUsesChronometer(true) and setChronometerCountDown(true) the chip shows a live countdown while it is positive. Otherwise with setShowWhen(true) it shows the time left until 'when'. The chip is at most 96dp wide.
  - evidence: Notification.Builder#setShortCriticalText reference ('Short critical text will always be the highest precedence content for a chip ... If setUsesChronometer(boolean) is true, the chip content will be a chronometer, if that is positive'). live-update doc 'Status chips' ('The chip has a maximum width of 96dp'; 'If less than 7 characters, show the whole text'). Sample: .setWhen(...).setUsesChronometer(true).setChronometerCountDown(true).
  - implication: For a countdown chip, set when to the auction end in device-clock terms (trueEnd minus the measured offset), set the two chronometer flags, and leave short text unset. Switch to setShortCriticalText("Bid!") or ("Won") at the end.
- **NT4** [high] Notification.ProgressStyle is API 36. Its methods are setProgress, setProgressIndeterminate, setProgressSegments/addProgressSegment(Segment), setProgressPoints/addProgressPoint(Point), setProgressTrackerIcon, setProgressStartIcon, setProgressEndIcon and setStyledByProgress. NotificationCompat.ProgressStyle and NotificationCompat.Builder.setRequestPromotedOngoing arrived in androidx.core 1.17.0-alpha01 (June 18, 2025), and MetricStyle and semantic styles in androidx.core 1.19.0-alpha01 (April 8, 2026).
  - evidence: developer.android.com/reference/android/app/Notification.ProgressStyle ('Added in API level 36', method list). developer.android.com/jetpack/androidx/releases/core (1.17.0-alpha01, June 18, 2025: 'Added NotificationCompat.ProgressStyle and NotificationCompat.Builder.setRequestPromotedOngoing()'; 1.19.0-alpha01 Apr 08 2026 MetricStyle; latest stable 1.19.1 Sept 23 2026).
  - implication: A progress bar can show the phases Armed, Synced, Page ready, Bid sent, Confirmed using points. Use NotificationCompat on androidx.core 1.17+ (MetricStyle needs 1.19).
- **NT5** [high] An inline 'raise max bid' action uses RemoteInput.Builder(KEY).setLabel(...) on a NotificationCompat.Action with addRemoteInput, backed by a PendingIntent with FLAG_MUTABLE. The receiver reads RemoteInput.getResultsFromIntent(intent) and must re-post the notification with the same id to clear the reply UI. A notification can have up to three actions.
  - evidence: developer.android.com/develop/ui/views/notifications/build-notification, 'Add a direct reply action' (PendingIntent.FLAG_MUTABLE in the example; 'After you process the text, update the notification ... This is necessary to hide the direct reply UI'; 'A notification can offer up to three action buttons'). Android 14: a mutable PendingIntent must use an explicit intent (behavior-changes-14, 'Restrictions to implicit and pending intents').
  - implication: Use explicit intents to an exported=false receiver, and a request code unique per snipe. Validate the typed amount (GBP or other currency, and above the current price) before saving. RemoteInput.Builder.setChoices could offer quick increments (inferred).
- **NT6** [medium] Full-screen intents need USE_FULL_SCREEN_INTENT (normal|appop) and a channel with IMPORTANCE_HIGH or above. They launch only when the device is locked or the screen is off, and show a heads-up while the phone is in use. For apps targeting 34+, Play revokes the default grant except for calling and alarm apps. Check NotificationManager.canUseFullScreenIntent() and send the user to Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT.
  - evidence: behavior-changes-14, 'Secure full-screen Intent notifications'. Notification.Builder#setFullScreenIntent reference ('To be launched as a full screen intent, the notification must also be posted to a channel with importance level set to IMPORTANCE_HIGH or higher'). AOSP AndroidManifest.xml lines 7956-7959 (protectionLevel normal|appop). AppOpsManager.java line 3102 (no forced default mode).
  - implication: The core design does not need a full-screen intent. For a sideloaded app the permission is probably granted (inferred), but check canUseFullScreenIntent() before relying on it in any lock-screen Activity fallback.
- **GW1** [high] Glance (androidx.glance:glance-appwidget) 1.2.0 is stable (Aug 26, 2026). For apps targeting 37, Android 17 rejects a widget update whose RemoteViews bitmaps exceed 6*width*height bytes (IllegalArgumentException).
  - evidence: developer.android.com/jetpack/androidx/releases/glance (1.2.0, August 26, 2026; 1.3.0-alpha02 July 01, 2026). behavior-changes-17, 'Memory limit widget'.
  - implication: An optional 'next snipes' widget with countdown and price is cheap to build. It also keeps the app in the ACTIVE bucket (NW6). Keep item thumbnails small (RGB_565, downsampled).
- **OS1** [high] Android developer verification for apps installed outside Play began on certified devices in Brazil, Indonesia, Singapore and Thailand on Sept 30, 2026, with global rollout in 2027. Limited-distribution accounts (up to 20 devices) and a power-user 'advanced flow' exist. Android 17 Advanced Protection Mode, when the user enables it, blocks installing apps from unknown sources.
  - evidence: developer.android.com/developer-verification (timeline: 'August 2026: Developer APIs, limited distribution accounts, and power user advanced flow launch'; 'September 30, 2026: Protections begin ... Brazil, Indonesia, Singapore, and Thailand'; '2027 and beyond'). developer.android.com/about/versions/17/features, 'Android Advanced Protection Mode (AAPM)': 'blocking app installation from unknown sources (sideloading)'.
  - implication: A UK user can sideload freely in Nov 2026. Plan a limited-distribution registration before the 2027 global rollout, and tell the user that enabling AAPM blocks installing updates outside Play (inferred).
- **OS2** [high] Alarm PendingIntents are sent with background activity launch disallowed, so an alarm cannot open an Activity by itself. Fallback paths to an on-screen Activity are SYSTEM_ALERT_WINDOW granted by the user (a BAL exemption) or a full-screen-intent notification. PendingIntent creators targeting 35+ and senders targeting 34+ must opt in explicitly, and Android 17 extends this to IntentSender.
  - evidence: AlarmManagerService.java lines 1776-1778 (setPendingIntentBackgroundActivityLaunchAllowed(false) on alarm options) and 4675-4685. developer.android.com/guide/components/activities/background-starts (exceptions list includes 'The app has the SYSTEM_ALERT_WINDOW permission granted by the user'; creator opt-in setPendingIntentCreatorBackgroundActivityStartMode for target 35+; sender opt-in for 34+; IntentSender.sendIntent opt-in for 37+).
  - implication: Keep the snipe headless (WV1). Use an Activity over the lock screen (setShowWhenLocked(true), setTurnScreenOn(true), API 27) only as a last-resort option, launched by a full-screen intent.

## Open questions
- Which exact build is on the user's Pixel 9a today: Android 17 QPR1 (37.1, CP3x) or still 17.0? This is inferred from the Sept 15, 2026 Pixel bulletin; check Settings > About phone > Android version and the build number.
- Does requestAnimationFrame, and do eBay page scripts that wait on animations or visibility, run in a never-attached WebView with the screen off? Does measure()/layout() on a detached WebView give the page a correct viewport? This must be tested on the device.
- Do eBay sign-in passkeys or security keys really fail in our WebView with WEB_AUTHENTICATION_SUPPORT_FOR_APP? eBay's /.well-known/assetlinks.json could not be fetched, since web.archive.org was unreachable from this environment.
- Which values do Pixel overlays use for config_ntpServers and config_timeDetectorAutoUpdateDiffMillis on Android 17? Pixel device trees are no longer public, so AOSP defaults (time.android.com, 2000 ms) were assumed.
- Have Android 17's AlarmManagerService constants changed from AOSP main of 2025-03-27 (MIN_FUTURITY 5 s, allow-while-idle quota 72/h, idle fuzz 2-15 min, 10 s temporary allowlist)? Check with 'adb shell dumpsys alarm' on the device.
- What memory limit does the Android 17 memory limiter apply to a non-visible app process and its WebView renderer on an 8 GB Pixel 9a? Check with 'adb shell am memory-limiter status' while a heavy eBay page is loaded headless.
- Is USE_FULL_SCREEN_INTENT granted by default to a sideloaded app targeting 37 on Android 17, and is 'Display over other apps' a restricted setting for sideloaded apps? This only matters for the lock-screen Activity or overlay fallbacks.
- Can the eBay bid flow be split, so the review step runs early and only the final confirm runs at T-L, and do its tokens survive about 30 s? This decides the latency compensation and is a question for the eBay-flow research.
- Will the 2027 global developer-verification rollout require registering this personal app, through a limited-distribution account (up to 20 devices) or the advanced flow, before new installs or updates?

## Report
# Android platform mechanics for an on-device auction sniper on a Pixel 9a (state on 2026-11-04)

## 0. Sources, dates, conventions
- **Official docs:** developer.android.com, source.android.com, developers.google.com and support.google.com pages, fetched 2026-11-04. Most pages showed no "last updated" date through the fetcher; dates are given where visible.
- **AOSP framework source:** GitHub mirror `aosp-mirror/platform_frameworks_base`, branch `main`, head `1cdfff555f4a`, committed **2025-03-27**.
  - The mirror is archived, so this is Android 16-era code.
  - Constants can be overridden by DeviceConfig, and Android 17 may differ.
  - Local read-only copies: `/var/tmp/sniper-research/src/aosp-frameworks-base-files/`. Line numbers below refer to these copies.
- **Chromium (WebView) source:** `github.com/chromium/chromium` main. Each file's last commit date is given.
  - Local copies: `/var/tmp/sniper-research/src/chromium-files/`.
  - Live Updates sample: `/var/tmp/sniper-research/src/platform-samples-liveupdates/`.
- **Conventions:** `[inferred]` marks a conclusion of mine that no source states. No eBay host was contacted, and no eBay page was fetched.

---

## 1. What the Pixel 9a runs in November 2026, and what changed

### 1.1 Release status
**Version names**
- Android 17 = API 37, `Build.VERSION_CODES.CINNAMON_BUN`. The android/platform-samples live-updates sample uses `Build.VERSION_CODES.CINNAMON_BUN` (commit 2026-06-04).
- Android 16 = API 36, `BAKLAVA`. Android 16 QPR2 introduced minor SDK 36.1.

**Android 17 stable, June 2026**
- AOSP tag `android-17.0.0_r1`, build `CP2A.260605.016`, security patch level 2026-06-05 (source.android.com/docs/setup/reference/build-numbers).
- Pixel Update Bulletin "June 2026" published June 16, 2026.
- Android Central, "Android 17 is finally here", 16 Jun 2026.
- Betas (developer.android.com/about/versions/17/release-notes): Beta 1 2026-02-13, Beta 2 2026-02-26, Beta 3 2026-03-26 (platform stability), Beta 4 2026-04-16, Beta 4.1 2026-06-01.

**Android 17 QPR1 = API 37.1** (developer.android.com/about/versions/17/qpr1/release-notes)
- Betas from 2026-04-22 to Beta 9 on 2026-08-17 (`CP31.260623.012`). "Reached Platform Stability as of Beta 6."
- The Pixel bulletin "September 2026" was published 2026-09-15. [inferred] QPR1 stable reached Pixels with the September 2026 drop, so a current Pixel 9a is on Android 17 QPR1.

**Android 17 QPR2 = API 37.2** (developer.android.com/about/versions/17/qpr2/release-notes)
- Beta 1 2026-07-20 to Beta 7.1 2026-10-16 (`CP41.260831.019`). It is a minor SDK release.
- [inferred] Stable around December 2026.

**Pixel 9a support**
- "Pixel 8 and later phones will get updates for seven years" (support.google.com/pixelphone/answer/4457705), so the Pixel 9a (launched with Android 15 in 2025) gets Android 17.

**Answer:** Android 17. Build with compileSdk/targetSdk 37 and test on Android 17 QPR1.

### 1.2 What changed in 16 and 17 that touches this app

**Android 16 (API 36)**
- **Job quotas (all apps).** JobScheduler and WorkManager quotas changed: "Jobs started while the app is visible ... and continues after the app becomes invisible, will adhere to the job runtime quota", and "jobs that are executing concurrently with a foreground service will adhere to the job runtime quota" (behavior-changes-all).
- **Notifications.** `Notification.ProgressStyle` (API 36) and promoted notifications ("Live Updates"). `POST_PROMOTED_NOTIFICATIONS` and `setRequestPromotedOngoing` show "Added in version 36.1".
- **Background activity launches.** `ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` (SDK 36).
- **Target 36.** `scheduleAtFixedRate` runs at most one missed execution; the edge-to-edge opt-out is removed; predictive back.
- **Local network.** Local network protection is opt-in only.

**Android 17 (API 37), all apps** (behavior-changes-all)
- **App memory limits.** A killed process has exit reason `REASON_OTHER` with a description containing `"MemoryLimiter:AnonSwap"`. Tools: `am memory-limiter status|ignore|manual`.
- **SMS OTP protection.** OTP SMS in WebOTP format is held for 3 hours from apps that are not the intended recipient.
- **Background audio hardening.**
- **Cross-profile loopback** is blocked.

**Android 17, apps targeting 37** (behavior-changes-17)
- Lock-free `android.os.MessageQueue`.
- `static final` fields can no longer be changed by reflection.
- `ACCESS_LOCAL_NETWORK` is enforced (LAN only).
- ECH is used for TLS, and Certificate Transparency is on by default.
- Background-activity-launch hardening now covers `IntentSender`.
- Widget `RemoteViews` bitmap memory cap of `6 * width * height` bytes.
- Standard SMS OTP 3-hour delay.
- Beta 1 notes: "Strict size limits enforced on custom notification views".

**Android 17 features** (features page)
- New `AlarmManager.setExactAndAllowWhileIdle(int type, long triggerAtMillis, String tag, Executor executor, OnAlarmListener listener)`, API 37.
- `Intent.ACTION_TIMEZONE_OFFSET_CHANGED`.
- Live Update semantic colours: `Notification.createSemanticStyleAnnotation(SEMANTIC_STYLE_INFO|SAFE|CAUTION|DANGER)`, and `Notification.MetricStyle`.
- Advanced Protection Mode (AAPM): when the user enables it, it blocks sideloading.
- `JobDebugInfo.getPendingJobReasonStats()`.

**Android 17 QPR2 (beta, all apps)**
- Broadcasts an app sends to itself are delivered inside its own process.
- They "don't raise your process's importance", wait while the process is frozen, and are lost if it is killed.
- Google's guidance: "Don't use self-broadcasts as a timer or keep-alive mechanism ... use AlarmManager for work that must happen at a specific time."

**Not found:** no new foreground-service timeouts and no new exact-alarm restrictions on the Android 16 or 17 behavior-change pages.

---

## 2. Exact alarms

### 2.1 Permissions (sideloaded app, targetSdk 37)
| | `USE_EXACT_ALARM` | `SCHEDULE_EXACT_ALARM` |
|---|---|---|
| protectionLevel (AOSP `core/res/AndroidManifest.xml` L5992-6022) | `normal` | `signature\|privileged\|appop` |
| Granted on install? | **Yes, automatically** | **No.** Denied by default for fresh installs targeting 33+ on Android 14+ (`SCHEDULE_EXACT_ALARM_DENIED_BY_DEFAULT` = 226439802) |
| User can revoke? | No | Yes ("Alarms & reminders"). Revoking stops the app and cancels all exact alarms. |
| Min targetSdk | 33 (`ENABLE_USE_EXACT_ALARM` = 218533173) | 31 |
| Standby bucket | "always stay in the WORKING_SET or lower standby bucket" | For apps targeting 34+, no longer pins the bucket (`SCHEDULE_EXACT_ALARM_DOES_NOT_ELEVATE_BUCKET` = 262645982) |
| Play policy | Limited use cases (does not apply to sideloading) | |

- **Declare:** `<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>`.
- If minSdk is below 33, also declare `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32"/>`, as the AOSP javadoc shows.
- **`canScheduleExactAlarms()`** is true if the app has either permission or is on the power-save allowlist (AlarmManagerService L2845-2850). Without any of these, the call throws `SecurityException`: "Caller ... needs to hold SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM to set exact alarms."
- **`AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`** = `"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"`. It is sent only when the permission is granted, to runtime and manifest receivers, as a foreground broadcast, and the app may start a foreground service from it. It is "not sent when the user revokes". It is irrelevant if the app uses `USE_EXACT_ALARM`.

### 2.2 API behaviour (AOSP `AlarmManagerService.java`, main 2025-03-27)
**`setExact(type, t, PendingIntent)`**
- Deferred in Doze.

**`setExactAndAllowWhileIdle(type, t, PendingIntent)`** (API 23)
- Fires in Doze and gives a temporary allowlist of about 10 s: `DEFAULT_ALLOW_WHILE_IDLE_ALLOWLIST_DURATION = 10*1000`, type `TEMPORARY_ALLOWLIST_TYPE_FOREGROUND_SERVICE_ALLOWED`, reason `REASON_ALARM_MANAGER_WHILE_IDLE` (L728, L874-889).
- Quota for apps targeting S+ that hold a permission: `DEFAULT_ALLOW_WHILE_IDLE_QUOTA = 72` per 1 h. Compat or no-permission case: `DEFAULT_ALLOW_WHILE_IDLE_COMPAT_QUOTA = 7` per 1 h (L751-755).
- The docs still say "can't fire alarms more than once per nine minutes, per app" (doze-standby), and the power-details table says "Limited to 7 per hour".

**`setAlarmClock(AlarmClockInfo(triggerWallMs, showIntent), PendingIntent)`** (API 21)
- Always `RTC_WAKEUP` and `FLAG_WAKE_FROM_IDLE` (L2724-2728).
- Doze's end is moved to `upcomingWakeFromIdle - fuzz`, with fuzz random in [`MIN_DEVICE_IDLE_FUZZ` = 2 min, `MAX_DEVICE_IDLE_FUZZ` = 15 min] (L759-760, L2304-2333).
- Exempt from:
  - app-standby quotas (`isExemptFromAppStandby`, L4328-4331);
  - Battery Saver (`isExemptFromBatterySaver`, L4141-4144);
  - the user's background restriction ("Don't defer alarm clocks", L4155-4159).
- FGS-start temporary allowlist with reason `REASON_ALARM_MANAGER_ALARM_CLOCK`.
- The javadoc says both exact APIs are "allowed to start a foreground service even if the app is in the background".

**Effect of Unrestricted battery** (user power allowlist)
- `if (workSource == null && (... || mAppStateTracker.isUidPowerSaveUserExempt(callingUid))) flags |= FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED` (L2729-2738).
- So every alarm the app sets has no Doze or Battery Saver limit.

**Minimum lead time**
- `MIN_FUTURITY = 5 s`: "making sure apps aren't firing alarms in the immediate future" (L724, L2196-2202). The T-5s firing therefore cannot be an alarm.

**Listener alarms**
- `setExact(type, t, tag, OnAlarmListener, Handler)` needs no permission on Android 14+.
- Since Android 14 they are dropped when the app goes "out of lifecycle" (`EXACT_LISTENER_ALARMS_DROPPED_ON_CACHED`, 10 s grace `CACHED_LISTENER_REMOVAL_DELAY`).
- API 37 adds `setExactAndAllowWhileIdle(type, t, tag, executor, listener)`.

**Alarm lifetime**
- RTC alarms follow wall-clock changes (`reevaluateRtcAlarms`, L1374-1394). ELAPSED alarms do not move.
- An app update keeps alarms: "This package is being updated; don't kill its alarms" (L4994-5001). Exact alarms are re-checked in case the update removed the permission.
- Force stop or uninstall removes them (`ACTION_PACKAGE_RESTARTED`). Reboot clears all alarms.
- Android 15+ force stop "cancels all pending intents"; when the user returns, the app receives BOOT_COMPLETED.
- A Task Manager "Stop" keeps alarms.
- Every exact alarm is `FLAG_STANDALONE`, so it is never batched with others.

**Delivery latency**
- [inferred] Alarm dispatch takes tens of ms. A cold start of the app process plus WebView adds roughly 0.3-1.5 s. This is why the wake happens at about T-120 s.

### 2.3 Recommendation
- **Primary wake:** `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, …)` at T-120 s, with `USE_EXACT_ALARM` and Unrestricted battery. Its behaviour matches an alarm clock, without the alarm UI.
- **Backup:** a second exact alarm at T-75 s with a different requestCode. It does nothing if the service is already running.
- **When to use `setAlarmClock`:** if `PowerManager.isIgnoringBatteryOptimizations(pkg)` is false, or `ActivityManager.isBackgroundRestricted()` is true, use `setAlarmClock` for the primary wake.
- **`setAlarmClock` side effects (AOSP):**
  - It becomes the device's "next alarm" (`ACTION_NEXT_ALARM_CLOCK_CHANGED`).
  - DND schedules set to exit at the next alarm (`ScheduleConditionProvider` L163-200 and L242-264, which use `AlarmManager.getNextAlarmClock()`) would end at the snipe time [inferred user impact].

---

## 3. Foreground services

### 3.1 Which type
| Type | Permission | Limit | From BOOT_COMPLETED (target 35+) | Verdict |
|---|---|---|---|---|
| `shortService` (`FOREGROUND_SERVICE_TYPE_SHORT_SERVICE`) | only `FOREGROUND_SERVICE` | about 3 min (`DEFAULT_SHORT_FGS_TIMEOUT_DURATION = 3*60_000`), then `onTimeout()`; cached after +5 s, ANR after +10 s; not sticky; cannot start other foreground services; the timeout applies "even if battery optimization disabled" | **No**: it is not in `DEFAULT_FGS_BOOT_COMPLETED_ALLOWLIST` (ActivityManagerConstants L191-197) | Fine for 20-60 s price checks; too tight for the snipe |
| `dataSync` | `FOREGROUND_SERVICE_DATA_SYNC` (normal) | 6 h per 24 h for target 35+ (`onTimeout(int,int)`) | **No** (listed in the Android 15 doc) | Works but brings nothing extra |
| **`specialUse`** | `FOREGROUND_SERVICE_SPECIAL_USE` (normal\|appop\|instant) plus `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="..."/>` | none | **Yes** | **Use this** |
| `systemExempted` | `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` plus any of `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` (ForegroundServiceTypePolicy L542-558) | none | Yes | Semantically wrong ("reserved for system"); no benefit found |

- **BOOT_COMPLETED rule:** the code comment on `FGS_BOOT_COMPLETED_RESTRICTIONS` (296558535) says: "Disables foreground service background starts from BOOT_COMPLETED broadcasts for all types except location, connectedDevice, remoteMessaging, health, systemExempted, specialUse".
- **When the rule applies:** only when the start reason is `REASON_BOOT_COMPLETED` (ActiveServices L1170-1180). MY_PACKAGE_REPLACED is not affected.

### 3.2 Starting it from the background (fgs/restrictions-bg-start)
**Exemptions that apply here**
- "Your app invokes an exact alarm to complete an action that the user requests" (the 10 s temporary allowlist from §2.2).
- "The user turns off battery optimizations for your app".
- BOOT_COMPLETED, LOCKED_BOOT_COMPLETED or MY_PACKAGE_REPLACED.
- TIMEZONE_CHANGED, TIME_CHANGED or LOCALE_CHANGED.
- The user acting on a notification or widget.
- `SYSTEM_ALERT_WINDOW`. For target 35+ it also needs a currently visible `TYPE_APPLICATION_OVERLAY` window.

**Deadlines** (AOSP)
- `startForeground` must be called within 30 s (`DEFAULT_SERVICE_START_FOREGROUND_TIMEOUT_MS`), then the app gets 10 s more before an ANR.
- Call it immediately anyway, and use `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)` (API 31) so the notification is not delayed.

**Exceptions to catch**
- `ForegroundServiceStartNotAllowedException` (API 31).
- `MissingForegroundServiceTypeException` and `InvalidForegroundServiceTypeException` (API 34).

**Task Manager:** "Stop" kills the whole app with no callback. Jobs and alarms remain. On the next start, check `ApplicationExitInfo.REASON_USER_REQUESTED`.

---

## 4. Network and power state during the window

### 4.1 Doze
Official list (doze-standby): "Suspends network access. Ignores wake locks. Defers standard AlarmManager alarms ... Doesn't let JobScheduler run."

**Exemptions in AOSP**
- **Network:** allowed if `procState <= PROCESS_STATE_BOUND_FOREGROUND_SERVICE` or the process has `PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK` (NetworkPolicyManager L188-189 and L831-838). An app running its own foreground service is in `PROCESS_STATE_FOREGROUND_SERVICE`, which qualifies.
- **Partial wake locks:** honoured if the app is allowlisted, temp-allowlisted, or `procState <= PROCESS_STATE_BOUND_FOREGROUND_SERVICE` (PowerManagerService L4332-4342).
- **Data Saver:** foreground-service processes keep network (`isProcStateAllowedWhileOnRestrictBackground`, L868-886).

**Android 15 background-network block (all apps)**
- "apps that start a network request outside of a valid process lifecycle receive an exception (UnknownHostException or other socket-related IOException)."
- AOSP threshold: `BACKGROUND_THRESHOLD_STATE = PROCESS_STATE_TOP_SLEEPING`. A process that goes cached loses network after a 2-5 s debounce.

**Official table** (power-details): "App process is running a foreground service | Network: No restrictions".

### 4.2 Battery optimization ("Unrestricted")
**How to request it**
- Permission: `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (normal, API 23).
- Intent: `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with data `package:<pkg>`.
- Check: `PowerManager.isIgnoringBatteryOptimizations(pkg)`.
- General settings screen: `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.
- Pixel UI path: Apps > See all apps > app > App battery usage > Allow background usage > Optimised / (Unrestricted).

**Effects**
- Official: "can use the network and hold partial wake locks during Doze and App Standby".
- AOSP: standby bucket `STANDBY_BUCKET_EXEMPTED` (AppStandbyController L1464-1465, fed by `DeviceIdleController.getFullPowerWhitelistExceptIdleInternal`, which includes `mPowerSaveWhitelistUserApps`, L3110-3129).
- AOSP: all alarms are `FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED`.
- The app may start foreground services from the background.

**Background restriction:** detect it with `ActivityManager.isBackgroundRestricted()` (API 28) and warn the user.

### 4.3 Standby buckets (power-details)
| Bucket | Regular jobs | Alarms | Network |
|---|---|---|---|
| Active | 20 min / 60 min | unlimited | unrestricted |
| Working set | 10 min / 4 h | 10 per hour | unrestricted |
| Frequent | 10 min / 12 h | 2 per hour | unrestricted |
| Rare | 10 min / 24 h | 1 per hour | **disabled** |
| Restricted | once a day | 1 per day | disabled |

- AOSP exempts allow-while-idle alarms and alarm clocks from the per-bucket alarm quota.
- `USE_EXACT_ALARM` gives WORKING_SET or better.
- A placed home-screen widget gives ACTIVE or better (`isBoundWidgetPackage`, AppStandbyController L1498-1501).
- Unrestricted battery gives EXEMPTED.

---

## 5. Headless WebView inside the service

### 5.1 Facts from source
**Visibility** (Chromium `browser_view_renderer.cc`, 2026-10-28, L561-569)
```cpp
// When WebView is not paused, we declare it visible even before it is
// attached to window to allow for background operations. ...
return is_paused_ ? false : !was_attached_ || (attached_to_window_ && window_visible_);
```
- A **never-attached** WebView stays `VISIBLE` (AwContents.java L3976-3988 maps this to `WebContents` visibility). Page timers are not throttled for hidden pages, and `document.visibilityState` is "visible" [the last point inferred from Blink semantics].
- Attaching and later detaching makes it hidden (`was_attached_ = true`). `WebView.onPause()` also makes it hidden.

**Timers**
- `WebView.pauseTimers()` is **global**: "Pauses all layout, parsing, and JavaScript timers for all WebViews" (WebView.java L1368-1373).
- `onPause()` "does not pause JavaScript".

**Threading:** `checkThread()` throws `"All WebView methods must be called on the same thread"` (WebView.java L2615-2635). `evaluateJavascript` "must be called on the UI thread".

**Renderer priority**
- Default `RENDERER_PRIORITY_IMPORTANT` "regardless of visibility" (WebView.java L2313-2342; AwContents L4198-4218).
- Handle `WebViewClient.onRenderProcessGone(view, detail)` (API 26) and return true.

**Processes:** WebView data belongs to one process; a second process would need `setDataDirectorySuffix()` (WebView.java L2045-2075). Keep everything in the default process.

**Frames**
- Begin frames come from `RootBeginFrameSourceWebView` (60 Hz, aligned with the Java frame clock; begin_frame_source_webview.cc, 2026-06-02).
- [inferred] `requestAnimationFrame` may not run with the screen off.
- [inferred] A never-attached view is 0x0 until `measure()`/`layout()` are called.
- Do not depend on rAF, and test with the screen off.

**Memory:** watch for Android 17 memory-limiter kills (§1.2). Use one WebView, and call `destroy()` after each snipe.

**Start-up:** androidx.webkit 1.18.0 (Oct 21, 2026). `WebViewCompat.startUpWebView(...)` and `WebViewStartUpConfig` are stable since 1.16.0 (May 6, 2026). `Profile#warmUpRendererProcess` is experimental.

### 5.2 Cookies
**Persistence**
- Chromium `cookie_manager.cc` (2026-10-09) L299-303: `restore_old_session_cookies=true, persist_session_cookies=true`. Session cookies survive app restarts.
- `sqlite_persistent_cookie_store.cc` (2026-10-05) L1237-1239: commits every 30 s or after 512 operations.
- `CookieManager.flush()` (API 21) "will block the caller until it is done and may perform I/O". Call it off the main thread after sign-in and after each snipe.

**Reading cookies**
- `getCookie(url)` uses `net::CookieOptions::MakeAllInclusive()` (L651-653), so it includes HttpOnly cookies. Format: `"k=v; k2=v2"`, without attributes.
- `CookieManagerCompat.getCookieInfo(cookieManager, url)` (androidx.webkit 1.6.0) returns full attribute lines.
- `setCookie(url, value, ValueCallback)` (API 21) is asynchronous.

**Sharing with OkHttp**
- A `CookieJar` can call `CookieManager.getCookie` in `loadForRequest` and `setCookie` for each `Set-Cookie` in `saveFromResponse`.
- Send `WebSettings.getDefaultUserAgent(context)`.
- [inferred] OkHttp's TLS ClientHello differs from Chromium's, so the bid itself is safer inside the WebView.

### 5.3 Alternatives
**Activity over the lock screen**
- `android:showWhenLocked="true"` and `android:turnScreenOn="true"`, or `setShowWhenLocked(true)` / `setTurnScreenOn(true)` (API 27).
- Alarm PendingIntents are sent with background launch disallowed (`setPendingIntentBackgroundActivityLaunchAllowed(false)`, AlarmManagerService L1776-1778), so an alarm cannot open it directly.
- It needs a full-screen-intent notification (USE_FULL_SCREEN_INTENT plus an IMPORTANCE_HIGH channel; it launches only when locked or the screen is off) or SYSTEM_ALERT_WINDOW (a background-launch exemption). This is intrusive, so it is a last resort only.

**1x1 overlay**
- Needs `SYSTEM_ALERT_WINDOW` (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`, `Settings.canDrawOverlays()`).
- [inferred] Overlays are hidden behind the keyguard, so the window may become invisible, which makes the WebView hidden.
- This is worse than a never-attached WebView. Not recommended.

---

## 6. Signing in inside the WebView
**Passkeys and WebAuthn**
- APIs: `WebSettingsCompat.setWebAuthenticationSupport(settings, WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP)`, guarded by `WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)`, `@UiThread`.
- Since androidx.webkit 1.12.0 (alpha Apr 17 2024, stable Sept 18 2024). The default is `WEB_AUTHENTICATION_SUPPORT_NONE`.
- FOR_APP covers "the app in which the WebView is embedded. See Digital Asset Links"; FOR_BROWSER is for "privileged apps".
- "The WebKit library doesn't support mediation:\"conditional\" requests."
- [inferred] eBay's assetlinks will not list our signing key, so eBay passkeys and security keys fail in our WebView. Use password plus SMS or authenticator code.

**Google sign-in:** OAuth is blocked in embedded WebViews (`disallowed_useragent`) since **2021-09-30** (Google Developers Blog, 2021-06-29).

**Facebook Login:** not supported in Android WebViews since **2021-10-05** (Meta developers blog, 2021-06-28, updated 2021-08-27).

**Handling these in the app**
- Intercept navigation to `accounts.google.com` and `facebook.com` login pages in `shouldOverrideUrlLoading` and show guidance.
- Android 17 SMS OTP protection only affects apps that read SMS. The user types codes, so it does not matter here.

---

## 7. Precise time

**System clock (AOSP defaults; Pixel overlay values not public)**
- `config_ntpServers = ntp://time.android.com`, `config_ntpPollingInterval = 64800000` (18 h), `config_ntpPollingIntervalShorter = 60000`, `config_ntpRetry = 3`, `config_ntpTimeout = 5000`.
- `config_autoTimeSourcesPriority = network, telephony`; `config_timeDetectorAutoUpdateDiffMillis = 2000` (config.xml L2017-2028, L2693-2711).
- source.android.com: "single time query approximately once a day"; "maximum theoretical error is approximately 2.5 seconds"; the device time is left unchanged if within "a couple of seconds" (`systemClockUpdateThresholdMillis()=2000`).

**`SystemClock.currentNetworkTimeClock()`** (API 33)
- Returns `java.time.Clock` from the last NTP result plus elapsed time. It does not block and throws `DateTimeException` if no network time exists.
- It is still available when the user turned automatic time off.

**`SystemClock.currentGnssTimeClock()`** (API 29) needs a location fix since boot. Not useful here.

**Clocks for scheduling**
- `Handler`, `Thread.sleep` and `Object.wait` use `uptimeMillis`, which stops in deep sleep.
- `elapsedRealtime[Nanos]` includes sleep.
- Hold `PowerManager.PARTIAL_WAKE_LOCK` (`android.permission.WAKE_LOCK`) during the window.

**App-level SNTP** (illustrative; NTP times are seconds since 1900; subtract 2208988800 s)
```kotlin
data class TimeAnchor(val trueEpochMsAtAnchor: Long, val elapsedMsAtAnchor: Long, val roundTripMs: Long)

fun measureSntp(host: String = "time.google.com", samples: Int = 6): TimeAnchor? {
    var best: TimeAnchor? = null
    repeat(samples) {
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = 1500
                val request = ByteArray(48).also { it[0] = 0x23 } // LI=0, VN=4, Mode=3 (client)
                val sendElapsedNanos = SystemClock.elapsedRealtimeNanos()
                socket.send(DatagramPacket(request, 48, InetAddress.getByName(host), 123))
                val response = DatagramPacket(ByteArray(48), 48)
                socket.receive(response)
                val receiveElapsedNanos = SystemClock.elapsedRealtimeNanos()
                val data = response.data
                val mode = data[0].toInt() and 0x7; val stratum = data[1].toInt() and 0xff
                if (mode != 4 || stratum == 0) return@use
                val serverReceive = readNtpMillis(data, 32)   // T2
                val serverTransmit = readNtpMillis(data, 40)  // T3
                val roundTrip = (receiveElapsedNanos - sendElapsedNanos) / 1_000_000 - (serverTransmit - serverReceive)
                // True time at the receive instant is approximately serverTransmit + roundTrip / 2
                val anchor = TimeAnchor(serverTransmit + roundTrip / 2, receiveElapsedNanos / 1_000_000, roundTrip)
                if (best == null || roundTrip < best!!.roundTripMs) best = anchor
            }
        }
        SystemClock.sleep(250)
    }
    return best
}
fun trueNowMs(a: TimeAnchor) = a.trueEpochMsAtAnchor + (SystemClock.elapsedRealtime() - a.elapsedMsAtAnchor)
```
- Run it on a background thread inside the service.
- Fallbacks in order: `currentNetworkTimeClock()`, then [inferred] the HTTP `Date` header of a same-origin response (1 s resolution), then the device clock with a wider safety margin.
- Use `time.google.com` only, never mixed with non-smeared servers (developers.google.com/time/guides).

---

## 8. Reboot, update, time change

**Manifest receivers**
- `android.intent.action.BOOT_COMPLETED` needs `RECEIVE_BOOT_COMPLETED` and is delivered only after the app has been launched once.
- `android.intent.action.LOCKED_BOOT_COMPLETED` needs `android:directBootAware="true"`. It may use device-protected storage only.
- `android.intent.action.MY_PACKAGE_REPLACED` is sent to the updated app.
- `android.intent.action.TIME_SET` and `android.intent.action.TIMEZONE_CHANGED`.
- Exemptions from the implicit-broadcast ban: BOOT/LOCKED_BOOT, `TIME_SET`, `TIMEZONE_CHANGED` (broadcast-exceptions page). `ACTION_PACKAGE_REPLACED` is "not exempted".
- Android 17 adds `ACTION_TIMEZONE_OFFSET_CHANGED`. Store all times in UTC epoch ms so time zones never matter.

**On each event:** reload pending snipes from the database, re-sync time, and reschedule.
- If a snipe window is already open (T-120 s or later), start the specialUse service directly. BOOT_COMPLETED, MY_PACKAGE_REPLACED and TIME_SET are all foreground-service start exemptions, and specialUse is allowed from BOOT_COMPLETED.

**Direct Boot**
- Before the first unlock, credential-encrypted storage (WebView cookies, Room DB) is unavailable.
- Post a "Unlock your phone to arm: <item> ends HH:MM" notification from the directBootAware receiver, using a small `createDeviceProtectedStorageContext()` copy of the schedule.
- Switch to normal mode on `ACTION_USER_UNLOCKED` or BOOT_COMPLETED.

**Auto-restart:** Play services 25.14 (April 2025) restarts a phone locked for 3 days in a row, leaving it in the locked-after-boot state.

**Force stop (Android 15+):** all PendingIntents are cancelled until the user opens the app. Check `ApplicationStartInfo.wasForceStopped()`.

**Unused-app hibernation** [inferred, low risk]: consider `IntentCompat.createManageUnusedAppRestrictionsIntent` so the user can exclude the app.

---

## 9. WorkManager for price checks
- androidx.work 2.12.0 is stable (Sept 23, 2026).
- `PeriodicWorkRequestBuilder<PriceCheckWorker>(30, TimeUnit.MINUTES)`. The repeat interval must be at least `MIN_PERIODIC_INTERVAL_MILLIS` (15 min) and the flex at least `MIN_PERIODIC_FLEX_MILLIS` (5 min).
- `Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)`.
- `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, SECONDS)`; the minimum `MIN_BACKOFF_MILLIS` is 10 s.
- `enqueueUniquePeriodicWork("price-<itemId>", ExistingPeriodicWorkPolicy.UPDATE, request)`.
- Expedited work: `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)` is one-time only, and delays are not allowed.
- Jobs do not run in Doze; they wait for maintenance windows. Android 16 quotas also count jobs that run alongside a foreground service. Android 14 requires `ACCESS_NETWORK_STATE` to use network constraints.

**Plan**
- More than 2 h before the end: periodic checks every 30-60 min.
- Last hour: chained `setExactAndAllowWhileIdle` checks at T-60, T-30, T-15 and T-5 min, each running a short service (shortService is fine) or an expedited job.
- At T-10 min: a fuller check (session valid, price, time sync) so there is time to ask the user to sign in again.

---

## 10. Notifications
**Permissions:** `POST_NOTIFICATIONS` (dangerous, API 33) and `POST_PROMOTED_NOTIFICATIONS` (normal|appops, 36.1).

**Channels**
- `snipe_live` (IMPORTANCE_DEFAULT, for the Live Update).
- `snipe_alert` (IMPORTANCE_HIGH, for over-max, outbid, re-login).
- `snipe_result` (IMPORTANCE_DEFAULT).

**Live Update rules**
- Allowed styles: standard, BigText, Call, Progress or Metric.
- Must be ongoing, have a contentTitle, have no custom RemoteViews, not be a group summary, not be colorized, and not use an IMPORTANCE_MIN channel.
- Checks: `canPostPromotedNotifications()`, `hasPromotableCharacteristics()`, `FLAG_PROMOTED_ONGOING`. User setting: `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS`.
- Use it only while the snipe is imminent; "upcoming calendar events" and "alerts" are listed as inappropriate.

**Chip content**
- Order: `setShortCriticalText` (API 36, 7 characters or fewer) wins, then a MetricStyle metric, then a chronometer if `setUsesChronometer(true)` (with `setChronometerCountDown(true)`, API 24), then time until `when` if `setShowWhen(true)`.
- The chip is at most 96dp wide.

**ProgressStyle** (API 36): `setProgress`, `setProgressIndeterminate`, `setProgressSegments`, `addProgressSegment(Segment(len).setColor())`, `setProgressPoints`, `addProgressPoint(Point(pos))`, `setProgressTrackerIcon`, `setProgressStartIcon`, `setProgressEndIcon`, `setStyledByProgress`. Compat versions are in androidx.core 1.17+ (MetricStyle needs 1.19.0-alpha01+).

```kotlin
val notification = NotificationCompat.Builder(context, "snipe_live")
    .setSmallIcon(R.drawable.ic_gavel)
    .setContentTitle("Sniping: $itemTitle")
    .setContentText("Max £$maxBid · now £$currentPrice")
    .setOngoing(true)
    .setRequestPromotedOngoing(true)
    .setWhen(auctionEndTrueMs - clockOffsetMs) // device-clock domain, so the countdown is right
    .setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
    .setStyle(NotificationCompat.ProgressStyle().setProgressPoints(phasePoints).setProgress(phaseProgress))
    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    .addAction(raiseMaxAction)
    .build()
```

**Inline "raise max bid"**
```kotlin
val remoteInput = RemoteInput.Builder("new_max").setLabel("New max (£)").build()
val raiseIntent = PendingIntent.getBroadcast(context, requestCodeFor(snipeId, ROLE_RAISE),
    Intent(context, RaiseMaxReceiver::class.java).putExtra("snipe_id", snipeId),
    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
val raiseMaxAction = NotificationCompat.Action.Builder(R.drawable.ic_raise, "Raise max", raiseIntent)
    .addRemoteInput(remoteInput).setAllowGeneratedReplies(false).build()
// Receiver: RemoteInput.getResultsFromIntent(intent)?.getCharSequence("new_max") -> validate -> save -> re-notify with the same id
```

**Full-screen intent:** `USE_FULL_SCREEN_INTENT` (normal|appop). Since Android 14, Play revokes it for apps that are not calling or alarm apps. Check `NotificationManager.canUseFullScreenIntent()` (API 34) and send the user to `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`. Not needed in this design.

---

## 11. Widgets (relevant)
- Glance 1.2.0 stable (Aug 26, 2026). Build with `GlanceAppWidget`, `GlanceAppWidgetReceiver` and `provideGlance`, and call `updateAll` after each state change.
- Benefits: the user sees upcoming snipes, and a placed widget keeps the app in the ACTIVE bucket or better (AOSP).
- Android 17 (target 37) caps widget bitmap memory at `6*w*h` bytes, so keep thumbnails downsampled.

---

## 12. Recommended reliability design for one snipe

### 12.1 Manifest (target 37, minSdk 35 or higher)
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>

<application ...>
  <service android:name=".snipe.SnipeService" android:exported="false"
      android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Places one user-scheduled auction bid at a user-chosen second"/>
  </service>
  <service android:name=".snipe.PriceCheckService" android:exported="false"
      android:foregroundServiceType="shortService"/>
  <receiver android:name=".snipe.SnipeAlarmReceiver" android:exported="false"/>
  <receiver android:name=".snipe.RaiseMaxReceiver" android:exported="false"/>
  <receiver android:name=".snipe.RescheduleReceiver" android:exported="false">
    <intent-filter>
      <action android:name="android.intent.action.BOOT_COMPLETED"/>
      <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>
      <action android:name="android.intent.action.TIME_SET"/>
      <action android:name="android.intent.action.TIMEZONE_CHANGED"/>
    </intent-filter>
  </receiver>
  <receiver android:name=".snipe.LockedBootReceiver" android:exported="false" android:directBootAware="true">
    <intent-filter><action android:name="android.intent.action.LOCKED_BOOT_COMPLETED"/></intent-filter>
  </receiver>
</application>
```
- Receivers with `exported="false"` still receive system broadcasts.
- Never use `android:process` (§5.1).

### 12.2 One-time onboarding
1. Request POST_NOTIFICATIONS.
2. Request Unrestricted battery (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and verify with `isIgnoringBatteryOptimizations`.
3. Assert `canScheduleExactAlarms()`.
4. Check `canPostPromotedNotifications()`.
5. Offer a home-screen widget.
6. Sign in to eBay in a visible WebView (password plus code; explain that Google, Facebook and passkeys will not work), then call `CookieManager.flush()`.
7. Never call `WebView.pauseTimers()` anywhere in the app.

### 12.3 Timeline (T = auction end in true UTC; L = user lead, for example 5 s)
| When | Mechanism | Action |
|---|---|---|
| Arm | UI | Save the snipe (credential-encrypted DB plus a device-protected stub). Run SNTP. Schedule A0 (T-10 min), A1 (T-120 s), A2 (T-75 s) with `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`, or A1 with `setAlarmClock` if the app is not Unrestricted. Enqueue periodic price checks. |
| T-60/-30/-15/-5 min | exact alarms, then shortService or expedited job | Price check. If price exceeds max, send a HIGH notification with "Raise max". |
| T-10 min (A0) | exact alarm, then specialUse service for about 30 s | Check the session in a headless WebView, SNTP, refresh price and end time. If logged out, send a HIGH "Sign in again" notification with a deep link. |
| T-120 s (A1) | exact alarm, receiver calls `startForegroundService` within 10 s | `startForeground(SPECIAL_USE)` with the Live Update countdown. Take a wake lock (`acquire(300_000)`). Call `startUpWebView`. Run SNTP (6 samples) and cross-check `currentNetworkTimeClock()`. Create a never-attached WebView (measure/layout to a phone size [inferred]), load the item or bid page, verify signed in, price at or below max, and end time unchanged. |
| T-75 s (A2) | exact alarm | If the service is not running, start it and take the same path with less time. Otherwise nothing. |
| T-30 s | in-process | Re-check price. Abort with a notification if it exceeds max. Get the bid form ready if the flow allows ([inferred]; depends on the eBay-flow research). |
| T-L | `Handler.postAtTime(r, SystemClock.uptimeMillis() + (fireTrueMs - trueNowMs()))` on main | Save `fired=true` before sending. Fire exactly once with `evaluateJavascript`. Never send twice. |
| T-L to T+60 s | in-process | Watch for the confirmation in the page, record it. After T, poll the result at about T+5 s, T+30 s and T+60 s. |
| End | service | Result notification (won, lost, error with reason). Update the widget. `CookieManager.flush()`, `webView.destroy()`, release the wake lock, `stopForeground(STOP_FOREGROUND_REMOVE)`, `stopSelf()`. If the result is unknown, schedule an exact-alarm check at T+3 min. |

**Fire-time maths:** `fireTrueMs = T - L - latencyCompensation`. latencyCompensation is a measured same-origin round trip (median of a few requests) [inferred]. Convert with the SNTP anchor; never use `System.currentTimeMillis()` directly.

### 12.4 Fallbacks
- **`ForegroundServiceStartNotAllowedException`:** post a HIGH notification "Tap to arm snipe now". A notification tap allows a foreground-service start.
- **Missed A1:** A2 covers it, and BOOT_COMPLETED, MY_PACKAGE_REPLACED and TIME_SET reschedule. A Task Manager "Stop" keeps alarms, so A2 still fires.
- **No validated network at T-120 s:** register a `ConnectivityManager.NetworkCallback` and retry. By T-L-2 s, fail with a notification.
- **Renderer gone:** rebuild and reload if more than about 15 s remain [inferred threshold]. Otherwise fail with a notification.
- **SNTP fails:** use `currentNetworkTimeClock()`, then the device clock with a larger L. Tell the user that time accuracy was reduced.
- **Session expired:** the T-10 min check gives time to sign in again. At T-120 s, try once to reload, then notify.
- **Reboot before unlock:** the direct-boot notification asks the user to unlock.
- **Memory-limiter kill (Android 17):** A2 retries. Log `ApplicationExitInfo.getDescription()`.
- **Live Update not allowed:** fall back to a normal ongoing service notification with the same countdown.

### 12.5 Test commands (adb)
- `adb shell dumpsys deviceidle force-idle`, then `adb shell dumpsys deviceidle unforce`, then `adb shell dumpsys battery reset` (doze-standby doc).
- `adb shell am set-standby-bucket <pkg> rare|restricted` and `adb shell am get-standby-bucket <pkg>` (Android 16 doc).
- `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED <pkg>` and `adb shell am compat enable FGS_BOOT_COMPLETED_RESTRICTIONS <pkg>` (Android 15 doc).
- `adb shell cmd activity stop-app <pkg>` (Task Manager stop).
- `adb shell am memory-limiter status` (Android 17).
- `adb shell dumpsys alarm` (pending alarms and allow-while-idle history) and `adb shell dumpsys activity services <pkg>` [standard dumpsys; output format not checked here].
- With the screen off, check `document.visibilityState` and rAF in the headless WebView using `evaluateJavascript`.

---

## 13. Things that remain uncertain (see open_questions)
- rAF and layout in a never-attached WebView with the screen off.
- The exact build on the user's phone.
- Pixel overlays for the NTP server and threshold.
- Whether eBay's passkey flow fails as predicted.
- The default USE_FULL_SCREEN_INTENT state for sideloaded apps on Android 17.
- Android 17 memory-limiter thresholds for the WebView renderer.
