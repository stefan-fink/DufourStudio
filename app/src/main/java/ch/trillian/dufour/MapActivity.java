package ch.trillian.dufour;

import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;

public class MapActivity extends Activity {

  // keys used for storing instance state
  private static final String KEY_LOCATION = "location";
  private static final String KEY_POI_LOCATION = "poiLocation";
  private static final String KEY_LAYER_INDEX = "layerIndex";
  private static final String KEY_SCALE = "scale";
  private static final String KEY_GPS_ENABLED = "gpsEnabled";
  private static final String KEY_GPS_TRACKING = "gpsTracking";
  private static final String KEY_SHOW_INFO = "infoLevel";

  // constants for zooming in via volume up/down
  private static final float ZOOM_FACTOR = 1.3f;
  private static final int ZOOM_REPEAT_SLOWDOWN = 3;

  // constants for GPS updates
  private static final int GPS_MIN_INTERVAL = 1000;
  private static final int GPS_MIN_DISTANCE = 0;

  private final Map map = createMap();
  private MapView mapView;
  private TileCache tileCache;
  private TileLoader tileLoader;

  // true if GPS is enabled
  boolean gpsWasEnabled;
  boolean gpsWasTracking;

  // true if info is visible
  boolean showInfo;

  // our optionMenu
  private Menu optionMenu;

