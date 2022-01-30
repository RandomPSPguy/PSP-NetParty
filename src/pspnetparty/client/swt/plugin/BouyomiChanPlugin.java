/*
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
package pspnetparty.client.swt.plugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import pspnetparty.client.swt.PlayClient;
import pspnetparty.client.swt.config.IPreferenceNodeProvider;
import pspnetparty.client.swt.message.Chat;
import pspnetparty.client.swt.message.IMessage;
import pspnetparty.client.swt.message.IMessageListener;
import pspnetparty.client.swt.message.LobbyCircleChat;
import pspnetparty.client.swt.message.PrivateChat;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;

public class BouyomiChanPlugin implements IPlugin, IPreferenceNodeProvider {
	private static final String SECTION_NAME = "BouyomiChan";
	private static final String INI_USE = "Use";
	private static final String INI_ADDRESS = "Address";
	private static final String INI_READ_MY_CHAT = "ReadMyChat";
	private static final String INI_READ_ROOM_CHAT = "ReadRoomChat";
	private static final String INI_READ_LOBBY_CHAT = "ReadLobbyChat";
	private static final String INI_READ_CIRCLE_CHAT = "ReadCircleChat";
	private static final String INI_READ_PRIVATE_CHAT = "ReadPrivateChat";

	private PlayClient application;
	private IniSection iniSection;

	private ByteBuffer headerBuffer;
	private InetSocketAddress socketAddress;

	private boolean use;
	private String address;
	private boolean readMyChat;
	private boolean readRoomChat;
	private boolean readLobbyChat;
	private boolean readCircleChat;
	private boolean readPrivateChat;

	private int errorCount = 0;

	public BouyomiChanPlugin() {
		headerBuffer = ByteBuffer.allocate(15);
		headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
		headerBuffer.putShort((short) 0); // コマンド
		headerBuffer.putShort((short) -1); // 速度
		headerBuffer.putShort((short) -1); // 音量
		// buffer.putShort((short) 0);
		headerBuffer.putShort((short) 0); // 声質
		headerBuffer.put((byte) 0); // 文字コード
	}

	public void sendMessage(InetSocketAddress address, String message) {
		byte[] bytes = message.getBytes(AppConstants.CHARSET);
		ByteBuffer buffer = ByteBuffer.allocate(bytes.length + 4);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		buffer.putInt(bytes.length);
		buffer.put(bytes);

		try {
			SocketChannel channel = SocketChannel.open();
			channel.connect(address);

			headerBuffer.flip();
			buffer.flip();
			channel.write(new ByteBuffer[] { headerBuffer, buffer });

			channel.close();

			errorCount = 0;
		} catch (IOException e) {
			if (errorCount < 5) {
				errorCount++;

				application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
				e.printStackTrace();
			} else {
				socketAddress = null;
				use = false;
				iniSection.set(INI_USE, false);

				errorCount = 0;
				application.getArenaWindow().appendToSystemLog("I couldn't connect to AquesTalk. Please review the settings.", true);
			}
		}
	}

	@Override
	public void initPlugin(PlayClient application) {
		this.application = application;
		iniSection = application.getIniSection(SECTION_NAME);
		application.addConfigPageProvider(this);

		use = iniSection.get(INI_USE, false);
		address = iniSection.get(INI_ADDRESS, ":50001");

		readMyChat = iniSection.get(INI_READ_MY_CHAT, false);
		readRoomChat = iniSection.get(INI_READ_ROOM_CHAT, true);
		readLobbyChat = iniSection.get(INI_READ_LOBBY_CHAT, true);
		readCircleChat = iniSection.get(INI_READ_CIRCLE_CHAT, true);
		readPrivateChat = iniSection.get(INI_READ_PRIVATE_CHAT, true);

		application.addRoomMessageListener(new IMessageListener() {
			@Override
			public void messageReceived(IMessage message) {
				if (socketAddress == null)
					return;

				if (!readRoomChat)
					return;

				if (message instanceof Chat) {
					Chat chat = (Chat) message;
					if (!chat.isMine() || readMyChat)
						sendMessage(socketAddress, chat.getMessage());
				}
			}
		});

		application.addLobbyMessageListener(new IMessageListener() {
			@Override
			public void messageReceived(IMessage message) {
				if (socketAddress == null)
					return;

				if (message instanceof PrivateChat) {
					PrivateChat chat = (PrivateChat) message;
					if (!chat.isMine() && readPrivateChat)
						sendMessage(socketAddress, chat.getMessage());
				} else if (message instanceof LobbyCircleChat) {
					if (!readCircleChat)
						return;
					LobbyCircleChat chat = (LobbyCircleChat) message;
					if (!chat.isMine() || readMyChat)
						sendMessage(socketAddress, chat.getMessage());
				} else if (message instanceof Chat) {
					if (!readLobbyChat)
						return;
					Chat chat = (Chat) message;
					if (!chat.isMine() || readMyChat)
						sendMessage(socketAddress, chat.getMessage());
				}
			}
		});

		setup();
	}

	@Override
	public void disposePlugin() {
	}

	private void setup() {
		if (use && !Utility.isEmpty(address)) {
			socketAddress = Utility.parseSocketAddress(address);
		} else {
			socketAddress = null;
		}
	}

	@Override
	public PreferenceNode createPreferenceNode() {
		return new PreferenceNode("bouyomichan", new BouyomiChanPage());
	}

	private class BouyomiChanPage extends PreferencePage {
		private Button useCheckButton;
		private Text addressText;
		private Button readMyChatCheck;
		private Button readRoomChatCheck;
		private Button readLobbyChatCheck;
		private Button readCircleChatCheck;
		private Button readPrivateChatCheck;

		public BouyomiChanPage() {
			super("Stick reading");
			noDefaultAndApplyButton();
		}

		@Override
		protected Control createContents(Composite parent) {
			GridLayout gridLayout;

			Composite container = new Composite(parent, SWT.NONE);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 6;
			gridLayout.verticalSpacing = 6;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginTop = 2;
			container.setLayout(gridLayout);

			useCheckButton = new Button(container, SWT.CHECK | SWT.FLAT);
			useCheckButton.setText("Use Sticky Reader * Sticky Reader (free software) is required separately.");
			useCheckButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
			useCheckButton.setSelection(use);

			Label addressLabel = new Label(container, SWT.NONE);
			addressLabel.setText("address");
			addressLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			addressText = new Text(container, SWT.BORDER | SWT.SINGLE);
			addressText.setTextLimit(100);
			addressText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			addressText.setText(address);
			addressText.setEnabled(use);

			readMyChatCheck = new Button(container, SWT.CHECK | SWT.FLAT);
			readMyChatCheck.setText("Read your chat aloud");
			readMyChatCheck.setLayoutData(new GridData(SWT.LEAD, SWT.CENTER, false, false, 2, 1));
			readMyChatCheck.setSelection(readMyChat);

			readRoomChatCheck = new Button(container, SWT.CHECK | SWT.FLAT);
			readRoomChatCheck.setText("Read aloud the chat in the playroom");
			readRoomChatCheck.setLayoutData(new GridData(SWT.LEAD, SWT.CENTER, false, false, 2, 1));
			readRoomChatCheck.setSelection(readRoomChat);

			readLobbyChatCheck = new Button(container, SWT.CHECK | SWT.FLAT);
			readLobbyChatCheck.setText("Read aloud the chat throughout the lobby");
			readLobbyChatCheck.setLayoutData(new GridData(SWT.LEAD, SWT.CENTER, false, false, 2, 1));
			readLobbyChatCheck.setSelection(readLobbyChat);

			readCircleChatCheck = new Button(container, SWT.CHECK | SWT.FLAT);
			readCircleChatCheck.setText("Read the circle chat aloud");
			readCircleChatCheck.setLayoutData(new GridData(SWT.LEAD, SWT.CENTER, false, false, 2, 1));
			readCircleChatCheck.setSelection(readCircleChat);

			readPrivateChatCheck = new Button(container, SWT.CHECK | SWT.FLAT);
			readPrivateChatCheck.setText("Read aloud a private message");
			readPrivateChatCheck.setLayoutData(new GridData(SWT.LEAD, SWT.CENTER, false, false, 2, 1));
			readPrivateChatCheck.setSelection(readPrivateChat);

			useCheckButton.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (useCheckButton.getSelection()) {
						addressText.setEnabled(true);
					} else {
						addressText.setEnabled(false);
					}
				}
			});

			return container;
		}

		private void reflectValues() {
			if (!isControlCreated())
				return;

			use = useCheckButton.getSelection();
			iniSection.set(INI_USE, use);

			address = addressText.getText();
			iniSection.set(INI_ADDRESS, address);

			setup();

			readMyChat = readMyChatCheck.getSelection();
			iniSection.set(INI_READ_MY_CHAT, readMyChat);

			readRoomChat = readRoomChatCheck.getSelection();
			iniSection.set(INI_READ_ROOM_CHAT, readRoomChat);

			readLobbyChat = readLobbyChatCheck.getSelection();
			iniSection.set(INI_READ_LOBBY_CHAT, readLobbyChat);

			readCircleChat = readCircleChatCheck.getSelection();
			iniSection.set(INI_READ_CIRCLE_CHAT, readCircleChat);

			readPrivateChat = readPrivateChatCheck.getSelection();
			iniSection.set(INI_READ_PRIVATE_CHAT, readPrivateChat);
		}

		@Override
		public boolean performOk() {
			reflectValues();
			return super.performOk();
		}
	}

	public static void main(String[] args) throws Exception {
		String message = "Stick reading";
		InetSocketAddress address = new InetSocketAddress(50001);

		BouyomiChanPlugin bc = new BouyomiChanPlugin();

		bc.sendMessage(address, message);
		bc.sendMessage(address, message);
	}
}
