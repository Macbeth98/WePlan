package edu.northeastern.weplan;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
import java.util.TimeZone;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;


public class EditTaskActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;
    private static final int PERMISSIONS_REQUEST_CAMERA = 2;
    private EditText editTextTaskDescription, editTextDueDate, editTextNotes;

    private TextView editTextEndDate, textViewEditRepeats;

    private CheckBox checkBoxTaskCompleted;

    private Button buttonEditImage, buttonAddToCalendar;
    private Uri imageUri, photoURI;
    private ImageView imageTask;
    private String taskId;
    private Task currentTask;
    private Spinner spinnerReminderTime, spinnerFrequency;
    private List<String> groupNames = new ArrayList<>();
    private Map<String, String> groupNameToIdMap = new HashMap<>();

    private Map<String, Object> taskFrequencyMap = new HashMap<>();

    private static final Map<String, Number> REMINDER_OPTION_MAP = new HashMap() {{
        put("No Reminder", -1);
        put("At time of event", 0);
        put("15 minutes before", 15);
        put("30 minutes before", 30);
        put("1 hour before", 60);
    }};

    private TextView textViewEditSelectGroup;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_task);

        spinnerReminderTime = findViewById(R.id.spinnerReminderTime);
        spinnerFrequency = findViewById(R.id.spinnerFrequency);
        editTextTaskDescription = findViewById(R.id.editTextTaskDescription);
        editTextDueDate = findViewById(R.id.editTextDueDate);
        editTextEndDate = findViewById(R.id.editTextEndDate);
        textViewEditRepeats = findViewById(R.id.textViewEditRepeats);
        editTextNotes = findViewById(R.id.editTextNotes);
        imageTask = findViewById(R.id.imagePreview);
        checkBoxTaskCompleted = findViewById(R.id.checkBoxTaskCompleted);

        Button deleteButton = findViewById(R.id.buttonDeleteTask);
        deleteButton.setOnClickListener(v -> {
            confirmDelete();
        });

        setupSpinners();
        setupEditTexts();

        textViewEditSelectGroup = findViewById(R.id.textViewEditSelectGroup);

        taskId = getIntent().getStringExtra("task_id");
        if (taskId != null) {
            loadTask(taskId);
        }

        Button updateButton = findViewById(R.id.buttonUpdateTask);
        updateButton.setOnClickListener(v -> {
            updateTask();
        });

        buttonEditImage = findViewById(R.id.buttonEditImage);
        buttonAddToCalendar = findViewById(R.id.buttonAddToCalendar);
        buttonAddToCalendar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addEventToCalendar();
            }
        });

