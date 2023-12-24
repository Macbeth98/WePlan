package edu.northeastern.weplan;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.Firebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.RemoteMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DataRepository {

    public static final String NOTIFICATION_CHANNEL_ID = "TASK_REMINDER";
    private static DataRepository instance;

    public boolean groupsLoaded = false;

    private Context context;

    private int pendingIntentCodeExtra = 0;

    private Map<String, Number> pendingIntentTaskNotificationMap = new HashMap<>();

    private User userData;
    private List<Group> groups;

    DataRepository() {
        groups = new ArrayList<>();
    }

    public static synchronized DataRepository getInstance() {
        if (instance == null) {
            instance = new DataRepository();
        }
        return instance;
    }

    public static void setInstance(DataRepository dataRepository) {
        instance = dataRepository;
    }

    public User getUserData() {
        return userData;
    }

    public void setUserData(User userData) {
        this.userData = userData;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public void resetData() {
        this.userData = null;
        this.groups = new ArrayList<>();
        this.groupsLoaded = false;
        instance = new DataRepository();
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
        if (userData != null) {
            this.userData.setGroups(groups);
            this.groupsLoaded = true;
        }
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return this.context;
    }

    public int getPendingIntentCodeExtra() {
        return this.pendingIntentCodeExtra;
    }

    public void setPendingIntentCodeExtra(int pendingIntentCodeExtra) {
        this.pendingIntentCodeExtra = pendingIntentCodeExtra;
    }

    public Map<String, Number> getPendingIntentTaskNotificationMap() {
        return this.pendingIntentTaskNotificationMap;
    }

    public void setPendingIntentTaskNotificationMap(Map<String, Number> pendingIntentTaskNotificationMap) {
        this.pendingIntentTaskNotificationMap = pendingIntentTaskNotificationMap;
    }

    public boolean checkNotificationPermission() {
        if (this.context == null) {
            return false;
        }

        if (ContextCompat.checkSelfPermission(this.context,
                android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            // Permission is granted
            return true;
        } else {
            // Permission is denied
            return false;
        }
    }

    private void buildAndShowTaskNotification(String description, String taskDueIn, PendingIntent pendingIntent) {
        boolean permissionGranted = this.checkNotificationPermission();
        if (permissionGranted) {
            Log.d("AlarmReceiver", "onReceive: Permission granted");
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                    DataRepository.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(description)
                    .setContentText("Task due in " + taskDueIn)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

            notificationManager.notify(new Random().nextInt(), builder.build());
        } else {
            Log.d("AlarmReceiver", "onReceive: Permission not granted");
        }
    }

    public void showTaskNotification(Map<String, Object> task, Context context) {

        String taskId = (String) task.get("taskId");

        String description = (String) task.get("description");

        String reminderOption = (String) task.get("reminderOption");

        String group_id = (String) task.get("groupId");

        if (reminderOption.equalsIgnoreCase("at time of event")) {
            reminderOption = "0 minutes";
        }

        String taskDueIn = reminderOption.replace("before", "").trim();

        String groupName = "";

        if (group_id != null && !group_id.contains("group")) {
            for (Group group : this.groups) {
                if (group.getId().equals(group_id)) {
                    groupName = group.getName();
                    break;
                }
            }
        }

        final PendingIntent[] pendingIntent = { null };

        try {

            DayViewFragment dayViewFragment = DayViewFragment.getInstance();

            if (dayViewFragment != null && taskId != null) {

                FirebaseFirestore db = FirebaseFirestore.getInstance();

                db.collection("tasks").document(taskId).get().addOnSuccessListener(documentSnapshot -> {
                    Task taskObj = documentSnapshot.toObject(Task.class);
                    if (taskObj != null) {
                        taskObj.setTask_id(documentSnapshot.getId());

                        Intent intent = new Intent(context, EditTaskActivity.class);
                        intent.putExtra("task_id", taskObj.getTask_id());
                        intent.putExtra("description", taskObj.getDescription());
                        intent.putExtra("due_date", taskObj.getDue_date());
                        intent.putExtra("notes", taskObj.getNotes());
                        intent.putExtra("image", taskObj.getImage());
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                        int randomValue = Math.abs(new Random().nextInt());

                        pendingIntent[0] = PendingIntent.getActivity(context, randomValue, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                        buildAndShowTaskNotification(description, taskDueIn, pendingIntent[0]);
                    }
                });

            } else {
                Log.d("AlarmReceiver", "onReceive: DayViewFragment is null");
                Log.d("AlarmReceiver", "onReceive: taskId: " + taskId);

                buildAndShowTaskNotification(description, taskDueIn, null);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void showHandleGroupNotification(RemoteMessage.Notification notification, Map<String, String> data) {
        String body = notification.getBody();
        String title = notification.getTitle();

        String groupId = data.get("groupId");
        String userId = data.get("userId");
        String onUserAddedToGroup = data.get("onUserAddedToGroup");
        String onUserRemovedFromGroup = data.get("onUserRemovedFromGroup");

        if (onUserAddedToGroup == null) {
            onUserAddedToGroup = "";
        }

        if (onUserRemovedFromGroup == null) {
            onUserRemovedFromGroup = "";
        }

        if (userId != null && userId.equals(userData.getId())) {
            Log.d("Notification", "showHandleGroupNotification: Ignoring notification for current user");
            return;
        }

        Log.d("Notification", "showHandleGroupNotification: " + groupId + " " + userId + " " + onUserAddedToGroup + " "
                + onUserRemovedFromGroup);

        boolean permissionGranted = this.checkNotificationPermission();

        if (!permissionGranted) {
            Log.d("Notification", "Permission not granted");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                DataRepository.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        notificationManager.notify(new Random().nextInt(), builder.build());
    }

    public void refreshTaskActivityView() {
        DayViewFragment dayViewFragment = DayViewFragment.getInstance();
        if (dayViewFragment != null) {
            dayViewFragment.fetchAndRefreshView();
        }
        WeekViewFragment weekViewFragment = WeekViewFragment.getInstance();
        if (weekViewFragment != null) {
            weekViewFragment.fetchAndRefreshView();
        }
    }

    public void showHandleUserNotification(RemoteMessage.Notification notification, Map<String, String> data) {
        String body = notification.getBody();
        String title = notification.getTitle();

        String groupId = data.get("groupId");
        String userId = data.get("userId");
        String onUserAddedToGroup = data.get("onUserAddedToGroup");
        String onUserRemovedFromGroup = data.get("onUserRemovedFromGroup");

        if (onUserAddedToGroup == null) {
            onUserAddedToGroup = "";
        }

        if (onUserRemovedFromGroup == null) {
            onUserRemovedFromGroup = "";
        }

        Log.d("Notification", "showHandleUserNotification: " + groupId + " " + userId + " " + onUserAddedToGroup + " "
                + onUserRemovedFromGroup);

        if (onUserAddedToGroup.equals("true")) {
            AppFirebaseMessagingService.subscribeToTopic(groupId);
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("groups").document(groupId).get().addOnSuccessListener(documentSnapshot -> {
                Group group = documentSnapshot.toObject(Group.class);
                if (group != null) {
                    group.setId(documentSnapshot.getId());
                    userData.getGroups().add(group);
                    refreshTaskActivityView();
                }
            });
        }

        if (onUserRemovedFromGroup.equals("true")) {
            AppFirebaseMessagingService.unsubscribeFromTopic(groupId);
            for (Group group : userData.getGroups()) {
                if (group.getId().equals(groupId)) {
                    userData.getGroups().remove(group);
                    refreshTaskActivityView();
                    break;
                }
            }
        }

        boolean permissionGranted = this.checkNotificationPermission();

        if (!permissionGranted) {
            Log.d("Notification", "Permission not granted");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                DataRepository.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        notificationManager.notify(new Random().nextInt(), builder.build());
    }

    private boolean isPartOfGroup(String groupId) {

        Log.d("WePlan", "Group Size: " + this.getGroups().size());

        for (Group group : this.getGroups()) {
            Log.d("WePlan", "isPartOfGroup: GroupId: " + group.getId());
            if (group.getId().equalsIgnoreCase(groupId)) {
                Log.d("WePlan", "isPartOfGroup: Part of group, GroupId: " + groupId);
                return true;
            }
        }

        Log.d("WePlan", "isPartOfGroup: Not part of group, GroupId: " + groupId);

        return false;
    }

    public void showTaskLifeCycleNotification(RemoteMessage.Notification notification, Map<String, String> data) {
        String body = notification.getBody();
        String title = notification.getTitle();

        String groupId = data.get("groupId");
        String assigned_to = data.get("assigned_to");
        String changeType = data.get("changeType");
        String taskId = data.get("taskId");

        if (taskId == null) {
            return;
        }

        if (changeType == null) {
            changeType = "";
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            return;
        }

        String currentUserId = currentUser.getUid();

        Log.d("Notification", "showTaskLifeCycleNotification: " + groupId + " " + assigned_to + " " + currentUserId);

        if (assigned_to != null && assigned_to.equals(currentUserId)) {
            if (changeType.equals("insert")) {
                Log.d("Notification", "showTaskLifeCycleNotification: Ignoring notification for current user");
                return;
            }
        }

        if (groupId != null) {
            boolean isPartOfGroup = this.isPartOfGroup(groupId);
            if (!isPartOfGroup) {
                Log.d("Notification", "showTaskLifeCycleNotification: Ignoring notification for current user");
                return;
            }
        }

        if (changeType.equals("insert")) {
            AppFirebaseMessagingService.subscribeToTopic(taskId);
        } else if (changeType.equals("delete")) {
            AppFirebaseMessagingService.unsubscribeFromTopic(taskId);
        }

        refreshTaskActivityView();

        boolean permissionGranted = this.checkNotificationPermission();

        if (!permissionGranted) {
            Log.d("Notification", "Permission not granted");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                DataRepository.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        notificationManager.notify(new Random().nextInt(), builder.build());
    }
}
