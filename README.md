# Pro Writer

Pro Writer turns your messages into a professional, mistake-free version, in any app on your
Android phone (WeChat, WhatsApp, Gmail, Messages, LinkedIn…).

**Floating button (recommended):** after you turn it on, a **✨ Pro** button appears above the
keyboard. When you stop typing for a moment, the professional version pops up automatically;
tap **Use** to replace your text, then send.

**Selection menu:** you can also select text and choose **Make Professional** from the menu
(may be under **⋮**). This works in most apps, but not in WeChat.

---

## 1. Get an API key

Pick one AI provider in the app and get a key for it:

- **Google Gemini (free):** go to <https://aistudio.google.com/apikey>, sign in with Google and
  tap **Create API key**. No card needed. On the free tier Google may use what you send to
  improve its products, and there is a daily limit.
- **Claude (paid, very cheap):** <https://console.anthropic.com/settings/keys>. Add credit under Billing.
- **DeepSeek (paid, very cheap):** <https://platform.deepseek.com/api_keys>. Add credit first.

Keys are stored only on your phone, inside the app. Keep them private.

## 2. Build and install the app

Choose **one** of these two ways.

### Option A: Android Studio (on your computer)

1. Install [Android Studio](https://developer.android.com/studio).
2. Unzip this project. In Android Studio choose **File → Open** and select the `ProWriter` folder.
   Wait for the first "Gradle sync" to finish (it downloads what it needs; this takes a few minutes the first time).
   If Android Studio offers to upgrade the Android Gradle Plugin, you can accept.
3. On your phone, turn on **Developer options** (Settings → About phone → tap *Build number* 7 times),
   then turn on **USB debugging**.
4. Connect the phone with a USB cable, pick it in the device list at the top, and press **Run ▶**.

To get an APK file instead: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
It appears in `app/build/outputs/apk/debug/app-debug.apk`; copy it to your phone and open it.

### Option B: No installs, use GitHub (builds the APK in the cloud)

1. Create a free account at <https://github.com> and make a **new repository**.
2. Upload all the files from this project folder (keep the folder structure, including the
   hidden `.github` folder; the easiest way is GitHub Desktop or `git push`).
3. Open the **Actions** tab. The "Build APK" job runs automatically (about 3–5 minutes).
4. When it finishes, open the repository's **Releases** page (on the main repository page) and
   download **ProWriter.apk** directly on your phone.
5. Open the APK. Allow "Install unknown apps" when asked.

## 3. Set it up on the phone

1. Open **Pro Writer**.
2. Choose your AI provider, paste its API key and tap **Save**.
3. (Optional) Tick **Always write the result in English** if you sometimes type in another
   language and want English output.
4. Use the **Try it here** box to test it.
5. Tap **Turn on floating button** and switch on **Pro Writer floating button** in Accessibility.
   If Android calls it a restricted setting: Settings → Apps → Pro Writer → ⋮ → **Allow restricted
   settings**, then try again. On Xiaomi phones, also set Battery saver to **No restrictions**.

## Tips and troubleshooting

- **I don't see "Make Professional".** Select the text, then tap **⋮** or the arrow in the
  pop-up menu; extra actions are often hidden there. Some apps with custom text boxes don't
  show this menu at all; in that case copy your text into the app's *Try it here* box.
- **Replace doesn't appear.** That app only lets you read the selected text, not change it.
  Use **Copy** and paste instead.
- **"Your API key was rejected".** Paste the key again, making sure you copied all of it.
- **"Credit balance is too low".** Add credit in the Anthropic console.

## For developers

- Plain Java, no third-party libraries. Main files are in `app/src/main/java/com/prowriter/app/`:
  - `ProcessTextActivity.java` handles Android's `ACTION_PROCESS_TEXT` (the selection menu).
  - `Rewriter.java` calls Gemini, Claude or DeepSeek. Model names are in the `Provider` list at
    the top; the writing rules are in `systemPrompt()`.
  - `MainActivity.java` is the settings and test screen.
- To rename the menu button, change `process_label` in `res/values/strings.xml`.
