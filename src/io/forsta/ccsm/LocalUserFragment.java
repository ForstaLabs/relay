package io.forsta.ccsm;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.R;
import io.forsta.securesms.color.MaterialColor;
import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.contacts.avatars.ContactPhotoFactory;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.util.Dialogs;

/**
 * Created by jlewis on 5/19/17.
 */

public class LocalUserFragment extends Fragment {
  private static final String TAG = LocalUserFragment.class.getSimpleName();
  private MasterSecret masterSecret;
  private TextView userName;
  private TextView orgTag;
  private AvatarImageView contactPhotoImage;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.masterSecret = getArguments().getParcelable("master_secret");
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

    final View view = inflater.inflate(R.layout.forsta_local_user_fragment, container, false);
    userName = (TextView) view.findViewById(R.id.local_user);
    orgTag = (TextView) view.findViewById(R.id.local_org_tag);
    contactPhotoImage = (AvatarImageView) view.findViewById(R.id.local_photo_image);

    final ForstaUser user = ForstaUser.getLocalForstaUser(getActivity());
    if (user != null) {
      userName.setText(user.getName());
      orgTag.setText("@" + user.getTag() + ": " + user.getOrgTag());
      if (!TextUtils.isEmpty(user.getAvatar())) {
        Glide.with(getActivity()).load("https://www.gravatar.com/avatar/" + user.getAvatar()).asBitmap().into(contactPhotoImage);
      } else {
        contactPhotoImage.setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(user.getName()).asDrawable(getActivity(), MaterialColor.GREY.toConversationColor(getActivity())));
      }
    }

    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
  }
}
