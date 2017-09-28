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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import io.forsta.ccsm.api.ForstaSyncAdapter;
import io.forsta.securesms.components.ContactFilterToolbar;
import io.forsta.securesms.components.ContactFilterToolbar.OnFilterChangedListener;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.util.DirectoryHelper;
import io.forsta.securesms.util.DynamicLanguage;
import io.forsta.securesms.util.DynamicNoActionBarTheme;
import io.forsta.securesms.util.DynamicTheme;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.ViewUtil;

import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Base activity container for selecting a list of contacts.
 *
 * @author Moxie Marlinspike
 *
 */
public abstract class ContactSelectionActivity extends PassphraseRequiredActionBarActivity
                                               implements SwipeRefreshLayout.OnRefreshListener,
                                                          ContactSelectionListFragment.OnContactSelectedListener
{
  private static final String TAG = ContactSelectionActivity.class.getSimpleName();

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();
  private MasterSecret masterSecret;

  protected ContactSelectionListFragment contactsFragment;
  protected ImageButton createConversationButton;
  protected TextView recipientExpression;
  protected ContactFilterToolbar toolbar;
  protected ProgressBar searchProgress;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    if (!getIntent().hasExtra(ContactSelectionListFragment.DISPLAY_MODE)) {
      getIntent().putExtra(ContactSelectionListFragment.DISPLAY_MODE,
                           TextSecurePreferences.isSmsEnabled(this)
                           ? ContactSelectionListFragment.DISPLAY_MODE_ALL
                           : ContactSelectionListFragment.DISPLAY_MODE_PUSH_ONLY);
    }

    setContentView(R.layout.contact_selection_activity);
    boolean syncing = ContentResolver.isSyncActive(ForstaSyncAdapter.getAccount(ContactSelectionActivity.this), ForstaSyncAdapter.AUTHORITY);
    if (syncing) {
      Log.d(TAG, "Show sync indicator");
    }

    createConversationButton = (ImageButton) findViewById(R.id.contact_selection_confirm_button);
    recipientExpression = (TextView) findViewById(R.id.forsta_input_recipient_expression);
    searchProgress = (ProgressBar) findViewById(R.id.contact_search_progress);

    initializeToolbar();
    initializeResources();
    initializeSearch();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  protected ContactFilterToolbar getToolbar() {
    return toolbar;
  }

  private void initializeToolbar() {
    this.toolbar = ViewUtil.findById(this, R.id.toolbar);
    setSupportActionBar(toolbar);

    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
    this.toolbar.hideSearch();
  }

  private void initializeResources() {
    contactsFragment = (ContactSelectionListFragment) getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
    contactsFragment.setOnContactSelectedListener(this);
    contactsFragment.setOnRefreshListener(this);
    contactsFragment.setOnSearchResultsCountChangedListener(new ContactSelectionListFragment.OnSearchResultsCountChanged() {
      @Override
      public void onSearchResultsCountChanged(int count) {
        if (count < 1) {
          toolbar.displaySearch();
        } else {
          toolbar.hideSearch();
        }
      }
    });
  }

  private void initializeSearch() {
    toolbar.setOnFilterChangedListener(new OnFilterChangedListener() {
      @Override public void onFilterChanged(String filter) {
        if (filter.startsWith("@")) {
          filter = filter.substring(1, filter.length());
        }
        contactsFragment.setQueryFilter(filter);
      }
    });
  }

  @Override
  public void onRefresh() {
    new RefreshDirectoryTask(this).execute(getApplicationContext());
  }

  @Override
  public void onContactSelected(String number) {}

  @Override
  public void onContactDeselected(String number) {}

  private class RefreshDirectoryTask extends AsyncTask<Context, Void, Void> {

    private final WeakReference<ContactSelectionActivity> activity;
    private final MasterSecret masterSecret;

    private RefreshDirectoryTask(ContactSelectionActivity activity) {
      this.activity     = new WeakReference<>(activity);
      this.masterSecret = activity.masterSecret;
    }


    @Override
    protected Void doInBackground(Context... params) {
      try {
        DirectoryHelper.refreshDirectory(params[0], masterSecret);
      } catch (IOException e) {
        e.printStackTrace();
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void result) {
      ContactSelectionActivity activity = this.activity.get();
      sendBroadcast(new Intent(ForstaSyncAdapter.FORSTA_SYNC_COMPLETE));

      if (activity != null && !activity.isFinishing()) {
        activity.toolbar.clear();
        activity.contactsFragment.resetQueryFilter();
      }
    }
  }
}
