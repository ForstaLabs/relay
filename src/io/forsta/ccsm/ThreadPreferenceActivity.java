package io.forsta.ccsm;

import android.os.Bundle;
import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.securesms.PassphraseRequiredActionBarActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.util.DynamicLanguage;
import io.forsta.securesms.util.DynamicNoActionBarTheme;
import io.forsta.securesms.util.DynamicTheme;

public class ThreadPreferenceActivity extends PassphraseRequiredActionBarActivity {
  public static final String TAG = ThreadPreferenceActivity.class.getSimpleName();
  public static final String THREAD_ID_EXTRA = "thread_id";

  private final DynamicTheme dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private long threadId;
  private AvatarImageView avatar;
  private Toolbar           toolbar;
  private TextView          title;
  private TextView          blockedIndicator;

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle instanceState, @NonNull MasterSecret masterSecret) {
    setContentView(R.layout.activity_thread_preference);
    threadId = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    initializeToolbar();
    initializeThread();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case android.R.id.home:
        super.onBackPressed();
        return true;
    }

    return false;
  }

  private void initializeToolbar() {
    this.toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowTitleEnabled(false);

    this.avatar           = (AvatarImageView) toolbar.findViewById(R.id.avatar);
    this.title            = (TextView) toolbar.findViewById(R.id.name);
    this.blockedIndicator = (TextView) toolbar.findViewById(R.id.blocked_indicator);
    this.blockedIndicator.setVisibility(View.GONE);
  }

  private void initializeThread() {
    ForstaThread thread = DatabaseFactory.getThreadDatabase(ThreadPreferenceActivity.this).getForstaThread(threadId);
    title.setText(TextUtils.isEmpty(thread.title) ? "No Title" : thread.title);

  }
}
