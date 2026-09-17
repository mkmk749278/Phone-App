# DualShieldPhone

A private, offline Android **Phone + SMS app** with a per-SIM call firewall built in.

The product principle, in one line:

> Normal phone experience on the surface. Independent dual-SIM protection underneath.

DualShieldPhone is a dialer and messaging app first. Shield is the fourth tab, not the
front page. A user should spend almost all of their time in it without thinking about
filtering at all.

---

## The two guarantees

**1. SIM isolation.** Every rule is explicitly scoped to SIM 1, SIM 2, or — deliberately,
never by default — both. A rule you wrote for your personal line can never silently block a
call on your duty line. This is enforced structurally: a rule is only ever *indexed* for the
slots its scope covers, so cross-SIM leakage is impossible rather than merely unlikely.

**2. Fail open.** If the SIM cannot be identified, if the slot has no profile, if the rule
snapshot is unavailable, if a regex will not compile, if the Vault write fails, or if
anything throws — the call rings. Blocking is only ever the result of a positive, deliberate
match. `RuleEngine.evaluate` catches `Throwable` and returns an `Allow`, and the return type
has no third state.

Emergency and safety numbers (112, 100, 101, 102, 108, 1091, 1098, 1930 and friends) are
checked before any rule and can never be blocked.

---

## How a call is decided

```
INCOMING CALL
      │
      ▼
 RESOLVE SIM  ──── unknown ────► ALLOW
      │
      ├── SIM 1 ──► SIM 1 rules only
      └── SIM 2 ──► SIM 2 rules only
                         │
                         ▼
             1. Emergency numbers        → ALLOW
             2. This SIM's allowlist     → ALLOW
             3. Your allow rules         → ALLOW
             4. Your block rules         → BLOCK
             5. Built-in India pack      → allow beats block
             6. Optional heuristics      → BLOCK
             7. Nothing matched          → ALLOW
                         │
                    ┌────┴────┐
                  BLOCK     ALLOW
                    │
              Vault record, then reject
```

A blocked call is written to the Shield Vault **before** it is rejected. If that write
fails, the call is allowed instead — a call that vanishes with no record is a worse outcome
than one unwanted ring.

Architecturally:

```
Compose UI → ViewModel → Repository → Room
                              │
                              ▼
                   in-memory RuleSnapshot (pre-indexed, pre-compiled)
                              │
                              ▼
                     CallScreeningService
```

The screening service never touches Room on the call path. It reads a snapshot that a
collector keeps warm from process start; regexes are compiled when rules are saved, not when
a call arrives.

---

## Two histories

| | Contains | Where |
|---|---|---|
| **Recents** | Calls that actually happened | Phone tab, read from the system call log |
| **Shield Vault** | Calls Shield rejected | Shield tab, stored only in the app's database |

Blocked calls never appear in Recents. The app does **not** modify or delete Android's call
log to achieve that — it simply keeps its own separate record. `setSkipCallLog(true)` is
requested, but the platform only honours it reliably for the default phone app, and OEM
behaviour varies. The app never claims otherwise.

---

## Privacy

DualShieldPhone has **no `android.permission.INTERNET`**. It cannot make a network request.

- No ads, analytics, telemetry, crash reporting or tracking SDKs
- No cloud rule database and no auto-updating spam list
- No WebView
- Contacts are read from the device's own provider; there is no reverse-lookup service
- Rules and Vault are excluded from cloud backup and device-to-device transfer

This is enforced, not just asserted. `./gradlew checkNoInternetPermission` inspects the
**merged** manifest — ours plus every library's, after manifest merging — and fails the build
if any network permission survived. It runs automatically as part of `assemble` and
`bundle`, and as its own CI step.

To break the privacy guarantee you would have to defeat a build failure, which is the point.

---

## Built-in India rule pack

Shipped at `app/src/main/assets/rules/india_rules.json`, versioned and re-importable.

| Series | Category | Default | Why |
|---|---|---|---|
| `140…` | Promotional / telemarketing | **BLOCK** on SIM 2 | The regulated Indian series for marketing calls |
| `1600…` | Transactional — BFSI / government | **ALLOW** both SIMs | Service and transactional traffic, not marketing |
| `1601…` | Transactional — other sectors | **ALLOW** both SIMs | Same, for non-BFSI senders |
| `1800…` | Toll-free | **ALLOW** both SIMs | Usually a company's own support line |
| `0900…` | Premium-rate | **BLOCK** on SIM 2 | Chargeable premium service series |
| BPO / collection digit patterns | Community heuristic | **OFF** | User observations, not a telecom classification |
| Unknown / private / international | Caller class | **OFF** | Each catches legitimate callers too |

Two deliberate choices here:

- **Nothing unwanted is called "spam."** 140 is a *regulated promotional series*, not a scam
  series, and the UI says so. Accurate language is what lets a user decide sensibly.
