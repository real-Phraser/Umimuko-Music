package com.lostf1sh.pixelplayeross.desktop.playback;

import java.io.*;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/**
 * Linux desktop media session implementing the MPRIS 2 (Media Player Remote Interfacing Specification)
 * over the local D-Bus session bus.
 */
public final class LinuxMediaSession implements PlatformMediaSession {
    private static final String BUS_NAME = "org.mpris.MediaPlayer2.UmimukoMusic";
    private static final String OBJECT_PATH = "/org/mpris/MediaPlayer2";

    private final IntConsumer button;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "pixel-linux-mpris");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean closed;
    private volatile String failureMessage;

    private SocketChannel channel;
    private int serial = 1;

    private volatile String title = "";
    private volatile String artist = "";
    private volatile String album = "";
    private volatile String coverPath = "";
    private volatile boolean playing;
    private volatile boolean previous;
    private volatile boolean next;

    public LinuxMediaSession(IntConsumer button) {
        this.button = button;
        worker.execute(this::initDbus);
    }

    private void initDbus() {
        String address = System.getenv("DBUS_SESSION_BUS_ADDRESS");
        if (address == null || address.isBlank()) {
            failureMessage = "DBUS_SESSION_BUS_ADDRESS not set";
            return;
        }

        try {
            String path = extractSocketPath(address);
            if (path == null) {
                failureMessage = "Unsupported D-Bus address: " + address;
                return;
            }

            channel = SocketChannel.open(UnixDomainSocketAddress.of(path));
            channel.configureBlocking(true);

            // SASL handshake: \0AUTH EXTERNAL <hex_uid>\r\nNEGOTIATE_UNIX_FD\r\nBEGIN\r\n
            long uid = getUid();
            String uidHex = bytesToHex(Long.toString(uid).getBytes(StandardCharsets.US_ASCII));
            String auth = "\0AUTH EXTERNAL " + uidHex + "\r\nBEGIN\r\n";
            channel.write(ByteBuffer.wrap(auth.getBytes(StandardCharsets.US_ASCII)));

            // Read response
            ByteBuffer buf = ByteBuffer.allocate(256);
            channel.read(buf);
            String response = new String(buf.array(), 0, buf.position(), StandardCharsets.US_ASCII);
            if (!response.startsWith("OK")) {
                failureMessage = "D-Bus auth failed: " + response;
                channel.close();
                return;
            }

            // Send Hello
            sendHello();
            // Request well-known bus name
            requestBusName();

            // Spawn incoming message listener
            Thread listener = new Thread(this::listenLoop, "pixel-mpris-listener");
            listener.setDaemon(true);
            listener.start();

            failureMessage = null;
        } catch (Throwable failure) {
            failureMessage = "D-Bus connection error: " + failure.getMessage();
        }
    }

    private static String extractSocketPath(String address) {
        for (String part : address.split(",")) {
            if (part.startsWith("unix:path=")) {
                return part.substring("unix:path=".length());
            }
        }
        return null;
    }

    private static long getUid() {
        try {
            String userName = System.getProperty("user.name");
            Process p = new ProcessBuilder("id", "-u", userName).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return Long.parseLong(line.trim());
                }
            }
        } catch (Exception ignored) {}
        return 1000L;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private synchronized int nextSerial() {
        return serial++;
    }

    private void sendHello() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] msg = encodeMethodCall(nextSerial(), "org.freedesktop.DBus", "/org/freedesktop/DBus",
            "org.freedesktop.DBus", "Hello", "", body.toByteArray());
        channel.write(ByteBuffer.wrap(msg));
    }

    private void requestBusName() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeString(body, BUS_NAME);
        writeInt(body, 7); // DBUS_NAME_FLAG_ALLOW_REPLACEMENT | DBUS_NAME_FLAG_REPLACE_EXISTING | DBUS_NAME_FLAG_DO_NOT_QUEUE
        byte[] msg = encodeMethodCall(nextSerial(), "org.freedesktop.DBus", "/org/freedesktop/DBus",
            "org.freedesktop.DBus", "RequestName", "su", body.toByteArray());
        channel.write(ByteBuffer.wrap(msg));
    }

    private static byte[] encodeMethodCall(int serial, String dest, String path, String iface, String member, String sig, byte[] body) throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write('l'); // Little-endian
        header.write(1);   // Message type: METHOD_CALL
        header.write(0);   // Flags
        header.write(1);   // Protocol version
        writeInt(header, body.length);
        writeInt(header, serial);

        ByteArrayOutputStream fields = new ByteArrayOutputStream();
        // Path (field 1)
        writeHeaderField(fields, 1, 'o', path);
        // Interface (field 2)
        writeHeaderField(fields, 2, 's', iface);
        // Member (field 3)
        writeHeaderField(fields, 3, 's', member);
        // Destination (field 6)
        if (dest != null && !dest.isEmpty()) {
            writeHeaderField(fields, 6, 's', dest);
        }
        // Signature (field 8)
        if (sig != null && !sig.isEmpty()) {
            writeHeaderField(fields, 8, 'g', sig);
        }

        byte[] fieldBytes = fields.toByteArray();
        writeInt(header, fieldBytes.length);
        header.write(fieldBytes);
        pad(header, 8);
        header.write(body);
        return header.toByteArray();
    }

    private static void writeHeaderField(ByteArrayOutputStream out, int code, char type, String value) throws IOException {
        pad(out, 8);
        out.write(code);
        if (type == 'g') {
            writeSignature(out, "g");
            byte[] val = value.getBytes(StandardCharsets.UTF_8);
            out.write(val.length);
            out.write(val);
            out.write(0);
        } else {
            writeSignature(out, String.valueOf(type));
            writeString(out, value);
        }
    }

    private static void writeString(ByteArrayOutputStream out, String value) throws IOException {
        pad(out, 4);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeInt(out, bytes.length);
        out.write(bytes);
        out.write(0);
    }

    private static void writeSignature(ByteArrayOutputStream out, String sig) throws IOException {
        byte[] bytes = sig.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes.length);
        out.write(bytes);
        out.write(0);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void pad(ByteArrayOutputStream out, int alignment) {
        while ((out.size() % alignment) != 0) {
            out.write(0);
        }
    }

    private void listenLoop() {
        ByteBuffer headerBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        try {
            while (!closed && channel != null && channel.isOpen()) {
                headerBuf.clear();
                while (headerBuf.hasRemaining()) {
                    if (channel.read(headerBuf) < 0) return;
                }
                headerBuf.flip();
                byte endian = headerBuf.get();
                byte msgType = headerBuf.get();
                headerBuf.get(); // flags
                headerBuf.get(); // proto
                int bodyLen = headerBuf.getInt();
                int msgSerial = headerBuf.getInt();
                int fieldsLen = headerBuf.getInt();

                int totalRemainder = fieldsLen + ((8 - (fieldsLen % 8)) % 8) + bodyLen;
                ByteBuffer remainder = ByteBuffer.allocate(totalRemainder);
                while (remainder.hasRemaining()) {
                    if (channel.read(remainder) < 0) return;
                }

                // If it's a METHOD_CALL, check if it targets MPRIS
                if (msgType == 1) {
                    handleIncomingMethod(msgSerial, remainder.array(), fieldsLen);
                }
            }
        } catch (Exception ignored) {}
    }

    private void handleIncomingMethod(int callSerial, byte[] data, int fieldsLen) {
        try {
            String text = new String(data, 0, fieldsLen, StandardCharsets.UTF_8);
            if (text.contains("PlayPause")) {
                button.accept(BUTTON_PLAY);
            } else if (text.contains("Play")) {
                button.accept(BUTTON_PLAY);
            } else if (text.contains("Pause")) {
                button.accept(BUTTON_PAUSE);
            } else if (text.contains("Next")) {
                button.accept(BUTTON_NEXT);
            } else if (text.contains("Previous")) {
                button.accept(BUTTON_PREVIOUS);
            } else if (text.contains("Stop")) {
                button.accept(BUTTON_STOP);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void update(String title, String artist, String album, String coverPath, boolean playing, boolean previous, boolean next) {
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        this.album = album != null ? album : "";
        this.coverPath = coverPath != null ? coverPath : "";
        this.playing = playing;
        this.previous = previous;
        this.next = next;
    }

    @Override
    public String getFailureMessage() {
        return failureMessage;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (Exception ignored) {}
        worker.shutdown();
    }
}
