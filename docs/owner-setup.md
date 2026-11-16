# Owner setup: switch on Ask Ustaad, videos and the full course

These steps are done once by the owner. The automatic safety rules stop the assistant from deploying to the live server, changing the live database or publishing content on its own.

## This round: student care, allowance and Google sign-in (about 15 minutes)

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

