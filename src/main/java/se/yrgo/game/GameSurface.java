package se.yrgo.game;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

public class GameSurface extends JPanel implements KeyListener, MouseListener {
    private static final long serialVersionUID = 6260582674762246325L;
    private static Logger logger = Logger.getLogger(GameSurface.class.getName());
    private static final double PILLAR_PIXELS_PER_MS = 0.12; // HÄR
    private static final int SCORE_PER_SECOND = 1000;
    private double velocityY = 0;
    private int lastTime = 0;
    private long lastGameOverTime = 0; // Sparar tidpunkten när spelaren dör
    private long menuOpenTime = 0; // Sparar tidpunkten när menyn visas eller nollställs
    private long playStartTime = 0; // // Håller koll på när spelaren lämnar menyn för att skapa en
                                    // startfördröjningp
    private static final double GRAVITY = 0.001; // hur snabbt skeppet sjunker.
    private static final double JUMP_FORCE = -0.4; // styrkan i hopp, negativt betyder högre
    private boolean inMenu = true; // spelet börjar i menyn
    private int selectedDifficulty = 1; // 1 = easy, 2 = normal, 3 = hard
    // make some transient to get past boring serialization demands...
    private transient FrameUpdater updater;
    private boolean gameOver;
    private boolean gameStarted = false; // Håller koll på om spelet börjat än och det är false så då står den still
    private transient List<Pillar> pillars;
    private Rectangle pony;
    private transient BufferedImage ponyImage;
    private transient BufferedImage backgroundImage;
    private transient BufferedImage pillarImage;
    private int score;
    private static final int GAP_SIZE = 200; // storleken på hålet mellan pelarna
    private int timeSinceLastPillar = 0;
    private int endMenuWidth = 400;
    private int endMenuHeight = 400;
    private static double speedMultiplier = 1; // HÄR
    private Font gameFont;
    private SoundPlayer music = new SoundPlayer();

