package io.forsta.ccsm;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.forsta.ccsm.api.ForstaRecipient;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.R;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.util.DirectoryHelper;

/**
 * Created by jlewis on 5/22/17.
 */

public class DirectoryDialogFragment extends DialogFragment {

  private RecyclerView recyclerView;
  private DirectoryAdapter adapter;
  private Button okButton;
  private Button cancelButton;
  private ProgressBar progress;
  private Set<ForstaRecipient> selectedRecipients = new HashSet();
  private OnCompleteListener onCompleteListener;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.forsta_directory_fragment, container, false);
    progress = (ProgressBar) view.findViewById(R.id.forsta_directory_progress);
    progress.setVisibility(View.VISIBLE);
    recyclerView = (RecyclerView) view.findViewById(R.id.forsta_directory_recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    okButton = (Button) view.findViewById(R.id.forsta_directory_ok);
    okButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (onCompleteListener != null) {
          onCompleteListener.onComplete(selectedRecipients);
        }
        dismiss();
      }
    });
    cancelButton = (Button) view.findViewById(R.id.forsta_directory_cancel);
    cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();
      }
    });
    GetRecipients getRecipients = new GetRecipients();
    getRecipients.execute();

    return view;
  }

  public void setOnCompleteListener(OnCompleteListener listener) {
    this.onCompleteListener = listener;
  }

  public interface OnCompleteListener {
    void onComplete(Set<ForstaRecipient> recipients);
  }

  class GetRecipients extends AsyncTask<Void, Void, List<ForstaRecipient>> {

    @Override
    protected List<ForstaRecipient> doInBackground(Void... voids) {
      List<ForstaRecipient> recipients = new ArrayList<>();
      List<ForstaRecipient> groups = new ArrayList<>();
      ContactDb db = DbFactory.getContactDb(getActivity());
      recipients = db.getRecipients();
      GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(getActivity());
      groups = groupDb.getGroupRecipients();
      recipients.addAll(groups);
      return recipients;
    }

    @Override
    protected void onPostExecute(List<ForstaRecipient> forstaRecipients) {

      adapter = new DirectoryAdapter(forstaRecipients, new DirectoryAdapter.ItemClickListener() {
        @Override
        public void onItemClick(ForstaRecipient recipient, boolean selected) {
          if (selected) {
            selectedRecipients.add(recipient);
          } else {
            selectedRecipients.remove(recipient);
          }
        }
      });
      recyclerView.setAdapter(adapter);
      progress.setVisibility(View.GONE);
    }
  }
}
