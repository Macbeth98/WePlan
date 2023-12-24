package edu.northeastern.weplan;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.BuildConfig;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

import okhttp3.internal.Util;

public class AddTaskActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 1;

    private static final int REQUEST_IMAGE_CAPTURE = 2;

    private static final int PERMISSIONS_REQUEST_CAMERA = 2;

    private Uri selectedImageUri;
    private Uri photoURI;

    private EditText editTextTaskDescription, editTextDueDate, editTextNotes;

    private TextView textViewSelectGroup;
    private Button buttonAddTask, buttonPickDueDate, buttonAddImage;
    private FirebaseFirestore firestore;

    private Spinner spinnerReminderTime, spinnerGroupFilterAddTask;
    private static final String[] REMINDER_OPTIONS = new String[] {
            "No Reminder", "At time of event", "15 minutes before", "30 minutes before", "1 hour before"
    };

    private static final Map<String, Number> REMINDER_OPTION_MAP = new HashMap() {
        {
            put("No Reminder", -1);
            put("At time of event", 0);
            put("15 minutes before", 15);
            put("30 minutes before", 30);
            put("1 hour before", 60);
        }
    };

    private Spinner spinnerFrequency;
    private EditText editTextEndDate;
    private List<String> groupNames = new ArrayList<>();
    private Map<String, String> groupNameToIdMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        editTextTaskDescription = findViewById(R.id.editTextTaskDescription);
        editTextDueDate = findViewById(R.id.editTextDueDate);
