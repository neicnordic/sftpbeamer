package no.neic.tryggve;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.WriteStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public final class UploadWriteStream implements WriteStream<Buffer>{
    private static final Logger logger = LoggerFactory.getLogger(UploadWriteStream.class);

    private final OutputStream outputStream;
    private Handler<Throwable> exceptionHandler;
    private boolean closed;
    private AtomicInteger writesOutstanding;
    private Handler<Void> drainHandler;

    private int maxWrites = 512 * 1024;
    private final Context context;

    private ByteBuffer dataByteBuffer;

    public UploadWriteStream(final OutputStream outputStream, final Context context) {
        this.outputStream = outputStream;
        this.closed = false;
        this.writesOutstanding = new AtomicInteger(0);
        this.context = context;
        this.dataByteBuffer = ByteBuffer.allocateDirect(2 * maxWrites);
    }

    @Override
    public UploadWriteStream exceptionHandler(Handler<Throwable> handler) {
        checkClosed();
        this.exceptionHandler = handler;
        return this;
    }

    private synchronized void handleException(Throwable t) {
        if (exceptionHandler != null && t instanceof Exception) {
            exceptionHandler.handle(t);
        } else {
            logger.error("Unhandled exception", t);

        }
    }

    @Override
    public UploadWriteStream write(Buffer buffer){
        checkClosed();
        this.writesOutstanding.addAndGet(buffer.length());
        dataByteBuffer.put(buffer.getBytes());
        if (this.writesOutstanding.get() >= maxWrites) {
            this.context.runOnContext(Void -> {
                try {
                    dataByteBuffer.flip();
                    byte[] temp = new byte[dataByteBuffer.remaining()];
                    dataByteBuffer.get(temp);
                    outputStream.write(temp);
                    temp = null;
                    dataByteBuffer.clear();
                    writesOutstanding.getAndSet(0);
                    checkDrained();
                } catch (IOException e) {
                    handleException(e);
                }
            });
        }
        return this;
    }

    @Override
    public void end() {
        checkClosed();
        if (writesOutstanding.get() > 0) {
            try {
                dataByteBuffer.flip();
                byte[] temp = new byte[dataByteBuffer.remaining()];
                dataByteBuffer.get(temp);
                outputStream.write(temp);
            } catch (IOException e) {
                handleException(e);
            }
        }
        closed = true;
    }

    private synchronized void checkClosed() {
        if (closed) {
            throw new IllegalStateException("OutputStream is closed");
        }
    }

    @Override
    public UploadWriteStream setWriteQueueMaxSize(int maxSize) {
        checkClosed();
        this.maxWrites = maxSize;
        return this;
    }

    @Override
    public boolean writeQueueFull() {
        checkClosed();
        return writesOutstanding.get() >= maxWrites;
    }

    @Override
    public UploadWriteStream drainHandler(Handler<Void> handler) {
        checkClosed();
        this.drainHandler = handler;
        return this;
    }

    private synchronized void checkDrained() {
        if (drainHandler != null) {
            Handler<Void> handler = drainHandler;
            drainHandler = null;
            handler.handle(null);
        }
    }
}
