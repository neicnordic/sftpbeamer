package no.neic.tryggve;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DownloadZipOutputStream extends OutputStream{
    private final WriteStream<Buffer> response;
    private final AtomicBoolean isWritable;


    public DownloadZipOutputStream(final WriteStream<Buffer> response, final AtomicBoolean isWritable) {
        this.response = response;
        this.isWritable = isWritable;
    }

    @Override
    public synchronized void write(final int b) throws IOException {
        response.write(Buffer.buffer().appendByte((byte) b));
        if (response.writeQueueFull()) {
            this.isWritable.set(false);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        response.write(Buffer.buffer().appendBytes(b));
        if (response.writeQueueFull()) {
            this.isWritable.set(false);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        response.write(Buffer.buffer().appendBytes(Arrays.copyOfRange(b, off, len)));
        if (response.writeQueueFull()) {
            this.isWritable.set(false);
        }
    }

    @Override
    public void flush() throws IOException {
    }

    @Override
    public void close() throws IOException {
        flush();
        response.end();
    }
}
