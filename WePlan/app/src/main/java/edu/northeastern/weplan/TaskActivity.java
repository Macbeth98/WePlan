package edu.northeastern.weplan;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class TaskActivity extends FragmentActivity {

    private ViewPager2 viewPager;
    private FragmentStateAdapter pagerAdapter;
    private TabLayout tabLayout;

    private User userData;

    private List<Group> groups;

//    private String userId = getIntent().getStringExtra("USER_ID");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);


        if (savedInstanceState != null) {
            // Restore the user data from savedInstanceState
            userData = savedInstanceState.getParcelable("USER_DATA");
            groups = savedInstanceState.getParcelableArrayList("USER_GROUPS");
        } else {
            Bundle bundle = getIntent().getBundleExtra("USER_BUNDLE");
            if (bundle != null) {
                userData = bundle.getParcelable("USER_DATA");
                groups = bundle.getParcelableArrayList("USER_GROUPS");
            }
        }

        Log.d("TaskActivity: onCreate: UserData", userData.toString());

        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

        pagerAdapter = new ScreenSlidePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(position == 0 ? "Day View" : "Week View")).attach();


        updateViewForTasks(false);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.todo) {
                updateViewForTasks(false);
                return true;
            } else if (itemId == R.id.completed) {
                updateViewForTasks(true);
                return true;
            }
            return false;
        });

        FloatingActionButton fab = findViewById(R.id.fabAddGroupMember);
        fab.setOnClickListener(view -> {
            Intent intent = new Intent(TaskActivity.this, AddTaskActivity.class);
//            intent.putExtra("USER_ID", userId);
            startActivity(intent);
        });

    }

    private void updateViewForTasks(boolean showCompleted) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (currentFragment!= null) {
            if (currentFragment instanceof DayViewFragment) {
                ((DayViewFragment) currentFragment).setShowCompletedTasks(showCompleted);
            } else if (currentFragment instanceof WeekViewFragment) {
                ((WeekViewFragment) currentFragment).setShowCompletedTasks(showCompleted);
            }
        }

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable("USER_DATA", userData);
        outState.putParcelableArrayList("USER_GROUPS", (ArrayList<Group>) groups);
    }

    private class ScreenSlidePagerAdapter extends FragmentStateAdapter {
        public ScreenSlidePagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new DayViewFragment();
            } else {
                return new WeekViewFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    public void onGroupsMenuClick(MenuItem item) {
        Intent intent = new Intent(TaskActivity.this, GroupsActivity.class);
        intent.putExtra("USER_DATA",  userData);
        intent.putExtra("USER_GROUPS", (ArrayList<? extends Parcelable>) groups);
        Log.d("TaskActivity: UserData", userData.toString());
        startActivity(intent);
    }


}
