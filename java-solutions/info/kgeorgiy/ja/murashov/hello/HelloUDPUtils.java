package info.kgeorgiy.ja.murashov.hello;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HelloUDPUtils {

    static int SO_TIMEOUT = 100;

    static long AWAIT_TIMEOUT = 1000L;

    static final Pattern pattern = Pattern.compile("\\D*(\\d+)\\D*(\\d+)\\D*");

    public static void closeExecutor(ExecutorService service, long await) {
        boolean success = false;
        service.shutdown();
        try {
            success = service.awaitTermination(await, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!success) {
                service.shutdownNow();
            }
        }
    }

    public static void sendMessage(String message, SocketAddress address, DatagramSocket socket, DatagramPacket packet) throws IOException {
        byte[] byteMessage = message.getBytes(StandardCharsets.UTF_8);
        packet.setData(byteMessage);
        packet.setSocketAddress(address);
        socket.send(packet);
    }

    public static DatagramPacket receivePacket(DatagramSocket socket, DatagramPacket packet) throws IOException {
        socket.receive(packet);
        return packet;
    }

    public static String getMessage(DatagramPacket packet) {
        return new String(Arrays.copyOf(packet.getData(), packet.getLength()));
    }

    static boolean checkMessage(final String message, final int threadId, final int requestId) {
        final Matcher matcher = pattern.matcher(message);
        return matcher.matches()
                && String.valueOf(threadId).equals(matcher.group(1))
                && String.valueOf(requestId).equals(matcher.group(2));
    }

}