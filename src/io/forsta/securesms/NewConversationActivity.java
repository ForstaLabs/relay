/**
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.securesms;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.model.ForstaDistribution;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.GroupUtil;

/**
 * Activity container for starting a new conversation.
 *
 * @author Moxie Marlinspike
 *
 */
public class NewConversationActivity extends ContactSelectionActivity {

  private static final String TAG = NewConversationActivity.class.getSimpleName();
  private Set<String> selectedTags = new HashSet<>();
  private Set<String> customTags = new HashSet<>();
  private Recipients selectedRecipients;

  @Override
  public void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    super.onCreate(bundle, masterSecret);

    getToolbar().setShowCustomNavigationButton(false);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    createConversationButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        handleCreateConversation(getConversationExpression());
      }
    });

    recipientsInput.addTextChangedListener(new TextChangedWatcher());
    recipientsInput.requestFocus();

    startRecipientButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        new AsyncTask<String, Void, ForstaDistribution>() {

          @Override
          protected ForstaDistribution doInBackground(String... strings) {
            return CcsmApi.getMessageDistribution(NewConversationActivity.this, strings[0]);
          }

          @Override
          protected void onPostExecute(ForstaDistribution distribution) {
            if (distribution.hasWarnings()) {
              Toast.makeText(NewConversationActivity.this, distribution.getWarnings(), Toast.LENGTH_LONG).show();
            }
            customTags.add(distribution.pretty);
            recipientExpression.setText(getConversationExpression());
            recipientsInput.setText("");
          }
        }.execute(recipientsInput.getText().toString());
      }
    });
  }

  @Override
  public void onContactSelected(final String number) {
    // XXXX Groups are currently used for non-user tags and have no device address associated with them.
    ForstaUser localUser = ForstaUser.getLocalForstaUser(NewConversationActivity.this);
    String text = recipientsInput.getText().toString();
    if (!text.endsWith(" ")) {
      int recipientIndex = text.lastIndexOf("@") != -1 ? text.lastIndexOf("@") : 0;
      recipientsInput.setText(text.substring(0, recipientIndex));
    }
    if (GroupUtil.isEncodedGroup(number)) {
      GroupDatabase.GroupRecord group = DatabaseFactory.getGroupDatabase(NewConversationActivity.this).getGroup(number);
      if (!selectedTags.contains(group.getFormattedTag(localUser.getOrgTag()))) {
        selectedTags.add(group.getFormattedTag(localUser.getOrgTag()));
      } else {
        selectedTags.remove(group.getFormattedTag(localUser.getOrgTag()));
      }
    } else {
      ForstaUser user = DbFactory.getContactDb(NewConversationActivity.this).getUserByAddress(number);
      if (!selectedTags.contains(user.getFormattedTag(localUser.getOrgTag()))) {
        selectedTags.add(user.getFormattedTag(localUser.getOrgTag()));
      } else {
        selectedTags.remove(user.getFormattedTag(localUser.getOrgTag()));
      }
    }
    recipientExpression.setText(getConversationExpression());
    recipientsInput.setSelection(recipientsInput.length());
  }

  private String getConversationExpression() {
    String selectedExpression = TextUtils.join(" + ", selectedTags);
    String customExpression = TextUtils.join(" + ", customTags);
    return selectedExpression + ((customExpression.length() > 0 && selectedExpression.length() > 0) ? " + (" + customExpression + ")" : customExpression);
  }

  private void handleCreateConversation(String inputText) {

    new AsyncTask<String, Void, ForstaDistribution>() {
      @Override
      protected ForstaDistribution doInBackground(String... params) {
        String expression = params[0];
        ForstaDistribution initialDistribution = CcsmApi.getMessageDistribution(NewConversationActivity.this, expression);
        ForstaUser localUser = ForstaUser.getLocalForstaUser(NewConversationActivity.this);
        if (initialDistribution.hasWarnings() || initialDistribution.userIds.contains(localUser.uid) || !initialDistribution.hasRecipients()) {
          return initialDistribution;
        } else {
          String newExpression = initialDistribution.pretty + " + @" + localUser.getTag();
          return CcsmApi.getMessageDistribution(NewConversationActivity.this, newExpression);
        }
      }

      @Override
      protected void onPostExecute(ForstaDistribution distribution) {
        if (distribution.hasWarnings()) {
          Toast.makeText(NewConversationActivity.this, distribution.getWarnings(), Toast.LENGTH_LONG).show();
          return;
        }

        if (distribution.hasRecipients()) {
          Recipients recipients = RecipientFactory.getRecipientsFromStrings(NewConversationActivity.this, distribution.getRecipients(NewConversationActivity.this), false);
          ForstaThread forstaThread = DatabaseFactory.getThreadDatabase(NewConversationActivity.this).getThreadForDistribution(distribution.universal);
          long threadId = -1L;
          if (forstaThread != null) {
            // Show dialog asking to select this thread or create a new thread.
            threadId = forstaThread.threadid;
          }

          Intent intent = new Intent(NewConversationActivity.this, ConversationActivity.class);
          intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
          intent.putExtra(ConversationActivity.TEXT_EXTRA, getIntent().getStringExtra(ConversationActivity.TEXT_EXTRA));
          intent.setDataAndType(getIntent().getData(), getIntent().getType());
          intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
          intent.putExtra(ConversationActivity.DISTRIBUTION_EXPRESSION_EXTRA, distribution.universal);
          intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
          recipientsInput.setText("");
          startActivity(intent);
          finish();
        } else {
          Toast.makeText(NewConversationActivity.this, "No recipients found in expression.", Toast.LENGTH_LONG).show();
        }
      }
    }.execute(inputText);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case android.R.id.home:   super.onBackPressed(); return true;
    case R.id.menu_refresh:   handleManualRefresh(); return true;
    case R.id.menu_new_group: handleCreateGroup();   return true;
//    case R.id.menu_invite:    handleInvite();        return true;
    }

    return false;
  }

  private void handleManualRefresh() {
    contactsFragment.setRefreshing(true);
    onRefresh();
  }

  private void handleCreateGroup() {
    startActivity(new Intent(this, GroupCreateActivity.class));
  }

  private void handleInvite() {
    startActivity(new Intent(this, InviteActivity.class));
  }

  @Override
  protected boolean onPrepareOptionsPanel(View view, Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();
    inflater.inflate(R.menu.new_conversation_activity, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  private class TextChangedWatcher implements TextWatcher {

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
      Pattern p = Pattern.compile("@[a-zA-Z0-9(-|.)]+");
      Matcher m = p.matcher(charSequence);
      String input = charSequence.toString();

      if (input.length() == 1 && !input.endsWith("@")) {
        recipientsInput.setText("@" + input);
        recipientsInput.setSelection(recipientsInput.length());
      }

      int slugStart = input.lastIndexOf("@");
      String slugPart = input.substring(slugStart + 1);
      if (slugPart.contains(" ")) {
        contactsFragment.resetQueryFilter();
      } else {
        contactsFragment.setQueryFilter(slugPart);
      }
    }

    @Override
    public void afterTextChanged(Editable editable) {
      recipientCount.setText(selectedTags.size() + "");
    }
  }
}
