package com.example.cameraproject_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class receive_group extends AppCompatActivity {

    private RegisterDatabaseHelper dbHelper;
    private String currentUserId;
    private RecyclerView recyclerViewInvitations;
    private InvitationAdapter invitationAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_receive_group);

        // Initialize DatabaseHelper and userId
        dbHelper = new RegisterDatabaseHelper(this);
        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        currentUserId = sharedPreferences.getString("userId", null);

        // Set window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize RecyclerView
        recyclerViewInvitations = findViewById(R.id.recycler_view_invitations);
        recyclerViewInvitations.setLayoutManager(new LinearLayoutManager(this));
        List<Invitation> invitationList = loadInvitationList();
        invitationAdapter = new InvitationAdapter(invitationList, this::onInvitationAction);
        recyclerViewInvitations.setAdapter(invitationAdapter);
    }

    private List<Invitation> loadInvitationList() {
        List<Invitation> invitationList = new ArrayList<>();
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            return invitationList;
        }

        SQLiteDatabase db = dbHelper.getRegisterDatabase();
        Cursor cursor = db.query(RegisterDatabaseHelper.TABLE_INVITATIONS,
                new String[]{RegisterDatabaseHelper.COL_INVITATION_ID, RegisterDatabaseHelper.COL_GROUP_NAME, RegisterDatabaseHelper.COL_STATUS},
                RegisterDatabaseHelper.COL_INVITED_USER + "=?",
                new String[]{currentUserId},
                null, null, null);

        while (cursor.moveToNext()) {
            String invitationId = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_INVITATION_ID));
            String groupName = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_GROUP_NAME));
            String status = cursor.getString(cursor.getColumnIndexOrThrow(RegisterDatabaseHelper.COL_STATUS));
            invitationList.add(new Invitation(invitationId, groupName, status));
        }
        cursor.close();
        return invitationList;
    }

    private void onInvitationAction(String invitationId, String action) {
        if ("accept".equals(action)) {
            dbHelper.updateInvitationStatus(invitationId, "accepted");
            Toast.makeText(this, "已同意加入群組", Toast.LENGTH_SHORT).show();
        } else if ("reject".equals(action)) {
            dbHelper.updateInvitationStatus(invitationId, "rejected");
            Toast.makeText(this, "已拒絕加入群組", Toast.LENGTH_SHORT).show();
        }
        // Refresh the list
        invitationAdapter.updateInvitations(loadInvitationList());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the list on resume
        invitationAdapter.updateInvitations(loadInvitationList());
    }
}

// InvitationAdapter class
class InvitationAdapter extends RecyclerView.Adapter<InvitationAdapter.ViewHolder> {

    private List<Invitation> invitations;
    private OnInvitationActionListener listener;

    public interface OnInvitationActionListener {
        void onInvitationAction(String invitationId, String action);
    }

    public InvitationAdapter(List<Invitation> invitations, OnInvitationActionListener listener) {
        this.invitations = invitations;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_invitation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Invitation invitation = invitations.get(position);
        holder.textViewGroupName.setText(invitation.getGroupName());
        if ("pending".equals(invitation.getStatus())) {
            holder.textViewStatus.setText("收到邀請，尚未同意");
            holder.buttonAction.setVisibility(View.VISIBLE);
            holder.buttonAction.setText("同意");
            holder.buttonAction.setOnClickListener(v -> listener.onInvitationAction(invitation.getInvitationId(), "accept"));
        } else if ("accepted".equals(invitation.getStatus())) {
            holder.textViewStatus.setText("已同意");
            holder.buttonAction.setVisibility(View.GONE);
        } else if ("rejected".equals(invitation.getStatus())) {
            holder.textViewStatus.setText("已拒絕");
            holder.buttonAction.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return invitations.size();
    }

    public void updateInvitations(List<Invitation> newInvitations) {
        this.invitations = newInvitations;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textViewGroupName;
        TextView textViewStatus;
        Button buttonAction;

        ViewHolder(View itemView) {
            super(itemView);
            textViewGroupName = itemView.findViewById(R.id.text_view_group_name);
            textViewStatus = itemView.findViewById(R.id.text_view_status);
            buttonAction = itemView.findViewById(R.id.button_action);
        }
    }
}