- **Heuristics are never dressed up as official.** Every community pattern carries a
  `LOW confidence · Community heuristic` badge and ships disabled, and every Vault record
  names the exact rule that matched so a false positive is fixed in one tap.

There is no blanket short-code block: short codes carry legitimate operator and service
traffic.

---

## Fresh-install defaults

```
SIM 1 — Duty       Protection OFF      (the duty line is never filtered until you ask)
SIM 2 — Personal   Protection ON

  140 Promotional            BLOCK
  0900 Premium-rate          BLOCK
  1600 / 1601 Transactional  ALLOW
  1800 Toll-free             ALLOW

  Unknown / private          OFF
  International              OFF
  BPO / collection heuristic OFF
  VoIP / cloud heuristic     not shipped
```

`BundledIndiaPackTest` asserts all of this against the file that actually ships, so an edit
to the JSON cannot quietly change what a fresh install does.

---

## Building

Requirements: JDK 17, Android SDK with API 35.

```bash
./gradlew assembleDebug              # debug APK
./gradlew testDebugUnitTest          # unit tests
./gradlew lintDebug                  # Android lint
./gradlew checkNoInternetPermission  # offline privacy gate
./gradlew assembleRelease bundleRelease
```

| | |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 (dynamic colour, light and dark) |
| Storage | Room, schemas checked in under `app/schemas/` |
| Min / target SDK | 29 / 35 |
| DI | A hand-written container — background entry points need the same singletons as the UI, and a plain container gives that without annotation processing |

---

## Continuous integration

`.github/workflows/android.yml` runs on every push, pull request and manual dispatch:

| Job | Does |
|---|---|
| `verify` | Gradle wrapper validation, unit tests, Android lint, the offline privacy gate; uploads test and lint reports |
| `debug-apk` | Builds and uploads the debug APK on every branch |
| `release-artifacts` | Signed release APK + AAB on the default branch, on `v*` tags and on manual dispatch; attaches them to the GitHub release for a tag |

### Signing secrets

Release signing is driven entirely by **GitHub Actions secrets**. Nothing secret is in this
repository, and `*.jks` / `*.keystore` are gitignored.

| Secret | Contents |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The release keystore, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

To create a keystore and load the secrets:

```bash
keytool -genkeypair -v \
  -keystore dualshield-release.jks \
  -alias dualshield \
  -keyalg RSA -keysize 4096 -validity 10000

base64 -w0 dualshield-release.jks > keystore.b64   # macOS: base64 -i ... -o ...

gh secret set ANDROID_KEYSTORE_BASE64   < keystore.b64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD

rm keystore.b64        # and keep dualshield-release.jks somewhere safe and offline
```

Keep the keystore. Losing it means you can never ship an update to an installed copy of the
app under the same signature.

In CI the keystore is decoded to `$RUNNER_TEMP` — never into the workspace, so it cannot be
swept up by an artifact upload — and deleted in an `always()` step. When the secrets are
absent (a fork PR, or a repository not yet configured), the release builds **unsigned**
rather than failing, and the job summary says so.

Locally, the same four environment variables drive signing:

```bash
DSP_KEYSTORE_FILE=/path/to/dualshield-release.jks \
DSP_KEYSTORE_PASSWORD=... DSP_KEY_ALIAS=... DSP_KEY_PASSWORD=... \
./gradlew assembleRelease
```

---

## Permissions and roles

Permissions are requested contextually, not as a first-run checklist. A user who never opens
Messages is never asked for SMS access.

| Role | Needed for | Without it |
|---|---|---|
| Call screening | Shield blocking calls | Shield cannot block anything |
| Default phone app | In-call UI, dual-SIM outgoing calls | Calls open in the system dialer |
| Default SMS app | Sending and storing SMS | Messages are read-only |

Only the call-screening role is required for the firewall itself. The other two are what make
this a phone app rather than a filter.

---

## What is not here yet

Stated plainly, because a roadmap that pretends to be a feature list is how users end up
trusting protection that does not exist:

- **Message filtering** (`NotificationListenerService`). The Vault has its blocked-messages
  tab and storage, but nothing writes to it yet. When it lands it will record what it hid and
  never delete the underlying SMS.
- **Repeated-caller and time-based rules.** `REPEATED_CALL` is modelled and explicitly
  rejected by the validator rather than silently accepted as a rule that never fires.
- **MMS retrieval**, which needs network access the app does not have.
- **VoIP / cloud heuristics.** The category exists; no rule ships, because there is no
  reliable Indian numbering pattern a third-party app can use to identify VoIP callers, and
  guessing would produce confident false positives.

## Testing on real hardware

Telecom behaviour is not uniform. Before trusting a build, verify on at least an AOSP-like
device plus one Samsung and one Xiaomi/POCO: role granting, SIM resolution, screening,
rejection, notification suppression, system call-log behaviour, and dual-SIM outgoing calls.
