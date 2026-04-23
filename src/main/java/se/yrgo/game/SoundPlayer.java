package se.yrgo.game;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;

public class SoundPlayer {

    private Clip clip;

    // 🎵 Spelar musik i loop
    public void playLoop(String path) {
        stop(); // stoppa eventuell tidigare musik

        try {
            System.out.println("Loading sound: " + path);

            InputStream audioSrc = getClass().getResourceAsStream(path);

            if (audioSrc == null) {
                System.out.println("❌ Could not find sound file: " + path);
                return;
            } else {
                System.out.println("✅ Found sound file!");
            }

            BufferedInputStream bufferedIn = new BufferedInputStream(audioSrc);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);

            clip = AudioSystem.getClip();
            clip.open(audioStream);

            // loopar oändligt
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();

        } catch (Exception e) {
            System.out.println("❌ Error playing sound:");
            e.printStackTrace();
        }
    }

    // 🛑 Stoppar musik
    public void stop() {
        if (clip != null) {
            clip.stop();
            clip.close();
        }
    }

    // 🔊 (BONUS) Spela en engångs-effekt (t.ex hopp)
    public void playOnce(String path) {
        try {
            InputStream audioSrc = getClass().getResourceAsStream(path);

            if (audioSrc == null) {
                System.out.println("❌ Could not find sound effect: " + path);
                return;
            }

            BufferedInputStream bufferedIn = new BufferedInputStream(audioSrc);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);

            Clip effectClip = AudioSystem.getClip();
            effectClip.open(audioStream);
            effectClip.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}