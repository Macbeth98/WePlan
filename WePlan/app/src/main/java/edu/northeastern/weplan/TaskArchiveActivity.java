package edu.northeastern.weplan;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TaskArchiveActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_archive);
        ListView listView = findViewById(R.id.archive_list);
        String[] dummyNotes = {"Note 1", "Note 2", "Note 3", "Note 4"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dummyNotes);
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                // Code to remove the note from the list and update the adapter
                String[] dummyNotes = {"Note 1", "Note 2", "Note 3", "Note 4"};
                List<String> list = new ArrayList<>(Arrays.asList(dummyNotes));
                list.remove(position);
                dummyNotes = list.toArray(new String[0]);
                adapter.notifyDataSetChanged();
                return true;
            }
        });
    }
}