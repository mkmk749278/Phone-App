# UI review checklist

This is section 101 of the handoff, turned into something you can actually run, plus the
parts of section 103 that need a phone.

**Read this first.** The code changes for sections 79–102 have been made and are covered by
unit tests where a unit test can decide the question — terminology, SIM naming, touch-target
minimums, navigation reachability, the spacing scale. None of that is the same as looking at
the app. Spacing, hierarchy, density and "does this feel like one product" are judgements a
test cannot make and this container cannot see: it builds an APK but has no device or
emulator to run one on.

So the boxes below are not ticked. They are for whoever has the phone.

```bash
adb install -r app/build/outputs/apk/preview/app-preview.apk
```

Use the **preview** variant. A debug build runs unshrunk with `debuggable=true` and Compose
is dramatically slower under it — judging whether a screen feels polished on a debug APK will
mislead you.

---

## How to run this review

Section 103 asks for two complete screen recordings, before and after. The "before" is the
recording that produced sections 79–102, so what is left is the second pass.

1. Record one continuous pass through every screen in the list below. Do not stop to take
   notes — the point is to see the flow, not the frames.
2. Watch the recording back before ticking anything. Screens that look fine while you are
   driving them look different when you are only watching.
3. Then go through the list with the phone in your hand.

What to look for, in the order it matters:

- **Does anything mislead?** A line shown as protected that is not, a label that says one
  thing and a switch that says another, a count that does not match what is below it. These
  are bugs, not polish.
- **Does anything block you?** Content under the navigation bar, a control you cannot reach,
  a keyboard covering the field it belongs to.
- **Does it feel like one app?** Whether Shield looks like it was built by the same people as
  Recents. This is the whole point of sections 79–102 and the hardest thing to see in a diff.

---

## Phone

- [ ] Phone home — recents list is the visual priority, not the header or the tabs
- [ ] Recents — one row per call, correct name, photo, type, SIM and time
- [ ] Recents — the oldest row is not hidden behind the dialpad button
- [ ] Favorites
- [ ] Search
- [ ] Dial pad — the keypad does not move as results appear
- [ ] Dial search results
- [ ] Outgoing call
- [ ] Incoming call — see §10b of `DEVICE_TESTING.md`; the pocket test is the one that matters
- [ ] In-call screen
- [ ] Call details — one button per SIM, each places the call on that line
- [ ] Blocked call logs — one row per caller with an attempt count

## Messages

- [ ] Message list — alphanumeric senders show the sender ID, not a single letter
- [ ] Search
- [ ] Conversation
- [ ] Compose
- [ ] Sender details
- [ ] Block / unblock
- [ ] SMS filtering state

## Contacts

- [ ] Contact list — a contact saved under several spellings of one number appears once
- [ ] Search
- [ ] Contact details
- [ ] Create contact
- [ ] Edit contact
- [ ] Contact photo — real photos, consistent fallback where there is none
- [ ] Multiple phone numbers

## Shield

- [ ] Shield status — which line is protected is answerable without scrolling
- [ ] SIM 1 protection
- [ ] SIM 2 protection — reads "Unprotected", and says the rules are saved but not enforced
- [ ] Pause Shield
- [ ] Blocked numbers
- [ ] Allowed numbers
- [ ] India blocklist — no regular expressions anywhere on the screen
- [ ] Recovery Call Protection — the two kinds of switch are distinguishable
- [ ] Rule tester
- [ ] Blocked call logs
- [ ] Advanced rules

## Settings

- [ ] Settings home — every Shield screen reachable from here
- [ ] SIM & calling
- [ ] Call recording
- [ ] Privacy
- [ ] About

## Setup

- [ ] Initial setup
- [ ] Permission states — a denied prompt is visible and fixable
- [ ] Default phone app
- [ ] Default SMS app
- [ ] SIM naming — no suggested label contradicts another screen's
- [ ] SIM protection choice

---

## Across every screen

These are the ones worth checking on more than one device.

- [ ] No screen shows `SIM 2 ·` with nothing after it.
- [ ] Every label a screen shows for a SIM is the label you set, on every screen.
- [ ] No navigation ghosting — never two screens visible at once.
- [ ] Back always returns to the parent you expect, from both the system gesture and the
      toolbar arrow.
- [ ] The selected bottom tab stays correct through every path.
- [ ] Opening the keyboard does not break a layout or cover the field it belongs to.
- [ ] Nothing sits under the navigation bar or the gesture bar.

Then repeat the parts that are about layout with:

- [ ] A small phone
- [ ] Font scale at maximum (Settings → Display → Font size)
- [ ] Display size increased
- [ ] Dark mode
- [ ] Light mode

Nothing should overlap, clip, disappear, become unreachable, or leave a large dead area.

---

## Found in the first recording (2026-09-18)

Four defects came out of the first review pass, none of which any test had caught, and all
four are fixed. Worth knowing what they were, because three of them were invisible to the
kind of test that was looking:

| What showed on screen | Why no test saw it |
|---|---|
| Every bank and operator message titled `S`, `T`, `P` or `G` | The sender-ID rule was right for the old two-part header and silently wrong for the current three-part one. Nothing asserted a sender is longer than a letter. |
| `SIM 1 ·` on the Call details buttons, with empty space after it | The *string* was correct. A single line that cannot fit drops the last whole word, so "Personal" vanished at layout time. |
| The same number as a contact's title and its subtitle | The provider fills the name column in with the number, so "was the name empty?" always answered no. |
| `919` as the avatar for every Indian mobile | Nothing said a monogram has to distinguish anyone. |

Each now has a test, and each test fails when the fix is reverted.

## What a unit test already covers

Do not spend review time on these; they fail the build if they regress.

| Question | Test |
|---|---|
| Is a SIM ever named `SIM 2 · ` with a dangling separator? | `SimLabelTest` |
| Does any screen build a SIM name by hand? | `SimLabelTest` |
| Does a regular expression reach the user? | `ProductLanguageTest` |
| Does an internal word ("Vault") reach the user? | `ProductLanguageTest` |
| Is the built-in list called one thing everywhere? | `ProductLanguageTest` |
| Does a switched-off rule advertise what it would do? | `ProductLanguageTest` |
| Is any shared control smaller than 48dp? | `DesignSystemTest` |
| Is the spacing scale a 4dp grid? | `DesignSystemTest` |
| Is every route reachable, and Shield kept off the tab bar? | `NavigationReachabilityTest` |
| Are the Shield screens reachable from Settings? | `NavigationReachabilityTest` |
| Does Shield lead with SIM state? | `NavigationReachabilityTest` |
| Is a rule enforced on a line whose Shield is off? | `SimProtectionPolicyTest` |
| Can a partial swipe answer a call? | `SwipeCommitTest` |

---

## Reporting

For anything that fails, capture what you did, a screenshot or a clip, and the device's
Android and HyperOS version:

```bash
adb shell getprop ro.build.version.release
adb shell getprop ro.mi.os.version.name
```

A layout problem that only appears at maximum font scale, or only on a short screen, is
still worth filing — it just gets fixed differently from one that appears everywhere.
