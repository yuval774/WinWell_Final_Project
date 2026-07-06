package com.winwell.app;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Glide — third-party image loader; used to load the profile photo (same as Uri's ContactAdapter)
import com.bumptech.glide.Glide;
// CircleImageView (hdodenhof) — shows the user's profile photo as a small circle
import de.hdodenhof.circleimageview.CircleImageView;

// ViewHolder for the user's own messages in the chat.
// Holds the message text, the timestamp, and a small circular avatar that shows the
// profile photo the user captured during onboarding.
public class UserMessageViewHolder extends RecyclerView.ViewHolder {

    public TextView MessageBody;
    public TextView MessageTime;
    public CircleImageView Avatar;   // the little profile picture next to the bubble

    public UserMessageViewHolder(@NonNull View itemView) {
        super(itemView);
        MessageBody = itemView.findViewById(R.id.text_message_body);
        MessageTime = itemView.findViewById(R.id.text_message_time);
        Avatar      = itemView.findViewById(R.id.img_user_avatar);
    }

    // Fills in the message text + time, and loads the user's profile photo into the avatar.
    // @param photoUri the image URI string saved in Firestore (from the onboarding camera).
    //                 If empty, the default logo placeholder stays.
    public void bind(Message message, String photoUri) {
        MessageBody.setText(message.Content);
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        MessageTime.setText(sdf.format(new Date(message.Timestamp)));

        // Load the profile photo with Glide, exactly like Uri's ContactAdapter loads a contact photo
        if (!TextUtils.isEmpty(photoUri)) {
            Glide.with(Avatar).load(photoUri).into(Avatar);
        }
    }
}