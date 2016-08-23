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
import no.neic.tryggve.constants.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.util.Arrays;

/**
 * This is used to handle data upload. We couldn't allow data upload through http post request. Because the data uploaded through post request will be temporarily kept
 * in the host where the sftpbeamer is running. For security reason, we want to avoid that situation.
 *
 * Because of Vertx's limitation, we couldn't put this function into HttpVerticle. So, we create a separate verticle.
 */
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

            if (httpServerRequest.method().equals(HttpMethod.PUT) && httpServerRequest.path().startsWith(UrlPath.SFTP_UPLOAD)) {
                String fileName = httpServerRequest.getHeader("Content-Disposition").split("filename=")[1]; //get the name of a uploaded file
                fileName = fileName.substring(1, fileName.length() - 1).replace("%20", " "); // convert %20 to empty space

                /**
                 * The reference number is generated when a user wants to start to upload data. The UploadVerticle is using this number to determine which sftp connection
                 * it can use.
                 */
                String reference = httpServerRequest.getHeader("Reference");

                /**
                 * This parameter represents where the data should be uploaded
                 */
                String path = httpServerRequest.getParam(UrlParam.PATH);


                LocalMap<String, JsonObject> localMap = vertx.sharedData().getLocalMap(VertxConstant.UPLOAD_LOCALMAP_NAME);
                JsonObject jsonObject = localMap.get(reference);
                String sessionId = jsonObject.getString(JsonPropertyName.SESSION_ID);

                String source = jsonObject.getString(JsonPropertyName.SOURCE);

                try {
                    ChannelSftp channelSftp = SftpConnectionManager.getManager().getSftpConnection(sessionId, source);

                    try {
                        OutputStream ops = channelSftp.put(StringUtils.join(path, FileSystems.getDefault().getSeparator(), fileName));
                        httpServerRequest.handler(buffer -> {
                            try {
                                ops.write(buffer.getBytes());
                            } catch (IOException e) {
                                logger.error(e);
                                try {
                                    ops.close();
                                } catch (IOException e1) {
                                }
                                if (channelSftp.isConnected()) {
                                    channelSftp.disconnect();
                                }
                            }
                        }).endHandler(aVoid -> {
                            try {
                                ops.flush();
                                ops.close();
                            } catch (IOException e) {
                            }
                            if (channelSftp.isConnected()) {
                                channelSftp.disconnect();
                            }
                            httpServerRequest.response().end();
                        });
                    } catch (SftpException e) {
                        if (channelSftp.isConnected()) {
                            channelSftp.disconnect();
                        }
                    }

                } catch (JSchException e) {
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
