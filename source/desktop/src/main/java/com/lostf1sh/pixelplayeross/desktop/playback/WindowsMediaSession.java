package com.lostf1sh.pixelplayeross.desktop.playback;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import static com.lostf1sh.pixelplayeross.desktop.playback.WinRtCom.*;
import com.sun.net.httpserver.HttpServer;
import java.awt.Window;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.IntConsumer;

/** Windows SMTC ABI bridge. COM calls are confined to one MTA thread. */
public final class WindowsMediaSession implements PlatformMediaSession {
    private static final String APP_ID="Umimuko.Music";
    private static final String CONTROLS="99fa3ff4-1742-42a6-902e-087d41f965ec";
    private static final String INTEROP="ddb0472d-c911-4a1f-86d9-dc3d71a95f5a";
    private static final NativeLibrary COMBASE=NativeLibrary.getInstance("combase");
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> {
        Thread thread=new Thread(r,"pixel-windows-media");thread.setDaemon(true);return thread;
    });
    private final IntConsumer button;
    private Pointer controls;
    private Pointer updater;
    private Pointer music;
    private Pointer music2;
    private SmtcButtonDelegate buttonDelegate;
    private long eventToken;
    private boolean initialized;
    private boolean buttonsRegistered;
    private boolean buttonFailureLogged;
    private HttpServer coverServer;
    private final Map<String,Path> covers=Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile boolean closed;
    private volatile String failureDetail;
    private volatile Snapshot pending;
    private record Snapshot(String title,String artist,String album,String coverPath,
                            boolean playing,boolean previous,boolean next) { }
    @Override public String getFailureMessage() {return failureDetail;}
    /** Give the unpackaged desktop window a stable identity before it is created. */
    public static void configureProcess() {
        if(!System.getProperty("os.name").startsWith("Windows"))return;
        try {
            NativeLibrary.getInstance("shell32").getFunction("SetCurrentProcessExplicitAppUserModelID",Function.ALT_CONVENTION)
                .invokeInt(new Object[]{new WString(APP_ID)});
        } catch(Throwable failure) {System.err.println("PixelPlayer app identity: " + failure);}
    }

    public WindowsMediaSession(Window window, IntConsumer button) {
        this.button=button;
        if (!System.getProperty("os.name").startsWith("Windows")) return;
        worker.execute(() -> {try {initialize(Native.getWindowPointer(window));}
            catch(Throwable failure) {
                failureDetail=failure.toString();
                System.err.println("PixelPlayer media session: " + failure);cleanup();
            }});
    }
    private static int nativeCall(String name,Object... args) {
        return COMBASE.getFunction(name,Function.ALT_CONVENTION).invokeInt(args);
    }
    private static void check(int hr) {if(hr<0) throw new IllegalStateException("Windows media API: 0x"+Integer.toHexString(hr));}
    private static Pointer hstring(String text) {
        PointerByReference result=new PointerByReference();
        check(nativeCall("WindowsCreateString",new WString(text),text.length(),result));
        return result.getValue();
    }
    private static void deleteString(Pointer value) {if(value!=null) nativeCall("WindowsDeleteString",value);}
    private static void setText(Pointer object,int slot,String text) {
        Pointer value=hstring(text==null?"":text);
        try {check(call(object,slot,value));} finally {deleteString(value);}
    }
    private void initialize(Pointer hwnd) {
        check(nativeCall("RoInitialize",1));
        initialized=true;
        Pointer name=hstring("Windows.Media.SystemMediaTransportControls");
        PointerByReference factory=new PointerByReference();
        try {check(nativeCall("RoGetActivationFactory",name,guid(INTEROP),factory));}
        finally {deleteString(name);}
        Pointer interop=factory.getValue();
        try {
            PointerByReference result=new PointerByReference();
            check(call(interop,6,hwnd,guid(CONTROLS),result));
            controls=result.getValue();
        } finally {call(interop,2);}
        check(call(controls,11,(byte)1)); // IsEnabled
        check(call(controls,13,(byte)1)); // Play
        check(call(controls,17,(byte)1)); // Pause
        check(call(controls,25,(byte)1)); // Previous
        check(call(controls,27,(byte)1)); // Next
        PointerByReference display=new PointerByReference();check(call(controls,8,display));updater=display.getValue();
        check(call(updater,7,1)); // MediaPlaybackType.Music
        try {setText(updater,9,APP_ID);} catch(Throwable ignored) { }
        PointerByReference properties=new PointerByReference();check(call(updater,12,properties));music=properties.getValue();
        PointerByReference albumProperties=new PointerByReference();
        if(call(music,0,guid("00368462-97d3-44b9-b00f-008afcefaf18"),albumProperties)>=0)
            music2=albumProperties.getValue();
        tryRegisterButtons();
        if(pending!=null) publish(pending);
    }
    private void registerButtons() {
        if(buttonsRegistered || closed)return;
        SmtcButtonDelegate candidate=new SmtcButtonDelegate(button,()->closed);
        try {
            Memory token=new Memory(8);
            check(call(controls,32,candidate.pointer(),token));
            eventToken=token.getLong(0);buttonDelegate=candidate;buttonsRegistered=true;
            buttonFailureLogged=false;
        } catch(Throwable failure){candidate.close();throw failure;}
    }
    private void tryRegisterButtons() {
        if(buttonsRegistered || closed)return;
        try {registerButtons();failureDetail=null;}
        catch(Throwable failure) {
            failureDetail="Media buttons: "+failure;
            if(!buttonFailureLogged)System.err.println("Umimuko media buttons: "+failure);
            buttonFailureLogged=true;
        }
    }
    private static Pointer factory(String name,String iid) {
        Pointer className=hstring(name);
        try {
            PointerByReference result=new PointerByReference();
            check(nativeCall("RoGetActivationFactory",className,guid(iid),result));
            return result.getValue();
        } finally {deleteString(className);}
    }
    private String coverUrl(String coverPath) throws Exception {
        Path cover=Path.of(coverPath).toAbsolutePath().normalize();
        if(!Files.isRegularFile(cover))throw new IllegalArgumentException("Missing cached cover: " + cover);
        if(coverServer==null) {
            coverServer=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),0);
            coverServer.createContext("/cover/",exchange -> {
                String key=exchange.getRequestURI().getPath().substring("/cover/".length());
                Path file=covers.get(key);
                if(file==null || !exchange.getRequestMethod().equals("GET")) {
                    exchange.sendResponseHeaders(404,-1);exchange.close();return;
                }
                try {
                    byte[] bytes=Files.readAllBytes(file);
                    if(bytes.length>16*1024*1024)throw new IllegalArgumentException("Cover too large");
                    exchange.getResponseHeaders().set("Content-Type","image/png");
                    exchange.getResponseHeaders().set("Cache-Control","no-store");
                    exchange.sendResponseHeaders(200,bytes.length);
                    try(var output=exchange.getResponseBody()){output.write(bytes);}
                } catch(Exception failure) {try {exchange.sendResponseHeaders(404,-1);} catch(Exception ignored) { }exchange.close();}
            });
            coverServer.setExecutor(r -> {Thread t=new Thread(r,"pixel-cover");t.setDaemon(true);t.start();});
            coverServer.start();
        }
        String key=UUID.randomUUID()+".png";
        synchronized(covers) {
            covers.put(key,cover);
            while(covers.size()>16)covers.remove(covers.keySet().iterator().next());
        }
        return "http://127.0.0.1:"+coverServer.getAddress().getPort()+"/cover/"+key;
    }
    private void artwork(String coverPath) throws Exception {
        if(coverPath==null || coverPath.isBlank()) {check(call(updater,11,Pointer.NULL));return;}
        Pointer uriFactory=factory("Windows.Foundation.Uri","44a9796f-723e-4fdf-a218-033e75b0c084");
        Pointer streamFactory=factory("Windows.Storage.Streams.RandomAccessStreamReference","857309dc-3fbf-4e7d-986f-ef3b1a07a964");
        try {
            // WinRT's URI stream can load HTTP images; file:// paths are not reliably
            // accessible to the media flyout. This server is bound only to loopback.
            Pointer text=hstring(coverUrl(coverPath));
            PointerByReference uri=new PointerByReference();
            try {check(call(uriFactory,6,text,uri));} finally {deleteString(text);}
            try {
                PointerByReference stream=new PointerByReference();check(call(streamFactory,7,uri.getValue(),stream));
                try {check(call(updater,11,stream.getValue()));}
                finally {call(stream.getValue(),2);}
            } finally {call(uri.getValue(),2);}
        } finally {call(streamFactory,2);call(uriFactory,2);}
    }
    public void update(String title,String artist,String album,String coverPath,boolean playing,boolean previous,boolean next) {
        if(closed) return;
        Snapshot snapshot=new Snapshot(title,artist,album,coverPath,playing,previous,next);
        pending=snapshot;
        try {worker.execute(() -> {
            if(controls==null || closed)return;
            publish(snapshot);
        });} catch(RejectedExecutionException ignored) { }
    }
    private void publish(Snapshot state) {
        try {
            setText(music,7,state.title());setText(music,11,state.artist());
            if(music2!=null)setText(music2,7,state.album());
            try {artwork(state.coverPath());}
            catch(Throwable failure) {System.err.println("PixelPlayer media artwork: " + failure);}
            check(call(updater,17));
            check(call(controls,25,(byte)(state.previous()?1:0)));check(call(controls,27,(byte)(state.next()?1:0)));
            check(call(controls,7,state.playing()?3:4));
            if(buttonsRegistered)failureDetail=null;
            else tryRegisterButtons();
        } catch(Throwable failure) {
            failureDetail=failure.toString();
            System.err.println("PixelPlayer media update: " + failure);
        }
    }
    private void cleanup() {
        if(coverServer!=null){coverServer.stop(0);coverServer=null;covers.clear();}
        try {if(controls!=null && buttonsRegistered)call(controls,33,eventToken);} catch(Throwable ignored) { }
        eventToken=0;buttonsRegistered=false;
        if(buttonDelegate!=null){buttonDelegate.close();buttonDelegate=null;}
        try {if(music2!=null)call(music2,2);} catch(Throwable ignored) { } finally {music2=null;}
        try {if(music!=null)call(music,2);} catch(Throwable ignored) { } finally {music=null;}
        try {if(updater!=null)call(updater,2);} catch(Throwable ignored) { } finally {updater=null;}
        try {if(controls!=null)call(controls,2);} catch(Throwable ignored) { } finally {controls=null;}
        if(initialized) {
            COMBASE.getFunction("RoUninitialize",Function.ALT_CONVENTION).invokeVoid(new Object[0]);
            initialized=false;
        }
    }
    @Override public synchronized void close() {if(closed)return;closed=true;worker.execute(this::cleanup);worker.shutdown();}
}
