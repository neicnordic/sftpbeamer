package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import no.neic.tryggve.constants.ConfigName;
import no.neic.tryggve.constants.JsonKey;
import no.neic.tryggve.constants.UrlParam;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.*;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.zip.ZipOutputStream;

public final class HttpRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(HttpRequestHandler.class);

    private static final String SEPARATOR = FileSystems.getDefault().getSeparator();

    private static final String HOST1 = "host1";
    private static final String HOST2 = "host2";

    static void fetchInfoHandler(RoutingContext routingContext) {
        String appInfo = "./app.info.json";
        File file = new File(appInfo);
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try {
            if (file.exists()) {
                br = new BufferedReader(new FileReader(appInfo));
            } else {
                br = new BufferedReader(new InputStreamReader(HttpRequestHandler.class.getClassLoader().getResourceAsStream("app.info.json")));
            }
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(sb.toString());
        } catch (IOException e) {
            logger.error(e);
            routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * This method receives a request of connecting remote host. The request body looks like
     * {"username": "", "password": "", "hostname": "", "port": 12, "otc": "", "source": ""}
     */
    static void loginHandler(RoutingContext routingContext) {

        validateRequestBody(routingContext, () -> {
            JsonObject requestJsonBody = routingContext.getBodyAsJson();
            String userName = requestJsonBody.getString(JsonKey.USERNAME);
            String otc = requestJsonBody.getString(JsonKey.OTC);
            String password = requestJsonBody.getString(JsonKey.PASSWORD);
            String hostName = requestJsonBody.getString(JsonKey.HOSTNAME);
            String port = requestJsonBody.getString(JsonKey.PORT);
            String source = requestJsonBody.getString(JsonKey.SOURCE);

            validateSpecificConstraint(routingContext, () -> (source.equals(HOST1) || source.equals(HOST2)) && StringUtils.isNumeric(port), () -> {
                logger.debug("User {} connects to host {} in port {} through source {}", userName, hostName, port, source);


                Session session = routingContext.session();
                String sessionId = session.id();

                SftpSessionManager sftpSessionManager = SftpSessionManager.getManager();
                try {

                    logger.debug("User {} is Connecting to host {}", userName, hostName);
                    String otpHosts = Config.valueOf(ConfigName.OTP_HOSTS);
                    if (otpHosts.contains(hostName)) {
                        sftpSessionManager.createSftpSession(sessionId, source, userName, password, otc, hostName, Integer.parseInt(port));
                    } else {
                        sftpSessionManager.createSftpSession(sessionId, source, userName, password, hostName, Integer.parseInt(port));
                    }

                    logger.debug("User {} connects to host {} successfully", userName, hostName);
                    routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();
                } catch (JSchException e) {
                    logger.error("User {} failed to connect to host {}, because error {} happens.", userName, hostName, e.getMessage());
                    routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(e.getMessage());
                }
            });
        }, JsonKey.USERNAME, JsonKey.PASSWORD, JsonKey.HOSTNAME, JsonKey.PORT, JsonKey.SOURCE);
    }

    /**
     * This method is used to create a connection, which is dedicated to downloading a file or a folder.
     */
    static void connectHandler(RoutingContext routingContext) {
        validateRequestBody(routingContext, () -> {
            JsonObject requestJsonBody = routingContext.getBodyAsJson();
            String userName = requestJsonBody.getString(JsonKey.USERNAME);
            String otc = requestJsonBody.getString(JsonKey.OTC);
            String password = requestJsonBody.getString(JsonKey.PASSWORD);
            String hostName = requestJsonBody.getString(JsonKey.HOSTNAME);
            String port = requestJsonBody.getString(JsonKey.PORT);
            String source = requestJsonBody.getString(JsonKey.SOURCE);

            validateSpecificConstraint(routingContext, () -> (source.equals(HOST1) || source.equals(HOST2)) && StringUtils.isNumeric(port), () -> {
                String sessionId = routingContext.session().id();

                try {
                    SftpSessionManager.getManager().createDownloadSftpSession(sessionId, source, userName, password, otc, hostName, Integer.valueOf(port));

                    routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();
                } catch (JSchException e) {
                    logger.error("User {} failed to connect to host {}, because error {} happens.", userName, hostName, e.getMessage());
                    routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
                }

            });
        }, JsonKey.USERNAME, JsonKey.PASSWORD, JsonKey.HOSTNAME, JsonKey.PORT, JsonKey.SOURCE);
    }

    /**
     * This method is used to check if a file is existing or not.
     *
     */
    static void checkFileHandler(RoutingContext routingContext) {

        validateRequestBody(routingContext, () -> {
            JsonObject requestJsonBody = routingContext.getBodyAsJson();
            String source = requestJsonBody.getString(JsonKey.SOURCE);
            String path = requestJsonBody.getString(JsonKey.PATH);

            validateSpecificConstraint(routingContext, () -> source.equals(HOST1) || source.equals(HOST2), () -> {
                logger.debug("Check if file {} is existing or not.", path);

                returnResponseFunction(routingContext, channelSftp -> {
                    try {
                        channelSftp.lstat(path);
                        logger.debug("File {} is existing.", path);
                        return Pair.of(HttpResponseStatus.FOUND, Optional.empty());
                    } catch (SftpException e) {
                        logger.debug("File {} is not existing.", path);
                        return Pair.of(HttpResponseStatus.NOT_FOUND, Optional.empty());
                    }
                });
            });

        }, JsonKey.SOURCE, JsonKey.PATH);
    }

    /**
     * This method is used to receive a request of creating a new folder. The request body looks like {"source": "", "path": ""}.
     * The path parameter represents absolute path of a newly created folder.
     *
     */
    static void createFolderHandler(RoutingContext routingContext) {
        validateRequestBody(routingContext, () -> {
            JsonObject requestJsonBody = routingContext.getBodyAsJson();
            String source = requestJsonBody.getString(JsonKey.SOURCE);
            String path = requestJsonBody.getString(JsonKey.PATH);

            validateSpecificConstraint(routingContext, () -> source.equals(HOST1) || source.equals(HOST2), () -> {
                logger.debug("Source {} wants to create a new folder {}.", source, path);

                returnResponseFunction(routingContext, channelSftp -> {
                    try {
                        channelSftp.mkdir(path);
                    } catch (SftpException e) {
                        logger.debug(e);
                    }
                    logger.debug("Folder {} is created.", path);
                    return Pair.of(HttpResponseStatus.CREATED, Optional.empty());
                });
            });
        }, JsonKey.SOURCE, JsonKey.PATH);
    }

    /**
     * This method is used to receive a request of renaming a file or a folder. The request body looks like
     * {"source": "", "path": "", "old_name": "", "new_name": ""}. The path parameter represents the absolute path
     * where the file or folder you want to rename is.
     *
     */
    static void renameHandler(RoutingContext routingContext) {
        validateRequestBody(routingContext, () -> {
            JsonObject requestJsonBody = routingContext.getBodyAsJson();
            String source = requestJsonBody.getString(JsonKey.SOURCE);
            String path = requestJsonBody.getString(JsonKey.PATH);
            String old_name = requestJsonBody.getString(JsonKey.OLD_NAME);
            String new_name = requestJsonBody.getString(JsonKey.NEW_NAME);

            validateSpecificConstraint(routingContext, () -> source.equals(HOST1) || source.equals(HOST2), () -> {
                logger.debug("Source {} renames the file or folder {} under {} to {}", source, old_name, path, new_name);

                returnResponseFunction(routingContext, channelSftp -> {
                    String newPath = StringUtils.join(path, SEPARATOR, new_name);
                    SftpATTRS attrs = null;
                    try {
                        attrs = channelSftp.lstat(newPath);
                    } catch (SftpException e) {}
                    if (attrs == null) {
                        channelSftp.rename(StringUtils.join(path, SEPARATOR, old_name), newPath);
                    }
                    logger.debug("Succeed to rename the file or folder {} in {}", old_name, path);
                    return Pair.of(HttpResponseStatus.NO_CONTENT, Optional.empty());
                });
            });
        }, JsonKey.SOURCE, JsonKey.PATH, JsonKey.OLD_NAME, JsonKey.NEW_NAME);
    }

    /**
     * This method is used to receive a request of transfer data from one host to the other one in an asynchronous way. After data transfer is done, an email will be sent.
     * The request body looks like {"from": {"hostname": "", "port": 22, "username": "", "password":"", "otc": "", "path": ""},
     * "to": {"hostname": "", "port": 22, "username": "", "password":"", "otc": "", "path": ""}, "data": [], "email": "xxx@xxx.xx"}
     */
    static void asyncTransferHandler(RoutingContext routingContext) {

        validateRequestBody(routingContext, () -> {

            routingContext.vertx().<JsonObject>executeBlocking(future -> {
                JsonObject result = new JsonObject();
                JsonObject requestBody = routingContext.getBodyAsJson();

                JsonObject from = requestBody.getJsonObject(JsonKey.FROM);
                String fromHost = from.getString(JsonKey.HOSTNAME);
                int fromPort = from.getInteger(JsonKey.PORT);
                String fromUserName = from.getString(JsonKey.USERNAME);
                String fromPassword = from.getString(JsonKey.PASSWORD);
                String fromOtc = from.getString(JsonKey.OTC);
                String fromPath = from.getString(JsonKey.PATH);
                com.jcraft.jsch.Session fromSession = null;

                JsonObject to = requestBody.getJsonObject(JsonKey.TO);
                String toHost = to.getString(JsonKey.HOSTNAME);
                int toPort = to.getInteger(JsonKey.PORT);
                String toUserName = to.getString(JsonKey.USERNAME);
                String toPassword = to.getString(JsonKey.PASSWORD);
                String toOtc = to.getString(JsonKey.OTC);
                String toPath = to.getString(JsonKey.PATH);
                com.jcraft.jsch.Session toSession = null;
                try {
                    fromSession = Utils.createSftpSession(fromUserName, fromPassword, fromHost, fromPort, StringUtils.isEmpty(fromOtc) ? Optional.empty() : Optional.of(fromOtc));
                    toSession = Utils.createSftpSession(toUserName, toPassword, toHost, toPort, StringUtils.isEmpty(toOtc) ? Optional.empty() : Optional.of(toOtc));

                    try {

                        ChannelSftp fromChannel = Utils.openSftpChannel(fromSession);
                        ChannelSftp toChannel = Utils.openSftpChannel(toSession);


                        JsonArray filesArray = requestBody.getJsonArray(JsonKey.DATA);

                        JsonArray successArray = new JsonArray();
                        JsonArray failedArray = new JsonArray();

                        for (Object object : filesArray) {
                            String fromFile = StringUtils.join(fromPath, FileSystems.getDefault().getSeparator(), object.toString());
                            String toFile = StringUtils.join(toPath, FileSystems.getDefault().getSeparator(), object.toString());
                            try {
                                fromChannel.get(fromFile, toChannel.put(toFile));
                                successArray.add(fromFile);
                            } catch (SftpException e) {
                                logger.error("Failed to transfer file {}", e.getCause(), fromFile);
                                failedArray.add(fromFile);
                            }
                        }
                        result.put(JsonKey.FROM, fromHost).put(JsonKey.TO, toHost).put(JsonKey.SUCCESS, successArray).put(JsonKey.FAILED, failedArray).put(JsonKey.STATUS, "success").put(JsonKey.EMAIL, requestBody.getString(JsonKey.EMAIL));
                        future.complete(result);
                    } catch (JSchException e) {
                        logger.error("User " + toUserName + " can't login host " + toHost, e.getCause());
                        result.put(JsonKey.MESSAGE, "connecting with " + toHost + " failed");
                        result.put(JsonKey.FROM, fromHost);
                        result.put(JsonKey.DATA, requestBody.getJsonArray(JsonKey.DATA));
                        result.put(JsonKey.STATUS, "failed");
                        result.put(JsonKey.EMAIL, requestBody.getString(JsonKey.EMAIL));
                        future.complete(result);
                    }
                } catch (JSchException e) {
                    logger.error("User " + fromUserName + " can't login host " + fromHost, e.getCause());
                    result.put(JsonKey.MESSAGE, "connecting with " + fromHost + " failed");
                    result.put(JsonKey.FROM, fromHost);
                    result.put(JsonKey.DATA, requestBody.getJsonArray(JsonKey.DATA));
                    result.put(JsonKey.EMAIL, requestBody.getString(JsonKey.EMAIL));
                    result.put(JsonKey.STATUS, "failed");
                    future.complete(result);
                } finally {
                    if (fromSession != null && fromSession.isConnected()) {
                        fromSession.disconnect();
                    }
                    if (toSession != null && toSession.isConnected()) {
                        toSession.disconnect();
                    }
                }
            }, false, asyncResult -> {
                if (asyncResult.succeeded()) {
                    routingContext.vertx().executeBlocking(future -> {
                        JsonObject result = asyncResult.result();
                        try {
                            Email email = new SimpleEmail();
                            email.setHostName(Config.valueOf(ConfigName.SMTP_HOST));
                            email.setAuthenticator(new DefaultAuthenticator(Config.valueOf(ConfigName.SMTP_USER_NAME), Config.valueOf(ConfigName.SMTP_PASSWORD)));
                            if (Boolean.valueOf(Config.valueOf(ConfigName.SMTP_SSL))) {
                                email.setSSLOnConnect(true);
                                email.setStartTLSEnabled(true);
                                email.setStartTLSRequired(true);
                                email.setSslSmtpPort(Config.valueOf(ConfigName.SMTP_PORT));
                            } else {
                                email.setSmtpPort(Integer.valueOf(Config.valueOf(ConfigName.SMTP_PORT)));
                            }
                            email.setFrom(Config.valueOf(ConfigName.FROM_EMAIL));
                            email.addTo(result.getString(JsonKey.EMAIL));

                            if (result.getString(JsonKey.STATUS).equals("success")) {
                                email.setSubject("Data transfer is done");
                                String message = "";
                                if (result.getJsonArray(JsonKey.FAILED) != null && result.getJsonArray(JsonKey.FAILED).size() != 0) {
                                    message = "The following files " + StringUtils.join(result.getJsonArray(JsonKey.FAILED).getList(), ",") + " failed to be transferred from " + result.getString(JsonKey.FROM) + " to " + result.getString(JsonKey.TO) + ".";
                                }
                                if (result.getJsonArray(JsonKey.SUCCESS) != null && result.getJsonArray(JsonKey.SUCCESS).size() != 0) {
                                    message = message + " The following files " + StringUtils.join(result.getJsonArray(JsonKey.SUCCESS).getList(), ",") + " succeeded to be transferred from " + result.getString(JsonKey.FROM) + " to " + result.getString(JsonKey.TO) + ".";
                                }
                                email.setMsg(message);
                            }
                            if (result.getString(JsonKey.STATUS).equals("failed")) {
                                email.setSubject("Data transfer failed");
                                String message = "The following files " + StringUtils.join(result.getJsonArray(JsonKey.DATA), ",") + " failed to transfer, because " + result.getString(JsonKey.MESSAGE) + ".";
                                email.setMsg(message);
                            }
                            email.send();
                            logger.debug("Have sent email to {}", result.getString(JsonKey.EMAIL));
                        } catch (EmailException e) {
                            logger.error("Sending email to " + result.getString(JsonKey.EMAIL) + " failed.", e.getCause());
                        }
                    }, false, asyncResult1 -> {});
                }
            });

            routingContext.response().setStatusCode(HttpResponseStatus.CREATED.code()).end();

        }, JsonKey.FROM, JsonKey.TO, JsonKey.DATA, JsonKey.EMAIL);
    }

    /**
     * This method is used to do some preparation before starting to transfer data between two remote hosts.
     * The request body looks like {"from": {"name": "", "path": "", "data": {"file": [], "folder": []}}, "to": {"name": "", "path": ""}}.
     *
     */
    static void transferPrepareHandler(RoutingContext routingContext) {
        JsonObject requestJsonBody = routingContext.getBodyAsJson();
        JsonObject fromJsonObject = requestJsonBody.getJsonObject(JsonKey.FROM);
        JsonObject toJsonObject = requestJsonBody.getJsonObject(JsonKey.TO);

        if (fromJsonObject == null || toJsonObject == null) {
            routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
        } else {
            String fromPath = fromJsonObject.getString(JsonKey.PATH);
            String toPath = toJsonObject.getString(JsonKey.PATH);

            String fromName = fromJsonObject.getString(JsonKey.NAME);
            String toName = toJsonObject.getString(JsonKey.NAME);

            if (StringUtils.isEmpty(fromPath) || StringUtils.isEmpty(toPath) || StringUtils.isEmpty(fromName) || StringUtils.isEmpty(toName)) {
                routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
            } else {
                JsonObject data = fromJsonObject.getJsonObject(JsonKey.DATA);

                if (data == null) {
                    routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
                } else {
                    JsonArray fileArray = data.getJsonArray(JsonKey.FILE);
                    JsonArray folderArray = data.getJsonArray(JsonKey.FOLDER);

                    if ((fileArray == null || fileArray.size() == 0) && (folderArray == null || folderArray.size() == 0)) {
                        routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
                    } else {
                        Session session = routingContext.session();
                        String sessionId = session.id();

                        ChannelSftp channelSftpFrom = null;
                        ChannelSftp channelSftpTo = null;
                        try {
                            channelSftpFrom = SftpSessionManager.getManager().getSftpChannel(sessionId, fromName);
                            channelSftpTo = SftpSessionManager.getManager().getSftpChannel(sessionId, toName);


                            String existingFile = null;
                            String existingFolder = null;

                            if (fileArray != null) {
                                for (Object fileName : fileArray) {
                                    try {
                                        channelSftpTo.lstat(StringUtils.join(toPath, SEPARATOR, fileName.toString()));
                                        existingFile = fileName.toString();
                                        break;
                                    } catch (SftpException e) {
                                    }
                                }
                            }

                            if (folderArray != null) {
                                for (Object folderName : folderArray) {
                                    try {
                                        channelSftpTo.lstat(StringUtils.join(toPath, SEPARATOR, folderName.toString()));
                                        existingFolder = folderName.toString();
                                        break;
                                    } catch (SftpException e) {
                                    }
                                }
                            }

                            if (existingFile != null || existingFolder != null) {
                                JsonObject existingItems = new JsonObject();
                                if (existingFile != null) {
                                    existingItems.put(JsonKey.FILE, existingFile);
                                }
                                if (existingFolder != null) {
                                    existingItems.put(JsonKey.FOLDER, existingFolder);
                                }

                                routingContext.response().setStatusCode(HttpResponseStatus.FOUND.code()).end(existingItems.encode());
                            } else {
                                FolderNode root = new FolderNode();
                                root.folderName = fromPath;


                                if (fileArray != null) {
                                    fileArray.stream().forEach(fileName -> root.fileNodeList.add(fileName.toString()));
                                }

                                if (folderArray != null) {
                                    FolderNode folderNode;
                                    for (Object folderName : folderArray) {
                                        String path = StringUtils.join(fromPath, SEPARATOR, folderName);
                                        folderNode = Utils.assembleFolderInfo(channelSftpFrom, path, folderName.toString());
                                        if (folderNode != null) {
                                            root.folderNodeList.add(folderNode);
                                        }
                                    }
                                }

                                root.createFolder(true, channelSftpTo, toPath);

                                routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end(new JsonArray(root.getRelativeFilePathArray("")).encode());
                            }

                        } catch (JSchException e) {
                            logger.error(e);
                            routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
                        } finally {
                            if (channelSftpFrom != null && channelSftpFrom.isConnected()) {
                                channelSftpFrom.disconnect();
                            }
                            if (channelSftpTo != null && channelSftpTo.isConnected()) {
                                channelSftpTo.disconnect();
                            }
                        }
                    }
                }
            }

        }
    }

    /**
     * This method is used to list content of a folder.
     *
     */
    static void listHandler(RoutingContext routingContext) {

        String path = routingContext.request().getParam(UrlParam.PATH);
        String source = routingContext.request().getParam(UrlParam.SOURCE);

        validateSpecificConstraint(routingContext, () ->
                !StringUtils.isEmpty(path) && !StringUtils.isEmpty(source) && (source.equals(HOST1) || source.equals(HOST2)), () -> {
            logger.debug("List the content of folder {}", path);

            returnResponseFunction(routingContext, channelSftp -> {
                Vector<ChannelSftp.LsEntry> entryVector = channelSftp.ls(path);
                List<List<String>> entryList = Utils.assembleFolderContent(entryVector, channelSftp, path);
                JsonObject responseJson = new JsonObject();
                responseJson.put(JsonKey.PATH, path).put(JsonKey.DATA, new JsonArray(entryList));
                return Pair.of(HttpResponseStatus.OK, Optional.of(responseJson.encode()));
            });
        });
    }

    /**
     * This method is used to delete files or folders. The request body looks like {"source": "", "path": "", "data": []}.
     *
     */
    static void deleteHandler(RoutingContext routingContext) {
        validateRequestBody(routingContext, () -> {
            JsonObject requestJsonBody = routingContext.getBodyAsJson();
            String path = requestJsonBody.getString(JsonKey.PATH);
            String source = requestJsonBody.getString(JsonKey.SOURCE);
            JsonArray data = requestJsonBody.getJsonArray(JsonKey.DATA);

            validateSpecificConstraint(routingContext,
                    () -> (source.equals(HOST1) || source.equals(HOST2)) && (data != null) && (data.size() != 0), () -> {
                        logger.debug("Delete the data of {}", path);

                        returnResponseFunction(routingContext, channelSftp -> {
                            JsonObject item;
                            for (Object object : data) {
                                item = (JsonObject) object;
                                String str = StringUtils.join(path, SEPARATOR, item.getString(JsonKey.NAME));
                                if (item.getString(JsonKey.TYPE).equals(JsonKey.FILE)) {
                                    logger.debug("Deleting a file {}", str);
                                    channelSftp.rm(str);
                                } else {
                                    logger.debug("Deleting a folder {}", str);
                                    Utils.deleteFolder(str, channelSftp);
                                }
                            }
                            logger.debug("Succeed to delete the data of {}", path);
                            return Pair.of(HttpResponseStatus.NO_CONTENT, Optional.empty());
                        });
                    });

        }, JsonKey.PATH, JsonKey.SOURCE);
    }

    /**
     * This method is used to disconnect from the remote host and clean up the kept connection.
     *
     */
    static void disconnectHandler(RoutingContext routingContext) {
        String source = routingContext.request().getParam(UrlParam.SOURCE);
        Session session = routingContext.session();
        String sessionId = session.id();
        if (source == null || source.isEmpty()) {
            SftpSessionManager.getManager().disconnectSftp(sessionId);
        } else {
            SftpSessionManager.getManager().disconnectSftp(sessionId, source);
        }

        routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();
    }

    static void downloadCheckHandler(RoutingContext routingContext) {
        String source = routingContext.request().getParam(UrlParam.SOURCE);
        String sessionId = routingContext.session().id();

        if (SftpSessionManager.getManager().isDownloadSessionExisting(sessionId, source)) {
            if (DownloadCounter.getCounter().checkIfBusy(sessionId + source)) {
                routingContext.response().setStatusCode(HttpResponseStatus.NOT_ACCEPTABLE.code()).end();
            } else {
                routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();
            }
        } else {
            routingContext.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
        }
    }

    static void downloadHandler(RoutingContext routingContext) {
        String source = routingContext.request().getParam(UrlParam.SOURCE);
        String path = routingContext.request().getParam(UrlParam.PATH);
        String sessionId = routingContext.session().id();

        DownloadCounter.getCounter().addDownloadTask(sessionId + source);
        int bufferSize = 2 * 4096;
        logger.debug("Download file {}", path);
        logger.debug("Source is {}", source);

        ChannelSftp channelSftp = null;
        try {
            channelSftp = SftpSessionManager.getManager().getDownloadChannel(sessionId, source);
            ChannelSftp temp = channelSftp;


            HttpServerResponse response = routingContext.response();

            AtomicLong readSize = new AtomicLong(0);

            SftpATTRS attrs = channelSftp.lstat(path);
            long fileSize = attrs.getSize();

            response.putHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""
                    + path.substring(path.lastIndexOf(FileSystems.getDefault().getSeparator()) + 1) + "\"");
            response.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize));
            response.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
            response.setWriteQueueMaxSize(bufferSize);

            InputStream inputStream = new BufferedInputStream(channelSftp.get(path), 2 * bufferSize);

            response.exceptionHandler(throwable -> {
                logger.error(throwable);
                if (temp.isConnected()) {
                    temp.disconnect();
                }
                DownloadCounter.getCounter().removeDownloadTask(sessionId + source);
            }).closeHandler(Void -> {
                if (readSize.get() < fileSize) {
                    if (temp.isConnected()) {
                        temp.disconnect();
                    }
                    DownloadCounter.getCounter().removeDownloadTask(sessionId + source);
                }
            });

            byte[] bytes = new byte[bufferSize];
            AtomicBoolean isWritable = new AtomicBoolean(true);
            response.drainHandler(Void -> isWritable.set(true));

            int size;
            while ((size = inputStream.read(bytes)) != -1) {
                readSize.getAndAdd(size);

                while (!isWritable.get()) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                    }
                }
                if (size >= bufferSize) {
                    response.write(Buffer.buffer().appendBytes(bytes));
                } else {
                    response.write(Buffer.buffer().appendBytes(bytes, 0, size));
                }

                if (response.writeQueueFull()) {
                    isWritable.set(false);
                }
            }

            if (channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            DownloadCounter.getCounter().removeDownloadTask(sessionId + source);
            response.end();
        } catch (JSchException | SftpException | IOException e) {

            logger.error(e);
            if (!(e instanceof JSchException)) {
                if (channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
            }
            DownloadCounter.getCounter().removeDownloadTask(sessionId + source);
            routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        }
    }

    static void downloadZipHandler(RoutingContext routingContext) {
        String source = routingContext.request().getParam(UrlParam.SOURCE);
        String path = routingContext.request().getParam(UrlParam.PATH);
        String sessionId = routingContext.session().id();
        String rootPath = path.substring(0, path.lastIndexOf(SEPARATOR));
        String relativePath = path.substring(path.lastIndexOf(SEPARATOR) + 1);

        logger.debug("Download folder {} as zip", path);
        logger.debug("Source is {}", source);

        HttpServerResponse response = routingContext.response();
        response.setWriteQueueMaxSize(2 * 4096);
        response.setChunked(true);
        response.putHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""
                + relativePath + ".zip" + "\"");
        response.putHeader(HttpHeaders.CONTENT_TYPE, "application/zip");

        AtomicBoolean isWritable = new AtomicBoolean(true);
        ChannelSftp channelSftp = null;
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new DownloadZipOutputStream(response, isWritable))) {
            channelSftp = SftpSessionManager.getManager().getSftpChannel(sessionId, source);

            ChannelSftp temp = channelSftp;
            response.closeHandler(Void -> {
                if (temp.isConnected()) {
                    temp.disconnect();
                }
            }).exceptionHandler(throwable -> {
                logger.error(throwable);
                if (temp.isConnected()) {
                    temp.disconnect();
                }
            }).drainHandler(Void -> isWritable.set(true));

            FolderNode folderNode = new FolderNode(zipOutputStream, channelSftp, rootPath, relativePath, isWritable);
            folderNode.startToZip();
            zipOutputStream.flush();
        } catch (IOException | JSchException | SftpException e) {
            logger.error(e);
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
        }
    }

    static void chunkUploadHandler(RoutingContext routingContext) {
        String fileName = routingContext.request().getHeader(HttpHeaders.CONTENT_DISPOSITION).split("filename=")[1]; //get the name of a uploaded file
        fileName = fileName.substring(1, fileName.length() - 1).replace("%20", " "); // convert %20 to empty space
        String source = routingContext.request().getParam(UrlParam.SOURCE);
        /**
         * This parameter represents where the data should be uploaded
         */
        String path = routingContext.request().getParam(UrlParam.PATH);
        String sessionId = routingContext.session().id();

        logger.debug("Upload file {} to path {}", fileName, path);
        try {
            ChannelSftp channelSftp = SftpSessionManager.getManager().getSftpChannel(sessionId, source);


            try {
                OutputStream ops = channelSftp.put(StringUtils.join(path, FileSystems.getDefault().getSeparator(), fileName), ChannelSftp.APPEND);

                Buffer body = routingContext.getBody();
                ops.write(body.getBytes());
                ops.close();
                if (channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
                routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();

            } catch (SftpException | IOException e) {
                logger.error(e);
                if (channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
                routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
            }

        } catch (JSchException e) {
            logger.error(e);
            routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        }
    }

    private static void returnResponseFunction(
            RoutingContext routingContext,
            CheckedFunction<ChannelSftp, Pair<HttpResponseStatus, Optional<String>>> function) {
        Session session = routingContext.session();
        String sessionId = session.id();
        String source = routingContext.request().getParam(JsonKey.SOURCE) != null ? routingContext.request().getParam(JsonKey.SOURCE) : routingContext.getBodyAsJson().getString(JsonKey.SOURCE);



        ChannelSftp channelSftp = null;
        try {
            channelSftp = SftpSessionManager.getManager().getSftpChannel(sessionId, source);
            Pair<HttpResponseStatus, Optional<String>> pair = function.apply(channelSftp);

            if (pair.getRight().isPresent()) {
                routingContext.response().setStatusCode(pair.getLeft().code()).end(pair.getRight().get());
            } else {
                routingContext.response().setStatusCode(pair.getLeft().code()).end();
            }
        } catch (SftpException | JSchException e) {
            logger.error(e.getMessage(), e.fillInStackTrace());
            routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        } catch (Exception e) {
            logger.error(e.getMessage(), e.fillInStackTrace());
            routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
        }
    }

    private static void validateRequestBody(RoutingContext routingContext, Runnable runnable, String... params) {
        JsonObject body = routingContext.getBodyAsJson();
        if (body == null || body.size() == 0) {
            routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
        } else {
            if (Arrays.stream(params).allMatch((param) -> body.containsKey(param))) {
                runnable.run();
            } else {
                routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
            }
        }
    }

    private static void validateSpecificConstraint(RoutingContext routingContext, Supplier<Boolean> validator, Runnable runnable) {
        if (validator.get()) {
            runnable.run();
        } else {
            routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
        }
    }

}
