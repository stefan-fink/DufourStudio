package ch.trillian.dufour;

import android.util.Log;

public class Map {

    private final String name;
    private final Layer[] layers;
    private TileCache tileCache;

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
    }

    public int getLayerCount() {

        return layers.length;
    }

    public Layer getLayer(int layerIndex) {

        return layers[layerIndex];
    }

    public Layer getMatchingLayer(Layer actualLayer, float meterPerPixel) {

        Log.w("TRILLIAN", String.format("scale() meterPerPixel: %f, actualLayer.getMeterPerPixel: %f, actualLayer.getMinScale: %f, actualLayer.getMaxScale: %f", meterPerPixel, actualLayer.getMeterPerPixel(), actualLayer.getMinScale(), actualLayer.getMaxScale()));

        // return actual layer if it still matches
        float scale = actualLayer.getMeterPerPixel() / meterPerPixel;
        if (scale >= actualLayer.getMinScale() && scale <= actualLayer.getMaxScale()) {
            return actualLayer;
        }

        // find first matching layer
        // TODO: should find best match, not first one
        for (Layer layer : layers) {
            scale = layer.getMeterPerPixel() / meterPerPixel;
            if (scale >= layer.getMinScale() && scale <= layer.getMaxScale()) {
                return layer;
            }
        }

        // TODO: should return nothing
        return actualLayer;
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
}
