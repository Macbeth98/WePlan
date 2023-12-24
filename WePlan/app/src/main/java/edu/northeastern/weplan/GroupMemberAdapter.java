package edu.northeastern.weplan;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class GroupMemberAdapter extends RecyclerView.Adapter<GroupMemberAdapter.ViewHolder> {

    private List<Member> memberList;

    private String adminEmail;

    public GroupMemberAdapter(List<Member> memberList, String adminEmail) {
        this.memberList = memberList;
        this.adminEmail = adminEmail;
    }

    @NonNull
    @Override
    public GroupMemberAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_member, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Member member = memberList.get(position);
        holder.textViewMemberName.setText(member.getName());
        holder.textViewMemberEmail.setText(member.getEmail());
        if (member.getEmail().equals(adminEmail)) {
            holder.buttonDeleteMember.setVisibility(View.GONE);
            holder.textViewGroupDetailAdmin.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return memberList.size();
    }

    public interface OnMemberClickListener {
        void onDeleteClick(Member member, int position);
    }

    private GroupMemberAdapter.OnMemberClickListener listener;

    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView textViewMemberName;
        public TextView textViewMemberEmail;

        public TextView textViewGroupDetailAdmin;

        public ImageButton buttonDeleteMember;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewMemberName = itemView.findViewById(R.id.textViewMemberName);
            textViewMemberEmail = itemView.findViewById(R.id.textViewMemberEmail);
            textViewGroupDetailAdmin = itemView.findViewById(R.id.textViewGroupDetailAdmin);
            buttonDeleteMember = itemView.findViewById(R.id.buttonDeleteMember);

            buttonDeleteMember.setOnClickListener(v -> {
                int position = getAdapterPosition();
                Member member = memberList.get(position);
                if (position != RecyclerView.NO_POSITION) {
                    listener.onDeleteClick(member, position);
                }
//                Log.d("GroupMemberAdapter", member.toString());
//                Group group = DataRepository.getInstance().getGroupData();
//                Log.d("GroupMemberAdapter", group.toString());
//                group.removeMember(member);
//                DataRepository.getInstance().updateGroup(group);
//                memberList.remove(position);
//                notifyItemRemoved(position);
//                notifyItemRangeChanged(position, memberList.size());
            });
        }
    }

    public void setOnMemberClickListener(GroupMemberAdapter.OnMemberClickListener listener) {
        this.listener = listener;
    }
}