  // the search view
  private SearchView searchView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);

    Log.i("TRILLIAN", "onCreate()");

    // initialize loader
    tileLoader = new TileLoader(this);
    tileLoader.setLoadListener(new LoadListener());

    // initialize view
    setContentView(R.layout.activity_map);
    mapView = (MapView) findViewById(R.id.map_view);
    mapView.setLayer(map.getLayer(0));
    mapView.setLocation(((LocationManager) getSystemService(Context.LOCATION_SERVICE)).getLastKnownLocation(LocationManager.GPS_PROVIDER));
    mapView.setViewListener(new MapViewListener());

    // retrieve state
    if (savedInstanceState != null) {
      mapView.setScale(savedInstanceState.getFloat(KEY_SCALE));
      mapView.setLayer(map.getLayer(savedInstanceState.getInt(KEY_LAYER_INDEX)));
      mapView.setLocation((Location) savedInstanceState.getParcelable(KEY_LOCATION));
      mapView.setPoiLocation((Location) savedInstanceState.getParcelable(KEY_POI_LOCATION));
      gpsWasEnabled = savedInstanceState.getBoolean(KEY_GPS_ENABLED);
      gpsWasTracking = savedInstanceState.getBoolean(KEY_GPS_TRACKING);
      setShowInfo(savedInstanceState.getBoolean(KEY_SHOW_INFO));
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {

    super.onSaveInstanceState(outState);
    
    outState.putParcelable(KEY_LOCATION, mapView.getLocation());
    outState.putParcelable(KEY_POI_LOCATION, mapView.getPoiLocation());
    outState.putInt(KEY_LAYER_INDEX, mapView.getLayer().getIndex());
    outState.putFloat(KEY_SCALE, mapView.getScale());
    outState.putBoolean(KEY_GPS_ENABLED, mapView.isGpsEnabled());
    outState.putBoolean(KEY_GPS_TRACKING, mapView.isGpsTracking());
    outState.putBoolean(KEY_SHOW_INFO, showInfo);
  }

  @Override
  protected void onStart() {

    super.onStart();

    Log.w("TRILLIAN", "onStart()");

    super.onResume();

    tileLoader.onResume();

    startTimer();

    setGpsEnabled(gpsWasEnabled);
    setGpsTracking(gpsWasTracking);
  }

  @Override
  protected void onStop() {

    Log.w("TRILLIAN", "onStop()");

    stopTimer();

    gpsWasEnabled = mapView.isGpsEnabled();
    gpsWasTracking = mapView.isGpsTracking();
    setGpsEnabled(false);

    tileLoader.onPause();

    Log.w("TRILLIAN", "onStop() gpsWasEnabled=" + gpsWasEnabled);
    Log.w("TRILLIAN", "onStop() gpsWasTracking=" + gpsWasTracking);

    super.onStop();
  }

  @Override
  protected void onResume() {

    super.onResume();
  }

  @Override
  protected void onPause() {

    super.onPause();
  }

  @Override
  protected void onDestroy() {

    Log.w("TRILLIAN", "onDestroy()");

    tileLoader.onDestroy();

    super.onDestroy();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {

    getMenuInflater().inflate(R.menu.map, menu);
    optionMenu = menu;
    
    if (mapView.getPoiLocation() == null) {
      optionMenu.findItem(R.id.action_goto_poi).setVisible(false);
      optionMenu.findItem(R.id.action_clear_poi).setVisible(false);
    }
    
    // Get the SearchView and set the searchable configuration
    SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
    searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
    searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
    searchView.setIconifiedByDefault(true);

    // update icons
    setGpsEnabled(gpsWasEnabled);
    setGpsTracking(gpsWasTracking);
    setShowInfo(showInfo);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    switch (item.getItemId()) {

    case R.id.action_gps:
      setGpsEnabled(!mapView.isGpsEnabled());
      setGpsTracking(true);
      return true;

    case R.id.action_info:
      setShowInfo(!showInfo);
      return true;
    
    case R.id.action_goto_poi:
      mapView.setLocation(mapView.getPoiLocation());
      mapView.setGpsTracking(false);
      return true;

    case R.id.action_clear_poi:
      mapView.setPoiLocation(null);
      optionMenu.findItem(R.id.action_goto_poi).setVisible(false);
      optionMenu.findItem(R.id.action_clear_poi).setVisible(false);
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onNewIntent(Intent intent) {

    Log.i("TRILLIAN", "onNewIntent() " + intent.toString());

    if (Intent.ACTION_SEARCH.equals(intent.getAction())) {

      // nothing to do here, we only handle suggestions via ACTION_VIEW

    } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {

      optionMenu.findItem(R.id.action_search).collapseActionView();

      Uri uri = intent.getData();
      if (uri != null) {
        
        String longitude = uri.getQueryParameter("longitude");
        String latitude = uri.getQueryParameter("latitude");

        if (longitude != null && latitude != null) {
          try {
            Location location = new Location("Geocoder");
            location.setLongitude(Double.valueOf(longitude));
            location.setLatitude(Double.valueOf(latitude));
            mapView.setLocation(location);
            mapView.setPoiLocation(location);
            mapView.setGpsTracking(false);
            optionMenu.findItem(R.id.action_goto_poi).setVisible(true);
            optionMenu.findItem(R.id.action_clear_poi).setVisible(true);
         } catch (NumberFormatException e) {
            Log.e("TRILLIAN", "Exception when parsing uri in ACTION_VIEW: " + e.getMessage());
          }
        }
      }
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {

    int action = event.getAction();
    int keyCode = event.getKeyCode();

    switch (keyCode) {

    case KeyEvent.KEYCODE_VOLUME_UP:
      if (action == KeyEvent.ACTION_DOWN) {
        if (event.getRepeatCount() == 0) {
          mapView.scale(ZOOM_FACTOR);
        } else {
          float zoomFactor = 1f + (ZOOM_FACTOR - 1f) / ZOOM_REPEAT_SLOWDOWN;
          mapView.scale(zoomFactor);
        }
      }
      return true;

    case KeyEvent.KEYCODE_VOLUME_DOWN:
      if (action == KeyEvent.ACTION_DOWN) {
        if (event.getRepeatCount() == 0) {
          mapView.scale(1 / ZOOM_FACTOR);
        } else {
          float zoomFactor = 1f + (ZOOM_FACTOR - 1f) / ZOOM_REPEAT_SLOWDOWN;
          mapView.scale(1f / zoomFactor);
        }
      }
      return true;

    default:
      return super.dispatchKeyEvent(event);
    }
  }

  private Map createMap() {

    String urlFormat = "http://wmts.geo.admin.ch/1.0.0/ch.swisstopo.pixelkarte-farbe/default/20140106/21781/%1$s/%3$d/%2$d.jpeg";

    Layer[] layers = { new Layer("CH16", "16", urlFormat, 420000f, 350000f, 250f, 256, 256, 0, 0, 7, 4), new Layer("CH17", "17", urlFormat, 420000f, 350000f, 100f, 256, 256, 0, 0, 18, 12),
        new Layer("CH18", "18", urlFormat, 420000f, 350000f, 50f, 256, 256, 0, 0, 37, 24), new Layer("CH19", "19", urlFormat, 420000f, 350000f, 20f, 256, 256, 0, 0, 93, 62),
        new Layer("CH20", "20", urlFormat, 420000f, 350000f, 10f, 256, 256, 0, 0, 187, 124), new Layer("CH21", "21", urlFormat, 420000f, 350000f, 5f, 256, 256, 0, 0, 374, 249),
        // new Layer("CH22", "22",  urlFormat, 420000f, 350000f, 2.5f, 256, 256, 0, 0,  749,  499),
        new Layer("CH23", "23", urlFormat, 420000f, 350000f, 2.0f, 256, 256, 0, 0, 937, 624),
        // new Layer("CH24", "24",  urlFormat, 420000f, 350000f, 1.5f, 256, 256, 0, 0, 1249,  833),
        new Layer("CH25", "25", urlFormat, 420000f, 350000f, 1.0f, 256, 256, 0, 0, 1875, 1249),
    //new Layer("CH26", "26",  urlFormat, 420000f, 350000f, 0.5f, 256, 256, 0, 0, 3749, 2499),
    };

    return new Map("CH", layers, 0.5f, 10.0f, 1.5f, 1.5f);
  }

  private class MapViewListener implements MapView.ViewListener {

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {

      if (w == 0 || h == 0) {
        return;
      }

      Log.w("TRILLIAN", "onSizeChanged: " + (tileCache == null ? "no old cache" : "has old cache"));

      tileCache = new TileCache(map, TileCache.PRELOAD_SIZE, w, h);
      tileCache.setCacheListener(new CacheListener());
    }

    @Override
    public Tile onGetTile(Layer layer, int x, int y) {

      if (tileCache == null) {
        return null;
      }

      return tileCache.getTile(layer, x, y);
    }

    @Override
    public void preloadRegion(Layer layer, int minTileX, int maxTileX, int minTileY, int maxTileY) {

      if (tileCache != null) {
        tileCache.preloadRegion(layer, minTileX, maxTileX, minTileY, maxTileY);
      }
    }
  }

  private class LoadListener implements TileLoader.LoadListener {

    @Override
    public void onLoadFinished(Tile tile) {

      if (tile == null) {
        Log.w("TRILLIAN", "Tile=null");
      } else if (tile.getBitmap() == null) {
        Log.w("TRILLIAN", "Tile bitmap=null" + tile);
      } else {
        Log.i("TRILLIAN", "Tile loaded: " + tile.toString());
        mapView.invalidate();
      }
    }
  }

  private class CacheListener implements TileCache.CacheListener {

    @Override
    public void onOrderLoadTile(Tile tile) {

      // Log.w("TRILLIAN", "onOrderLoadTile: " + tile);
      tileLoader.orderLoadTile(tile);
    }

    @Override
    public void onCancelLoadTile(Tile tile) {

      // Log.w("TRILLIAN", "onCancelLoadTile: " + tile);
      tileLoader.cancelLoadTile(tile);
    }
  }

  private final void setShowInfo(boolean showInfo) {

    this.showInfo = showInfo;

    if (optionMenu != null) {
      MenuItem actionGps = optionMenu.findItem(R.id.action_info);
      if (actionGps != null) {
        actionGps.setIcon(showInfo ? R.drawable.ic_action_info_on : R.drawable.ic_action_info_off);
      }
    }

    mapView.setShowInfo(showInfo);
  }

  private final void setGpsTracking(boolean tracking) {

    mapView.setGpsTracking(tracking);
  }

  private final void setGpsEnabled(boolean enable) {

    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    Location location = null;

    // start or stop listening to GPS updates
    if (enable) {
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_MIN_INTERVAL, GPS_MIN_DISTANCE, locationListener);
      location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    } else {
      locationManager.removeUpdates(locationListener);
    }

    // change GPS icon
    if (optionMenu != null) {
      MenuItem actionGps = optionMenu.findItem(R.id.action_gps);
      if (actionGps != null) {
        actionGps.setIcon(enable ? R.drawable.ic_action_gps_on : R.drawable.ic_action_gps_off);
      }
    }

    mapView.setGpsEnabled(enable);
    mapView.setGpsLocation(location);
  }

  private final LocationListener locationListener = new LocationListener() {

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onLocationChanged(Location location) {

      mapView.setGpsLocation(location);
    }
  };

  private Timer timer;

  private final void startTimer() {

    timer = new Timer();
    timer.scheduleAtFixedRate(new TimerTask() {

      @Override
      public void run() {
        timerHandler.obtainMessage(1).sendToTarget();
      }
    }, 0, 1000);

  }

  private final void stopTimer() {

    timer.cancel();
  }

  @SuppressLint("HandlerLeak")
  public Handler timerHandler = new Handler() {

    public void handleMessage(Message message) {

      mapView.onTick();
    }
  };
}
