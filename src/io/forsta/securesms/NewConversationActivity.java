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
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.model.ForstaDistribution;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.database.loaders.ConversationListLoader;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.TextSecurePreferences;

/**
 * Activity container for starting a new conversation.
 *
 * @author Moxie Marlinspike
 *
 */
public class NewConversationActivity extends ContactSelectionActivity {

  private static final String TAG = NewConversationActivity.class.getSimpleName();
  private Map<String, String> slugs;

  @Override
  public void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    super.onCreate(bundle, masterSecret);

    getToolbar().setShowCustomNavigationButton(false);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    slugs = DbFactory.getContactDb(NewConversationActivity.this).getContactSlugs();
  }

  @Override
  public void onContactSelected(final String number) {
    StringBuilder sb = new StringBuilder();
    ForstaUser localUser = ForstaUser.getLocalForstaUser(NewConversationActivity.this);
    final Recipients recipients;

    // XXXX Groups are currently used for non-user tags and have no device address associated with them.
    if (GroupUtil.isEncodedGroup(number)) {
      GroupDatabase.GroupRecord group = DatabaseFactory.getGroupDatabase(NewConversationActivity.this).getGroup(number);
      recipients = RecipientFactory.getRecipientsFromStrings(NewConversationActivity.this, group.getMembers(), false);
      // If tag contains the current user, don't add them to the expression.
      sb.append(group.getExpression(localUser));
    } else {
      ForstaUser user = DbFactory.getContactDb(NewConversationActivity.this).getUserByAddress(number);
      recipients = RecipientFactory.getRecipientsFromString(NewConversationActivity.this, number, false);
      // Always add self to conversations.
      sb.append(user.getFullTag() + " " + localUser.getFullTag());
    }

    new AsyncTask<String, Void, ForstaDistribution>() {
      @Override
      protected ForstaDistribution doInBackground(String... params) {
        String expression = params[0];
        JSONObject result = CcsmApi.getDistribution(NewConversationActivity.this, expression);
        return ForstaDistribution.fromJson(result);
      }

      @Override
      protected void onPostExecute(ForstaDistribution distribution) {
        if (distribution.hasRecipients()) {
          ForstaThread forstaThread = DatabaseFactory.getThreadDatabase(NewConversationActivity.this).getThreadForDistribution(distribution.universal);
          long threadId = -1L;
          if (forstaThread != null) {
            threadId = forstaThread.threadid;
          }

          Intent intent = new Intent(NewConversationActivity.this, ConversationActivity.class);
          intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
          intent.putExtra(ConversationActivity.TEXT_EXTRA, getIntent().getStringExtra(ConversationActivity.TEXT_EXTRA));
          intent.setDataAndType(getIntent().getData(), getIntent().getType());
          intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
          intent.putExtra(ConversationActivity.DISTRIBUTION_EXPRESSION_EXTRA, distribution.universal);
          intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
          startActivity(intent);
          finish();
        } else {
          Toast.makeText(NewConversationActivity.this, "No recipients found for selection", Toast.LENGTH_LONG).show();
        }
      }
    }.execute(sb.toString());
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
}
