package com.winwell.app;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// ViewHolder for bot messages in the chat.
// Same idea as UserMessageViewHolder but for the bot's replies.
// Having a separate ViewHolder lets us use a different layout and style
// for bot messages (left-aligned, different color bubble).
public class BotMessageViewHolder extends RecyclerView.ViewHolder {

    // The TextView that displays the bot's message text
    public TextView MessageBody;

    // The TextView that displays the timestamp
    public TextView MessageTime;

    // Constructor - finds and stores references to the views in the layout
    public BotMessageViewHolder(@NonNull View itemView) {
        super(itemView);
        MessageBody = itemView.findViewById(R.id.text_message_body);
        MessageTime = itemView.findViewById(R.id.text_message_time);
    }

    // Fills in the bot message data into the views.
    // Sets the message text and formats the timestamp to show
    // a readable time like "3:45 PM".
    public void bind(Message message) {
        MessageBody.setText(message.Content);
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        MessageTime.setText(sdf.format(new Date(message.Timestamp)));
    }
}
