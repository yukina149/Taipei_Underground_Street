package com.example.cameraproject_2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final int VIEW_TYPE_DATE = 3;

    private List<Object> messages;
    private String currentUsername;

    public MessageAdapter(List<Object> messages, String currentUsername) {
        this.messages = messages;
        this.currentUsername = currentUsername;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = messages.get(position);
        if (item instanceof String) {
            return VIEW_TYPE_DATE;
        }
        Message message = (Message) item;
        return message.sender.equals(currentUsername) ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_DATE) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.date_item, parent, false);
            return new DateViewHolder(view);
        }
        int layoutRes = viewType == VIEW_TYPE_SENT ? R.layout.message_item_sent : R.layout.message_item_received;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = messages.get(position);
        if (holder instanceof DateViewHolder) {
            ((DateViewHolder) holder).textView.setText((String) item);
        } else {
            Message message = (Message) item;
            MessageViewHolder messageHolder = (MessageViewHolder) holder;
            messageHolder.senderView.setText(message.sender); // 顯示發送者名稱
            messageHolder.textView.setText(message.content);
            messageHolder.timeView.setText(message.formattedTimestamp);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView senderView; // 新增發送者名稱
        TextView textView;
        TextView timeView;

        MessageViewHolder(View itemView) {
            super(itemView);
            senderView = itemView.findViewById(R.id.textViewSender);
            textView = itemView.findViewById(R.id.textViewMessage);
            timeView = itemView.findViewById(R.id.textViewTime);
        }
    }

    static class DateViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        DateViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.textViewDate);
        }
    }

    public static class Message {
        String sender;
        String content;
        String formattedTimestamp;

        public Message(String sender, String content, String formattedTimestamp) {
            this.sender = sender;
            this.content = content;
            this.formattedTimestamp = formattedTimestamp;
        }
    }
}