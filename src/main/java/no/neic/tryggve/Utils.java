package no.neic.tryggve;

import com.jcraft.jsch.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;

public final class Utils {
    public static Session createJschSession(String userName, String password, String otc, String hostName, int port) throws JSchException {
        JSch jSch = new JSch();
        Session session = jSch.getSession(userName, hostName, port);
        JSch.setConfig("StrictHostKeyChecking", "no");
        session.setUserInfo(new TwoStepsAuth(password, otc));
        session.connect();
        return session;
    }

    public static Session createJschSession(String userName, String password, String hostName, int port) throws JSchException {
        JSch jSch = new JSch();
        Session session = jSch.getSession(userName, hostName, port);
        JSch.setConfig("StrictHostKeyChecking", "no");
        session.setUserInfo(new OneStepAuth(password));
        session.connect();
        return session;
    }

    public static ChannelSftp createSftpChannel(Session session) throws JSchException {
        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
        channelSftp.setBulkRequests(128);
//        channelSftp.setInputStream(new ByteArrayInputStream(new byte[32768]));
        channelSftp.setOutputStream(new ByteArrayOutputStream(32768));
        channelSftp.connect();
        return channelSftp;
    }

    private static class OneStepAuth implements UserInfo {
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

    private static class TwoStepsAuth implements UserInfo, UIKeyboardInteractive {
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

    public static void transferFile(ChannelSftp from, ChannelSftp to, String pathFrom, String pathTo, String fileName, SftpProgressMonitor monitor) throws SftpException, IOException {
        from.cd(pathFrom);
        to.cd(pathTo);

        from.get(pathFrom + FileSystems.getDefault().getSeparator() + fileName, to.put(pathTo + FileSystems.getDefault().getSeparator() + fileName), monitor);
//        to.put(from.get(pathFrom + FileSystems.getDefault().getSeparator() + fileName), pathTo + FileSystems.getDefault().getSeparator() + fileName);

//        try (BufferedInputStream bis = new BufferedInputStream(from.get(pathFrom + FileSystems.getDefault().getSeparator() + fileName), 32768)) {
//            to.put(bis, pathTo + FileSystems.getDefault().getSeparator() + fileName);
//        }
    }
}
