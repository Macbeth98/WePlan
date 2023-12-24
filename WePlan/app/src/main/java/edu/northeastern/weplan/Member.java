package edu.northeastern.weplan;

import com.google.firebase.firestore.DocumentReference;

import java.util.ArrayList;
import java.util.List;

public class Member {

    private String id;

    private String name;

    private String email;

    private List<DocumentReference> groups;

    public Member() {

    }
    public Member(String name, String email) {
        this.name = name;
        this.email = email;
        this.groups = new ArrayList<>();
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public  void setGroups(List<DocumentReference> groups) {
        this.groups = groups;
    }

    public String getName() {
        return this.name;
    }

    public String getEmail() {
        return this.email;
    }

    public List<DocumentReference> getGroups() {
        return this.groups;
    }
}
