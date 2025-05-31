package com.example.cameraproject_2;

import static org.opencv.android.NativeCameraView.TAG;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    protected SharedPreferences sharedPreferences;
    protected DrawerLayout drawerLayout;
    protected NavigationView navigationView;
    protected ActionBarDrawerToggle toggle;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        preferenceChangeListener = (sharedPrefs, key) -> {
            Log.d("BaseActivity", "Preference changed, key: " + key);
            if (key.equals("isLoggedIn") || key.equals("loggedInUser") || key.equals("userId") || key.equals("groupNames")) {
                Log.d("BaseActivity", "Relevant key changed, updating UI");
                updateHeader();
                updateNavigationMenu();
                if (navigationView != null) {
                    navigationView.getMenu().clear();
                    getMenuInflater().inflate(R.menu.nav_menu, navigationView.getMenu());
                    updateNavigationMenu();
                    navigationView.requestLayout();
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        updateHeader();
        updateNavigationMenu();
        Log.d("BaseActivity", "onStart: Navigation menu updated");
    }

    @Override
    protected void onStop() {
        super.onStop();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    protected void updateNavigationMenu() {
        Menu menu = navigationView.getMenu();
        menu.clear();
        Log.d(TAG, "Menu cleared, inflating nav_menu");
        Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
        Log.d(TAG, "Group names from SharedPreferences: " + groupNames);
        for (String groupName : groupNames) {
            menu.add(groupName).setOnMenuItemClickListener(item -> {
                startChatroomActivity(groupName);
                return true;
            });
            Log.d(TAG, "Added group: " + groupName);
        }
        navigationView.invalidate();
    }

    protected void startChatroomActivity(String groupName) {
        Intent intent = new Intent(this, Chatroom.class);
        intent.putExtra("groupName", groupName);
        Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
        if (groupNames.contains(groupName)) {
            String membersString = sharedPreferences.getString(groupName + "_members", "");
            List<String> members = new ArrayList<>();
            if (!membersString.isEmpty()) {
                String[] membersArray = membersString.split(",");
                for (String member : membersArray) {
                    members.add(member.trim());
                }
            }
            intent.putStringArrayListExtra("members", new ArrayList<>(members));
        }
        startActivity(intent);
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    protected void updateHeader() {
        Log.d("BaseActivity", "Updating header...");
        if (navigationView == null) {
            Log.e("BaseActivity", "navigationView is null");
            return;
        }

        View headerView = navigationView.getHeaderView(0);
        if (headerView == null) {
            Log.e("BaseActivity", "headerView is null");
            headerView = navigationView.inflateHeaderView(R.layout.activity_menu_header);
        }

        TextView usernameValue = headerView.findViewById(R.id.textViewUsernameValue);
        TextView accountValue = headerView.findViewById(R.id.textViewAccountValue);

        if (usernameValue == null || accountValue == null) {
            Log.e("BaseActivity", "TextViews not found: usernameValue=" + usernameValue + ", accountValue=" + accountValue);
            return;
        }

        String username = sharedPreferences.getString("loggedInUser", "訪客");
        String userId = sharedPreferences.getString("userId", "訪客");
        Log.d("BaseActivity", "Setting username: " + username + ", userId: " + userId);

        usernameValue.setText(username);
        accountValue.setText(userId);

        headerView.invalidate();
        headerView.requestLayout();
    }

    protected void setupDrawer() {
        if (drawerLayout == null || navigationView == null) {
            Log.e("BaseActivity", "drawerLayout or navigationView is null");
            return;
        }

        toggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        } else {
            Toast.makeText(this, "ActionBar not available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.personal_account) {
            Intent intent = new Intent(this, PersonalAccount.class);
            startActivity(intent);
        } else if (id == R.id.Chat_room) {
            boolean isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);
            if (isLoggedIn) {
                Intent intent = new Intent(this, Chatroom.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, PersonalAccount.class);
                startActivity(intent);
            }
        } else if (id == R.id.Create_Group) {
            Intent intent = new Intent(this, CreateGroupActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_logout) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("確認登出")
                    .setMessage("您確定要登出嗎？")
                    .setPositiveButton("確定", (dialog, which) -> {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove("loggedInUser");
                        editor.putBoolean("isLoggedIn", false);
                        editor.putString("userId", "訪客");
                        editor.apply();
                        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, PersonalAccount.class);
                        startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            String groupName = item.getTitle().toString();
            Set<String> groupNames = sharedPreferences.getStringSet("groupNames", new HashSet<>());
            if (groupNames.contains(groupName)) {
                String membersString = sharedPreferences.getString(groupName + "_members", "");
                List<String> members = new ArrayList<>();
                if (!membersString.isEmpty()) {
                    String[] membersArray = membersString.split(",");
                    for (String member : membersArray) {
                        members.add(member);
                    }
                }
                Intent intent = new Intent(this, Chatroom.class);
                intent.putExtra("groupName", groupName);
                intent.putStringArrayListExtra("members", new ArrayList<>(members));
                startActivity(intent);
            }
        }

        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}