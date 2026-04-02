package se.yrgo.game;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

/**
 * A simple panel with a space invaders "game" in it. This is just to
 * demonstrate the bare minimum of stuff than can be done drawing on a panel.
 * This is by no means good code, but rather a short demonstration on
 * some things one can do to make a very simple Swing based game.
 * 
 * If you really want to make a good game there are several toolkits for
 * game making out there which are much more suitable for this.
 * 
 */
public class GameSurface extends JPanel implements KeyListener, MouseListener {
    private static final long serialVersionUID = 6260582674762246325L;
    private static Logger logger = Logger.getLogger(GameSurface.class.getName());
    private static final double ALIEN_PIXELS_PER_MS = 0.12;
    private static final int SCORE_PER_SECOND = 1000;
    private double velocityY = 0;
    private int lastTime = 0;
    private static final double GRAVITY = 0.001; // hur snabbt skeppet sjunker.
    private static final double JUMP_FORCE = -0.4; // styrkan i hopp, negativt betyder högre

    // make some transient to get past boring serialization demands...
    private transient FrameUpdater updater;
    private boolean gameOver;
    private transient List<Alien> aliens;
    private Rectangle spaceShip;
    private transient BufferedImage shipImageSprite;
    private transient BufferedImage backgroundImage;
    private int score;
    private static final int GAP_SIZE = 200; // storleken på hålet mellan pelarna
    private int timeSinceLastPillar = 0;

    public GameSurface(final int width) {
        try (InputStream spriteStream = GameSurface.class.getResourceAsStream("/pony.png")) {
            if (spriteStream == null) {
                logger.log(Level.WARNING, "Unable to load image resource: /pony.png");
            } else {
                this.shipImageSprite = ImageIO.read(spriteStream);
            }
            // this.shipImageSpriteCount = 0;
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to load image resource: /pony.png", ex);
        }
        try (InputStream bgStream = GameSurface.class.getResourceAsStream("/background.jpg")) {
            if (bgStream == null) {
                logger.log(Level.WARNING, "Unable to load image resource: /background.jpg");
            } else {
                this.backgroundImage = ImageIO.read(bgStream);
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to load image resource: /background.jpg", ex);
        }
        this.gameOver = false;
        this.aliens = new ArrayList<>();
        this.spaceShip = new Rectangle(160, width / 2 - 15, 80, 80);
        this.score = 0;

        this.updater = new FrameUpdater(this, 60);
        this.updater.setDaemon(true); // it should not keep the app running
        this.updater.start();
        // registrerar musklick på spelet
        this.addMouseListener(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;
        drawSurface(g2d);
    }

    /**
     * Call this method when the graphics needs to be repainted on the graphics
     * surface.
     * 
     * @param g the graphics to paint on
     */
    private void drawSurface(Graphics2D g) {
        final Dimension d = this.getSize();
        // sparar ursprunglig transformation så lutningen inte påverkar nästa frame
        java.awt.geom.AffineTransform original = g.getTransform();

        if (gameOver) {
            g.setColor(Color.pink);
            g.fillRect(0, 0, d.width, d.height);
            g.setColor(Color.black);
            g.setFont(new Font("Arial", Font.BOLD, 48));
            g.drawString("Game over!", 20, d.width / 2 - 24);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.drawString("You have fallen asleep... Press Space OR Left Click to wake up", 20,
                    d.height / 2 + 20);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.drawString("Silly little pony", 20, d.height / 2 + 50);
            drawScore(g, d, true);
            g.setTransform(original);
            return;
        }

        // fill the background
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, d.width, d.height, null);
        } else {
            // fallback om bilden inte laddas
            g.setColor(Color.DARK_GRAY);
            g.fillRect(0, 0, d.width, d.height);
        }

        // varje alien är nu två pelare, en uppifrån och en nedifrån med ett mellanrum
        // emellan
        // Det blir en pelare för varje alien...
        // lila färg som matchar pony-temat
        for (Alien alien : aliens) {
            g.setColor(new Color(147, 112, 219));
            g.fillRect(alien.topPillar.x, alien.topPillar.y,
                    alien.topPillar.width, alien.topPillar.height);
            g.fillRect(alien.bottomPillar.x, alien.bottomPillar.y,
                    alien.bottomPillar.width, alien.bottomPillar.height);
        }
        // draw the space ship, as a cool image if it did load properly

        // clampedVelocity begränsar hastigheten så att ponyn inte roterar för mycket
        // angle är ansvarig för vinkeln på ponyn högre multiplikator ger en mer
        // överdriven rörelse
        if (shipImageSprite != null) {
            double clampedVelocity = Math.max(-5, Math.min(5, velocityY));
            double angle = Math.toRadians(clampedVelocity * 40);
            java.awt.geom.AffineTransform old = g.getTransform();
            try {
                g.rotate(angle, spaceShip.x + spaceShip.width / 2,
                        spaceShip.y + spaceShip.height / 2);
                g.drawImage(shipImageSprite, spaceShip.x, spaceShip.y,
                        spaceShip.width, spaceShip.height, null);
            } finally {
                // återställer alltid transformationen oavsett vad som händer
                g.setTransform(old);
            }
        } else {
            g.setColor(Color.black);
            g.fillRect(spaceShip.x, spaceShip.y, spaceShip.width, spaceShip.height);
        }

        drawScore(g, d, false);
    }

