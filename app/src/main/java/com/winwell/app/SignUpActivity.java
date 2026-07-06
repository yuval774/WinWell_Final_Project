package com.winwell.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// Firebase Analytics — logs a SIGN_UP event when a new account is created
import com.google.firebase.analytics.FirebaseAnalytics;

// Firebase Authentication — creates the new email/password account
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

// SignUpActivity — the "Create Account" screen.
//
// This implements our SECOND authentication method: email + password registration via
// Firebase Auth. Having a second method (on top of Google) is what earns the +10 bonus.
//
// Flow:
//   1. User types name, email, password.
//   2. We create the account with createUserWithEmailAndPassword().
//   3. We save the typed name onto the Firebase user's profile (display name).
//   4. We log a SIGN_UP analytics event and enter the app.
public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";

    private FirebaseAuth mAuth;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        FirebaseCrashlytics.getInstance().log("SignUpActivity opened");

        final EditText nameField     = findViewById(R.id.signup_name);
        final EditText emailField    = findViewById(R.id.signup_email);
        final EditText passwordField = findViewById(R.id.signup_password);
        final Button   createButton  = findViewById(R.id.btn_create_account);
        final TextView haveAccount   = findViewById(R.id.text_have_account);

        //  Create Account button
        createButton.setOnClickListener(v -> {
            String name     = nameField.getText().toString().trim();
            String email    = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            // Validate input before talking to Firebase
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                // Firebase requires passwords of at least 6 characters
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            // Ask Firebase to create the new account
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            saveDisplayName(name);   // store the user's name on their profile
                            logSignUpEvent();        // Analytics
                            Toast.makeText(this, getString(R.string.welcome_user, name), Toast.LENGTH_SHORT).show();
                            onAuthSuccess();         // enter the app
                        } else {
                            // e.g. email already in use, invalid email, weak password
                            String msg = task.getException() != null
                                    ? task.getException().getMessage()
                                    : getString(R.string.signup_failed);
                            if (task.getException() != null) {
                                FirebaseCrashlytics.getInstance().recordException(task.getException());
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                            Log.w(TAG, "createUser failed", task.getException());
                        }
                    });
        });

        //  Back to the Sign In screen
        haveAccount.setOnClickListener(v -> finish()); // just close → returns to Login

        //  Back button → return to the Sign In screen
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    // Saves the user's typed name as their Firebase display name.
    private void saveDisplayName(String name) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            UserProfileChangeRequest update = new UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build();
            user.updateProfile(update);
        }
    }

    // Logs a Firebase Analytics SIGN_UP event with the method used.
    private void logSignUpEvent() {
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.METHOD, "password");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, bundle);
    }

    // Runs after a new account is created. A new user always goes to onboarding next.
    private void onAuthSuccess() {
        // A brand-new account always goes to Onboarding to set goal + profile photo.
        Intent intent = new Intent(SignUpActivity.this, OnboardingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
