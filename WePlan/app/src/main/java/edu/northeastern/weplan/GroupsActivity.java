package edu.northeastern.weplan;

import static java.lang.Integer.parseInt;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class GroupsActivity extends AppCompatActivity implements GroupEventListener {

    private RecyclerView recyclerViewGroups;
    private GroupAdapter groupAdapter;
    private List<Group> groupList;

    private User userData;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups);

        userData = DataRepository.getInstance().getUserData();

        if (userData != null) {
            groupList = DataRepository.getInstance().getGroups();
            Log.d("GroupsActivity", "User data is not null: " + groupList.size());
        } else {
            groupList = new ArrayList<>();
        }

        Log.d("GroupsActivity", groupList.toString());

        recyclerViewGroups = findViewById(R.id.recyclerViewGroups);
        recyclerViewGroups.setLayoutManager(new LinearLayoutManager(this));

        groupAdapter = new GroupAdapter(groupList);
        recyclerViewGroups.setAdapter(groupAdapter);

        groupAdapter.setOnGroupClickListener((group, position) -> {
            Intent intent = new Intent(this, GroupDetailActivity.class);

            if (group.getId() == null) {
                Log.d("GroupsActivity", "Group id is null");
                group = DataRepository.getInstance().getGroups().get(position);
                Log.d("GroupsActivity", "Group id should not be null now: " + group.getId());
            }

            intent.putExtra("GROUP_DATA", group);
            intent.putExtra("GROUP_POSITION", position);

            GroupDetailActivity.setGroupEventListener(this);

            startActivity(intent);
        });

        groupAdapter.notifyDataSetChanged();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.d("GroupsActivity", "Saving user data to savedInstanceState");
        Log.d("GroupsActivity", userData.toString());
        Log.d("groupsList", groupList.toString());

        outState.putParcelable("USER_DATA", userData);
    }

    private void fetchGroups() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("groups")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot snapshots,
                                        @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            // Handle error
                            Log.d("GroupsActivity", "Error: " + e.getMessage());
                            return;
                        }

                        groupList.clear();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Log.d("GroupsActivity doc", doc.getId() + " => " + doc.getData());
                            Group group = doc.toObject(Group.class);
                            groupList.add(group);
                        }

                        groupAdapter.notifyDataSetChanged();
                    }
                });
    }


    public void onAddGroupClicked(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_group, null);
        builder.setView(dialogView);

        final EditText editTextGroupName = dialogView.findViewById(R.id.editTextGroupName);
        final EditText editTextGroupDescription = dialogView.findViewById(R.id.editTextGroupDescription);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String groupName = editTextGroupName.getText().toString();
            String groupDescription = editTextGroupDescription.getText().toString();
            if (!groupName.isEmpty() && !groupDescription.isEmpty()) {
                createGroup(groupName, groupDescription);
            } else {
                // Show error message
                Snackbar.make(findViewById(android.R.id.content), "Please Enter All the Values!", Snackbar.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateUserGroups(DocumentReference groupReference) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(userData.getId())
                .update("groups", FieldValue.arrayUnion(groupReference))
                .addOnSuccessListener(aVoid -> {
                    Log.d("GroupsActivity", "User groups updated successfully");
                    DataRepository.getInstance().refreshTaskActivityView();
                })
                .addOnFailureListener(e -> Log.d("GroupsActivity", "Failed to update user groups", e));
    }

    private void updateGroupList (Group group) {
        groupList.add(0, group);
        groupAdapter.notifyItemInserted(0);

        recyclerViewGroups.post(new Runnable() {
            @Override
            public void run() {
                recyclerViewGroups.smoothScrollToPosition(0);
            }
        });

        DataRepository.getInstance().setGroups(groupList);
        userData = DataRepository.getInstance().getUserData();
    }

    private void createGroup(String groupName, String groupDescription) {
        Group newGroup = new Group();
        newGroup.setName(groupName);
        newGroup.setDescription(groupDescription);
        newGroup.setAdminId(userData.getId());
        newGroup.setTimestamp(parseInt(String.valueOf(System.currentTimeMillis()/1000)));

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("groups").add(newGroup)
                .addOnSuccessListener(documentReference -> {
                    newGroup.setId(documentReference.getId());

                    updateGroupList(newGroup);

                    updateUserGroups(documentReference);

                    Snackbar.make(findViewById(android.R.id.content), "Group created successfully", Snackbar.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    // Handle failure
                    Snackbar.make(findViewById(android.R.id.content), "Failed to create group", Snackbar.LENGTH_LONG).show();
                });
    }

    private void updateUserGroupsListDb(Group group) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference groupRef = db.collection("groups").document(group.getId());

        db.collection("users")
                .whereArrayContains("groups", groupRef)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                        removeFromUserGroups(documentSnapshot.getReference(), groupRef);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.d("GroupsActivity", "Error:updateUserGroupsListDb " + e.getMessage());
                });
    }

    private void removeFromUserGroups(DocumentReference userRef, DocumentReference groupRef) {
        userRef.update("groups", FieldValue.arrayRemove(groupRef))
                .addOnSuccessListener(aVoid -> {
                    Log.d("GroupsActivity", "User groups updated successfully");
                    DataRepository.getInstance().refreshTaskActivityView();
                })
                .addOnFailureListener(e -> Log.d("GroupsActivity", "Failed to update user groups", e));
    }

    private void deleteTasksForTheGroup (Group group) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("tasks")
                .whereEqualTo("group_id", group.getId())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                        AppFirebaseMessagingService.unsubscribeFromTopic(documentSnapshot.getId());
                        deleteTask(documentSnapshot.getReference());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.d("GroupsActivity", "Error:deleteTasksForTheGroup " + e.getMessage());
                });
    }

    private void deleteTask(DocumentReference taskRef) {
        taskRef.delete()
                .addOnSuccessListener(aVoid -> Log.d("GroupsActivity", "Task deleted successfully"))
                .addOnFailureListener(e -> Log.d("GroupsActivity", "Failed to delete task", e));
    }

    @Override
    public void onGroupDeleted(Group group, int position) {
        Log.d("GroupsActivity", "Group deleted: " + group.getId());
        Log.d("GroupsActivity", "Group deleted: " + group.getName());
        Log.d("GroupsActivity", "Group deleted at position: " + position);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("groups").document(group.getId()).delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d("GroupsActivity", "Group deleted successfully");
                    AppFirebaseMessagingService.unsubscribeFromTopic(group.getId());

                    updateUserGroupsListDb(group);
                    deleteTasksForTheGroup(group);

                    groupList.remove(position);
                    groupAdapter.notifyItemRemoved(position);

                    Snackbar.make(findViewById(android.R.id.content),
                            "Group: " + group.getName() + " deleted successfully", Snackbar.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    Log.d("GroupsActivity", "Failed to delete group", e);
                    Snackbar.make(findViewById(android.R.id.content),
                            "Failed to delete group: " + group.getName(), Snackbar.LENGTH_LONG).show();
                });


    }

    @Override
    public void onGroupUpdated(Group group, int position) {
        Log.d("GroupsActivity", "Group updated: " + group.getName());
        Log.d("GroupsActivity", "Group updated at position: " + position);

        groupList.set(position, group);
        groupAdapter.notifyItemChanged(position);
    }
}