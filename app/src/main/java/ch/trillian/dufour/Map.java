package ch.trillian.dufour;

public class Map {

  private final String name;
  private final Layer[] layers;
  
  public Map(String name, Layer[] layers, float minScale, float maxScale, float minScaleThreshold, float maxScaleThreshold) {
    
    this.name = name;
    this.layers = layers;
    
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
  
  public String getName() {
    return name;
  }

  public Layer[] getLayers() {
    return layers;
  }
}