//        String taskId = getIntent().getStringExtra("task_id");
//        String description = getIntent().getStringExtra("description");
//        String dueDate = getIntent().getStringExtra("due_date");
//        String notes = getIntent().getStringExtra("notes");
//        imageUri = getIntent().getStringExtra("image") != null ? Uri.parse(getIntent().getStringExtra("image")) : null;
//        if (taskId != null) {
//            // Fetch the task data using taskId and populate the fields for editing
//            editTextTaskDescription.setText(description);
//            editTextDueDate.setText(dueDate);
//            editTextNotes.setText(notes);
//            Picasso.get().load(imageUri).into(imageTask);
//            setupImagePreviewClickListener();
//        }
    }

    private void addEventToCalendar() {
        Intent calendarIntent = new Intent(Intent.ACTION_INSERT);
        calendarIntent.setType("vnd.android.cursor.item/event");

        String title = editTextTaskDescription.getText().toString();
        String notes = editTextNotes.getText().toString();
        Calendar dueDate = parseDateForCalendar(editTextDueDate.getText().toString());
        Calendar endDate = parseDateForCalendar(editTextEndDate.getText().toString());

        if (dueDate != null) {
            calendarIntent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dueDate.getTimeInMillis());
        }

        if (endDate != null) {
            calendarIntent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endDate.getTimeInMillis());
        }

        calendarIntent.putExtra(CalendarContract.Events.TITLE, title);
        calendarIntent.putExtra(CalendarContract.Events.DESCRIPTION, notes);
        try {
            startActivity(calendarIntent);
        } catch (Exception e) {
            Toast.makeText(EditTaskActivity.this, "No calendar app found", Toast.LENGTH_SHORT).show();
        }
    }

    private Calendar parseDateForCalendar(String dateString) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        try {
            Date date = dateFormat.parse(dateString);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            return calendar;
        } catch (ParseException e) {
            Log.e("EditTaskActivity", "Error parsing date", e);
            return null;
        }
    }

    private void setupSpinners() {
        spinnerReminderTime = findViewById(R.id.spinnerReminderTime);
        ArrayAdapter<CharSequence> reminderAdapter = ArrayAdapter.createFromResource(this,
                R.array.reminder_options, android.R.layout.simple_spinner_item);
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReminderTime.setAdapter(reminderAdapter);

        spinnerFrequency = findViewById(R.id.spinnerFrequency);
        ArrayAdapter<CharSequence> frequencyAdapter = ArrayAdapter.createFromResource(this,
                R.array.frequency_options, android.R.layout.simple_spinner_item);
        frequencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrequency.setAdapter(frequencyAdapter);

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

    private void setupEditTexts() {
        editTextDueDate.setOnClickListener(v ->  showDateTimePicker());
//        editTextEndDate.setOnClickListener(v -> showEndDatePicker());
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
                                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
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
                    editTextEndDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime()));
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        endDatePicker.show();
    }



    private void loadTask(String taskId) {
        FirebaseFirestore
                .getInstance()
                .collection("tasks")
                .document(taskId)
                .get()
                .addOnSuccessListener(documentSnapshot-> {
                    taskFrequencyMap = (Map<String, Object>) documentSnapshot.get("frequency");
                    currentTask = documentSnapshot.toObject(Task.class);
                    if (currentTask != null) {
                        editTextTaskDescription.setText(currentTask.getDescription());
                        editTextDueDate.setText(currentTask.getDue_date());
                        editTextNotes.setText(currentTask.getNotes());
                        editTextEndDate.setText(currentTask.getFrequency() != null ? currentTask.getFrequency().getEnd_date() : null);
                        if (editTextEndDate.getText() == null) {
                            editTextEndDate.setVisibility(View.GONE);
                        }
                        String reminderTime = currentTask.getReminder() != null ? currentTask.getReminder().getOption() : null;
                        if (reminderTime != null) {
                            spinnerReminderTime.setSelection(((ArrayAdapter<String>) spinnerReminderTime.getAdapter()).getPosition(reminderTime));
                        }
                        String frequency = currentTask.getFrequency() != null ? currentTask.getFrequency().getOption() : null;
                        if (frequency != null) {
                            spinnerFrequency.setSelection(((ArrayAdapter<String>) spinnerFrequency.getAdapter()).getPosition(frequency));
                            textViewEditRepeats.setText("Repeats: " + frequency);
                        } else {
                            editTextEndDate.setVisibility(View.GONE);
                        }

                        String group_id = currentTask.getGroup_id();

                        if (group_id != null) {
                            List<Group> groups = DataRepository.getInstance().getGroups();

                            for (Group group : groups) {
                                if (group.getId().equals(group_id)) {
                                    textViewEditSelectGroup.setText("Group: " + group.getName());
                                    break;
                                }
                            }
                        }

                        imageUri = currentTask.getImage() != null ? Uri.parse(currentTask.getImage()) : null;
                        checkBoxTaskCompleted.setChecked(currentTask.getIs_completed());
                        Picasso.get().load(imageUri).into(imageTask);
                        imageTask.setVisibility(View.VISIBLE);
                        setupImagePreviewClickListener();
                        setupAddImageButtonClickListener();
//                        setupCheckboxListener();
                    }
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading task", Toast.LENGTH_SHORT).show();
                });
    }

    //    private void setupCheckboxListener() {
