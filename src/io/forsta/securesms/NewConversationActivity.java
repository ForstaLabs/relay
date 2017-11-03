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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.model.ForstaDistribution;
import io.forsta.ccsm.components.SelectedRecipient;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.DirectoryHelper;

/**
 * Activity container for starting a new conversation.
 *
 * @author Moxie Marlinspike
 *
 */
public class NewConversationActivity extends ContactSelectionActivity {

  private static final String TAG = NewConversationActivity.class.getSimpleName();
  private Recipients selectedRecipients;
  private RemoveRecipientClickListener selectedRecipientRemoveListener;

  @Override
  public void onCreate(Bundle bundle, @NonNull final MasterSecret masterSecret) {
    super.onCreate(bundle, masterSecret);

    getToolbar().setShowCustomNavigationButton(false);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    toolbar.setSearchOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        showProgressBar();
        String searchText = toolbar.getSearchText();
        if (!searchText.startsWith("@")) {
          searchText = "@" + searchText;
        }
        new AsyncTask<String, Void, ForstaDistribution>() {

          @Override
          protected ForstaDistribution doInBackground(String... strings) {
            ForstaDistribution distribution = CcsmApi.getMessageDistribution(NewConversationActivity.this, strings[0]);
            if (distribution.hasRecipients()) {
              DirectoryHelper.refreshDirectoryFor(NewConversationActivity.this, masterSecret, distribution.getRecipients(NewConversationActivity.this));
            }
            return distribution;
          }

          @Override
          protected void onPostExecute(ForstaDistribution distribution) {
            hideProgressBar();
            if (distribution.hasWarnings()) {
              Toast.makeText(NewConversationActivity.this, distribution.getWarnings(), Toast.LENGTH_LONG).show();
            }
            if (distribution.hasRecipients()) {
              String searchText = toolbar.getSearchText();
              if (searchText.contains(":")) {
                String removeDomain = searchText.substring(0, searchText.indexOf(":"));
                toolbar.setSearchText(removeDomain);
              } else {
                toolbar.setSearchText(searchText);
              }
            }
          }
        }.execute(searchText);
      }
    });

    toolbar.setCreateConversationListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        handleCreateConversation();
      }
    });
    selectedRecipientRemoveListener = new RemoveRecipientClickListener();
  }

  private class RemoveRecipientClickListener implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      removeRecipientChip(view);
      selectedRecipients = RecipientFactory.getRecipientsFromStrings(NewConversationActivity.this, contactsFragment.getSelectedAddresses(), false);
      updateToggleBar();
    }
  }

  @Override
  public void onContactSelected(final String number) {
    addRecipientChip(number);
    selectedRecipients = RecipientFactory.getRecipientsFromStrings(NewConversationActivity.this, contactsFragment.getSelectedAddresses(), false);
    toolbar.clear();
    updateToggleBar();
  }

  private void removeRecipientChip(View view) {
    SelectedRecipient recipient = (SelectedRecipient) view;
    String address = recipient.getAddress();
    contactsFragment.removeAddress(address);
    expressionElements.removeView(view);
  }

  private void addRecipientChip(String number) {
    Recipients newRecipients = RecipientFactory.getRecipientsFromString(NewConversationActivity.this, number, false);
    if (newRecipients.isEmpty()) {
      Toast.makeText(NewConversationActivity.this, "Not a valid recipient", Toast.LENGTH_LONG).show();
      return;
    }
    if (selectedRecipients == null || selectedRecipients.getRecipient(number) == null) {
      SelectedRecipient recipientChip = new SelectedRecipient(this);
      recipientChip.setAddress(newRecipients.getPrimaryRecipient().getNumber());
      recipientChip.setText(newRecipients.getPrimaryRecipient().getName());
      recipientChip.setOnClickListener(selectedRecipientRemoveListener);
      expressionElements.addView(recipientChip);
    }
  }

  private void handleCreateConversation() {
    final ForstaUser localUser = ForstaUser.getLocalForstaUser(NewConversationActivity.this);
    if (localUser == null) {
      Toast.makeText(NewConversationActivity.this, "Unable to retrieve local user information.", Toast.LENGTH_LONG).show();
      return;
    }
    if (selectedRecipients == null || selectedRecipients.isEmpty()) {
      Toast.makeText(NewConversationActivity.this, "Please select a recipient", Toast.LENGTH_LONG).show();
      return;
    }

    new AsyncTask<String, Void, ForstaDistribution>() {
      @Override
      protected ForstaDistribution doInBackground(String... params) {
        String expression = params[0];
        ForstaDistribution initialDistribution = CcsmApi.getMessageDistribution(NewConversationActivity.this, expression);
        if (initialDistribution.hasWarnings() || initialDistribution.userIds.contains(localUser.getUid()) || !initialDistribution.hasRecipients()) {
          return initialDistribution;
        } else {
          String newExpression = initialDistribution.pretty + " + @" + localUser.getTag();
          return CcsmApi.getMessageDistribution(NewConversationActivity.this, newExpression);
        }
      }

      @Override
      protected void onPostExecute(final ForstaDistribution distribution) {

        if (distribution.hasWarnings()) {
          hideProgressBar();
          Toast.makeText(NewConversationActivity.this, distribution.getWarnings(), Toast.LENGTH_LONG).show();
          return;
        }

        if (distribution.hasRecipients()) {
          final Recipients recipients = RecipientFactory.getRecipientsFromStrings(NewConversationActivity.this, distribution.getRecipients(NewConversationActivity.this), false);
          final ForstaThread forstaThread = DatabaseFactory.getThreadDatabase(NewConversationActivity.this).getThreadForDistribution(distribution.universal);
          if (forstaThread == null) {
            createConversation(DatabaseFactory.getThreadDatabase(NewConversationActivity.this).allocateThread(recipients, distribution), recipients);
          } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(NewConversationActivity.this);
            builder.setTitle("New Conversation")
                .setMessage("Use existing conversation or create new?")
                .setPositiveButton("New", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialogInterface, int i) {
                    createConversation(DatabaseFactory.getThreadDatabase(NewConversationActivity.this).allocateThread(recipients, distribution), recipients);
                  }
                })
                .setNegativeButton("Existing", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialogInterface, int i) {
                    createConversation(forstaThread, recipients);
                  }
                })
                .setCancelable(true)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                  @Override
                  public void onCancel(DialogInterface dialogInterface) {
                    hideProgressBar();
                  }
                })
                .create();
            builder.show();
            showProgressBar();
          }
        } else {
          hideProgressBar();
          Toast.makeText(NewConversationActivity.this, "No recipients found in expression.", Toast.LENGTH_LONG).show();
        }
      }
    }.execute(selectedRecipients.getLocalizedRecipientExpression(localUser.getOrgTag()));
  }

  private void createConversation(ForstaThread forstaThread, Recipients recipients) {
    long threadId = forstaThread.getThreadid();
    Intent intent = new Intent(NewConversationActivity.this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    intent.putExtra(ConversationActivity.TEXT_EXTRA, getIntent().getStringExtra(ConversationActivity.TEXT_EXTRA));
    intent.setDataAndType(getIntent().getData(), getIntent().getType());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
    startActivity(intent);
    finish();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case android.R.id.home:   super.onBackPressed(); return true;
    case R.id.menu_refresh:   handleManualRefresh(); return true;
    case R.id.menu_new_group: handleCreateGroup();   return true;
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

  @Override
  protected boolean onPrepareOptionsPanel(View view, Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();
    inflater.inflate(R.menu.new_conversation_activity, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }
}
