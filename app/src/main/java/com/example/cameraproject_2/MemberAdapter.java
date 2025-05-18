package com.example.cameraproject_2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import com.example.cameraproject_2.User; // 確保添加這行

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

    private List<User> userList;

    public MemberAdapter(List<User> userList) {
        this.userList = userList;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        User user = userList.get(position);
        holder.textView.setText(user.getUsername() + " (ID: " + user.getId() + ")");
        holder.checkBox.setChecked(user.isSelected());
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            user.setSelected(isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public List<String> getSelectedMembers() {
        List<String> selectedMembers = new ArrayList<>();
        for (User user : userList) {
            if (user.isSelected()) {
                selectedMembers.add(user.getId());
            }
        }
        return selectedMembers;
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView textView;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox_member);
            textView = itemView.findViewById(R.id.text_member);
        }
    }
}