    private void drawScore(Graphics2D g, Dimension d, boolean gameOverBackground) {
        final String scoreText = String.format("%07d", score);
        final Font scoreFont = new Font("Monospaced", Font.BOLD, 15);
        final int margin = 14;

        g.setFont(scoreFont);
        FontMetrics metrics = g.getFontMetrics(scoreFont);
        int textX = d.width - metrics.stringWidth(scoreText) - margin;
        int textY = margin + metrics.getAscent();

        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.setColor(new Color(255, 230, 0));
        g.drawString("⭐", textX - 30, textY + 2);

        // rita poängtexten
        g.setFont(scoreFont);
        g.setColor(new Color(255, 230, 0));
        g.drawString(scoreText, textX, textY);
    }

    public void update(int time) {
        if (gameOver) {
            updater.interrupt();
            return;
        }

        final Dimension d = getSize();
        if (d.height <= 0 || d.width <= 0) {
            // if the panel has not been placed properly in the frame yet
            // just return without updating any state
            return;
        }

        if (lastTime == 0) {
            lastTime = time;
        }
        // delta = millisekunder sedan förra framen, t.ex. 16ms på 60fps
        // time är total tid sedan spelet startade, så time - lastTime = tid sedan förra
        // framen
        // vi sparar sedan time i lastTime så vi kan räkna ut delta nästa frame
        int delta = time - lastTime;
        lastTime = time;

        // multiplicerar med delta (millisekunder sedan förra framen) så att
        // gravitationen alltid är lika stark oavsett datorns hastighet.
        // snabb dator = litet delta, långsam dator = stort delta, resultatet blir samma
        velocityY += GRAVITY * delta;
        spaceShip.y += (int) (velocityY * delta);

        // håller skeppet inom skärmens gränser så det inte försvinner utanför
        int minY = 10;
        int maxY = d.height - spaceShip.height - 10;
        spaceShip.y = Math.max(minY, Math.min(maxY, spaceShip.y));

        // fill up with some aliens if we have none (at start of game)
        if (aliens.isEmpty()) {
            for (int i = 0; i < 3; ++i) {
                addAlien(time + 3000 - (i * 3000), d.height, false);
            }
            timeSinceLastPillar = time + 3000; // sparar när senaste pelaren skedde
        }

        // time-based score gives predictable progression independent of frame rate.
        score = (int) ((time / 1000.0) * SCORE_PER_SECOND);

        final List<Alien> toRemove = new ArrayList<>();

        for (Alien alien : aliens) {
            int timeElapsed = time - alien.created;
            int newX = (int) (d.width - (timeElapsed * ALIEN_PIXELS_PER_MS));

            // båda pelarna rör sig tillsammans
            alien.topPillar.x = newX;
            alien.bottomPillar.x = newX;

            if (alien.topPillar.x + alien.topPillar.width < 0) {
                toRemove.add(alien);
            }

            // båda delarna av pelarn har kollision
            if (alien.topPillar.intersects(spaceShip) ||
                    alien.bottomPillar.intersects(spaceShip)) {
                gameOver = true;
            }
        }

        // remove all aliens that are out of frame
        // we can't remove things from the aliens list while we're
        // iterating over it.
        aliens.removeAll(toRemove);

        // add new aliens for every one that was removed

        // skapar en ny pelare var 2000ms automatiskt
        // time - timeSinceLastPillar räknar ut hur lång tid sedan senaste pelaren
        if (time - timeSinceLastPillar >= 3000) {
            timeSinceLastPillar = time; // sparar när senaste pelaren skapades
            addAlien(time, d.height, false);
        }
    }

    private void addAlien(final int time, final int height, boolean randomX) {
        int newTime = time;
        if (randomX) {
            // make sure they start randomly somewhere on the screen
            // by adjusting the create time, making it seem like they
            // have traveled on the screen for some time already
            final int MIN_PIXELS_FROM_LEFT = 180;
            final int MS_TO_TRAVEL_MIN_PIXELS = (int) (MIN_PIXELS_FROM_LEFT / ALIEN_PIXELS_PER_MS);
            newTime = time - ThreadLocalRandom.current().nextInt(MS_TO_TRAVEL_MIN_PIXELS);
        }

        final int FAR_OFFSCREEN = 10000;
        // detta slumpar vart mellanrummet ska vara på skärmen
        // gör så att hålet inte kan vara för nära toppen eller botten så man har en
        // chans att komma igenom
        int gapY = ThreadLocalRandom.current().nextInt(80, height - GAP_SIZE - 80);
        aliens.add(new Alien(newTime, FAR_OFFSCREEN, gapY, GAP_SIZE, height));
    }

    private void resetGame() {
        // stoppar den gamla tråden
        if (updater != null) {
            updater.interrupt();
            try {
                updater.join(); // den här biten väntar tills tråden faktiskt stoppat
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Dimension d = this.getSize();
        spaceShip.setLocation(160, d.height / 2);
        aliens.clear();
        velocityY = 0;
        lastTime = 0;
        score = 0;
        timeSinceLastPillar = 0;
        gameOver = false;
        updater = new FrameUpdater(this, 60);
        updater.setDaemon(true);
        updater.start();
    }

    public void keyPressed(KeyEvent e) {
        final int kc = e.getKeyCode();

        if (gameOver) {
            if (kc == KeyEvent.VK_SPACE) {
                resetGame();
            }
            return;
        }

        if (kc == KeyEvent.VK_SPACE) {
            velocityY = JUMP_FORCE;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // do nothing
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // do nothing
    }

    // mouselistener fungerar precis som keylistener för musknapp istället för key
    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (gameOver) {
                resetGame();
                return;
            }
            velocityY = JUMP_FORCE;
        }
    }

    // dessa metoder måste finnas med precis som ovanför med alla key metoder
    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
}
