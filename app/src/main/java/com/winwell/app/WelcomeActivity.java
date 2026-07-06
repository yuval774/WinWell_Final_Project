package com.winwell.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// Firebase Authentication — we use it here to check if the user is ALREADY signed in
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

// WelcomeActivity — the first screen of WinWell and the app's launcher.
//
// What it does:
//   1. If the user is already signed in (from a previous session), we skip the whole
//      login flow and jump straight into the app. This is the standard "remember me"
//      behaviour Firebase Auth gives us for free via getCurrentUser().
//   2. Otherwise we show the Welcome screen with a single "Get Started" button that
//      opens the Login screen.
//
// This matches the "Welcome" page from our HW2 design.
public class WelcomeActivity extends AppCompatActivity {

    // Firebase Authentication entry point — lets us ask "is anyone currently signed in?"
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Grab the shared FirebaseAuth instance for this app
        mAuth = FirebaseAuth.getInstance();
        FirebaseCrashlytics.getInstance().log("WelcomeActivity opened");

        //  "Remember me" check
        // If a user is already signed in, getCurrentUser() returns their account
        // (not null). In that case we don't show Welcome at all — we go straight in.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Already signed in → let UserRouter send them to onboarding or chat
            UserRouter.routeAfterAuth(this);
            return; // stop here so we never build the Welcome UI
        }

        //  No one signed in → show the Welcome screen
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_welcome);

        // Keep content clear of the status / navigation bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // "Get Started" → open the Login screen
        Button getStarted = findViewById(R.id.btn_get_started);
        getStarted.setOnClickListener(v ->
                startActivity(new Intent(WelcomeActivity.this, LoginActivity.class)));
    }

}
