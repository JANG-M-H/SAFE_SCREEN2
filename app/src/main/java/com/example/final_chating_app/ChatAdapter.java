package com.example.final_chating_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<net.flow9.thisisKotlin.firebase.ChatMessage> chatMessages;

    public ChatAdapter(List<net.flow9.thisisKotlin.firebase.ChatMessage> chatMessages) {
        this.chatMessages = chatMessages;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_item, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        net.flow9.thisisKotlin.firebase.ChatMessage chatMessage = chatMessages.get(position);
        holder.nicknameTv.setText(chatMessage.getNickname());
        holder.messageTv.setText(chatMessage.getMessage());
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView nicknameTv;
        TextView messageTv;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            nicknameTv = itemView.findViewById(R.id.nicknameTv);
            messageTv = itemView.findViewById(R.id.messageTv);
        }
    }
}