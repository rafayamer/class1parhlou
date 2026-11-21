# Owner setup: switch on Ask Ustaad, videos and the full course

These steps are done once by the owner. The automatic safety rules stop the assistant from deploying to the live server, changing the live database or publishing content on its own.

## Newest round: App design (5 minutes)

### Apply the App design database update (2 minutes, needed)
Open `supabase/migrations/20261121000001_app_customization.sql` → **Copy raw file** → paste into
https://supabase.com/dashboard/project/hulfrloizndafqchcwhz/sql/new → **Run**. (If `20261119000004_security.sql` and
`20261120000001_audit_fixes.sql` aren't applied yet, run those two first, in that order.)

### What the App design page does
Owner panel → **App design**. Every change shows in the phone preview first; **Publish to students** sends it, and phones use it
the next time they open the app (offline phones keep the last version they saw). Nothing here needs a new APK.
- **Brand**: the app's main colour (buttons, rings, the selected tab). The page warns when white text would be hard to read.
- **Announcement**: a card at the top of Home with an emoji, title, message and an optional https:// button; for everyone, free
  students or Plus students; with optional start and end dates. "Show again to students who closed it" re-posts it.
- **Features**: switch the League tab, daily chest, celebrations, the Ask Ustaad button on Home, Guess Paper, past papers, Make my
  own test, video lessons and the Plus upgrade on or off. "Try on a share of students first" gives a feature to 5–95% of
  students (each student always gets the same answer), then **Give to everyone**.
- **Game & XP**: XP for lessons, written answers and papers, the daily chest's prize range and the daily goal.
- **Texts**: the line under the Home greeting, the evening reminder (`{streak}` becomes the streak length) and the Plus pitch.
- **App health**: maintenance mode (covers the app with "We'll be right back") and a minimum build that must update first. Both
  ask you to confirm before publishing.
- **History**: every publish is kept; load an earlier one, check it and publish to restore it. You can also download a backup file.

## Previous round: $3 AI cap, League, Plus payments and ads (about 20 minutes)

### 1. Apply three database updates, in this order (5 minutes, needed)
For each file: open it, **Copy raw file**, paste into https://supabase.com/dashboard/project/hulfrloizndafqchcwhz/sql/new and **Run**.
1. `supabase/migrations/20261119000001_ai_dollar_cap.sql`: the monthly AI allowance in dollars, shown as a meter in Profile.
2. `supabase/migrations/20261119000002_social.sql`: League tab (weekly leagues, friends by code, 👏 nudges, class groups).
3. `supabase/migrations/20261119000003_plans_payments.sql`: Free and Plus plans, and the **Payments** page in the owner panel.

Then redeploy the AI server code (Step 1 below) so the gateway gives Plus students $3 and free students $0.50 a month.
Until 3 is applied, everyone keeps the $3 allowance as before.

### 2. Payments by JazzCash / Easypaisa (5 minutes)
No registered business is needed: students send money to **your personal wallet**, then type the transaction ID (TID) in the app.
1. Owner panel → **Payments** → **Plans**: set the price (default Rs 499 a month), the two allowances, and your JazzCash and/or
   Easypaisa number and account name → **Save**. Until a number is saved, the app says "Payments open soon".
2. When a student pays, they appear under **Payments to check**. Match the TID and amount with the SMS in your wallet app, then
   **Approve** (Plus switches on for the months paid) or **Reject** with a short reason (the student sees it).
3. Keep a simple record of payments for tax. Personal wallets have monthly receiving limits (check yours in the app); when sales
   grow, register a sole proprietorship (NTN from FBR, free online) and move to a JazzCash/Easypaisa merchant account or Google
   Play Billing.

### 3. Ads (AdMob, 15 minutes, can wait)
The app already shows test ads to free-plan students after every second finished quiz (at most one every 10 minutes, never in
lessons, papers or Ask Ustaad). They are child-safe: child-directed, G-rated, never personalised, no advertising ID.
1. Make an account at https://admob.google.com with your Google account (an individual is fine; payments need your CNIC-linked bank
   account and tax info later).
