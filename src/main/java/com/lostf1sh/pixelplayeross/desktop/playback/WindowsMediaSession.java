package com.lostf1sh.pixelplayeross.desktop.playback;

import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths;
import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import static com.lostf1sh.pixelplayeross.desktop.playback.WinRtCom.*;
import com.sun.net.httpserver.HttpServer;
import java.awt.Window;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final NativeLibrary COMBASE = com.sun.jna.Platform.isWindows() ? NativeLibrary.getInstance("combase") : null;
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
        ensureAumidRegistration();
    }

    public static void ensureAumidRegistration() {
        if(!System.getProperty("os.name").startsWith("Windows"))return;
        try {
            Path icon = extractAppIcon();
            String iconPath = icon != null ? icon.toAbsolutePath().toString() : "";

            // Register AUMID in HKCU\Software\Classes\AppUserModelId\Umimuko.Music
            String aumidKey = "Software\\Classes\\AppUserModelId\\" + APP_ID;
            writeRegistryValue(aumidKey, "DisplayName", "Umimuko Music");
            if (!iconPath.isBlank()) {
                writeRegistryValue(aumidKey, "IconUri", iconPath);
                writeRegistryValue(aumidKey, "IconBackgroundColor", "0");
            }

            // Also register in HKCU\Software\Classes\Umimuko.Music for complete shell association
            String progIdKey = "Software\\Classes\\" + APP_ID;
            writeRegistryValue(progIdKey, null, "Umimuko Music");
            writeRegistryValue(progIdKey, "DisplayName", "Umimuko Music");
            writeRegistryValue(progIdKey, "FriendlyTypeName", "Umimuko Music");
            if (!iconPath.isBlank()) {
                writeRegistryValue(progIdKey, "IconUri", iconPath);
                writeRegistryValue(progIdKey + "\\DefaultIcon", null, iconPath);
            }

            // Ensure Start Menu shortcut with System.AppUserModel.ID
            ensureStartMenuShortcut(iconPath);
        } catch (Throwable failure) {
            System.err.println("PixelPlayer AUMID registration: " + failure);
        }
    }

    private static Path extractAppIcon() {
        try {
            Path dataDir = PlatformPaths.getDataDirectory();
            Files.createDirectories(dataDir);
            Path icoDest = dataDir.resolve("umimuko.ico");
            Path pngDest = dataDir.resolve("app-icon.png");

            // 1. If packaging/umimuko.ico exists on disk, copy it
            Path projectIco = Path.of("packaging", "umimuko.ico");
            if (Files.isRegularFile(projectIco)) {
                try {
                    if (!Files.exists(icoDest) || Files.size(icoDest) != Files.size(projectIco)) {
                        Files.copy(projectIco, icoDest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Throwable ignored) {}
            }

            // 2. Classpath resource for umimuko.ico
            for (String res : new String[]{"/packaging/umimuko.ico", "/branding/umimuko.ico", "/umimuko.ico"}) {
                try (InputStream in = WindowsMediaSession.class.getResourceAsStream(res)) {
                    if (in != null) {
                        Files.copy(in, icoDest, StandardCopyOption.REPLACE_EXISTING);
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            // 3. Classpath resource for window-icon.png
            for (String res : new String[]{"/branding/window-icon.png", "branding/window-icon.png"}) {
                try (InputStream in = WindowsMediaSession.class.getResourceAsStream(res)) {
                    if (in != null) {
                        Files.copy(in, pngDest, StandardCopyOption.REPLACE_EXISTING);
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            // 4. Source tree fallback for png
            if (!Files.exists(pngDest)) {
                for (Path src : new Path[]{
                    Path.of("packaging", "umimuko.png"),
                    Path.of("src", "main", "resources", "branding", "window-icon.png")
                }) {
                    if (Files.isRegularFile(src)) {
                        try {
                            Files.copy(src, pngDest, StandardCopyOption.REPLACE_EXISTING);
                            break;
                        } catch (Throwable ignored) {}
                    }
                }
            }

            if (Files.isRegularFile(icoDest) && Files.size(icoDest) > 0) {
                return icoDest.toAbsolutePath().normalize();
            }
            if (Files.isRegularFile(pngDest) && Files.size(pngDest) > 0) {
                return pngDest.toAbsolutePath().normalize();
            }
        } catch (Throwable failure) {
            System.err.println("PixelPlayer icon extraction: " + failure);
        }
        return null;
    }

    private static void writeRegistryValue(String subkey, String valueName, String data) {
        if (!PlatformPaths.isWindows() || data == null) return;
        boolean jnaOk = false;
        try {
            WinRegistry.set(subkey, valueName, data);
            jnaOk = true;
        } catch (Throwable ignored) {
        }
        if (!jnaOk) {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add("reg");
                cmd.add("add");
                cmd.add("HKCU\\" + subkey);
                if (valueName == null || valueName.isEmpty()) {
                    cmd.add("/ve");
                } else {
                    cmd.add("/v");
                    cmd.add(valueName);
                }
                cmd.add("/t");
                cmd.add("REG_SZ");
                cmd.add("/d");
                cmd.add(data);
                cmd.add("/f");
                new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            } catch (Throwable t) {
                System.err.println("PixelPlayer reg add fallback: " + t);
            }
        }
    }

    private static final class WinRegistry {
        static void set(String key, String name, String val) {
            com.sun.jna.platform.win32.Advapi32Util.registryCreateKey(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, key);
            com.sun.jna.platform.win32.Advapi32Util.registrySetStringValue(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, key, name != null ? name : "", val);
        }
    }

    private static void ensureStartMenuShortcut(String iconPath) {
        if (!PlatformPaths.isWindows()) return;
        try {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isBlank()) {
                appData = System.getProperty("user.home") + "\\AppData\\Roaming";
            }
            Path programsDir = Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs");
            Path shortcutPath = programsDir.resolve("Umimuko Music.lnk");
            Files.createDirectories(programsDir);

            String target = resolveExecutablePath();

            boolean comSuccess = false;
            try {
                comSuccess = createOrUpdateShortcutViaCom(shortcutPath, target, iconPath);
            } catch (Throwable t) {
                System.err.println("PixelPlayer COM shortcut: " + t);
            }

            if (!comSuccess && !Files.exists(shortcutPath)) {
                createShortcutViaPowerShell(shortcutPath, target, iconPath);
            }
        } catch (Throwable failure) {
            System.err.println("PixelPlayer Start Menu shortcut: " + failure);
        }
    }

    private static boolean createOrUpdateShortcutViaCom(Path shortcutPath, String targetPath, String iconPath) {
        if (!Platform.isWindows()) return false;
        NativeLibrary ole32;
        try {
            ole32 = NativeLibrary.getInstance("ole32");
        } catch (Throwable t) {
            return false;
        }
        try {
            ole32.getFunction("CoInitialize", Function.ALT_CONVENTION).invokeInt(new Object[]{Pointer.NULL});
        } catch (Throwable ignored) {}

        Pointer link = null;
        Pointer persist = null;
        try {
            PointerByReference linkRef = new PointerByReference();
            int hr = ole32.getFunction("CoCreateInstance", Function.ALT_CONVENTION).invokeInt(new Object[]{
                guid("00021401-0000-0000-c000-000000000046"), // CLSID_ShellLink
                Pointer.NULL,
                1, // CLSCTX_INPROC_SERVER
                guid("000214f9-0000-0000-c000-000000000046"), // IID_IShellLinkW
                linkRef
            });
            if (hr < 0 || linkRef.getValue() == null) return false;
            link = linkRef.getValue();

            PointerByReference persistRef = new PointerByReference();
            if (call(link, 0, guid("0000010b-0000-0000-c000-000000000046"), persistRef) < 0 || persistRef.getValue() == null) {
                return false;
            }
            persist = persistRef.getValue();

            if (Files.exists(shortcutPath)) {
                try {
                    call(persist, 5, new WString(shortcutPath.toAbsolutePath().toString()), 0); // IPersistFile::Load
                } catch (Throwable ignored) {}
            }

            if (targetPath != null && !targetPath.isBlank()) {
                call(link, 20, new WString(targetPath)); // IShellLinkW::SetPath
                try {
                    Path parent = Path.of(targetPath).getParent();
                    if (parent != null) {
                        call(link, 9, new WString(parent.toAbsolutePath().toString())); // IShellLinkW::SetWorkingDirectory
                    }
                } catch (Throwable ignored) {}
            }
            call(link, 7, new WString("Umimuko Music")); // IShellLinkW::SetDescription
            if (iconPath != null && !iconPath.isBlank()) {
                call(link, 17, new WString(iconPath), 0); // IShellLinkW::SetIconLocation
            }

            // Set PKEY_AppUserModel_ID on IPropertyStore
            PointerByReference storeRef = new PointerByReference();
            if (call(link, 0, guid("886d8eeb-8cf2-4446-8d02-cdba1dbdcf99"), storeRef) >= 0 && storeRef.getValue() != null) {
                Pointer store = storeRef.getValue();
                try {
                    Memory key = new Memory(20);
                    key.write(0, guid("9f4c2855-9f79-4b39-a8d0-e1d42de1d5f3").getByteArray(0, 16), 0, 16);
                    key.setInt(16, 5);

                    int ptrSize = Native.POINTER_SIZE;
                    Memory propvar = new Memory(8 + ptrSize * 2L);
                    propvar.clear();
                    propvar.setShort(0, (short) 31); // VT_LPWSTR
                    Memory strVal = new Memory((APP_ID.length() + 1) * 2L);
                    strVal.setWideString(0, APP_ID);
                    propvar.setPointer(8, strVal);

                    call(store, 6, key, propvar); // SetValue
                    call(store, 7); // Commit
                } finally {
                    call(store, 2); // Release
                }
            }

            int saveHr = call(persist, 6, new WString(shortcutPath.toAbsolutePath().toString()), 1); // IPersistFile::Save
            return saveHr >= 0;
        } catch (Throwable t) {
            System.err.println("PixelPlayer COM shortcut: " + t);
            return false;
        } finally {
            if (persist != null) {
                try { call(persist, 2); } catch (Throwable ignored) {}
            }
            if (link != null) {
                try { call(link, 2); } catch (Throwable ignored) {}
            }
            try {
                ole32.getFunction("CoUninitialize", Function.ALT_CONVENTION).invokeVoid(new Object[0]);
            } catch (Throwable ignored) {}
        }
    }

    private static void createShortcutViaPowerShell(Path shortcutPath, String targetPath, String iconPath) {
        try {
            StringBuilder script = new StringBuilder();
            script.append("$ws = New-Object -ComObject WScript.Shell; ");
            script.append("$sc = $ws.CreateShortcut('").append(shortcutPath.toAbsolutePath().toString().replace("'", "''")).append("'); ");
            if (targetPath != null && !targetPath.isBlank()) {
                script.append("$sc.TargetPath = '").append(targetPath.replace("'", "''")).append("'; ");
                Path parent = Path.of(targetPath).getParent();
                if (parent != null) {
                    script.append("$sc.WorkingDirectory = '").append(parent.toAbsolutePath().toString().replace("'", "''")).append("'; ");
                }
            }
            if (iconPath != null && !iconPath.isBlank()) {
                script.append("$sc.IconLocation = '").append(iconPath.replace("'", "''")).append(",0'; ");
            }
            script.append("$sc.Description = 'Umimuko Music'; ");
            script.append("$sc.Save();");

            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script.toString())
                .redirectErrorStream(true)
                .start()
                .waitFor();
        } catch (Throwable t) {
            System.err.println("PixelPlayer PowerShell shortcut fallback: " + t);
        }
    }

    private static String resolveExecutablePath() {
        try {
            var cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent() && !cmd.get().isBlank()) {
                Path p = Path.of(cmd.get());
                if (Files.isRegularFile(p)) {
                    return p.toAbsolutePath().toString();
                }
            }
        } catch (Throwable ignored) {}

        try {
            String javaHome = System.getProperty("java.home");
            if (javaHome != null && !javaHome.isBlank()) {
                Path javaw = Path.of(javaHome, "bin", "javaw.exe");
                if (Files.isRegularFile(javaw)) return javaw.toAbsolutePath().toString();
                Path java = Path.of(javaHome, "bin", "java.exe");
                if (Files.isRegularFile(java)) return java.toAbsolutePath().toString();
            }
        } catch (Throwable ignored) {}

        return "";
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
        ensureAumidRegistration();
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
        try {call(updater,17);} catch(Throwable ignored) { }
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
            try {setText(updater,9,APP_ID);} catch(Throwable ignored) { }
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
