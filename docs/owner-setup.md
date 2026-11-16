# Owner setup: switch on Ask Ustaad, videos and the full course

These steps are done once by the owner. The automatic safety rules stop the assistant from deploying to the live server, changing the live database or publishing content on its own.

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

