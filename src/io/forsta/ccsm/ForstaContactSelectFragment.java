package io.forsta.ccsm;

import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

import io.forsta.ccsm.api.ForstaUser;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.ContactSelectionListFragment;
import io.forsta.securesms.R;
import io.forsta.securesms.util.ViewUtil;

/**
 * Created by jlewis on 5/1/17.
 */

public class ForstaContactSelectFragment extends Fragment {
  private static final String TAG = ContactSelectionListFragment.class.getSimpleName();

  public final static String DISPLAY_MODE = "display_mode";
  public final static String MULTI_SELECT = "multi_select";
  public final static String REFRESHABLE  = "refreshable";

  private TextView emptyText;
  private RecyclerView recyclerView;
  private Map<Long, String> selectedContacts;
  private ContactSelectionListFragment.OnContactSelectedListener onContactSelectedListener;
  private SwipeRefreshLayout swipeRefresh;
  private String cursorFilter;


  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onCreate(icicle);
    initializeAdapter();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.forst_contact_selection_fragment, container, false);

    emptyText    = ViewUtil.findById(view, android.R.id.empty);
    recyclerView = ViewUtil.findById(view, R.id.recycler_view);
    swipeRefresh = ViewUtil.findById(view, R.id.swipe_refresh);
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

    swipeRefresh.setEnabled(getActivity().getIntent().getBooleanExtra(REFRESHABLE, true) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN);

    return view;
  }

  public interface OnContactSelectedListener {
    void onContactSelected(String number);
    void onContactDeselected(String number);
  }

  private void initializeAdapter() {
    ContactDb db = DbFactory.getContactDb(getActivity());
    List<ForstaUser> users = db.getUsers();
    ForstaContactsAdapter adapter = new ForstaContactsAdapter(users);
    recyclerView.setAdapter(adapter);
  }

  private class ForstaContactsAdapter extends RecyclerView.Adapter<ContactHolder> {
    private List<ForstaUser> contacts;


    public ForstaContactsAdapter(List<ForstaUser> contacts) {
      this.contacts = contacts;
    }

    @Override
    public ContactHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(getActivity());
      View view = inflater.inflate(R.layout.forsta_contacts_list_item, parent, false);
      return new ContactHolder(view);
    }

    @Override
    public void onBindViewHolder(ContactHolder holder, int position) {
      ForstaUser item = contacts.get(position);
      holder.name.setText(item.getName());
      holder.number.setText(item.phone);
      holder.registered.setVisibility(item.tsRegistered ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
      return contacts.size();
    }
  }

  private class ContactHolder extends RecyclerView.ViewHolder {
    public TextView name;
    public TextView number;
    public ImageView registered;

    public ContactHolder(View itemView) {
      super(itemView);
      name = (TextView) itemView.findViewById(R.id.forsta_contact_name);
      number = (TextView) itemView.findViewById(R.id.forsta_contact_number);
      registered = (ImageView) itemView.findViewById(R.id.forsta_contact_registered);
      registered.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {

        }
      });
    }
  }

}
