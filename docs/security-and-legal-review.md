# Security and legal review (19 November 2026)

A white-hat review of the app, the AI server, the database and the owner panel, attacked the way a real attacker would.
Everything marked **Fixed** is in the code and tested. Items marked **You** need the owner; **Lawyer** need professional advice.

## Security findings

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| 1 | High | Signed-out callers could invent a new install ID per request and get a fresh AI allowance each time, draining the OpenAI balance. | **Fixed:** AI that costs money now needs an account (`SIGN_IN_REQUIRED`). |
| 2 | High | No overall money ceiling: many fake accounts could still add up. | **Fixed:** project-wide daily AI spend limit (`daily_usd_total`, default $1/day), set in the owner panel. |
| 3 | High | The email sign-in link carried the login token itself (`#access_token=`); another app claiming the same link scheme could catch it and sign in as the child. | **Fixed:** PKCE. Links carry a one-time code that only works with a secret kept on the phone; token links are refused. |
| 4 | High | The repository is **public**, including the prototype signing key and its password. Anyone can sign a fake "Parhlou update" that installs over the real app. | **Partly fixed:** builds now use a private key from GitHub secrets when one is added. **You:** create the Play upload key before launch (steps below); never ship real students an APK signed with the prototype key. |
| 5 | Medium | Friend and class-group codes could be guessed by script, letting strangers befriend children or join their class groups. | **Fixed:** 20 wrong codes per student per day, then locked. |
| 6 | Medium | Class group names (seen by other children) accepted any text. | **Fixed:** letters/digits/simple punctuation only, abusive English and Roman-Urdu words refused; owner can remove a group. |
| 7 | Medium | Saved login on the phone was plain text inside app storage. | **Fixed:** encrypted with the phone's hardware key store (AES-256-GCM). |
| 8 | Medium | A half-downloaded update could be handed to the installer ("package appears to be invalid"). | **Fixed:** size and SHA-256 checked against the release. |
| 9 | Low | Network rules were left at Android defaults. | **Fixed:** HTTPS only; certificates installed by users or malware are not trusted. |
| 10 | Low | The ads library added advertising-ID and ad-tracking permissions. | **Fixed:** all removed (children's app). |
| 11 | Low | Owner panel had no Content Security Policy. | **Fixed:** scripts only from the panel itself, network only to Supabase. |
| 12 | Low | Oversized requests were read before being rejected. | **Fixed:** requests over 16 MB refused up front. |

**Checked and found sound:**
- Row-level security is on for every table.
- Every privileged function has a fixed search path.
- Signed-out users can't call any privileged function.
- Owner powers are checked in the database, not the page.
- `set_owner_email` can't be called by users.
- AI requests are built from server-side content, with image type, size and count limits.
- Payment amounts are computed on the server.
- A transaction ID can't be reused.
- Account deletion removes all personal data.
- No secrets or personal details are in the repository; the publishable key is meant to be public.

## Pakistani legal checklist

| Area | What applies | Status |
|------|--------------|--------|
| Children's data | No Pakistani children's-privacy law yet; Google Play Families policy and the Personal Data Protection Bill's principles are the standard to meet. | **Fixed:** parent/guardian agreement at sign-up, minimal data, no advertising ID, child-directed G-rated ads, no chat between students, first name only in leagues. |
| Privacy policy | Required by Google Play and good practice under the PDP Bill (consent, purpose, access, correction, deletion, transfers abroad, breach notice). | **Fixed:** `privacy.html`, linked at sign-up and in Profile. Download, correct and delete are already in the app. |
| Terms of use | Not affiliated with PCTB/BISE; guess papers are predictions, not leaks; AI can be wrong; no promised results; PECA 2016 content rules; Pakistani law and Punjab courts. | **Fixed:** `terms.html`. |
| Minors and payments | A minor can't make a binding contract (Contract Act 1872). | **Fixed:** "I'm the parent or guardian, or my parent made this payment" must be ticked before "I've paid". |
| Consumer protection | Punjab Consumer Protection Act 2005: clear price, no misleading claims, fair refunds. | **Fixed:** price shown before paying; `refunds.html` (7-day refund, double payments always refunded). |
| Tax records | Income must be declared; records kept. | **Fixed:** payment records now survive account deletion, without the link to the student. **You:** declare the income; get an NTN when sales grow. |
| PECA 2016 / online content | Harmful or abusive content between users. | **Fixed:** no chat; filtered group names; Ustaad refuses and flags harmful messages; feedback route for complaints. |
| Payments to a personal wallet | Fine for small amounts; wallets have monthly limits, and commercial use at scale should move to a merchant account. | **You:** watch wallet limits; move to a merchant account or Google Play Billing as sales grow. |
| **Copyright of textbook and past papers** | PCTB owns the textbooks; the boards own their past papers (Copyright Ordinance 1962). Lessons quote and follow the books closely. | **Lawyer / You:** the biggest legal risk. Ask PCTB for written permission, or have a lawyer review "fair dealing for education". Our own explanations, solutions and questions are safer than reproduced book text and figures. |
| Data stored abroad | Supabase, OpenAI and Google servers are outside Pakistan. Allowed today; the PDP Bill may later restrict "critical" data. | Disclosed in the privacy policy. Re-check if the bill becomes law. |

## What the owner needs to do
1. Run `supabase/migrations/20261119000004_security.sql` in the Supabase SQL editor, after the three earlier ones.
2. Owner panel: the daily AI limit is $1 by default. Raise it only when you have more OpenAI credit.
3. Before the Play Store: create a private upload key and add it to GitHub secrets (`RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`). I'll give exact commands when you're ready. Use Google Play App Signing.
4. Consider making the repository private at launch. Note that prototype in-app updates download from its public releases.
5. Make a support email and tell me; I'll add it to the policies.
6. Copyright: write to PCTB for permission to use the textbooks in a learning app, or get a lawyer's opinion.
7. Have a Pakistani lawyer read the three policy pages once before launch. They're written to be correct, but they're not legal advice.

## Full audit (20 November 2026)

A second, deeper pass over everything: every database function and policy, the AI server line by line, Android lint and
manifest, the owner panel, the build pipelines and every dependency. All fixes are tested (database: 12 new tests; AI server:
type-checked in CI from now on; app: full suite).

| # | Severity | Finding | Fix |
|---|----------|---------|-----|
| A1 | High | **AI server bug:** after a student's 10th Ustaad message, every reply made an extra AI call that crashed before its cost was logged (undefined variable). Unlogged spend escaped every limit, and Ustaad's memory never saved. | Fixed. CI now type-checks the AI server before deploying, which catches this kind of bug. |
| A2 | High | Spending was added up from at most 1,000 rows (the database API's page size), so heavy users and the whole-app daily ceiling were undercounted. | Sums now run inside the database (`ai_spend`), server-only. |
| A3 | High | **App crash on Android 7.x:** dates, streaks and the calendar used Android 8+ code. | Core library desugaring: works on Android 7.1 and up. |
| A4 | High (latent) | The internal function that grants the owner role was callable by any signed-in user. It isn't reachable through the API today, but would be if the private schema were ever exposed. | Locked to the server. |
| A5 | Medium | Made-up install IDs could still create credit and chat rows, and use the YouTube search, without an account. | Without an account only feedback, MCQ checking and the allowance screen work. |
| A6 | Medium | League XP and streaks are reported by the phone; any value up to 100,000 was accepted. | Capped at what real study can earn (1,500 XP per day of the week; a streak can't be older than the account). |
| A7 | Medium | Avatars accepted any 4 characters (a short rude word other children would see); first names in leagues weren't filtered. | Avatars from the app's list only; unsuitable names show as "Learner"; existing rows cleaned. |
| A8 | Medium | A student could move their profile to another organisation (tenant). | Students can change only their name and birth year. |
| A9 | Medium | No limits on feedback, saved MCQ marks or synced events per student (storage abuse). | 20 feedback a day (300 shared by all guests), 3,000 marks and 20,000 events a day. |
| A10 | Medium | Links from course content and server replies were opened without checking the kind of link; "Watch on YouTube" could crash. | One safe opener: https web pages only, never crashes; YouTube video IDs validated. |
| A11 | Medium | The Play Store version would have contained the self-updater and the "install other apps" permission, which Play rejects. | Two versions: `prototype` (shared APK, as before) and `play` (no updater, no install permission, no wallet transfers, targets Android 16 as Play requires). |
| A12 | Low | The owner panel's test runner (Vitest) had known critical vulnerabilities (developer machines only). | Upgraded; `npm audit` reports 0. |
| A13 | Low | Build pipelines used movable version tags for third-party actions. | Pinned to exact commits. |
| A14 | Low | The parent email used in the owner panel's "Email parent" link came straight from what the student typed. | Used only if it's a plain email address. |
| A15 | Low | A crash inside an AI call returned a bare server error. | Returns `AI_UNAVAILABLE`; details only in the server log. |
| A16 | Low | The paper-sitting test sometimes timed out on a busy machine, which could block a CI build. | Longer waits; if it ever fails it now reports what the screen showed. |

**Checked and sound:** row security on every table; every privileged function has a fixed search path; no
privileged function callable without an account; only the main screen is exposed to other apps (plus three standard,
permission-protected Android components); no secrets in the code; no log output of personal data; HTTPS only; notifications
use immutable intents; account deletion removes everything; payments computed on the server.

**Run once in Supabase:** `supabase/migrations/20261120000001_audit_fixes.sql` (after the earlier ones). Until then the AI
server falls back safely to adding up rows.

**Still open (not code):** textbook copyright permission from PCTB; Google Play Billing for Plus in the Play version (needs a
Play Console account and products); a private upload key for Play (Play Console can also reset it later).

## App design (owner panel), 21 November 2026

The owner can now change the app's colour, a Home announcement, feature switches, XP numbers, maintenance mode and a minimum
version from the owner panel (migration `20261121000001_app_customization.sql`). How it stays safe:

- **Only the owner can write it.** `owner_set_config('app_customization', …)` checks the owner role, accepts only a JSON object
  under 20 kB, and every save goes to the audit log (which also gives the panel its History and restore).
- **Phones read only public values.** `public_app_settings()` returns the customisation and the ads switch, nothing else: no
  AI prices, wallet numbers or owner email (tested in `customization.test.sql`).
- **A bad value can't break the app.** The app re-checks everything: colours must be `#RRGGBB`, numbers are clamped to safe
  ranges, the announcement button opens only `https://` links through the app's one safe link opener, text is cut to a
  maximum length, and anything unreadable falls back to the built-in defaults. The panel applies the same limits and shows
  problems before publishing.
- **Mistakes are visible.** Turning on maintenance mode, raising the minimum version or switching a feature off for everyone
  asks for confirmation, and the Dashboard shows a warning while maintenance or a minimum version is on.
- **Students can always recover.** The maintenance screen has "Check again"; the app refreshes the settings every time it
  opens.
- "View as student" now shows the student's AI use this month against their plan's allowance (still logged on every look).

## Premium paywall and credits, 21 November 2026

- Premium status comes from the server (`my_plan`); the phone keeps the last confirmed end date so Premium still works offline,
  and it lapses on the phone too once that date passes.
- AI is enforced on the server, not the phone: every AI call checks `paid_until`, and free students get `PREMIUM_REQUIRED`
  (free allowance $0). Premium students are stopped at their month's allowance ($3.50 = 1,000 credits, counted from the day they
  paid) with `CREDITS_USED_UP`. The project-wide daily dollar ceiling still applies on top.
- Promo codes: only a SHA-256 hash of each code is stored (the owner sees the code once). Codes are 48 random bits, each account
  can redeem a code once, and 10 wrong codes in an hour block further tries for that hour. Redeeming needs a signed-in account.
  The Play build hides promo codes, because Google Play requires its own billing for digital goods.
- Cost estimates come from `ai_task_costs`, which only the gateway (service role) can call; students see only rounded credits.
- Lessons, papers and solutions ship inside the app's course file, so the paywall hides them in the app but can't stop someone
  who unpacks and modifies the APK. That is normal for an offline-first app at this stage; if it ever matters, Premium chapters
  can be delivered from the server per student instead. AI, which costs money, can't be unlocked this way.
- Under-18s: the payment screen still asks for a parent or guardian's agreement, and the refund policy is unchanged.

