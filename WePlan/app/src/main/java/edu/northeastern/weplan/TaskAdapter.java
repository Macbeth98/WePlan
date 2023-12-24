package edu.northeastern.weplan;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.squareup.picasso.Picasso;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> taskList;
    private OnTaskClickListener listener;
    private OnCheckboxClickListener checkboxClickListener;

    private ImageView taskImagePreview;
    private boolean isBinding = false; // Flag to identify when binding is happening


    public TaskAdapter(List<Task> taskList, OnTaskClickListener listener, OnCheckboxClickListener checkboxClickListener) {
        this.taskList = taskList;
        this.listener = listener;
        this.checkboxClickListener = checkboxClickListener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view, this);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = taskList.get(position);
        holder.bind(task);
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    public void updateTasks(List<Task> newTaskList) {
        taskList.clear();
        taskList.addAll(newTaskList);
        notifyDataSetChanged();
    }

    public void onTaskCompletionChanged(int position, boolean isCompleted) {
        Task task = taskList.get(position);
        task.setIs_completed(isCompleted);
        FirebaseFirestore.getInstance()
                .collection("tasks")
                .document(task.getTask_id())
                .update("is_completed", isCompleted)
                .addOnSuccessListener(aVoid -> {
                    if (checkboxClickListener != null) {
                        checkboxClickListener.onCheckboxClick(task.getTask_id(), isCompleted);
                    }
                    notifyItemChanged(position);
                }).addOnFailureListener(e -> {
                    task.setIs_completed(!isCompleted);
                    notifyItemChanged(position);
                });

    }


    class TaskViewHolder extends RecyclerView.ViewHolder {
        private TextView taskDescription;
        TextView assignedUser;
        TextView dueDate;
        private CheckBox taskCompleted;
        private CardView cardView;

        public TaskViewHolder(View itemView, TaskAdapter adapter) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view_task);
            taskDescription = itemView.findViewById(R.id.task_description);
            taskCompleted = itemView.findViewById(R.id.task_completed);
            assignedUser = itemView.findViewById(R.id.task_assigned_user);
            dueDate = itemView.findViewById(R.id.task_due_date);
//            taskImagePreview = itemView.findViewById(R.id.task_image_preview);

            taskCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int position = getAdapterPosition();
                if (!isBinding && position != RecyclerView.NO_POSITION) {
                    adapter.onTaskCompletionChanged(getAdapterPosition(), isChecked);
                }
            });
        }

        private void fetchUserName(String userId) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                    //donot cast the object just use the name from the document snapshot
                        String name = documentSnapshot.getString("name");
                        assignedUser.setText("Assigned to: " + name);
                    })
                    .addOnFailureListener(e -> Log.e("TaskAdapter", "Error fetching user", e));
        }

        private String formatDate(String rawDate) {
            try {
                SimpleDateFormat originalFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                SimpleDateFormat targetFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                Date date = originalFormat.parse(rawDate);
                return targetFormat.format(date);
            } catch (ParseException e) {
                Log.e("TaskAdapter", "Error parsing date", e);
                return rawDate;
            }
        }


        public void bind(Task task) {
            isBinding = true;
            taskDescription.setText(task.getDescription());
            taskCompleted.setChecked(task.getIs_completed());
            fetchUserName(task.getAssigned_to());
            String formattedDate = formatDate(task.getDue_date());
            dueDate.setText("Due Date: " + formattedDate);
            Date currentDate = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            try {
                Date taskDueDate = sdf.parse(task.getDue_date());
                if (taskDueDate != null && taskDueDate.before(currentDate)) {
                    if (task.getIs_completed()) {
                        cardView.setCardBackgroundColor(itemView.getContext().getResources().getColor(R.color.completed_task_color));
                    } else {
                        cardView.setCardBackgroundColor(itemView.getContext().getResources().getColor(R.color.overdue_task_color));
                    }
                } else {
                    if (task.getIs_completed()) {
                        cardView.setCardBackgroundColor(itemView.getContext().getResources().getColor(R.color.completed_task_color));
                    } else {
                        cardView.setCardBackgroundColor(itemView.getContext().getResources().getColor(R.color.white));
                    }
                }
            } catch (ParseException e) {
                Log.e("TaskAdapter", "Error parsing date", e);
            }

//            if (task.getImage() != null && !task.getImage().isEmpty()) {
//                taskImagePreview.setVisibility(View.VISIBLE);
//                Picasso.get().load(task.getImage()).into(taskImagePreview);
//            } else {
//                taskImagePreview.setVisibility(View.GONE);
//            }

            isBinding = false;

            itemView.setOnClickListener(v->{
                if (listener != null) {
                    listener.onTaskClick(task);
                }
            });
        }
    }


}
