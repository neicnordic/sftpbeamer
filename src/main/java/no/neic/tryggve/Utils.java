package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public final class Utils {
    private static Logger logger = LoggerFactory.getLogger(Utils.class);

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

    public static List<List<String>> assembleFolderContent(Vector<ChannelSftp.LsEntry> entryVector, ChannelSftp channelSftp, String rootPath) {
        List<List<String>> entryList = new ArrayList<>(entryVector.size());
        entryVector.stream().filter(entry -> !entry.getFilename().startsWith(".")).forEach(entry -> {
            List<String> item = new ArrayList<>(3);
            item.add(entry.getFilename());

            if (entry.getAttrs().isDir()) {
                try {
                    item.add(String.valueOf(getSizeOfFolder(channelSftp, rootPath + FileSystems.getDefault().getSeparator() + entry.getFilename())));
                } catch (SftpException e) {
                    //TODO
                    logger.error(e);
                    item.add(String.valueOf(entry.getAttrs().getSize()));
                }
                item.add("folder");
            } else {
                item.add(String.valueOf(entry.getAttrs().getSize()));
                item.add("file");
            }
            entryList.add(item);
        });
        return entryList;
    }

    public static long getSizeOfFolder(ChannelSftp channelSftp, String folder) throws SftpException{
        logger.debug("Get size of folder : " + folder);
        long size = 0;
        Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(folder);
        for (ChannelSftp.LsEntry entry : entries) {
            if (!entry.getFilename().startsWith(".")) {
                if (entry.getAttrs().isDir()) {
                    size += getSizeOfFolder(channelSftp, folder + FileSystems.getDefault().getSeparator() + entry.getFilename());
                } else {
                    size += entry.getAttrs().getSize();
                }
            }
        }
        return size;
    }
}
