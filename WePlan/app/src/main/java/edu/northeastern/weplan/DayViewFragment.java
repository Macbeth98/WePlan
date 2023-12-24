package edu.northeastern.weplan;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DayViewFragment extends Fragment implements OnCheckboxClickListener {

    private static DayViewFragment instance;

    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private FirebaseFirestore firestore;

    private Map<String, Task> allTasks = new HashMap<>();
    private Spinner spinnerGroupFilter;
    private ArrayAdapter<String> groupFilterAdapter;
    private List<String> groupNames = new ArrayList<>();
    private Map<String, String> groupNameToIdMap = new HashMap<>();

    private boolean showCompletedTasks = false;
    private Set<String> userGroupIds = new HashSet<>();

    private int selectedGroupPosition = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        firestore = FirebaseFirestore.getInstance();
        fetchUserGroups();
        instance = this;
        // databaseReference = FirebaseDatabase.getInstance().getReference("tasks");
    }

    // private void fetchUserGroups() {
    // String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    // firestore.collection("users").document(currentUserId).get()
    // .addOnSuccessListener(documentSnapshot -> {
    // List<DocumentReference> groupRefs = (List<DocumentReference>)
    // documentSnapshot.get("groups");
    // if (groupRefs != null) {
    // userGroupIds = new ArrayList<>();
    // for (DocumentReference groupRef : groupRefs) {
    // // Extract the group ID from the DocumentReference
    // String groupId = groupRef.getId();
    // userGroupIds.add(groupId);
    // }
    // } else {
    // userGroupIds = new ArrayList<>();
    // }
    // fetchGroups();
    // })
    // .addOnFailureListener(e -> {
    // Log.e("WeekViewFragment", "Error fetching user groups", e);
    // });
    // }

    public static DayViewFragment getInstance() {
        return instance;
    }

    public void fetchAndRefreshView() {
        Log.d("DayViewFragment", "fetchAndRefreshView");
        fetchUserGroups();
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
                    Log.e("DayViewFragment", "Error fetching user groups", e);

                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_day_view, container, false);
        recyclerView = view.findViewById(R.id.recycler_view_tasks);
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
                Log.d("DayViewFragment", "selectedGroupName: " + selectedGroup);
                filterTasks(selectedGroup);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        spinnerGroupFilter.setEnabled(false); // Initially disabled
        fetchGroups();
        fetchTasks();

        return view;
    }

    private void fetchGroups() {
        List<Group> groups = DataRepository.getInstance().getGroups();

        groupNames.clear();
        groupNameToIdMap.clear();

        groupNames.add("All");
        groupNameToIdMap.put("All", null);

        for (Group group : groups) {
            groupNames.add(group.getName());
            groupNameToIdMap.put(group.getName(), group.getId());
        }

        groupFilterAdapter.notifyDataSetChanged();
        spinnerGroupFilter.setEnabled(true);
    }

    private void fetchTasks() {
        // String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (!userGroupIds.isEmpty()) {
            firestore.collection("tasks")
                    .whereIn("group_id", Arrays.asList(userGroupIds.toArray()))
                    .get()
                    .addOnCompleteListener(groupTask -> {
                        if (!groupTask.isSuccessful()) {
                            Log.w("DayViewFragment", "Error getting group tasks: ", groupTask.getException());
                            return;
                        }
                        allTasks.clear();
                        for (QueryDocumentSnapshot document : groupTask.getResult()) {
                            addTaskIfRelevant(document);
                        }

                        updateFilteredTasks();
                    });
        }
        // firestore.collection("tasks")
        // .whereEqualTo("assigned_to", currentUserId)
        // .get()
        // .addOnCompleteListener(task -> {
        // if (!task.isSuccessful()) {
        // Log.w("DayViewFragment", "Error getting documents: ", task.getException());
        // return;
        // }
        //
        // if (userGroupIds.isEmpty()) {
        // allTasks.clear();
        // }
        //
        // for (QueryDocumentSnapshot document : task.getResult()) {
        // addTaskIfRelevant(document);
        // }
        //
        // if (userGroupIds.isEmpty()) {
        // updateFilteredTasks();
        // return;
        // }
        // });
    }

    private void addTaskIfRelevant(QueryDocumentSnapshot document) {
        try {
            Task taskItem = document.toObject(Task.class);
            if (taskItem != null && isTaskForToday(taskItem) && !allTasks.containsKey(taskItem.getTask_id())) {
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
        Log.d("DayViewFragment", "filterTasks: " + groupNameSelected);
        Log.d("DayViewFragment", "filterTasks: " + allTasks.size());
        List<Task> filteredTasks = new ArrayList<>();
        // for (Task task: allTasks) {
        // if ("All".equals(groupNameSelected) ||
        // (groupNameSelected.equals(task.getGroup_id()))) {
        // if (showCompletedTasks && task.getIs_completed() ||
        // !showCompletedTasks && !task.getIs_completed()) {
        // filteredTasks.add(task);
        // }
        // }
        //
        // }
        // change to map
        for (Map.Entry<String, Task> entry : allTasks.entrySet()) {
            Task task = entry.getValue();
            if ("All".equals(groupNameSelected) || groupNameToIdMap.get(groupNameSelected).equals(task.getGroup_id())) {
                if (showCompletedTasks && task.getIs_completed() ||
                        !showCompletedTasks && !task.getIs_completed()) {
                    filteredTasks.add(task);
                }
            }
        }
        taskAdapter.updateTasks(filteredTasks);
    }

    private boolean isTaskForToday(Task task) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date taskDate;
        try {
            taskDate = sdf.parse(task.getDue_date());
        } catch (ParseException e) {
            Log.e("DayViewFragment", "Error parsing task due date", e);
            return false;
        }

        String todayStr = sdf.format(new Date());
        Date today;
        try {
            today = sdf.parse(todayStr);
        } catch (ParseException e) {
            Log.e("DayViewFragment", "Error parsing today's date", e);
            return false;
        }

        return taskDate != null && today != null && taskDate.compareTo(today) == 0;
    }

    public Intent getEditTaskActivityIntent(Task task) {
        if (getActivity() == null)
            return null;
        Intent intent = new Intent(getActivity(), EditTaskActivity.class);
        intent.putExtra("task_id", task.getTask_id());
        intent.putExtra("description", task.getDescription());
        intent.putExtra("due_date", task.getDue_date());
        intent.putExtra("notes", task.getNotes());
        intent.putExtra("image", task.getImage());
        return intent;
    }

    private void navigateToEditTask(Task task) {
        Intent intent = getEditTaskActivityIntent(task);
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
        Log.d("DayViewFragment", "filterTasks No param: " + allTasks.size());
        List<Task> filteredTasks = new ArrayList<>();
        // for (Task task : allTasks) {
        // if (showCompletedTasks && task.getIs_completed() ||
        // !showCompletedTasks && !task.getIs_completed()) {
        // filteredTasks.add(task);
        // }
        // }

        for (Map.Entry<String, Task> entry : allTasks.entrySet()) {
            Task task = entry.getValue();
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
        // filterTasks();
    }
}
