package edu.northeastern.weplan;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


public class MainActivity extends AppCompatActivity {
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 123;

    private static final int RC_SIGN_IN = 9001;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private TextView mStatusTextView;

    private FirebaseUser currentUser;

    private User userData;

    private List<Group> groups = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DataRepository dataRepository = DataRepository.getInstance();

        Log.d("MainActivity", "DataReposistory Groups size: " + dataRepository.getGroups().size());

        resetDataRepository();

        mStatusTextView = findViewById(R.id.status_text_view);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        mAuth = FirebaseAuth.getInstance();

        findViewById(R.id.sign_in_button).setOnClickListener(view -> signIn());

        Button signOutButton = findViewById(R.id.sign_out_button);
        signOutButton.setOnClickListener(view -> signOut());

        FirebaseMessaging.getInstance().subscribeToTopic("testTopic")
                .addOnCompleteListener(task -> {
                    Log.d("FCM", String.valueOf(task.isSuccessful()));
                    if (task.isSuccessful()) {
                        Log.d("FCM", "Subscription to topic Successful");
                    } else {
                        Exception exception = task.getException();
                        Log.d("FCM", "Subscription to topic failed", exception);
                    }
                });

        if (checkNotificationPermission()) {
            Log.d("FCM", "Notification permission granted");
        } else {
            Log.d("FCM", "Notification permission not granted");
            requestNotificationPermission();
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            addUserToFirestore(currentUser);
        }
    }

    private void resetDataRepository() {
        Log.d("MainActivity", "resetDataRepository");
//        DataRepository.getInstance().resetData();
    }


    private boolean checkNotificationPermission() {
        if (DataRepository.getInstance().getContext() == null) {
            DataRepository.getInstance().setContext(this);
        }
        return DataRepository.getInstance().checkNotificationPermission();
    }

    private void requestNotificationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "permission for notifications granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
        Log.d("MainActivity", "signIn");
    }

    private void signOut() {
        Log.d("MainActivity", "signOut");
        resetDataRepository();
        mAuth.signOut();

        findViewById(R.id.homeButton).setVisibility(View.GONE);

        Log.d("MainActivity", "signOut");


        mGoogleSignInClient.signOut().addOnCompleteListener(this,
                task -> updateUI(null));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
                Log.d("MainActivity", "onActivityResult");
            } catch (ApiException e) {
                updateUI(null);
                Log.d("MainActivity", "onActivityResult:failed", e);

            }
        }
    }


    FirebaseFirestore db = FirebaseFirestore.getInstance();
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        addUserToFirestore(user);
//                        updateUI(user);   // Moved this line to the end of addUserToFirestore
                        Log.d("MainActivity", "signInWithCredential:success");
                    } else {
                        Log.d("MainActivity", "signInWithCredential:failure", task.getException());
                        updateUI(null);
                    }
                });
    }

    private void addUserToFirestore(FirebaseUser firebaseUser) {
        resetDataRepository();
        DocumentReference docRef = db.collection("users").document(firebaseUser.getUid());
        docRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("email", firebaseUser.getEmail());
                userMap.put("name", firebaseUser.getDisplayName());
                docRef.set(userMap);
                userData = new User(firebaseUser.getUid(), firebaseUser.getEmail(), firebaseUser.getDisplayName(), new ArrayList<>());
                userData.setGroups(new ArrayList<>());
                updateUI(firebaseUser); // Update UI here as no groups to fetch
            } else {
                String name = documentSnapshot.getString("name");
                String email = documentSnapshot.getString("email");

                List<DocumentReference> groupRefs = (List<DocumentReference>) documentSnapshot.get("groups");
                if (groupRefs != null && !groupRefs.isEmpty()) {
                    AtomicInteger remainingGroups = new AtomicInteger(groupRefs.size());

                    for (DocumentReference groupRef : groupRefs) {
                        groupRef.get().addOnSuccessListener(groupSnapshot -> {
                            if (groupSnapshot.exists()) {
                                Group group = groupSnapshot.toObject(Group.class);
                                group.setId(groupSnapshot.getId());
                                groups.add(group);
                            }
                            if (remainingGroups.decrementAndGet() == 0) {
                                groups.sort((o1, o2) -> o2.getTimestamp() - o1.getTimestamp());
                                userData = new User(firebaseUser.getUid(), email, name, groups);
                                updateUI(firebaseUser); // Update UI after all groups are fetched
                            }
                        }).addOnFailureListener(e -> {
                            if (remainingGroups.decrementAndGet() == 0) {
                                userData = new User(firebaseUser.getUid(), email, name, groups);
                                updateUI(firebaseUser); // Update UI in case of failure too
                            }
                        });
                    }
                } else {
                    userData = new User(firebaseUser.getUid(), email, name, new ArrayList<>());
                    updateUI(firebaseUser); // Update UI here as there are no groups
                }
            }
        });
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            currentUser = user;
            AppFirebaseMessagingService.subscribeToTopic(currentUser.getUid());
            mStatusTextView.setText(getString(R.string.google_status_fmt, user.getEmail()));
            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            findViewById(R.id.sign_out_button).setVisibility(View.VISIBLE);
            findViewById(R.id.homeButton).setVisibility(View.VISIBLE);
            Log.d("MainActivity", "updateUI:signed_in:" + user.getEmail());

            DataRepository.getInstance().getGroups().clear();

            DataRepository.getInstance().setUserData(userData);
            DataRepository.getInstance().setGroups(groups);

            displayTaskActivity(user);

        } else {
            mStatusTextView.setText(R.string.signed_out);
            findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            findViewById(R.id.sign_out_button).setVisibility(View.GONE);
            Log.d("MainActivity", "updateUI:signed_out");
        }
    }

    private void displayTaskActivity(FirebaseUser user) {
        Intent intent = new Intent(MainActivity.this, TaskActivity.class);
        intent.putExtra("USER_ID", user.getUid());
        intent.putExtra("USER_EMAIL", currentUser.getEmail());

        Bundle bundle = new Bundle();
        bundle.putParcelable("USER_DATA", userData);
        bundle.putParcelableArrayList("USER_GROUPS", (ArrayList<? extends Parcelable>) userData.getGroups());

        intent.putExtra("USER_BUNDLE", bundle);

        Log.e("MainActivity", "updateUI:signed_in:" + user.getUid());
        startActivity(intent);
    }

    public void onHomeButtonClick (View view) {
        this.displayTaskActivity(this.currentUser);
    }


}
