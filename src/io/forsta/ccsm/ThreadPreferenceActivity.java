package io.forsta.ccsm;

import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.preference.PreferenceFragment;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.MuteDialog;
import io.forsta.securesms.PassphraseRequiredActionBarActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.color.MaterialColor;
import io.forsta.securesms.color.MaterialColors;
import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.ThreadPreferenceDatabase;
import io.forsta.securesms.preferences.AdvancedRingtonePreference;
import io.forsta.securesms.preferences.ColorPreference;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.DynamicLanguage;
import io.forsta.securesms.util.DynamicNoActionBarTheme;
import io.forsta.securesms.util.DynamicTheme;

public class ThreadPreferenceActivity extends PassphraseRequiredActionBarActivity {
  public static final String TAG = ThreadPreferenceActivity.class.getSimpleName();
  public static final String THREAD_ID_EXTRA = "thread_id";

  private static final String PREFERENCE_MUTED    = "pref_key_thread_mute";
  private static final String PREFERENCE_TONE     = "pref_key_thread_ringtone";
  private static final String PREFERENCE_VIBRATE  = "pref_key_thread_vibrate";
  private static final String PREFERENCE_BLOCK    = "pref_key_thread_block";
  private static final String PREFERENCE_COLOR    = "pref_key_thread_color";
  private static final String PREFERENCE_IDENTITY = "pref_key_thread_identity";


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
  private TextView forstaExpression;
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

