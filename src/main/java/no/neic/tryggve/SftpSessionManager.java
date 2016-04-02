package no.neic.tryggve;

import com.jcraft.jsch.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public final class SftpSessionManager {
    private static SftpSessionManager manager;

    public static SftpSessionManager getManager() {
        if (manager == null) {
            manager = new SftpSessionManager();
        }
        return manager;
    }

    private Map<String, SftpSessionHolder> sftpSessionHolderMap;
    private String HOST1 = "host1";
    private String HOST2 = "host2";

    private SftpSessionManager() {
        sftpSessionHolderMap = new HashMap<>();
    }

    public void createSftpSession(String sessionId, String source,
                                  String userName, String password, String otc, String hostName, int port) throws JSchException {
        JSch jSch = new JSch();
        Session session = jSch.getSession(userName, hostName, port);
        JSch.setConfig("StrictHostKeyChecking", "no");
        session.setUserInfo(new TwoStepsAuth(password, otc));
        session.connect();

        saveSftpSession(sessionId, source, session);
    }

    public void createSftpSession(String sessionId, String source,
                                  String userName, String password, String hostName, int port) throws JSchException {
        JSch jSch = new JSch();
        Session session = jSch.getSession(userName, hostName, port);
        JSch.setConfig("StrictHostKeyChecking", "no");
        session.setUserInfo(new OneStepAuth(password));
        session.connect();

        saveSftpSession(sessionId, source, session);
    }

    public void disconnectSftp(String sessionId, String source) {
        if (sftpSessionHolderMap.containsKey(sessionId)) {
            if (source.equals(HOST1) && sftpSessionHolderMap.get(sessionId).getHost1() != null) {
                sftpSessionHolderMap.get(sessionId).getHost1().disconnect();
                sftpSessionHolderMap.get(sessionId).setHost1(null);
            }
            if (source.equals(HOST2) && sftpSessionHolderMap.get(sessionId).getHost2() != null) {
                sftpSessionHolderMap.get(sessionId).getHost2().disconnect();
                sftpSessionHolderMap.get(sessionId).setHost2(null);
            }
        }
    }

    public ChannelSftp openSftpChannel(String sessionId, String source) throws JSchException{
        ChannelSftp channelSftp;
        if (source.equals(HOST1)) {
            channelSftp = (ChannelSftp) sftpSessionHolderMap.get(sessionId).getHost1().openChannel("sftp");
        } else {
            channelSftp = (ChannelSftp) sftpSessionHolderMap.get(sessionId).getHost2().openChannel("sftp");
        }
        channelSftp.setBulkRequests(128);
        channelSftp.setInputStream(new ByteArrayInputStream(new byte[32768]));
        channelSftp.setOutputStream(new ByteArrayOutputStream(32768));
        channelSftp.connect();
        return channelSftp;
    }

    private void saveSftpSession(String sessionId, String source, Session session) {
        if (sftpSessionHolderMap.containsKey(sessionId)) {
            if (source.equals(HOST1)) {
                sftpSessionHolderMap.get(sessionId).setHost1(session);
            }
            if (source.equals(HOST2)) {
                sftpSessionHolderMap.get(sessionId).setHost2(session);
            }
        } else {
            SftpSessionHolder holder = new SftpSessionHolder();
            if (source.equals(HOST1)) {
                holder.setHost1(session);
            }
            if (source.equals(HOST2)) {
                holder.setHost2(session);
            }
            sftpSessionHolderMap.put(sessionId, holder);
        }
    }



    private class SftpSessionHolder {
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
