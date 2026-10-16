package com.prowriter.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** The "ProChat" tile in the quick-settings panel. Tapping it opens ClipboardActivity. */
public class ProTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        // The panel was just pulled down: start connecting now so the tap feels instant.
        Rewriter.warmUp(Prefs.getProvider(this));
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isLocked()) {
            unlockAndRun(this::openPopup);
        } else {
            openPopup();
        }
    }

    @SuppressWarnings("deprecation")
    private void openPopup() {
        Intent intent = new Intent(this, ClipboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
