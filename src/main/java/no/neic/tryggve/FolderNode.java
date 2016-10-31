package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.IO;
import com.jcraft.jsch.SftpException;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FolderNode {
    private static String SEPARATOR = FileSystems.getDefault().getSeparator();

    public String folderName;

    public List<String> fileNodeList;
    public List<FolderNode> folderNodeList;

    public FolderNode() {
        this.fileNodeList = new ArrayList<>();
        this.folderNodeList = new ArrayList<>();
    }

    private static int bufferSize = 2 * 4096;
    private static byte[] bytes = new byte[bufferSize];
    private ZipOutputStream zipOutputStream;
    private ChannelSftp channelSftp;
    private String rootPath;
    private String relativePath;
    private AtomicBoolean isWritable;
    public FolderNode(ZipOutputStream zipOutputStream, ChannelSftp channelSftp, String rootPath, String relativePath, AtomicBoolean isWritable) {
        this.zipOutputStream = zipOutputStream;
        this.channelSftp = channelSftp;
        this.relativePath = relativePath;
        this.rootPath = rootPath;
        this.isWritable = isWritable;
        this.folderNodeList = new ArrayList<>();
    }

    public void startToZip() throws IOException, SftpException{
        Vector<ChannelSftp.LsEntry> entries = this.channelSftp.ls(StringUtils.join(this.rootPath, SEPARATOR, this.relativePath));
        for (ChannelSftp.LsEntry entry : entries) {
            if (!entry.getFilename().startsWith(".")) {
                if (entry.getAttrs().isDir()) {
                    folderNodeList.add(new FolderNode(this.zipOutputStream, this.channelSftp, this.rootPath, StringUtils.join(this.relativePath, SEPARATOR, entry.getFilename()), isWritable));
                } else {
                    InputStream inputStream = null;
                    try {
                        inputStream = new BufferedInputStream(this.channelSftp.get(StringUtils.join(new String[]{this.rootPath, this.relativePath, entry.getFilename()}, SEPARATOR)), 5120);
                        this.zipOutputStream.putNextEntry(new ZipEntry(StringUtils.join(this.relativePath, SEPARATOR, entry.getFilename())));
                        int bytesRead;
                        while ((bytesRead = inputStream.read(bytes)) != -1) {
                            while (!isWritable.get()) {
                                try {
                                    Thread.sleep(3);
                                } catch (InterruptedException e) {
                                }
                            }
                            if (bytesRead >= bufferSize) {
                                this.zipOutputStream.write(bytes);
                            } else {
                                this.zipOutputStream.write(bytes, 0, bytesRead);
                            }
                        }
                        this.zipOutputStream.closeEntry();
                    } finally {
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e) {
                            }
                        }
                    }
                }
            }
        }

        for (FolderNode node : this.folderNodeList) {
            node.startToZip();
        }

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
