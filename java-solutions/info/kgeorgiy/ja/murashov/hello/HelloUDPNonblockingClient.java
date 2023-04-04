package info.kgeorgiy.ja.murashov.hello;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import info.kgeorgiy.java.advanced.hello.HelloClient;

public class HelloUDPNonblockingClient implements HelloClient {

    @Override
    public void run(final String host, final int port, final String prefix, final int threads, final int requests) {
        try {
            final SocketAddress address = new InetSocketAddress(InetAddress.getByName(host), port);
            List<DatagramChannel> channels = new ArrayList<>();
            final Selector selector = Selector.open();
            final int[] requestId = new int[threads];
            final ByteBuffer[] buffers = new ByteBuffer[threads];
            for (int i = 0; i < threads; i++) {
                DatagramChannel channel = DatagramChannel.open();
                channel.configureBlocking(false);
                channel.connect(address);
                channel.register(selector, SelectionKey.OP_WRITE, i);
                channels.add(channel);
                buffers[i] = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());
            }
            while (!selector.keys().isEmpty() && !Thread.interrupted()) {
                if (selector.select(HelloUDPUtils.SO_TIMEOUT) == 0) {
                    selector.keys().forEach(k -> k.interestOps(SelectionKey.OP_WRITE));
                    continue;
                }
                for (final Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                    final SelectionKey key = it.next();
                    final DatagramChannel channel = (DatagramChannel) key.channel();
                    final int threadId = (int) key.attachment();
                    if (key.isWritable()) {
                        final String message = prefix + threadId + "_" + requestId[threadId];
                        channel.send(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)), address);
                        key.interestOps(SelectionKey.OP_READ);
                    } else {
                        channel.receive(buffers[threadId].clear());
                        String response = StandardCharsets.UTF_8.decode(buffers[threadId].flip()).toString();
                        if (HelloUDPUtils.checkMessage(response, threadId, requestId[threadId])) {
                            // System.out.println(threadId + "_" + requestId[threadId] + " " + response);
                            requestId[threadId]++;
                        }
                        if (requests <= requestId[threadId]) {
                            channel.close();
                        } else {
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                    }
                    it.remove();
                }
            }
            channels.forEach(ch -> {
                try {
                    ch.close();
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            // :NOTE: обычно это плохой способ обработки исключений
            e.printStackTrace();
        }
    }
}