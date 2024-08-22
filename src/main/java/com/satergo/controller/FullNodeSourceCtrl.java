package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.ProgramData;
import com.satergo.Utils;
import com.satergo.node.EmbeddedNode;
import com.satergo.node.EmbeddedNodeInfo;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPromptDialog;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Pair;
import org.ergoplatform.appkit.NetworkType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class FullNodeSourceCtrl implements SetupPage.WithExtra {
	@FXML private Parent root;

	@FXML
	public void downloadAndSetup(ActionEvent e) {
		Main.get().displaySetupPage(Load.<NodeDownloaderCtrl>fxmlController("/setup-page/node-download.fxml"));
	}

	@FXML
	public void useExisting(ActionEvent e) {
		useExisting();
	}

	/**
	 * The node being selected can be a light node as well. It is determined from the conf file.
	 */
	@SuppressWarnings("unchecked")
	public static void useExisting() {
		DirectoryChooser directoryChooser = new DirectoryChooser();
		directoryChooser.setTitle(Main.lang("nodeDirectory"));
		File nodeDirectory;
		File selectedDirectory = directoryChooser.showDialog(Main.get().stage());
		if (selectedDirectory == null || !selectedDirectory.exists()) return;
		// If the .ergo directory inside the node directory was selected by accident,
		// use the parent directory instead
		if (selectedDirectory.getName().equals(".ergo")) {
			nodeDirectory = selectedDirectory.getParentFile();
		} else {
			nodeDirectory = selectedDirectory;
		}
		// If the selected directory does not contain a .ergo directory
		if (!Files.exists(nodeDirectory.toPath().resolve(".ergo"))) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("selectedDirectoryNotNode"));
			return;
		}
		File nodeJar;
		File nodeInfoFile = new File(nodeDirectory, EmbeddedNodeInfo.FILE_NAME);
		// ask for required EmbeddedNodeInfo values if it doesn't exist
		if (!nodeInfoFile.exists()) {
			// try to find the node jar automatically, with the ergo*.jar pattern
			File[] files = nodeDirectory.listFiles();
			List<File> candidates = files == null ? null : Arrays.stream(files)
					.filter(f -> f.getName().startsWith("ergo") && f.getName().endsWith(".jar")).toList();
			nodeJar = candidates != null && candidates.size() == 1 ? candidates.getFirst() : null;
			if (nodeJar == null) { // could not find or found multiple, request from user
				FileChooser fileChooser = new FileChooser();
				fileChooser.setInitialDirectory(nodeDirectory);
				fileChooser.setTitle(Main.lang("nodeJar"));
				nodeJar = fileChooser.showOpenDialog(Main.get().stage());
				if (nodeJar == null) return;
			}

			SatPromptDialog<Pair<NetworkType, File>> dialog = new SatPromptDialog<>();
			Utils.initDialog(dialog, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
			dialog.setTitle(Main.lang("programName"));
			dialog.setHeaderText(Main.lang("moreInformationNeededNode"));
			Parent root = Load.fxml("/dialog/need-more-info-existing-node.fxml");
			ComboBox<NetworkType> networkType = (ComboBox<NetworkType>) root.lookup("#networkType");
			networkType.getItems().addAll(NetworkType.values());
			networkType.setValue(NetworkType.MAINNET);
			File[] existingNodeConfFile = { null };
			Button confFile = (Button) root.lookup("#confFile");
			confFile.setText(Main.lang("select"));
			confFile.setOnAction(ae -> {
				FileChooser fileChooser = new FileChooser();
				fileChooser.setInitialDirectory(nodeDirectory);
				fileChooser.setTitle(Main.lang("confFile"));
				existingNodeConfFile[0] = fileChooser.showOpenDialog(Main.get().stage());
				if (existingNodeConfFile[0] == null) return;
				confFile.setText(existingNodeConfFile[0].getName());
			});
			dialog.getDialogPane().setContent(root);
			dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
			dialog.setResultConverter(type -> {
				if (type == ButtonType.OK && existingNodeConfFile[0] != null) {
					return new Pair<>(networkType.getValue(), existingNodeConfFile[0]);
				}
				return null;
			});
			Pair<NetworkType, File> moreInfo = dialog.showForResult().orElse(null);
			if (moreInfo == null) return;
			EmbeddedNodeInfo info = new EmbeddedNodeInfo(moreInfo.getKey(), nodeJar.getName(), EmbeddedNode.DEFAULT_LOG_LEVEL, moreInfo.getValue().getName());
			try {
				Path path = nodeDirectory.toPath().resolve(EmbeddedNodeInfo.FILE_NAME);
				Main.programData().embeddedNodeInfo.set(path);
				Files.writeString(path, info.toJson());
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		} else {
			Main.programData().embeddedNodeInfo.set(nodeInfoFile.toPath());
		}
		Main.node = Main.get().nodeFromInfo();
		Main.programData().blockchainNodeKind.set(Main.node.isConfigLightNode() ? ProgramData.NodeKind.EMBEDDED_LIGHT_NODE : ProgramData.NodeKind.EMBEDDED_FULL_NODE);
		Main.programData().nodeAddress.set(Main.node.localApiHttpAddress());
		Main.programData().nodeNetworkType.set(Main.node.info.networkType());
		Main.get().displaySetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
	}

	@Override
	public Parent recreate() {
		return Load.fxml("/setup-page/full-node-source.fxml");
	}

	@Override
	public Parent content() {
		return root;
	}
}
