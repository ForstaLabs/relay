package io.forsta.ccsm;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.R;
import io.forsta.securesms.components.ComposeText;

/**
 * Created by jlewis on 4/27/17.
 */

public class ForstaInputFragment extends Fragment {
  private static final String TAG = ForstaInputFragment.class.getSimpleName();

  private ImageButton directoryButton;
  private ComposeText messageInput;
  private TextView messageType;
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
    messageType = (TextView) view.findViewById(R.id.forsta_input_type);

    messageInput = (ComposeText) view.findViewById(R.id.embedded_text_editor);
    messageInput.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

      }

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        ContactDb db = DbFactory.getContactDb(getActivity());
        Map<String, String> slugMap = db.getContactSlugs();
        Map<String, String> matched = new HashMap<String, String>();

        Set<String> recipients = new HashSet<String>();
        Pattern p = Pattern.compile("@[a-zA-Z0-9-]+");
        Matcher m = p.matcher(charSequence);
        while (m.find()) {
          String recipient = m.group();
          recipient = recipient.substring(1);
          if (slugMap.containsKey(recipient)) {
            recipients.add(slugMap.get(recipient));
          }
        }
        messageType.setText(recipients.toString());
        Log.d(TAG, "Recipients: " + recipients.size());
      }

      @Override
      public void afterTextChanged(Editable editable) {

      }
    });
    return view;
  }
}
