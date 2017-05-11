package io.forsta.ccsm;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.R;
import io.forsta.securesms.components.ComposeText;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.groups.GroupManager;

/**
 * Created by jlewis on 4/27/17.
 */

public class ForstaInputFragment extends Fragment {
  private static final String TAG = ForstaInputFragment.class.getSimpleName();

  private ImageButton directoryButton;
  private ImageButton sendButton;
  private ComposeText messageInput;
  private TextView messageType;
  private Map<String, String> recipients = new HashMap<>();
  private Map<String, String> slugMap = new HashMap<>();
  private static final int DIRECTORY_PICK = 13;


  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.forsta_input_fragment, container, false);
    sendButton = (ImageButton) view.findViewById(R.id.forsta_send_button);
    directoryButton = (ImageButton) view.findViewById(R.id.forsta_quick_directory);
    messageType = (TextView) view.findViewById(R.id.forsta_input_type);
    messageInput = (ComposeText) view.findViewById(R.id.embedded_text_editor);

    initializeSlugs();

    initializeListeners();
    return view;
  }

  private void initializeSlugs() {
    ContactDb db = DbFactory.getContactDb(getActivity());
    GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(getActivity());
    slugMap = db.getContactSlugs();
    slugMap.putAll(groupDb.getGroupSlugs());
  }

  private void initializeListeners() {
    sendButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (recipients.size() > 0) {
          // Take all of the new recipients and groups and create a new group for them.

          if (recipients.size() == 1) {
            // Send a single recipient message


          } else {
            // Create a new group, using all the recipients.
            // Use the tags and usernames as the new group title... @john-lewis, @dev-team
            // Need to stop other users from modifying the group.
            String title = "";
            for (Map.Entry<String, String> entry : recipients.entrySet()) {
              title += entry.getKey();
            }
          }

        } else {
          Toast.makeText(getActivity(), "There are no recipients in messsage.", Toast.LENGTH_SHORT).show();
        }
      }
    });

    directoryButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(getActivity(), DirectoryActivity.class);
        startActivityForResult(intent, DIRECTORY_PICK);
      }
    });

    messageInput.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

      }

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        Map<String, String> matched = new HashMap<String, String>();

        Pattern p = Pattern.compile("@[a-zA-Z0-9-]+");
        Matcher m = p.matcher(charSequence);
        while (m.find()) {
          String recipient = m.group();
          recipient = recipient.substring(1);
          if (slugMap.containsKey(recipient)) {
            recipients.put(recipient, slugMap.get(recipient));
          }
        }
        messageType.setText(recipients.values().toString());
        Log.d(TAG, "Recipients: " + recipients.size());
      }

      @Override
      public void afterTextChanged(Editable editable) {

      }
    });
  }
}
