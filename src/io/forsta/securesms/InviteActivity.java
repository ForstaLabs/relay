package io.forsta.securesms;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.AnimRes;
import android.support.annotation.NonNull;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import io.forsta.securesms.components.ContactFilterToolbar;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.sms.OutgoingTextMessage;
import io.forsta.securesms.util.ViewUtil;
import io.forsta.securesms.util.task.ProgressDialogAsyncTask;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.concurrent.ExecutionException;

import io.forsta.securesms.database.RecipientPreferenceDatabase;
import io.forsta.securesms.util.concurrent.ListenableFuture;

public class InviteActivity extends PassphraseRequiredActionBarActivity implements ContactSelectionListFragment.OnContactSelectedListener {

  private MasterSecret masterSecret;
  private ContactSelectionListFragment contactsFragment;
  private EditText                     inviteText;
  private View                         shareButton;
  private View                         smsButton;
  private ViewGroup                    smsSendFrame;
  private Button                       smsSendButton;
  private Button                       smsCancelButton;
  private Animation                    slideInAnimation;
  private Animation                    slideOutAnimation;
  private ContactFilterToolbar contactFilter;
  private ImageView                    heart;

  @Override
  protected void onCreate(Bundle savedInstanceState, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;

    getIntent().putExtra(ContactSelectionListFragment.DISPLAY_MODE, ContactSelectionListFragment.DISPLAY_MODE_OTHER_ONLY);
    getIntent().putExtra(ContactSelectionListFragment.MULTI_SELECT, true);
    getIntent().putExtra(ContactSelectionListFragment.REFRESHABLE, false);

    super.onCreate(savedInstanceState, masterSecret);
    setContentView(R.layout.invite_activity);
    getSupportActionBar().setTitle(R.string.AndroidManifest__invite_friends);

    initializeResources();
  }

  private void initializeResources() {
    slideInAnimation  = loadAnimation(R.anim.slide_from_bottom);
    slideOutAnimation = loadAnimation(R.anim.slide_to_bottom);
    shareButton       = ViewUtil.findById(this, R.id.share_button);
    smsButton         = ViewUtil.findById(this, R.id.sms_button);
    inviteText        = ViewUtil.findById(this, R.id.invite_text);
    smsSendFrame      = ViewUtil.findById(this, R.id.sms_send_frame);
    smsSendButton     = ViewUtil.findById(this, R.id.send_sms_button);
    smsCancelButton   = ViewUtil.findById(this, R.id.cancel_sms_button);
    contactFilter     = ViewUtil.findById(this, R.id.contact_filter);
    heart             = ViewUtil.findById(this, R.id.heart);
    contactsFragment  = (ContactSelectionListFragment)getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);

