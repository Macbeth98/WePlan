package edu.northeastern.weplan;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class WeekViewFragment extends Fragment implements OnCheckboxClickListener{

    public static WeekViewFragment instance;

    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private FirebaseFirestore firestore;

    private Map<String, Task> allTasks = new HashMap<>();

    private Spinner spinnerGroupFilter;

    private ArrayAdapter<String> groupFilterAdapter;

    private List<String> groupNames = new ArrayList<>();

    private Map<String, String> groupNameToIdMap = new HashMap<>();

    private TextView textViewCurrentWeek;

    private boolean showCompletedTasks = false;
    private Calendar currentWeek;

    private Set<String> userGroupIds = new HashSet<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        firestore = FirebaseFirestore.getInstance();
        fetchUserGroups();
        instance = this;
    }

    public static WeekViewFragment getInstance() {
        return instance;
    }

    public void fetchAndRefreshView() {
        Log.d("WeekViewFragment", "fetchAndRefreshView");
        fetchUserGroups();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_week_view, container, false);
        currentWeek = Calendar.getInstance();
        textViewCurrentWeek = view.findViewById(R.id.textViewCurrentWeek);
        updateWeekDisplay();
        view.findViewById(R.id.buttonPrevWeek).setOnClickListener(v -> changeWeek(-1));
        view.findViewById(R.id.buttonNextWeek).setOnClickListener(v -> changeWeek(1));

        recyclerView = view.findViewById(R.id.recycler_view_week_tasks);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        taskAdapter = new TaskAdapter(new ArrayList<>(), this::navigateToEditTask, this);
        recyclerView.setAdapter(taskAdapter);

        spinnerGroupFilter = view.findViewById(R.id.spinnerGroupFilter);
        groupFilterAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, groupNames);
        groupFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGroupFilter.setAdapter(groupFilterAdapter);

        spinnerGroupFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String selectedGroup = groupNames.get(i);
                Log.d("WeekViewFragment", "selectedGroupName: " + selectedGroup);
                filterTasks(selectedGroup);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }});
        spinnerGroupFilter.setEnabled(false);
        fetchGroups();
        fetchTasks();
        return view;
    }

    private void fetchGroups() {
        List<Group> groups =  DataRepository.getInstance().getGroups();

        groupNames.clear();
        groupNameToIdMap.clear();

        groupNames.add("All");
        groupNameToIdMap.put("All", null);



        for (Group group: groups) {
            groupNames.add(group.getName());
            groupNameToIdMap.put(group.getName(), group.getId());
        }

        groupFilterAdapter.notifyDataSetChanged();
        spinnerGroupFilter.setEnabled(true);
    }

    private void fetchUserGroups() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        firestore.collection("users").document(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    List<DocumentReference> groupRefs = (List<DocumentReference>) documentSnapshot.get("groups");
                    if (groupRefs != null) {
                        userGroupIds = new HashSet<>();
                        userGroupIds.add(FirebaseAuth.getInstance().getCurrentUser().getUid());
                        for (DocumentReference groupRef : groupRefs) {
                            userGroupIds.add(groupRef.getId());
                        }
                    } else {
                        userGroupIds = new HashSet<>();
                        userGroupIds.add(FirebaseAuth.getInstance().getCurrentUser().getUid());
                    }
                    fetchGroups();
                    fetchTasks();
                })
                .addOnFailureListener(e -> {
                    Log.e("WeekViewFragment", "Error fetching user groups", e);

                });
    }

    private void fetchTasks() {
//        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (!userGroupIds.isEmpty()) {
            firestore.collection("tasks")
                    .whereIn("group_id", Arrays.asList(userGroupIds.toArray()))
                    .get()
                    .addOnCompleteListener(groupTask -> {
                        if (!groupTask.isSuccessful()) {
                            Log.w("WeekViewFragment", "Error getting group tasks: ", groupTask.getException());
                            return;
                        }
                        allTasks.clear();
                        for (QueryDocumentSnapshot document : groupTask.getResult()) {
                            addTaskIfRelevant(document);
                        }

                        updateFilteredTasks();
                    });
        }