    public GameSurface(final int width) {
        try (InputStream fontStream = getClass().getResourceAsStream("/PressStart2P-Regular.ttf")) {
            if (fontStream != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                gameFont = font.deriveFont(24f); // storlek
            } else {
                System.out.println("Font kunde inte laddas!");
                gameFont = new Font("Arial", Font.BOLD, 24);
            }
        } catch (Exception e) {
            e.printStackTrace();
            gameFont = new Font("Arial", Font.BOLD, 24);
        }

        try (InputStream spriteStream = GameSurface.class.getResourceAsStream("/pony.png")) {
            if (spriteStream == null) {
                logger.log(Level.WARNING, "Unable to load image resource: /pony.png");
            } else {
                this.ponyImage = ImageIO.read(spriteStream);
            }
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

        try (InputStream pillarStream = GameSurface.class.getResourceAsStream("/cloud2.png")) {
            if (pillarStream == null) {
                logger.log(Level.WARNING, "Unable to load image resource: /cloud2.png");
            } else {
                this.pillarImage = ImageIO.read(pillarStream);
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to load image resource: /cloud2.png", ex);
        }
        this.gameOver = false;
        this.pillars = new ArrayList<>();
        this.pony = new Rectangle(160, width / 2 - 15, 80, 80);
        this.score = 0;
        this.menuOpenTime = System.currentTimeMillis();
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

        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, d.width, d.height, null);
        } else {
            // fallback om bilden inte laddas
            g.setColor(Color.DARK_GRAY);
            g.fillRect(0, 0, d.width, d.height);
        }

        for (Pillar pillar : pillars) {

            // OBS moln som pelare
            for (int y = pillar.topPillar.y; y < pillar.topPillar.y + pillar.topPillar.height; y += 75) {
                g.drawImage(pillarImage,
                        pillar.topPillar.x,
                        y,
                        90,
                        70,
                        null);
            }

            for (int y = pillar.bottomPillar.y; y < pillar.bottomPillar.y + pillar.bottomPillar.height; y += 75) {
                g.drawImage(pillarImage,
                        pillar.bottomPillar.x,
                        y,
                        90,
                        70,
                        null);
            }
        }

        if (inMenu) {

            int xStartMenu = (d.width - endMenuWidth) / 2;
            int yStartMenu = (d.height - endMenuHeight) / 2;

            g.setColor(new Color(255, 192, 203, 150));
            g.fillRect(xStartMenu, yStartMenu, endMenuWidth, endMenuHeight);
            // g.fillRoundRect(xStartMenu, yStartMenu,500 , 500, xStartMenu, yStartMenu);
            g.setColor(Color.black);

            g.setFont(gameFont.deriveFont(Font.BOLD, 28f));
            g.drawString("Jumpy Birb!", xStartMenu + 50, yStartMenu + 80);

            // markerar valt alternativ med en annan färg
            g.setFont(gameFont.deriveFont(Font.BOLD, 18f));
            g.setColor(selectedDifficulty == 1 ? Color.magenta : Color.black);
            g.drawString("Easy", xStartMenu + 150, yStartMenu + 170);
            g.setColor(selectedDifficulty == 2 ? Color.magenta : Color.black);
            g.drawString("Normal", xStartMenu + 150, yStartMenu + 220);
            g.setColor(selectedDifficulty == 3 ? Color.magenta : Color.black);
            g.drawString("Hard", xStartMenu + 150, yStartMenu + 270);

            g.setFont(gameFont.deriveFont(Font.BOLD, 14f));
            g.setColor(Color.black);
            FontMetrics fm = g.getFontMetrics();
            String instructions = "↑↓ for difficulty.";
            int instructionsX = (d.width - fm.stringWidth(instructions)) / 2;
            g.drawString(instructions, instructionsX, d.height / 2 + 150);
            String instructions2 = "Space to start";
            int instructionsX2 = (d.width - fm.stringWidth(instructions2)) / 2;
            g.drawString(instructions2, instructionsX2, d.height / 2 + 170);
            g.setTransform(original);
            return;
        }

        /**
         * fill the background
         */

        if (gameOver) {
            g.setColor(new Color(255, 192, 203, 150));

            /**
             * Screen size minus the square size split in 2 in order to center the square
             */
            int xEndMeny = (d.width - endMenuWidth) / 2;
            int yEndMenu = (d.height - endMenuHeight) / 2;

            // *
            // Draw the square based on x-position, y-position and size */
            g.fillRect(xEndMeny, yEndMenu, endMenuWidth, endMenuHeight);

            g.setColor(Color.black);

            g.setFont(gameFont.deriveFont(Font.BOLD, 28f));
            // * Increase value for x-position to move text to the right, increase
            // y-position to move down */
            g.drawString("Game over!", xEndMeny + 50, yEndMenu + 80);

            g.setFont(gameFont.deriveFont(Font.BOLD, 14f));
            g.drawString("Space or left click",
                    xEndMeny + 50, yEndMenu + 115);
            g.drawString("to wake up",
                    xEndMeny + 95, yEndMenu + 145);
            g.setFont(gameFont.deriveFont(Font.BOLD, 15f));
            g.drawString("Silly little pony", xEndMeny + 55, yEndMenu + 215);

            String difficulty;
            if (selectedDifficulty == 1) {
                difficulty = "Easy";
            } else if (selectedDifficulty == 2) {
                difficulty = "Medium";
            } else {
                difficulty = "Hard";
            }

            g.setFont(gameFont.deriveFont(Font.BOLD, 18f));
            g.drawString("Difficulty: " + difficulty,
                    xEndMeny + 40,
                    yEndMenu + 290);

            g.setFont(gameFont.deriveFont(Font.BOLD, 14f));
            g.drawString("This round's score: " + score, xEndMeny + 20, yEndMenu + endMenuHeight - 65);
            int highScore = getHighScore();
            g.drawString("All time highscore: " + highScore,
                    xEndMeny + 20,
                    yEndMenu + endMenuHeight - 40);

            drawScore(g, d, true);

            g.setTransform(original);

            return;
        }

        // rita ponyn om bilden laddades korrekt

        // clampedVelocity begränsar hastigheten så att ponyn inte roterar för mycket
        // angle är ansvarig för vinkeln på ponyn högre multiplikator ger en mer
        // överdriven rörelse
        if (ponyImage != null) {
            double clampedVelocity = Math.max(-5, Math.min(5, velocityY));
            double angle = Math.toRadians(clampedVelocity * 40);
            java.awt.geom.AffineTransform old = g.getTransform();
            try {
                g.rotate(angle, pony.x + pony.width / 2,
                        pony.y + pony.height / 2);
                g.drawImage(ponyImage, pony.x, pony.y,
                        pony.width, pony.height, null);
            } finally {
                // återställer alltid transformationen oavsett vad som händer
                g.setTransform(old);
            }
        } else {
            g.setColor(Color.black);
            g.fillRect(pony.x, pony.y, pony.width, pony.height);
        }

        drawScore(g, d, false);
    }

