package com.prowriter.app;

/** The "ProChat Voice" tile: opens the pop-up already listening, so you can speak your message. */
public class ProVoiceTileService extends ProTileService {
    @Override
    protected boolean voice() {
        return true;
    }
}
