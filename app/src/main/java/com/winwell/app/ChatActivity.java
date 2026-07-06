package com.winwell.app;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Lottie — 3rd party animation library by Airbnb (bonus points)
// Used to show a smooth loading animation while bot responses are fetched from Firestore
import com.airbnb.lottie.LottieAnimationView;

// Firebase Analytics — tracks user actions (login event, message_sent event)
import com.google.firebase.analytics.FirebaseAnalytics;

// Firebase Crashlytics — captures crashes and logs key moments in the app flow
import com.google.firebase.crashlytics.FirebaseCrashlytics;

// Firebase Firestore — the cloud database where our bot responses are stored
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

// Firebase Auth + Google Sign-In — used by the header back button to sign the user out
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// ChatActivity — the main chat screen of WinWell.
//
// Firebase features used on this screen:
//   1. Firebase Analytics    → logs a "message_sent" event every time the user sends a message
//   2. Firebase Crashlytics  → logs key moments as breadcrumbs; "Test Crash" button triggers a crash on purpose
//   3. Firebase Firestore    → bot responses are loaded from the cloud "bot_responses" collection
//                              instead of being hardcoded in the app
//
// Bonus (3rd-party library):
//   • Airbnb Lottie → shows a smooth JSON-based loading animation while Firestore is fetching data
public class ChatActivity extends AppCompatActivity {

    //  UI elements
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private EditText editMessage;
    private ImageButton btnSend;
    private Button btnTestCrash;

    // Lottie view — the animated loading spinner shown while Firestore fetches data
    private LottieAnimationView lottieLoading;

    //  Firebase
    // Analytics: tracks what users do inside the app (events)
    private FirebaseAnalytics mFirebaseAnalytics;

    // Firestore: our cloud NoSQL database — stores bot responses dynamically
    private FirebaseFirestore db;

    //  Bot responses
    // This list starts EMPTY and is filled from Firestore when the app opens.
    // Uri: this is how the app fetches dynamic data from the cloud instead of using hardcoded strings.
    private List<String> botAnswers = new ArrayList<>();

    // Suggested activities loaded from Firestore
    // Suggested activities loaded from Firestore; each entry = {title, description}.
    private List<String[]> suggestedActivities = new ArrayList<>();
    // Bot greeting fallback text — overwritten from Firestore config/chat if present.
    private String botFallback = "I'm here for you! Let me know how you're feeling today.";

