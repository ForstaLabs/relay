package io.forsta.ccsm;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import io.forsta.securesms.R;

/**
 * Created by jlewis on 4/27/17.
 */

public class ForstaInputFragment extends Fragment {

  private ImageButton directoryButton;
  private static final int DIRECTORY_PICK = 13;


  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.forsta_input_fragment, container, false);
    directoryButton = (ImageButton) view.findViewById(R.id.forsta_quick_directory);
    directoryButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(getActivity(), DirectoryActivity.class);
        startActivityForResult(intent, DIRECTORY_PICK);
      }
    });
    return view;
  }
}