//        firestore.collection("tasks")
//                .whereEqualTo("assigned_to", currentUserId)
//                .get()
//                .addOnCompleteListener(task -> {
//                    if (!task.isSuccessful()) {
//                        Log.w("WeekViewFragment", "Error getting documents: ", task.getException());
//                        return;
//                    }
//
//                    if (userGroupIds.isEmpty()) {
//                        allTasks.clear();
//                    }
//
//                    for (QueryDocumentSnapshot document : task.getResult()) {
//                        addTaskIfRelevant(document);
//                    }
//
//                    if (userGroupIds.isEmpty()) {
//                        updateFilteredTasks();
//                        return;
//                    }
//                });
    }

    private void addTaskIfRelevant(QueryDocumentSnapshot document) {
        try {
            Task taskItem = document.toObject(Task.class);
            if (taskItem != null && isTaskForThisWeek(taskItem) && !allTasks.containsKey(taskItem.getTask_id())) {
                taskItem.setTask_id(document.getId());
                allTasks.put(taskItem.getTask_id(), taskItem);
            }
        } catch (Exception e) {
            Log.e("DayViewFragment", "Error converting document to task", e);
        }
    }

    private void updateFilteredTasks() {
        if (spinnerGroupFilter.getSelectedItem() != null) {
            String selectedGroupName = spinnerGroupFilter.getSelectedItem().toString();
            filterTasks(selectedGroupName);
        } else {
            filterTasks("All");
        }
    }

    private void filterTasks(String groupNameSelected) {
        List<Task> filteredTasks = new ArrayList<>();
//        for (Task task: allTasks) {
//            if ("All".equals(groupNameSelected) || (groupNameSelected.equals(task.getGroup_id()))) {
//                if (showCompletedTasks && task.getIs_completed() ||
//                        !showCompletedTasks && !task.getIs_completed()) {
//                    filteredTasks.add(task);
//                }
//            }
//
//        }
        for (Task task: allTasks.values()) {
            if ("All".equals(groupNameSelected) || groupNameToIdMap.get(groupNameSelected).equals(task.getGroup_id())) {
                if (showCompletedTasks && task.getIs_completed() ||
                        !showCompletedTasks && !task.getIs_completed()) {
                    filteredTasks.add(task);
                }
            }

        }
        taskAdapter.updateTasks(filteredTasks);
    }

    private Calendar[] getWeekStartAndEndDates(Calendar date) {
        Calendar start = (Calendar) date.clone();
        start.set(Calendar.DAY_OF_WEEK, start.getFirstDayOfWeek());
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_WEEK, 6);
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);

        return new Calendar[]{start, end};
    }
    private void changeWeek(int amount) {
        currentWeek.add(Calendar.WEEK_OF_YEAR, amount);
        updateWeekDisplay();
        fetchTasks(); // Refresh the task list based on the new week
    }

    private void updateWeekDisplay() {
        SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        Calendar[] weekStartEnd = getWeekStartAndEndDates(currentWeek);
        String weekDisplay = formatter.format(weekStartEnd[0].getTime()) + " - " + formatter.format(weekStartEnd[1].getTime());
        textViewCurrentWeek.setText("Week of: " + weekDisplay);
    }

    private boolean isTaskForThisWeek(Task task) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar[] weekStartEnd = getWeekStartAndEndDates(currentWeek);

        Date taskDate;
        try {
            taskDate = sdf.parse(task.getDue_date());
        } catch (ParseException e) {
            Log.e("WeekViewFragment", "Error parsing task due date", e);
            return false;
        }

        return taskDate != null && taskDate.after(weekStartEnd[0].getTime()) && taskDate.before(weekStartEnd[1].getTime());
    }



    private void navigateToEditTask(Task task) {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), EditTaskActivity.class);
        intent.putExtra("task_id", task.getTask_id());
        startActivity(intent);
    }
    @Override
    public void onResume() {
        super.onResume();
        fetchTasks();
    }

    public void setShowCompletedTasks(boolean show) {
        this.showCompletedTasks = show;
        if (allTasks.isEmpty()) {
            fetchTasks();
        } else {
            updateFilteredTasks();
        }
    }

    private void filterTasks() {
        List<Task> filteredTasks = new ArrayList<>();
//        for (Task task : allTasks) {
//            if (showCompletedTasks && task.getIs_completed() ||
//                    !showCompletedTasks && !task.getIs_completed()) {
//                filteredTasks.add(task);
//            }
//        }
        for (Task task : allTasks.values()) {
            if (showCompletedTasks && task.getIs_completed() ||
                    !showCompletedTasks && !task.getIs_completed()) {
                filteredTasks.add(task);
            }
        }
        taskAdapter.updateTasks(filteredTasks);
    }
    @Override
    public void onCheckboxClick(String taskId, boolean isCompleted) {
        fetchTasks();
    }
}
