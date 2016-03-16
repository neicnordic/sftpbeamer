package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import no.neic.tryggve.constants.JsonName;
import no.neic.tryggve.constants.UrlParam;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public final class HttpVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);

        router.route().handler(CookieHandler.create());

        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

        router.route().handler(BodyHandler.create());

        router.route(HttpMethod.POST, "/login")
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
                        com.jcraft.jsch.Session jschSession;
                        if (otc.isEmpty()) {
                            jschSession = Utils.createJschSession(userName, password, hostName, Integer.parseInt(port));
                        } else {
                            jschSession = Utils.createJschSession(userName, password, otc, hostName, Integer.parseInt(port));
                        }
                        session.put(source, jschSession);
                        channelSftp = (ChannelSftp) jschSession.openChannel("sftp");
                        channelSftp.setBulkRequests(64);
                        channelSftp.connect();

                        Vector<ChannelSftp.LsEntry> entryVector = channelSftp.ls(FileSystems.getDefault().getSeparator());
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

//        router.route(HttpMethod.POST, "/transfer")
//                .consumes("*/json").produces("application/json")
//                .blockingHandler(routingContext -> {
//                    JsonObject requestJsonBody = routingContext.getBodyAsJson();
//                    String fromPath = requestJsonBody.getJsonObject("from").getString("path");
//                    String toPath = requestJsonBody.getJsonObject("to").getString("path");
//                    JsonArray dataArray = requestJsonBody.getJsonObject("from").getJsonArray("data");
//
//                    ChannelSftp channelSftpFrom = null;
//                    ChannelSftp channelSftpTo = null;
//
//                    Session session = routingContext.session();
//
//                    try {
//                        channelSftpFrom = Utils.createSftpChannel(session.<com.jcraft.jsch.Session>get(requestJsonBody.getJsonObject("from").getString("name")));
//                        channelSftpTo = Utils.createSftpChannel(session.<com.jcraft.jsch.Session>get(requestJsonBody.getJsonObject("to").getString("name")));
//                        JsonObject jsonObject;
//                        for (Object item : dataArray) {
//                            jsonObject = (JsonObject) item;
//                            if (jsonObject.getString("type").equals("file")) {
//                                Utils.transferFile(channelSftpFrom, channelSftpTo, fromPath, toPath, jsonObject.getString("name"));
//                            }
//                        }
//                        routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();
//                    } catch (JSchException | SftpException | IOException e) {
//                        e.printStackTrace();
//                        routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
//                    } finally {
//                        if (channelSftpFrom != null && channelSftpFrom.isConnected()) {
//                            channelSftpFrom.disconnect();
//                        }
//                        if (channelSftpTo != null && channelSftpTo.isConnected()) {
//                            channelSftpTo.disconnect();
//                        }
//                    }
//
//
//                }, false);

        router.route(HttpMethod.POST, "/transfer")
                .consumes("*/json").produces("application/json")
                .handler(routingContext -> {
                    JsonObject requestJsonBody = routingContext.getBodyAsJson();
                    String fromPath = requestJsonBody.getJsonObject("from").getString("path");
                    String toPath = requestJsonBody.getJsonObject("to").getString("path");
                    JsonArray dataArray = requestJsonBody.getJsonObject("from").getJsonArray("data");
                    String messageAddress = requestJsonBody.getString("address");
                    EventBus bus = vertx.eventBus();

                    vertx.executeBlocking(future -> {
                        ChannelSftp channelSftpFrom = null;
                        ChannelSftp channelSftpTo = null;

                        Session session = routingContext.session();

                        try {
                            channelSftpFrom = Utils.createSftpChannel(session.<com.jcraft.jsch.Session>get(requestJsonBody.getJsonObject("from").getString("name")));
                            channelSftpTo = Utils.createSftpChannel(session.<com.jcraft.jsch.Session>get(requestJsonBody.getJsonObject("to").getString("name")));
                            JsonObject jsonObject;
                            for (Object item : dataArray) {
                                jsonObject = (JsonObject) item;
                                if (jsonObject.getString("type").equals("file")) {
                                    Utils.transferFile(channelSftpFrom, channelSftpTo, fromPath, toPath, jsonObject.getString("name"), new ProgressMonitor(bus, messageAddress));
                                }
                            }
                        } catch (JSchException | SftpException | IOException e) {
                            e.printStackTrace();
                        } finally {
                            if (channelSftpFrom != null && channelSftpFrom.isConnected()) {
                                channelSftpFrom.disconnect();
                            }
                            if (channelSftpTo != null && channelSftpTo.isConnected()) {
                                channelSftpTo.disconnect();
                            }
                        }
                    }, false, result -> {});
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.put("result", "ok");
                    routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(jsonObject.encode());

                });

        router.get("/list").produces("application/json").blockingHandler(routingContext -> {
            String path = routingContext.request().getParam(UrlParam.PATH);
            String source = routingContext.request().getParam(UrlParam.SOURCE);
            ChannelSftp channelSftp = null;

            Session session = routingContext.session();

            try {
                channelSftp = Utils.createSftpChannel(session.<com.jcraft.jsch.Session>get(source));

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

        router.post("/upload").blockingHandler(routingContext -> {
            String source = routingContext.request().getParam(UrlParam.SOURCE);
            String destination = routingContext.request().getParam(UrlParam.PATH);

            routingContext.request().setExpectMultipart(true);
            routingContext.request().uploadHandler(upload -> {
                String absoluteFilePath = destination + upload.filename();
                ChannelSftp channelSftp = null;
                try {
                    channelSftp = Utils.createSftpChannel(routingContext.session().<com.jcraft.jsch.Session>get(source));
                    OutputStream ops = channelSftp.put(absoluteFilePath);
                    upload.exceptionHandler(cause -> routingContext.request().response().setChunked(true).end("Upload failed"));
                    upload.endHandler(v -> routingContext.request().response().setChunked(true).end("Successfully uploaded to " + upload.filename()));
                    upload.handler(buffer -> {
                        try {
                            ops.write(buffer.getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (JSchException | SftpException e) {
                    e.printStackTrace();
                    routingContext.request().response().setChunked(true).end(e.getMessage());

                } finally {
                    if (channelSftp != null && channelSftp.isConnected()) {
                        channelSftp.disconnect();
                    }
                }
            });
        }, false);

        router.route().handler(StaticHandler.create());
        router.route("/static/*").handler(StaticHandler.create());

        httpServer.requestHandler(router::accept).listen(8080);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }
}
