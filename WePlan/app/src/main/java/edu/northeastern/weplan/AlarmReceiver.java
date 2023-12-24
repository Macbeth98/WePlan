package edu.northeastern.weplan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String description = intent.getStringExtra("description");

        Log.d("AlarmReceiver", "onReceive: " + description);

        String reminderOption = intent.getStringExtra("reminderOption");

        String taskId = intent.getStringExtra("taskId");

        String groupId = intent.getStringExtra("groupId");

        Map<String, Object> task = new HashMap<>();

        task.put("description", description);
        task.put("reminderOption", reminderOption);
        task.put("taskId", taskId);
        task.put("groupId", groupId);

        DataRepository dataRepository = DataRepository.getInstance();

        dataRepository.showTaskNotification(task, context);
    }



}
