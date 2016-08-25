package no.neic.tryggve;

import com.jcraft.jsch.SftpProgressMonitor;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import no.neic.tryggve.constants.JsonPropertyName;
import no.neic.tryggve.constants.TransferStatus;

public final class ProgressMonitor implements SftpProgressMonitor {
    private long fileSize;
    private String fileName;
    private long transferredSize = 0;
    private ServerWebSocket serverWebSocket;

    public ProgressMonitor(ServerWebSocket serverWebSocket) {
        this.serverWebSocket = serverWebSocket;
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
        jsonObject.put(JsonPropertyName.STATUS, TransferStatus.TRANSFERRING);
        jsonObject.put(JsonPropertyName.TRANSFERRED_BYTES, transferredSize);
        jsonObject.put(JsonPropertyName.TOTAL_BYTES, fileSize);
        jsonObject.put(JsonPropertyName.FILE_NAME, this.fileName);
        serverWebSocket.writeFinalTextFrame(jsonObject.encode());
        return true;
    }

    @Override
    public void end() {
        transferredSize = 0;
        JsonObject jsonObject = new JsonObject();
        jsonObject.put(JsonPropertyName.STATUS, TransferStatus.DONE);
        jsonObject.put(JsonPropertyName.FILE, fileName);
        serverWebSocket.writeFinalTextFrame(jsonObject.encode());
    }
}
