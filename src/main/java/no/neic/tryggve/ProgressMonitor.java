package no.neic.tryggve;

import com.jcraft.jsch.SftpProgressMonitor;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import no.neic.tryggve.constants.JsonPropertyName;
import no.neic.tryggve.constants.VertxConstant;

import java.nio.file.FileSystems;

public final class ProgressMonitor implements SftpProgressMonitor {
    private EventBus bus;
    private String address;
    private long fileSize;
    private String fileName;
    private long transferredSize = 0;

    public ProgressMonitor(EventBus bus, String address) {
        this.bus = bus;
        this.address = address;
    }

    @Override
    public void init(int op, String src, String dest, long fileSize) {
        this.fileSize = fileSize;
        this.fileName = src;
    }

    @Override
    public boolean count(long transferredData) {
        this.transferredSize += transferredData;
        JsonObject jsonObject = new JsonObject();
        jsonObject.put(JsonPropertyName.STATUS, "transferring").put(JsonPropertyName.ADDRESS, address);
        jsonObject.put(JsonPropertyName.TRANSFERRED_BYTES, transferredSize);
        jsonObject.put(JsonPropertyName.TOTAL_BYTES, fileSize);
        jsonObject.put(JsonPropertyName.FILE_NAME, this.fileName);
        bus.publish(VertxConstant.TRANSFER_EVENTBUS_NAME, jsonObject.encode());

        return true;
    }

    @Override
    public void end() {
        transferredSize = 0;
        JsonObject jsonObject = new JsonObject();
        jsonObject.put(JsonPropertyName.STATUS, "done").put("address", address);
        jsonObject.put(JsonPropertyName.FILE, fileName);
        bus.publish(VertxConstant.TRANSFER_EVENTBUS_NAME, jsonObject.encode());
    }
}
