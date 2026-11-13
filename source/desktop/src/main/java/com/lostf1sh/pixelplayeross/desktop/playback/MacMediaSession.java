package com.lostf1sh.pixelplayeross.desktop.playback;

import com.sun.jna.Callback;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/**
 * macOS native media session using MPNowPlayingInfoCenter and MPRemoteCommandCenter via Objective-C runtime.
 */
public final class MacMediaSession implements PlatformMediaSession {
    public interface ObjcCallback extends Callback {
        long invoke(Pointer self, Pointer cmd, Pointer event);
    }

    private static final NativeLibrary OBJC = NativeLibrary.getInstance("objc");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "pixel-mac-media");
        thread.setDaemon(true);
        return thread;
    });

    private final IntConsumer button;
    private volatile boolean closed;
    private volatile String failureMessage;

    private Pointer targetInstance;
    private Pointer nowPlayingCenter;
    private Pointer remoteCenter;
    private Pointer playCmd;
    private Pointer pauseCmd;
    private Pointer toggleCmd;
    private Pointer nextCmd;
    private Pointer prevCmd;

    // Retain callback references so JNA does not GC them
    private final ObjcCallback playCallback;
    private final ObjcCallback pauseCallback;
    private final ObjcCallback toggleCallback;
    private final ObjcCallback nextCallback;
    private final ObjcCallback prevCallback;

    public MacMediaSession(IntConsumer button) {
        this.button = button;
        this.playCallback = (self, cmd, event) -> { button.accept(BUTTON_PLAY); return 0L; };
        this.pauseCallback = (self, cmd, event) -> { button.accept(BUTTON_PAUSE); return 0L; };
        this.toggleCallback = (self, cmd, event) -> { button.accept(BUTTON_PLAY); return 0L; };
        this.nextCallback = (self, cmd, event) -> { button.accept(BUTTON_NEXT); return 0L; };
        this.prevCallback = (self, cmd, event) -> { button.accept(BUTTON_PREVIOUS); return 0L; };

        try {
            NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer");
            NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit");
            initialize();
        } catch (Throwable failure) {
            failureMessage = failure.getMessage();
        }
    }

    private static Pointer cls(String name) {
        return OBJC.getFunction("objc_getClass").invokePointer(new Object[]{name});
    }

    private static Pointer sel(String name) {
        return OBJC.getFunction("sel_registerName").invokePointer(new Object[]{name});
    }

    private static Pointer msgSend(Pointer receiver, Pointer selector, Object... args) {
        Object[] all = new Object[args.length + 2];
        all[0] = receiver;
        all[1] = selector;
        System.arraycopy(args, 0, all, 2, args.length);
        return OBJC.getFunction("objc_msgSend").invokePointer(all);
    }

    private static void msgSendVoid(Pointer receiver, Pointer selector, Object... args) {
        Object[] all = new Object[args.length + 2];
        all[0] = receiver;
        all[1] = selector;
        System.arraycopy(args, 0, all, 2, args.length);
        OBJC.getFunction("objc_msgSend").invokeVoid(all);
    }

    private static Pointer nsString(String str) {
        if (str == null) return Pointer.NULL;
        return msgSend(cls("NSString"), sel("stringWithUTF8String:"), str);
    }

    private synchronized void registerTargetClass() {
        String className = "UmimukoMacMediaTarget";
        Pointer existing = cls(className);
        if (existing != null) {
            targetInstance = msgSend(existing, sel("alloc"));
            targetInstance = msgSend(targetInstance, sel("init"));
            return;
        }

        Pointer nsObjectClass = cls("NSObject");
        Pointer newClass = OBJC.getFunction("objc_allocateClassPair").invokePointer(new Object[]{nsObjectClass, className, 0L});
        if (newClass != null) {
            OBJC.getFunction("class_addMethod").invokeInt(new Object[]{newClass, sel("handlePlay:"), playCallback, "q@:@"});
            OBJC.getFunction("class_addMethod").invokeInt(new Object[]{newClass, sel("handlePause:"), pauseCallback, "q@:@"});
            OBJC.getFunction("class_addMethod").invokeInt(new Object[]{newClass, sel("handleToggle:"), toggleCallback, "q@:@"});
            OBJC.getFunction("class_addMethod").invokeInt(new Object[]{newClass, sel("handleNext:"), nextCallback, "q@:@"});
            OBJC.getFunction("class_addMethod").invokeInt(new Object[]{newClass, sel("handlePrev:"), prevCallback, "q@:@"});
            OBJC.getFunction("objc_registerClassPair").invokeVoid(new Object[]{newClass});

            targetInstance = msgSend(newClass, sel("alloc"));
            targetInstance = msgSend(targetInstance, sel("init"));
        }
    }

    private void initialize() {
        registerTargetClass();
        nowPlayingCenter = msgSend(cls("MPNowPlayingInfoCenter"), sel("defaultCenter"));
        remoteCenter = msgSend(cls("MPRemoteCommandCenter"), sel("sharedCommandCenter"));

        if (remoteCenter != null && targetInstance != null) {
            playCmd = msgSend(remoteCenter, sel("playCommand"));
            pauseCmd = msgSend(remoteCenter, sel("pauseCommand"));
            toggleCmd = msgSend(remoteCenter, sel("togglePlayPauseCommand"));
            nextCmd = msgSend(remoteCenter, sel("nextTrackCommand"));
            prevCmd = msgSend(remoteCenter, sel("previousTrackCommand"));

            msgSendVoid(playCmd, sel("addTarget:action:"), targetInstance, sel("handlePlay:"));
            msgSendVoid(playCmd, sel("setEnabled:"), 1L);

            msgSendVoid(pauseCmd, sel("addTarget:action:"), targetInstance, sel("handlePause:"));
            msgSendVoid(pauseCmd, sel("setEnabled:"), 1L);

            msgSendVoid(toggleCmd, sel("addTarget:action:"), targetInstance, sel("handleToggle:"));
            msgSendVoid(toggleCmd, sel("setEnabled:"), 1L);

            msgSendVoid(nextCmd, sel("addTarget:action:"), targetInstance, sel("handleNext:"));
            msgSendVoid(prevCmd, sel("addTarget:action:"), targetInstance, sel("handlePrev:"));
        }
    }

    @Override
    public void update(String title, String artist, String album, String coverPath, boolean playing, boolean previous, boolean next) {
        if (closed || nowPlayingCenter == null) return;
        worker.execute(() -> {
            try {
                if (nextCmd != null) msgSendVoid(nextCmd, sel("setEnabled:"), next ? 1L : 0L);
                if (prevCmd != null) msgSendVoid(prevCmd, sel("setEnabled:"), previous ? 1L : 0L);

                Pointer dict = msgSend(cls("NSMutableDictionary"), sel("dictionary"));
                if (dict != null) {
                    if (title != null && !title.isBlank()) {
                        msgSendVoid(dict, sel("setObject:forKey:"), nsString(title), nsString("title"));
                    }
                    if (artist != null && !artist.isBlank()) {
                        msgSendVoid(dict, sel("setObject:forKey:"), nsString(artist), nsString("artist"));
                    }
                    if (album != null && !album.isBlank()) {
                        msgSendVoid(dict, sel("setObject:forKey:"), nsString(album), nsString("albumTitle"));
                    }
                    if (coverPath != null && !coverPath.isBlank() && Files.isRegularFile(Path.of(coverPath))) {
                        try {
                            Pointer img = msgSend(cls("NSImage"), sel("alloc"));
                            img = msgSend(img, sel("initWithContentsOfFile:"), nsString(coverPath));
                            if (img != null) {
                                Pointer artwork = msgSend(cls("MPMediaItemArtwork"), sel("alloc"));
                                artwork = msgSend(artwork, sel("initWithImage:"), img);
                                if (artwork != null) {
                                    msgSendVoid(dict, sel("setObject:forKey:"), artwork, nsString("artwork"));
                                    msgSendVoid(artwork, sel("release"));
                                }
                                msgSendVoid(img, sel("release"));
                            }
                        } catch (Throwable ignored) {}
                    }
                    msgSendVoid(nowPlayingCenter, sel("setNowPlayingInfo:"), dict);
                }

                // MPNowPlayingPlaybackState: 1 = Playing, 2 = Paused
                try {
                    msgSendVoid(nowPlayingCenter, sel("setPlaybackState:"), playing ? 1L : 2L);
                } catch (Throwable ignored) {}

                failureMessage = null;
            } catch (Throwable failure) {
                failureMessage = failure.getMessage();
            }
        });
    }

    @Override
    public String getFailureMessage() {
        return failureMessage;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        worker.execute(() -> {
            try {
                if (nowPlayingCenter != null) {
                    msgSendVoid(nowPlayingCenter, sel("setNowPlayingInfo:"), Pointer.NULL);
                }
                if (targetInstance != null) {
                    if (playCmd != null) msgSendVoid(playCmd, sel("removeTarget:action:"), targetInstance, sel("handlePlay:"));
                    if (pauseCmd != null) msgSendVoid(pauseCmd, sel("removeTarget:action:"), targetInstance, sel("handlePause:"));
                    if (toggleCmd != null) msgSendVoid(toggleCmd, sel("removeTarget:action:"), targetInstance, sel("handleToggle:"));
                    if (nextCmd != null) msgSendVoid(nextCmd, sel("removeTarget:action:"), targetInstance, sel("handleNext:"));
                    if (prevCmd != null) msgSendVoid(prevCmd, sel("removeTarget:action:"), targetInstance, sel("handlePrev:"));
                    msgSendVoid(targetInstance, sel("release"));
                    targetInstance = null;
                }
            } catch (Throwable ignored) {}
        });
        worker.shutdown();
    }
}
