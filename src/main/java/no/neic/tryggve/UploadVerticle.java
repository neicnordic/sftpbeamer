package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import no.neic.tryggve.constants.ConfigName;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.util.Arrays;

public final class UploadVerticle extends AbstractVerticle{
    private static final Logger logger = LoggerFactory.getLogger(UploadVerticle.class);

    @Override
    public void start() throws Exception {
        HttpServer httpServer = vertx.createHttpServer();
        httpServer.requestHandler(httpServerRequest -> {
            httpServerRequest.response().headers().add("Access-Control-Allow-Origin", "http://" + Config.valueOf(ConfigName.HOST) + ":8080");
            httpServerRequest.response().headers().add("Access-Control-Allow-Methods", "PUT");
            httpServerRequest.response().headers().add("Access-Control-Allow-Headers", Arrays.<String>asList(new String[]{"Content-Type", "Content-Range", "Content-Disposition", "Content-Description", "Reference"}));

            if (httpServerRequest.method().equals(HttpMethod.PUT) && httpServerRequest.path().startsWith("/upload")) {
                String fileName = httpServerRequest.getHeader("Content-Disposition").split("filename=")[1];
                fileName = fileName.substring(1, fileName.length() - 1);
                System.out.println("File Name " + fileName);
                String reference = httpServerRequest.getHeader("Reference");
                System.out.println("Reference " + reference);
                String path = httpServerRequest.getParam("Path");
                System.out.println("Path " + path);

                SharedData sharedData = vertx.sharedData();
                LocalMap<String, JsonObject> localMap = sharedData.getLocalMap("upload");
                JsonObject jsonObject = localMap.get(reference);
                String sessionId = jsonObject.getString("session_id");
                System.out.println("Session Id " + sessionId);
                String source = jsonObject.getString("source");
                localMap.remove(reference);
                System.out.println("Source " + source);
                ChannelSftp channelSftp = null;


                try {
                    channelSftp = SftpSessionManager.getManager().openSftpChannel(sessionId, source);
                    ChannelSftp temp = channelSftp;
                    OutputStream ops = temp.put(path + FileSystems.getDefault().getSeparator() + fileName);

                    httpServerRequest.handler(buffer -> {
                        try {
                            ops.write(buffer.getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).endHandler(aVoid -> {
                        if (temp.isConnected()) {
                            temp.disconnect();
                        }
                        httpServerRequest.response().end();
                    });
                } catch (JSchException | SftpException e) {
                    e.printStackTrace();
                    logger.error(e);
                    if (channelSftp != null && channelSftp.isConnected()) {
                        channelSftp.disconnect();
                    }
                }

            } else {
                httpServerRequest.response().end();
            }
        }).listen(Integer.parseInt(Config.valueOf(ConfigName.UPLOAD_VERTICLE_PORT)),
                Config.valueOf(ConfigName.HOST));
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }
}
