package com.chatagg.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public class SyncEventBus {

    private static final Logger log = LoggerFactory.getLogger(SyncEventBus.class);

    private final LinkedBlockingQueue<SyncEvent> queue = new LinkedBlockingQueue<>(512);
    private final AtomicReference<PrintWriter> writerRef = new AtomicReference<>();

    /** Called from the sync thread; drains immediately if a client is connected. */
    public void emit(SyncEvent event) {
        queue.offer(event);
        drainToClient();
    }

    /** Called when the browser opens the SSE stream; flushes any buffered events. */
    public synchronized void setWriter(PrintWriter writer) {
        writerRef.set(writer);
        drainToClient();
    }

    /** Called when the SSE connection closes. */
    public synchronized void clearWriter() {
        writerRef.set(null);
    }

    private synchronized void drainToClient() {
        PrintWriter writer = writerRef.get();
        if (writer == null) return;
        SyncEvent event;
        while ((event = queue.poll()) != null) {
            writer.write("event: " + event.type() + "\n");
            writer.write("data: " + event.toJson() + "\n\n");
            writer.flush();
            if (writer.checkError()) {
                log.warn("Client disconnected while sending: {}", event.type());
                writerRef.set(null);
                return;
            }
        }
    }
}
