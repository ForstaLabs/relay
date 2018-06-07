package io.forsta.securesms.components.location;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.MarkerOptions;

import io.forsta.securesms.R;
import io.forsta.securesms.util.ViewUtil;
import io.forsta.securesms.util.concurrent.ListenableFuture;
import io.forsta.securesms.util.concurrent.SettableFuture;

public class SignalMapView extends LinearLayout {

  private MapView   mapView;
  private ImageView imageView;
  private TextView  textView;

  public SignalMapView(Context context) {
    this(context, null);
  }

  public SignalMapView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(context);
  }

  private void initialize(Context context) {
    setOrientation(LinearLayout.VERTICAL);
    LayoutInflater.from(context).inflate(R.layout.signal_map_view, this, true);

    this.mapView   = ViewUtil.findById(this, R.id.map_view);
    this.imageView = ViewUtil.findById(this, R.id.image_view);
    this.textView  = ViewUtil.findById(this, R.id.address_view);
  }

  public ListenableFuture<Bitmap> display(final SignalPlace place) {
    final SettableFuture<Bitmap> future = new SettableFuture<>();

    this.mapView.onCreate(null);
    this.mapView.onResume();

    this.mapView.setVisibility(View.VISIBLE);
    this.imageView.setVisibility(View.GONE);

    this.mapView.getMapAsync(new OnMapReadyCallback() {
      @Override
      public void onMapReady(final GoogleMap googleMap) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLong(), 13));
        googleMap.addMarker(new MarkerOptions().position(place.getLatLong()));
        googleMap.setBuildingsEnabled(true);
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.getUiSettings().setAllGesturesEnabled(false);
        googleMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
          @Override
          public void onMapLoaded() {
            googleMap.snapshot(new GoogleMap.SnapshotReadyCallback() {
              @Override
              public void onSnapshotReady(Bitmap bitmap) {
                future.set(bitmap);
                imageView.setImageBitmap(bitmap);
                imageView.setVisibility(View.VISIBLE);
                mapView.setVisibility(View.GONE);
                mapView.onPause();
                mapView.onDestroy();
              }
            });
          }
        });
      }
    });

    this.textView.setText(place.getDescription());

    return future;
  }

}
