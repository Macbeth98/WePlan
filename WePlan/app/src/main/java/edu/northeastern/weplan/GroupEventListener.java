package edu.northeastern.weplan;

public interface GroupEventListener {
    void onGroupDeleted(Group group, int position);
    void onGroupUpdated(Group group, int position);
}
