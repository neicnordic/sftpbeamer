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
import no.neic.tryggve.constants.VertxConstant;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.Optional;

public final class UploadVerticle extends AbstractVerticle{
    private static final Logger logger = LoggerFactory.getLogger(UploadVerticle.class);

    @Override
    public void start() throws Exception {
        HttpServer httpServer = vertx.createHttpServer();
        httpServer.requestHandler(httpServerRequest -> {
            String schema;
            if (Config.valueOf(ConfigName.SSL).equals("true")) {
                schema = "https";
            } else {
                schema = "http";
            }
            httpServerRequest.response().headers().add("Access-Control-Allow-Origin", schema + "://" + Config.valueOf(ConfigName.HOST) + ":" + Config.valueOf(ConfigName.HTTP_VERTICLE_PORT));
            httpServerRequest.response().headers().add("Access-Control-Allow-Methods", "PUT");
            httpServerRequest.response().headers().add("Access-Control-Allow-Headers", Arrays.<String>asList(new String[]{"Content-Type", "Content-Range", "Content-Disposition", "Content-Description", "Reference"}));

            if (httpServerRequest.method().equals(HttpMethod.PUT) && httpServerRequest.path().startsWith("/upload")) {
                String fileName = httpServerRequest.getHeader("Content-Disposition").split("filename=")[1];
                fileName = fileName.substring(1, fileName.length() - 1);

                String reference = httpServerRequest.getHeader("Reference");

                String path = httpServerRequest.getParam("Path");


                SharedData sharedData = vertx.sharedData();
                LocalMap<String, JsonObject> localMap = sharedData.getLocalMap(VertxConstant.UPLOAD_LOCALMAP_NAME);
                JsonObject jsonObject = localMap.get(reference);
                String sessionId = jsonObject.getString("session_id");

                String source = jsonObject.getString("source");




                try {
                    Optional<ChannelSftp> optional = SftpConnectionManager.getManager().getSftpConnection(sessionId, source);

                    OutputStream ops = optional.get().put(path + FileSystems.getDefault().getSeparator() + fileName);

                    httpServerRequest.handler(buffer -> {
                        try {
                            ops.write(buffer.getBytes());
                        } catch (IOException e) {
                            logger.error(e);
                        }
                    }).endHandler(aVoid -> {
                        try {
                            ops.close();
                        } catch (IOException e) {}
                        httpServerRequest.response().end();
                    });
                } catch (SftpException e) {
                    logger.error(e);
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
