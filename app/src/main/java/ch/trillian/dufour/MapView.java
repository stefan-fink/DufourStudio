package ch.trillian.dufour;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class MapView extends View {

  private static final int GPS_OUTDATE_INTERVAL = 10 * 1000;

  public interface ViewListener {
    
    public void onSizeChanged(int w, int h, int oldw, int oldh);

    public Tile onGetTile(Layer layer, int x, int y);
    
    public void preloadRegion(Layer layer, int minTileX, int maxTileX, int minTileY, int maxTileY);
    
    // public void gpsIsTracking(boolean isTracking);
  }
  
  // view listener (our activity)
  private ViewListener viewListener;
  
  private static final int INVALID_POINTER_ID = -1;

  // the map 
  private Layer layer;
  
  // attributes
  private float mapGridTextSize;
  private boolean mapGridDrawCoordinates;
  private String mapGridNoDataText;
  private float poiPosSize;
  private int poiPosColor;
  private int poiPosBorderColor;
  private float poiPosBorderStroke;
  private float gpsPosSize;
  private int gpsPosColor;
  private int gpsPosAltColor;
  private int gpsPosBorderColor;
  private float gpsPosBorderStroke;
  private int gpsPosAccuracyColor;
  private float infoTextSize;
  private int infoTextColor;
  private int infoLineColor;
  private int infoBackColor;
  private int infoBackAltColor;
  private float infoLineStroke;
  private float crossSize;
  private float crossStroke;

  // painters and paths
  private Paint mapPaint;
  private Paint gpsPaint;
  private Paint poiPaint;
  private Paint infoPaint;
  private Paint crossPaint;

  // bitmaps
  private Bitmap infoLocationBitmap;
  private Bitmap infoSpeedBitmap;
  private Bitmap infoAltitudeBitmap;
  private Bitmap tileLoadingBitmap;
  private Bitmap tileLoadFailedBitmap;
  
  // screen size in pixel
  private int screenSizeX;
  private int screenSizeY;

  // center position in pixel
  private float centerX;
  private float centerY;
  
  // position and zoom (position is in mapPixels)
  // pixelX = (positionX + x) * scale 
  // x = pixelX / scale - positionX
  private float positionX = 0f;
  private float positionY = 0f;
  private float scale = 1f;

  // min and max coordinates of tiles on screen
  private int minTileX;
  private int maxTileX;
  private int minTileY;
  private int maxTileY;

  // stuff for motion detection
  float lastTouchX;
  float lastTouchY;
  int activePointerId;

  // gesture detectors
  ScaleGestureDetector mScaleGestureDetector;
  GestureDetector mGestureDetector;

  // GPS
  private boolean gpsEnabled;
  private boolean gpsTracking;
  private boolean gpsStatus;
  private Location gpsLastLocation;
  private String infoSpeed = "?";
  private String infoAltitude = "?";
  
  // POI
  private Location poiLocation;
  
  // true if info is displayed
  private boolean showInfo;

  public MapView(Context context, AttributeSet attrs) {

    super(context, attrs);

    // get attributes
    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.MapView, 0, 0);
    try {
      mapGridTextSize = a.getDimension(R.styleable.MapView_mapGridTextSize, 50f);
      mapGridDrawCoordinates = a.getBoolean(R.styleable.MapView_mapGridDrawCoordinates, false);
      mapGridNoDataText = a.getString(R.styleable.MapView_mapGridNoDataText);
      poiPosSize = a.getDimension(R.styleable.MapView_poiPosSize, 10f);
      poiPosColor = a.getColor(R.styleable.MapView_poiPosColor, 0xFF000000);
      poiPosBorderColor = a.getColor(R.styleable.MapView_poiPosBorderColor, 0xFF000000);
      poiPosBorderStroke = a.getDimension(R.styleable.MapView_poiPosBorderStroke, 2f);
      gpsPosSize = a.getDimension(R.styleable.MapView_gpsPosSize, 10f);
      gpsPosColor = a.getColor(R.styleable.MapView_gpsPosColor, 0xFF000000);
      gpsPosAltColor = a.getColor(R.styleable.MapView_gpsPosAltColor, 0xFF000000);
      gpsPosBorderColor = a.getColor(R.styleable.MapView_gpsPosBorderColor, 0xFF000000);
      gpsPosBorderStroke = a.getDimension(R.styleable.MapView_poiPosBorderStroke, 2f);
      gpsPosAccuracyColor = a.getColor(R.styleable.MapView_gpsPosAccuracyColor, 0x50000000);
      infoTextSize = a.getDimension(R.styleable.MapView_infoTextSize, 20f);
      infoTextColor = a.getColor(R.styleable.MapView_infoTextColor, 0xFF000000);
      infoLineColor = a.getColor(R.styleable.MapView_infoLineColor, 0xFF000000);
      infoBackColor = a.getColor(R.styleable.MapView_infoBackColor, 0x80FFFFFF);
      infoBackAltColor = a.getColor(R.styleable.MapView_infoBackAltColor, 0x80FF0000);
      infoLineStroke = a.getDimension(R.styleable.MapView_infoLineStroke, 1f);
      crossSize = a.getDimension(R.styleable.MapView_crossSize, 10f);
      crossStroke = a.getDimension(R.styleable.MapView_crossStroke, 1f);
    } finally {
      a.recycle();
    }

    // initialize painters
    initPainters();

    // load bitmaps
    infoLocationBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_info_location);
    infoSpeedBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_info_speed);
    infoAltitudeBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_info_altitude);
    tileLoadingBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_tile_loading);
    tileLoadFailedBitmap  = BitmapFactory.decodeResource(getResources(), R.drawable.ic_tile_load_failed);
    
    // Create our ScaleGestureDetector
    mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
    mGestureDetector = new GestureDetector(context, new GestureListener());
  }

  private void initPainters() {

    mapPaint = new Paint(0);
    mapPaint.setColor(0xFF808080);
    mapPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    mapPaint.setTextSize(mapGridTextSize);
    mapPaint.setTextAlign(Paint.Align.CENTER);

    gpsPaint = new Paint(0);
    gpsPaint.setStrokeWidth(gpsPosBorderStroke);
    
    poiPaint = new Paint(0);
    poiPaint.setStrokeWidth(poiPosBorderStroke);

    infoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    infoPaint.setTextAlign(Paint.Align.LEFT);
    infoPaint.setTextSize(infoTextSize);
    infoPaint.setStrokeWidth(infoLineStroke);

    crossPaint = new Paint(0);
    crossPaint.setStyle(Paint.Style.STROKE);
    crossPaint.setStrokeWidth(crossStroke);
  }
  
  public float getScale() {
    
    return scale;
  }
  
  public void setScale(float newScale) {
    
    scale(newScale / scale);
  }
  
  public void scale(float scaleFactor) {
    
    scale(scaleFactor, (float) screenSizeX / 2, (float) screenSizeY / 2);
  }
  
  public void scale(float scaleFactor, float focusScreenX, float focusScreenY) {
    
    float newScale = scale * scaleFactor;

    // focal point in map-pixels
    float focusMapX = screen2map(focusScreenX, scale, positionX);
    float focusMapY = screen2map(focusScreenY, scale, positionY);

    if (newScale > layer.getMaxScale()) {
      
      // try to zoom layer in
      Layer newLayer = layer.getLayerIn();
      if (newLayer != null) {
        float scaleRatio = newLayer.getMeterPerPixel() / layer.getMeterPerPixel();
        newScale *= scaleRatio;
        focusMapX /= scaleRatio;
        focusMapY /= scaleRatio;
        layer = newLayer;
      }
      
    } else if (newScale < layer.getMinScale()) {
      
      // try to zoom layer out
      Layer newLayer = layer.getLayerOut();
      if (newLayer != null) {
        float scaleRatio = newLayer.getMeterPerPixel() / layer.getMeterPerPixel();
        newScale = newScale * scaleRatio;
        focusMapX /= scaleRatio;
        focusMapY /= scaleRatio;
        layer = newLayer;
      }
    }
    
    // Don't let the object get too small or too large.
    newScale = Math.max(layer.getMinScale(), Math.min(newScale, layer.getMaxScale()));

    positionX = focusScreenX / newScale - focusMapX;
    positionY = focusScreenY / newScale - focusMapY;

    scale = newScale;

    invalidate();
  }
  

  private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    @Override
    public boolean onScale(ScaleGestureDetector detector) {

      scale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
      return true;
    }
  }

  public void setViewListener(ViewListener viewListener) {
  
    this.viewListener = viewListener;
  }
  
  private class GestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onDoubleTap(MotionEvent e) {

      // reset viewport
      if (gpsLastLocation != null && !gpsTracking) {
        setGpsTracking(true);
        setLocation(gpsLastLocation);
      } else if (poiLocation != null) {
        setGpsTracking(false);
        setLocation(poiLocation);
      }
      
      return true;
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {

    float centerXnew = (float) w / 2f;
    float centerYnew = (float) h / 2f;
    
    positionX = (centerXnew - centerX + positionX * scale) / scale;
    positionY = (centerYnew - centerY + positionY * scale) / scale;

    screenSizeX = w;
    screenSizeY = h;
    
    centerX = centerXnew;
    centerY = centerYnew;
    
    if (viewListener != null) {
      viewListener.onSizeChanged(w, h, oldw, oldh);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent ev) {

    // let the ScaleGestureDetector inspect all events.
    mScaleGestureDetector.onTouchEvent(ev);

    // let the GestureDetector inspect all events.
    mGestureDetector.onTouchEvent(ev);

    final int pointerIndex;
    final float x;
    final float y;

    final int action = ev.getAction();
    switch (action & MotionEvent.ACTION_MASK) {

    case MotionEvent.ACTION_DOWN:

      // remember last touch
      lastTouchX = ev.getX();
      lastTouchY = ev.getY();
      activePointerId = ev.getPointerId(0);

      break;

    case MotionEvent.ACTION_MOVE:

      pointerIndex = ev.findPointerIndex(activePointerId);
      x = ev.getX(pointerIndex);
      y = ev.getY(pointerIndex);

      // only move if the ScaleGestureDetector isn't processing a gesture.
      if (!mScaleGestureDetector.isInProgress()) {

        // calculate the distance moved
        final float dx = x - lastTouchX;
        final float dy = y - lastTouchY;

        // move the viewport
        positionX += dx / scale;
        positionY += dy / scale;

        // disable gps tracking if we moved too far away from GPS location
        if (gpsLastLocation != null && gpsTracking) {
          
          // calculate screen pixel coordinates of GPS location
          float[] mapPixel = layer.locationToMapPixel(gpsLastLocation);
          float deltaX = map2screen(mapPixel[0], scale, positionX) - centerX;
          float deltaY = map2screen(mapPixel[1], scale, positionY) - centerY;
          float deltaSquare = deltaX * deltaX + deltaY * deltaY;
          float gpsTrackDistance = Math.min(screenSizeX, screenSizeX) / 10;
          if (deltaSquare > gpsTrackDistance * gpsTrackDistance) {
            setGpsTracking(false);
          }
        }
        
        invalidate();
      }

      // Remember this touch position for the next move event
      lastTouchX = x;
      lastTouchY = y;

      break;

    case MotionEvent.ACTION_UP:
    case MotionEvent.ACTION_CANCEL:
      activePointerId = INVALID_POINTER_ID;
      break;

    case MotionEvent.ACTION_POINTER_UP:

      // Extract the index of the pointer that left the touch sensor
      pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
      final int pointerId = ev.getPointerId(pointerIndex);

      // If it was our active pointer going up then choose a new active pointer and adjust accordingly.
      if (pointerId == activePointerId) {
        final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
        lastTouchX = ev.getX(newPointerIndex);
        lastTouchY = ev.getY(newPointerIndex);
        activePointerId = ev.getPointerId(newPointerIndex);
      }
      break;
    }

    return true;
  }

  private final float map2screen(float map, float scale, float position) {
    
    return (position + map) * scale;
  }
  
  private final float screen2map(float screen, float scale, float position) {
    
    return screen / scale - position;
  }
  
  @SuppressLint("DefaultLocale")
  protected void onDraw(Canvas canvas) {

    super.onDraw(canvas);
    
    drawMap(canvas);
    
    drawPoiPosition(canvas);
    
    drawGpsPosition(canvas);
    
    drawInfo(canvas);
    
    drawCross(canvas);
  }

  private final void drawMap(Canvas canvas) {
    
    // prepare canvas
    canvas.save();
    canvas.scale(scale, scale);
    canvas.translate(positionX, positionY);

    // order tiles to draw
    updateTilesMinMax();
    
    float incX = layer.getTileSizeX();
    float incY = layer.getTileSizeY();
    float minX = minTileX * incX;
    float minY = minTileY * incY;
    float x, y;
    
    // draw bitmaps
    x = minX;
    for(int i = minTileX; i <= maxTileX; i++) {
      y = minY;
      for(int j = minTileY; j <= maxTileY; j++) {
        if (viewListener != null) {
          Tile tile = viewListener.onGetTile(layer, i, j);
          if (tile != null) {
            Bitmap bitmap = tile.getBitmap();
            if (bitmap != null) {
              canvas.drawBitmap(bitmap, x, y, mapPaint);
            } else {
              bitmap = tile.isLoading() ? tileLoadingBitmap : tileLoadFailedBitmap;
              canvas.drawBitmap(bitmap, x + (incX - bitmap.getWidth()) / 2, y + (incY - bitmap.getHeight()) / 2, mapPaint);
            }
          }
        }
        y += incY;
      }
      x += incX;
    }
    
    // draw grid coordinates
    float textVerticalOffset = (mapPaint.descent() - mapPaint.ascent()) / 2 - mapPaint.descent();
    x = minX + incX / 2;
    for(int i = minTileX; i <= maxTileX; i++) {
      y = minY + incY / 2;
      for(int j = minTileY; j <= maxTileY; j++) {
        boolean hasTile = layer.hasTile(i, j);
        if (!hasTile || mapGridDrawCoordinates) {
          String text = layer.hasTile(i, j) ? "(" + i + "," + j + ")" : mapGridNoDataText;
          canvas.save();
          canvas.rotate(-45f, x, y);
          canvas.drawText(text, x, y + textVerticalOffset, mapPaint);
          canvas.restore();
        }
        y += incY;
      }
      x += incX;
    }
    
    canvas.restore();
  }

  private final void drawPoiPosition(Canvas canvas) {
    
    if (poiLocation == null) {
      return;
    }
      
    // get coordinates of POI position in screen pixels
    float[] mapPixel = layer.locationToMapPixel(poiLocation);
    float x = map2screen(mapPixel[0], scale, positionX);
    float y = map2screen(mapPixel[1], scale, positionY);
    
    // prepare canvas
    canvas.save();
    canvas.translate(x, y);

    // draw colored dot
    poiPaint.setColor(poiPosColor);
    poiPaint.setStyle(Paint.Style.FILL);
    canvas.drawCircle(0, 0, poiPosSize, poiPaint);
    poiPaint.setStyle(Paint.Style.STROKE);
    poiPaint.setColor(poiPosBorderColor);
    canvas.drawCircle(0, 0, poiPosSize, poiPaint);
    
    canvas.restore();
  }

  private final void drawGpsPosition(Canvas canvas) {
    
    if (gpsLastLocation == null) {
      return;
    }
      
    // get coordinates of GPS position in screen pixels
    float[] mapPixel = layer.locationToMapPixel(gpsLastLocation);
    float x = map2screen(mapPixel[0], scale, positionX);
    float y = map2screen(mapPixel[1], scale, positionY);
    
    // prepare canvas
    canvas.save();
    canvas.translate(x, y);

    // draw accuracy
    if (gpsLastLocation.hasAccuracy()) {
      float accuracySize = gpsLastLocation.getAccuracy() / layer.getMeterPerPixel() * scale;
      gpsPaint.setColor(gpsPosAccuracyColor);
      gpsPaint.setStyle(Paint.Style.FILL);
      canvas.drawCircle(0, 0, accuracySize, gpsPaint);
    }
    
    // draw colored dot
    gpsPaint.setColor(gpsTracking ? gpsPosColor : gpsPosAltColor);
    gpsPaint.setStyle(Paint.Style.FILL);
    canvas.drawCircle(0, 0, gpsPosSize, gpsPaint);
    gpsPaint.setStyle(Paint.Style.STROKE);
    gpsPaint.setColor(gpsPosBorderColor);
    canvas.drawCircle(0, 0, gpsPosSize, gpsPaint);
    
    canvas.restore();
  }

  @SuppressLint("DefaultLocale")
  private final void drawInfo(Canvas canvas) {
    
    if (!showInfo) {
      return;
    }
      
    float lineHeight = infoPaint.getFontSpacing() * 1.3f;
    
    // draw coordinates
    String[] displayCoordinates = layer.getDisplayCoordinates(screen2map(centerX, scale, positionX), screen2map(centerY, scale, positionY));
    String text = String.format("%s, %s (%1.2f@%s)", displayCoordinates[0], displayCoordinates[1], scale, layer.getName());
    drawInfoText(canvas, infoLocationBitmap, text, 0f, 0f, screenSizeX, lineHeight, infoBackColor, infoPaint);

    // draw GPS details
    if (gpsLastLocation != null) {
      int backgroundColor = gpsStatus ? infoBackColor : infoBackAltColor;
      drawInfoText(canvas, infoSpeedBitmap, infoSpeed, 0f, lineHeight, centerX, lineHeight, backgroundColor, infoPaint);
      infoPaint.setColor(infoLineColor);
      drawInfoText(canvas, infoAltitudeBitmap, infoAltitude, centerX, lineHeight, centerX, lineHeight, backgroundColor, infoPaint);
    }
    
    // draw lines
    infoPaint.setColor(infoLineColor);
    canvas.drawLine(0f, lineHeight, screenSizeX, lineHeight, infoPaint);
    if (gpsLastLocation != null) {
      canvas.drawLine(0f, 2 * lineHeight, screenSizeX, 2 * lineHeight, infoPaint);
      canvas.drawLine(centerX, lineHeight, centerX, 2 * lineHeight, infoPaint);
    }
  }

  private final void drawInfoText(Canvas canvas, Bitmap bitmap, String text, float x, float y, float width, float height, int backgroundColor, Paint paint) {

    // draw background
    paint.setColor(backgroundColor);
    paint.setStyle(Paint.Style.FILL_AND_STROKE);
    canvas.drawRect(x, y, x + width, y + height, paint);
    paint.setStyle(Paint.Style.FILL);

    // draw bitmap
    canvas.save();
    paint.setColor(0xFF000000);
    float bitmapScale = height / bitmap.getHeight();
    canvas.scale(bitmapScale, bitmapScale);
    canvas.drawBitmap(bitmap, x, y / bitmapScale, paint);
    canvas.restore();
    
    // draw text
    paint.setColor(infoTextColor);
    canvas.drawText(text, x + height + paint.descent(), y - paint.ascent() + 0.5f * (height - paint.getFontSpacing()), paint);
  }
  
  private final void drawCross(Canvas canvas) {
    
    // draw cross
    canvas.save();
    canvas.translate(centerX, centerY);
    canvas.drawCircle(0, 0, crossSize, crossPaint);
    canvas.drawLine(-crossSize, 0, crossSize, 0, crossPaint);
    canvas.drawLine(0, -crossSize, 0, crossSize, crossPaint);
    canvas.restore();
  }

  private void updateTilesMinMax() {
    
    // calculate new tile-region
    int minTileXnew = (int) Math.floor(-positionX / layer.getTileSizeX());
    int maxTileXnew = (int) Math.floor((screenSizeX / scale - positionX) / layer.getTileSizeX());
    int minTileYnew = (int) Math.floor(-positionY / layer.getTileSizeY());
    int maxTileYnew = (int) Math.floor((screenSizeY / scale - positionY) / layer.getTileSizeY());
    
    // remember new values and preload new region
    if (minTileXnew != minTileX || maxTileXnew != maxTileX || minTileYnew != minTileY || maxTileYnew != maxTileY) {
      
      // remember new values
      minTileX = minTileXnew;
      maxTileX = maxTileXnew;
      minTileY = minTileYnew;
      maxTileY = maxTileYnew;
      
      // preload tiles
      if (viewListener != null) {
        viewListener.preloadRegion(layer, minTileX, maxTileX, minTileY, maxTileY);
      }
    }
  }
  
  public void setLocation(Location location) {

    if (location == null) {
      return;
    }
    
    Log.w("TRILLIAN", String.format("setLocation: %f, %f", location.getLongitude(), location.getLatitude()));

    float[] mapPixel = layer.locationToMapPixel(location);
    positionX = centerX / scale - mapPixel[0];
    positionY = centerY / scale - mapPixel[1];
    
    invalidate();
  }

  public Location getLocation() {

    float mapPixelX = centerX / scale - positionX;
    float mapPixelY = centerY / scale - positionY;
    
    Location location = layer.mapPixelTolocation(mapPixelX, mapPixelY);
    
    return location;
  }

  public void setPoiLocation(Location location) {

    poiLocation = location;
    
    invalidate();
  }

  public Location getPoiLocation() {

    return poiLocation;
  }

  public Location getGpsLocation() {
  
    return gpsLastLocation;
  }
  
  public void setGpsLocation(Location location) {

    Log.w("TRILLIAN", "setGpsLocation() trackGps=" + gpsTracking);
    
    if (location == null) {
      gpsLastLocation = null;
      invalidate();
      return;
    }

    gpsLastLocation = location;
    
    double[] ch1903 = Ch1903.wgs84toCh1903(location);
    
    infoSpeed = gpsLastLocation.hasSpeed() ? String.format("%.1f km/h", gpsLastLocation.getSpeed() * 3.6f) : "- km/h";
    infoAltitude  = gpsLastLocation.hasAltitude() ? String.format("%.0f m", ch1903[2]) : "- km/h";

    // center map to gps position if we're tracking
    if (gpsTracking) {
      setLocation(gpsLastLocation);
    }

    updateGpsStatus();
    
    invalidate();
  }

  public void setGpsEnabled(boolean enable) {
    
    gpsEnabled = enable;
  }
  
  public boolean isGpsEnabled() {
    
    return gpsEnabled;
  }
  
  public void setGpsTracking(boolean tracking) {
    
    gpsTracking = tracking;

    // center map to gps position if we're tracking
    if (tracking) {
      setLocation(gpsLastLocation);
    }

    invalidate();
  }
  
  public boolean isGpsTracking() {
    
    return gpsTracking;
  }

  public void updateGpsStatus() {
    
    // check if GPS location is out-dated
    if (gpsLastLocation != null) {
      boolean newGpsStatus = System.currentTimeMillis() - gpsLastLocation.getTime() < GPS_OUTDATE_INTERVAL;
      if (newGpsStatus != gpsStatus) {
        gpsStatus = newGpsStatus;
        invalidate();
      }
    }
  }
  
  public void onTick() {

    updateGpsStatus();
  }
  
  public Layer getLayer() {
    
    return layer;
  }
  
  public void setLayer(Layer layer) {
    
    this.layer = layer;
    
    invalidate();
  }

  public boolean isShowInfo() {
  
    return showInfo;
  }
  
  public void setShowInfo(boolean showInfo) {
    
    this.showInfo = showInfo;
    
    invalidate();
  }
}
