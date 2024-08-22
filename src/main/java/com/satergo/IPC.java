package com.satergo;

import javafx.application.Platform;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Uses Unix Domain Sockets, or if they are not supported, TCP sockets, to communicate with an existing instance
 * Despite the name of Unix Domain Sockets, they are also supported on Windows 10+ (but not on 7).
 */
public class IPC {

	private ServerSocketChannel serverChannel;
	public final Path path;

	private ByteBuffer readBytes(SocketChannel channel, int length) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(length);
		channel.read(buffer);
		buffer.flip();
		return buffer;
	}

	private final boolean unixDomainSocketsSupported;

	public IPC(Path path) {
		this.path = path;
		boolean unixDomainSocketsSupported;
		try {
			SocketChannel.open(StandardProtocolFamily.UNIX).close();
			unixDomainSocketsSupported = true;
		} catch (UnsupportedOperationException e) {
			unixDomainSocketsSupported = false;
		} catch (IOException e) {
			unixDomainSocketsSupported = true;
		}
		this.unixDomainSocketsSupported = unixDomainSocketsSupported;
	}

	public boolean exists() {
		return Files.exists(path);
	}

	@SuppressWarnings("InfiniteLoopStatement")
	public void listen() throws IOException {
		Files.deleteIfExists(path);
		try {
			SocketAddress address;
			if (unixDomainSocketsSupported) {
				address = UnixDomainSocketAddress.of(path);
				serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
				serverChannel.bind(address);
			} else {
				serverChannel = tcpChannelOnAvailablePort();
				Files.writeString(path, String.valueOf(((InetSocketAddress) serverChannel.getLocalAddress()).getPort()));
			}
			while (true) {
				SocketChannel channel = serverChannel.accept();
				int messageType = readBytes(channel, 1).get();
				if (messageType == 1) { // open ergo URI
					int length = readBytes(channel, 4).getInt();
					String uri = StandardCharsets.UTF_8.decode(readBytes(channel, length)).toString();
					Platform.runLater(() -> Main.get().handleErgoURI(uri));
				} else if (messageType == 2) { // handle ergopay URI
					int length = readBytes(channel, 4).getInt();
					String uri = StandardCharsets.UTF_8.decode(readBytes(channel, length)).toString();
					Platform.runLater(() -> Main.get().handleErgoPayURI(uri));
				}
				channel.close();
			}
		} catch (ClosedChannelException ignored) {
			// Happens when serverChannel.close() is called
		}
	}

	public void connectAndSend(int messageType, String data) throws IOException {
		SocketAddress address;
		SocketChannel channel;
		if (unixDomainSocketsSupported) {
			address = UnixDomainSocketAddress.of(path);
			channel = SocketChannel.open(StandardProtocolFamily.UNIX);
		} else {
			address = new InetSocketAddress(Integer.parseInt(Files.readString(path)));
			channel = SocketChannel.open();
		}
		channel.connect(address);
		channel.write(ByteBuffer.allocate(1).put((byte) messageType).flip());
		if (data != null) {
			byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
			channel.write(ByteBuffer.allocate(4).putInt(bytes.length).flip());
			channel.write(ByteBuffer.wrap(bytes));
		}
		channel.close();
	}

	public void stopListening() throws IOException {
		serverChannel.close();
	}

	private ServerSocketChannel tcpChannelOnAvailablePort() throws IOException {
		int port = ThreadLocalRandom.current().nextInt(49152, 65536);
		try {
			ServerSocketChannel serverChannel = ServerSocketChannel.open();
			serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			serverChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
			return serverChannel;
		} catch (BindException e) {
			return tcpChannelOnAvailablePort();
		}
	}
}
