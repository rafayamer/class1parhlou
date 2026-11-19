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
