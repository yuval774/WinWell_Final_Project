package com.winwell.app;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;

// UserRouter — decides where a signed-in user should land:
//   • If they have a Firestore profile with onboardingComplete = true  → ChatActivity.
//   • Otherwise (new user, no profile yet)                              → OnboardingActivity.
//
// This keeps the "have you finished onboarding?" logic in ONE place, used by both the
// Welcome screen (auto sign-in) and the Login screen (after signing in).
public final class UserRouter {

    private UserRouter() { } // utility class — no instances

    // Reads the user's Firestore profile and starts the correct next screen.
    public static void routeAfterAuth(AppCompatActivity activity) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // No signed-in user — shouldn't happen here, but be safe and stay put.
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    boolean done = doc.exists() && Boolean.TRUE.equals(doc.getBoolean("onboardingComplete"));
                    go(activity, done ? ChatActivity.class : OnboardingActivity.class);
                })
                .addOnFailureListener(e -> {
                    // If the read fails (e.g. no network), don't trap the user on a blank
                    // screen — send them to the chat and log the error to Crashlytics.
                    FirebaseCrashlytics.getInstance().recordException(e);
                    go(activity, ChatActivity.class);
                });
    }

    // Starts the target screen and clears the back stack so Back can't return to auth.
    private static void go(AppCompatActivity activity, Class<?> target) {
        Intent intent = new Intent(activity, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
