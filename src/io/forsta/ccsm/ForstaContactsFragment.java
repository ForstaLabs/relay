package io.forsta.ccsm;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import io.forsta.securesms.R;

/**
 * Created by jlewis on 4/25/17.
 */

public class ForstaContactsFragment extends Fragment {

  private RecyclerView list;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.forsta_contacts_fragment, container, false);

    list = (RecyclerView) view.findViewById(R.id.forsta_contacts_recycler_view);
    list.setLayoutManager(new LinearLayoutManager(getActivity()));

    return view;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    initializeAdapter();
  }

  @Override
  public void onResume() {
    super.onResume();

//    updateReminders();
//    list.getAdapter().notifyDataSetChanged();
  }

  private void initializeAdapter() {
    List<String> contacts = new ArrayList<>();
    contacts.add("One");
    contacts.add("Two");
    list.setAdapter(new ForstaContactsAdapter(contacts));
  }
}
