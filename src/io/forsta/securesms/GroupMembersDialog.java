package io.forsta.securesms;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class GroupMembersDialog extends AsyncTask<Void, Void, Recipients> {

  private static final String TAG = GroupMembersDialog.class.getSimpleName();

  private final Recipients recipients;
  private final Context    context;

  public GroupMembersDialog(Context context, Recipients recipients) {
    this.recipients = recipients;
    this.context    = context;
  }

  @Override
  public void onPreExecute() {}

  @Override
  protected Recipients doInBackground(Void... params) {
    try {
      String groupId = recipients.getPrimaryRecipient().getAddress();
      return DatabaseFactory.getGroupDatabase(context)
                            .getGroupMembers(GroupUtil.getDecodedId(groupId), true);
    } catch (IOException e) {
      Log.w(TAG, e);
      return RecipientFactory.getRecipientsFor(context, new LinkedList<Recipient>(), true);
    }
  }

  @Override
  public void onPostExecute(Recipients members) {
    GroupMembers groupMembers = new GroupMembers(members);
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(R.string.ConversationActivity_conversation_members);
    builder.setIconAttribute(R.attr.group_members_dialog_icon);
    builder.setCancelable(true);
//    builder.setItems(groupMembers.getRecipientStrings(), new GroupMembersOnClickListener(context, groupMembers));
    builder.setItems(groupMembers.getRecipientStrings(), null);
    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  public void display() {
    onPostExecute(recipients);
  }

  private static class GroupMembersOnClickListener implements DialogInterface.OnClickListener {
    private final GroupMembers groupMembers;
    private final Context      context;

    public GroupMembersOnClickListener(Context context, GroupMembers members) {
      this.context      = context;
      this.groupMembers = members;
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int item) {
      Recipient recipient = groupMembers.get(item);
      //TODO implement.
    }
  }

  /**
   * Wraps a List of Recipient (just like @class Recipients),
   * but with focus on the order of the Recipients.
   * So that the order of the RecipientStrings[] matches
   * the internal order.
   *
   * @author Christoph Haefner
   */
  private class GroupMembers {
    private final String TAG = GroupMembers.class.getSimpleName();

    private final LinkedList<Recipient> members = new LinkedList<>();

    public GroupMembers(Recipients recipients) {
      for (Recipient recipient : recipients.getRecipientsList()) {
        if (isLocalNumber(recipient)) {
          members.push(recipient);
        } else {
          members.add(recipient);
        }
      }
    }

    public String[] getRecipientStrings() {
      List<String> recipientStrings = new LinkedList<>();

      for (Recipient recipient : members) {
        if (isLocalNumber(recipient)) {
          recipientStrings.add(recipient.getFullTag() + "(" + context.getString(R.string.GroupMembersDialog_me) + ")");
        } else {
          recipientStrings.add(recipient.getFullTag());
        }
      }

      return recipientStrings.toArray(new String[members.size()]);
    }

    public Recipient get(int index) {
      return members.get(index);
    }

    private boolean isLocalNumber(Recipient recipient) {
      try {
        String localNumber = TextSecurePreferences.getLocalNumber(context);
        String e164Number  = Util.canonicalizeNumber(context, recipient.getAddress());

        return e164Number != null && e164Number.equals(localNumber);
      } catch (InvalidNumberException e) {
        Log.w(TAG, e);
        return false;
      }
    }
  }
}
