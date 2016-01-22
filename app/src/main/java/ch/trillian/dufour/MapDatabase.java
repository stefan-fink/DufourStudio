package ch.trillian.dufour;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class MapDatabase extends SQLiteOpenHelper {

  private static final String TAG = "DATABASE";

  private static final String DATABASE_NAME = "map.db";
  private static final int DATABASE_VERSION = 1;

  // Our singleton
  private static MapDatabase instance;
  
  // our database
  private SQLiteDatabase db;
  
  // the number of open 'connections'
  private int openCount = 0;
  
  // the current number of tiles
  private int tileCount;
  
  private MapDatabase(Context context) {
    
    super(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase database) {
    
    TileTable.onCreate(database);
  }

  @Override
  public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
    
    TileTable.onUpgrade(database, oldVersion, newVersion);
  }
  
  public static MapDatabase newInstance(Context context) {
    
    if (instance == null) {
      instance = new MapDatabase(context);
    }
      
    return instance;
  }
  
  public static MapDatabase getInstance() {
    
    return instance;
  }
  
  public synchronized void openDatabase() {
    
    long start = System.currentTimeMillis();

    if (openCount == 0) {
      db = getWritableDatabase();
      tileCount = readTileCount();
      Log.i(TAG, String.format("Opened (tileCount=%d)", tileCount));
    }

    openCount++;
    
    Log.i(TAG, String.format("Opened in %d ms (openCount=%d)", (System.currentTimeMillis() - start), openCount));
  }

  public synchronized void closeDatabase() {

    long start = System.currentTimeMillis();

    openCount--;

    if (openCount == 0) {
      db.close();
      db = null;
    }

    Log.i(TAG, String.format("Closed in %d ms (openCount=%d)", (System.currentTimeMillis() - start), openCount));
  }

  public int getTileCount() {
    
    return tileCount;
  }
  
  private static final String SQL_GET_TILE_COUNT = "SELECT COUNT(*) FROM " + TileTable.TABLE_NAME;

  public int readTileCount() {
    
    Cursor cursor = db.rawQuery(SQL_GET_TILE_COUNT, new String[] {});
    
    if (cursor == null) {
      return -1;
      
    }

    try {
      if(cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    } finally {
      cursor.close();
    }

    return -1;
  }
  
  private static final String SQL_GET_TILE_IMAGE = "SELECT " + TileTable.COL_LAST_USED + ", " + TileTable.COL_IMAGE + " FROM " + TileTable.TABLE_NAME + " WHERE " + TileTable.COL_LAYER_ID + " = ? AND " + TileTable.COL_X + " = ? AND " + TileTable.COL_Y + "=?";

  public synchronized boolean readTile(Tile tile) {

    Cursor cursor = db.rawQuery(SQL_GET_TILE_IMAGE, new String[] { tile.getLayer().getName(), String.valueOf(tile.getX()), String.valueOf(tile.getY())});
    
    try {
      if(cursor.moveToFirst()) {
        
        tile.setLastUsed(cursor.getLong(0));
        byte[] encodedImage = cursor.getBlob(1);
        Bitmap bitmap = BitmapFactory.decodeByteArray(encodedImage, 0, encodedImage.length);
        if (bitmap != null) {
          tile.setBitmap(bitmap);
          return true;
        }
        
      }
    } finally {
      cursor.close();
    }

    return false;
  }

  private static final String SQL_EXISTS_TILE = "SELECT 1 FROM " + TileTable.TABLE_NAME + " WHERE " + TileTable.COL_LAYER_ID + " = ? AND " + TileTable.COL_X + " = ? AND " + TileTable.COL_Y + "=?";

  public boolean isTileExisting(Tile tile) {

    Cursor cursor = db.rawQuery(SQL_EXISTS_TILE, new String[] { tile.getLayer().getName(), String.valueOf(tile.getX()), String.valueOf(tile.getY())});
 
    try {
      if(cursor.moveToFirst()) {
        return true;
      }
    } finally {
      cursor.close();
    }

    return false;
  }

  private static final String SQL_UPDATE_LAST_USED = "UPDATE " + TileTable.TABLE_NAME + " SET " + TileTable.COL_LAST_USED + "=? WHERE " + TileTable.COL_LAYER_ID + " = ? AND " + TileTable.COL_X + " = ? AND " + TileTable.COL_Y + "=?";
  
  public synchronized void updateLastUsed(Tile tile) {
    
    long start = System.currentTimeMillis();

    SQLiteStatement updateLastUsedStatement = db.compileStatement(SQL_UPDATE_LAST_USED);
    updateLastUsedStatement.clearBindings();
    updateLastUsedStatement.bindLong(1, tile.getLastUsed());
    updateLastUsedStatement.bindString(2, tile.getLayer().getName());
    updateLastUsedStatement.bindLong(3, tile.getX());
    updateLastUsedStatement.bindLong(4, tile.getY());
    updateLastUsedStatement.executeUpdateDelete();
    
    Log.i(TAG, String.format("Updated last-used in %d ms", (System.currentTimeMillis() - start)));
  }
  
  private static final String SQL_UPDATE_BITMAP = "UPDATE " + TileTable.TABLE_NAME + " SET " + TileTable.COL_LAST_USED + "=?," + TileTable.COL_IMAGE + "=? WHERE " + TileTable.COL_LAYER_ID + " = ? AND " + TileTable.COL_X + " = ? AND " + TileTable.COL_Y + "=?";

  public synchronized void updateBitmap(Tile tile, byte[] image) {
    
    long start = System.currentTimeMillis();
    
    SQLiteStatement statement = db.compileStatement(SQL_UPDATE_BITMAP);
    statement.clearBindings();
    statement.bindLong(1, tile.getLastUsed());
    statement.bindBlob(2, image);
    statement.bindString(3, tile.getLayer().getName());
    statement.bindLong(4, tile.getX());
    statement.bindLong(5, tile.getY());
    statement.executeUpdateDelete();

    Log.i(TAG, String.format("Updated bitmap in %d ms", (System.currentTimeMillis() - start)));
  }
  
  private static final String SQL_INSERT_TILE = "INSERT INTO " + TileTable.TABLE_NAME + " (" + TileTable.COL_LAYER_ID + "," + TileTable.COL_X + "," + TileTable.COL_Y + "," + TileTable.COL_LAST_USED + "," + TileTable.COL_IMAGE + ") VALUES(?,?,?,?,?)";

  public synchronized void insertTile(Tile tile, byte[] image) {
    
    long start = System.currentTimeMillis();

    SQLiteStatement statement = db.compileStatement(SQL_INSERT_TILE);
    statement.clearBindings();
    statement.bindString(1, tile.getLayer().getName());
    statement.bindLong(2, tile.getX());
    statement.bindLong(3, tile.getY());
    statement.bindLong(4, tile.getLastUsed());
    statement.bindBlob(5, image);
    if (statement.executeInsert() >= 0) {
      tileCount++;
    }
    
    Log.i(TAG, String.format("Inserted row in %d ms (tileCount=%d)", (System.currentTimeMillis() - start), tileCount));
  }
  
  private static final String SQL_DELETE_LEAST_RECENTLY_USED = "DELETE FROM " + TileTable.TABLE_NAME + " WHERE ROWID IN (SELECT ROWID FROM " + TileTable.TABLE_NAME + " ORDER BY " + TileTable.COL_LAST_USED + " ASC LIMIT ?)";

  public synchronized void deleteLeastRecentlyUsed(int numberToDelete) {
    
    long start = System.currentTimeMillis();
    
    SQLiteStatement statement = db.compileStatement(SQL_DELETE_LEAST_RECENTLY_USED);
    statement.clearBindings();
    statement.bindLong(1, numberToDelete);
    int rowsDeleted = statement.executeUpdateDelete();
    if (rowsDeleted > 0) {
      tileCount -= rowsDeleted;
    }

    Log.i(TAG, String.format("Deleted %d rows in %d ms (tileCount=%d)", rowsDeleted, (System.currentTimeMillis() - start), tileCount));
  }
}
