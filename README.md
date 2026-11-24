# Weekly Spend

An Android app that tracks spending automatically from bank SMS (Barq, Alinma) against a weekly envelope, and warns you when you get close to your limits.

Everything stays on the phone. The app has no INTERNET permission (it is stripped from the merged manifest), no accounts and no analytics, and it is excluded from cloud backup and device transfer.

- Kotlin, Jetpack Compose (Material 3), Room, WorkManager and AlarmManager
- minSdk 26, targetSdk 35
- Currency SAR. Weeks run Sunday to Saturday in the device time zone (Asia/Riyadh).

## Install the prebuilt APK

`dist/weekly-spend-debug.apk` is a debug build signed with the standard debug key.

1. Copy it to the phone, open it, and allow "Install unknown apps" for your file manager when asked.
2. Open **Weekly Spend** and tap **Allow** on the Home card. This grants SMS and notification permissions. Once SMS access is granted, the app scans the last 60 days of your inbox.
3. Tap **Exempt** on the battery card. On Samsung, also go to *Settings > Apps > Weekly Spend > Battery* and choose **Unrestricted**, then check that the app is not under *Battery > Background usage limits > Sleeping apps*.
4. Optional: long-press the home screen, choose *Widgets*, then *Weekly Spend*.

Android 13+ may block SMS permission for sideloaded apps ("Restricted setting"). If it does, go to *Settings > Apps > Weekly Spend*, open the ⋮ menu, tap **Allow restricted settings**, then grant the permission again.

## Build from source

Requirements: JDK 17 or newer, and the Android SDK with `platforms;android-35` and `build-tools;35.0.0`.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties   # or open the project in Android Studio
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`./gradlew assembleRelease` produces a release APK signed with the debug key, which is fine for sideloading.

## How it works

### SMS capture
- `SmsReceiver` handles incoming SMS. Multi-part messages are joined before parsing.
- `InboxScanWorker` runs once to scan the last 60 days of the inbox. You can run it again from *Settings > Rescan inbox*.
- Sender names come from *Settings > SMS senders* and are compared case-insensitively, ignoring spaces (`BARQ APP` = `barq app`).
- Deduplication uses a unique index on (sender, amount, timestamp). Two identical purchases in the same minute would be merged into one.

### Parser table (`parser/`)
Each row has:
- a sender (or `*`)
- a type: `IGNORE`, `POS`, `ONLINE`, `TRANSFER_OUT`, `TRANSFER_IN`, `SALARY` or `OTHER`
- a match regex
- optional regexes for amount, merchant, date, card and balance. The first capture group is the value, and a blank field falls back to the default in `FieldDefaults`.

Rows are tried in priority order and the first match wins. You can edit the table in *Settings > Parser table*, which also has a **Test an SMS** box that shows how a pasted message would be read, without storing anything.

- Dates: `yyyy-MM-dd HH:mm`, `yy-MM-dd HH:mm` and `MM-dd-yyyy HH:mm` (and `dd-MM-yyyy` when the first number is above 12). If the date is missing or implausible, the received time is used.
- One-time codes are blocked by a hard-coded `OtpGuard` before the table runs. A code is never stored or logged, whatever the table says.
- SMS from a tracked sender that no row can parse goes to **Inbox > To review**.

### Counting rules
| Type | Envelope |
|---|---|
| POS, online, manual | counts, unless flagged **Side line** or **Fixed** |
| Transfer out | listed under **Inbox > Transfers**, never counts. Marked as own-account if it matches *My own names / accounts* |
| Transfer in, salary | stored but ignored for spending |

### Limits
- Defaults: 245 SAR/week (35/day) until 2027-04-26, then 315 (45/day). All values and the switch date are editable.
- Leaving the daily limit blank means weekly/7.
- The week that contains the switch date is prorated day by day. For example, the week of 2027-04-25 is 35 + 6×45 = 305 SAR.

### Notifications
- **Budget alerts** (high importance): fire when today or the week crosses 80%, again at 100%, and then on every purchase while over. Each alert shows what is left and how many days are left. Alerts fire for live SMS and manual adds, not for the inbox scan.
- **21:00 reminder**, "Anything missing today?": type `12.5` or `8 coffee` straight into the notification to add an expense.
- **Sunday 20:00 summary** of the week that just ended: spent vs limit, best and worst day, side line total, fixed total.
- Optional persistent notification: "Left this week: X SAR (Y today)".
- Alarms use `USE_EXACT_ALARM` and `setExactAndAllowWhileIdle`. They are re-armed after a reboot, an app update or a time/time-zone change.

### Merchant rules
A rule applies when the merchant contains its text (case and spaces ignored), and the longest match wins. The defaults are:
- KING FAHAD U O P M, Saudi Marketing Co, Shatirti → Food
- Dunkin, Starbucks → Coffee
- Uber, Kiabi → Side line
- Tabby, Tamara, Noon One, Claude, Anthropic, Spotify → Fixed

To add a rule from a transaction, tap it (or swipe it right) and tick **Always put "…" here**. That also re-files the transactions you already have from that merchant.

## Tests
`app/src/test/` has 49 JVM unit tests:
- the parser against all 9 real samples (including the OTP and the activation notice)
- the three date formats
- week boundaries, limits and the switch-date proration
- alert thresholds
- counting, own-name and categorisation rules
- quick-add parsing, CSV escaping and alarm times

## Project layout
```
app/src/main/java/com/weeklyspend/
  parser/   table-driven SMS parser, date parsing, default rules (pure Kotlin)
  budget/   limits, week maths, alerts, categories (pure Kotlin)
  ingest/   SmsProcessor (pure), SMS receiver, inbox scan, quick-add receiver
  data/     Room database, settings, repository
  notify/   notification channels, alarms, boot receiver
  widget/   home-screen widget
  export/   CSV export
  ui/       Compose screens: Home, Week, Inbox (To review / Transfers), Add, Settings, rules editors
```
