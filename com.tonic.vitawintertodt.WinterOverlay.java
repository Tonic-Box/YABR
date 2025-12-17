package com.tonic.vitawintertodt;

import com.tonic.ui.VitaOverlay;
import com.tonic.vitawintertodt.data.State;
import java.awt.Color;
import net.runelite.client.ui.overlay.OverlayPosition;

public class WinterOverlay extends VitaOverlay {

    public WinterOverlay() {
        super();
        setPosition(OverlayPosition.TOP_CENTER);
        setHeight(28);
        setWidth(150);
        return;
    }

    public void update(State arg0) {
        clear();
        if (arg0 == null) {
            newLine("Inactive", 14, Color.RED);
            return;
        }
        switch (arg0) {
            case 1:
                newLine("Starting...", 14, Color.YELLOW);
                return;
            case 2:
                newLine("Preparation Phase", 14, Color.CYAN);
                return;
            case 3:
                newLine("In Game", 14, Color.GREEN);
                return;
            default:
                return;
        }
    }

}
