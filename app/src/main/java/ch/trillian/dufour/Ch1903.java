package ch.trillian.dufour;

import android.location.Location;

public final class Ch1903 {

  public static final double[] wgs84toCh1903(Location location) {

    double[] result = new double[3];
    
    // calculate ch1903 coordinates
    double p = (location.getLatitude() * 3600.0 - 169028.66) / 10000.0;
    double l = (location.getLongitude() * 3600.0 - 26782.5) / 10000.0;
    double x = 200147.07 + 308807.95 * p + 3745.25 * l * l + 76.63 * p * p + 119.79 * p * p * p - 194.56 * l * l * p;
    double y = 600072.37 + 211455.93 * l - 10938.51 * l * p - 0.36 * l * p * p - 44.54 * l * l * l;
    double h = location.getAltitude() - 49.55 + 2.73 * l + 6.94 * p;

    result[0] = x;
    result[1] = y;
    result[2] = h;
    
    return result;
  }

  public static final double[] ch1903toWgs84to(double x, double y, double h) {

    double[] result = new double[3];

    // calculate wgs84 coordinates
    x = (x - 600000.0) / 1000000.0;
    y = (y - 200000.0) / 1000000.0;

    double l = 2.6779094 + 4.728982 * x + 0.791484 * x * y + 0.1306 * x * y * y - 0.0436 * x * x * x;
    double p = 16.9023892 + 3.238272 * y - 0.270978 * x * x - 0.002528 * y * y - 0.0447 * x * x * y - 0.0140 * y * y * y;
    double a = h + 49.55 - 12.60 * x - 22.64 * y;
    l = l * 100 / 36;
    p = p * 100 / 36;

    result[0] = l;
    result[1] = p;
    result[2] = a;
    
    return result;
  }
}
