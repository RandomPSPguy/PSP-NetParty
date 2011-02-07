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
package pspnetparty.lib;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.constants.ProtocolConstants.Search;

public class ProxyRoomEngine {

	private ConcurrentHashMap<String, Room> masterNameRoomMap;
	private ConcurrentHashMap<InetSocketAddress, TunnelState> notYetLinkedTunnels;

	private IServer<PlayerState> roomServer;
	private RoomHandler roomHandler;

	private IServer<TunnelState> tunnelServer;
	private TunnelHandler tunnelHandler;

	private int port = -1;
	private int maxRooms = 10;
	private boolean passwordAllowed = true;
	private File loginMessageFile;

	private boolean isStarted = false;
	private ILogger logger;

	public ProxyRoomEngine(ILogger logger) {
		this.logger = logger;

		masterNameRoomMap = new ConcurrentHashMap<String, ProxyRoomEngine.Room>(20, 0.75f, 1);
		notYetLinkedTunnels = new ConcurrentHashMap<InetSocketAddress, TunnelState>(30, 0.75f, 2);

		roomServer = new AsyncTcpServer<PlayerState>();
		roomHandler = new RoomHandler();

		tunnelServer = new AsyncUdpServer<TunnelState>();
		tunnelHandler = new TunnelHandler();
	}

	public void start(int port) throws IOException {
		if (isStarted)
			throw new IllegalStateException();
		logger.log("プロトコル: " + ProtocolConstants.PROTOCOL_NUMBER);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		roomServer.startListening(bindAddress, roomHandler);
		tunnelServer.startListening(bindAddress, tunnelHandler);

		this.port = port;
		isStarted = true;
	}

	public void stop() {
		if (!isStarted)
			return;

		roomServer.stopListening();
		tunnelServer.stopListening();
		this.port = -1;
	}

	public int getPort() {
		return port;
	}

	public int getCurrentRooms() {
		return masterNameRoomMap.size();
	}

	public int getMaxRooms() {
		return maxRooms;
	}

	public void setMaxRooms(int maxRooms) {
		if (maxRooms > 0)
			this.maxRooms = maxRooms;
	}

	public boolean isRoomPasswordAllowed() {
		return passwordAllowed;
	}

	public void setRoomPasswordAllowed(boolean passwordAllowed) {
		this.passwordAllowed = passwordAllowed;
	}

	public void setLoginMessageFile(String loginMessageFile) {
		if (Utility.isEmpty(loginMessageFile)) {
			this.loginMessageFile = null;
		} else {
			this.loginMessageFile = new File(loginMessageFile);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Entry<String, Room> entry : masterNameRoomMap.entrySet()) {
			Room room = entry.getValue();
			sb.append(entry.getKey());
			sb.append('\t').append(room.title);
			sb.append('\t').append(room.playersByName.size()).append('/').append(room.maxPlayers);
			sb.append('\t').append(room.password);
			sb.append(AppConstants.NEW_LINE);
		}
		return sb.toString();
	}

	public void notifyAllPlayers(String message) {
		final String notify = ProtocolConstants.Room.NOTIFY_FROM_ADMIN + ProtocolConstants.ARGUMENT_SEPARATOR + message;
		IClientStateAction<PlayerState> action = new IClientStateAction<ProxyRoomEngine.PlayerState>() {
			@Override
			public void action(PlayerState p) {
				p.connection.send(notify);
			}
		};
		for (Entry<String, Room> entry : masterNameRoomMap.entrySet()) {
			Room room = entry.getValue();
			room.forEachPlayer(action);
		}
	}

	public boolean destroyRoom(String masterName) {
		Room room = masterNameRoomMap.remove(masterName);
		if (room == null)
			return false;

		room.forEachPlayer(new IClientStateAction<ProxyRoomEngine.PlayerState>() {
			@Override
			public void action(PlayerState p) {
				p.room = null;
				p.name = "";
				p.connection.send(ProtocolConstants.Room.NOTIFY_ROOM_DELETED);
				p.connection.disconnect();
			}
		});
		return true;
	}

