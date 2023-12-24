package edu.northeastern.weplan;

import android.os.Parcel;
import android.os.Parcelable;

public class Group implements Parcelable {
    private String id;
    private String name;

    private String description;
    private String adminId;

    private String adminEmail;

    private int timestamp;

    // Constructor, getters, and setters

    public Group() {

    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public String getAdminId() {
        return this.adminId;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public int getTimestamp() {
        return this.timestamp;
    }


    public void setId(String id) {
        AppFirebaseMessagingService.subscribeToTopic(id);
        this.id = id;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public String getAdminEmail() {
        return this.adminEmail;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    protected Group(Parcel in) {
        id = in.readString();
        name = in.readString();
        description = in.readString();
        adminId = in.readString();
        adminEmail = in.readString();
        timestamp = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(description);
        dest.writeString(adminId);
        dest.writeString(adminEmail);
        dest.writeInt(timestamp);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<Group> CREATOR = new Parcelable.Creator<Group>() {
        @Override
        public Group createFromParcel(Parcel in) {
            return new Group(in);
        }

        @Override
        public Group[] newArray(int size) {
            return new Group[size];
        }
    };
}
