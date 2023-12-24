package edu.northeastern.weplan;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;

public class FeedbackActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        LinearLayout layout = findViewById(R.id.feedback_layout);

        TextView feedbackLabel = new TextView(this);
        feedbackLabel.setText("Your Feedback");
        layout.addView(feedbackLabel);

        EditText feedbackEdit = new EditText(this);
        feedbackEdit.setHint("Enter your feedback here");
        layout.addView(feedbackEdit);

        Button submitButton = new Button(this);
        submitButton.setText("Submit Feedback");
        layout.addView(submitButton);

        RatingBar ratingBar = new RatingBar(this);
        ratingBar.setNumStars(5);
        layout.addView(ratingBar);

        TextView contactLabel = new TextView(this);
        contactLabel.setText("Contact Info (Optional)");
        layout.addView(contactLabel);

        EditText contactEdit = new EditText(this);
        contactEdit.setHint("Enter your email or phone number");
        layout.addView(contactEdit);

        Spinner categorySpinner = new Spinner(this);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.feedback_categories, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);
        layout.addView(categorySpinner);
    }
}