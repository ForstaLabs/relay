package io.forsta.ccsm;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.StringBuilderPrinter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
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

import io.forsta.ccsm.api.ForstaUser;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.NewConversationActivity;
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
  private ImageButton newConversationButton;
  private ComposeText messageInput;
  private TextView recipientCount;
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
    newConversationButton = (ImageButton) view.findViewById(R.id.forsta_single_recipient);
    recipientCount = (TextView) view.findViewById(R.id.forsta_input_recipients);
    messageInput = (ComposeText) view.findViewById(R.id.embedded_text_editor);

    initializeListeners();
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    getSlugs();
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
//        Intent intent = new Intent(getActivity(), DirectoryActivity.class);
//        startActivityForResult(intent, DIRECTORY_PICK);
        Set<String> options = slugMap.keySet();

        CharSequence[] selectChoices = options.toArray(new CharSequence[options.size()]);
        final List<Integer> chosenSlugs = new ArrayList();

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Choose a recipient");
        builder.setMultiChoiceItems(selectChoices, null, new DialogInterface.OnMultiChoiceClickListener() {

          @Override
          public void onClick(DialogInterface dialogInterface, int i, boolean b) {
            if (b) {
              chosenSlugs.add(i);
            } else if (chosenSlugs.contains(i)) {
              chosenSlugs.remove(chosenSlugs.indexOf(i));
            }
          }
        });
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            StringBuilder slugs = new StringBuilder();
            for (Integer slug : chosenSlugs) {
              slugs.append("@").append(slugMap.keySet().toArray()[slug]).append(" ");
            }
            messageInput.append(slugs);
            messageInput.setSelection(messageInput.getText().length());
          }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            dialogInterface.dismiss();
          }
        });
        builder.show();
      }
    });

    newConversationButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        startActivity(new Intent(getActivity(), NewConversationActivity.class));
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
        recipientCount.setText("" + recipients.size());
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
        try {
          // This will create a threadId if there is not one already.
          final long threadId = DatabaseFactory.getThreadDatabase(getActivity()).getThreadIdFor(params[0].getRecipients());
          MessageSender.send(getActivity(), masterSecret, params[0], threadId, false);
        } catch (Exception e) {
          e.printStackTrace();
        }
        return null;
      }
    }.execute(mediaMessage);
  }
}
