package ch.trillian.dufour;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;

public class Tile {

    private final Layer layer;
    private final int x;
    private final int y;
    private boolean loading;
    private boolean ok;

    private Bitmap bitmap;
    private long lastUsed;

    public Tile(Layer layer, int x, int y) {

        this.layer = layer;
        this.x = x;
        this.y = y;
    }

    @SuppressLint("DefaultLocale")
    public String toString() {

        return String.format("layer=%s, x=%d, y=%d", layer.getName(), x, y);
    }

    public String getUrl() {

        return layer.getUrl(this);
    }

    public Layer getLayer() {
        return layer;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setLoading() {
        loading = true;
        ok = false;
    }

    public boolean isLoading() {
        return loading;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(long lastUsed) {
        this.lastUsed = lastUsed;
    }

    public boolean isOk() {
        return ok;
    }

    public boolean isFailed() {
        return !ok;
    }

    public void setOK() {
        loading = false;
        ok = true;
    }

    public void setFailed() {
        loading = false;
        ok = false;
    }
}
