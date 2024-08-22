package com.satergo.controller;

import com.satergo.Load;
import com.satergo.Main;
import com.satergo.ProgramData;
import com.satergo.Utils;
import com.satergo.extra.SimpleTask;
import com.satergo.extra.dialog.MoveStyle;
import com.satergo.extra.dialog.SatPromptDialog;
import com.satergo.node.ErgoNodeAccess;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import org.ergoplatform.appkit.NetworkType;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ResourceBundle;

public class RemoteNodeSetupCtrl implements SetupPage.WithExtra, Initializable {
	@FXML private Pane root;
	// Container of address and networkType
	@FXML private VBox vbox;
	@FXML private TextField address;
	@FXML private ComboBox<NetworkType> networkType;
	@FXML private Button continueButton;
	@FXML private Node testingConnection;

	@FXML
	public void continueSetup(ActionEvent e) {
		if (address.getText().isBlank() || !address.getText().startsWith("http")) {
			Utils.alert(Alert.AlertType.ERROR, Main.lang("remoteNodeAddressInvalid"));
		} else {
			ErgoNodeAccess nodeAccess = new ErgoNodeAccess(URI.create(address.getText())) {
				@Override
				protected HttpRequest.Builder httpRequestBuilder() {
					return super.httpRequestBuilder().timeout(Duration.ofSeconds(4));
				}
			};
			Runnable onSuccess = () -> {
				Main.programData().blockchainNodeKind.set(ProgramData.NodeKind.REMOTE_NODE);
				Main.programData().nodeNetworkType.set(networkType.getValue());
				Main.programData().nodeAddress.set(address.getText());
				Main.get().displaySetupPage(Load.<WalletSetupCtrl>fxmlController("/setup-page/wallet.fxml"));
			};
			SimpleTask<ErgoNodeAccess.Status> testTask = new SimpleTask<>(nodeAccess::getStatus)
					.onFail(ex -> {
						SatPromptDialog<ButtonType> dialog = new SatPromptDialog<>();
						Utils.initDialog(dialog, Main.get().stage(), MoveStyle.FOLLOW_OWNER);
						dialog.setTitle("Error");
						dialog.getDialogPane().setContent(new Label(Main.lang("errorWhenTryingToConnectToNode").formatted(ex.getClass() == RuntimeException.class ? ex.getCause().getClass().getSimpleName() : ex.getClass().getSimpleName())));
						ButtonType ignore = new ButtonType(Main.lang("ignore"), ButtonBar.ButtonData.FINISH);
						dialog.getDialogPane().getButtonTypes().addAll(ignore, ButtonType.CANCEL);
						dialog.showForResult().ifPresent(t -> {
							if (t == ignore) onSuccess.run();
						});
					})
					.onSuccess(status -> onSuccess.run());
			continueButton.disableProperty().bind(testTask.runningProperty());
			testingConnection.visibleProperty().bind(testTask.runningProperty());
			testTask.newThread();
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		networkType.getItems().addAll(NetworkType.values());
		networkType.setValue(NetworkType.MAINNET);
		vbox.maxWidthProperty().bind(root.widthProperty().divide(2));
	}

	@FXML
	public void useKnownPublicNode(ActionEvent e) {
		// TODO node discovery or at the very least a list of nodes MUST be used to prevent centralization issues
		address.setText("http://213.239.193.208:9053");
		continueSetup(null);
	}

	@Override
	public Parent content() {
		return root;
	}

	@Override
	public Parent recreate() {
		Pair<Parent, RemoteNodeSetupCtrl> load = Load.fxmlNodeAndController("/setup-page/remote-node.fxml");
		load.getValue().address.setText(address.getText());
		load.getValue().networkType.setValue(networkType.getValue());
		return load.getKey();
	}
}
