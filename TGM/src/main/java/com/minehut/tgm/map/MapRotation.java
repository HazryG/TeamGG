package com.minehut.tgm.map;

/**
 * Created by luke on 4/27/17.
 */
public interface MapRotation {
    MapContainer cycle();
    MapContainer getNext();
    void refresh();
}
