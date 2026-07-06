package com.winwell.app;

// Camera permissions — same ones Uri uses in his contacts app (AddContactActivity)
import static android.Manifest.permission.ACCESS_MEDIA_LOCATION;
import static android.Manifest.permission.CAMERA;

import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// Firebase Analytics — logs when onboarding is completed
import com.google.firebase.analytics.FirebaseAnalytics;
// Firebase Auth — identifies the current user (their uid is the document id)
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Firebase Crashlytics — breadcrumbs + error reporting
import com.google.firebase.crashlytics.FirebaseCrashlytics;
// Firebase Firestore — reads the goal options and writes the user profile
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

// CircleImageView (hdodenhof) — shows the profile photo as a circle
import de.hdodenhof.circleimageview.CircleImageView;

import java.util.HashMap;
import java.util.Map;

// OnboardingActivity — the 5th screen. A new user sets up their profile here:
//
//   1) PROFILE PHOTO via the device CAMERA — the required "phone capability".
//      This uses EXACTLY the same camera approach Uri taught in his contacts app
//      (AddContactActivity): we create a MediaStore image URI, launch the camera with
//      ActivityResultContracts.TakePicture(), show the result with setImageURI(), and
//      store the image URI string inside Firestore (no paid Firebase Storage).
//
//   2) MAIN GOAL — the options are loaded dynamically from the Firestore "goals"
//      collection (NO hardcoded/static data), shown as radio buttons.
//
// On "Finish Setup" we write a users/{uid} document to Firestore and open the chat.
public class OnboardingActivity extends AppCompatActivity {

    //  Camera fields (1:1 with Uri's AddContactActivity)
    private int REQUEST_PERMISSIONS_CODE = 1;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private Uri CurrentImage;   // URI of the photo we just took; its string is saved to Firestore

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseAnalytics mAnalytics;

    private CircleImageView imgProfile;
    private RadioGroup radioGoals;
    private TextView textGoalsLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        mAnalytics = FirebaseAnalytics.getInstance(this);
        FirebaseCrashlytics.getInstance().log("OnboardingActivity opened");

        // Connect views
        imgProfile      = findViewById(R.id.img_profile);
        radioGoals      = findViewById(R.id.radio_goals);
        textGoalsLoading = findViewById(R.id.text_goals_loading);
        Button btnTakePhoto = findViewById(R.id.btn_take_photo);
        Button btnFinish    = findViewById(R.id.btn_finish);
        ImageButton btnBack = findViewById(R.id.btn_back);

        //  Camera launcher (Uri's pattern)
        // TakePicture writes the photo into the URI we created, and returns true on success.
        // We then display that URI in the circle with setImageURI().
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), result -> {
            if (result) {
                imgProfile.setImageURI(CurrentImage);
                FirebaseCrashlytics.getInstance().log("Onboarding: profile photo captured");
            }
        });

        // Tapping the circle OR the button opens the camera (checks permissions first,
        // exactly like Uri's avatar.setOnClickListener in AddContactActivity).
        View.OnClickListener openCamera = view -> {
            if (checkSelfPermission(CAMERA) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{CAMERA, ACCESS_MEDIA_LOCATION}, REQUEST_PERMISSIONS_CODE);
            } else {
                captureImage();
            }
        };
        imgProfile.setOnClickListener(openCamera);
        btnTakePhoto.setOnClickListener(openCamera);

        // Load goal options dynamically from Firestore
        loadGoalsFromFirestore();

        // Finish → validate + save the user profile to Firestore
        btnFinish.setOnClickListener(v -> saveProfileAndContinue());

        // Back → sign out and return to Login (onboarding is post-auth, like chat)
        btnBack.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(OnboardingActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    // Called after the user answers the permission dialog. If the camera is allowed,
    // we take the picture. (Uri's app re-taps to capture; here we capture right away.)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            boolean cameraGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if (CAMERA.equals(permissions[i]) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    cameraGranted = true;
                }
            }
            if (cameraGranted) {
                captureImage();
            } else {
                Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show();
            }
        }
    }

    // Creates a MediaStore image URI and launches the camera into it (Uri's captureImage).
    private void captureImage() {
        Uri imageUri = createImageUri();
        if (imageUri != null) {
            CurrentImage = imageUri;
            takePictureLauncher.launch(imageUri);
        }
    }

    // Inserts a new image entry into the device gallery and returns its URI (Uri's createImageUri).
    private Uri createImageUri() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "WinWell Profile Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    // Loads the goal options from the Firestore "goals" collection and turns each one
    // into a RadioButton. This keeps the goals as DYNAMIC data in the cloud (no static list).
    private void loadGoalsFromFirestore() {
        db.collection("goals")
                .get()
                .addOnSuccessListener(query -> {
                    radioGoals.removeAllViews();
                    for (QueryDocumentSnapshot doc : query) {
                        String title = doc.getString("title");
                        if (title != null) {
                            RadioButton rb = new RadioButton(this);
                            rb.setText(title);
                            rb.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                            rb.setTextSize(16);
                            rb.setPadding(8, 16, 8, 16);
                            radioGoals.addView(rb);
                        }
                    }
                    textGoalsLoading.setVisibility(View.GONE);
                    FirebaseCrashlytics.getInstance().log("Onboarding: loaded " + radioGoals.getChildCount() + " goals");
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    textGoalsLoading.setText("Couldn't load goals. Check your connection.");
                });
    }

    // Validates the form, writes users/{uid} to Firestore, then opens the chat.
    private void saveProfileAndContinue() {
        // A goal must be selected
        int checkedId = radioGoals.getCheckedRadioButtonId();
        if (checkedId == -1) {
            Toast.makeText(this, R.string.onboarding_no_goal, Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton checked = findViewById(checkedId);
        String goal = checked.getText().toString();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show();
            return;
        }

        // Work out which sign-in method this account used (google vs password)
        String authMethod = "password";
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if ("google.com".equals(info.getProviderId())) {
                authMethod = "google";
            }
        }

        // Build the user profile document.
        // photo = the camera image URI as a string (exactly like Uri stores it for contacts).
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", user.getUid());
        profile.put("name", user.getDisplayName() != null ? user.getDisplayName() : "");
        profile.put("email", user.getEmail() != null ? user.getEmail() : "");
        profile.put("goal", goal);
        profile.put("photo", CurrentImage != null ? String.valueOf(CurrentImage) : "");
        profile.put("authMethod", authMethod);
        profile.put("onboardingComplete", true);
        profile.put("createdAt", System.currentTimeMillis());

        // Save it under users/{uid} (the user's id is the document id)
        db.collection("users").document(user.getUid())
                .set(profile)
                .addOnSuccessListener(unused -> {
                    mAnalytics.logEvent("onboarding_complete", new Bundle());
                    Toast.makeText(this, R.string.onboarding_saved, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(OnboardingActivity.this, ChatActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    Toast.makeText(this, "Couldn't save your profile. Try again.", Toast.LENGTH_LONG).show();
                });
    }
}
