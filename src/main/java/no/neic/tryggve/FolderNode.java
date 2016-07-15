package no.neic.tryggve;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import no.neic.tryggve.constants.JsonPropertyName;
import no.neic.tryggve.constants.VertxConstant;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;

public class FolderNode {

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
                fileNodeList.stream().forEach(fileName -> filesList.add(StringUtils.join(new String[]{relativePath, fileName}, FileSystems.getDefault().getSeparator())));
            }
        }
        if (folderNodeList.size() != 0) {
            if (relativePath.isEmpty()) {
                folderNodeList.stream().forEach(folderNode -> filesList.addAll(folderNode.getRelativeFilePathArray(folderNode.folderName)));
            } else {
                folderNodeList.stream().forEach(folderNode -> filesList.addAll(folderNode.getRelativeFilePathArray(StringUtils.join(new String[]{relativePath, folderNode.folderName}, FileSystems.getDefault().getSeparator()))));
            }
        }
        return filesList;
    }

    /**
     * {"files":["test", "test"],
     * "folders":[{"folder_name":{"files":[], "folders":[]}},
     * {"folder_name":{"files":[], "folders":[]}}]}
     *
     */
    public JsonObject getJsonStructure() {
        if (fileNodeList.size() == 0 && folderNodeList.size() == 0) {
            return null;
        }

        JsonObject folderContentJson = new JsonObject();
        JsonArray fileArray = new JsonArray();
        if (fileNodeList.size() != 0) {
            fileNodeList.stream().forEach(fileArray::add);
            folderContentJson.put("files", fileArray);
        }
        if (folderNodeList.size() != 0) {
            JsonArray folderArray = new JsonArray();
            JsonObject temp;
            for (FolderNode node : folderNodeList) {
                temp = node.getJsonStructure();
                if (temp != null) {
                    folderArray.add(temp);
                }
            }
            folderContentJson.put("folders", folderArray);
        }

        return new JsonObject().put(folderName, folderContentJson);
    }

    public void transfer(ChannelSftp channelSftpFrom, String pathFrom, ChannelSftp channelSftpTo, String pathTo, SftpProgressMonitor monitor, EventBus bus, String messageAddress) {
        if (!pathFrom.equals(folderName)) {
            try {
                channelSftpTo.mkdir(pathTo);
            } catch (SftpException e) {}
        }
        JsonObject jsonObject = new JsonObject();
        for (String fileName : fileNodeList) {
            try {
                jsonObject.put(JsonPropertyName.STATUS, "start").put("address", messageAddress);
                jsonObject.put(JsonPropertyName.FILE, pathFrom + FileSystems.getDefault().getSeparator() + fileName);
                bus.publish(VertxConstant.TRANSFER_EVENTBUS_NAME, jsonObject.encode());
                channelSftpFrom.get(pathFrom + FileSystems.getDefault().getSeparator() + fileName, channelSftpTo.put(pathTo + FileSystems.getDefault().getSeparator() + fileName), monitor);
            } catch (SftpException e) {
                e.printStackTrace();
            }
        }
        for (FolderNode folderNode : folderNodeList) {
            folderNode.transfer(channelSftpFrom, pathFrom + FileSystems.getDefault().getSeparator() + folderNode.folderName,
                    channelSftpTo, pathTo + FileSystems.getDefault().getSeparator() + folderNode.folderName, monitor, bus, messageAddress);
        }
    }

    public void createFolder(boolean isRootNode, ChannelSftp channelSftpTo, String pathTo) {
        if (isRootNode) {
            for (FolderNode folderNode : folderNodeList) {
                folderNode.createFolder(false, channelSftpTo, pathTo);
            }
        } else {
            try {
                channelSftpTo.mkdir(StringUtils.join(new String[]{pathTo, this.folderName}, FileSystems.getDefault().getSeparator()));
                for (FolderNode folderNode : folderNodeList) {
                    folderNode.createFolder(false, channelSftpTo, StringUtils.join(new String[]{pathTo, this.folderName}, FileSystems.getDefault().getSeparator()));
                }
            } catch (SftpException e) {}
        }
    }
}
