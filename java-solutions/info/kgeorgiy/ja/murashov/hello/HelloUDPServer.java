package info.kgeorgiy.ja.murashov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HelloUDPServer implements HelloServer {

    private ExecutorService senders;
    private DatagramSocket socket;

    @Override
    public void start(int port, int threads) {
        senders = Executors.newFixedThreadPool(threads);
        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(HelloUDPUtils.SO_TIMEOUT);
            int bufferSize = socket.getReceiveBufferSize();
            for (int i = 0; i < threads; i++) {
                senders.submit(
                        () -> {
                            final DatagramPacket packet = new DatagramPacket(new byte[bufferSize], bufferSize);
                            while (!Thread.interrupted() && !socket.isClosed()) {
                                try {
                                    String message = HelloUDPUtils.getMessage(HelloUDPUtils.receivePacket(socket, packet));
                                    HelloUDPUtils.sendMessage("Hello, " + message, packet.getSocketAddress(), socket, packet);
                                } catch (IOException ignored) {
                                }
                            }
                        }
                );
            }
        } catch (SocketException ignored) {
        }
    }

    @Override
    public void close() {
        socket.close();
        HelloUDPUtils.closeExecutor(senders, HelloUDPUtils.AWAIT_TIMEOUT);
    }

    public static void main(String[] args) {
        if (Objects.isNull(args) || args.length != 2 || Objects.isNull(args[0]) || Objects.isNull(args[1])) {
            System.err.println("Expected 2 non null arguments");
            return;
        }
        try {
            final int port = Integer.parseInt(args[0]);
            final int threads = Integer.parseInt(args[1]);
            HelloServer server = new HelloUDPServer();
            server.start(port, threads);
            server.close();
        } catch (final NumberFormatException e) {
            System.err.println("Arguments 1 and 2 expected to be integer numbers");
        }
    }

}