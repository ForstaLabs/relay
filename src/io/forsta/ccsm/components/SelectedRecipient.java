package io.forsta.ccsm.components;

import android.content.Context;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.forsta.securesms.R;

/**
 * Created by john on 9/30/2017.
 */

public class SelectedRecipient extends LinearLayout {
  private ImageButton removeButton;
  private TextView selectedTag;
  public SelectedRecipient(Context context) {
    super(context);

    inflate(context, R.layout.new_conversation_selected_recipient, this);
    removeButton = (ImageButton) findViewById(R.id.selected_recipient_remove);
    selectedTag = (TextView) findViewById(R.id.selected_recipient_tag);
  }
}
