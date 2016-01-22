package ch.trillian.dufour;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

public class SwissGeocoderProvider extends ContentProvider {

  private static final String TAG = "GEOCODER";
  
  private static final String BASE_URL = "http://api3.geo.admin.ch/rest/services/ech/SearchServer?type=locations&features=&lang=de";
  private static final String LOCATION_PARAM = "searchText";
  
  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {

    return 0;
  }

  @Override
  public String getType(Uri uri) {

    return null;
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {

    return null;
  }

  @Override
  public boolean onCreate() {

    return false;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    
    // get query string
    String query = uri.getLastPathSegment();
    
    if (SearchManager.SUGGEST_URI_PATH_QUERY.equals(query)) {
      return null;
    }
    
    MatrixCursor cursor = new MatrixCursor(new String[] {"_ID", SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2, SearchManager.SUGGEST_COLUMN_INTENT_DATA });
    
    // get list of matching addresses
    String response = requestAddresses(query);
      
    // convert to cursor
    parseResponse(response, cursor);
    
    return cursor;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

    return 0;
  }
  
  private void parseResponse(String response, MatrixCursor cursor) {
    
    try {
      JSONObject result = new JSONObject(response);
      JSONArray locations = result.getJSONArray("results");

      // loop over all locations in response
      for (int i = 0; i < locations.length(); i++) {
        
        try {
          
          // parse location
          JSONObject location = locations.getJSONObject(i);
          
          JSONObject attributes = location.getJSONObject("attrs");
          String origin = attributes.getString("origin");
          String box = attributes.getString("geom_st_box2d");
          String label = attributes.getString("label");
          
          // parse box
          box = box.replace("BOX(", "");
          box = box.replace(")", "");
          box = box.replace(",", " ");
          String[] coordinates = box.split(" ");

          if (!"sn25".equals(origin) && !"address".equals(origin)) {
            continue;
          }
          
          // get mean value of bounding box
          double x = (Double.valueOf(coordinates[0]) + Double.valueOf(coordinates[2])) / 2d;
          double y = (Double.valueOf(coordinates[1]) + Double.valueOf(coordinates[3])) / 2d;
          double[] wgs84 = Ch1903.ch1903toWgs84to(x, y, 0);

          // build intent's data
          Uri.Builder uriBuilder = Uri.parse("content://ch.trillian.dufour.geocoder/").buildUpon();
          uriBuilder.appendQueryParameter("longitude", String.valueOf(wgs84[0]));
          uriBuilder.appendQueryParameter("latitude", String.valueOf(wgs84[1]));
          
          // prepare label
          label = label.replaceAll("^<b>", "");
          label = label.replaceAll("</b>", "");
          String[] lines = label.split("<b>", 2);
          
          // build result row
          cursor.addRow(new Object[] { i, lines[0] != null ? lines[0] : "", lines.length > 1 && lines[1] != null ? lines[1] : "", uriBuilder.toString() });
            
        } catch (Exception e) {
          Log.i(TAG, "parse location failed: " + e.getMessage());
          continue;
        }
      }
      
    } catch (JSONException e) {
      Log.i(TAG, "parse response failed: " + e.getMessage());
    }
  }
  
  private String requestAddresses(String location) {
    
    try {
  
      // build URL
      Uri.Builder uriBuilder = Uri.parse(BASE_URL).buildUpon();
      uriBuilder.appendQueryParameter(LOCATION_PARAM, location);
      
      // open HTTP stream
      URL url = new URL(uriBuilder.toString());
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.addRequestProperty("referer", "http://map.geo.admin.ch/");
      
      // read response
      InputStream inputStream = connection.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      String line;
      StringBuilder builder = new StringBuilder ();
      while ((line = reader.readLine()) != null) {
          builder.append(line);
     }
      
      inputStream.close();
      connection.disconnect();
      
      return builder.toString();
  
    } catch (Exception e) {
      Log.w(TAG, "Exception: " + e.getMessage(), e);
    }
    
    return null;
  }
}
