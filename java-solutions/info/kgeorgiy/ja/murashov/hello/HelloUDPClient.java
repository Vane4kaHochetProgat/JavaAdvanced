package info.kgeorgiy.ja.murashov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HelloUDPClient implements HelloClient {

    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        try {
            ExecutorService senders = Executors.newFixedThreadPool(threads);
            final SocketAddress address = new InetSocketAddress(InetAddress.getByName(host), port);
            for (int i = 0; i < threads; i++) {
                final int threadNumber = i;
                senders.submit(
                        () ->
                        {
                            try (final DatagramSocket socket = new DatagramSocket()) {
                                socket.setSoTimeout(HelloUDPUtils.SO_TIMEOUT);
                                int bufferSize = socket.getReceiveBufferSize();
                                final DatagramPacket packet = new DatagramPacket(new byte[bufferSize], bufferSize);
                                final DatagramPacket sendPacket = new DatagramPacket(new byte[bufferSize], bufferSize);
                                for (int j = 0; j < requests; j++) {
                                    String message = (prefix + threadNumber + "_" + j);
                                    while (!Thread.interrupted() && !socket.isClosed()) {
                                        try {
                                            HelloUDPUtils.sendMessage(message, address, socket, sendPacket);
                                            String response = HelloUDPUtils.getMessage(HelloUDPUtils.receivePacket(socket, packet));
                                            if (response.contains(message)) {
                                                break;
                                            }
                                        } catch (IOException ignored) {
                                        }
                                    }
                                }
                            } catch (SocketException ignored) {
                            }
                        }
                );
            }
            HelloUDPUtils.closeExecutor(senders, HelloUDPUtils.AWAIT_TIMEOUT * requests);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (Objects.isNull(args) || args.length != 5 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Expected 5 non null arguments");
            return;
        }
        try {
            String host = args[0];
            int port = Integer.parseInt(args[1]);
            String prefix = args[2];
            int threads = Integer.parseInt(args[3]);
            int requests = Integer.parseInt(args[4]);
            new HelloUDPClient().run(host, port, prefix, threads, requests);
        } catch (final NumberFormatException e) {
            System.err.println("Arguments 1, 3 and 4 expected to be integer numbers.");
        }
    }

}