	public void hirakeGoma(String masterName) {
		Room room = masterNameRoomMap.get(masterName);
		if (room == null)
			return;

		room.maxPlayers++;
	}

	private static class PlayerState implements IClientState {

		private ISocketConnection connection;

		private HashMap<String, IServerMessageHandler<PlayerState>> messageHandlers;
		private String name;
		private Room room;
		private TunnelState tunnelState;

		PlayerState(ISocketConnection conn) {
			connection = conn;
		}

		@Override
		public ISocketConnection getConnection() {
			return connection;
		}
	}

	private class Room {
		private ConcurrentSkipListMap<String, PlayerState> playersByName;
		private HashMap<String, TunnelState> tunnelsByMacAddress;

		private PlayerState roomMaster;

		private int maxPlayers = 4;
		private String title;
		private String description = "";
		private String password = "";

		private String roomMasterAuthCode;

		private Room() {
			playersByName = new ConcurrentSkipListMap<String, PlayerState>();
			tunnelsByMacAddress = new HashMap<String, TunnelState>();
		}

		private void forEachPlayer(IClientStateAction<PlayerState> action) {
			for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
				PlayerState state = entry.getValue();
				action.action(state);
			}
		}

		private void appendRoomInfo(StringBuilder sb) {
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(roomMaster.name);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(maxPlayers);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(title);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(password);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(description);
		}

