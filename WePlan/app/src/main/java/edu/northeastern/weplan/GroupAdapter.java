package edu.northeastern.weplan;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Map;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.ViewHolder> {

    private List<Group> groupList;

    public GroupAdapter(List<Group> groupList) {
        this.groupList = groupList;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Group group = groupList.get(position);
        holder.textViewGroupName.setText(group.getName());
        holder.textViewGroupDescription.setText(group.getDescription());
        String adminId = group.getAdminId();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(adminId).get().addOnSuccessListener(documentSnapshot -> {
            Map admin = documentSnapshot.getData();
            String adminName = admin.get("name").toString();
            holder.textViewGroupAdmin.setText("Admin: " + adminName);
            String adminEmail = admin.get("email").toString();
            group.setAdminEmail(adminEmail);
        }).addOnFailureListener(e -> holder.textViewGroupAdmin.setText("unknown"));

    }

    @Override
    public int getItemCount() {
        return groupList.size();
    }

    public interface OnGroupClickListener {
        void onGroupClick(Group group, int position);
    }

    private OnGroupClickListener listener;

    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView textViewGroupName;

        public TextView textViewGroupDescription;

        public TextView textViewGroupAdmin;

        public ViewHolder(View itemView) {
            super(itemView);
            textViewGroupName = itemView.findViewById(R.id.textViewGroupName);

            textViewGroupDescription = itemView.findViewById(R.id.textViewGroupDescription);

            textViewGroupAdmin = itemView.findViewById(R.id.textViewGroupAdmin);

            itemView.setOnClickListener(view -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onGroupClick(groupList.get(position), position);
                }
            });
        }
    }

    public void setOnGroupClickListener(OnGroupClickListener listener) {
        this.listener = listener;
    }


}

