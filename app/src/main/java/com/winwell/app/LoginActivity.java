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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// Firebase Analytics — logs a LOGIN event whenever a user signs in
import com.google.firebase.analytics.FirebaseAnalytics;

// Firebase Authentication — verifies the user's identity (email/password AND Google)
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

// Google Sign-In (Google Play Services) — provides the "Login with Google" flow.
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

// LoginActivity — lets an existing user sign in to WinWell.
//
// It supports TWO authentication methods (both backed by Firebase Auth):
//   1. Google Sign-In  → the REQUIRED method for the final project.
//   2. Email / Password → our SECOND method, worth the +10 bonus
//                         (accounts are created on the SignUp screen).
//
// On a successful sign-in (either method) we:
//   • log a Firebase Analytics LOGIN event (with the method used), and
//   • open the app (onboarding for a new user, or straight to chat for a returning one).
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // Firebase Analytics — records the LOGIN event
    private FirebaseAnalytics mFirebaseAnalytics;

    // Firebase Auth — the single source of truth for "who is signed in"
    private FirebaseAuth mAuth;

    // Google Sign-In client — knows how to launch the Google account picker
    private GoogleSignInClient mGoogleSignInClient;

    // Modern way to get a result back from the Google sign-in screen
    // (replaces the old onActivityResult). It hands us the chosen Google account.
    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // The user came back from the Google account picker — read the account
                Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    // We have the Google account — now exchange it for a Firebase sign-in
                    firebaseAuthWithGoogle(account);
                } catch (ApiException e) {
                    // User cancelled or something went wrong with Google
                    Log.w(TAG, "Google sign in failed", e);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // Keep content clear of system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        //  Initialize Firebase
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        mAuth = FirebaseAuth.getInstance();
        FirebaseCrashlytics.getInstance().log("LoginActivity opened");

        //  Configure Google Sign-In
        // requestIdToken(...) asks Google for a token that Firebase can verify.
        // default_web_client_id is generated automatically from google-services.json
        // (it comes from the OAuth Web client inside google-services.json).
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        //  Connect views
        final EditText emailField    = findViewById(R.id.username);
        final EditText passwordField = findViewById(R.id.password);
        final Button   loginButton   = findViewById(R.id.login);
        final Button   googleButton  = findViewById(R.id.btn_google_sign_in);
        final TextView createAccount = findViewById(R.id.text_create_account);

        //  Email / password sign-in (bonus method)
        loginButton.setOnClickListener(v -> {
            String email    = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            // Basic validation so we don't send empty values to Firebase
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            // Ask Firebase to verify these credentials against the accounts we created
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, t -> {
                        if (t.isSuccessful()) {
                            logLoginEvent("password");          // Analytics
                            onAuthSuccess();                    // enter the app
                        } else {
                            // Wrong password, no such user, etc.
                            if (t.getException() != null) {
                                FirebaseCrashlytics.getInstance().recordException(t.getException());
                            }
                            Toast.makeText(this, R.string.login_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        //  Google sign-in (required method)
        // Tapping the button launches Google's account picker. The result comes
        // back to googleSignInLauncher above.
        googleButton.setOnClickListener(v ->
                googleSignInLauncher.launch(mGoogleSignInClient.getSignInIntent()));

        //  Go to the Create Account screen
        createAccount.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, SignUpActivity.class)));

        //  Back button → return to the Welcome screen
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    // Takes the Google account the user picked and signs into Firebase with it.
    // Firebase verifies the Google token and creates/loads a matching Firebase user.
    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Welcome toast with the Google display name
                        String name = account.getDisplayName() != null ? account.getDisplayName() : "";
                        Toast.makeText(this, getString(R.string.welcome_user, name), Toast.LENGTH_SHORT).show();
                        logLoginEvent("google");   // Analytics
                        onAuthSuccess();           // enter the app
                    } else {
                        Log.w(TAG, "signInWithCredential failed", task.getException());
                        if (task.getException() != null) {
                            FirebaseCrashlytics.getInstance().recordException(task.getException());
                        }
                        Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Logs a Firebase Analytics LOGIN event, recording which method was used.
    private void logLoginEvent(String method) {
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.METHOD, method);
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle);
    }

    // Runs after a successful sign-in (either method).
    private void onAuthSuccess() {
        // After signing in, UserRouter checks Firestore and sends the user to
        // onboarding (new user) or straight to the chat (returning user).
        UserRouter.routeAfterAuth(this);
    }
}
