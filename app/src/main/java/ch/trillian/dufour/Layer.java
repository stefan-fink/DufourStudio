package ch.trillian.dufour;

import android.location.Location;


public class Layer {

  private final String name; 
  private final String urlName; 
  private final String urlFormat;
  
  // the top left corner in ch1903 coordinates
  private final float left;
  private final float top;
  
  // the number of meters per pixel
  private float meterPerPixel;

  // the size of each tile
  private final int tileSizeX;
  private final int tileSizeY;
  
  // the indexes used for loading tiles via URL
  private final int leftTile;
  private final int topTile;
  private final int rightTile;
  private final int bottomTile;
  
  // the total size in number of tiles
  private final int tilesX;
  private final int tilesY;
  
  // the minimum and maximum scale this layer should be used to
  private float minScale;
  private float maxScale;
  
  private Map map;
  private int index;
  
  public Layer(String name, String urlName, String urlFormat, float left, float top, float meterPerPixel, int tileSizeX, int tileSizeY, int leftTile, int topTile, int rightTile, int bottomTile) {
    
    this.name = name;
    this.urlName = urlName;
    this.urlFormat = urlFormat;
    this.left = left;
    this.top = top;
    this.meterPerPixel = meterPerPixel;
    this.tileSizeX = tileSizeX;
    this.tileSizeY = tileSizeY;
    this.leftTile = leftTile;
    this.topTile = topTile;
    this.rightTile = rightTile;
    this.bottomTile = bottomTile;
    
    tilesX = rightTile > leftTile ? rightTile - leftTile + 1 : leftTile - rightTile + 1; 
    tilesY = bottomTile > topTile ? bottomTile - topTile + 1 : topTile - bottomTile + 1; 
  }

  public String[] getDisplayCoordinates(float pixelX, float pixelY) {
    
    float x = left + pixelX * meterPerPixel;
    float y = top - pixelY * meterPerPixel;
    
    return new String[] { String.format("%.0f", x), String.format("%.0f", y) };
  }
  
  public float[] locationToMapPixel(Location location) {
    
    double[] ch1903 = Ch1903.wgs84toCh1903(location);

    float[] mapPixel = new float[2];
    mapPixel[0] = ((float) ch1903[1] - left) / meterPerPixel;
    mapPixel[1] = (top - (float) ch1903[0]) / meterPerPixel;
    
    return mapPixel;
  }
  
  public Location mapPixelTolocation(float mapPixelX, float mapPixelY) {
    
    float x = mapPixelX * meterPerPixel + left;
    float y = top - mapPixelY * meterPerPixel;

    double[] wgs84 = Ch1903.ch1903toWgs84to(x, y, 600f);

    Location location = new Location("Dufour");
    location.setLongitude(wgs84[0]);
    location.setLatitude(wgs84[1]);
    
    return location;
  }
  
  public Map getMap() {
    
    return map;
  }
  
  public void setMap(Map map) {
    
    this.map = map;
  }
  
  public int getIndex() {
    
    return index;
  }
  
  public void setIndex(int layerIndex) {
    
    this.index = layerIndex;
  }
  
  public float getMeterPerPixel() {
    
    return meterPerPixel;
  }
  
  public Layer getLayerIn() {
    
    if (index + 1 < map.getLayerCount()) {
      return map.getLayer(index + 1);
    }
    
    return null;
  }
  
  public Layer getLayerOut() {
    
    if (index > 0) {
      return map.getLayer(index - 1);
    }
    
    return null;
  }
  
  public String getUrl(Tile tile) {
    
    return String.format(urlFormat, urlName, getUrlX(tile.getX()), getUrlY(tile.getY()));
  }
  
  public int getUrlX(int x) {
    
    return leftTile < rightTile ? leftTile + x : leftTile - x;
  }
  
  public int getUrlY(int y) {
    
    return topTile < bottomTile ? topTile + y : topTile - y;
  }
  
  public boolean hasTile(int x, int y) {

    return x >= 0 && x < tilesX && y >= 0 && y < tilesY;
  }
  
  public String getName() {
    return name;
  }

  public int getTileSizeX() {
    return tileSizeX;
  }

  public int getTileSizeY() {
    return tileSizeY;
  }

  public int getLeftTile() {
    return leftTile;
  }

  public int getTopTile() {
    return topTile;
  }

  public int getRightTile() {
    return rightTile;
  }

  public int getBottomTile() {
    return bottomTile;
  }

  public int getSizeX() {
    return tilesX;
  }

  public int getSizeY() {
    return tilesY;
  }

  public float getMinScale() {
    return minScale;
  }

  public float getMaxScale() {
    return maxScale;
  }
  
  public void setMinScale(float minScale) {
    this.minScale = minScale;
  }

  public void setMaxScale(float maxScale) {
    this.maxScale = maxScale;
  }
}
