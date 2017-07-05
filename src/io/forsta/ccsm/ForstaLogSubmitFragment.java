package io.forsta.ccsm;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Locale;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.util.NetworkUtils;
import io.forsta.securesms.R;

/**
 * Created by jlewis on 7/5/17.
 */

public class ForstaLogSubmitFragment extends Fragment {
  private static final String TAG = ForstaLogSubmitFragment.class.getSimpleName();

  private TextView logPreview;
  private Button okButton;
  private Button   cancelButton;
  private boolean  emailActivityWasStarted = false;

  private ForstaLogSubmitFragment.OnLogSubmittedListener mListener;

  public static ForstaLogSubmitFragment newInstance()
  {
    ForstaLogSubmitFragment fragment = new ForstaLogSubmitFragment();
    return fragment;
  }

  public ForstaLogSubmitFragment() { }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    return inflater.inflate(R.layout.forsta_log_submit_fragment, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    initializeResources();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    try {
      mListener = (ForstaLogSubmitFragment.OnLogSubmittedListener) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString() + " must implement OnFragmentInteractionListener");
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    if (emailActivityWasStarted && mListener != null)
      mListener.onSuccess();
  }

  @Override
  public void onDetach() {
    super.onDetach();
    mListener = null;
  }

  private void initializeResources() {
    logPreview   = (TextView) getView().findViewById(R.id.log_preview);
    okButton     = (Button) getView().findViewById(R.id.ok);
    cancelButton = (Button) getView().findViewById(R.id.cancel);

    okButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        new SubmitToPastebinAsyncTask(logPreview.getText().toString()).execute();
      }
    });

    cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (mListener != null) mListener.onCancel();
      }
    });
    new PopulateLogcatAsyncTask(getActivity()).execute();
  }

  private static String grabLogcat() {
    try {
      final Process         process        = Runtime.getRuntime().exec("logcat -e");
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      final StringBuilder   log            = new StringBuilder();
      final String          separator      = System.getProperty("line.separator");

      String line;
      while ((line = bufferedReader.readLine()) != null) {
        log.append(line);
        log.append(separator);
      }
      return log.toString();
    } catch (IOException ioe) {
      Log.w(TAG, "IOException when trying to read logcat.", ioe);
      return null;
    }
  }

  private TextView handleBuildSuccessTextView(final String logUrl) {
    TextView showText = new TextView(getActivity());

    showText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    showText.setPadding(15, 30, 15, 30);
    showText.setText(getString(org.whispersystems.libpastelog.R.string.log_submit_activity__copy_this_url_and_add_it_to_your_issue, logUrl));
    showText.setAutoLinkMask(Activity.RESULT_OK);
    showText.setMovementMethod(LinkMovementMethod.getInstance());
    showText.setOnLongClickListener(new View.OnLongClickListener() {

      @Override
      public boolean onLongClick(View v) {
        @SuppressWarnings("deprecation")
        ClipboardManager manager =
            (ClipboardManager) getActivity().getSystemService(Activity.CLIPBOARD_SERVICE);
        manager.setText(logUrl);
        Toast.makeText(getActivity(),
            org.whispersystems.libpastelog.R.string.log_submit_activity__copied_to_clipboard,
            Toast.LENGTH_SHORT).show();
        return true;
      }
    });

    Linkify.addLinks(showText, Linkify.WEB_URLS);
    return showText;
  }

  private void handleShowSuccessDialog(final String logUrl) {
    TextView            showText = handleBuildSuccessTextView(logUrl);
    AlertDialog.Builder builder  = new AlertDialog.Builder(getActivity());

    builder.setTitle(org.whispersystems.libpastelog.R.string.log_submit_activity__success)
        .setView(showText)
        .setCancelable(false)
        .setNeutralButton(org.whispersystems.libpastelog.R.string.log_submit_activity__button_got_it, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            dialogInterface.dismiss();
            if (mListener != null) mListener.onSuccess();
          }
        });

    builder.create().show();
  }

  private class PopulateLogcatAsyncTask extends AsyncTask<Void,Void,String> {
    private WeakReference<Context> weakContext;

    public PopulateLogcatAsyncTask(Context context) {
      this.weakContext = new WeakReference<>(context);
    }

    @Override
    protected String doInBackground(Void... voids) {
      Context context = weakContext.get();
      if (context == null) return null;

      return buildDescription(context) + "\n" + grabLogcat();
    }

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      logPreview.setText(org.whispersystems.libpastelog.R.string.log_submit_activity__loading_logs);
      okButton.setEnabled(false);
    }

    @Override
    protected void onPostExecute(String logcat) {
      super.onPostExecute(logcat);
      if (TextUtils.isEmpty(logcat)) {
        if (mListener != null) mListener.onFailure();
        return;
      }
      logPreview.setText(logcat);
      okButton.setEnabled(true);
    }
  }

  private class SubmitToPastebinAsyncTask extends AsyncTask<Void,Void,String> {
    private final String         paste;

    public SubmitToPastebinAsyncTask(String paste) {
      this.paste = paste;
    }

    @Override
    protected String doInBackground(Void... voids) {
      try {
        final JSONObject outJson = new JSONObject();
        final JSONObject catlog  = new JSONObject(Collections.singletonMap("content", paste));
        final JSONObject files   = new JSONObject();
        files.put("cat.log", catlog);
        outJson.put("files", files);
        outJson.put("public", false);
//        JSONObject response = CcsmApi.sendDebugLog(getActivity(), outJson);

      } catch (JSONException e) {
        Log.e(TAG, e.getMessage());
      }
      return null;
    }

    @Override
    protected void onPostExecute(final String response) {
      super.onPostExecute(response);

      if (response != null)
        handleShowSuccessDialog(response);
      else {
        Log.w(TAG, "Response was null from API.");
        Toast.makeText(getActivity(), "Unable to submit log at this time.", Toast.LENGTH_LONG).show();
      }
    }
  }

  private static long asMegs(long bytes) {
    return bytes / 1048576L;
  }

  public static String getMemoryUsage(Context context) {
    Runtime info = Runtime.getRuntime();
    info.totalMemory();
    return String.format(Locale.ENGLISH, "%dM (%.2f%% free, %dM max)",
        asMegs(info.totalMemory()),
        (float)info.freeMemory() / info.totalMemory() * 100f,
        asMegs(info.maxMemory()));
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  public static String getMemoryClass(Context context) {
    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    String          lowMem          = "";

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && activityManager.isLowRamDevice()) {
      lowMem = ", low-mem device";
    }
    return activityManager.getMemoryClass() + lowMem;
  }

  private static String buildDescription(Context context) {
    final PackageManager pm      = context.getPackageManager();
    final StringBuilder  builder = new StringBuilder();


    builder.append("Device  : ")
        .append(Build.MANUFACTURER).append(" ")
        .append(Build.MODEL).append(" (")
        .append(Build.PRODUCT).append(")\n");
    builder.append("Android : ").append(Build.VERSION.RELEASE).append(" (")
        .append(Build.VERSION.INCREMENTAL).append(", ")
        .append(Build.DISPLAY).append(")\n");
    builder.append("Memory  : ").append(getMemoryUsage(context)).append("\n");
    builder.append("Memclass: ").append(getMemoryClass(context)).append("\n");
    builder.append("OS Host : ").append(Build.HOST).append("\n");
    builder.append("App     : ");
    try {
      builder.append(pm.getApplicationLabel(pm.getApplicationInfo(context.getPackageName(), 0)))
          .append(" ")
          .append(pm.getPackageInfo(context.getPackageName(), 0).versionName)
          .append("\n");
    } catch (PackageManager.NameNotFoundException nnfe) {
      builder.append("Unknown\n");
    }

    return builder.toString();
  }

  /**
   * This interface must be implemented by activities that contain this
   * fragment to allow an interaction in this fragment to be communicated
   * to the activity and potentially other fragments contained in that
   * activity.
   * <p>
   * See the Android Training lesson <a href=
   * "http://developer.android.com/training/basics/fragments/communicating.html"
   * >Communicating with Other Fragments</a> for more information.
   */
  public interface OnLogSubmittedListener {
    public void onSuccess();
    public void onFailure();
    public void onCancel();
  }
}