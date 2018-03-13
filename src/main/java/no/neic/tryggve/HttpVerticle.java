package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import no.neic.tryggve.constants.ConfigName;
import no.neic.tryggve.constants.JsonKey;
import no.neic.tryggve.constants.TransferStatus;
import no.neic.tryggve.constants.UrlPath;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.core.MediaType;
import java.nio.file.FileSystems;

public final class HttpVerticle extends AbstractVerticle {

    private static final String TRANSFER_LOCALMAP_NAME = "transfer";
    private static Logger logger = LoggerFactory.getLogger(HttpVerticle.class);


    @Override
    public void start() throws Exception {

        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setUsePooledBuffers(true);
        HttpServer httpServer = vertx.createHttpServer(httpServerOptions);
        Router router = Router.router(vertx);

        router.route().handler(CookieHandler.create());

        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

        router.route("/sftp/*").handler(BodyHandler.create());

        router.put(UrlPath.SFTP_UPLOAD).blockingHandler(HttpRequestHandler::chunkUploadHandler, false);

        router.get(UrlPath.SFTP_INFO).produces(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::fetchInfoHandler);

        router.post(UrlPath.SFTP_LOGIN).consumes(MediaType.APPLICATION_JSON).produces(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestHandler::loginHandler, false);

        router.post(UrlPath.SFTP_TRANSFER_PREPARE).consumes(MediaType.APPLICATION_JSON).produces(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::transferPrepareHandler);

        router.post(UrlPath.SFTP_TRANSFER_ASYNC).consumes(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestHandler::asyncTransferHandler, false);

        router.get(UrlPath.SFTP_LIST).produces(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestHandler::listHandler, false);

        router.post(UrlPath.SFTP_FILE_CHECK).consumes(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::checkFileHandler);

        router.delete(UrlPath.SFTP_DELETE).consumes(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestHandler::deleteHandler, false);

        router.delete(UrlPath.SFTP_DISCONNECT).produces(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::disconnectHandler);

        router.post(UrlPath.SFTP_CREATE).consumes(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::createFolderHandler);

        router.post(UrlPath.SFTP_RENAME).consumes(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::renameHandler);

        router.get(UrlPath.SFTP_DOWNLOAD).blockingHandler(HttpRequestHandler::downloadHandler, false);

        router.get(UrlPath.SFTP_ZIP).blockingHandler(HttpRequestHandler::downloadZipHandler, false);

        router.get(UrlPath.SFTP_DOWNLOAD_CHECK).handler(HttpRequestHandler::downloadCheckHandler);

        router.route(UrlPath.SFTP_WS).handler(this::webSocketHandler);

        router.route().handler(StaticHandler.create());
        router.route("/static/*").handler(StaticHandler.create());

        httpServer.requestHandler(router::accept).listen(
                Integer.parseInt(Config.valueOf(ConfigName.HTTP_VERTICLE_PORT)),
                Config.valueOf(ConfigName.HOST));

    }

    /**
     * Once the data transfer between two hosts is started, the web socket connection is established, and is used to
     * send transfer progress to client.
     */
    private void webSocketHandler(RoutingContext routingContext) {
        ServerWebSocket serverWebSocket = routingContext.request().upgrade();

        routingContext.vertx().sharedData().<String, String>getLocalMap(TRANSFER_LOCALMAP_NAME).put(serverWebSocket.textHandlerID(), routingContext.session().id());

        serverWebSocket.handler(buffer -> {
            String sessionId = vertx.sharedData().<String, String>getLocalMap(TRANSFER_LOCALMAP_NAME).get(serverWebSocket.textHandlerID());

            JsonObject message = buffer.toJsonObject();
            JsonObject fromJsonObject = message.getJsonObject(JsonKey.FROM);
            JsonObject toJsonObject = message.getJsonObject(JsonKey.TO);

            String fromPath = fromJsonObject.getString(JsonKey.PATH);
            String fromName = fromJsonObject.getString(JsonKey.NAME);
            String toPath = toJsonObject.getString(JsonKey.PATH);
            String toName = toJsonObject.getString(JsonKey.NAME);
            JsonArray filesArray = message.getJsonArray(JsonKey.DATA);
            
            vertx.executeBlocking(future -> {

                ChannelSftp channelSftpFrom = null;
                ChannelSftp channelSftpTo = null;
                try {
                    channelSftpFrom = SftpSessionManager.getManager().getSftpChannel(sessionId, fromName);
                    channelSftpTo = SftpSessionManager.getManager().getSftpChannel(sessionId, toName);

                    if (channelSftpFrom == null || channelSftpTo == null) {
                        throw new JSchException();
                    }

                    SftpProgressMonitor monitor = new ProgressMonitor(serverWebSocket);
                    JsonObject jsonObject = new JsonObject();

                    logger.debug("Start to transfer data from {} to {}", fromPath, toPath);

                    for (Object object : filesArray) {
                        String fromFile = StringUtils.join(fromPath, FileSystems.getDefault().getSeparator(), object.toString());
                        String toFile = StringUtils.join(toPath, FileSystems.getDefault().getSeparator(), object.toString());
                        try {
                            jsonObject.put(JsonKey.STATUS, TransferStatus.START).put(JsonKey.FILE, fromFile);

                            serverWebSocket.writeFinalTextFrame(jsonObject.encode());

                            logger.debug("Transfer file from {} to {}", fromFile, toFile);
                            channelSftpFrom.get(fromFile, channelSftpTo.put(toFile), monitor);
                        } catch (SftpException e) {
                            logger.debug("Failed to transfer file {}", fromFile);
                            logger.error(e);
                            jsonObject.clear();
                            jsonObject.put(JsonKey.STATUS, TransferStatus.FAILED).put(JsonKey.FILE, fromFile);

                            serverWebSocket.writeFinalTextFrame(jsonObject.encode());
                        }
                        jsonObject.clear();
                    }
                    serverWebSocket.writeFinalTextFrame(new JsonObject().put(JsonKey.STATUS, TransferStatus.FINISH).encode());

                    logger.debug("Data transfer from {} to {} is done.", fromPath, toPath);
                } catch (JSchException e) {
                    logger.debug("Failed to start data transfer from {} to {}", fromPath, toPath);
                    logger.error(e);
                    serverWebSocket.writeFinalTextFrame(new JsonObject().put(JsonKey.STATUS, TransferStatus.ERROR).put(JsonKey.MESSAGE, StringUtils.isNotEmpty(e.getMessage()) ? e.getMessage() : "Internal Error").encode());
                } finally {
                    vertx.sharedData().<String, String>getLocalMap(TRANSFER_LOCALMAP_NAME).remove(serverWebSocket.textHandlerID());
                    serverWebSocket.close();
                    if (channelSftpFrom != null && channelSftpFrom.isConnected()) {
                        channelSftpFrom.disconnect();
                    }
                    if (channelSftpTo != null && channelSftpTo.isConnected()) {
                        channelSftpTo.disconnect();
                    }
                }
            }, false, result -> {});

        });
    }
}
