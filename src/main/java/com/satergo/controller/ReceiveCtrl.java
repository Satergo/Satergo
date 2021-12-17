package com.satergo.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.satergo.Load;
import com.satergo.Main;
import com.satergo.Utils;
import com.satergo.ergouri.ErgoURIString;
import javafx.collections.MapChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.ergoplatform.appkit.Address;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.ResourceBundle;

public class ReceiveCtrl implements Initializable, WalletTab {
	@FXML private VBox addresses;
	@FXML private ImageView qrCodeImage;
	@FXML private Button saveQrCode;
	private final ToggleGroup qrToggleGroup = new ToggleGroup();

	private static class AddressLine extends BorderPane {
		@FXML public ToggleButton qr;
		@SuppressWarnings("unused")
		@FXML private Label name, address;
		@SuppressWarnings("unused")
		@FXML private Button copy;

		public final Address addr;

		public AddressLine(ToggleGroup qrGroup, int index, String name, Address address) {
			Load.thisFxml(this, "/receive-address-line.fxml");
			this.qr.setToggleGroup(qrGroup);
			this.name.setText(index + ": " + name);
			this.address.setText(address.toString());
			this.copy.setOnAction(e -> Utils.copyStringToClipboard(address.toString()));
			this.addr = address;
		}
	}

	private void updateAddresses() {
		addresses.getChildren().clear();
		Main.get().getWallet().myAddresses.forEach((index, name) -> {
			addresses.getChildren().add(new AddressLine(qrToggleGroup, index, name, Main.get().getWallet().publicAddress(index)));
		});
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		qrCodeImage.managedProperty().bindBidirectional(qrCodeImage.visibleProperty());
		saveQrCode.managedProperty().bindBidirectional(saveQrCode.visibleProperty());
		updateAddresses();
		Main.get().getWallet().myAddresses.addListener((MapChangeListener<Integer, String>) change -> updateAddresses());
		qrToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue == null) {
				qrCodeImage.setVisible(false);
				saveQrCode.setVisible(false);
				return;
			}
			qrCodeImage.setVisible(true);
			saveQrCode.setVisible(true);
			try {
				QRCodeWriter qrCodeWriter = new QRCodeWriter();
				int size = 300;
				String address = ((AddressLine) ((ToggleButton) newValue).getParent().getParent()).addr.toString();
				ErgoURIString ergoURI = new ErgoURIString(address, null);
				BitMatrix bitMatrix = qrCodeWriter.encode(ergoURI.toString(), BarcodeFormat.QR_CODE, size, size, Map.of(EncodeHintType.MARGIN, 1));
				WritableImage img = new WritableImage(size, size);
				PixelWriter writer = img.getPixelWriter();
				for (int readY = 0; readY < size; readY++) {
					for (int readX = 0; readX < size; readX++) {
						writer.setColor(readX, readY, bitMatrix.get(readX, readY) ? Color.BLACK : Color.WHITE);
					}
				}
				qrCodeImage.setImage(img);
				saveQrCode.setOnAction(e -> {
					Path path = Utils.fileChooserSave(Main.get().stage(), Main.lang("saveQrCode"), "qr-code.png",
							new FileChooser.ExtensionFilter(Main.lang("pngImage"), "*.png"),
							new FileChooser.ExtensionFilter(Main.lang("jpegImage"), "*.jpg"));
					if (path == null) return;
					RenderedImage renderedImage = SwingFXUtils.fromFXImage(img, null);
					String format = "png";
					if (path.getFileName().toString().endsWith(".jpg") || path.getFileName().toString().endsWith(".jpeg"))
						format = "jpg";
					try {
						ImageIO.write(renderedImage, format, path.toFile());
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}
				});
			} catch (WriterException e) {
				throw new RuntimeException(e);
			}
		});
		qrToggleGroup.selectToggle(qrToggleGroup.getToggles().get(0));
	}
}
