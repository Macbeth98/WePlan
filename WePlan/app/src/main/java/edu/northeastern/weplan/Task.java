package edu.northeastern.weplan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public class Task {
    private String task_id;
    private String description;
    private String due_date;
    private String created_at;
    private String updated_at;
    private boolean is_completed;
    private String assigned_to;
    private String group_id;
    private Reminder reminder;

    private Frequency frequency;
    private String notes;
    private String image;


    public Task() {

    }

    public Task(String task_id, String description, String due_date, String created_at,
                String updated_at, boolean is_completed, String assigned_to, String group_id,
                Reminder reminder, String notes) {
        this.task_id = task_id;
        this.description = description;
        this.due_date = due_date;
        this.created_at = created_at;
        this.updated_at = updated_at;
        this.is_completed = is_completed;
        this.assigned_to = assigned_to;
        this.group_id = group_id;
        this.reminder = reminder;
        this.notes = notes;
        this.image = image;
    }

    public String getDescription(){
        return this.description;
    }

    public String getGroup_id() {
        return this.group_id;
    }

    public boolean getIs_completed() {
        return this.is_completed;
    }

    public void setIs_completed(boolean is_completed) {
        this.is_completed = is_completed;
    }

    public String getTask_id() {
        return task_id;
    }

    public String getDue_date() {
        return due_date;
    }

    public void setTask_id(String id) {
        this.task_id = id;
    }


    public String getNotes() {
        return this.notes;
    }

    public String getImage() {
        return this.image;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setImage(String imageUrl) {
        this.image = imageUrl;
    }

    public void setAssigned_to(String currentUserId) {
        this.assigned_to = currentUserId;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public Reminder getReminder() {
        return reminder;
    }

    public String getAssigned_to() {
        return assigned_to;
    }


    public static class Reminder {

        private int beforeMinutes;
        private String option;
        private String time;
        private long timestamp;

        public int getBeforeMinutes() {
            return beforeMinutes;
        }

        public String getOption() {
            return option;
        }

        public String getTime() {
            return time;
        }
        public long  getTimestamp() {
            return timestamp;
        }
    }

    public static class Frequency {
        private String option;
        private String end_date;

        public String getOption() {
            return option;
        }

        public String getEnd_date() {
            return end_date;
        }
    }

}