    // Accept / decline replies, also loaded from Firestore config (safe defaults below).
    // "{activity}" is replaced with the chosen activity's title at runtime.
    private String acceptReply = "Great choice! I've added \"{activity}\" to your day.";
    private String declineReply = "No worries, I'll find a better moment for that.";

    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge so content fills the full screen behind system bars
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);

        // Push content below the status bar / above the navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //  Connect XML views to Java variables
        recyclerView  = findViewById(R.id.recycler_chat);
        editMessage   = findViewById(R.id.edit_message);
        btnSend       = findViewById(R.id.btn_send);
        btnTestCrash  = findViewById(R.id.btn_test_crash);
        lottieLoading = findViewById(R.id.lottie_loading);

        //  Initialize Firebase services
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        db = FirebaseFirestore.getInstance();

        // Leave a Crashlytics breadcrumb so we can trace that this screen was reached
        FirebaseCrashlytics.getInstance().log("ChatActivity opened");

        //  Set up the RecyclerView
        chatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(chatAdapter);

        // Load the user's onboarding photo to show as the avatar on their messages
        loadUserAvatar();

        // Suggested activities + Accept/Decline
        // Load the wellness activities used by the "Suggested Activity" cards
        loadSuggestedActivitiesFromFirestore();

        // React when the user accepts/declines a suggestion
        chatAdapter.setSuggestionListener(new ChatAdapter.SuggestionListener() {
            @Override public void onAccept(Message m)  { onActivityAccepted(m); }
            @Override public void onDecline(Message m) { onActivityDeclined(m); }
        });

        // Load the greeting + fallback text from Firestore (config/chat) and show the greeting
        loadChatConfig();

        //  Start the Lottie loading animation
        // The animation is already visible (set in XML), but we play it explicitly here.
        // The send button is disabled until Firestore finishes loading.
        // Uri: this is the Lottie 3rd-party library in action — it plays a JSON animation.
        lottieLoading.setVisibility(View.VISIBLE);
        lottieLoading.playAnimation();
        btnSend.setEnabled(false); // user can't send until bot responses are ready

        //  Load bot responses from Firebase Firestore
        fetchBotResponsesFromFirestore();

        //  Send button
        btnSend.setOnClickListener(v -> {
            String content = editMessage.getText().toString().trim();
            // Only send if the user actually typed something
            if (!content.isEmpty()) {
                SendMessage(content);
            }
        });

        //  Back / Sign-out button
        // Signs the user out of BOTH Firebase Auth and Google, then returns to the
        // Sign In screen. We sign out of Google too so the account picker shows again
        // next time instead of silently re-using the last account.
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> signOutAndReturnToLogin());

        //  Test Crash button
        // Pressing this button intentionally crashes the app.
        // Firebase Crashlytics will catch it and report it to the Firebase console.
        // Uri: this button is here to prove Crashlytics is active and capturing real crashes.
        btnTestCrash.setOnClickListener(v -> {
            FirebaseCrashlytics.getInstance().log("Test crash triggered by user — Crashlytics is working");
            throw new RuntimeException("Test Crash — Firebase Crashlytics confirmed working!");
        });
    }

    // Loads the current user's profile photo from Firestore (users/{uid}) and gives it to
    // the chat adapter, so it appears as the small avatar next to each message they send.
    // The photo is stored as an image URI string (the one captured by the onboarding camera).
    private void loadUserAvatar() {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        // Tag any future crash reports with this user's id so we can trace who hit a bug
        FirebaseCrashlytics.getInstance().setUserId(user.getUid());
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String photo = doc.getString("photo");
                    if (photo != null && !photo.isEmpty()) {
                        chatAdapter.setUserPhotoUri(photo);
                    }
                });
    }

    // Signs the user out and sends them back to the Sign In screen.
    // Called by the header back button.
    private void signOutAndReturnToLogin() {
        FirebaseCrashlytics.getInstance().log("User signed out from ChatActivity");

        // 1) Sign out of Firebase Auth (clears the current user)
        FirebaseAuth.getInstance().signOut();

        // 2) Sign out of Google so the account chooser appears on the next login
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        GoogleSignInClient googleClient = GoogleSignIn.getClient(this, gso);
        googleClient.signOut();

        // 3) Go to the Login screen and clear the back stack so Back can't return to chat
        Intent intent = new Intent(ChatActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Fetches all bot response documents from the Firestore "bot_responses" collection.
    //
    // Uri: this is the Firebase Firestore integration.
    //   - The app queries the cloud database at startup.
    //   - Each document has a "text" field containing one bot response.
    //   - When data arrives, the Lottie animation is hidden and the chat becomes active.
    //   - If Firestore fails (no internet, etc.), the error is logged to Crashlytics
    //     and the app falls back gracefully.
    private void fetchBotResponsesFromFirestore() {
        db.collection("bot_responses")   // the collection we created in the Firebase console
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    // Loop through every document and collect its "text" field
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String text = doc.getString("text");
                        if (text != null && !text.isEmpty()) {
                            botAnswers.add(text);
                        }
                    }

                    // Tell Crashlytics how many responses we loaded — useful for debugging
                    FirebaseCrashlytics.getInstance().log(
                            "Firestore fetch complete — loaded " + botAnswers.size() + " bot responses"
                    );

                    //  Hide the Lottie animation now that data is ready
                    // Uri: the loading animation disappears exactly when the cloud data arrives.
                    lottieLoading.cancelAnimation();
                    lottieLoading.setVisibility(View.GONE);

                    // Unlock the send button so the user can start chatting
                    btnSend.setEnabled(true);
                })

                .addOnFailureListener(e -> {
                    // Log the exception to Firebase Crashlytics for debugging
                    FirebaseCrashlytics.getInstance().recordException(e);

                    // Still hide the animation so the app doesn't look frozen
                    lottieLoading.cancelAnimation();
                    lottieLoading.setVisibility(View.GONE);
                    btnSend.setEnabled(true);

                    // Let the user know something went wrong
                    AddBotMessage("⚠️ Couldn't connect to the server. Please check your internet and restart the app.");
                });
    }

    // Handles a user message:
    //   1. Adds the user's bubble to the chat
    //   2. Logs a Firebase Analytics "message_sent" event
    //   3. After a 1-second delay (to simulate the bot "thinking"), picks a random
    //      bot response from the Firestore data and adds it to the chat
    private void SendMessage(String content) {

        // Add the user's message bubble to the chat list and scroll to it
        chatAdapter.AddMessage(new Message(content, true));
        recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);

        // Clear the input box so the user can type their next message
        editMessage.setText("");

        //  Firebase Analytics: log the "message_sent" event
        // Uri: this proves Analytics is recording user actions inside the app.
        Bundle analyticsBundle = new Bundle();
        analyticsBundle.putInt("message_length", content.length()); // extra data attached to the event
        mFirebaseAnalytics.logEvent("message_sent", analyticsBundle);

        // Leave a Crashlytics breadcrumb so if a crash happens during/after sending,
        // we can see in the report that the user had just sent a message
        FirebaseCrashlytics.getInstance().log("User sent a message, length: " + content.length());

        //  Simulate bot typing delay then reply
        new Handler().postDelayed(() -> {

            // Pick a random bot response from the Firestore list
            // If for some reason the list is empty, fall back to a safe default message
            String randomAnswer;
            if (!botAnswers.isEmpty()) {
                randomAnswer = botAnswers.get(new Random().nextInt(botAnswers.size()));
            } else {
                randomAnswer = botFallback;
            }

            AddBotMessage(randomAnswer);

            // After replying, proactively suggest a wellness activity (WinWell's core idea)
            maybeSuggestActivity();

        }, 1000); // 1 second delay — makes the bot feel more natural and human
    }

    // Loads the chat greeting + fallback text from Firestore (config/chat) so they are not
    // hardcoded. Falls back to a safe default if the document is missing.
    private void loadChatConfig() {
        db.collection("config").document("chat").get()
                .addOnSuccessListener(doc -> {
                    String greeting = doc.getString("greeting");
                    String fallback = doc.getString("fallback");
                    if (fallback != null && !fallback.isEmpty()) botFallback = fallback;
                    String ar = doc.getString("accept_reply");
                    String dr = doc.getString("decline_reply");
                    if (ar != null && !ar.isEmpty()) acceptReply = ar;
                    if (dr != null && !dr.isEmpty()) declineReply = dr;
                    AddBotMessage((greeting != null && !greeting.isEmpty())
                            ? greeting : "Welcome to WinWell! How can I help you today?");
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    AddBotMessage("Welcome to WinWell! How can I help you today?");
                });
    }

    // Loads the wellness activities (title + description) from the Firestore collection.
    private void loadSuggestedActivitiesFromFirestore() {
        db.collection("suggested_activities").get()
                .addOnSuccessListener(query -> {
                    for (QueryDocumentSnapshot d : query) {
                        String title = d.getString("title");
                        String desc  = d.getString("description");
                        if (title != null) {
                            suggestedActivities.add(new String[]{ title, desc != null ? desc : "" });
                        }
                    }
                    FirebaseCrashlytics.getInstance().log(
                            "Loaded " + suggestedActivities.size() + " suggested activities");
                })
                .addOnFailureListener(e -> FirebaseCrashlytics.getInstance().recordException(e));
    }

    // Picks a random activity and adds a Suggested Activity card to the chat.
    private void maybeSuggestActivity() {
        if (suggestedActivities.isEmpty()) return;
        String[] act = suggestedActivities.get(new Random().nextInt(suggestedActivities.size()));
        // Created as pending so the user can Accept or Decline it.
        Message suggestion = Message.suggestion(act[0], act[1], false);
        chatAdapter.AddMessage(suggestion);
        recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    // Called when the user taps Accept on a suggestion card (Co-pilot mode).
    private void onActivityAccepted(Message m) {
        logActivityEvent("activity_accepted", m.ActivityTitle);
        FirebaseCrashlytics.getInstance().log("Activity accepted: " + m.ActivityTitle);
        AddBotMessage(acceptReply.replace("{activity}", m.ActivityTitle));
    }

    // Called when the user taps Decline on a suggestion card.
    private void onActivityDeclined(Message m) {
        logActivityEvent("activity_declined", m.ActivityTitle);
        FirebaseCrashlytics.getInstance().log("Activity declined: " + m.ActivityTitle);
        AddBotMessage(declineReply);
    }

    // Logs an Analytics event for an activity (accepted/declined) with the activity name.
    private void logActivityEvent(String event, String title) {
        Bundle b = new Bundle();
        b.putString("activity_name", title);
        mFirebaseAnalytics.logEvent(event, b);
    }

    // Adds a bot message bubble to the chat and scrolls the list down
    // so the new message is always visible.
    private void AddBotMessage(String content) {
        chatAdapter.AddMessage(new Message(content, false));
        recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
    }
}
