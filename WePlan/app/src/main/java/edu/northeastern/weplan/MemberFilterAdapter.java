package edu.northeastern.weplan;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MemberFilterAdapter extends ArrayAdapter<Member> {
    private List<Member> originalUsers;
    private List<Member> filteredUsers;
    private UserFilter userFilter;

    public MemberFilterAdapter(Context context, List<Member> users) {
        super(context, android.R.layout.simple_spinner_dropdown_item, users);
        this.originalUsers = new ArrayList<>(users);
        this.filteredUsers = new ArrayList<>(users);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createCustomView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createCustomView(position, convertView, parent);
    }

    private View createCustomView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
        }
        TextView textView = convertView.findViewById(android.R.id.text1);
        Member user = getItem(position);
        if (user != null) {
            textView.setText(user.getName() + " (" + user.getEmail() + ")");
        }
        return convertView;
    }

    @Override
    public int getCount() {
        return filteredUsers.size();
    }

    @Override
    public Member getItem(int position) {
        return filteredUsers.get(position);
    }

    @Override
    public Filter getFilter() {
        if (userFilter == null) {
            userFilter = new UserFilter();
        }
        return userFilter;
    }

    private class UserFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            if (constraint == null || constraint.length() == 0) {
                results.values = originalUsers;
                results.count = originalUsers.size();
            } else {
                List<Member> filteredList = new ArrayList<>();
                String filterString = constraint.toString().toLowerCase();

                for (Member user : originalUsers) {
                    if (user.getName().toLowerCase().contains(filterString) ||
                            user.getEmail().toLowerCase().contains(filterString)) {
                        filteredList.add(user);
                    }
                }

                results.values = filteredList;
                results.count = filteredList.size();
            }
            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredUsers = (List<Member>) results.values;
            notifyDataSetChanged();
        }


    }
}