    Bundle bundle = new Bundle();
    bundle.putLong(THREAD_ID_EXTRA, threadId);
    initFragment(R.id.thread_preference_fragment, new ThreadPreferenceFragment(), masterSecret, null, bundle);
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

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.thread_preference_fragment);
    fragment.onActivityResult(requestCode, resultCode, data);
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
    forstaExpression = (TextView) findViewById(R.id.forsta_thread_expression);
    forstaSaveTitle = (ImageButton) findViewById(R.id.forsta_title_save_button);
    forstaSaveTitle.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        DatabaseFactory.getThreadDatabase(ThreadPreferenceActivity.this).updateThreadTitle(threadId, forstaTitle.getText().toString());
        forstaSaveTitle.setVisibility(View.GONE);
        Toast.makeText(ThreadPreferenceActivity.this, "Conversation title saved", Toast.LENGTH_LONG).show();
      }
    });
  }

  private void initializeThread() {
    Recipients recipients = DatabaseFactory.getThreadDatabase(ThreadPreferenceActivity.this).getRecipientsForThreadId(threadId);
    threadRecipients.setText(getRecipientData(recipients));
    ForstaThread thread = DatabaseFactory.getThreadDatabase(ThreadPreferenceActivity.this).getForstaThread(threadId);
    forstaTitle.setHint("Add a Title");
    forstaTitle.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s.length() > 0) {
          forstaSaveTitle.setVisibility(View.VISIBLE);
        } else {
          forstaSaveTitle.setVisibility(View.GONE);
        }
      }

      @Override
      public void afterTextChanged(Editable s) {

      }
    });
    title.setText(TextUtils.isEmpty(thread.title) ? recipients.toShortString() : thread.title);

    if (BuildConfig.FLAVOR.equals("dev")) {
      forstaUid.setText(thread.uid);
      forstaExpression.setText(thread.distribution);
      LinearLayout debugLayout = (LinearLayout) findViewById(R.id.forsta_thread_debug_details);
      debugLayout.setVisibility(View.VISIBLE);
    }
  }

  private String getRecipientData(Recipients recipients) {
    List<String> contacts = new ArrayList();
    for (Recipient recipient : recipients) {
      StringBuilder sb = new StringBuilder();
      sb.append(recipient.getName()).append("(").append(recipient.getFullTag()).append(")");
      contacts.add(sb.toString());
    }
    return TextUtils.join(", ", contacts);
  }

  public static class ThreadPreferenceFragment extends PreferenceFragment {
    private MasterSecret masterSecret;
    private long threadId;
    private CheckBoxPreference mutePreference;
    private AdvancedRingtonePreference notificationPreference;
    private ListPreference vibratePreference;
    private ColorPreference colorPreference;
    private Preference blockPreference;


    @Override
    public void onCreate(Bundle icicle) {
      super.onCreate(icicle);

      this.masterSecret = getArguments().getParcelable("master_secret");
      this.threadId = getArguments().getLong(THREAD_ID_EXTRA);

      addPreferencesFromResource(R.xml.thread_preferences);
      initializePreferences();

      this.findPreference(PREFERENCE_MUTED)
          .setOnPreferenceClickListener(new MuteClickListener());
      this.findPreference(PREFERENCE_COLOR)
          .setOnPreferenceChangeListener(new ColorChangeListener());
//      this.findPreference(PREFERENCE_TONE)
//          .setOnPreferenceChangeListener(new RingtoneChangeListener());
//      this.findPreference(PREFERENCE_VIBRATE)
//          .setOnPreferenceChangeListener(null);
//      this.findPreference(PREFERENCE_BLOCK)
//          .setOnPreferenceClickListener(null);

    }

    private void initializePreferences() {
      ThreadPreferenceDatabase db = DatabaseFactory.getThreadPreferenceDatabase(getActivity());
      ThreadPreferenceDatabase.ThreadPreference threadPreference = db.getThreadPreferences(threadId);

      mutePreference = (CheckBoxPreference) this.findPreference(PREFERENCE_MUTED);
      colorPreference = (ColorPreference) this.findPreference(PREFERENCE_COLOR);
      colorPreference.setChoices(MaterialColors.CONVERSATION_PALETTE.asConversationColorArray(getActivity()));
      mutePreference.setChecked(threadPreference.isMuted());
      colorPreference.setValue(threadPreference.getColor().toConversationColor(getActivity()));

//      notificationPreference = (AdvancedRingtonePreference) this.findPreference(PREFERENCE_TONE);
//      notificationPreference.setSummary(R.string.preferences__default);
//      vibratePreference = (ListPreference) this.findPreference(PREFERENCE_VIBRATE);

      //      if (threadPreference.getNotification() != null) {
//          Ringtone tone = RingtoneManager.getRingtone(getActivity(), threadPreference.getNotification());
//          if (tone != null) {
//            notificationPreference.setSummary(tone.getTitle(getActivity()));
//            notificationPreference.setCurrentRingtone(threadPreference.getNotification());
//          }
//        }

    }

    private class RingtoneChangeListener implements Preference.OnPreferenceChangeListener {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        String value = (String)newValue;

        final Uri uri;

        if (TextUtils.isEmpty(value) || Settings.System.DEFAULT_NOTIFICATION_URI.toString().equals(value)) {
          uri = null;
        } else {
          uri = Uri.parse(value);
        }

        new AsyncTask<Uri, Void, Void>() {
          @Override
          protected Void doInBackground(Uri... params) {
            DatabaseFactory.getThreadPreferenceDatabase(getActivity()).setNotification(threadId, params[0]);
            return null;
          }

          @Override
          protected void onPostExecute(Void aVoid) {
            notificationPreference.setCurrentRingtone(uri);
            if (uri == null) {
              notificationPreference.setSummary(R.string.preferences__default);
            } else {
              Ringtone tone = RingtoneManager.getRingtone(getActivity(), uri);
              if (tone != null) {
                notificationPreference.setSummary(tone.getTitle(getActivity()));
              }
            }
          }
        }.execute(uri);

        return false;
      }
    }

    private class VibrateChangeListener implements Preference.OnPreferenceChangeListener {

      @Override
      public boolean onPreferenceChange(Preference preference, Object o) {
        return false;
      }
    }

    private class MuteClickListener implements Preference.OnPreferenceClickListener {

      @Override
      public boolean onPreferenceClick(Preference preference) {

        ThreadPreferenceDatabase.ThreadPreference threadPreference = DatabaseFactory.getThreadPreferenceDatabase(getActivity()).getThreadPreferences(threadId);
        if (threadPreference != null && threadPreference.isMuted()) {
          new AsyncTask<Long, Void, Void>() {

            @Override
            protected Void doInBackground(Long... params) {
              DatabaseFactory.getThreadPreferenceDatabase(getActivity()).setMuteUntil(threadId, params[0]);
              return null;
            }
          }.execute(0L);
        } else {
          MuteDialog.show(getActivity(), new MuteDialog.MuteSelectionListener() {
            @Override
            public void onMuted(long until) {
              new AsyncTask<Long, Void, Void>() {

                @Override
                protected Void doInBackground(Long... params) {
                  DatabaseFactory.getThreadPreferenceDatabase(getActivity()).setMuteUntil(threadId, params[0]);
                  return null;
                }
              }.execute(until);
            }
          });
        }




        return false;
      }
    }

    private class BlockChangeListener implements Preference.OnPreferenceChangeListener {

      @Override
      public boolean onPreferenceChange(Preference preference, Object o) {
        return false;
      }
    }

    private class ColorChangeListener implements Preference.OnPreferenceChangeListener {

      @Override
      public boolean onPreferenceChange(Preference preference, Object o) {
        final int           value         = (Integer) o;
        final MaterialColor selectedColor = MaterialColors.CONVERSATION_PALETTE.getByColor(getActivity(), value);
        DatabaseFactory.getThreadPreferenceDatabase(getActivity()).setColor(threadId, selectedColor);
        return true;
      }
    }
  }
}
