package io.forsta.ccsm.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
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
  }

  public SelectedRecipient(Context context, AttributeSet attrs) {
    super(context, attrs);
    LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.new_conversation_selected_recipient, this, true);
    removeButton = (ImageButton) findViewById(R.id.selected_recipient_remove);
    selectedTag = (TextView) findViewById(R.id.selected_recipient_tag);
  }
}
