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

        void onSizeChanged(int w, int h, int oldw, int oldh);

        Tile onGetTile(Layer layer, int x, int y);

        void preloadRegion(Layer layer, int minTileX, int maxTileX, int minTileY, int maxTileY);
    }

    // view listener (our activity)
    private ViewListener viewListener;

    private static final int INVALID_POINTER_ID = -1;

    // the maps
    private Map[] maps = new Map[] {};

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

    // meterX/meterY are CH1903 coordinates of the left upper corner
    private float meterPerPixel;
    private float meterX;
    private float meterY;

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
        tileLoadFailedBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_tile_load_failed);

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

    private float ch1903ToScreenX(float ch1903X) {

        return (ch1903X - meterX) / meterPerPixel;
    }

    private float ch1903ToScreenY(float ch1903Y) {

        return (meterY - ch1903Y) / meterPerPixel;
    }

    private float screenToCh1903X(float screenX) {

        return meterX + screenX * meterPerPixel;
    }

    private float screenToCh1903Y(float screenY) {

        return meterY - screenY * meterPerPixel;
    }

    public void scale(float scaleFactor) {

        scale(scaleFactor, centerX, centerY);
    }

    private void move(float dx, float dy) {

        meterX -= dx * meterPerPixel;
        meterY += dy * meterPerPixel;

        // disable gps tracking if we moved too far away from GPS location
        if (gpsLastLocation != null && gpsTracking) {

            // calculate delta in screen pixels from gpsLastLocation to screen center
            double[] gpsLastLocationCh1903 = Ch1903.wgs84toCh1903(gpsLastLocation);
            float deltaX = ch1903ToScreenX((float) gpsLastLocationCh1903[1]) - centerX;
            float deltaY = ch1903ToScreenY((float) gpsLastLocationCh1903[0]) - centerY;
            float deltaSquare = deltaX * deltaX + deltaY * deltaY;
            float gpsTrackDistance = Math.min(screenSizeX, screenSizeY) / 10;
            if (deltaSquare > gpsTrackDistance * gpsTrackDistance) {
                setGpsTracking(false);
            }
        }

        invalidate();
    }

    public void scale(float scaleFactor, float focusScreenX, float focusScreenY) {

        // calculate focus in CH1903
        float focusCh1903X = screenToCh1903X(focusScreenX);
        float focusCh1903Y = screenToCh1903Y(focusScreenY);

        // calculate new meterPerPixel
        // TODO: replace min/max by better code
        meterPerPixel = meterPerPixel / scaleFactor;
        meterPerPixel = Math.min(meterPerPixel, 250f * 1.5f);
        meterPerPixel = Math.max(meterPerPixel, 1.0f * 0.1f);

        // calculate new meterX / meterY
        meterX = focusCh1903X - focusScreenX * meterPerPixel;
        meterY = focusCh1903Y + focusScreenY * meterPerPixel;

        // maybe layer changed
        for(Map map : maps) {
            map.setMatchingLayer(meterPerPixel);
        }

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

        float oldCenterCh1903X = screenToCh1903X(centerX);
        float oldCenterCh1903Y = screenToCh1903Y(centerY);

        centerX = (float) w / 2f;
        centerY = (float) h / 2f;
        screenSizeX = w;
        screenSizeY = h;

        meterX = oldCenterCh1903X - centerX * meterPerPixel;
        meterY = oldCenterCh1903Y + centerY * meterPerPixel;

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
                    move(dx, dy);
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

    @SuppressLint("DefaultLocale")
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        for(Map map : maps) {
            drawMap(canvas, map);
        }

        drawPoiPosition(canvas);

        drawGpsPosition(canvas);

        drawInfo(canvas);

        drawCross(canvas);
    }

    private void drawMap(Canvas canvas, Map map) {

        Layer layer = map.getCurrentLayer();

        if (layer == null) {
            return;
        }

        // prepare canvas
        float scale = layer.getMeterPerPixel() / meterPerPixel;
        float deltaPixelX = (meterX - layer.getLeft()) / meterPerPixel;
        float deltaPixelY = (layer.getTop() - meterY) / meterPerPixel;
        canvas.save();
        canvas.translate(-deltaPixelX, -deltaPixelY);
        canvas.scale(scale, scale);

        // order tiles to draw
        updateTilesMinMax(layer);
        int minTileX = layer.getMinTileX();
        int maxTileX = layer.getMaxTileX();
        int minTileY = layer.getMinTileY();
        int maxTileY = layer.getMaxTileY();

        float incX = layer.getTileSizeX();
        float incY = layer.getTileSizeY();
        float minX = minTileX * incX;
        float minY = minTileY * incY;
        float x, y;

        // draw bitmaps
        x = minX;
        for (int i = minTileX; i <= maxTileX; i++) {
            y = minY;
            for (int j = minTileY; j <= maxTileY; j++) {
                if (viewListener != null) {
                    Tile tile = viewListener.onGetTile(layer, i, j);
                    if (tile != null) {
                        if (tile.isOk()) {
                            Bitmap bitmap = tile.getBitmap();
                            if (bitmap != null) {
                                canvas.drawBitmap(bitmap, x, y, mapPaint);
                            }
                        } else {
                            Bitmap bitmap = tile.isLoading() ? tileLoadingBitmap : tileLoadFailedBitmap;
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
        for (int i = minTileX; i <= maxTileX; i++) {
            y = minY + incY / 2;
            for (int j = minTileY; j <= maxTileY; j++) {
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

    private void drawPoiPosition(Canvas canvas) {

        if (poiLocation == null) {
            return;
        }

        // get coordinates of POI position in screen pixels
        double[] ch1903 = Ch1903.wgs84toCh1903(poiLocation);
        float x = ch1903ToScreenX((float) ch1903[1]);
        float y = ch1903ToScreenY((float) ch1903[0]);

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

    private void drawGpsPosition(Canvas canvas) {

        if (gpsLastLocation == null) {
            return;
        }

        // get coordinates of GPS position in screen pixels
        double[] ch1903 = Ch1903.wgs84toCh1903(gpsLastLocation);
        float x = ch1903ToScreenX((float) ch1903[1]);
        float y = ch1903ToScreenY((float) ch1903[0]);

        // prepare canvas
        canvas.save();
        canvas.translate(x, y);

        // draw accuracy
        if (gpsLastLocation.hasAccuracy()) {
            float accuracySize = gpsLastLocation.getAccuracy() / meterPerPixel;
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
    private void drawInfo(Canvas canvas) {

        if (!showInfo) {
            return;
        }

        float lineHeight = infoPaint.getFontSpacing() * 1.3f;

        // draw coordinates
        float ch1903X = screenToCh1903X(centerX);
        float ch1903Y = screenToCh1903Y(centerY);

        String text = String.format("%6.0f, %6.0f (%1.2f mpp)", ch1903X, ch1903Y, meterPerPixel);
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

    private void drawInfoText(Canvas canvas, Bitmap bitmap, String text, float x, float y, float width, float height, int backgroundColor, Paint paint) {

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
        canvas.drawBitmap(bitmap, x / bitmapScale, y / bitmapScale, paint);
        canvas.restore();

        // draw text
        paint.setColor(infoTextColor);
        canvas.drawText(text, x + height + paint.descent(), y - paint.ascent() + 0.5f * (height - paint.getFontSpacing()), paint);
    }

    private void drawCross(Canvas canvas) {

        // draw cross
        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.drawCircle(0, 0, crossSize, crossPaint);
        canvas.drawLine(-crossSize, 0, crossSize, 0, crossPaint);
        canvas.drawLine(0, -crossSize, 0, crossSize, crossPaint);
        canvas.restore();
    }

    private void updateTilesMinMax(Layer layer) {

        // calculate new tile-region
        int minTileXnew = (int) Math.floor((meterX - layer.getLeft()) / layer.getMeterPerPixel() / layer.getTileSizeX());
        int maxTileXnew = (int) Math.floor((meterX + screenSizeX * meterPerPixel - layer.getLeft()) / layer.getMeterPerPixel() / layer.getTileSizeX());
        int minTileYnew = (int) Math.floor((layer.getTop()- meterY) / layer.getMeterPerPixel() / layer.getTileSizeY());
        int maxTileYnew = (int) Math.floor((layer.getTop()- meterY + screenSizeY * meterPerPixel) / layer.getMeterPerPixel() / layer.getTileSizeY());

        // remember new values and preload new region
        if (minTileXnew != layer.getMinTileX() || maxTileXnew != layer.getMaxTileX() || minTileYnew != layer.getMinTileY() || maxTileYnew != layer.getMaxTileY()) {

            // remember new values
            layer.setMinTileX(minTileXnew);
            layer.setMaxTileX(maxTileXnew);
            layer.setMinTileY(minTileYnew);
            layer.setMaxTileY(maxTileYnew);

            // preload tiles
            if (viewListener != null) {
                viewListener.preloadRegion(layer, minTileXnew, maxTileXnew, minTileYnew, maxTileYnew);
            }
        }
    }

    public void setLocation(Location location) {

        if (location == null) {
            return;
        }

        Log.w("TRILLIAN", String.format("setLocation: %f, %f", location.getLongitude(), location.getLatitude()));

        double[] ch1903 = Ch1903.wgs84toCh1903(location);

        meterX = (float) ch1903[1] - centerX * meterPerPixel;
        meterY = (float) ch1903[0] + centerY * meterPerPixel;

        invalidate();
    }

    public Location getLocation() {

        float x = screenToCh1903X(centerX);
        float y = screenToCh1903Y(centerY);

        double[] wgs84 = Ch1903.ch1903toWgs84to(x, y, 600f);

        Location location = new Location("Dufour");
        location.setLongitude(wgs84[0]);
        location.setLatitude(wgs84[1]);

        return location;
    }

    public void setPoiLocation(Location location) {

        poiLocation = location;

        invalidate();
    }

    public Location getPoiLocation() {

        return poiLocation;
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
        infoAltitude = gpsLastLocation.hasAltitude() ? String.format("%.0f m", ch1903[2]) : "- km/h";

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

    public void setMaps(Map[] maps, float meterPerPixel) {

        this.maps = maps;
        this.meterPerPixel = meterPerPixel;

        for(Map map : maps) {
            map.setMatchingLayer(meterPerPixel);
        }

        invalidate();
    }

    public void setShowInfo(boolean showInfo) {

        this.showInfo = showInfo;

        invalidate();
    }

    public float getMeterPerPixel() {
        return meterPerPixel;
    }
}
