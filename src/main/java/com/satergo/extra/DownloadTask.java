package com.satergo.extra;

import javafx.concurrent.Task;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DownloadTask extends Task<Void> {

	private final HttpClient httpClient;
	private final HttpRequest request;
	private final OutputStream out;

	public DownloadTask(HttpClient httpClient, HttpRequest request, OutputStream out) {
		this.httpClient = httpClient;
		this.request = request;
		this.out = out;
	}

	@Override
	protected Void call() throws Exception {
		HttpResponse<InputStream> dl = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		InputStream in = dl.body();
		long totalSize = Long.parseLong(dl.headers().firstValue("Content-Length").orElseThrow());

		long downloaded = 0;
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer, 0, 8192)) >= 0) {
			out.write(buffer, 0, read);
			downloaded += read;
			updateProgress(downloaded, totalSize);
		}
		in.close();
		out.close();
		return null;
	}
}
