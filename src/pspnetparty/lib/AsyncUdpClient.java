﻿/*
Copyright (C) 2011 monte

This file is part of PSP NetParty.

PSP NetParty is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package pspnetparty.lib;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import pspnetparty.lib.constants.AppConstants;

public class AsyncUdpClient {

	private static final int BUFFER_SIZE = 20000;

	private Selector selector;
	private ConcurrentLinkedQueue<Connection> newConnectionQueue = new ConcurrentLinkedQueue<Connection>();
	private ConcurrentHashMap<Connection, Object> connections;
	private final Object valueObject = new Object();

	private Thread selectorThread;

	public AsyncUdpClient() {
		connections = new ConcurrentHashMap<AsyncUdpClient.Connection, Object>(16, 0.75f, 2);
		try {
			selector = Selector.open();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		selectorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (selector.isOpen()) {
						int s = selector.select();
						// System.out.println("Select: " + s);
						if (s > 0) {
							for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
								SelectionKey key = it.next();
								it.remove();
								Connection connection = (Connection) key.attachment();

								try {
									if (key.isReadable()) {
										connection.readReady();
									}
								} catch (IOException e) {
									connection.handler.log(connection, Utility.makeStackTrace(e));
									key.cancel();
									connection.disconnect();
								}
							}
						}

						Connection conn;
						while ((conn = newConnectionQueue.poll()) != null)
							conn.prepareConnect();
					}
				} catch (ClosedSelectorException e) {
				} catch (IOException e) {
				}
			}
		}, AsyncUdpClient.class.getName());
		selectorThread.setDaemon(true);
		selectorThread.start();
	}

	public ISocketConnection connect(InetSocketAddress address, IAsyncClientHandler handler) throws IOException {
		if (address == null)
			throw new IllegalArgumentException();

		Connection conn = new Connection(address, handler);
		newConnectionQueue.offer(conn);
		selector.wakeup();

		return conn;
	}

	public void dispose() {
		if (!selector.isOpen())
			return;
		try {
			selector.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		for (Connection conn : connections.keySet()) {
			conn.disconnect();
		}
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		dispose();
	}

	private class Connection implements ISocketConnection {
		private DatagramChannel channel;

		private InetSocketAddress remoteAddress;
		private IAsyncClientHandler handler;

		private ByteBuffer readBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
		private PacketData packetData = new PacketData(readBuffer);

		public Connection(InetSocketAddress address, IAsyncClientHandler handler) throws IOException {
			this.remoteAddress = address;
			this.handler = handler;
		}

		private void prepareConnect() {
			try {
				channel = DatagramChannel.open();
				channel.configureBlocking(false);

				channel.register(selector, SelectionKey.OP_READ, this);
				channel.connect(remoteAddress);

				connections.put(this, valueObject);
				handler.connectCallback(this);

				return;
			} catch (RuntimeException e) {
				handler.log(this, Utility.makeStackTrace(e));
			} catch (IOException e) {
			}
			handler.disconnectCallback(this);
		}

		private void readReady() throws IOException {
			readBuffer.clear();
			channel.read(readBuffer);
			readBuffer.flip();

			handler.readCallback(this, packetData);
		}

		@Override
		public void disconnect() {
			if (connections.remove(this) == null)
				return;
			try {
				handler.disconnectCallback(this);
			} catch (RuntimeException re) {
			}
			try {
				if (channel.isOpen())
					channel.close();
			} catch (IOException e) {
			}
		}

		@Override
		public boolean isConnected() {
			return channel != null && channel.isOpen();
		}

		@Override
		public InetSocketAddress getRemoteAddress() {
			return remoteAddress;
		}
		
		@Override
		public InetSocketAddress getLocalAddress() {
			return (InetSocketAddress) channel.socket().getLocalSocketAddress();
		}

		@Override
		public void send(ByteBuffer buffer) {
			if (!isConnected())
				return;

			try {
				channel.send(buffer, remoteAddress);
			} catch (IOException e) {
				handler.log(this, buffer.toString());
				handler.log(this, Utility.makeStackTrace(e));
			}
		}

		@Override
		public void send(byte[] data) {
			send(ByteBuffer.wrap(data));
		}

		@Override
		public void send(String data) {
			ByteBuffer buffer = AppConstants.CHARSET.encode(data);
			send(buffer);
		}
	}

	public static void main(String[] args) throws IOException {
		final AsyncUdpClient client = new AsyncUdpClient();
		InetSocketAddress address = new InetSocketAddress("localhost", 30000);
		final ISocketConnection conn = client.connect(address, new IAsyncClientHandler() {
			@Override
			public void log(ISocketConnection connection, String message) {
				System.out.println(message);
			}

			@Override
			public void connectCallback(ISocketConnection connection) {
				System.out.println("接続しました: " + connection.getRemoteAddress());
			}

			@Override
			public void disconnectCallback(ISocketConnection connection) {
				System.out.println("切断しました");
			}

			@Override
			public void readCallback(ISocketConnection connection, PacketData data) {
				for (String msg : data.getMessages()) {
					System.out.println("受信(" + msg.length() + ")： " + msg);
				}
			}
		});

		Thread sendThread = new Thread(new Runnable() {
			@Override
			public void run() {
				for (int i = 0; i < 3; i++)
					try {
						Thread.sleep(500);
						conn.send("TEST " + i);
						Thread.sleep(500);
					} catch (InterruptedException e) {
					}

				conn.disconnect();
				client.dispose();
			}
		});
		sendThread.start();
	}
}