    inviteText.setText(getString(R.string.InviteActivity_lets_switch_to_signal, "http://forsta.io"));
    updateSmsButtonText();

    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      heart.getViewTreeObserver().addOnPreDrawListener(new HeartPreDrawListener());
    }
    contactsFragment.setOnContactSelectedListener(this);
    shareButton.setOnClickListener(new ShareClickListener());
    smsButton.setOnClickListener(new SmsClickListener());
    smsCancelButton.setOnClickListener(new SmsCancelClickListener());
    smsSendButton.setOnClickListener(new SmsSendClickListener());
    contactFilter.setOnFilterChangedListener(new ContactFilterChangedListener());
    contactFilter.setToolbarHintSms();
  }

  private Animation loadAnimation(@AnimRes int animResId) {
    final Animation animation = AnimationUtils.loadAnimation(this, animResId);
    animation.setInterpolator(new FastOutSlowInInterpolator());
    return animation;
  }

  @Override
  public void onContactSelected(String number) {
    updateSmsButtonText();
  }

  @Override
  public void onContactDeselected(String number) {
    updateSmsButtonText();
  }

  private void sendSmsInvites() {
    new SendSmsInvitesAsyncTask(this, inviteText.getText().toString())
        .execute(contactsFragment.getSelectedAddresses()
                                 .toArray(new String[contactsFragment.getSelectedAddresses().size()]));
  }

  private void updateSmsButtonText() {
    smsSendButton.setText(getResources().getQuantityString(R.plurals.InviteActivity_send_sms_to_friends,
                                                           contactsFragment.getSelectedAddresses().size(),
                                                           contactsFragment.getSelectedAddresses().size()));
    smsSendButton.setEnabled(!contactsFragment.getSelectedAddresses().isEmpty());
  }

  @Override public void onBackPressed() {
    if (smsSendFrame.getVisibility() == View.VISIBLE) {
      cancelSmsSelection();
    } else {
      super.onBackPressed();
    }
  }

  private void cancelSmsSelection() {
    contactsFragment.reset();
    updateSmsButtonText();
    ViewUtil.animateOut(smsSendFrame, slideOutAnimation, View.GONE);
  }

  private class ShareClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      Intent sendIntent = new Intent();
      sendIntent.setAction(Intent.ACTION_SEND);
      sendIntent.putExtra(Intent.EXTRA_TEXT, inviteText.getText().toString());
      sendIntent.setType("text/plain");
      if (sendIntent.resolveActivity(getPackageManager()) != null) {
        startActivity(Intent.createChooser(sendIntent, getString(R.string.InviteActivity_invite_to_signal)));
      } else {
        Toast.makeText(InviteActivity.this, R.string.InviteActivity_no_app_to_share_to, Toast.LENGTH_LONG).show();
      }
    }
  }

  private class SmsClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      ViewUtil.animateIn(smsSendFrame, slideInAnimation);
    }
  }

  private class SmsCancelClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      cancelSmsSelection();
    }
  }

  private class SmsSendClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      new AlertDialog.Builder(InviteActivity.this)
          .setTitle(getResources().getQuantityString(R.plurals.InviteActivity_send_sms_invites,
                                                     contactsFragment.getSelectedAddresses().size(),
                                                     contactsFragment.getSelectedAddresses().size()))
          .setMessage(inviteText.getText().toString())
          .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
              sendSmsInvites();
            }
          })
          .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
              dialog.dismiss();
            }
          })
          .show();
    }
  }

  private class ContactFilterChangedListener implements ContactFilterToolbar.OnFilterChangedListener {
    @Override
    public void onFilterChanged(String filter) {
      contactsFragment.setQueryFilter(filter);
    }
  }

  private class HeartPreDrawListener implements OnPreDrawListener {
    @Override
    @TargetApi(VERSION_CODES.LOLLIPOP)
    public boolean onPreDraw() {
      heart.getViewTreeObserver().removeOnPreDrawListener(this);
      final int w = heart.getWidth();
      final int h = heart.getHeight();
      Animator reveal = ViewAnimationUtils.createCircularReveal(heart,
                                                                w / 2, h,
                                                                0, (float)Math.sqrt(h*h + (w*w/4)));
      reveal.setInterpolator(new FastOutSlowInInterpolator());
      reveal.setDuration(800);
      reveal.start();
      return false;
    }
  }

  private class SendSmsInvitesAsyncTask extends ProgressDialogAsyncTask<String,Void,Void> {
    private final String message;

    public SendSmsInvitesAsyncTask(Context context, String message) {
      super(context, R.string.InviteActivity_sending, R.string.InviteActivity_sending);
      this.message = message;
    }

    @Override
    protected Void doInBackground(String... numbers) {
      final Context context = getContext();
      if (context == null) return null;

      for (String number : numbers) {
        Recipients recipients = RecipientFactory.getRecipientsFromString(context, number, false);

        if (recipients.getPrimaryRecipient() != null) {
          MessageSender.sendSmsInvite(context, masterSecret, new OutgoingTextMessage(recipients, message, -1));
        }
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
      super.onPostExecute(aVoid);
      final Context context = getContext();
      if (context == null) return;

      ViewUtil.animateOut(smsSendFrame, slideOutAnimation, View.GONE).addListener(new ListenableFuture.Listener<Boolean>() {
        @Override
        public void onSuccess(Boolean result) {
          contactsFragment.reset();
        }

        @Override
        public void onFailure(ExecutionException e) {}
      });
      Toast.makeText(context, R.string.InviteActivity_invitations_sent, Toast.LENGTH_LONG).show();
    }
  }
}
