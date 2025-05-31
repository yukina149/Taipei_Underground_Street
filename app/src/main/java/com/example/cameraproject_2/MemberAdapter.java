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

    private List<User> users;
    private List<String> selectedMembers;

    public MemberAdapter(List<User> users) {
        this.users = users;
        this.selectedMembers = new ArrayList<>();
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        User user = users.get(position);
        holder.textViewUsername.setText(user.getUsername());
        holder.textViewId.setText(user.getId());
        holder.checkBox.setChecked(selectedMembers.contains(user.getUsername()));
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!selectedMembers.contains(user.getUsername())) {
                    selectedMembers.add(user.getUsername());
                }
            } else {
                selectedMembers.remove(user.getUsername());
            }
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public List<String> getSelectedMembers() {
        return selectedMembers;
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView textViewUsername;
        TextView textViewId;
        CheckBox checkBox;

        MemberViewHolder(View itemView) {
            super(itemView);
            textViewUsername = itemView.findViewById(android.R.id.text1);
            textViewId = itemView.findViewById(android.R.id.text2);
            checkBox = new CheckBox(itemView.getContext());
            checkBox.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            ((ViewGroup) itemView).addView(checkBox);
        }
    }
}