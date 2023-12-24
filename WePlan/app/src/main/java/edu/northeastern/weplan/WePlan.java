package edu.northeastern.weplan;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class WePlan extends Application {

    private boolean isListenerAttached = false;

    private FirebaseFirestore db;
    private ListenerRegistration listenerRegistration;
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        db = FirebaseFirestore.getInstance();

//        if (!isListenerAttached || listenerRegistration == null) {
//            setupFirebaseFireStoreDbListener();
//        }
    }

    private void createNotificationChannel() {
        CharSequence name = "TaskReminderChannel";
        String description = "Channel for Reminding Task Due Notifications";
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(DataRepository.NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        DataRepository.getInstance().setContext(this);
        Log.d("WePlan", "createNotificationChannel: Notification channel created");
    }


    /**
     * NOT USING ANY OF THE BELOW METHODS
     */

    private void deleteAllTasksWithFrequencyId() {
        Log.d("WePlan", "deleteAllTasksWithFrequencyId: Deleting all tasks with frequency id");
        String frequencyId = "1804808804";

        db.collection("tasks")
                .whereEqualTo("frequency.id", frequencyId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Log.d("WePlan", "deleteAllTasksWithFrequencyId: " + document.getId() + " => " + document.getData());
                        document.getReference().delete();
                    }
                });
    }

    private void setupFirebaseFireStoreDbListener() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("tasks")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot snapshots,
                                        @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.w("FirestoreListener", "Listen failed.", e);
                            return;
                        }

                        isListenerAttached = true;

                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            switch (dc.getType()) {
                                case ADDED:
                                    Log.d("FirestoreListener", "New task: " + dc.getDocument().getData());
                                    handleTaskChangeEvent(dc.getDocument());
                                    break;
                                case MODIFIED:
                                    Log.d("FirestoreListener", "Modified task: " + dc.getDocument().getData());
                                    handleTaskChangeEvent(dc.getDocument());
                                    break;
                                case REMOVED:
                                    Log.d("FirestoreListener", "Removed task: " + dc.getDocument().getData());
                                    // Handle the removed document
                                    String taskId = dc.getDocument().getId();

                                    AppFirebaseMessagingService.unsubscribeFromTopic(taskId);

                                    break;
                            }
                        }
                    }
                });
    }

    private void detachFirebaseFireStoreDbListener() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
            isListenerAttached = false;
        }
    }


    private boolean isPartOfGroup(String groupId) {
        DataRepository dataRepository = DataRepository.getInstance();

        Log.d("WePlan", "Group Szie: " + dataRepository.getGroups().size());

        for (Group group : dataRepository.getGroups()) {
            if (group.getId().equals(groupId)) {
                return true;
            }
        }

        Log.d("WePlan", "isPartOfGroup: Not part of group, GroupId: " + groupId);

        return false;
    }


    private void handleTaskChangeEvent(QueryDocumentSnapshot document) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            return;
        }

        db.collection("users").document(currentUser.getUid()).get().addOnSuccessListener(documentSnapshot -> {
            List<DocumentReference> groupRefs = (List<DocumentReference>) documentSnapshot.get("groups");
            List<String> groups = new ArrayList<>();

            if (groupRefs != null && !groupRefs.isEmpty()) {
                for (DocumentReference groupRef : groupRefs) {
                    groups.add(groupRef.getId());
                }
            }

            checkAndScheduleNotification(document, groups);
        });



    }

    private void checkAndScheduleNotification(QueryDocumentSnapshot document, List<String> groups) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        String taskId = document.getId();

        String groupId = (String) document.get("group_id");

        String assigned_to = (String) document.get("assigned_to");

        Map<String, Object> reminder = (Map<String, Object>) document.get("reminder");

        if (!(reminder != null && reminder.containsKey("timestamp"))) {
            return;
        }

        long reminderTimestamp = ((long) reminder.get("timestamp")) * 1000;

        if (reminderTimestamp < System.currentTimeMillis()) {
            Log.d("WePlan", "handleTaskChangeEvent: reminderTimestamp is less than current time " + reminderTimestamp + ", " + System.currentTimeMillis());
            Log.d("WePlan", "handleTaskChangeEvent: Unsubscribing from topic " + taskId);
            AppFirebaseMessagingService.unsubscribeFromTopic(taskId);
            return;
        }

        if (assigned_to.equals(currentUser.getUid()) || groups.contains(groupId)) {
            Log.d("WePlan", "handleTaskChangeEvent: Subscribing to topic " + taskId);
            Log.d("CurrentUserId", "handleTaskChangeEvent: " + currentUser.getUid());
            Log.d("GroupId", "handleTaskChangeEvent: " + groupId);
            AppFirebaseMessagingService.subscribeToTopic(taskId);
        } else {
            Log.d("WePlan", "handleTaskChangeEvent: Unsubscribing from topic " + taskId);
            AppFirebaseMessagingService.unsubscribeFromTopic(taskId);
            return;
        }

        long delay = reminderTimestamp - System.currentTimeMillis();
        long _15Minutes = 15 * 60 * 1000;
        long delayInMinutes = delay / 1000 / 60;

        Log.d("WePlan", "handleTaskChangeEvent: delayInSeconds " + delay);
        Log.d("WePlan", "handleTaskChangeEvent: delayInMinutes " + delayInMinutes);

        if (delay < _15Minutes) {
            Log.d("WePlan", "handleTaskChangeEvent: delay is less than 15 minutes");
            sendTheScheduleNotificationRequest(taskId);
        }
    }


    private void sendTheScheduleNotificationRequest(String taskId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlString = "https://us-central1-weplan-4fbf1.cloudfunctions.net/testFCM?topic=" + taskId;
                    URL url = new URL(urlString);
                    HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();

                    InputStream inputStream = connection.getInputStream();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    String result = response.toString();

                    Log.d("WePlan", "sendTheScheduleNotificationRequest: " + result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }



}