2. **Apps → Add app → Android → not published yet**, name Parhlou. Copy the **App ID** (`ca-app-pub-…~…`).
3. **Ad units → Interstitial**, name "After quiz". Copy its ID (`ca-app-pub-…/…`).
4. In AdMob **Settings → Blocking controls**, set the max ad content rating to **G**, and turn on "treat as child-directed".
5. Send me both IDs (they're not secret) and I'll put them in the build. To switch ads off at any time: owner panel → Payments →
   untick "Show ads".

## Earlier round: student care, allowance and Google sign-in (about 15 minutes)

### A. Apply the student-care database update (2 minutes, needed)

This turns on the monthly Ustaad allowance, Ustaad's memory, saved chats (30 days), safety flags, feedback, and "view as student" in the owner panel. Until it is applied, the app works as before with no allowance, and the panel's **Student care** page asks for it.
1. Open the file: https://github.com/rafayamer/class1parhlou/blob/claude/confident-planck-dn7fy2/supabase/migrations/20261117000001_students_care.sql → **Copy raw file**.
2. Open https://supabase.com/dashboard/project/hulfrloizndafqchcwhz/sql/new → paste → **Run**. It should say "Success. No rows returned".
3. In the owner panel, open **Student care**. It should show "No safety flags" and an empty feedback list.

### B. Google sign-in (paused)

Paused for now: the app hides the "Continue with Google" button (`GOOGLE_SIGN_IN = false` in `AccountScreens.kt`). When you want it, do these steps and ask me to switch it on.


1. **Google Cloud:** go to https://console.cloud.google.com/apis/credentials (the Parhlou project) → **Create credentials → OAuth client ID**.
   - If asked, set up the consent screen first: User type **External**, app name **Parhlou**, your support email → **Save**.
   - Application type **Web application**, name **Parhlou Supabase**.
   - **Authorised redirect URIs:** `https://hulfrloizndafqchcwhz.supabase.co/auth/v1/callback`
   - **Create**, then copy the **Client ID** and **Client secret**. Don't paste them in the chat.
2. **Supabase:** go to https://supabase.com/dashboard/project/hulfrloizndafqchcwhz/auth/providers → **Google** → turn on → paste the Client ID and Client secret → **Save**.
3. **Redirect:** go to https://supabase.com/dashboard/project/hulfrloizndafqchcwhz/auth/url-configuration → **Redirect URLs** → check `pk.parhlou.student://auth` is listed. It was added for email links; add it if it's missing.
4. **Test:** in the app, Profile → Sign in → **Continue with Google**. A student who already signed in by email with the same Gmail address gets the same account.

### C. Publish the checked course (1 minute)

The same as Step 4 below. The new course has the audit corrections; the report is in `docs/content-audit-class9-maths.md`, and 27 items there are listed for a teacher to confirm.

---

## Step 1: Turn on the new AI server code (about 5 minutes, once)

1. **Create a Supabase access token:** open https://supabase.com/dashboard/account/tokens → **Generate new token** → name it `github-deploy` → copy it. It starts with `sbp_`; don't paste it in the chat.
2. **Save it in GitHub:** open https://github.com/rafayamer/class1parhlou/settings/secrets/actions/new
   - **Name:** `SUPABASE_ACCESS_TOKEN`
   - **Secret:** paste the token → **Add secret**.
3. **Tell me "secret added".** I'll push the next AI change, and GitHub deploys it by itself; watch it at https://github.com/rafayamer/class1parhlou/actions/workflows/deploy-ai.yml (a green tick means it worked). From then on, every AI change deploys itself. (GitHub only shows a "Run workflow" button for workflows on the main branch, and this work is on the `claude/confident-planck-dn7fy2` branch.)
4. **Check it:** open https://supabase.com/dashboard/project/hulfrloizndafqchcwhz/functions. The function **ai** shows a new version, updated just now.

## Step 2: Apply the small database change (2 minutes, optional but recommended)

This adds the video cache and lets you change the daily photo limit in the owner panel.
1. Open the file: https://github.com/rafayamer/class1parhlou/blob/claude/confident-planck-dn7fy2/supabase/migrations/20261116000001_ask_ustaad.sql → the **Copy raw file** button.
2. Open the SQL editor: https://supabase.com/dashboard/project/hulfrloizndafqchcwhz/sql/new → paste → **Run**. It should say "Success. No rows returned".
3. Run it once only; running it again is harmless.

## Step 3: YouTube videos inside the app (10 minutes, optional)

1. Create a Google Cloud project: https://console.cloud.google.com/projectcreate → name `Parhlou` → **Create**.
2. Turn on the YouTube API: https://console.cloud.google.com/apis/library/youtube.googleapis.com → choose the Parhlou project → **Enable**.
3. Create the key: https://console.cloud.google.com/apis/credentials → **Create credentials → API key** → copy it.
   - Then **Edit API key** → **API restrictions → Restrict key → YouTube Data API v3** → **Save**.
4. Save the key in Supabase: https://supabase.com/dashboard/project/hulfrloizndafqchcwhz/functions/secrets → **Add new secret**
   - **Name:** `YOUTUBE_API_KEY`
   - **Value:** the key → **Save**.
5. **Cost:** free, within YouTube's daily quota of about 100 topic searches per day. Each topic is searched only once, because results are cached. Without this step, the app shows a "Find a video lesson" button that opens YouTube search.

## Step 4: Publish the full course (1 minute)

1. Open the owner panel: https://rafayamer.github.io/class1parhlou/ → sign in with your owner email.
2. Go to **Content** → **pb-9-math** → check it says "13 units" and all checks pass → **Publish**.
3. Students get the full course the next time they open the app. You can roll back any version from the same page.

## Step 5: Update the app on your phone

Install build 21 or newer from https://github.com/rafayamer/class1parhlou/releases/latest. If an older Parhlou build is already installed, it offers the update by itself.

## Then tell me "done"

I will then:
- check the live AI with 3 real photos (a textbook question, a handwritten one, a guess-paper one);
- confirm the course is live;
- fix anything that comes out wrong.

