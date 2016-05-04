package no.neic.tryggve;

import com.jcraft.jsch.*;
import com.sun.org.apache.bcel.internal.generic.LSTORE;
import io.vertx.core.eventbus.EventBus;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

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
        channelSftp.setInputStream(new ByteArrayInputStream(new byte[32768]));
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

    public static void transferFile(ChannelSftp from, ChannelSftp to, String pathFrom, String pathTo, String fileName, EventBus bus, String messageAddress) {
        SftpProgressMonitor monitor = new ProgressMonitor(bus, messageAddress);
        try {
            from.cd(pathFrom);
            to.cd(pathTo);

            from.get(pathFrom + FileSystems.getDefault().getSeparator() + fileName, to.put(pathTo + FileSystems.getDefault().getSeparator() + fileName), monitor);
        } catch (SftpException e) {
            e.printStackTrace();
        }
//        to.put(from.get(pathFrom + FileSystems.getDefault().getSeparator() + fileName), pathTo + FileSystems.getDefault().getSeparator() + fileName);

//        try (BufferedInputStream bis = new BufferedInputStream(from.get(pathFrom + FileSystems.getDefault().getSeparator() + fileName), 32768)) {
//            to.put(bis, pathTo + FileSystems.getDefault().getSeparator() + fileName);
//        }
    }

    public static void transferFolder(ChannelSftp from, ChannelSftp to, String pathFrom, String pathTo, String folderName, EventBus bus, String messageAddress){
        String newPathTo = pathTo + FileSystems.getDefault().getSeparator() + folderName;

        try {
            to.mkdir(newPathTo);
        } catch (SftpException e) {}

        String newPathFrom = pathFrom + FileSystems.getDefault().getSeparator() + folderName;
        try {
            Vector<ChannelSftp.LsEntry> entryVector = from.ls(newPathFrom);
            entryVector.stream().filter(entry -> !entry.getFilename().startsWith(".")).forEach(entry -> {
                if (entry.getAttrs().isDir()) {
                    transferFolder(from, to, newPathFrom, newPathTo, entry.getFilename(), bus, messageAddress);
                } else {
                    transferFile(from, to, newPathFrom, newPathTo, entry.getFilename(), bus, messageAddress);
                }
            });
        } catch (SftpException e) {
            e.printStackTrace();
        }
    }

    public static FolderNode assembleFolderInfo(ChannelSftp channelSftp, String absolutePath, String folderName) {
        FolderNode folderNode = new FolderNode();
        folderNode.folderName = folderName;
        List<String> fileList = new ArrayList<>();
        List<FolderNode> folderList = new ArrayList<>();
        try {
            Vector<ChannelSftp.LsEntry> entryVector = channelSftp.ls(absolutePath);
            entryVector.stream().filter(entry -> !entry.getFilename().startsWith(".")).forEach(entry -> {
                if (entry.getAttrs().isDir()) {
                    FolderNode temp = assembleFolderInfo(channelSftp, absolutePath + FileSystems.getDefault().getSeparator() + entry.getFilename(), entry.getFilename());
                    if (temp != null) {
                        folderList.add(temp);
                    }
                } else {
                    fileList.add(entry.getFilename());
                }
            });
            if (fileList.size() == 0 && folderList.size() == 0) {
                return null;
            }
            if (fileList.size() != 0) {
                folderNode.fileNodeList.addAll(fileList);
            }

            if (folderList.size() != 0) {
                folderNode.folderNodeList.addAll(folderList);
            }
            return folderNode;
        } catch (SftpException e) {
            return null;
        }
    }

    public static void deleteFolder(String folderPath, ChannelSftp channelSftp) throws SftpException{
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
