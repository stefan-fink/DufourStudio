package ch.trillian.dufour;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;

public class TileLoader {

    private static final String TAG = "LOADER";

    public static final int NUMBER_OF_PRIORITIES = 2;
    public static final int PRIORITY_HIGH = 0;
    public static final int PRIORITY_LOW = 1;

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

        void onLoadFinished(Tile tile);
    }

    private static class LoaderHandler extends Handler {

        private final WeakReference<TileLoader> tileLoaderRef;

        public LoaderHandler(TileLoader tileLoader) {

            tileLoaderRef = new WeakReference<>(tileLoader);
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
    }

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

    public void orderLoadTile(Tile tile, int priority) {

        databaseLoader.orderLoad(tile, priority);
    }

    public void cancelLoadTile(Tile tile) {

        databaseLoader.cancelLoad(tile);
        urlLoader.cancelLoad(tile);
    }

    private class DatabaseLoader implements Runnable {

        private boolean pause;
        private boolean destroy;
        private Thread thread;
        private final ArrayDeque<Tile>[] queues = new ArrayDeque[NUMBER_OF_PRIORITIES];

        public DatabaseLoader() {

            thread = new Thread(this);
            thread.start();

            for (int i = 0; i < NUMBER_OF_PRIORITIES; i++) {
                queues[i] = new ArrayDeque<>();
            }
        }

        public void onPause() {

            synchronized (this) {
                pause = true;
                this.notify();
            }
        }

        public void onResume() {

            synchronized (this) {
                pause = false;
                this.notify();
            }
        }

        public void onDestroy() {

            synchronized (this) {
                destroy = true;
                this.notify();
            }
        }

        private int totalQueuesSize() {

            int number = 0;

            for (ArrayDeque queue: queues) {
                number += queue.size();
            }

            return number;
        }

        public void orderLoad(Tile tile, int priority) {

            synchronized (this) {

                priority = Math.min(Math.max(0, priority), NUMBER_OF_PRIORITIES);

                // put tile in order queue
                queues[priority].offer(tile);

                if (totalQueuesSize() == 1) {
                    this.notify();
                }
            }
        }

        public void cancelLoad(Tile tile) {

            synchronized (this) {
                for (ArrayDeque queue: queues) {
                    queue.remove(tile);
                }
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

                    synchronized (this) {

                        if (destroy) {
                            break;
                        }

                        if (pause) {
                            Log.i(TAG, "DatabaseThread paused (loaded " + numTiles + " tiles).");
                            this.wait();
                            numTiles = 0;
                            continue;
                        }

                        // for (ArrayDeque<Tile> queue: queues) {
                        for (int i = 0; i < NUMBER_OF_PRIORITIES; i++) {
                            if ((tile = queues[i].poll()) != null) {
                                Log.i(TAG, "DatabaseThread dequeued tile from priority: " + i);
                                break;
                            }
                        }

                        if (tile == null) {
                            Log.i(TAG, "DatabaseThread waiting (loaded " + numTiles + " tiles).");
                            this.wait();
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
        private final ArrayDeque<Tile> queue = new ArrayDeque<>();

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

                    Tile tile;

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
                long start = System.currentTimeMillis();

                // open http stream
                URL url = new URL(tile.getUrl());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.addRequestProperty("referer", "http://map.geo.admin.ch/");
                InputStream inputStream = connection.getInputStream();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK || connection.getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT) {

                    // read inputStream into byte[] if there is data
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        int numRead;
                        byte[] block = new byte[16384];
                        while ((numRead = inputStream.read(block, 0, block.length)) != -1) {
                            buffer.write(block, 0, numRead);
                        }
                    }

                    inputStream.close();
                    connection.disconnect();

                    // convert image
                    byte[] image = null;
                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        buffer.flush();
                        image = buffer.toByteArray();
                        long startDecode = System.currentTimeMillis();
                        Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
                        // Log.i(TAG, String.format("Decoded %d bytes in %d ms (%s)", image == null ? 0 : image.length, (System.currentTimeMillis() - startDecode), tile));
                        if (bitmap != null) {
                            tile.setBitmap(bitmap);
                        }
                    }

                    // Log.i(TAG, String.format("Downloaded %d bytes in %d ms (%s)", image == null ? 0 : image.length, (System.currentTimeMillis() - start), tile));

                    // write tile to database
                    tile.setLastUsed(System.currentTimeMillis());
                    tile.setOK();
                    handler.obtainMessage(LOADED_FROM_URL, tile).sendToTarget();
                    insertOrUpdateTileBitmap(database, tile, image);
                    return true;
                }

            } catch (Exception e) {
                Log.w(TAG, "Exception: " + e.getMessage(), e);
            }

            // download failed
            tile.setFailed();
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
