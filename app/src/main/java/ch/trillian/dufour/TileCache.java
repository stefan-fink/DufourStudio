package ch.trillian.dufour;

import android.util.Log;

public class TileCache {

  private static final String TAG = "CACHE";

  public final static int PRELOAD_SIZE = 1;

  private Map map;
  private Tile[][][] cache;
  private CacheListener cacheListener;

  public interface CacheListener {

    public void onOrderLoadTile(Tile tile);

    public void onCancelLoadTile(Tile tile);
  }

  public TileCache(Map map, int preloadSize, int screenSizeX, int screenSizeY) {

    this.map = map;

    // create array of layers
    cache = new Tile[map.getLayerCount()][][];

    for (int layerIndex = 0; layerIndex < map.getLayerCount(); layerIndex++) {

      Layer layer = map.getLayer(layerIndex);
      int cacheSizeX = (int) ((1f / layer.getMinScale()) * screenSizeX / layer.getTileSizeX()) + 2 * preloadSize + 2;
      int cacheSizeY = (int) ((1f / layer.getMinScale()) * screenSizeY / layer.getTileSizeY()) + 2 * preloadSize + 2;

      cacheSizeX = Math.min(cacheSizeX, layer.getSizeX());
      cacheSizeY = Math.min(cacheSizeY, layer.getSizeY());

      // create array of columns
      cache[layerIndex] = new Tile[cacheSizeY][];

      // create arrays of rows
      for (int y = 0; y < cacheSizeY; y++) {
        cache[layerIndex][y] = new Tile[cacheSizeX];
      }

      Log.w("TRILLIAN", "Created cache: mapName=" + map.getName() + ", layerName=" + layer.getName() + ", cacheSizeX=" + cacheSizeX + ", cacheSizeY=" + cacheSizeY);
    }
  }

  public void setCacheListener(CacheListener cacheListener) {

    this.cacheListener = cacheListener;
  }

  public Tile getTile(Layer layer, int x, int y) {

    if (this.map != layer.getMap()) {
      return null;
    }

    int layerIndex = layer.getIndex();

    if (!map.getLayer(layerIndex).hasTile(x, y)) {
      return null;
    }

    int cacheSizeX = cache[layerIndex][0].length;
    int cacheSizeY = cache[layerIndex].length;

    int cacheIndexX = x % cacheSizeX;
    int cacheIndexY = y % cacheSizeY;

    Tile tile = cache[layerIndex][cacheIndexY][cacheIndexX];

    // check if current cached item matches the requested one
    if (tile != null) {
      if (tile.getX() != x || tile.getY() != y) {
        if (tile.isLoading()) {
          cancelLoad(tile);
        }
        tile = null;
      }
    }

    // order new tile if none exists
    if (tile == null) {
      tile = new Tile(layer, x, y);
      cache[layerIndex][cacheIndexY][cacheIndexX] = tile;
      orderLoad(tile);
    }

    return tile;
  }

  public void preloadRegion(Layer layer, int minTileX, int maxTileX, int minTileY, int maxTileY) {

    Log.i(TAG, String.format("Preloading layer=%s, minTileX=%d, maxTileX=%d, minTileY=%d, maxTileY=%d " , layer.getName(), minTileX, maxTileX, minTileY, maxTileY));
    
    int layerIndex = layer.getIndex();

    // cancel load for all other layers or for tiles that don't fit the region of the current layer
    for (int l = cache.length - 1; l >= 0; l--) {
      for (int y = cache[l].length - 1; y >= 0; y--) {
        for (int x = cache[l][y].length - 1; x >= 0; x--) {

          Tile tile = cache[l][y][x];

          if (tile == null) {
            continue;
          }

          if (tile.isLoading()) {
            if ((l != layerIndex) || (l == layerIndex) && (tile.getX() < minTileX || tile.getX() > maxTileX) && (tile.getY() < minTileY || tile.getY() > maxTileY)) {
              cancelLoad(tile);
              cache[l][y][x] = null;
            }
          }
        }
      }
    }

    // order loads for all region's tiles
    for (int y = maxTileY; y >= minTileY; y--) {
      for (int x = maxTileX; x >= minTileX; x--) {
        getTile(layer, x, y);
      }
    }

    // order loads for preload region
    for (int y = maxTileY + PRELOAD_SIZE; y >= minTileY - PRELOAD_SIZE; y--) {
      if (y > maxTileY || y < minTileY) {
        for (int x = maxTileX + PRELOAD_SIZE; x >= minTileX - PRELOAD_SIZE; x--) {
          getTile(layer, x, y);
        }
      } else {
        for (int x = maxTileX + PRELOAD_SIZE; x > maxTileX; x--) {
          getTile(layer, x, y);
        }
        for (int x = minTileX - PRELOAD_SIZE; x < minTileX; x++) {
          getTile(layer, x, y);
        }
      }
    }
  }

  private void orderLoad(Tile tile) {

    if (cacheListener != null) {
      cacheListener.onOrderLoadTile(tile);
    }
  }

  private void cancelLoad(Tile tile) {
    
    if (cacheListener != null) {
      cacheListener.onCancelLoadTile(tile);
    }
  }
}
