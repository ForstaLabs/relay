package io.forsta.ccsm;

import android.os.Bundle;
import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.PassphraseRequiredActionBarActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.RecipientPreferenceActivity;
import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.recipients.Recipients;
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
  private TextView threadRecipients;
  private EditText forstaTitle;
  private TextView forstaUid;
  private ImageButton forstaSaveTitle;

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

    blockedIndicator = (TextView) toolbar.findViewById(R.id.blocked_indicator);
    blockedIndicator.setVisibility(View.GONE);
    avatar = (AvatarImageView) toolbar.findViewById(R.id.avatar);
    title = (TextView) toolbar.findViewById(R.id.name);
    threadRecipients = (TextView) findViewById(R.id.forsta_thread_recipients);
    forstaTitle = (EditText) findViewById(R.id.forsta_thread_title);
    forstaUid = (TextView) findViewById(R.id.forsta_thread_uid);
    forstaSaveTitle = (ImageButton) findViewById(R.id.forsta_title_save_button);
    forstaSaveTitle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        DatabaseFactory.getThreadDatabase(ThreadPreferenceActivity.this).updateThreadTitle(threadId, forstaTitle.getText().toString());
        Toast.makeText(ThreadPreferenceActivity.this, "Conversation title saved", Toast.LENGTH_LONG).show();
      }
    });
  }

  private void initializeThread() {
    Recipients recipients = DatabaseFactory.getThreadDatabase(ThreadPreferenceActivity.this).getRecipientsForThreadId(threadId);
    threadRecipients.setText(recipients.toShortString());
    ForstaThread thread = DatabaseFactory.getThreadDatabase(ThreadPreferenceActivity.this).getForstaThread(threadId);
    title.setText(TextUtils.isEmpty(thread.title) ? recipients.toShortString() : thread.title);
    forstaUid.setText(thread.uid);
    if (!BuildConfig.FORSTA_API_URL.contains("dev")) {
      LinearLayout debugLayout = (LinearLayout) findViewById(R.id.forsta_thread_debug_details);
      debugLayout.setVisibility(View.VISIBLE);
    }
  }
}
