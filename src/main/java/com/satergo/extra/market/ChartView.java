package com.satergo.extra.market;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.satergo.Load;
import com.satergo.Utils;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;

import static com.satergo.Utils.HTTP;

public class ChartView extends VBox {

	private final PriceCurrency vs;

	public enum TimeDetail { HOUR_DAY, DAY_MONTH, MONTH_YEAR }

	@FXML private ToggleGroup time;
	@FXML private LineChart<Number, Number> chart;
	@FXML private NumberAxis xAxis;

	private TimeDetail timeDetail;

	public ChartView(PriceCurrency vs) {
		Load.thisFxml(this, "/chart-view.fxml");
		this.vs = vs;

		xAxis.setTickLabelFormatter(new StringConverter<>() {
			@Override
			public String toString(Number object) {
				LocalDateTime time = Instant.ofEpochMilli(object.longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
				return switch (timeDetail) {
					case HOUR_DAY -> time.getHour() + ":00 " + time.getDayOfMonth();
					case DAY_MONTH -> time.getDayOfMonth() + "/" + time.getMonthValue();
					case MONTH_YEAR -> time.getMonthValue() + "-" + time.getYear();
				};
			}

			@Override public Number fromString(String string) { throw new RuntimeException(); }
		});

		handleTimeSelection();
		time.selectedToggleProperty().addListener((observable, oldValue, newValue) -> handleTimeSelection());
	}

	private void handleTimeSelection() {
		int days = Integer.parseInt((String) time.getSelectedToggle().getUserData());
		try {
			updateChart(fetchCoinGeckoChart(vs, days), switch (days) {
				case 1 -> TimeDetail.HOUR_DAY;
				case 7, 14, 30, 90 -> TimeDetail.DAY_MONTH;
				case 365, -1 -> TimeDetail.MONTH_YEAR;
				default -> throw new RuntimeException();
			});
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void updateChart(ChartData chartData, TimeDetail timeDetail) {
		this.timeDetail = timeDetail;
		chart.getData().clear();
		XYChart.Series<Number, Number> series = new XYChart.Series<>();
		for (ChartData.Point point : chartData.points()) {
			series.getData().add(new XYChart.Data<>(point.time().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), point.price()));
		}
		chart.getData().add(series);
	}

	private record ChartData(Point[] points) {
		private record Point(LocalDateTime time, double price) {}
	}

	private static final ZonedDateTime MAINNET_START = ZonedDateTime.of(2019, 7, 1, 10, 0, 0, 0, ZoneOffset.UTC);
	
	private static ChartData fetchCoinGeckoChart(PriceCurrency vs, int days) throws IOException, InterruptedException {
		HttpRequest request = Utils.httpRequestBuilder().uri(URI.create("https://api.coingecko.com/api/v3/coins/ergo/market_chart?vs_currency=" + vs.lc() + "&days=" + (days == -1 ? "max" : days))).build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		try {
			JsonObject o = JsonParser.object().from(response.body());
			return new ChartData(o.getArray("prices").stream().map(a -> (JsonArray) a).map(a ->
							new ChartData.Point(Instant.ofEpochMilli(a.getLong(0)).atZone(ZoneId.systemDefault()).toLocalDateTime(),  a.getDouble(1)))
					// Drop data before 10:00 1 July 2019 UTC as that was before Ergo mainnet
					.dropWhile(p -> p.time.atZone(ZoneId.systemDefault()).isBefore(MAINNET_START))
					.toArray(ChartData.Point[]::new));
		} catch (JsonParserException e) {
			throw new RuntimeException(e);
		}
	}
}
