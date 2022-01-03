package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.ProgramData;
import com.satergo.ergo.EmbeddedFullNode;
import com.satergo.ergo.EmbeddedNodeInfo;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
import java.util.stream.Collectors;

public class FullNodeSourceCtrl {

	private File existingNodeConfFile;

	@FXML
	public void downloadAndSetup(MouseEvent e) {
		if (e.getButton() == MouseButton.PRIMARY) {
			Main.get().displayPage(Load.fxml("/full-node-downloader.fxml"));
		}
	}

	@SuppressWarnings("unchecked")
	@FXML
	public void useExisting(MouseEvent e) {
		if (e.getButton() != MouseButton.PRIMARY) return;
		DirectoryChooser directoryChooser = new DirectoryChooser();
		directoryChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
		directoryChooser.setTitle(Main.lang("nodeDirectory"));
		File nodeDirectory = directoryChooser.showDialog(Main.get().stage());
		if (nodeDirectory == null || !nodeDirectory.exists()) return;
		File[] files = nodeDirectory.listFiles();
		// try to find the node jar automatically, with the ergo*.jar pattern
		List<File> candidates = files == null ? null : Arrays.stream(files)
				.filter(f -> f.getName().startsWith("ergo") && f.getName().endsWith(".jar")).collect(Collectors.toList());
		File nodeJar = candidates != null && candidates.size() == 1 ? candidates.get(0) : null;
		if (nodeJar == null) { // could not find, request from user
			FileChooser fileChooser = new FileChooser();
			fileChooser.setInitialDirectory(nodeDirectory);
			fileChooser.setTitle(Main.lang("nodeJar"));
			nodeJar = fileChooser.showOpenDialog(Main.get().stage());
			if (nodeJar == null) return;
		}
		File nodeInfoFile = new File(nodeDirectory, EmbeddedNodeInfo.FILE_NAME);
		// ask for required EmbeddedNodeInfo values if it doesn't exist
		if (!nodeInfoFile.exists()) {
			Dialog<Pair<NetworkType, File>> dialog = new Dialog<>();
			dialog.initOwner(Main.get().stage());
			dialog.setTitle(Main.lang("programName"));
			dialog.setHeaderText(Main.lang("moreInformationNeededNode"));
			Parent root = Load.fxml("/need-more-info-existing-node.fxml");
			ComboBox<NetworkType> networkType = (ComboBox<NetworkType>) root.lookup("#networkType");
			networkType.getItems().addAll(NetworkType.values());
			networkType.setValue(NetworkType.MAINNET);
			Button confFile = (Button) root.lookup("#confFile");
			confFile.setText(Main.lang("select"));
			confFile.setOnAction(ae -> {
				FileChooser fileChooser = new FileChooser();
				fileChooser.setInitialDirectory(nodeDirectory);
				fileChooser.setTitle(Main.lang("confFile"));
				existingNodeConfFile = fileChooser.showOpenDialog(Main.get().stage());
				if (existingNodeConfFile == null) return;
				confFile.setText(existingNodeConfFile.getName());
			});
			dialog.getDialogPane().setContent(root);
			dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
			dialog.setResultConverter(type -> {
				if (type == ButtonType.OK && existingNodeConfFile != null) {
					return new Pair<>(networkType.getValue(), existingNodeConfFile);
				}
				return null;
			});
			Pair<NetworkType, File> moreInfo = dialog.showAndWait().orElse(null);
			if (moreInfo == null) return;
			EmbeddedNodeInfo info = new EmbeddedNodeInfo(moreInfo.getKey(), nodeJar.getName(), EmbeddedFullNode.LogLevel.WARN, moreInfo.getValue().getName());
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
		Main.programData().blockchainNodeKind.set(ProgramData.BlockchainNodeKind.EMBEDDED_FULL_NODE);
		Main.node = Main.get().nodeFromInfo();
		Main.programData().nodeAddress.set(Main.node.localApiHttpAddress());
		Main.programData().nodeNetworkType.set(Main.node.info.networkType());
		Main.get().displayPage(Load.fxml("/wallet-setup.fxml"));
	}
}