		private void appendNotifyUserList(StringBuilder sb) {
			sb.append(ProtocolConstants.Room.NOTIFY_USER_LIST);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR).append(roomMaster.name);
			for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
				PlayerState state = entry.getValue();
				if (state != roomMaster)
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR).append(state.name);
			}
		}
	}

	private void appendLoginMessage(StringBuilder sb) {
		String loginMessage = Utility.getFileContent(loginMessageFile);
		if (!Utility.isEmpty(loginMessage)) {
			sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
			sb.append(ProtocolConstants.Room.NOTIFY_FROM_ADMIN);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(loginMessage);
		}
	}

	private class RoomHandler implements IAsyncServerHandler<PlayerState> {

		private HashMap<String, IServerMessageHandler<PlayerState>> protocolHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();
		private HashMap<String, IServerMessageHandler<PlayerState>> loginHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();
		private HashMap<String, IServerMessageHandler<PlayerState>> sessionHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();

		private HashMap<String, IServerMessageHandler<PlayerState>> searchLoginHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();
		private HashMap<String, IServerMessageHandler<PlayerState>> searchHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();

		RoomHandler() {
			protocolHandlers.put(ProtocolConstants.Room.PROTOCOL_NAME, new RoomProtocolHandler());
			protocolHandlers.put(ProtocolConstants.Search.PROTOCOL_NAME, new SearchProtocolHandler());

			loginHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_CREATE, new RoomCreateHandler());
			loginHandlers.put(ProtocolConstants.Room.COMMAND_LOGIN, new LoginHandler());
			loginHandlers.put(ProtocolConstants.Room.COMMAND_LOGOUT, new LogoutHandler());
			loginHandlers.put(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE, new ConfirmAuthCodeHandler());

			sessionHandlers.put(ProtocolConstants.Room.COMMAND_LOGOUT, new LogoutHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_CHAT, new ChatHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_PING, new PingHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_PING, new InformPingHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_UDP_PORT, new InformTunnelPortHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER, new MacAddressPlayerHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_UPDATE, new RoomUpdateHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_KICK_PLAYER, new RoomKickPlayerHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_MASTER_TRANSFER, new RoomMasterTransferHandler());

			searchLoginHandlers.put(ProtocolConstants.Search.COMMAND_LOGIN, new SearchLoginHandler());
			searchHandlers.put(ProtocolConstants.Search.COMMAND_SEARCH, new SearchHandler());
		}

		@Override
		public void serverStartupFinished() {
		}

		@Override
		public void serverShutdownFinished() {
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public PlayerState createState(ISocketConnection connection) {
			PlayerState state = new PlayerState(connection);
			state.messageHandlers = protocolHandlers;
			return state;
		}

		@Override
		public void disposeState(PlayerState state) {
			if (state.tunnelState != null) {
				state.tunnelState = null;
			}

			Room room = state.room;
			if (room == null)
				return;

			String name = state.name;
			room.playersByName.remove(name);

			if (state == room.roomMaster) {
				masterNameRoomMap.remove(name);
				PlayerState newRoomMaster = null;
				for (Entry<String, PlayerState> entry : room.playersByName.entrySet()) {
					String playerName = entry.getKey();
					if (masterNameRoomMap.putIfAbsent(playerName, room) == null) {
						newRoomMaster = entry.getValue();
						room.roomMaster = newRoomMaster;
						break;
					}
				}
				if (newRoomMaster == null) {
					for (Entry<String, PlayerState> entry : room.playersByName.entrySet()) {
						PlayerState p = entry.getValue();
						// playerRoomMap.remove(p);

						p.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_DELETED);
						p.getConnection().disconnect();
					}
					room.playersByName.clear();
				} else {
					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.Room.NOTIFY_USER_EXITED);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(name);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
					sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
					room.appendRoomInfo(sb);

					final String notify = sb.toString();
					room.forEachPlayer(new IClientStateAction<PlayerState>() {
						@Override
						public void action(PlayerState p) {
							p.getConnection().send(notify);
						}
					});

					sb.delete(0, sb.length());

					room.roomMasterAuthCode = Utility.makeAuthCode();
					sb.append(ProtocolConstants.Room.NOTIFY_ROOM_MASTER_AUTH_CODE);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.roomMasterAuthCode);

					newRoomMaster.getConnection().send(sb.toString());
				}
			} else {
				final String notify = ProtocolConstants.Room.NOTIFY_USER_EXITED + ProtocolConstants.ARGUMENT_SEPARATOR + name;
				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(notify);
					}
				});
			}
		}

		@Override
		public boolean processIncomingData(PlayerState state, PacketData data) {
			boolean sessionContinue = false;

			for (String message : data.getMessages()) {
				int commandEndIndex = message.indexOf(ProtocolConstants.ARGUMENT_SEPARATOR);
				String command, argument;
				if (commandEndIndex > 0) {
					command = message.substring(0, commandEndIndex);
					argument = message.substring(commandEndIndex + 1);
				} else {
					command = message;
					argument = "";
				}

				IServerMessageHandler<PlayerState> handler = state.messageHandlers.get(command);
				if (handler != null) {
					try {
						sessionContinue = handler.process(state, argument);
					} catch (RuntimeException e) {
						logger.log(Utility.makeStackTrace(e));
					}
				}

				if (!sessionContinue)
					break;
			}

			return sessionContinue;
		}

		private class RoomProtocolHandler implements IServerMessageHandler<PlayerState> {
			String errorMessage = ProtocolConstants.ERROR_PROTOCOL_MISMATCH + ProtocolConstants.ARGUMENT_SEPARATOR
					+ ProtocolConstants.PROTOCOL_NUMBER;

			@Override
			public boolean process(PlayerState state, String argument) {
				if (ProtocolConstants.PROTOCOL_NUMBER.equals(argument)) {
					state.messageHandlers = loginHandlers;
					return true;
				} else {
					state.getConnection().send(errorMessage);
					return false;
				}
			}
		}

		private class ConfirmAuthCodeHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				// CAC masterName authCode
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return false;

				String masterName = tokens[0];

				Room room = masterNameRoomMap.get(masterName);
				if (room == null)
					return false;

				String authCode = tokens[1];
				if (authCode.equals(room.roomMasterAuthCode)) {
					state.getConnection().send(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE);
				} else {
					state.getConnection().send(ProtocolConstants.Room.ERROR_CONFIRM_INVALID_AUTH_CODE);
				}
				return false;
			}
		}

		private class RoomCreateHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				// RC masterName maxPlayers title password description
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 5) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_INVALID_DATA_ENTRY);
					return false;
				}

				String password = tokens[3];
				if (!passwordAllowed && !Utility.isEmpty(password)) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_PASSWORD_NOT_ALLOWED);
					return false;
				}

				String name = tokens[0];
				if (name.length() == 0) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_INVALID_DATA_ENTRY);
					return false;
				}

				int maxPlayers;
				try {
					maxPlayers = Integer.parseInt(tokens[1]);
					if (maxPlayers < 2 || maxPlayers > ProtocolConstants.Room.MAX_ROOM_PLAYERS) {
						state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_INVALID_DATA_ENTRY);
						return false;
					}
				} catch (NumberFormatException e) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_INVALID_DATA_ENTRY);
					return false;
				}

				String title = tokens[2];
				if (title.length() == 0) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_INVALID_DATA_ENTRY);
					return false;
				}

				Room newRoom = null;
				String errorMsg = null;
				if (masterNameRoomMap.size() >= maxRooms) {
					errorMsg = ProtocolConstants.Room.ERROR_ROOM_CREATE_BEYOND_LIMIT;
				} else if (masterNameRoomMap.containsKey(name)) {
					errorMsg = ProtocolConstants.Room.ERROR_ROOM_CREATE_DUPLICATED_NAME;
				} else {
					newRoom = new Room();
					if (masterNameRoomMap.putIfAbsent(name, newRoom) != null) {
						errorMsg = ProtocolConstants.Room.ERROR_ROOM_CREATE_DUPLICATED_NAME;
					}
				}
				if (errorMsg != null) {
					state.getConnection().send(errorMsg);
					return false;
				}

				newRoom.playersByName.put(name, state);

				newRoom.roomMaster = state;
				newRoom.title = title;
				newRoom.maxPlayers = maxPlayers;
				newRoom.password = password;
				newRoom.description = tokens[4];

				state.room = newRoom;

				state.name = name;
				state.messageHandlers = sessionHandlers;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.COMMAND_ROOM_CREATE);
				sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

				newRoom.roomMasterAuthCode = Utility.makeAuthCode();
				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_MASTER_AUTH_CODE);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(newRoom.roomMasterAuthCode);

				appendLoginMessage(sb);

				state.getConnection().send(sb.toString());

				return true;
			}
		}

		private class LoginHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(final PlayerState state, String argument) {
				// LI loginName "masterName" password
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);

				if (tokens.length < 2)
					return false;

				String loginName = tokens[0];
				if (Utility.isEmpty(loginName)) {
					return false;
				}

				String masterName = tokens[1];
				if (Utility.isEmpty(masterName)) {
					// default room
					masterName = "";
				}

				if (masterName.equals(loginName)) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}

				Room room = masterNameRoomMap.get(masterName);
				if (room == null) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_ROOM_NOT_EXIST);
					return false;
				}

				String sentPassword = tokens.length == 3 ? tokens[2] : null;
				if (!Utility.isEmpty(room.password)) {
					if (sentPassword == null) {
						state.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_PASSWORD_REQUIRED);
						return true;
					}
					if (!room.password.equals(sentPassword)) {
						state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_ENTER_PASSWORD_FAIL);
						return true;
					}
				}

				if (room.playersByName.size() >= room.maxPlayers) {
					// 最大人数を超えたので接続を拒否します
					state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_BEYOND_CAPACITY);
					return false;
				}

				if (room.playersByName.putIfAbsent(loginName, state) != null) {
					// 同名のユーザーが存在するので接続を拒否します
					state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}

				state.messageHandlers = sessionHandlers;
				state.name = loginName;

				state.room = room;

				final String notify = ProtocolConstants.Room.NOTIFY_USER_ENTERED + ProtocolConstants.ARGUMENT_SEPARATOR + loginName;
				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						if (p != state)
							p.getConnection().send(notify);
					}
				});

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Room.COMMAND_LOGIN);
				room.appendRoomInfo(sb);

				sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
				room.appendNotifyUserList(sb);

				appendLoginMessage(sb);

				state.getConnection().send(sb.toString());

				return true;
			}
		}

		private class LogoutHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				return false;
			}
		}

		private class ChatHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = state.room;
				if (room == null)
					return false;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.COMMAND_CHAT);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append('<').append(state.name).append("> ");
				sb.append(argument);
				final String message = sb.toString();
				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(message);
					}
				});
				return true;
			}
		}

		private class PingHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				state.getConnection().send(ProtocolConstants.Room.COMMAND_PINGBACK + ProtocolConstants.ARGUMENT_SEPARATOR + argument);
				return true;
			}
		}

		private class InformPingHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(final PlayerState state, String argument) {
				Room room = state.room;
				if (room == null)
					return false;

				try {
					Integer.parseInt(argument);
					final String message = ProtocolConstants.Room.COMMAND_INFORM_PING + ProtocolConstants.ARGUMENT_SEPARATOR + state.name
							+ ProtocolConstants.ARGUMENT_SEPARATOR + argument;
					room.forEachPlayer(new IClientStateAction<PlayerState>() {
						@Override
						public void action(PlayerState p) {
							if (p != state)
								p.getConnection().send(message);
						}
					});
				} catch (NumberFormatException e) {
				}
				return true;
			}
		}

		private class InformTunnelPortHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = state.room;
				if (room == null)
					return false;

				try {
					int port = Integer.parseInt(argument);
					InetSocketAddress remoteEP = new InetSocketAddress(state.getConnection().getRemoteAddress().getAddress(), port);
					state.tunnelState = notYetLinkedTunnels.remove(remoteEP);

					if (state.tunnelState != null) {
						state.getConnection().send(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_UDP_PORT);
						state.tunnelState.room = room;
						state.tunnelState.playerName = state.name;
					}
				} catch (NumberFormatException e) {
				}
				return true;
			}
		}

		private class MacAddressPlayerHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String macAddress) {
				Room room = state.room;
				if (room == null)
					return false;
				TunnelState tunnelState = room.tunnelsByMacAddress.get(macAddress);
				if (tunnelState == null)
					return true;
				if (Utility.isEmpty(tunnelState.playerName))
					return true;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(macAddress);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(tunnelState.playerName);

				state.connection.send(sb.toString());
				return true;
			}
		}

		private class RoomUpdateHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(final PlayerState state, String argument) {
				Room room = state.room;
				if (room == null || state != room.roomMaster)
					return false;

				// RU maxPlayers title "password" "description"
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 4) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_INVALID_DATA_ENTRY);
					return true;
				}
				String password = tokens[2];
				if (!passwordAllowed && !Utility.isEmpty(password)) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_PASSWORD_NOT_ALLOWED);
					return true;
				}

				room.maxPlayers = Math.min(Integer.parseInt(tokens[0]), ProtocolConstants.Room.MAX_ROOM_PLAYERS);
				room.title = tokens[1];
				room.password = password;
				room.description = tokens[3];

				state.getConnection().send(ProtocolConstants.Room.COMMAND_ROOM_UPDATE);

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
				room.appendRoomInfo(sb);

				final String notify = sb.toString();

				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						if (p != state)
							p.getConnection().send(notify);
					}
				});

				return true;
			}
		}

		private class RoomKickPlayerHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = state.room;
				if (room == null || state != room.roomMaster)
					return false;

				String name = argument;
				if (Utility.equals(name, state.name))
					return true;

				PlayerState kickedPlayer = null;
				synchronized (room.playersByName) {
					kickedPlayer = room.playersByName.remove(name);
					if (kickedPlayer == null)
						return true;
				}

				final String notify = ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED + ProtocolConstants.ARGUMENT_SEPARATOR + name;

				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(notify);
					}
				});

				kickedPlayer.getConnection().send(
						ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED + ProtocolConstants.ARGUMENT_SEPARATOR + name);
				kickedPlayer.getConnection().disconnect();

				return true;
			}
		}

		private class RoomMasterTransferHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = state.room;
				if (room == null || state != room.roomMaster)
					return false;

				String name = argument;
				if (Utility.equals(name, state.name))
					return true;

				PlayerState newRoomMaster = room.playersByName.get(name);
				if (newRoomMaster == null)
					return true;

				if (masterNameRoomMap.putIfAbsent(name, room) != null) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_TRANSFER_DUPLICATED_NAME);
					return true;
				}

				masterNameRoomMap.remove(state.name);
				room.roomMaster = newRoomMaster;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
				room.appendRoomInfo(sb);

				final String notify = sb.toString();
				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(notify);
					}
				});

				room.roomMasterAuthCode = Utility.makeAuthCode();
				newRoomMaster.getConnection().send(
						ProtocolConstants.Room.NOTIFY_ROOM_MASTER_AUTH_CODE + ProtocolConstants.ARGUMENT_SEPARATOR
								+ room.roomMasterAuthCode);

				return true;
			}
		}

		private class SearchProtocolHandler implements IServerMessageHandler<PlayerState> {
			String errorMessage = ProtocolConstants.ERROR_PROTOCOL_MISMATCH + ProtocolConstants.ARGUMENT_SEPARATOR
					+ ProtocolConstants.PROTOCOL_NUMBER;

			@Override
			public boolean process(PlayerState state, String argument) {
				if (ProtocolConstants.PROTOCOL_NUMBER.equals(argument)) {
					state.messageHandlers = searchLoginHandlers;
					return true;
				} else {
					state.getConnection().send(errorMessage);
					return false;
				}
			}
		}

		private class SearchLoginHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				if (Search.MODE_PARTICIPANT.equals(argument)) {
					state.messageHandlers = searchHandlers;
				} else {
					return false;
				}
				return true;
			}
		}

		private class SearchHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				InetSocketAddress address = state.getConnection().getLocalAddress();
				StringBuilder sb = new StringBuilder();
				for (Entry<String, Room> entry : masterNameRoomMap.entrySet()) {
					String masterName = entry.getKey();
					Room room = entry.getValue();

					sb.append(ProtocolConstants.Search.COMMAND_SEARCH);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(":").append(address.getPort());
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(masterName);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.title);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.playersByName.size());
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.maxPlayers);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(!Utility.isEmpty(room.password) ? "Y" : "N");
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.description.replace("\n", " "));
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
				}
				sb.append(ProtocolConstants.Search.COMMAND_SEARCH);

				state.getConnection().send(sb.toString());

				return false;
			}
		}
	}

	private static class TunnelState implements IClientState {

		private ISocketConnection connection;
		private Room room;
		private String playerName;
		private long lastTunnelTime;

		TunnelState(ISocketConnection connection) {
			this.connection = connection;
		}

		@Override
		public ISocketConnection getConnection() {
			return connection;
		}
	}

	private class TunnelHandler implements IAsyncServerHandler<TunnelState> {
		@Override
		public void serverStartupFinished() {
		}

		@Override
		public void serverShutdownFinished() {
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public TunnelState createState(ISocketConnection connection) {
			TunnelState state = new TunnelState(connection);
			notYetLinkedTunnels.put(connection.getRemoteAddress(), state);
			return state;
		}

		@Override
		public void disposeState(TunnelState state) {
			state.room = null;
			state.playerName = "";
			notYetLinkedTunnels.remove(state.getConnection().getRemoteAddress());
		}

		@Override
		public boolean processIncomingData(TunnelState state, PacketData data) {
			InetSocketAddress remoteEP = state.getConnection().getRemoteAddress();

			ByteBuffer packet = data.getBuffer();
			if (!Utility.isPspPacket(packet)) {
				if (notYetLinkedTunnels.containsKey(remoteEP)) {
					state.getConnection().send(Integer.toString(remoteEP.getPort()));
				}
				return true;
			}

			Room room = state.room;
			if (room == null)
				return true;

			String destMac = Utility.makeMacAddressString(packet, 0, false);
			String srcMac = Utility.makeMacAddressString(packet, 6, false);

			room.tunnelsByMacAddress.put(srcMac, state);

			if (Utility.isMacBroadCastAddress(destMac)) {
				for (Entry<String, PlayerState> entry : room.playersByName.entrySet()) {
					PlayerState sendTo = entry.getValue();
					if (sendTo.tunnelState != null && sendTo.tunnelState != state) {
						packet.position(0);
						sendTo.tunnelState.getConnection().send(packet);
						sendTo.tunnelState.lastTunnelTime = System.currentTimeMillis();
					}
				}
			} else {
				TunnelState sendTo = room.tunnelsByMacAddress.get(destMac);
				if (sendTo != null) {
					sendTo.getConnection().send(packet);
					sendTo.lastTunnelTime = System.currentTimeMillis();
				}
			}

			return true;
		}
	}
}