    //
    private void drawScore(Graphics2D g, Dimension d, boolean gameOverBackground) {
        final String scoreText = String.format("%07d", score);
        final Font scoreFont = new Font("Monospaced", Font.BOLD, 30);
        final int margin = 15;

        FontMetrics metrics = g.getFontMetrics(scoreFont);
        int textX = d.width - metrics.stringWidth(scoreText) - margin;
        int textY = margin + metrics.getAscent();

        g.setFont(scoreFont);
        g.setColor(new Color(255, 230, 0));

        // rita poängtexten
        g.drawString("⭐", textX - 30, textY + 2);
        g.drawString(scoreText, textX, textY);
    }

    public void update(int time) {
        if (gameOver) {
            if (updater != null) {
                updater.interrupt();
            }
            return;
        }

        if (!gameStarted) {
            lastTime = time; // Vi fryser spelet tills spelaren klickat space eller musklick efter 1seks
                             // spärren
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
        pony.y += (int) (velocityY * delta);

        // håller ponyn inom skärmens gränser så det inte försvinner utanför
        int minY = 10;
        int maxY = d.height - pony.height - 10;
        pony.y = Math.max(minY, Math.min(maxY, pony.y));

        // fill up with some pillars if we have none (at start of game)
        if (pillars.isEmpty()) {
            for (int i = 0; i < 3; ++i) {
                // ändrade från 3000 till -2000 så pelarna började längre till vänster (då jag
                // ändrade resolution till 1920x1080)
                addPillar(time + (int) (-1000 / speedMultiplier) - (int) (i * (3000 / speedMultiplier)), d.height,
                        false); // HÄR
            }
            timeSinceLastPillar = time; // sparar när senaste pelaren skedde
        }

        // time-based score gives predictable progression independent of frame rate.
        score = (int) ((time / 1000.0) * SCORE_PER_SECOND);

        final List<Pillar> toRemove = new ArrayList<>();

        for (Pillar pillar : pillars) {
            int timeElapsed = time - pillar.created;
            int newX = (int) (d.width - (timeElapsed * PILLAR_PIXELS_PER_MS * speedMultiplier)); // HÄR

            // båda pelarna rör sig tillsammans
            pillar.topPillar.x = newX;
            pillar.bottomPillar.x = newX;

            if (pillar.topPillar.x + pillar.topPillar.width < 0) {
                toRemove.add(pillar);
            }

            // Hitbox
            Rectangle hitbox = new Rectangle(
                    pony.x + 15,
                    pony.y + 10,
                    pony.width - 30,
                    pony.height - 30);

            // båda delarna av pelarn har kollision
            if (pillar.topPillar.intersects(hitbox) ||
                    pillar.bottomPillar.intersects(hitbox)) {
                gameOver = true;
                music.playOnce("/Death.wav");
                lastGameOverTime = System.currentTimeMillis(); // Fryser till en halvsek i gamover
                updateHighScore();
            }
        }

        // remove all pillars that are out of frame
        // we can't remove things from the pillars list while we're
        // iterating over it.
        pillars.removeAll(toRemove);

        // add new pillars for every one that was removed

        // skapar en ny pelare var 2000ms automatiskt
        // time - timeSinceLastPillar räknar ut hur lång tid sedan senaste pelaren
        if (time - timeSinceLastPillar >= 3000 / speedMultiplier) { // HÄR
            timeSinceLastPillar = time; // sparar när senaste pelaren skapades
            addPillar(time, d.height, false);
        }
    }

    public void setDifficulty(int level) { // HÄR
        switch (level) {
            case 1 -> speedMultiplier = 1.0;
            case 2 -> speedMultiplier = 1.5;
            case 3 -> speedMultiplier = 2;
        }
    }

    private void addPillar(final int time, final int height, boolean randomX) {
        int newTime = time;
        if (randomX) {
            // make sure they start randomly somewhere on the screen
            // by adjusting the create time, making it seem like they
            // have traveled on the screen for some time already
            final int MIN_PIXELS_FROM_LEFT = 180;
            final int MS_TO_TRAVEL_MIN_PIXELS = (int) (MIN_PIXELS_FROM_LEFT / PILLAR_PIXELS_PER_MS * speedMultiplier); // HÄR
            newTime = time - ThreadLocalRandom.current().nextInt(MS_TO_TRAVEL_MIN_PIXELS);
        }

        final int FAR_OFFSCREEN = 10000;
        // detta slumpar vart mellanrummet ska vara på skärmen
        // gör så att hålet inte kan vara för nära toppen eller botten så man har en
        // chans att komma igenom
        int gapY = ThreadLocalRandom.current().nextInt(80, height - GAP_SIZE - 80);
        pillars.add(new Pillar(newTime, FAR_OFFSCREEN, gapY, GAP_SIZE, height));
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

        // Sets pony position
        pony.setLocation(170, d.height / 2);
        pillars.clear();
        velocityY = 0;
        lastTime = 0;
        score = 0;
        timeSinceLastPillar = 0;
        gameOver = false;
        gameStarted = false; // Här börjar spelet på nytt och står still igen
        inMenu = true;
        menuOpenTime = System.currentTimeMillis();
        playStartTime = 0; // Återställer starttimern inför nästa spelomgång
        updater = new FrameUpdater(this, 60);
        updater.setDaemon(true);
        updater.start();
    }

    // hämta highscore från highscore.txt

    public int getHighScore() {
        Path path;
        if (selectedDifficulty == 1) {
            path = Path.of("highscore.txt");
        } else if (selectedDifficulty == 2) {
            path = Path.of("highscoreMed.txt");
        } else {
            path = Path.of("highscoreDiff.txt");
        }
        // om filen är tom eller inte finns returnera 0
        try {
            if (!Files.exists(path)) {
                return 0;
            }

            String value = Files.readString(path).trim();

            if (value.isEmpty()) {
                return 0;
            }

            return Integer.parseInt(value);

        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    public void keyPressed(KeyEvent e) {
        final int kc = e.getKeyCode();

        if (inMenu) {
            if (System.currentTimeMillis() - menuOpenTime < 1000)
                return; // förhindrar så man ej kan spamklicka sig genom menyn med 1sek marginal

            if (kc == KeyEvent.VK_UP && selectedDifficulty > 1) {
                selectedDifficulty--;
            } else if (kc == KeyEvent.VK_DOWN && selectedDifficulty < 3) {
                selectedDifficulty++;
            } else if (kc == KeyEvent.VK_SPACE) {
                setDifficulty(selectedDifficulty);
                inMenu = false;
                playStartTime = System.currentTimeMillis(); // Klockan startar när vi stänger menyn
                music.playLoop("/Sugarhoof Bounce.wav");
                music.playOnce("/Horse.wav");
            }
            return;
        }

        if (gameOver) {
            // kollar så en 1sek har gått innan man får börja om
            if (kc == KeyEvent.VK_SPACE && System.currentTimeMillis() - lastGameOverTime > 1000) {

                resetGame();
            }
            return;
        }

        if (kc == KeyEvent.VK_SPACE) {
            // / Ignorerar trycket om 1 sekund inte har gått sedan menyn stängdes
            if (System.currentTimeMillis() - playStartTime < 1000) {
                return;
            }

            if (!gameStarted) {
                gameStarted = true;
            }
            velocityY = JUMP_FORCE;

            music.playOnce("/Jump.wav");
        }
    }

    // updatera highscore
    // om denna rundans score är högre än highscore från filen, ersätt och uppdatera
    // med score
    public void updateHighScore() {
        Path path = Path.of("highscore.txt");
        Path path2 = Path.of("highscoreMed.txt");
        Path path3 = Path.of("highscoreDiff.txt");

        try {
            int highscoreEasy = getHighScore();
            int highscoreMedium = getHighScore();
            int highscoreDifficult = getHighScore();

            if (score > highscoreEasy && selectedDifficulty == 1) {
                Files.writeString(
                        path,
                        String.valueOf(score),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } else if (score > highscoreMedium && selectedDifficulty == 2) {
                Files.writeString(
                        path2,
                        String.valueOf(score),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } else if (score > highscoreDifficult && selectedDifficulty == 3) {
                Files.writeString(
                        path3,
                        String.valueOf(score),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("Could not update highscore: " + e.getMessage());
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
            if (inMenu) {
                if (System.currentTimeMillis() - menuOpenTime < 1000)
                    return; // gör så musen är aktiverad och dröjer en halvsek innan man får börja
                setDifficulty(selectedDifficulty);
                inMenu = false;
                playStartTime = System.currentTimeMillis(); // Klockan startar när vi stänger menyn
                return;
            }

            if (gameOver) {
                // Gör så musen dröjer en halvsek innan man får starta på nytt
                if (System.currentTimeMillis() - lastGameOverTime > 1000) {
                    resetGame();
                }
                return;
            }

            // Ignorerar klicket om det inte gått 1 sek än
            if (System.currentTimeMillis() - playStartTime < 1000) {
                return;
            }

            if (!gameStarted) {
                gameStarted = true;
            }
            music.playOnce("/Jump.wav");
            velocityY = JUMP_FORCE;
        }
    }

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