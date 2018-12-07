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
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;

import io.forsta.ccsm.api.ForstaSyncAdapter;
import io.forsta.ccsm.components.FlowLayout;
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
                                                          ContactSelectionListFragment.OnContactSelectedListener, ContactSelectionListFragment.OnSearchResultsCountChanged
{
  private static final String TAG = ContactSelectionActivity.class.getSimpleName();

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();
  protected MasterSecret masterSecret;

  protected ContactSelectionListFragment contactsFragment;
  protected ContactFilterToolbar toolbar;
  protected ProgressBar progressBar;
  protected FlowLayout expressionElements;
  protected RadioGroup threadType;

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

    progressBar = (ProgressBar) findViewById(R.id.contact_search_progress);
    expressionElements = (FlowLayout) findViewById(R.id.contact_expression_elements);
    threadType = (RadioGroup) findViewById(R.id.new_conversation_thread_type);

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
  }

  private void initializeResources() {
    contactsFragment = (ContactSelectionListFragment) getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
    contactsFragment.setOnContactSelectedListener(this);
    contactsFragment.setOnRefreshListener(this);
    contactsFragment.setOnSearchResultsCountChangedListener(this);
  }

  private void initializeSearch() {
    toolbar.setOnFilterChangedListener(new OnFilterChangedListener() {
      @Override public void onFilterChanged(String filter) {
        if (filter.startsWith("@")) {
          filter = filter.substring(1, filter.length());
        }
        contactsFragment.setQueryFilter(filter);
        updateToggleBar();
      }
    });
  }

  protected void updateToggleBar() {
    toolbar.updateToggleState(!contactsFragment.getSelectedAddresses().isEmpty(), contactsFragment.getSearchResultCount() > 0);
  }

  @Override
  public void onRefresh() {
    new RefreshDirectoryTask(this, false).execute(ContactSelectionActivity.this);
  }

  public void resetDirectory() {
    new RefreshDirectoryTask(this, true).execute(ContactSelectionActivity.this);
  }

  @Override
  public void onContactSelected(String number) {}

  @Override
  public void onContactDeselected(String number) {}

  @Override
  public void onSearchResultsCountChanged(int count) {
    if (count == 0) {
      updateToggleBar();
    }
  }

  protected void showProgressBar() {
    progressBar.setVisibility(View.VISIBLE);
    getSupportFragmentManager().beginTransaction().hide(contactsFragment).commit();
  }

  protected void hideProgressBar() {
    progressBar.setVisibility(View.GONE);
    getSupportFragmentManager().beginTransaction().show(contactsFragment).commit();
  }

  private class RefreshDirectoryTask extends AsyncTask<Context, Void, Void> {

    private final WeakReference<ContactSelectionActivity> activity;
    private final MasterSecret masterSecret;
    private final boolean reset;

    private RefreshDirectoryTask(ContactSelectionActivity activity, boolean reset) {
      this.activity     = new WeakReference<>(activity);
      this.masterSecret = activity.masterSecret;
      this.reset = reset;
    }


    @Override
    protected Void doInBackground(Context... params) {
      try {
        if (reset) {
          DirectoryHelper.resetDirectory(params[0]);
        } else {
          DirectoryHelper.refreshDirectory(params[0]);
        }

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
