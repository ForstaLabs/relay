package io.forsta.ccsm;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.forsta.securesms.R;
import io.forsta.securesms.crypto.MasterSecret;

/**
 * Created by jlewis on 5/19/17.
 */

public class DrawerFragment extends Fragment {
  private static final String TAG = DrawerFragment.class.getSimpleName();
  private MasterSecret masterSecret;
  private TextView domainName;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.masterSecret = getArguments().getParcelable("master_secret");
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.forsta_drawer_fragment, container, false);
    domainName = (TextView) view.findViewById(R.id.forsta_drawer_domain);
    domainName.setText("DOMAIN: " + ForstaPreferences.getForstaOrgName(getActivity()));

    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
  }
}
