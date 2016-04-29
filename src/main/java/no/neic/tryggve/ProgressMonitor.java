package no.neic.tryggve;

import com.jcraft.jsch.SftpProgressMonitor;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

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
        jsonObject.put("status", "transferring");
        jsonObject.put("transferred_bytes", transferredSize);
        jsonObject.put("total_bytes", fileSize);
        bus.publish(address, jsonObject.encode());

        return true;
    }

    @Override
    public void end() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("status", "done");
        bus.publish(address, jsonObject.encode());
    }
}
