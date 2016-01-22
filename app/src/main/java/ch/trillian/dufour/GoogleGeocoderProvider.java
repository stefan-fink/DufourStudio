package ch.trillian.dufour;

import java.util.List;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.util.Log;

public class GoogleGeocoderProvider extends ContentProvider {

  private static final String TAG = "GEOCODER";
  
  private static final int DEFAULT_LIMIT = 10;
  
  private double[] lowerLeft = Ch1903.ch1903toWgs84to(420000, 20000, 0);
  private double[] upperRight = Ch1903.ch1903toWgs84to(850000, 350000, 0);

  private Geocoder geocoder;

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

    geocoder = new Geocoder(getContext());
    
    return false;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    
    // get query string
    String query = uri.getLastPathSegment();
    
    // get limit
    String limitStr = uri.getQueryParameter("limit");
    int limit = DEFAULT_LIMIT;
    if (limitStr != null) {
      try {
        limit = Integer.valueOf(limitStr);
      } catch (NumberFormatException e) {
        Log.w(TAG, "query() limit parameter not a number in URI: " + uri.toString());
      }
    }
    Log.i(TAG, "query() limit=" + limit);
    
    MatrixCursor cursor = new MatrixCursor(new String[] {"_ID", SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2, SearchManager.SUGGEST_COLUMN_INTENT_DATA });
    
    try {
      
      // get list of matching addresses
      List<Address> addressList = geocoder.getFromLocationName(query, limit, lowerLeft[1], lowerLeft[0], upperRight[1], upperRight[0]);
      
      int i=0;
      for (Address address : addressList) {
        
        Log.i(TAG, "query() address: " + address);

        if (address.hasLongitude() && address.hasLatitude()) {
          
          // build first line
          StringBuilder text1 = new StringBuilder();
          if (address.getPostalCode() != null) {
            text1.append(address.getPostalCode());
            text1.append(" ");
          }
          if (address.getLocality() != null) {
            text1.append(address.getLocality());
          }
          
          // build second line
          StringBuilder text2 = new StringBuilder();
          if (address.getAddressLine(0) != null) {
            text2.append(address.getAddressLine(0));
          }
          
          // build intent's data
          Uri.Builder uriBuilder = Uri.parse("content://ch.trillian.dufour.geocoder/").buildUpon();
          uriBuilder.appendQueryParameter("longitude", String.valueOf(address.getLongitude()));
          uriBuilder.appendQueryParameter("latitude", String.valueOf(address.getLatitude()));

          // add to result
          cursor.addRow(new Object[] { i++, text1.toString(), text2.toString(), uriBuilder.toString() });
        }
      }
    } catch (Exception e) {
      Log.i(TAG, "query() getFromLocationName failed: " + e.getMessage());
    }
    
    return cursor;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

    return 0;
  }
}