//        editTextTags = findViewById(R.id.editTextTags);
        editTextNotes = findViewById(R.id.editTextNotes);
        buttonAddTask = findViewById(R.id.buttonAddTask);
        buttonPickDueDate = findViewById(R.id.buttonPickDueDate);
        buttonAddImage = findViewById(R.id.buttonAddImage);

        spinnerReminderTime = findViewById(R.id.spinnerReminderTime);
        ArrayAdapter<CharSequence> reminderAdapter = ArrayAdapter.createFromResource(this,
                R.array.reminder_options, android.R.layout.simple_spinner_item);
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReminderTime.setAdapter(reminderAdapter);

        firestore = FirebaseFirestore.getInstance();

        spinnerFrequency = findViewById(R.id.spinnerFrequency);
        ArrayAdapter<CharSequence> frequencyAdapter = ArrayAdapter.createFromResource(this,
                R.array.frequency_options, android.R.layout.simple_spinner_item);
        frequencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrequency.setAdapter(frequencyAdapter);

        textViewSelectGroup = findViewById(R.id.textViewSelectGroup);

        spinnerGroupFilterAddTask = findViewById(R.id.spinnerGroupFilterAddTask);
        this.fetchGroups();
        ArrayAdapter<String> groupFilterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                groupNames);
        groupFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGroupFilterAddTask.setAdapter(groupFilterAdapter);

        editTextEndDate = findViewById(R.id.editTextEndDate);
        editTextEndDate.setOnClickListener(v -> showEndDatePicker());
        editTextEndDate.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showEndDatePicker();
            }
        });

        buttonPickDueDate.setOnClickListener(v -> showDateTimePicker());
        buttonAddTask.setOnClickListener(v -> uploadImageAndCreateTask());
        buttonAddImage.setOnClickListener(v -> selectImage());

    }

    private void fetchGroups() {
        List<Group> groups = DataRepository.getInstance().getGroups();

        groupNames.clear();
        groupNameToIdMap.clear();

        groupNames.add("Self");
        groupNameToIdMap.put("Self", null);

        for (Group group : groups) {
            groupNames.add(group.getName());
            groupNameToIdMap.put(group.getName(), group.getId());
        }

        // groupFilterAdapter.notifyDataSetChanged();
        // spinnerGroupFilter.setEnabled(true);
    }

    private void showDateTimePicker() {
        final Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    new TimePickerDialog(this,
                            (timeView, hourOfDay, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);
                                calendar.set(Calendar.SECOND, 0);
                                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                                        Locale.getDefault());
                                isoFormat.setTimeZone(TimeZone.getTimeZone("America/New_York"));
                                editTextDueDate.setText(isoFormat.format(calendar.getTime()));
                            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void showEndDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        DatePickerDialog endDatePicker = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    editTextEndDate.setText(
                            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime()));
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        endDatePicker.show();
    }

    private void addTask(String imageUrl) {
        String description = editTextTaskDescription.getText().toString().trim();
        String dueDate = editTextDueDate.getText().toString().trim();
//        String tags = editTextTags.getText().toString().trim();
        String notes = editTextNotes.getText().toString().trim();
        String frequency = spinnerFrequency.getSelectedItem().toString();
        String endDate = editTextEndDate.getText().toString();
        String groupName = spinnerGroupFilterAddTask.getSelectedItem().toString();

        Log.d("AddTaskActivity", "Group Name: " + groupName);
        Log.d("AddTaskActivity", "Frequency: " + frequency);

        if (dueDate.isEmpty()) {
            dueDate = getDefaultDueDate();
        }

        // String taskId = databaseReference.push().getKey();
        String currentISODateTime = getCurrentISODateTime();
        boolean isCompleted = false;

        String assignedTo = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        Log.e("AddTaskActivity", "assignedTo: " + assignedTo);

        String groupId = groupNameToIdMap.get(groupName);

        if (groupId == null) {
            groupId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        HashMap<String, Object> attachments = new HashMap<>();
        attachments.put("notes", notes);

        String selectedReminderOption = spinnerReminderTime.getSelectedItem().toString();
        HashMap<String, Object> reminder = new HashMap<>();
        reminder.put("option", selectedReminderOption);


        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneId.of("America/New_York"));

        if (selectedReminderOption.equals("No Reminder")) {
            reminder.put("time", "");
        } else {
            int reminderTimeInMinutes = REMINDER_OPTION_MAP.get(selectedReminderOption).intValue();
            try {
                // LocalDateTime localDateTime = LocalDateTime.parse(dueDate, formatter);
                // localDateTime = localDateTime.minusMinutes(reminderTimeMinutes);
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(dueDate, formatter);
                zonedDateTime = zonedDateTime.minusMinutes(reminderTimeInMinutes);

                reminder.put("beforeMinutes", reminderTimeInMinutes);
                // reminder.put("time", localDateTime.toString());
                // reminder.put("timestamp", localDateTime.toEpochSecond(ZoneOffset.UTC));
                reminder.put("time", zonedDateTime.toLocalDateTime().toString());
                long epochSeconds = zonedDateTime.toEpochSecond();
                reminder.put("timestamp", epochSeconds);

                // Log.d("AddTaskActivity", "Reminder Time: " + localDateTime);
                // Log.d("AddTaskActivity", "Reminder Timestamp: " +
                // localDateTime.toEpochSecond(ZoneOffset.UTC));
                Log.d("AddTaskActivity", "Reminder Time EST/EDT: " + zonedDateTime);
                Log.d("AddTaskActivity", "Reminder Timestamp: " + epochSeconds);
            } catch (Exception e) {
                Log.e("AddTaskActivity", "Error parsing date", e);
            }
        }

        // make task id auto gen from firebase
        HashMap<String, Object> taskMap = new HashMap<>();
        taskMap.put("description", description);
        taskMap.put("due_date", dueDate);
        taskMap.put("created_at", currentISODateTime);
        taskMap.put("updated_at", currentISODateTime);
        taskMap.put("is_completed", isCompleted);
        taskMap.put("assigned_to", assignedTo);
        taskMap.put("group_id", groupId);
        taskMap.put("reminder", reminder);
        taskMap.put("notes", notes);
        taskMap.put("image", imageUrl);

        HashMap<String, Object> frequencyMap = new HashMap<>();
        frequencyMap.put("option", frequency);
        frequencyMap.put("end_date", endDate);
        taskMap.put("frequency", frequencyMap);

        Log.d("AddTaskActivity", "Frequency: " + frequencyMap);

        boolean originalSource;

        if (!frequency.equals("Never") && !endDate.isEmpty()) {
            originalSource = true;
            taskMap.put("original_source", true);
            int randomValue = Math.abs(new Random().nextInt());
            frequencyMap.put("id", String.valueOf(randomValue));

            DateTimeFormatter formatterDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDateTime localDateTime = LocalDateTime.parse(dueDate, formatter);
            LocalDate endDateLocalDate = LocalDate.parse(endDate, formatterDate);
            LocalDateTime endDateTime = LocalDateTime.parse(endDateLocalDate.toString() + "T23:59:59Z", formatter);

            Log.d("ENdate time", "endDateTime: " + endDateTime);

            if (frequency.equals("Daily")) {
                int days = (int) (endDateTime.toLocalDate().toEpochDay() - localDateTime.toLocalDate().toEpochDay());

                if (days > 60) {
                    new AlertDialog.Builder(this)
                            .setTitle("Error")
                            .setMessage("Daily tasks cannot be scheduled for more than 60 days")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
            } else if (frequency.equals("Weekly")) {
                int weeks = (int) ((endDateTime.toLocalDate().toEpochDay() - localDateTime.toLocalDate().toEpochDay())
                        / 7);

                if (weeks > 53) {
                    new AlertDialog.Builder(this)
                            .setTitle("Error")
                            .setMessage("Weekly tasks cannot be scheduled for more than 53 weeks")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
            } else if (frequency.equals("Monthly")) {
                int months = (int) ((endDateTime.toLocalDate().toEpochDay() - localDateTime.toLocalDate().toEpochDay())
                        / 30);

                if (months > 12) {
                    new AlertDialog.Builder(this)
                            .setTitle("Error")
                            .setMessage("Monthly tasks cannot be scheduled for more than 12 months")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
            }
        } else {
            originalSource = false;
        }

        if (frequencyMap.containsKey("id")) {
            Log.d("AddTaskActivity", "This is a frequency set task");
            Log.d("AddTaskActivity", "Frequency ID: " + frequencyMap.get("id"));
            Log.d("AddTaskActivity", "Frequency: " + frequencyMap);
        }

        Log.d("AddTaskActivity", "Description: " + description);
        Log.d("AddTaskActivity", "Due Date: " + dueDate);
//        Log.d("AddTaskActivity", "Tags: " + tags);
        Log.d("AddTaskActivity", "Notes: " + notes);
        Log.d("AddTaskActivity", "Image: " + imageUrl);
        Log.d("AddTaskActivity", "Current ISO DateTime: " + currentISODateTime);
        Log.d("AddTaskActivity", "Is Completed: " + isCompleted);
        Log.d("AddTaskActivity", "Assigned To: " + assignedTo);
        Log.d("AddTaskActivity", "Group ID: " + groupId);
        Log.d("AddTaskActivity", "Selected Reminder Option: " + selectedReminderOption);

        firestore.collection("tasks").add(taskMap)
                .addOnSuccessListener(documentReference -> {
                    AppFirebaseMessagingService.subscribeToTopic(documentReference.getId());
                    Toast.makeText(AddTaskActivity.this, "Task added successfully!", Toast.LENGTH_SHORT).show();
                    if (originalSource) {
                        invokeFrequencyTaskAddedCF(documentReference.getId(), taskMap);
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(AddTaskActivity.this, "Failed to add task: " + e.getMessage(), Toast.LENGTH_SHORT)
                            .show();
                });
    }

    private void invokeFrequencyTaskAddedCF(String taskId, HashMap<String, Object> taskMap) {
        Map<String, Object> frequencyMap = (Map<String, Object>) taskMap.get("frequency");

        String frequency = (String) frequencyMap.get("option");

        if (frequency.equals("Never")) {
            return;
        }

        boolean originalSource = (boolean) taskMap.get("original_source");

        if (!originalSource) {
            return;
        }

        Log.d("AddTaskActivity", "Invoking Cloud Function for frequency task");

        String urlString = "https://us-central1-weplan-4fbf1.cloudfunctions.net/frequencyTaskAdded?taskId=" + taskId;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    Log.d("AddFrequencyTask: Add Frequency Task", "Request URL: " + urlString);
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

                    Log.d("AddFrequencyTask: Frequency Task Response", "Response: " + result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private String getDefaultDueDate() {

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 7);
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        isoFormat.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        return isoFormat.format(calendar.getTime());
    }

    private String getDefaultReminderTime() {

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 6);
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        isoFormat.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        return isoFormat.format(calendar.getTime());
    }

    public static String getCurrentISODateTime() {
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        isoFormat.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        return isoFormat.format(new Date());
    }

    private void selectImage() {
        final CharSequence[] options = { "Take Photo", "Choose from Gallery", "Cancel" };

        AlertDialog.Builder builder = new AlertDialog.Builder(AddTaskActivity.this);
        builder.setTitle("Add Photo!");
        builder.setItems(options, (dialog, item) -> {
            if (options[item].equals("Take Photo")) {
                dispatchTakePictureIntent();
            } else if (options[item].equals("Choose from Gallery")) {
                Intent pickPhoto = new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(pickPhoto, REQUEST_IMAGE_PICK);
            } else if (options[item].equals("Cancel")) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void dispatchTakePictureIntent() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { android.Manifest.permission.CAMERA },
                    PERMISSIONS_REQUEST_CAMERA);
        } else {
            try {
                createImageFile();
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            } catch (IOException ex) {
                Toast.makeText(this, "Error occurred while creating the file", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(this, "Camera permission is required to use camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();

            ImageView imagePreview = findViewById(R.id.imagePreview);
            Picasso.get().load(selectedImageUri).into(imagePreview);

        }

        else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            ImageView imagePreview = findViewById(R.id.imagePreview);
            Picasso.get().load(photoURI).into(imagePreview);
            selectedImageUri = photoURI;
        }

    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }

    // private void uploadImageAndCreateTask() {
    // if (selectedImageUri != null) {
    // StorageReference storageRef = FirebaseStorage.getInstance().getReference();
    // StorageReference imageRef = storageRef.child("task_images/" +
    // UUID.randomUUID().toString());
    //
    // imageRef.putFile(selectedImageUri)
    // .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl()
    // .addOnSuccessListener(uri -> addTask(uri.toString())))
    // .addOnFailureListener(e -> {
    // Log.e("AddTaskActivity", "Image upload failed", e);
    // Toast.makeText(this, "Image upload failed: " + e.getMessage(),
    // Toast.LENGTH_LONG).show();
    // addTask(null);
    // });
    // } else {
    // addTask(null);
    // }
    // }

    private void uploadImageAndCreateTask() {
        if (selectedImageUri != null) {
            StorageReference storageRef = FirebaseStorage.getInstance().getReference();
            StorageReference imageRef = storageRef.child("task_images/" + UUID.randomUUID().toString());

            imageRef.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                String firebaseStorageUrl = uri.toString();
                                addTask(firebaseStorageUrl);
                            }))
                    .addOnFailureListener(e -> {
                        Log.e("AddTaskActivity", "Image upload failed", e);
                        Toast.makeText(this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        addTask(null);
                    });
        } else {
            addTask(null);
        }
    }

    private void createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir);
        selectedImageUri = Uri.fromFile(image);
        photoURI = FileProvider.getUriForFile(this, "edu.northeastern.weplan.provider", image);
    }

}