//        checkBoxTaskCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> updateTaskCompletion(isChecked));
//    }
//
//    private void updateTaskCompletion(boolean isCompleted) {
//        FirebaseFirestore.getInstance().collection("tasks").document(taskId)
//                .update("is_completed", isCompleted)
//                .addOnSuccessListener(s -> Toast.makeText(EditTaskActivity.this, "Task completion updated", Toast.LENGTH_SHORT).show())
//                .addOnFailureListener(e -> Toast.makeText(EditTaskActivity.this, "Error updating task completion", Toast.LENGTH_SHORT).show());
//    }
    private void updateTask() {
        String updatedDescription = editTextTaskDescription.getText().toString().trim();
        String updatedDueDate = editTextDueDate.getText().toString().trim();
        String updatedNotes = editTextNotes.getText().toString().trim();
        String reminderTime = spinnerReminderTime.getSelectedItem().toString();
//        String frequency = spinnerFrequency.getSelectedItem().toString();
        String dueDate = editTextDueDate.getText().toString();
//        String endDate = editTextEndDate.getText().toString();

        boolean isCompleted = checkBoxTaskCompleted.isChecked();

        uploadImageAndUpdateTask();

        Map<String, Object> updates = new HashMap<>();
        updates.put("description", updatedDescription);
        updates.put("updated_at", AddTaskActivity.getCurrentISODateTime());
        updates.put("notes", updatedNotes);
        updates.put("image", imageUri != null ? imageUri.toString() : null);
        updates.put("due_date", updatedDueDate);
        updates.put("is_completed", isCompleted);

         if (reminderTime != null && !reminderTime.isEmpty()) {
                Map<String, Object> reminder = new HashMap<>();
             int reminderTimeInMinutes = REMINDER_OPTION_MAP.get(reminderTime).intValue();
             DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                     .withZone(ZoneId.of("America/New_York"));
             try {
//                 LocalDateTime localDateTime = LocalDateTime.parse(dueDate, formatter);
//                 localDateTime = localDateTime.minusMinutes(reminderTimeInMinutes);
                 ZonedDateTime zonedDateTime = ZonedDateTime.parse(dueDate, formatter);
                 zonedDateTime = zonedDateTime.minusMinutes(reminderTimeInMinutes);

                 reminder.put("beforeMinutes", reminderTimeInMinutes);
                 reminder.put("option", reminderTime);
//                 reminder.put("time", localDateTime.toString());
                 reminder.put("time", zonedDateTime.toLocalDateTime().toString());
                 long epochSeconds = zonedDateTime.toEpochSecond();
                 reminder.put("timestamp", epochSeconds);
                    updates.put("reminder", reminder);
//                 Log.d("AddTaskActivity", "Reminder Time: " + localDateTime);
//                 Log.d("AddTaskActivity", "Reminder Timestamp: " + localDateTime.toEpochSecond(ZoneOffset.UTC));
                 Log.d("AddTaskActivity", "Reminder Time EST/EDT: " + zonedDateTime);
                 Log.d("AddTaskActivity", "Reminder Timestamp: " + epochSeconds);
             } catch (Exception e) {
                 Log.e("AddTaskActivity", "Error parsing date", e);
             }
         }
//        if (frequency != null && !frequency.isEmpty()) {
//            HashMap<String, Object> frequencyMap = new HashMap<>();
//            frequencyMap.put("option", frequency);
//            frequencyMap.put("end_date", endDate);
//            updates.put("frequency", frequencyMap);
//        }
        FirebaseFirestore.getInstance().collection("tasks")
                .document(taskId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Task updated", Toast.LENGTH_SHORT).show();
                    finish();
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, "Error updating task", Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmDelete() {
        // Show a confirmation dialog before deleting the task
        new AlertDialog.Builder(this)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete task : " + editTextTaskDescription.getText().toString())
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Delete the task
                    deleteTask();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteTask() {
        // Delete the task from the database using its id
        String taskId = getIntent().getStringExtra("task_id");
        if (taskId != null) {
            // Delete the task from the database using its id
            FirebaseFirestore.getInstance()
                    .collection("tasks")
                    .document(taskId)
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
                        invokeFrequencyTaskDeletedCF(taskId);
                        finish();
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Error deleting task", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void invokeFrequencyTaskDeletedCF(String taskId) {

        if (!taskFrequencyMap.containsKey("option")) {
            return;
        }

        String frequency = (String) taskFrequencyMap.get("option");

        if (frequency != null && frequency.equals("Never")) {
            return;
        }

        if (!taskFrequencyMap.containsKey("id")) {
            return;
        }

        String frequencyTaskId = (String) taskFrequencyMap.get("id");

        if (frequencyTaskId == null) {
            return;
        }

        Log.d("DeleteTaskActivity", "Invoking Cloud Function for frequency task Deleted");

        String urlString = "https://us-central1-weplan-4fbf1.cloudfunctions.net/frequencyTaskDeleted?taskId=" + taskId + "&frequency_id=" + frequencyTaskId;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    Log.d("DeleteFrequencyTask: Delete Frequency Task", "Request URL: " + urlString);
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

                    Log.d("DeleteFrequencyTask: Frequency Task Response", "Response: " + result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void setupImagePreviewClickListener() {
        ImageView imagePreview = findViewById(R.id.imagePreview);
        imagePreview.setOnClickListener(view -> {
            if (imageUri != null) {
                Intent fullScreenIntent = new Intent(this, FullScreenImageActivity.class);
                fullScreenIntent.setData(imageUri);
                startActivity(fullScreenIntent);
            }
        });
    }

    private void setupAddImageButtonClickListener() {
        buttonEditImage.setOnClickListener(v -> selectImage());
    }

    private void selectImage() {
        final CharSequence[] options = {"Take Photo", "Choose from Gallery", "Cancel"};

        AlertDialog.Builder builder = new AlertDialog.Builder(EditTaskActivity.this);
        builder.setTitle("Change Photo!");
        builder.setItems(options, (dialog, item) -> {
            if (options[item].equals("Take Photo")) {
                dispatchTakePictureIntent();
            } else if (options[item].equals("Choose from Gallery")) {
                Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(pickPhoto, REQUEST_IMAGE_PICK);
            } else if (options[item].equals("Cancel")) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void dispatchTakePictureIntent() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
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

    private void createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        photoURI = FileProvider.getUriForFile(this, "edu.northeastern.weplan.provider", image);
    }


//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
//            imageUri = data.getData();
//            Picasso.get().load(imageUri).into(imageTask);
//        } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
//            imageUri = photoURI;
//            Picasso.get().load(photoURI).into(imageTask);
//        }
//    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(this, "Camera permission is required to use camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void uploadImageAndUpdateTask() {
        if (imageUri != null) {
            StorageReference storageRef = FirebaseStorage.getInstance().getReference();
            StorageReference imageRef = storageRef.child("task_images/" + UUID.randomUUID().toString());

            imageRef.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                // Update the task in Firestore with this new image URL
                                updateTaskImageInFirestore(uri.toString());
                            }))
                    .addOnFailureListener(e -> {
                        Log.e("EditTaskActivity", "Image upload failed or image not present", e);
//                        Toast.makeText(this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void updateTaskImageInFirestore(String imageUrl) {
        FirebaseFirestore.getInstance().collection("tasks")
                .document(taskId)
                .update("image", imageUrl)
                .addOnSuccessListener(aVoid -> {
                  Toast.makeText(this, "Task updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error updating task", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
            Picasso.get().load(imageUri).into(imageTask);

        }
        else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            ImageView imagePreview = findViewById(R.id.imagePreview);
            Picasso.get().load(photoURI).into(imagePreview);
            imageUri = photoURI;
        }
}

}

