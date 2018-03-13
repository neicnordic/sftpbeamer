package no.neic.tryggve;


import com.jcraft.jsch.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This class is used to manage the sftp connection.
 */
public final class SftpSessionManager {
    private static Logger logger = LoggerFactory.getLogger(SftpSessionManager.class);
    private static final String HOST1 = "host1";
    private static final String HOST2 = "host2";

    private static SftpSessionManager instance = new SftpSessionManager();

    public static SftpSessionManager getManager() {
        return instance;
    }

    /**
     * The sftpConnectionHolderMap is used to keep the sftp connections of a user. The key is the session id.
     */
    private Map<String, SftpSessionHolder> sftpConnectionHolderMap;

    private SftpSessionManager() {
        sftpConnectionHolderMap = new HashMap<>();
    }

    public void createSftpSession(String sessionId, String source,
                                  String userName, String password, String otc, String hostName, int port) throws JSchException {
        JSch jSch = new JSch();
        Session session = jSch.getSession(userName, hostName, port);
        JSch.setConfig("StrictHostKeyChecking", "no");
        session.setUserInfo(new TwoStepsAuth(password, otc));
        session.connect();

        Session sessionDownload = jSch.getSession(userName, hostName, port);
        JSch.setConfig("StrictHostKeyChecking", "no");
        sessionDownload.setUserInfo(new TwoStepsAuth(password, otc));
        sessionDownload.connect();

        saveSftpSession(sessionId, source,
                Utils.createSftpSession(userName, password, hostName, port, Optional.of(otc)),
                Utils.createSftpSession(userName, password, hostName, port, Optional.of(otc)));
    }

    public void createSftpSession(String sessionId, String source,
                                  String userName, String password, String hostName, int port) throws JSchException {
        saveSftpSession(sessionId, source,
                Utils.createSftpSession(userName, password, hostName, port, Optional.empty()),
                Utils.createSftpSession(userName, password, hostName, port, Optional.empty()));
    }

    private ChannelSftp openSftpChannel(Session session) throws JSchException{
        return Utils.openSftpChannel(session);
    }

    public ChannelSftp getDownloadChannel(String sessionId, String source) throws JSchException {
        ChannelSftp channelSftp;
        if (source.equals(HOST1)) {
            channelSftp = openSftpChannel(this.sftpConnectionHolderMap.get(sessionId).getHost1Download());
        } else if (source.equals(HOST2)) {
            channelSftp = openSftpChannel(this.sftpConnectionHolderMap.get(sessionId).getHost2Download());
        } else {
            channelSftp = null;
        }
        return channelSftp;
    }

    public ChannelSftp getSftpChannel(String sessionId, String source) throws JSchException{
        ChannelSftp channelSftp;
        if (source.equals(HOST1)) {
            channelSftp = openSftpChannel(this.sftpConnectionHolderMap.get(sessionId).getHost1());
        } else if (source.equals(HOST2)) {
            channelSftp = openSftpChannel(this.sftpConnectionHolderMap.get(sessionId).getHost2());
        } else {
            channelSftp = null;
        }
        return channelSftp;
    }

    public void disconnectSftp(String sessionId, String source) {

        if (sftpConnectionHolderMap.containsKey(sessionId)) {
            if (source.equals(HOST1)) {
                if (sftpConnectionHolderMap.get(sessionId).getHost1() != null) {
                    sftpConnectionHolderMap.get(sessionId).getHost1().disconnect();
                    sftpConnectionHolderMap.get(sessionId).setHost1(null);
                }
                if (sftpConnectionHolderMap.get(sessionId).getHost1Download() != null) {
                    sftpConnectionHolderMap.get(sessionId).getHost1Download().disconnect();
                    sftpConnectionHolderMap.get(sessionId).setHost1Download(null);
                }
            }
            if (source.equals(HOST2)) {
                if (sftpConnectionHolderMap.get(sessionId).getHost2() != null) {
                    sftpConnectionHolderMap.get(sessionId).getHost2().disconnect();
                    sftpConnectionHolderMap.get(sessionId).setHost2(null);
                }
                if (sftpConnectionHolderMap.get(sessionId).getHost2Download() != null) {
                    sftpConnectionHolderMap.get(sessionId).getHost2Download().disconnect();
                    sftpConnectionHolderMap.get(sessionId).setHost2Download(null);
                }
            }
        }

    }

    public void disconnectSftp(String sessionId) {
        if (sftpConnectionHolderMap.containsKey(sessionId)) {
            if (sftpConnectionHolderMap.get(sessionId).getHost1() != null) {
                sftpConnectionHolderMap.get(sessionId).getHost1().disconnect();
            }
            if (sftpConnectionHolderMap.get(sessionId).getHost1Download() != null) {
                sftpConnectionHolderMap.get(sessionId).getHost1Download().disconnect();
            }
            if (sftpConnectionHolderMap.get(sessionId).getHost2() != null) {
                sftpConnectionHolderMap.get(sessionId).getHost2().disconnect();
            }
            if (sftpConnectionHolderMap.get(sessionId).getHost2Download() != null) {
                sftpConnectionHolderMap.get(sessionId).getHost2Download().disconnect();
            }
            sftpConnectionHolderMap.remove(sessionId);
        }

    }

    private void saveSftpSession(String sessionId, String source, Session session, Session sessionDownload) {
        if (sftpConnectionHolderMap.containsKey(sessionId)) {
            if (source.equals(HOST1)) {
                sftpConnectionHolderMap.get(sessionId).setHost1(session);
                sftpConnectionHolderMap.get(sessionId).setHost1Download(sessionDownload);
            }
            if (source.equals(HOST2)) {
                sftpConnectionHolderMap.get(sessionId).setHost2(session);
                sftpConnectionHolderMap.get(sessionId).setHost2Download(sessionDownload);
            }
        } else {
            SftpSessionHolder holder = new SftpSessionHolder();
            if (source.equals(HOST1)) {
                holder.setHost1(session);
                holder.setHost1Download(sessionDownload);
            }
            if (source.equals(HOST2)) {
                holder.setHost2(session);
                holder.setHost2Download(sessionDownload);
            }
            sftpConnectionHolderMap.put(sessionId, holder);
        }
    }



    private class SftpSessionHolder {
        private Session host1;
        private Session host1Download;

        private Session host2;
        private Session host2Download;

        public Session getHost1() {
            return host1;
        }

        public void setHost1(Session host1) {
            this.host1 = host1;
        }

        public Session getHost2() {
            return host2;
        }

        public void setHost2(Session host2) {
            this.host2 = host2;
        }

        public Session getHost1Download() {
            return host1Download;
        }

        public void setHost1Download(Session host1Download) {
            this.host1Download = host1Download;
        }

        public Session getHost2Download() {
            return host2Download;
        }

        public void setHost2Download(Session host2Download) {
            this.host2Download = host2Download;
        }
    }

}
