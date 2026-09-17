# Device testing

Everything in this file needs a real phone. None of it can be checked in CI, and the unit
tests deliberately do not pretend to cover it: they verify decisions and data, not what
Telecom, the OEM or the modem actually do.

Target device is a dual-SIM POCO/Xiaomi running HyperOS. Where a result is expected to differ
by manufacturer, that is called out rather than treated as a pass/fail.

## Before you start

```bash
adb install -r app/build/outputs/apk/preview/app-preview.apk
```

Install the **preview** variant, not debug. A debug build runs unshrunk with `debuggable=true`
and Compose is dramatically slower under it — judging the dialer's responsiveness on a debug
APK will mislead you.

Make DualShieldPhone the default phone and SMS app, then grant phone, contacts and SMS
permissions. Settings → Shield & blocking should show protection active on at least one SIM
before any blocking test.

Useful throughout:

```bash
# Everything this app logs, nothing else
adb logcat -s ShieldScreening:* ShieldEngine:* CallFrequency:* RecordingCapability:*

# Clear first so a run's output starts empty
adb logcat -c
```

---

## 1. Screen-off blocking (§54)

**The single most important test in this file.** The whole point of the product is that a
blocked call does not disturb you.

1. Add a block rule for a number you can call from.
2. Lock the phone. Wait until the display is fully off.
3. Call from that number.

Expected:

- [ ] The call is rejected.
- [ ] **The display stays off.** It does not flash, wake or show a lock-screen call UI.
- [ ] No DualShieldPhone screen appears.
- [ ] No ringtone or vibration.
- [ ] `logcat` shows `Blocked a call on slot N via rule '...'`.
- [ ] Opening the app afterwards shows the call under Recents → Blocked call logs.

If the display wakes: capture `adb logcat` for the whole event and note it as an OEM
behaviour. The app requests no wake lock, launches no Activity and sets no screen-on flag on
the blocking path — a test in `ScreeningPathTest` enforces that — so a wake is HyperOS's own
Telecom UI, not something this app can switch off.

## 2. Blocking speed

Android gives the screening callback a few seconds and the caller hears the delay.

```bash
adb logcat -v time -s ShieldScreening:*
```

- [ ] Time between the call arriving and the `Blocked a call` line is well under a second.
- [ ] **Cold start:** force-stop the app, then call from a blocked number immediately.
      Expect the call to **ring** — on a cold start the rule snapshot may not have loaded and
      the app allows rather than making Telecom wait. This is intended (§55), not a bug.
      Verify a second call from the same number is then blocked.

```bash
adb shell am force-stop com.dualshield.phone.preview
```

## 3. Dual-SIM isolation (§6, §14)

A fresh install ships **SIM 1 protected and SIM 2 not**. Confirm that on the Shield screen
before anything else: SIM 1 reads "Protected", SIM 2 reads "Unprotected · Shield is off for
this SIM".

> If you are upgrading rather than installing fresh, your existing choice is kept — the app
> does not re-decide which line is filtered. Check Shield and set it the way you want before
> running these.

With the shipped defaults, and a block rule covering both SIMs:

- [ ] Calling the **SIM 1** number from the blocked number → blocked.
- [ ] Calling the **SIM 2** number from the same blocked number → **rings**.
- [ ] Recents shows the correct SIM label on each call.
- [ ] The labels shown are the ones you set, not "Duty"/"Personal". An unnamed SIM reads
      "SIM 1" / "SIM 2", never "SIM 1 · " with nothing after it.

Then invert it — protection ON for SIM 2 only — and repeat. A failure here is the worst class
of bug this app can have: a rule for one line silently affecting the other.

## 3b. Rules survive protection being switched off (§SIM policy)

- [ ] With SIM 2 unprotected, open Shield → SIM 2. The screen says the rules are saved but
      not enforced, and the rules are still listed.
- [ ] Open India protection from there: it says the same, and the switches keep their state.
- [ ] Turn SIM 2 protection ON. Call from a number its rules block → blocked.
- [ ] Turn it OFF again. The same number → rings, immediately, with no restart.
- [ ] Reboot. SIM 2 is still off, and its rules are still exactly as you left them.

## 4. Shield Pause (§Pause)

- [ ] Pause for 1 hour on both SIMs. Status shows "resumes at HH:MM".
- [ ] A previously blocked number now rings.
- [ ] Force-stop the app, reopen: still paused, same expiry.
- [ ] Reboot the phone: still paused, same expiry — not extended, not reset.
- [ ] Wait for expiry (or set a 1-hour pause and change the system clock forward):
      the same number is blocked again, still without waking the screen.
