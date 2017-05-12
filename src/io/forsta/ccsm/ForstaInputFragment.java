package io.forsta.ccsm;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.StringBuilderPrinter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.R;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.components.ComposeText;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.groups.GroupManager;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.util.GroupUtil;

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
  private MasterSecret masterSecret;


  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.masterSecret = getArguments().getParcelable("master_secret");
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.forsta_input_fragment, container, false);
    sendButton = (ImageButton) view.findViewById(R.id.forsta_send_button);
    directoryButton = (ImageButton) view.findViewById(R.id.forsta_quick_directory);
    messageType = (TextView) view.findViewById(R.id.forsta_input_type);
    messageInput = (ComposeText) view.findViewById(R.id.embedded_text_editor);

    getSlugs();

    initializeListeners();
    return view;
  }

  private void getSlugs() {
    ContactDb db = DbFactory.getContactDb(getActivity());
    GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(getActivity());
    slugMap = db.getContactSlugs();
    slugMap.putAll(groupDb.getGroupSlugs());
  }

  private void initializeListeners() {
    sendButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(getActivity());
        String message = messageInput.getText().toString();

        if (recipients.size() > 0) {
          // Take all of the new recipients and groups and create a new group for them.
          if (recipients.size() == 1) {
            // Send a single recipient message. Works for a single number or groupId
            Recipients messageRecipients = RecipientFactory.getRecipientsFromStrings(getActivity(), new ArrayList<String>(recipients.values()), false);
            sendMessage(message, messageRecipients);
          } else {
            // Create a new group, using all the recipients.
            // Use the tags and usernames as the new group title... @john-lewis, @dev-team
            // Need to stop other users from modifying the group.
            StringBuilder title = new StringBuilder();
            Set<String> numbers = new HashSet<String>();
            for (Map.Entry<String, String> entry : recipients.entrySet()) {
              title.append(entry.getKey()).append(", ");
              if (GroupUtil.isEncodedGroup(entry.getValue())) {
                try {
                  Set<String> members = groupDb.getGroupMembers(GroupUtil.getDecodedId(entry.getValue()));
                  numbers.addAll(members);
                } catch (IOException e) {
                  e.printStackTrace();
                }
              } else {
                numbers.add(entry.getValue());
              }
            }
            // Add this phone's user to the end of the list.
            title.append(ForstaPreferences.getForstaUsername(getActivity()));
            // Now create new group and send to the new groupId.
            sendGroupMessage(message, numbers, title.toString());
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
          String slug = m.group();
          slug = slug.substring(1);
          if (slugMap.containsKey(slug)) {
            matched.put(slug, slugMap.get(slug));
          }
        }
        recipients = matched;
        messageType.setText(recipients.values().toString());
        Log.d(TAG, "Recipients: " + recipients.size());
      }

      @Override
      public void afterTextChanged(Editable editable) {

      }
    });
  }

  private void sendGroupMessage(final String message, Set<String> numbers, final String title) {
    // Need to check here to see if these numbers are already part of an existing group.
    //
    //
    new AsyncTask<Set<String>, Void, Recipients>() {

      @Override
      protected Recipients doInBackground(Set<String>... numbers) {
        try {
          return RecipientFactory.getRecipientsFromStrings(getActivity(), new ArrayList<String>(numbers[0]), false);
        } catch (Exception e) {
          Log.d(TAG, "sendGroupMessage failed");
          e.printStackTrace();
        }
        return null;
      }

      @Override
      protected void onPostExecute(Recipients recipients) {
        if (recipients != null) {
          try {
            List<Recipient> validRecipients = recipients.getRecipientsList();
            GroupManager.GroupActionResult result = GroupManager.createGroup(getActivity(), masterSecret,  new HashSet<>(validRecipients), null, title);
            Recipients groupRecipient = result.getGroupRecipient();
            sendMessage(message, groupRecipient);
          } catch (InvalidNumberException e) {
            e.printStackTrace();
          }
        }
      }
    }.execute(numbers);
  }

  private void sendMessage(String message, Recipients messageRecipients) {
    long expiresIn = messageRecipients.getExpireMessages() * 1000;

    OutgoingMediaMessage mediaMessage = new OutgoingMediaMessage(messageRecipients, message, new LinkedList<Attachment>(), System.currentTimeMillis(), -1, expiresIn, ThreadDatabase.DistributionTypes.DEFAULT);
    new AsyncTask<OutgoingMediaMessage, Void, Void>() {

      protected void onPostExecute(Void aVoid) {
        messageInput.setText("");
        recipients.clear();
      }

      @Override
      protected Void doInBackground(OutgoingMediaMessage... params) {
        // This will create a threadId if there is not one already.
        final long threadId = DatabaseFactory.getThreadDatabase(getActivity()).getThreadIdFor(params[0].getRecipients());
        MessageSender.send(getActivity(), masterSecret, params[0], threadId, false);
        return null;
      }
    }.execute(mediaMessage);
  }
}
