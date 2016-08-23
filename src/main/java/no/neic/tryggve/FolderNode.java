package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;

public class FolderNode {
    private static String SEPARATOR = FileSystems.getDefault().getSeparator();

    public String folderName;

    public List<String> fileNodeList;
    public List<FolderNode> folderNodeList;

    public FolderNode() {
        this.fileNodeList = new ArrayList<>();
        this.folderNodeList = new ArrayList<>();
    }

    public List<String> getRelativeFilePathArray(String relativePath) {
        List<String> filesList = new ArrayList<>();
        if (fileNodeList.size() == 0 && folderNodeList.size() == 0) {
            return filesList;
        }

        if (fileNodeList.size() != 0) {
            if (relativePath.isEmpty()) {
                fileNodeList.stream().forEach(filesList::add);
            } else {
                fileNodeList.stream().forEach(fileName -> filesList.add(StringUtils.join(relativePath, SEPARATOR, fileName)));
            }
        }
        if (folderNodeList.size() != 0) {
            if (relativePath.isEmpty()) {
                folderNodeList.stream().forEach(folderNode -> filesList.addAll(folderNode.getRelativeFilePathArray(folderNode.folderName)));
            } else {
                folderNodeList.stream().forEach(folderNode -> filesList.addAll(folderNode.getRelativeFilePathArray(StringUtils.join(relativePath, SEPARATOR, folderNode.folderName))));
            }
        }
        return filesList;
    }

    public void createFolder(boolean isRootNode, ChannelSftp channelSftpTo, String pathTo) {
        if (isRootNode) {
            for (FolderNode folderNode : folderNodeList) {
                folderNode.createFolder(false, channelSftpTo, pathTo);
            }
        } else {
            try {
                channelSftpTo.mkdir(StringUtils.join(pathTo, SEPARATOR, this.folderName));
                for (FolderNode folderNode : folderNodeList) {
                    folderNode.createFolder(false, channelSftpTo, StringUtils.join(pathTo, SEPARATOR, this.folderName));
                }
            } catch (SftpException e) {}
        }
    }
}