- [ ] Your rules are untouched throughout — check Blocked numbers before and after.
- [ ] Pause SIM 1 only: SIM 2 still blocks.

## 5. Behavioural protection (§59)

Off by default — confirm that first.

- [ ] Settings → Shield → Repeated callers shows **Normal** on a fresh install.
- [ ] Set it to "Screen suspicious calls" on one SIM.
- [ ] Call 5 times in one day from an unsaved number. The 5th should be **silenced**:
      no ring, but it still appears in Recents and can be returned.
- [ ] Save that number to contacts, call again: it **rings**. Contacts are exempt.
- [ ] A first call from a new unsaved number always rings.

`setSilenceCall` behaviour varies by manufacturer. If HyperOS rings anyway, note it here —
the app is asking Telecom to silence, and cannot force it.

## 6. Dialer and T9 (§7–§10)

- [ ] Type `5474` → Kishore (or your equivalent contact) appears.
- [ ] Type the surname's T9 digits → the contact appears.
- [ ] Type `96185` → numbers starting 96185 appear, including ones saved as `+91 96185…`.
- [ ] **The keypad does not move** as results appear and disappear. Watch one key's position.
- [ ] Contact photos appear in results, not just initials.
- [ ] A contact saved under several spellings of one number appears **once**.
- [ ] Typing feels immediate — no lag between keypress and list update.

## 7. Recents and Call details (§16–§24, §60)

- [ ] Make a call, hang up, return to Phone: it appears without restarting the app.
- [ ] Correct name, photo, number, call type, SIM label, time.
- [ ] Tap a row → action sheet opens (not the details screen).
- [ ] Tap the chevron → Call details opens.
- [ ] Call details shows one button per SIM; each places the call on that line.
- [ ] WhatsApp opens the real app at the right conversation.
- [ ] Save contact opens the system editor; after saving, Recents shows the new name
      **without** restarting the app.
- [ ] Block, then reopen details: the row reads Unblock.

## 8. Blocked call logs (§25)

- [ ] Recents shows **one** "Blocked call logs" row, not one row per blocked call.
- [ ] The count matches.
- [ ] Opening it shows one row per caller with an attempt count, e.g. `(17)`.
- [ ] Call repeatedly from one blocked number: the count increments, Recents stays clean.
- [ ] Restart the app: both histories are still correct.

## 9. Messages (§32, §33, §55)

- [ ] A bank SMS shows as e.g. `AXISBK`, **not** "Private number".
- [ ] A second message from the same sender joins the same conversation.
- [ ] Opening a conversation with a real number offers Call, WhatsApp and Details in the
      header; a sender-ID conversation offers none of them (nothing to dial).

## 10. Call recording (§38)

- [ ] Settings → Calling → Call recording says **not available on this device**, and explains
      why.
- [ ] There is no Record button anywhere in the in-call screen.

```bash
# Confirm neither audio permission is present in the installed app
adb shell dumpsys package com.dualshield.phone.preview | grep -i -E "RECORD_AUDIO|CAPTURE_AUDIO"
```

- [ ] That command prints nothing.

## 11. Navigation and insets (§57, §58)

Walk every path: Phone → Messages → Contacts → Phone, Phone → Dialer → Call details →
Contacts, Settings → Shield → Rule tester, and back through all of them.

- [ ] No ghosting — never two screens visible at once.
- [ ] Tab switches are instant.
- [ ] With gesture navigation on: no content under the gesture bar, the dialpad FAB is not
      hidden behind the navigation bar, and the keyboard does not cover the message input.

## 12. Privacy (§69)

```bash
adb shell dumpsys package com.dualshield.phone.preview | grep -i internet
```

- [ ] Prints nothing. The app has no network permission.

Optionally, with the phone in airplane mode, confirm every feature except actually placing
calls still works — the app should never need the network.

---

## Reporting a failure

For anything that fails, capture:

1. What you did, precisely enough to repeat.
2. `adb logcat` from `adb logcat -c` immediately before the action.
3. The device's Android and HyperOS version (`adb shell getprop ro.build.version.release`,
   `adb shell getprop ro.mi.os.version.name`).

A failure that only reproduces on this OEM is still worth filing — it just gets fixed
differently from one that reproduces everywhere.
