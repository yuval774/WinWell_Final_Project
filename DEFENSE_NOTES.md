# WinWell — Project B Defense Notes

> Purpose: a running record of **everything we build**, so the presenter can defend any
> part of the app in the Zoom defense (worth 10% of the grade).
> Each feature below lists: what it does -> the file(s) it lives in -> the requirement it
> satisfies -> a one-line "how to explain it" for the defense.
>
> This file grows with every step. It is the only file we add outside the app code.

Team: Yuval Vin, Maya Bloom, Samuel Moldauer
Package: `com.winwell.app`

---

## Requirement -> where it's met (live checklist)

| Requirement (ProjectB.pdf) | Status | Where it lives |
|---|---|---|
| All 5 pages implemented | [ ] planned | Welcome, Login, SignUp, Onboarding, Chat |
| No static data (all in Firestore) | [ ] in progress | `users`, `bot_responses`, `suggested_activities` |
| Phone capability (Camera or GPS) | [ ] planned | _to be decided_ |
| Firebase Analytics | [x] from HW3 | LoginActivity, ChatActivity |
| Firebase Crashlytics | [x] from HW3 | ChatActivity (logs, recordException, test crash) |
| Firebase Firestore (dynamic data) | [x] from HW3 | ChatActivity (`bot_responses`) |
| Google Authentication (required) | [ ] planned | LoginActivity |
| 2nd auth method (+10 bonus) | [ ] planned | SignUpActivity (email/password) |
| GitHub (clean, runnable, documented) | [ ] planned | repo + README.md |
| Reflection document (Moodle) | [ ] planned | separate doc |

---

## Feature log

### Starting point (inherited from HW3)
- **LoginActivity** — currently a *hardcoded* email/password check
  (`uritheteacher@gmail.com` / `androidstudio`). **Will be replaced** with real Google Sign-In.
- **ChatActivity** — emotion-aware chat. Loads bot responses from Firestore `bot_responses`
  via a real-time `addSnapshotListener`. Logs Analytics `LOGIN` + `message_sent`,
  Crashlytics breadcrumbs + a Test Crash button, and shows a Lottie loading animation
  (3rd-party bonus).

_(New features will be appended here as we build them.)_

---

## Defense Q&A prep
_(We will fill this with likely questions Uri may ask and the answers, as the app takes shape.)_

---

## Phase 0 — Firebase & dependencies (in progress)

**Decisions locked:** Phone capability = **Camera** (onboarding profile photo). Firebase = **reuse the HW3 project** (`winwell-51ae4`).

