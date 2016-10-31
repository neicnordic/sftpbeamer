package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public final class Utils {
    private static Logger logger = LoggerFactory.getLogger(Utils.class);

    private static String SEPARATOR = FileSystems.getDefault().getSeparator();

    public static FolderNode assembleFolderInfo(ChannelSftp channelSftp, String absolutePath, String folderName) {
        FolderNode folderNode = new FolderNode();
        folderNode.folderName = folderName;
        List<String> fileList = new ArrayList<>();
        List<FolderNode> folderList = new ArrayList<>();
        try {
            Vector<ChannelSftp.LsEntry> entryVector = channelSftp.ls(absolutePath);
            entryVector.stream().filter(entry -> !entry.getFilename().startsWith(".")).forEach(entry -> {
                if (entry.getAttrs().isDir()) {
                    FolderNode temp = assembleFolderInfo(channelSftp, StringUtils.join(absolutePath, SEPARATOR, entry.getFilename()), entry.getFilename());
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
        logger.debug("Remove folder: {}", folderPath);
        Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(folderPath);
        for (ChannelSftp.LsEntry entry : entries) {
            if (!entry.getFilename().startsWith(".")) {
                if (entry.getAttrs().isDir()) {
                    deleteFolder(StringUtils.join(folderPath, SEPARATOR, entry.getFilename()), channelSftp);
                } else {
                    String file = StringUtils.join(folderPath, SEPARATOR, entry.getFilename());
                    logger.debug("Remove file: {}", file);
                    channelSftp.rm(file);
                }
            }
        }
        channelSftp.rmdir(folderPath);
        logger.debug("Folder {} is removed", folderPath);
    }

    public static List<List<String>> assembleFolderContent(Vector<ChannelSftp.LsEntry> entryVector, ChannelSftp channelSftp, String rootPath) {
        List<List<String>> entryList = new ArrayList<>(entryVector.size());
        entryVector.stream().filter(entry -> !entry.getFilename().startsWith(".")).forEach(entry -> {
            List<String> item = new ArrayList<>(3);
            item.add(entry.getFilename());

            if (entry.getAttrs().isDir()) {
                item.add(String.valueOf(entry.getAttrs().getSize()));
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
        logger.debug("Get size of folder {}", folder);
        long size = 0;
        Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(folder);
        for (ChannelSftp.LsEntry entry : entries) {
            if (!entry.getFilename().startsWith(".")) {
                if (entry.getAttrs().isDir()) {
                    size += getSizeOfFolder(channelSftp, StringUtils.join(folder, SEPARATOR, entry.getFilename()));
                } else {
                    size += entry.getAttrs().getSize();
                }
            }
        }
        return size;
    }
}
