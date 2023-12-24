package edu.northeastern.weplan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class AppFirebaseMessagingService extends FirebaseMessagingService{

    private DataRepository dataRepository = DataRepository.getInstance();

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // Check if the message contains data
        if (remoteMessage.getData().size() > 0) {
            handleNotificationData(remoteMessage);
        }

        // Check if the message contains a notification
        if (remoteMessage.getNotification() != null) {
            handleNotification(remoteMessage.getNotification(), remoteMessage.getData(), getApplicationContext());
//            showNotification(remoteMessage.getNotification(), new HashMap<>());
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
    }

    private void handleNotificationData(RemoteMessage remoteMessage) {
        // Handle data payload (e.g., for silent notifications)
        Log.d("AppFirebaseMessagingService", "Data payload: " + remoteMessage.getData());
    }

    private void showNotification(RemoteMessage.Notification notification, Map<String, Object> task) {
        // Create and show a notification with the FCM notification data
        Log.d("AppFirebaseMessagingService", "Show Notification: " + notification.getBody());

        DataRepository.getInstance().showTaskNotification(task, getApplicationContext());
    }

    private boolean isPartOfGroup(String groupId) {
        DataRepository dataRepository = DataRepository.getInstance();

        Log.d("WePlan", "Group Size: " + dataRepository.getGroups().size());

        for (Group group : dataRepository.getGroups()) {
            Log.d("WePlan", "isPartOfGroup: GroupId: " + group.getId());
            if (group.getId().equalsIgnoreCase(groupId)) {
                Log.d("WePlan", "isPartOfGroup: Part of group, GroupId: " + groupId);
                return true;
            }
        }

        Log.d("WePlan", "isPartOfGroup: Not part of group, GroupId: " + groupId);

        return false;
    }

    private void handleNotification(RemoteMessage.Notification notification, Map<String, String> data, Context applicationContext) {
        // Handle FCM messages here.
        Log.d("AppFirebaseMessagingService", "handle Notification: " + notification.getBody());
        Log.d("AppFirebaseMessagingService", "handle Notification: " + data.toString());

        String payloadType = data.get("payloadType");

        if (payloadType != null) {
            if (payloadType.equals("HandleGroup")) {

                DataRepository.getInstance().showHandleGroupNotification(notification, data);

            } else if (payloadType.equals("HandleUser")) {
                DataRepository.getInstance().showHandleUserNotification(notification, data);
            } else if (payloadType.equals("TaskLifeCycle")) {
                DataRepository.getInstance().showTaskLifeCycleNotification(notification, data);
            }
        }

        if (payloadType != null && !payloadType.equals("TaskReminder")) {
            Log.d("AppFirebaseMessagingService", "handle Notification: Not a task reminder");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        String currentUserId = currentUser.getUid();

        String taskId = data.get("taskId");

        if (taskId == null) {
            return;
        }

        db.collection("tasks").document(taskId).get().addOnSuccessListener(documentSnapshot -> {
            Map<String, Object> task = documentSnapshot.getData();

            Task taskObj = documentSnapshot.toObject(Task.class);

            boolean is_completed = (boolean) task.get("is_completed");

            if (is_completed) {
                return;
            }

            String assigned_to = (String) task.get("assigned_to");

            String groupId = (String) task.get("group_id");

            if (groupId == null) groupId = "";

            String finalGroupId = groupId;

            boolean taskIsPartOfGroup = isPartOfGroup(finalGroupId);

            if (!currentUserId.equals(assigned_to) && !taskIsPartOfGroup) {
                Log.d("AppFirebaseMessagingService", "handle Notification: Not assigned to user or not part of group");
                return;
            }

            if (!task.containsKey("reminder")) {
                Log.d("AppFirebaseMessagingService", "handle Notification: No reminder");
                return;
            }

            Map<String, Object> reminder = (Map<String, Object>) task.get("reminder");

            if (reminder == null) {
                Log.d("AppFirebaseMessagingService", "handle Notification: No reminder");
                return;
            }

            if (!reminder.containsKey("timestamp")) {
                Log.d("AppFirebaseMessagingService", "handle Notification: No reminder timestamp");
                return;
            }

            long timestamp = ((long) reminder.get("timestamp")) * 1000;

            long currentTime = System.currentTimeMillis();

            long delay = timestamp - currentTime;

            float delayInMinutes = delay / 1000 / 60;

            Log.d("AppFirebaseMessagingService", "handle Notification: delayInSeconds " + delay);
            Log.d("AppFirebaseMessagingService", "handle Notification: delayInMinutes " + delayInMinutes);

//            delay = 0;

            if (delay > 0) {

                Map<String, Number> pendingIntentTaskNotificationMap = dataRepository.getPendingIntentTaskNotificationMap();

                if (pendingIntentTaskNotificationMap.containsKey(taskId)) {
                    long intentTimestamp = Objects.requireNonNull(pendingIntentTaskNotificationMap.get(taskId)).longValue();

                    if (intentTimestamp == timestamp) {
                        Log.d("AppFirebaseMessagingService", "handle Notification: Already set");
                        return;
                    }
                }

                Intent intent = new Intent(applicationContext, AlarmReceiver.class);
                intent.putExtra("description", (String) task.get("description"));
                intent.putExtra("taskId", taskId);
                intent.putExtra("reminderOption", (String) reminder.get("option"));
                intent.putExtra("reminderTimestamp", timestamp);
                intent.putExtra("due_date", (String) task.get("due_date"));
                intent.putExtra("notes", (String) task.get("notes"));
                intent.putExtra("group_id", (String) task.get("group_id"));

                int requestCodeExtra = dataRepository.getPendingIntentCodeExtra();

                int requestCode = new Random().nextInt() + requestCodeExtra;

                PendingIntent pendingIntent = PendingIntent.getBroadcast(applicationContext, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager alarmManager = (AlarmManager) applicationContext.getSystemService(ALARM_SERVICE);

                requestCodeExtra++;

                pendingIntentTaskNotificationMap.put(taskId, timestamp);

                dataRepository.setPendingIntentCodeExtra(requestCodeExtra);
                dataRepository.setPendingIntentTaskNotificationMap(pendingIntentTaskNotificationMap);

                if (alarmManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, currentTime + delay, pendingIntent);
                        } else {
                            alarmManager.set(AlarmManager.RTC_WAKEUP, currentTime + delay - (1000 * 25), pendingIntent);
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, currentTime + delay - (1000 * 25), pendingIntent);
                    }
                }
            } else {
                task.put("reminderOption", reminder.get("option"));
                showNotification(notification, task);
            }
        });
    }

    public static void subscribeToTopic(String topic) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnCompleteListener(task -> {
                    String msg = "Subscribed to " + topic;
                    if (!task.isSuccessful()) {
                        msg = "Failed to subscribe to " + topic;
                    }
                    Log.d("AppFirebaseMessagingService", msg);
                });
    }

    public static void unsubscribeFromTopic(String topic) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener(task -> {
                    String msg = "Unsubscribed from " + topic;
                    if (!task.isSuccessful()) {
                        msg = "Failed to unsubscribe from " + topic;
                    }
                    Log.d("AppFirebaseMessagingService", msg);
                });
    }
}
