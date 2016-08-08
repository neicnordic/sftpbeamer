package no.neic.tryggve;




import com.jcraft.jsch.*;
import io.vertx.core.cli.InvalidValueException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

public final class SftpConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(SftpConnectionManager.class);

    private static SftpConnectionManager manager;

    public static SftpConnectionManager getManager() {
        if (manager == null) {
            manager = new SftpConnectionManager();
        }
        return manager;
    }

    private Map<String, SftpConnectionHolder> sftpConnectionHolderMap;
    private String HOST1 = "host1";
    private String HOST2 = "host2";

    private SftpConnectionManager() {
        sftpConnectionHolderMap = new HashMap<>();
    }

    public void createSftpConnection(String sessionId, String source,
                                     String userName, String password, String otc, String hostName, int port) throws JSchException {
        JSch jSch = new JSch();
        Session session = jSch.getSession(userName, hostName, port);
        JSch.setConfig("StrictHostKeyChecking", "no");
        session.setUserInfo(new TwoStepsAuth(password, otc));
        session.connect();
        saveSftpConnection(sessionId, source, session);
    }

    public void createSftpConnection(String sessionId, String source,
                                     String userName, String password, String hostName, int port) throws JSchException {
        JSch jSch = new JSch();
        Session session = jSch.getSession(userName, hostName, port);
        JSch.setConfig("StrictHostKeyChecking", "no");
        session.setUserInfo(new OneStepAuth(password));
        session.connect();
        saveSftpConnection(sessionId, source, session);
    }

    private ChannelSftp openSftpChannel(Session session) throws JSchException{
        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
        channelSftp.setBulkRequests(128);
        channelSftp.setInputStream(new ByteArrayInputStream(new byte[32768]));
        channelSftp.setOutputStream(new ByteArrayOutputStream(32768));
        channelSftp.connect();
        return channelSftp;
    }

    public ChannelSftp getSftpConnection(String sessionId, String source) throws JSchException{
        if (source.equals(HOST1)) {
            return openSftpChannel(this.sftpConnectionHolderMap.get(sessionId).getHost1());
        } else if (source.equals(HOST2)) {
            return openSftpChannel(this.sftpConnectionHolderMap.get(sessionId).getHost2());
        } else {
            return null;
        }
    }

    public void disconnectSftp(String sessionId, String source) {
        if (sftpConnectionHolderMap.containsKey(sessionId)) {
            if (source.equals(HOST1) && sftpConnectionHolderMap.get(sessionId).getHost1() != null) {
                sftpConnectionHolderMap.get(sessionId).getHost1().disconnect();
                sftpConnectionHolderMap.get(sessionId).setHost1(null);
            }
            if (source.equals(HOST2) && sftpConnectionHolderMap.get(sessionId).getHost2() != null) {
                sftpConnectionHolderMap.get(sessionId).getHost2().disconnect();
                sftpConnectionHolderMap.get(sessionId).setHost2(null);
            }
        }

    }

    public void disconnectSftp(String sessionId) {
        if (sftpConnectionHolderMap.containsKey(sessionId)) {
            if (sftpConnectionHolderMap.get(sessionId).getHost1() != null) {
                sftpConnectionHolderMap.get(sessionId).getHost1().disconnect();
            }
            if (sftpConnectionHolderMap.get(sessionId).getHost2() != null) {
                sftpConnectionHolderMap.get(sessionId).getHost2().disconnect();
            }
            sftpConnectionHolderMap.remove(sessionId);
        }

    }

    private void saveSftpConnection(String sessionId, String source, Session session) {
        if (sftpConnectionHolderMap.containsKey(sessionId)) {
            if (source.equals(HOST1)) {
                sftpConnectionHolderMap.get(sessionId).setHost1(session);
            }
            if (source.equals(HOST2)) {
                sftpConnectionHolderMap.get(sessionId).setHost2(session);
            }
        } else {
            SftpConnectionHolder holder = new SftpConnectionHolder();
            if (source.equals(HOST1)) {
                holder.setHost1(session);
            }
            if (source.equals(HOST2)) {
                holder.setHost2(session);
            }
            sftpConnectionHolderMap.put(sessionId, holder);
        }
    }



    private class SftpConnectionHolder {
        private Session host1;
        private Session host2;

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
    }

    private class OneStepAuth implements UserInfo {
        private String password;

        public OneStepAuth(String password) {
            this.password = password;
        }

        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return this.password;
        }

        @Override
        public boolean promptPassword(String s) {
            return true;
        }

        @Override
        public boolean promptPassphrase(String s) {
            return false;
        }

        @Override
        public boolean promptYesNo(String s) {
            return false;
        }

        @Override
        public void showMessage(String s) {

        }
    }

    private class TwoStepsAuth implements UserInfo, UIKeyboardInteractive {
        private String password;
        private String otc;

        public TwoStepsAuth(String password, String otc) {
            this.password = password;
            this.otc = otc;
        }

        @Override
        public String[] promptKeyboardInteractive(String destination, String name,
                                                  String instruction, String[] prompt, boolean[] echo) {
            if (prompt[0].contains("Password")) {
                return new String[]{password};
            } else {
                return new String[]{otc};
            }
        }

        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String s) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String s) {
            return false;
        }

        @Override
        public boolean promptYesNo(String s) {
            return false;
        }

        @Override
        public void showMessage(String s) {
        }
    }
}
