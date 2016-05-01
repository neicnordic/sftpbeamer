package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import no.neic.tryggve.constants.HostName;
import no.neic.tryggve.constants.JsonName;
import no.neic.tryggve.constants.UrlParam;

import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Vector;

public final class HttpVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(HttpVerticle.class);

    @Override
    public void start() throws Exception {

        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);

        router.route().handler(CookieHandler.create());

        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

        router.route("/sftp/*").handler(BodyHandler.create());

        router.post("/sftp/login")
                .consumes("*/json").produces("application/json")
                .blockingHandler(routingContext -> {

                    JsonObject requestJsonBody = routingContext.getBodyAsJson();
                    String userName = requestJsonBody.getString(JsonName.USERNAME);
                    String otc = requestJsonBody.getString(JsonName.OTC);
                    String password = requestJsonBody.getString(JsonName.PASSWORD);
                    String hostName = requestJsonBody.getString(JsonName.HOSTNAME);
                    String port = requestJsonBody.getString(JsonName.PORT);
                    String source = requestJsonBody.getString(JsonName.SOURCE);


                    ChannelSftp channelSftp = null;
                    try {
                        Session session = routingContext.session();
                        String sessionId = session.id();

                        SftpSessionManager sftpSessionManager = SftpSessionManager.getManager();
                        if (otc.isEmpty()) {
                            sftpSessionManager.createSftpSession(sessionId, source, userName, password, hostName, Integer.parseInt(port));
                        } else {
                            sftpSessionManager.createSftpSession(sessionId, source, userName, password, otc, hostName, Integer.parseInt(port));
                        }
                        channelSftp = sftpSessionManager.openSftpChannel(sessionId, source);

                        String homePath;
                        if (hostName.equals(HostName.TSD)) {
                            homePath = FileSystems.getDefault().getSeparator() + userName.split("-")[0];
                        } else if (hostName.equals(HostName.MOSLER)) {
                            homePath = FileSystems.getDefault().getSeparator();
                        } else {
                            homePath = channelSftp.getHome();
                        }

                        Vector<ChannelSftp.LsEntry> entryVector = channelSftp.ls(homePath);
                        List<List<String>> entryList = new ArrayList<>(entryVector.size());
                        entryVector.stream().filter(entry -> !entry.getFilename().startsWith(".")).forEach(entry -> {
                            List<String> item = new ArrayList<>(3);
                            item.add(entry.getFilename());
                            item.add(String.valueOf(entry.getAttrs().getSize()));
                            if (entry.getAttrs().isDir()) {
                                item.add("folder");
                            } else {
                                item.add("file");
                            }
                            entryList.add(item);
                        });
                        JsonObject responseJson = new JsonObject();
                        responseJson.put(JsonName.DATA, new JsonArray(entryList));
                        responseJson.put(JsonName.HOME, homePath);
                        routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(responseJson.encode());
                    } catch (JSchException | SftpException e) {
                        e.printStackTrace();
                        routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
                    } finally {
                        if (channelSftp != null && channelSftp.isConnected()) {
                            channelSftp.disconnect();
                        }
                    }
                }, false);


        router.post( "/sftp/transfer")
                .consumes("*/json").produces("application/json")
                .handler(routingContext -> {
                    JsonObject requestJsonBody = routingContext.getBodyAsJson();
                    String fromPath = requestJsonBody.getJsonObject("from").getString("path");
                    String toPath = requestJsonBody.getJsonObject("to").getString("path");
                    JsonObject data = requestJsonBody.getJsonObject("from").getJsonObject("data");
                    String messageAddress = requestJsonBody.getString("address");

                    Session session = routingContext.session();
                    String sessionId = session.id();


                    vertx.executeBlocking(future -> {
                        EventBus bus = vertx.eventBus();
                        ChannelSftp channelSftpFrom = null;
                        ChannelSftp channelSftpTo = null;

                        try {
                            channelSftpFrom = SftpSessionManager.getManager().openSftpChannel(sessionId, requestJsonBody.getJsonObject("from").getString("name"));
                            channelSftpTo = SftpSessionManager.getManager().openSftpChannel(sessionId, requestJsonBody.getJsonObject("to").getString("name"));

                            FolderNode root = new FolderNode();
                            root.folderName = fromPath;

                            for (Object fileName : data.getJsonArray("file")) {
                                root.fileNodeList.add(fileName.toString());
                            }


                            FolderNode folderNode = null;
                            for (Object folderName : data.getJsonArray("folder")) {
                                String path = fromPath + FileSystems.getDefault().getSeparator() + folderName;
                                folderNode = Utils.assembleFolderInfo(channelSftpFrom, path, folderName.toString());
                                if (folderNode != null) {
                                    root.folderNodeList.add(folderNode);
                                }
                            }
                            root.transfer(channelSftpFrom, fromPath, channelSftpTo, toPath, new ProgressMonitor(bus, messageAddress), bus, messageAddress);
                        } catch (JSchException e) {

                        }
                    }, false, result -> {});

                    JsonObject jsonObject = new JsonObject();
                    jsonObject.put("result", "ok");
                    routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(jsonObject.encode());
                });

        router.get("/sftp/list").produces("application/json").blockingHandler(routingContext -> {
            String path = routingContext.request().getParam(UrlParam.PATH);
            String source = routingContext.request().getParam(UrlParam.SOURCE);
            ChannelSftp channelSftp = null;

            Session session = routingContext.session();
            String sessionId = session.id();

            try {
                channelSftp = SftpSessionManager.getManager().openSftpChannel(sessionId, source);

                Vector<ChannelSftp.LsEntry> entryVector = channelSftp.ls(path);
                List<List<String>> entryList = new ArrayList<>(entryVector.size());
                entryVector.stream().filter(entry -> !entry.getFilename().startsWith(".")).forEach(entry -> {
                    List<String> item = new ArrayList<>(3);
                    item.add(entry.getFilename());
                    item.add(String.valueOf(entry.getAttrs().getSize()));
                    if (entry.getAttrs().isDir()) {
                        item.add("folder");
                    } else {
                        item.add("file");
                    }
                    entryList.add(item);
                });
                JsonObject responseJson = new JsonObject();
                responseJson.put(JsonName.DATA, new JsonArray(entryList));
                responseJson.put(JsonName.PATH, path);
                routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(responseJson.encode());
            } catch (JSchException | SftpException e) {
                e.printStackTrace();
                routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
            }
        }, false);

        router.get("/sftp/upload").produces("text/plain").handler(routingContext -> {
            String source = routingContext.request().getParam(UrlParam.SOURCE);
            String sessionId = routingContext.session().id();
            System.out.println("Session Id " + sessionId);

            SharedData sharedData = vertx.sharedData();
            LocalMap<String, JsonObject> localMap = sharedData.getLocalMap("upload");
            String uuid = UUID.randomUUID().toString();


            System.out.println("Generated reference " + uuid);
            JsonObject jsonObject = new JsonObject();
            jsonObject.put("source", source);
            jsonObject.put("session_id", sessionId);
            localMap.put(uuid, jsonObject);

            routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(uuid);
        });

        router.post("/sftp/delete").produces("application/json").blockingHandler(routingContext -> {
            JsonObject requestJsonBody = routingContext.getBodyAsJson();
            String path = requestJsonBody.getString("path");
            String source = requestJsonBody.getString("source");
            JsonArray data = requestJsonBody.getJsonArray("data");

            Session session = routingContext.session();
            String sessionId = session.id();

            ChannelSftp channelSftp = null;
            try {
                channelSftp = SftpSessionManager.getManager().openSftpChannel(sessionId, source);

                JsonObject item;
                for (Object object : data) {
                    item = (JsonObject) object;
                    if (item.getString("type").equals("file")) {
                        channelSftp.rm(path + FileSystems.getDefault().getSeparator() + item.getString("name"));
                    } else {
                        deleteFolder(path + FileSystems.getDefault().getSeparator() + item.getString("name"), channelSftp);
                    }
                }
                routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();

            } catch (JSchException | SftpException e) {
                e.printStackTrace();
                JsonObject ex = new JsonObject();
                ex.put("exception", e.getMessage());
                routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(ex.encode());
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
            }
        }, false);

        router.delete("/sftp/disconnect").produces("application/json").handler(routingContext -> {
            String source = routingContext.request().getParam(UrlParam.SOURCE);

            Session session = routingContext.session();
            String sessionId = session.id();
            SftpSessionManager.getManager().disconnectSftp(sessionId, source);
            routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();

        });

        router.route().handler(StaticHandler.create());
        router.route("/static/*").handler(StaticHandler.create());

        httpServer.requestHandler(router::accept).listen(8080);

    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }

    private void deleteFolder(String folderPath, ChannelSftp channelSftp) throws SftpException{
        Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(folderPath);
        for (ChannelSftp.LsEntry entry : entries) {
            if (!entry.getFilename().startsWith(".")) {
                if (entry.getAttrs().isDir()) {
                    deleteFolder(folderPath + FileSystems.getDefault().getSeparator() + entry.getFilename(), channelSftp);
                } else {
                    channelSftp.rm(folderPath + FileSystems.getDefault().getSeparator() + entry.getFilename());
                }
            }
        }
        channelSftp.rmdir(folderPath);
    }
}
