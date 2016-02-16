package ch.trillian.dufour;

import android.util.Log;

public class Map {

    private final String name;
    private final Layer[] layers;
    private TileCache tileCache;
    private Layer currentLayer;

    public Map(String name, Layer[] layers, float minScale, float maxScale, float minScaleThreshold, float maxScaleThreshold) {

        this.name = name;
        this.layers = layers;
        this.tileCache = tileCache;

        // set layers map and indexes
        for (int i = 0; i < layers.length; i++) {
            layers[i].setMap(this);
            layers[i].setIndex(i);
        }

        // set minScale and maxScale of layers
        for (int i = 0; i < layers.length - 1; i++) {
            layers[i].setMinScale(minScaleThreshold);
            layers[i].setMaxScale(maxScaleThreshold * layers[i].getMeterPerPixel() / layers[i + 1].getMeterPerPixel());
        }
        layers[0].setMinScale(minScale);
        layers[layers.length - 1].setMinScale(minScaleThreshold);
        layers[layers.length - 1].setMaxScale(maxScale);

        for (int i = 0; i < layers.length; i++) {
            Log.d("TRILLIAN", String.format("Map(): name: %s, minScale: %f, maxScale: %f", layers[i].getName(), layers[i].getMinScale(), layers[i].getMaxScale()));
        }
    }

    public int getLayerCount() {

        return layers.length;
    }

    public Layer getLayer(int layerIndex) {

        return layers[layerIndex];
    }

    public void setMatchingLayer(float meterPerPixel) {

        // return actual layer if it still matches
        if (currentLayer != null) {
            float scale = currentLayer.getMeterPerPixel() / meterPerPixel;
            if (scale >= currentLayer.getMinScale() && scale <= currentLayer.getMaxScale()) {
                return;
            }
        }

        // find first matching layer
        // TODO: should find best match, not first one
        for (Layer layer : layers) {
            float scale = layer.getMeterPerPixel() / meterPerPixel;
            if (scale >= layer.getMinScale() && scale <= layer.getMaxScale()) {
                currentLayer = layer;
                return;
            }
        }

        currentLayer = null;
    }

    public String getName() {
        return name;
    }

    public Layer[] getLayers() {
        return layers;
    }

    public TileCache getTileCache() {
        return tileCache;
    }

    public void setTileCache(TileCache tileCache) {
        this.tileCache = tileCache;
    }

    public Layer getCurrentLayer() {
        return currentLayer;
    }

    public void setCurrentLayer(Layer currentLayer) {
        this.currentLayer = currentLayer;
    }
}
