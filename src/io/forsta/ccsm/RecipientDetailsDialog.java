package io.forsta.ccsm;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import cn.carbswang.android.numberpickerview.library.NumberPickerView;
import io.forsta.securesms.R;
import io.forsta.securesms.recipients.Recipient;

/**
 * Created by jlewis on 1/9/18.
 */

public class RecipientDetailsDialog extends AlertDialog {
  protected RecipientDetailsDialog(Context context) {
    super(context);
  }

  protected RecipientDetailsDialog(Context context, int theme) {
    super(context, theme);
  }

  protected RecipientDetailsDialog(Context context, boolean cancelable, final OnCancelListener cancelListener) {
    super(context, cancelable, cancelListener);
  }

  public static void show(Context context, Recipient recipient) {
    View view = createView(context, recipient);
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setView(view);
    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  public static View createView(Context context, Recipient recipient) {
    final LayoutInflater inflater = LayoutInflater.from(context);
    View view = inflater.inflate(R.layout.recipient_details, null);
    TextView name = (TextView) view.findViewById(R.id.recipient_details_name);
    TextView slug = (TextView) view.findViewById(R.id.recipient_details_slug);
    TextView phone = (TextView) view.findViewById(R.id.recipient_details_phone);
    TextView phoneLabel = (TextView) view.findViewById(R.id.recipient_details_phone_label);
    TextView email = (TextView) view.findViewById(R.id.recipient_details_email);
    ImageView avatar = (ImageView) view.findViewById(R.id.recipient_details_avatar);

    name.setText(recipient.getName());
    slug.setText(recipient.getFullTag());
    if (!TextUtils.isEmpty(recipient.getPhone())) {
      phone.setText(recipient.getPhone());
      phoneLabel.setVisibility(View.VISIBLE);
    }
    email.setText(recipient.getEmail());
    avatar.setImageDrawable(recipient.getContactPhoto().asDrawable(context, recipient.getColor().toActionBarColor(context)));
    return view;
  }

  public interface OnClickListener {
    public void onClick(int expirationTime);
  }
}
