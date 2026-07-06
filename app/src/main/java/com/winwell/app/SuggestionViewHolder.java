package com.winwell.app;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

// ViewHolder for a "Suggested Activity" card.
// Holds the title, description, the Accept/Decline buttons, and a status line.
// ChatAdapter decides what to show based on the message's SuggestionState and the mode.
public class SuggestionViewHolder extends RecyclerView.ViewHolder {

    public TextView Title;
    public TextView Description;
    public TextView Status;
    public LinearLayout ButtonsRow;
    public Button BtnAccept;
    public Button BtnDecline;

    public SuggestionViewHolder(@NonNull View itemView) {
        super(itemView);
        Title       = itemView.findViewById(R.id.text_activity_title);
        Description  = itemView.findViewById(R.id.text_activity_desc);
        Status       = itemView.findViewById(R.id.text_suggestion_status);
        ButtonsRow   = itemView.findViewById(R.id.buttons_row);
        BtnAccept    = itemView.findViewById(R.id.btn_accept);
        BtnDecline   = itemView.findViewById(R.id.btn_decline);
    }

    // Fills the card and shows buttons (pending) or a status line (accepted/declined).
    public void bind(Message message) {
        Title.setText(message.ActivityTitle);
        Description.setText(message.ActivityDescription);

        if (message.SuggestionState == 0) {
            // Pending → show the Accept/Decline buttons
            ButtonsRow.setVisibility(View.VISIBLE);
            Status.setVisibility(View.GONE);
        } else {
            // Already decided (or auto-pilot) → hide buttons, show a status line
            ButtonsRow.setVisibility(View.GONE);
            Status.setVisibility(View.VISIBLE);
            Status.setText(message.SuggestionState == 1
                    ? "✓ " + Status.getContext().getString(R.string.activity_accepted_status)
                    : "✕ " + Status.getContext().getString(R.string.activity_declined_status));
        }
    }
}
