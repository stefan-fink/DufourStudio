package ch.trillian.dufour;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class TileLoader {

  private static final String TAG = "LOADER";
  
  private static final int LOADED_FROM_DB = 1;
  private static final int LOADED_FROM_URL = 2;
  private static final int LOAD_FAILED = 3;
  
  // the minimum number of milliseconds before updating a tile's LAST_USED
  private static final int LAST_USED_THRESHOLD = 24 * 60 * 60 * 1000;
  
  // the maximum number of tiles to keep on DB
  private static int MAX_NUMBER_OF_TILES = 10000;
  
  // the number of tiles to delete from DB at once
  private static int DELETE_CHUNK_SIZE = 25;
  
  // the listener for finished loads
  private LoadListener loadListener;
  
  // the handler for synchronizing messages from threads 
  private final Handler handler;

  // the database loader with it's own thread
  private DatabaseLoader databaseLoader;

  // the URL loader with it's own thread
  private UrlLoader urlLoader;
  
  public interface LoadListener {

    public void onLoadFinished(Tile tile);
  }
  
  private static class LoaderHandler extends Handler {
    
    private final WeakReference<TileLoader> tileLoaderRef; 

    public LoaderHandler(TileLoader tileLoader) {
      
      tileLoaderRef = new WeakReference<TileLoader>(tileLoader);
    }
    
    public void handleMessage(Message message) {

      TileLoader tileLoader = tileLoaderRef.get();
      if (tileLoader != null) {
        LoadListener listener = tileLoader.loadListener;
        if (listener != null) {
          listener.onLoadFinished((Tile) message.obj);
        } else {
          Log.w(TAG, "LoadListener is null");
        }
      }
    }
  };

  public TileLoader(Context context) {
    
    MapDatabase.newInstance(context);
    
    handler = new LoaderHandler(this);
    databaseLoader = new DatabaseLoader();
    urlLoader = new UrlLoader();
  }

  public void onPause() {

    databaseLoader.onPause();
    urlLoader.onPause();
  }
  
  public void onResume() {

    databaseLoader.onResume();
    urlLoader.onResume();
  }
  
  public void onDestroy() {

    databaseLoader.onDestroy();
    urlLoader.onDestroy();
  }
  
  public void setLoadListener(LoadListener loadListener) {

    this.loadListener = loadListener;
  }

  public void orderLoadTile(Tile tile) {

    databaseLoader.orderLoad(tile);
  }

  public void cancelLoadTile(Tile tile) {

    databaseLoader.cancelLoad(tile);
    urlLoader.cancelLoad(tile);
  }

  private class DatabaseLoader implements Runnable {
  
    private boolean pause;
    private boolean destroy;
    private Thread thread;
    private ArrayDeque<Tile> queue = new ArrayDeque<Tile>();
    
    public DatabaseLoader() {
      
      thread = new Thread(this);
      thread.start();
    }
    
    public void onPause() {
      
      synchronized (queue) {
        pause = true;
        queue.notify();
      }
    }
    
    public void onResume() {
      
      synchronized (queue) {
        pause = false;
        queue.notify();
      }
    }
    
    public void onDestroy() {
      
      synchronized (queue) {
        destroy = true;
        queue.notify();
      }
    }
    
    public void orderLoad(Tile tile) {

      synchronized (queue) {

        // put tile in order queue
        queue.offer(tile);

        if (queue.size() == 1) {
          queue.notify();
        }
      }
    }

    public void cancelLoad(Tile tile) {

      synchronized (queue) {
        queue.remove(tile);
      }
    }

    public void run() {

      Log.i(TAG, "DatabaseThread started.");

      MapDatabase database = MapDatabase.getInstance();
      int numTiles = 0;
      
      try {
        
        database.openDatabase();
        
        while (true) {
  
          Tile tile = null;
  
          synchronized (queue) {
            
            if (destroy) {
              break;
            }
            
            if (pause) {
              Log.i(TAG, "DatabaseThread paused (loaded " + numTiles + " tiles).");
              queue.wait();
              numTiles = 0;
              continue;
            }
            
            if ((tile = queue.poll()) == null) {
              Log.i(TAG, "DatabaseThread waiting (loaded " + numTiles + " tiles).");
              queue.wait();
              numTiles = 0;
              continue;
            }
          }
  
          if (getTileFromDatabase(database, tile)) {
            numTiles++;
            continue;
          }
  
          // order tile from download thread
          urlLoader.orderLoad(tile);
        }
        
      } catch (InterruptedException e) {
        Log.w(TAG, "DatabaseThread has been interrupted.");
      }
      
      database.closeDatabase();

      Log.i(TAG, "DatabaseThread has been shut down.");
    }
    
    private boolean getTileFromDatabase(MapDatabase database, Tile tile) {

      // read image from database
      if (!database.readTile(tile)) {
        return false;
      }

      // notify GUI
      handler.obtainMessage(LOADED_FROM_DB, tile).sendToTarget();

      // update last used if update threshold reached
      long now = System.currentTimeMillis();
      if (now - tile.getLastUsed() > LAST_USED_THRESHOLD) {
        tile.setLastUsed(now);
        database.updateLastUsed(tile);
      }
      
      return true;
    }
  }
  
  private class UrlLoader implements Runnable {
    
    private boolean pause;
    private boolean destroy;
    private Thread thread;
    private ArrayDeque<Tile> queue = new ArrayDeque<Tile>();
    
    public UrlLoader() {
      
      thread = new Thread(this);
      thread.start();
    }
    
    public void onPause() {
      
      synchronized (queue) {
        pause = true;
        queue.notify();
      }
    }
    
    public void onDestroy() {
      
      synchronized (queue) {
        destroy = true;
        queue.notify();
      }
    }
    
    public void onResume() {
      
      synchronized (queue) {
        pause = false;
        queue.notify();
      }
    }
    
    public void orderLoad(Tile tile) {

      synchronized (queue) {

        // put tile in order queue
        queue.offer(tile);

        // (re)start thread
        if (thread == null) {
          thread = new Thread(this);
          thread.start();
        }
        
        if (queue.size() == 1) {
          queue.notify();
        }
      }
    }

    public void cancelLoad(Tile tile) {
      
      synchronized (queue) {
        queue.remove(tile);
      }
    }
    
    public void run() {

      Log.i(TAG, "DownloadThread started.");
      
      MapDatabase database = MapDatabase.getInstance();
      int numTiles = 0;

      try {
        
        database.openDatabase();
        
        while (true) {
  
          Tile tile = null;
  
          synchronized (queue) {
            
            if (destroy) {
              break;
            }
            
            if (pause) {
              Log.i(TAG, "DownloadThread paused (downloaded " + numTiles + " tiles).");
              queue.wait();
              numTiles = 0;
              continue;
            }
            
            if ((tile = queue.poll()) == null) {
              Log.i(TAG, "DownloadThread waiting (downloaded " + numTiles + " tiles).");
              queue.wait();
              numTiles = 0;
              continue;
            }
          }
  
          getTileFromUrl(database, tile);
          
          numTiles++;
        }
        
      } catch (InterruptedException e) {
        Log.w(TAG, "DownloadThread has been interrupted.");
      }
      
      database.closeDatabase();
      
      Log.i(TAG, "DownloadThread has been shut down.");
    }
    
    private boolean getTileFromUrl(MapDatabase database, Tile tile) {

      try {

        // open http stream
        URL url = new URL(tile.getUrl());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.addRequestProperty("referer", "http://map.geo.admin.ch/");
        InputStream inputStream = connection.getInputStream();

        // read inputStream into byte[]
        int numRead;
        byte[] block = new byte[16384];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while ((numRead = inputStream.read(block, 0, block.length)) != -1) {
          buffer.write(block, 0, numRead);
        }
        inputStream.close();
        connection.disconnect();
        buffer.flush();
        byte[] image = buffer.toByteArray();

        // convert byte[] to Bitmap
        Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
        if (bitmap != null) {
          tile.setBitmap(bitmap);
          tile.setLastUsed(System.currentTimeMillis());
          handler.obtainMessage(LOADED_FROM_URL, tile).sendToTarget();
          insertOrUpdateTileBitmap(database, tile, image);
          return true;
        }

      } catch (Exception e) {
        Log.w(TAG, "Exception: " + e.getMessage(), e);
      }

      // download failed
      tile.setLoadFailed(true);
      handler.obtainMessage(LOAD_FAILED, tile).sendToTarget();
      
      return false;
    }
    
    private void insertOrUpdateTileBitmap(MapDatabase database, Tile tile, byte[] image) {
      
      if (database.isTileExisting(tile)) {
        database.updateBitmap(tile, image);
      } else {
        if (database.getTileCount() > MAX_NUMBER_OF_TILES) {
          database.deleteLeastRecentlyUsed(DELETE_CHUNK_SIZE);
        }
        database.insertTile(tile, image);
      }
    }
  }
}
