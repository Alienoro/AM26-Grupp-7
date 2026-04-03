package se.yrgo.game;

import java.awt.Rectangle;


// this class can be much improved, better encapsulation
// draw itself, update itself etc. etc.
public class Pillar {
    public final int created;
    public final Rectangle topPillar;
    public final Rectangle bottomPillar;

    public Pillar(int created, int x, int gapY, int gapSize, int screenHeight) {
        this.created = created;
        this.topPillar = new Rectangle(x, 0, 60, gapY);
        this.bottomPillar = new Rectangle(x, gapY + gapSize, 60, screenHeight - gapY - gapSize);
    }
}
