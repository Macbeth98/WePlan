package edu.northeastern.weplan;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class GroupDetailActivity extends AppCompatActivity implements GroupMemberAdapter.OnMemberClickListener {

    private static GroupEventListener groupEventListener;
    private TextView textViewGroupName, textViewGroupDescription, textViewMemberCount;
    private RecyclerView recyclerViewGroupMembers;
    private ImageButton buttonDeleteGroup, buttonEditGroup;
    private GroupMemberAdapter groupMemberAdapter;

    private List<Member> memberList;
    private Group currentGroup;

    private int groupPosition;

    private String adminEmail;


    private List<Member> usersNotInGroup = new ArrayList<>();

    private boolean fetchedUsersNotInGroup = false;

    public static void setGroupEventListener(GroupsActivity groupsActivity) {
        groupEventListener = groupsActivity;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        currentGroup = getIntent().getParcelableExtra("GROUP_DATA");
        groupPosition = getIntent().getIntExtra("GROUP_POSITION", -1);

        textViewGroupName = findViewById(R.id.groupNameTextView);
        textViewGroupDescription = findViewById(R.id.textViewGroupDescription);
        textViewMemberCount = findViewById(R.id.textViewMemberCount);

        recyclerViewGroupMembers = findViewById(R.id.recyclerViewGroupMembers);
        buttonDeleteGroup = findViewById(R.id.buttonDeleteGroup);
        buttonEditGroup = findViewById(R.id.buttonEditGroup);

        textViewGroupName.setText(currentGroup.getName());
        textViewGroupDescription.setText(currentGroup.getDescription());

        adminEmail = currentGroup.getAdminEmail();

        memberList = new ArrayList<>();

        recyclerViewGroupMembers = findViewById(R.id.recyclerViewGroupMembers);
        recyclerViewGroupMembers.setLayoutManager(new LinearLayoutManager(this));


        groupMemberAdapter = new GroupMemberAdapter(memberList, currentGroup.getAdminEmail());
        recyclerViewGroupMembers.setAdapter(groupMemberAdapter);

        fetchMembers();

        buttonDeleteGroup.setOnClickListener(view -> {
            new AlertDialog.Builder(GroupDetailActivity.this)
                    .setTitle("Delete Group")
                    .setMessage("Are you sure you want to delete this group?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        groupEventListener.onGroupDeleted(currentGroup, groupPosition);
                        finish();
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        });

        buttonEditGroup.setOnClickListener(view -> {
            onEditGroupClicked(view);
        });

        groupMemberAdapter.setOnMemberClickListener(this);

    }

    private void fetchMembers() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Log.d("GroupDetailActivity", "Fetching members for group: " + currentGroup.getId());

        DocumentReference groupRef = db.collection("groups").document(currentGroup.getId());

        db.collection("users")
                        .whereArrayContains("groups", groupRef)
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            memberList.clear();


                            for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                                Member member = documentSnapshot.toObject(Member.class);
                                member.setId(documentSnapshot.getId());
                                if (member.getEmail().equals(adminEmail)) {
                                    memberList.add(0, member);
                                    continue;
                                }
                                memberList.add(member);
                            }

                            textViewMemberCount.setText(String.valueOf(memberList.size()));

                            groupMemberAdapter.notifyDataSetChanged();
                        })
                .addOnFailureListener(e -> Log.d("GroupDetailActivity", "Error: " + e.getMessage()));

    }

    private void updateGroupData(Group group, int position) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", group.getName());
        updates.put("description", group.getDescription());

        db.collection("groups").document(group.getId()).update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d("GroupsActivity", "Group updated successfully");

                    groupEventListener.onGroupUpdated(group, position);

                    Snackbar.make(findViewById(android.R.id.content),
                            "Group: " + group.getName() + " updated successfully", Snackbar.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    Log.d("GroupsActivity", "Failed to update group", e);
                    Snackbar.make(findViewById(android.R.id.content),
                            "Failed to update group: " + group.getName(), Snackbar.LENGTH_LONG).show();
                });
    }

    public void onEditGroupClicked(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_group, null);
        builder.setView(dialogView);

        final EditText editTextGroupName = dialogView.findViewById(R.id.editTextGroupName);
        final EditText editTextGroupDescription = dialogView.findViewById(R.id.editTextGroupDescription);

        editTextGroupName.setText(currentGroup.getName());
        editTextGroupDescription.setText(currentGroup.getDescription());

        builder.setPositiveButton("Edit", (dialog, which) -> {
            String groupName = editTextGroupName.getText().toString();
            String groupDescription = editTextGroupDescription.getText().toString();
            if (!groupName.isEmpty() && !groupDescription.isEmpty()) {
                currentGroup.setName(groupName);
                currentGroup.setDescription(groupDescription);
                textViewGroupName.setText(groupName);
                textViewGroupDescription.setText(groupDescription);
                updateGroupData(currentGroup, groupPosition);
            } else {
                Snackbar.make(findViewById(android.R.id.content), "Please Enter All the Values!", Snackbar.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void getListOfUsersNotInGroup() {
        usersNotInGroup.clear();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference groupRef = db.collection("groups").document(currentGroup.getId());

        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                        Member user = documentSnapshot.toObject(Member.class);
                        user.setId(documentSnapshot.getId());
                        Log.d("GroupDetailActivity", "User: " + user.getName());
                        List<DocumentReference> groups = (List<DocumentReference>) documentSnapshot.get("groups");
                        if (groups == null) {
                            usersNotInGroup.add(user);
                            continue;
                        }
                        if (!groups.contains(groupRef)) {
                            usersNotInGroup.add(user);
                        }
                    }
                    fetchedUsersNotInGroup = true;
                    showAddMemberDialog();
                })
                .addOnFailureListener(e -> {
                    Log.d("GroupDetailActivity", "Error: getListOfUsersNotInGroup" + e.getMessage());
                });
    }

    private void addMemberToGroup(Member member) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference groupRef = db.collection("groups").document(currentGroup.getId());

        db.collection("users").document(member.getId())
                .update("groups", FieldValue.arrayUnion(groupRef))
                .addOnSuccessListener(aVoid -> {
                    Log.d("GroupDetailActivity", "User groups updated successfully");
                    memberList.add(1, member);
                    textViewMemberCount.setText(String.valueOf(memberList.size()));
                    groupMemberAdapter.notifyItemInserted(1);
                    usersNotInGroup.remove(member);
                    Snackbar.make(findViewById(android.R.id.content),
                            "Member: " + member.getName() + " added successfully", Snackbar.LENGTH_LONG).show();

                    sendAddUserNotification(member, currentGroup);
                })
                .addOnFailureListener(e -> {
                    Log.d("GroupDetailActivity", "Failed to update user groups", e);
                    Snackbar.make(findViewById(android.R.id.content),
                            "Failed to add member: " + member.getName(), Snackbar.LENGTH_LONG).show();
                });
    }



    private void showAddMemberDialog() {
        List<Member> userList = this.usersNotInGroup;
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_member, null);

        EditText editTextUserFilter = dialogView.findViewById(R.id.editTextUserFilter);
        Spinner spinnerUserList = dialogView.findViewById(R.id.spinnerUserList);

        MemberFilterAdapter adapter = new MemberFilterAdapter(this, userList);
        spinnerUserList.setAdapter(adapter);

        editTextUserFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                adapter.getFilter().filter(charSequence);
            }

            @Override
            public void afterTextChanged(Editable editable) {}

        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView)
                .setTitle("Add Member")
                .setPositiveButton("Add", (dialog, id) -> {
                    Log.d("GroupDetailActivity", "Add Member Clicked");
                    Member selectedUser = (Member) spinnerUserList.getSelectedItem();
                    Log.d("GroupDetailActivity", "Selected User: " + selectedUser.getName());
                    addMemberToGroup(selectedUser);
                })
                .setNegativeButton("Cancel", (dialog, id) -> dialog.cancel())
                .create().show();
    }


    public void onAddGroupMemberClicked(View view) {
        Log.d("GroupDetailActivity", "Add Group Member Clicked");
        if (fetchedUsersNotInGroup) {
            showAddMemberDialog();
        } else {
            getListOfUsersNotInGroup();
        }
    }

    @Override
    public void onDeleteClick(Member member, int position) {
        Log.d("GroupDetailActivity", "Delete Member Clicked");
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference groupRef = db.collection("groups").document(currentGroup.getId());

        db.collection("users").document(member.getId())
                .update("groups", FieldValue.arrayRemove(groupRef))
                .addOnSuccessListener(aVoid -> {
                    Log.d("GroupDetailActivity", "User groups updated successfully");
                    memberList.remove(position);
                    textViewMemberCount.setText(String.valueOf(memberList.size()));
                    groupMemberAdapter.notifyItemRemoved(position);
                    Snackbar.make(findViewById(android.R.id.content),
                            "Member: " + member.getName() + " removed successfully", Snackbar.LENGTH_LONG).show();
                    sendUserDeleteNotification(member, currentGroup);
                })
                .addOnFailureListener(e -> {
                    Log.d("GroupDetailActivity", "Failed to update user groups", e);
                    Snackbar.make(findViewById(android.R.id.content),
                            "Failed to remove member: " + member.getName(), Snackbar.LENGTH_LONG).show();
                });
    }

    private void sendAddUserNotification(Member member, Group group) {
        String urlString = "https://us-central1-weplan-4fbf1.cloudfunctions.net/onUserAddedToGroup?userId=" + member.getId() + "&groupId=" + group.getId();
        sendGroupUserNotification(urlString);
    }

    private void sendUserDeleteNotification(Member member, Group group) {
        String urlString = "https://us-central1-weplan-4fbf1.cloudfunctions.net/onUserRemovedFromGroup?userId=" + member.getId() + "&groupId=" + group.getId();
        sendGroupUserNotification(urlString);
    }

    private void sendGroupUserNotification(String urlString) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    Log.d("GroupDetails: Add User Notification Request", "Request URL: " + urlString);
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

                    Log.d("GroupDetails: Add user Notification Response", "Response: " + result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}