**Dependencies added to `app/build.gradle`** (Uri's standard libraries from class 7):
- `firebase-auth` — backs both sign-in methods.
- `play-services-auth:21.2.0` — the `GoogleSignInClient` API for "Login with Google" (required auth).
- Glide `4.16.0` — loads the Google profile picture (and camera photo) into the avatar.
- CircleImageView `3.1.0` — shows profile pictures as a circle.
- **How to explain it:** "We added the same auth + image libraries Uri taught in class 7 — Google Play Services Auth for the Google login, Glide and CircleImageView for the profile picture."

**Console steps (done by the team in Firebase):** enabled Google + Email/Password providers, generated the debug **SHA-1** via `./gradlew signingReport`, added it to the `com.winwell.app` app, and re-downloaded `google-services.json` (now contains the OAuth client).

---

## Phase 1 — Auth foundation (Welcome, Login, SignUp)

### New screen: WelcomeActivity (the launcher)
- **What it does:** first screen; logo + "Win at life, on your terms" + "Get Started".
  Also checks `FirebaseAuth.getCurrentUser()` — if someone is already signed in, it skips
  the login flow and goes straight into the app ("remember me").
- **Files:** `WelcomeActivity.java`, `res/layout/activity_welcome.xml`.
- **Requirement:** counts as one of the 5 pages.
- **How to explain it:** "Welcome is the launcher. Firebase Auth remembers a signed-in user,
  so returning users skip login automatically via getCurrentUser()."

### Rebuilt screen: LoginActivity (TWO auth methods)
- **What it does:** real sign-in, replacing the old hardcoded password check.
  - **Google Sign-In (REQUIRED):** `GoogleSignInClient` + `requestIdToken(default_web_client_id)`,
    then `GoogleAuthProvider.getCredential(...)` -> `mAuth.signInWithCredential(...)`. Shows a
    "Welcome, <name>" toast using the Google display name — exactly Uri's class-7 pattern.
  - **Email/Password (BONUS method):** `mAuth.signInWithEmailAndPassword(...)`.
- **Analytics:** logs a `LOGIN` event with `METHOD` = "google" or "password".
- **Files:** `LoginActivity.java`, `res/layout/activity_login.xml`.
- **Requirement:** Google authentication (required, 30% Firebase) + sets up the bonus.
- **How to explain it:** "Both buttons end in a FirebaseAuth sign-in. Google uses an id-token
  credential; email/password uses signInWithEmailAndPassword. We log which method was used to Analytics."

### New screen: SignUpActivity (the bonus method)
- **What it does:** name + email + password -> `mAuth.createUserWithEmailAndPassword(...)`,
  saves the name as the Firebase display name, logs a `SIGN_UP` analytics event.
- **Files:** `SignUpActivity.java`, `res/layout/activity_signup.xml`.
- **Requirement:** the +10 bonus (a second authentication method beyond Google).
- **How to explain it:** "Email/password registration through Firebase Auth — our second
  auth method for the bonus. We also store the user's display name on their profile."

### Wiring
- `AndroidManifest.xml`: WelcomeActivity is the LAUNCHER; Login/SignUp/Chat are internal.
  Added the `INTERNET` permission (Firebase + Google Sign-In need network).
- New flow: **Welcome -> Login -> (Create one -> SignUp) -> Chat**. (Phase 2 inserts Onboarding before Chat.)

### Dependencies used (added in Phase 0)
- `play-services-auth` (Google Sign-In), `firebase-auth` (both methods),
  CircleImageView (welcome logo), Glide (ready for Phase 2 profile photo).

---

## Fixes during Phase 1 testing

### Firestore security rules (the "Couldn't connect" bug)
- **Problem:** the HW3 database used Firebase's default *test-mode* rules, which expire on a
  fixed date (`if request.time < timestamp.date(2026, 6, 17)`). That date passed, so Firestore
  denied all reads and the chat showed "Couldn't connect to the server."
- **Fix:** replaced the rules with an auth-based rule:
  `allow read, write: if request.auth != null;` — only signed-in users can access the database.
- **How to explain it:** "Our security rules require the user to be authenticated. The old test
  rules had expired, so we switched to a permanent rule tied to Firebase Auth — which also makes
  the database more secure."

### Back / Sign-out button in chat (HW2 "Back Button" navigation)
- **What it does:** a back arrow in the chat header signs the user out of **both** Firebase Auth
  and Google, then returns to the Sign In screen with a cleared back stack.
- **Files:** `res/drawable/ic_back.xml`, `activity_chat.xml` (header `btn_back`),
  `ChatActivity.java` (`signOutAndReturnToLogin()`).
- **How to explain it:** "The back button calls FirebaseAuth.signOut() and GoogleSignInClient.signOut()
  so the next login shows the account picker again, then sends the user back to Login."

### Back navigation on every screen (except Welcome)
- **Login** and **SignUp**: a top-left back arrow (`btn_back`) calls `finish()` to return to the
  previous screen (Login -> Welcome, SignUp -> Login).
- **Chat**: the back arrow signs out (Firebase + Google) and returns to Login.
- **Welcome** has no back button because it is the app's entry point (the launcher).
- **How to explain it:** "Every screen gives the user a way back, matching the navigation
  integrity we planned in HW2. The chat's back doubles as sign-out since it's the end of the flow."

---

## Phase 2 — Onboarding + Camera + user profile (the 5th page)

### New screen: OnboardingActivity
- **What it does:** after a user signs up (or signs in without a profile yet), they set up
  their profile here:
  1. **Profile photo via the device CAMERA** — the required *phone capability*. We use
     `ActivityResultContracts.TakePicturePreview()` to open the camera and get a thumbnail
     bitmap back, after requesting the `CAMERA` runtime permission. (No paid Firebase Storage,
     matching Uri's approach — the photo is stored as a small Base64 string in Firestore.)
  2. **Main goal** — the options are loaded **dynamically from the Firestore `goals`
     collection** (no static/hardcoded list), shown as radio buttons.
- On **Finish**, we write a `users/{uid}` document to Firestore with: name, email, goal,
  photo (Base64), authMethod (google/password), onboardingComplete=true, createdAt — then
  open the chat. Logs an `onboarding_complete` Analytics event.
- **Files:** `OnboardingActivity.java`, `activity_onboarding.xml`, `ic_camera.xml`.
- **Requirements covered:** the 5th page, the phone-capability (Camera), and more dynamic
  Firestore data (goals + user profile).
- **How to explain it:**
  - *Camera:* "Tapping the circle requests CAMERA permission, opens the camera with
    TakePicturePreview, and we store the photo as Base64 inside the user's Firestore doc."
  - *Goals:* "The goal list isn't hardcoded — it's read from the Firestore `goals` collection
    and turned into radio buttons at runtime."

### Routing: UserRouter
- **What it does:** after authentication, decides where to send the user:
  reads `users/{uid}` → if `onboardingComplete` is true go to Chat, otherwise go to Onboarding.
- **Used by:** WelcomeActivity (auto sign-in) and LoginActivity (after sign-in).
  SignUpActivity always goes to Onboarding (brand-new user).
- **Files:** `UserRouter.java`.
- **How to explain it:** "New users are sent through onboarding once; returning users who
  already have a profile skip straight to the chat. That decision lives in one place — UserRouter."

### Firestore collections now in use
- `bot_responses` (chat replies) · `goals` (onboarding goal options) · `users/{uid}` (profiles).

### Manifest
- Added `CAMERA` permission + `uses-feature camera (required=false)`, and registered OnboardingActivity.

---

## Update: camera now a 1:1 copy of Uri's contacts app

We changed the onboarding camera to match Uri's `AddContactActivity` exactly:
- Fields: `ActivityResultLauncher<Uri> takePictureLauncher`, `Uri CurrentImage`, `REQUEST_PERMISSIONS_CODE`.
- `ActivityResultContracts.TakePicture()` writes the photo into a MediaStore URI we create
  with `createImageUri()` (inserts into `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`).
- On success: `imgProfile.setImageURI(CurrentImage)` — same as Uri's `avatar.setImageURI(...)`.
- Permissions requested: `CAMERA` + `ACCESS_MEDIA_LOCATION` (the same two Uri requests).
- We store the photo as the **image URI string** in Firestore (`String.valueOf(CurrentImage)`),
  exactly like Uri stores `String.valueOf(CurrentImage)` on his Contact object. No paid Storage.
- **How to explain it:** "The camera is implemented the same way as the contacts app from class —
  TakePicture into a MediaStore URI, setImageURI to show it, and we save the URI string in Firestore."

---

## Chat polish

### Centered header
- The chat header is now a `RelativeLayout`: back button pinned far-left, Test Crash far-right,
  and the **logo + "WinWell" centered** in the middle (`layout_centerInParent`).

### User avatar on messages
- Each message the user sends now shows their onboarding **profile photo** as a small circle
  (`img_user_avatar`, a CircleImageView) next to the bubble.
- `ChatActivity.loadUserAvatar()` reads the `photo` URI from `users/{uid}` and passes it to
  `ChatAdapter.setUserPhotoUri(...)`; `UserMessageViewHolder` shows it with `setImageURI(...)`
  (same approach Uri uses to display a contact's photo).
- **How to explain it:** "We store the camera photo's URI in the user's Firestore profile, then
  load it into the avatar with setImageURI — the same way the contacts app shows pictures."

---

## Phase 3 — Chat upgrades + static-data sweep

### Suggested Activity card (Accept / Decline)
- After each bot reply, the chat proactively shows a **Suggested Activity** card (WinWell's
  signature feature), with the activity loaded from the Firestore **`suggested_activities`**
  collection (title + description).
- **Co-pilot mode:** the card shows **Accept / Decline** buttons. Accept logs an
  `activity_accepted` event + a confirmation; Decline logs `activity_declined`.
- **Auto-pilot mode:** the card is created already-accepted (no buttons) and logs
  `activity_accepted` automatically.
- **Files:** `Message.java` (suggestion fields + factory), `item_chat_suggestion.xml`,
  `bg_chat_suggestion.xml`, `bg_button_outline.xml`, `SuggestionViewHolder.java`,
  `ChatAdapter.java` (3rd view type + `SuggestionListener`), `ChatActivity.java`.
- **How to explain it:** "Activities come from Firestore. The card is a third RecyclerView view
  type. Co-pilot needs your approval (Accept/Decline); Auto-pilot accepts automatically — that's
  the AI Control Slider from our design."

### Co-pilot / Auto-pilot toggle (AI Control Slider)
- A `SwitchCompat` under the header switches modes; the active label is bolded.
- Controls whether suggestion cards need manual approval or are auto-accepted.

### Static-data sweep (no hardcoded content)
- The chat **greeting** and **fallback** text now load from Firestore **`config/chat`**
  (fields `greeting`, `fallback`), with safe defaults only if the doc is missing.
- All displayed content now lives in Firestore: `bot_responses`, `goals`,
  `suggested_activities`, `config/chat`, and `users/{uid}`. Remaining hardcoded strings are
  only UI labels, error messages, and short interactive confirmations — not data lists.

### Analytics events added
- `activity_accepted`, `activity_declined` (with `activity_name`).

---

## Update: Co-pilot/Auto-pilot toggle removed

We removed the mode toggle to keep the chat UI clean. Suggested Activity cards now always show
**Accept / Decline** (the Co-pilot behaviour). This does not affect any grading requirement — the
toggle was an optional extra. The Accept/Decline feature and its `activity_accepted` /
`activity_declined` Analytics events remain.

---

## Phase 4 — Analytics & Crashlytics sweep

### Analytics events (full user journey)
- `login` (LoginActivity) — param `method` = google / password
- `sign_up` (SignUpActivity) — param `method` = password
- `onboarding_complete` (OnboardingActivity)
- `message_sent` (ChatActivity) — param `message_length`
- `activity_accepted` / `activity_declined` (ChatActivity) — param `activity_name`
- (Firebase also auto-logs `screen_view`, `first_open`, `session_start`.)

### Crashlytics coverage (~25 calls)
- **Breadcrumbs on every screen open:** Welcome, Login, SignUp, Onboarding, Chat.
- **recordException on failures:** email/password login, Google sign-in (ApiException +
  credential failure), email/password sign-up, and every Firestore read failure
  (bot_responses, goals, suggested_activities, config, users).
- **setUserId** — crash reports are tagged with the signed-in user's uid (set in ChatActivity).
- **Test Crash button** — deliberate crash to prove live capture.
- **How to explain it:** "Every screen leaves a breadcrumb, every failure path records an
  exception, and crashes are tied to the user via setUserId — so a crash report shows the full
  trail of what the user did and who they were."

---

## Phase 5 — Code quality pass

- All comments rewritten as plain `//` notes in simple language (removed JavaDoc `*` blocks
  and decorative separators) so the code reads naturally.
- Removed development-history notes ("Phase N", "HW3", "for now") so comments describe the
  final behaviour, not how we got there.
- Removed unused string resources left over from the deleted Co-pilot/Auto-pilot toggle.
- Verified: every Java file's braces balance, no dead references, clean package layout
  (5 screens + UserRouter + ChatAdapter + 3 view holders + Message model).
