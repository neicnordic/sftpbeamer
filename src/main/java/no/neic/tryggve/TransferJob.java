package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import no.neic.tryggve.constants.ConfigName;
import no.neic.tryggve.constants.JsonKey;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

import java.nio.file.FileSystems;

public final class TransferJob {
    private static final Logger logger = LoggerFactory.getLogger(TransferJob.class);

    private JsonObject job;
    private Session from;
    private Session to;
    private String email;

    public void setJob(JsonObject job) {
        this.job = job;
    }

    public Session getFrom() {
        return from;
    }

    public void setFrom(Session from) {
        this.from = from;
    }

    public Session getTo() {
        return to;
    }

    public void setTo(Session to) {
        this.to = to;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void execute(Future<JsonObject> future) {
        ChannelSftp fromChannel;
        ChannelSftp toChannel;
        JsonObject result = new JsonObject();


        JsonObject fromObject = job.getJsonObject(JsonKey.FROM);
        String fromHost = fromObject.getString(JsonKey.HOSTNAME);
        String fromPath = fromObject.getString(JsonKey.PATH);

        JsonObject toObject = job.getJsonObject(JsonKey.TO);
        String toHost = toObject.getString(JsonKey.HOSTNAME);
        String toPath = toObject.getString(JsonKey.PATH);
        try {
            fromChannel = Utils.openSftpChannel(from);
            toChannel = Utils.openSftpChannel(to);


            logger.debug("A transfer job from {} to {} has been submitted. The email is {}.", fromHost, toHost, email);
            try {

                JsonArray filesArray = job.getJsonArray(JsonKey.DATA);

                JsonArray successArray = new JsonArray();
                JsonArray failedArray = new JsonArray();

                for (Object object : filesArray) {
                    String fromFile = StringUtils.join(fromPath, FileSystems.getDefault().getSeparator(), object.toString());
                    String toFile = StringUtils.join(toPath, FileSystems.getDefault().getSeparator(), object.toString());
                    try {
                        fromChannel.get(fromFile, toChannel.put(toFile));
                        logger.debug("File {} is transferred from {} in host {} to {} in host {}", object.toString(), fromPath, fromHost, toPath, toHost);
                        successArray.add(fromFile);
                    } catch (SftpException e) {
                        logger.error("Failed to transfer file {} from {} in host {} to {} in host {}", e.getCause(), object.toString(), fromPath, fromHost, toPath, toHost);
                        failedArray.add(fromFile);
                    }
                }
                result.put(JsonKey.FROM, fromHost).put(JsonKey.TO, toHost).put(JsonKey.SUCCESS, successArray).put(JsonKey.FAILED, failedArray).put(JsonKey.STATUS, "success").put(JsonKey.EMAIL, email);
                future.complete(result);
            } finally {
                if (from.isConnected()) {
                    from.disconnect();
                }
                if (to.isConnected()) {
                    to.disconnect();
                }
                logger.debug("Sessions for email {} are disconnected", email);
            }
        } catch (JSchException e) {
            logger.error("Failed to open channel", e.getCause());
            result.put(JsonKey.MESSAGE, "Failed to open channel");
            result.put(JsonKey.FROM, fromHost);
            result.put(JsonKey.DATA, job.getJsonArray(JsonKey.DATA));
            result.put(JsonKey.EMAIL, email);
            result.put(JsonKey.STATUS, "failed");
            future.complete(result);
            if (from != null && from.isConnected()) {
                from.disconnect();
            }
            if (to != null && to.isConnected()) {
                to.disconnect();
            }
        }
    }

    public void sendEmail(JsonObject result) {
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
    }
}
