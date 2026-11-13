package com.lostf1sh.pixelplayeross.desktop.playback;

import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths;
import java.awt.Window;
import java.util.function.IntConsumer;

/**
 * Common cross-platform abstraction for native desktop media transport controls
 * (Windows SMTC, macOS MPNowPlayingInfoCenter / MPRemoteCommandCenter, Linux MPRIS2).
 */
public interface PlatformMediaSession extends AutoCloseable {
    int BUTTON_PLAY = 0;
    int BUTTON_PAUSE = 1;
    int BUTTON_STOP = 2;
    int BUTTON_NEXT = 6;
    int BUTTON_PREVIOUS = 7;

    void update(String title, String artist, String album, String coverPath, boolean playing, boolean previous, boolean next);
    String getFailureMessage();
    @Override
    void close();

    static PlatformMediaSession create(Window window, IntConsumer button) {
        if (PlatformPaths.isWindows()) {
            try {
                if (window != null) {
                    return new WindowsMediaSession(window, button);
                }
            } catch (Throwable failure) {
                System.err.println("Windows media session initialization error: " + failure);
            }
        }
        if (PlatformPaths.isMac()) {
            try {
                return new MacMediaSession(button);
            } catch (Throwable failure) {
                System.err.println("macOS media session initialization error: " + failure);
            }
        }
        if (PlatformPaths.isLinux()) {
            try {
                return new LinuxMediaSession(button);
            } catch (Throwable failure) {
                System.err.println("Linux MPRIS media session initialization error: " + failure);
            }
        }
        return null;
    }
}
