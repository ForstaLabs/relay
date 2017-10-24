package io.forsta.ccsm;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.R;
import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.contacts.avatars.ContactPhoto;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;

/**
 * Created by jlewis on 5/19/17.
 */

public class DrawerFragment extends Fragment {
  private static final String TAG = DrawerFragment.class.getSimpleName();
  private MasterSecret masterSecret;
  private TextView orgName;
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

    final View view = inflater.inflate(R.layout.forsta_drawer_fragment, container, false);
    orgName = (TextView) view.findViewById(R.id.forsta_drawer_domain);
    userName = (TextView) view.findViewById(R.id.drawer_user);
    orgTag = (TextView) view.findViewById(R.id.drawer_org_tag);
    contactPhotoImage = (AvatarImageView) view.findViewById(R.id.drawer_photo_image);
    final ForstaUser user = ForstaUser.getLocalForstaUser(getActivity());

    new AsyncTask<Void, Void, Recipients>() {

      @Override
      protected Recipients doInBackground(Void... voids) {
        return RecipientFactory.getRecipientsFromString(getActivity(), user.getUid(), false);
      }

      @Override
      protected void onPostExecute(Recipients recipients) {
        userName.setText(user.getName());
        orgTag.setText("@" + user.getTag() + ": " + user.getOrgTag());
        contactPhotoImage.setAvatar(recipients, true);
        orgName.setText(user.getOrgTag());
      }
    }.execute();

    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
  }
}
