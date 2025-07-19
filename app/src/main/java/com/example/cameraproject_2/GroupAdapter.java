package com.example.cameraproject_2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {

    private List<Group> groupList;
    private final OnGroupClickListener clickListener;

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
    }

    public GroupAdapter(List<Group> groupList, OnGroupClickListener clickListener) {
        this.groupList = groupList != null ? groupList : new ArrayList<>();
        this.clickListener = clickListener;
    }

    public void updateGroups(List<Group> newGroupList) {
        this.groupList = newGroupList != null ? newGroupList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Group group = groupList.get(position);
        holder.groupName.setText(group.getGroupName());
        holder.groupMessage.setText(group.getLastMessage());
        holder.lastMessageTime.setText(group.getLastMessageTime());

        // Load creator's avatar
        String avatarUrl = group.getCreatorAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(avatarUrl)
                    .placeholder(R.drawable.icon_logo)
                    .error(R.drawable.icon_logo)
                    .into(holder.groupCreatorAvatar);
        } else {
            holder.groupCreatorAvatar.setImageResource(R.drawable.icon_logo);
        }

        holder.itemView.setOnClickListener(v -> clickListener.onGroupClick(group));
    }

    @Override
    public int getItemCount() {
        return groupList.size();
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        ImageView groupCreatorAvatar;
        TextView groupName;
        TextView groupMessage;
        TextView lastMessageTime;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            groupCreatorAvatar = itemView.findViewById(R.id.group_creator_avatar);
            groupName = itemView.findViewById(R.id.group_name);
            groupMessage = itemView.findViewById(R.id.group_message);
            lastMessageTime = itemView.findViewById(R.id.last_message_time);
        }
    }
}