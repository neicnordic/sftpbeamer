package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import no.neic.tryggve.constants.HostName;
import no.neic.tryggve.constants.JsonName;
import no.neic.tryggve.constants.UrlParam;
import no.neic.tryggve.constants.VertxConstant;

import java.io.*;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Vector;

public final class HttpRequestFacade {
    private static final Logger logger = LoggerFactory.getLogger(HttpRequestFacade.class);

    public static void fetchInfoHandler(RoutingContext routingContext) {
        String appInfo = "./app.info.json";
        File file = new File(appInfo);
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try {
            if (file.exists()) {
                br = new BufferedReader(new FileReader(appInfo));
            } else {
                br = new BufferedReader(new InputStreamReader(HttpRequestFacade.class.getClassLoader().getResourceAsStream("app.info.json")));
            }
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(sb.toString());
        } catch(IOException e) {
            logger.error(e);
            routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {}
            }
        }
    }

    public static void loginHandler(RoutingContext routingContext) {
        JsonObject requestJsonBody = routingContext.getBodyAsJson();
        String userName = requestJsonBody.getString(JsonName.USERNAME);
        String otc = requestJsonBody.getString(JsonName.OTC);
        String password = requestJsonBody.getString(JsonName.PASSWORD);
        String hostName = requestJsonBody.getString(JsonName.HOSTNAME);
        String port = requestJsonBody.getString(JsonName.PORT);
        String source = requestJsonBody.getString(JsonName.SOURCE);


        try {
            Session session = routingContext.session();
            String sessionId = session.id();

            SftpConnectionManager sftpSessionManager = SftpConnectionManager.getManager();
            if (otc.isEmpty()) {
                sftpSessionManager.createSftpConnection(sessionId, source, userName, password, hostName, Integer.parseInt(port));
            } else {
                sftpSessionManager.createSftpConnection(sessionId, source, userName, password, otc, hostName, Integer.parseInt(port));
            }
            Optional<ChannelSftp> optional = sftpSessionManager.getSftpConnection(sessionId, source);

            String homePath;
            if (hostName.equals(HostName.TSD)) {
                homePath = FileSystems.getDefault().getSeparator() + userName.split("-")[0];
            } else if (hostName.equals(HostName.MOSLER)) {
                homePath = FileSystems.getDefault().getSeparator();
            } else {
                homePath = optional.get().getHome();
            }

            Vector<ChannelSftp.LsEntry> entryVector = optional.get().ls(homePath);
            List<List<String>> entryList = Utils.assembleFolderContent(entryVector);
            JsonObject responseJson = new JsonObject();
            responseJson.put(JsonName.DATA, new JsonArray(entryList));
            responseJson.put(JsonName.HOME, homePath);
            routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(responseJson.encode());
        } catch (JSchException | SftpException e) {
            logger.error(e);
            routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        }
    }

    public static void transferHandler(RoutingContext routingContext) {
        JsonObject requestJsonBody = routingContext.getBodyAsJson();
        String fromPath = requestJsonBody.getJsonObject("from").getString("path");
        String toPath = requestJsonBody.getJsonObject("to").getString("path");
        JsonObject data = requestJsonBody.getJsonObject("from").getJsonObject("data");
        String messageAddress = requestJsonBody.getString("address");

        Session session = routingContext.session();
        String sessionId = session.id();


        routingContext.vertx().executeBlocking(future -> {
            EventBus bus = routingContext.vertx().eventBus();


            Optional<ChannelSftp> channelSftpFrom = SftpConnectionManager.getManager().getSftpConnection(sessionId, requestJsonBody.getJsonObject("from").getString("name"));
            Optional<ChannelSftp> channelSftpTo = SftpConnectionManager.getManager().getSftpConnection(sessionId, requestJsonBody.getJsonObject("to").getString("name"));

            FolderNode root = new FolderNode();
            root.folderName = fromPath;

            for (Object fileName : data.getJsonArray(JsonName.FILE)) {
                root.fileNodeList.add(fileName.toString());
            }


            FolderNode folderNode = null;
            for (Object folderName : data.getJsonArray(JsonName.FOLDER)) {
                String path = fromPath + FileSystems.getDefault().getSeparator() + folderName;
                folderNode = Utils.assembleFolderInfo(channelSftpFrom.get(), path, folderName.toString());
                if (folderNode != null) {
                    root.folderNodeList.add(folderNode);
                }
            }
            root.transfer(channelSftpFrom.get(), fromPath, channelSftpTo.get(), toPath, new ProgressMonitor(bus, messageAddress), bus, messageAddress);
            bus.publish(VertxConstant.TRANSFER_EVENTBUS_NAME, new JsonObject().put(JsonName.STATUS, "done").put(JsonName.ADDRESS, messageAddress).encode());

        }, false, result -> {});

        JsonObject jsonObject = new JsonObject();
        jsonObject.put("result", "ok");
        routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(jsonObject.encode());
    }

    public static void listHandler(RoutingContext routingContext) {
        String path = routingContext.request().getParam(UrlParam.PATH);
        String source = routingContext.request().getParam(UrlParam.SOURCE);

        Session session = routingContext.session();
        String sessionId = session.id();

        try {
            Optional<ChannelSftp> optional = SftpConnectionManager.getManager().getSftpConnection(sessionId, source);

            Vector<ChannelSftp.LsEntry> entryVector = optional.get().ls(path);
            List<List<String>> entryList = Utils.assembleFolderContent(entryVector);
            JsonObject responseJson = new JsonObject();
            responseJson.put(JsonName.DATA, new JsonArray(entryList));
            responseJson.put(JsonName.PATH, path);
            routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(responseJson.encode());
        } catch (SftpException e) {
            logger.error(e);
            routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        }
    }

    public static void getReferenceHandler(RoutingContext routingContext) {
        String source = routingContext.request().getParam(UrlParam.SOURCE);
        String sessionId = routingContext.session().id();


        SharedData sharedData = routingContext.vertx().sharedData();
        LocalMap<String, JsonObject> localMap = sharedData.getLocalMap(VertxConstant.UPLOAD_LOCALMAP_NAME);
        String uuid = UUID.randomUUID().toString();


        JsonObject jsonObject = new JsonObject();
        jsonObject.put("source", source);
        jsonObject.put("session_id", sessionId);
        localMap.put(uuid, jsonObject);

        routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(uuid);
    }

    public static void deleteReferenceHandler(RoutingContext routingContext) {
        String reference = routingContext.getBodyAsString();
        SharedData sharedData = routingContext.vertx().sharedData();
        LocalMap<String, JsonObject> localMap = sharedData.getLocalMap(VertxConstant.UPLOAD_LOCALMAP_NAME);

        localMap.remove(reference);
        routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();
    }

    public static void deleteHandler(RoutingContext routingContext) {
        JsonObject requestJsonBody = routingContext.getBodyAsJson();
        String path = requestJsonBody.getString("path");
        String source = requestJsonBody.getString("source");
        JsonArray data = requestJsonBody.getJsonArray("data");

        Session session = routingContext.session();
        String sessionId = session.id();

        try {
            Optional<ChannelSftp> optional = SftpConnectionManager.getManager().getSftpConnection(sessionId, source);

            JsonObject item;
            for (Object object : data) {
                item = (JsonObject) object;
                if (item.getString("type").equals("file")) {
                    optional.get().rm(path + FileSystems.getDefault().getSeparator() + item.getString("name"));
                } else {
                    Utils.deleteFolder(path + FileSystems.getDefault().getSeparator() + item.getString("name"), optional.get());
                }
            }
            routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();

        } catch (SftpException e) {
            logger.error(e);
            routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        }
    }

    public static void disconnectHandler(RoutingContext routingContext) {
        String source = routingContext.request().getParam(UrlParam.SOURCE);
        Session session = routingContext.session();
        String sessionId = session.id();
        if (source == null || source.isEmpty()) {
            SftpConnectionManager.getManager().disconnectSftp(sessionId);
        } else {
            SftpConnectionManager.getManager().disconnectSftp(sessionId, source);
        }

        routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();
    }
}
