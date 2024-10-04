package com.satergo.controller;

import com.satergo.extra.FlexibleTilePane;
import com.satergo.tool.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class ToolsCtrl implements WalletTab, Initializable {

	private final List<Tool> tools = new ArrayList<>();
	@FXML private FlexibleTilePane root;

	public ToolsCtrl() {
		tools.add(new AirdropTool());
		tools.add(new BurnTokens());
		tools.add(new ConsolidationTool());
		tools.add(new TransferEverythingTool());
	}

	@Override
	public void initialize(URL url, ResourceBundle resourceBundle) {
		for (Tool tool : tools) {
			FlexibleTilePane.setColumnSpan(tool.tile(), tool.tileColumnSpan());
			root.getNodes().add(tool.tile());
		}
	}
}
