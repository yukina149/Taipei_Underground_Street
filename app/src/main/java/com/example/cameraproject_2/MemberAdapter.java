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

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

    private List<User> users; // 成員列表
    private List<String> selectedMembers; // 選中的用戶 ID 列表

    public MemberAdapter(List<User> users) {
        this.users = users != null ? users : new ArrayList<>();
        this.selectedMembers = new ArrayList<>();
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
        User user = users.get(position);
        holder.textViewMember.setText("(" + user.getId() + ") " + user.getUsername());
        holder.checkBox.setChecked(selectedMembers.contains(user.getId())); // 使用 userId
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!selectedMembers.contains(user.getId())) {
                    selectedMembers.add(user.getId());
                }
            } else {
                selectedMembers.remove(user.getId());
            }
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public List<String> getSelectedMembers() {
        return new ArrayList<>(selectedMembers); // 返回 userId 列表
    }

    // 添加或移除選中成員
    public void toggleMemberSelection(String userId, boolean isChecked) {
        if (isChecked && !selectedMembers.contains(userId)) {
            selectedMembers.add(userId);
        } else if (!isChecked) {
            selectedMembers.remove(userId);
        }
        notifyDataSetChanged(); // 刷新適配器
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView textViewMember;
        CheckBox checkBox;

        MemberViewHolder(View itemView) {
            super(itemView);
            textViewMember = itemView.findViewById(R.id.text_member);
            checkBox = itemView.findViewById(R.id.checkbox_member);
        }
    }
}