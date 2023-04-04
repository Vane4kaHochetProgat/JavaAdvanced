package info.kgeorgiy.ja.murashov.hello;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import info.kgeorgiy.java.advanced.hello.HelloServer;

public class HelloUDPNonblockingServer implements HelloServer {

    private Selector selector;
    private DatagramChannel channel;
    private ExecutorService service;
    private Queue<Response> responses;
    private int bufferSize;

    @Override
    public void start(int port, int threads) {
        try {
            selector = Selector.open();
            channel = DatagramChannel.open();
            channel.configureBlocking(false);
            channel.bind(new InetSocketAddress(port));
            channel.register(selector, SelectionKey.OP_READ);
            service = Executors.newFixedThreadPool(threads + 1);
            responses = new ConcurrentLinkedDeque<>();
            bufferSize = channel.socket().getReceiveBufferSize();
            service.submit(this::run);
        } catch (final IOException e) {
            System.err.println("Can't start server " + e.getLocalizedMessage());
        }
    }

    private void run() {
        while (!Thread.interrupted() && !channel.socket().isClosed() && selector.isOpen() && !selector.keys().isEmpty()) {
            try {
                selector.select();
            } catch (IOException e) {
                System.err.println("Can not select keys " + e.getLocalizedMessage());
                close();
                return;
            }
            for (final Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                final SelectionKey key = it.next();
                if (key.isReadable()) {
                    try {
                        final ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
                        SocketAddress address = channel.receive(byteBuffer);
                        service.submit(() -> {
                            responses.add(new Response(address, "Hello, " + StandardCharsets.UTF_8.decode(byteBuffer.flip())));
                            key.interestOps(SelectionKey.OP_WRITE);
                            selector.wakeup();
                        });
                    } catch (IOException ignored) {
                    }
                } else {
                    final Response message = responses.poll();
                    if (message != null) {
                        try {
                            channel.send(ByteBuffer.wrap(message.message.getBytes(StandardCharsets.UTF_8)), message.address);
                        } catch (IOException ignored) {
                        }
                        key.interestOpsOr(SelectionKey.OP_READ);
                    }
                }
                it.remove();
            }
        }
    }

    @Override
    public void close() {
        HelloUDPUtils.closeExecutor(service, HelloUDPUtils.AWAIT_TIMEOUT);
        try {
            channel.close();
            selector.close();
        } catch (IOException e) {
            System.err.println("Can't  close selector or channel " + e.getLocalizedMessage());
        }
    }

    private record Response(SocketAddress address, String message) {}
}