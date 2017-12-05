/**
 * Copyright (C) 2014 Open Whisper Systems
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

import android.accounts.Account;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import io.forsta.ccsm.DrawerFragment;
import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.LoginActivity;
import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.model.ForstaDistribution;
import io.forsta.ccsm.database.model.ForstaOrg;
import io.forsta.ccsm.api.ForstaSyncAdapter;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.jobs.DirectoryRefreshJob;
import io.forsta.securesms.notifications.MessageNotifier;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.KeyCachingService;
import io.forsta.securesms.util.DynamicLanguage;
import io.forsta.securesms.util.DynamicTheme;
import io.forsta.securesms.util.TextSecurePreferences;

public class ConversationListActivity extends PassphraseRequiredActionBarActivity
    implements ConversationListFragment.ConversationSelectedListener
{
  private static final String TAG = ConversationListActivity.class.getSimpleName();
  private static IntentFilter syncIntentFilter = new IntentFilter(ForstaSyncAdapter.FORSTA_SYNC_COMPLETE);
  private BroadcastReceiver syncReceiver;

  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ConversationListFragment fragment;
  private DrawerFragment drawerFragment;
  private LinearLayout syncIndicator;

  private MasterSecret masterSecret;
  private ForstaUser localUser;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
  }

  @Override
  protected void onCreate(Bundle savedState, @NonNull MasterSecret masterSecret) {
    localUser = ForstaUser.getLocalForstaUser(ConversationListActivity.this);
    Log.w(TAG, "User: " + localUser.getFullTag());
    Log.w(TAG, "ID: " + localUser.getUid() + " Device ID: " + TextSecurePreferences.getLocalDeviceId(ConversationListActivity.this));

    this.masterSecret = masterSecret;
    setContentView(R.layout.conversation_list_activity);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
    getSupportActionBar().setCustomView(R.layout.conversation_list_title_view);

    syncIndicator = (LinearLayout) findViewById(R.id.forsta_sync_indicator);
    syncReceiver = new ContactsSyncReceiver();
    registerReceiver(syncReceiver, syncIntentFilter);
    fragment = initFragment(R.id.forsta_conversation_list, new ConversationListFragment(), masterSecret, dynamicLanguage.getCurrentLocale());
    drawerFragment = initFragment(R.id.forsta_drawer_left, new DrawerFragment(), masterSecret, dynamicLanguage.getCurrentLocale());

    RefreshUserOrg task = new RefreshUserOrg();
    task.execute();

    // Force set enable thread trimming to 30 days.
    if (!TextSecurePreferences.isThreadLengthTrimmingEnabled(getApplicationContext())) {
      TextSecurePreferences.setThreadTrimEnabled(getApplicationContext(), true);
    }

    if (ForstaPreferences.getForstaContactSync(this) == -1) {
      syncIndicator.setVisibility(View.VISIBLE);
      Account account = ForstaSyncAdapter.getAccount(getApplicationContext());
      ContentResolver.requestSync(account, ForstaSyncAdapter.AUTHORITY, Bundle.EMPTY);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  protected void onPause() {
    syncIndicator.setVisibility(View.GONE);
    super.onPause();
  }

  @Override
  public void onDestroy() {
    if (syncReceiver != null) unregisterReceiver(syncReceiver);
    super.onDestroy();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.text_secure_normal, menu);

    menu.findItem(R.id.menu_clear_passphrase).setVisible(!TextSecurePreferences.isPasswordDisabled(this));

    inflater.inflate(R.menu.conversation_list, menu);
    MenuItem menuItem = menu.findItem(R.id.menu_search);
    initializeSearch(menuItem);

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  private void initializeSearch(MenuItem searchViewItem) {
    SearchView searchView = (SearchView)MenuItemCompat.getActionView(searchViewItem);
    searchView.setQueryHint(getString(R.string.ConversationListActivity_search));
    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        if (fragment != null) {
          fragment.setQueryFilter(query);
          return true;
        }

        return false;
      }

      @Override
      public boolean onQueryTextChange(String newText) {
        return onQueryTextSubmit(newText);
      }
    });

    MenuItemCompat.setOnActionExpandListener(searchViewItem, new MenuItemCompat.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem menuItem) {
        return true;
      }

      @Override
      public boolean onMenuItemActionCollapse(MenuItem menuItem) {
        if (fragment != null) {
          fragment.resetQueryFilter();
        }

        return true;
      }
    });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case R.id.menu_new_group:         createGroup();           return true;
      case R.id.menu_settings:          handleDisplaySettings(); return true;
      case R.id.menu_clear_passphrase:  handleClearPassphrase(); return true;
      case R.id.menu_mark_all_read:     handleMarkAllRead();     return true;
      case R.id.menu_import_export:     handleImportExport();    return true;
      case R.id.menu_invite:            handleInvite();          return true;
      case R.id.menu_help:              handleHelp();            return true;
      case R.id.menu_logout:            handleLogout();            return true;
      case R.id.menu_directory:         handleDirectoryRefresh();       return true;
      case R.id.menu_linked_devices:    handleLinkedDevices();   return true;
      case R.id.menu_archive:
        onSwitchToArchive();
        return true;
    }

    return false;
  }

  @Override
  public void onActivityResult(final int reqCode, int resultCode, Intent data) {
    Log.w(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);
  }

  @Override
  public void onCreateConversation(long threadId, Recipients recipients, int distributionType) {
    if (recipients == null || recipients.isEmpty()) {
      Toast.makeText(ConversationListActivity.this, "Error. This thread has no recipients. Please remove it.", Toast.LENGTH_LONG).show();
    } else {
      Intent intent = new Intent(this, ConversationActivity.class);
      intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
      intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
      intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

      startActivity(intent);
      overridePendingTransition(R.anim.slide_from_right, R.anim.fade_scale_out);
    }
  }

  @Override
  public void onSwitchToArchive() {
    Intent intent = new Intent(this, ConversationListArchiveActivity.class);
    startActivity(intent);
  }

  private void createGroup() {
    Intent intent = new Intent(this, GroupCreateActivity.class);
    startActivity(intent);
  }

  private void handleLogout() {
    ForstaPreferences.clearLogin(ConversationListActivity.this);
    Intent intent = new Intent(ConversationListActivity.this, LoginActivity.class);
    startActivity(intent);
    finish();
  }

  private void handleDirectoryRefresh() {
    syncIndicator.setVisibility(View.VISIBLE);
    ApplicationContext.getInstance(getApplicationContext()).getJobManager().add(new DirectoryRefreshJob(getApplicationContext()));
  }

  private void handleDisplaySettings() {
    Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
    startActivity(preferencesIntent);
  }

  private void handleLinkedDevices() {
    Intent intent = new Intent(this, DeviceActivity.class);
    startActivity(intent);
  }

  private void handleClearPassphrase() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    startService(intent);
  }

  private void handleImportExport() {
    startActivity(new Intent(this, ImportExportActivity.class));
  }

  private void handleMarkAllRead() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getThreadDatabase(ConversationListActivity.this).setAllThreadsRead();
        MessageNotifier.updateNotification(ConversationListActivity.this, masterSecret);
        return null;
      }
    }.execute();
  }

  private void handleInvite() {
    startActivity(new Intent(this, InviteActivity.class));
  }

  private void handleHelp() {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://support.forsta.io")));
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, R.string.ConversationListActivity_there_is_no_browser_installed_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  private void showValidationError() {
    Toast.makeText(ConversationListActivity.this, "An error has occured validating login.", Toast.LENGTH_LONG).show();
  }

  private class ContactsSyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.d(TAG, "Sync complete");
      syncIndicator.setVisibility(View.GONE);
      RecipientFactory.clearCache(ConversationListActivity.this);
      fragment.getListAdapter().notifyDataSetChanged();
    }
  }

  public class RefreshUserOrg extends AsyncTask<Void, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Void... voids) {
      CcsmApi.tokenNeedsRefresh(ConversationListActivity.this);

      JSONObject userResponse = CcsmApi.getForstaUser(ConversationListActivity.this);
      if (userResponse.has("id")) {
        ForstaPreferences.setForstaUser(ConversationListActivity.this, userResponse.toString());
        JSONObject orgResponse = CcsmApi.getOrg(ConversationListActivity.this);
        if (orgResponse.has("id")) {
          ForstaPreferences.setForstaOrg(ConversationListActivity.this, orgResponse.toString());
        } else {
          return orgResponse;
        }
      } else {
        return userResponse;
      }
      return null;
    }

    @Override
    protected void onPostExecute(JSONObject errorResponse) {
      if (errorResponse != null) {
        try {
          if (errorResponse.getString("error").equals("401")) {
            Log.e(TAG, "Not Authorized");
            handleLogout();
          }
        } catch (JSONException e) {
          showValidationError();
        }
      } else {
        ForstaOrg forstaOrg = ForstaOrg.fromJsonString(ForstaPreferences.getForstaOrg(ConversationListActivity.this));
        if (forstaOrg != null) {
          TextView title = (TextView) getSupportActionBar().getCustomView().findViewById(R.id.conversation_list_title);
          title.setText(forstaOrg.getName().toLowerCase());
          if (forstaOrg.getOffTheRecord()) {
            ForstaPreferences.setOffTheRecord(ConversationListActivity.this, true);
          }
        }
      }
    }
  }
}
