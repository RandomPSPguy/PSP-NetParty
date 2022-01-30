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
package pspnetparty.server;

import java.net.InetSocketAddress;
import java.util.HashMap;

import pspnetparty.lib.ICommandHandler;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniPublicServerRegistry;
import pspnetparty.lib.engine.RoomEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.AsyncUdpServer;
import pspnetparty.lib.socket.IProtocol;

public class RoomServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s Room Server  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("protocol: " + IProtocol.NUMBER);

		String iniFileName = "RoomServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("Setting INI file name: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 30000);
		if (port < 1 || port > 65535) {
			System.out.println("The port number is invalid: " + port);
			return;
		}
		System.out.println("port: " + port);

		int maxRooms = settings.get(IniConstants.MAX_ROOMS, 10);
		if (maxRooms < 1) {
			System.out.println("The number of rooms is incorrect: " + maxRooms);
			return;
		}
		System.out.println("Maximum number of rooms: " + maxRooms);

		final String loginMessageFile = settings.get(IniConstants.LOGIN_MESSAGE_FILE, "");
		System.out.println("Login message file : " + loginMessageFile);

		ini.saveToIni();

		ILogger logger = ServerUtils.createLogger();
		AsyncTcpServer tcpServer = new AsyncTcpServer(40000);
		AsyncUdpServer udpServer = new AsyncUdpServer();

		final RoomEngine engine = new RoomEngine(tcpServer, udpServer, logger, new IniPublicServerRegistry());
		engine.setMaxRooms(maxRooms);
		engine.setLoginMessageFile(loginMessageFile);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);
		udpServer.startListening(bindAddress);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tShut down the server");
				System.out.println("list\n\tView current room list");
				System.out.println("status\n\tShow current server status");
				System.out.println("set MaxRooms number of rooms\n\tSet the maximum number of rooms to the number of rooms");
				System.out.println("notify message\n\tAnnounce a message to everyone");
				System.out.println("destroy Room owner name\n\tDismantle the room with the name of the room owner");
				System.out.println("goma room owner name\n\tIncrease the maximum number of people in a room with a room owner");
				System.out.println("myroom list\n\tList of my rooms");
				System.out.println("myroom destroy Room address\n\tDelete the registration of the specified My Room");
				System.out.println("portal list\n\tList of registered portals");
				System.out.println("portal accept\n\tStart accepting portal registration");
				System.out.println("portal reject\n\tStop accepting portal registration");
			}
		});
		handlers.put("list", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println(engine.allRoomsToString());
			}
		});
		handlers.put("status", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("port: " + port);
				System.out.println("# of rooms: " + engine.getRoomCount() + " / " + engine.getMaxRooms());
				System.out.println("Login message file : " + loginMessageFile);
				System.out.println("Portal registration: " + (engine.isAcceptingPortal() ? "Accepting" : "Stop"));
			}
		});
		handlers.put("set", new ICommandHandler() {
			@Override
			public void process(String argument) {
				String[] tokens = argument.split(" ");
				if (tokens.length != 2)
					return;

				String key = tokens[0];
				String value = tokens[1];
				if (IniConstants.MAX_ROOMS.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						if (max < 0)
							return;
						engine.setMaxRooms(max);
						System.out.println("Maximum number of rooms " + max + " set to");
					} catch (NumberFormatException e) {
					}
				}
			}
		});
		handlers.put("notify", new ICommandHandler() {
			@Override
			public void process(String message) {
				if (Utility.isEmpty(message))
					return;

				engine.notifyAllPlayers(message);
				System.out.println("Announced the message : " + message);
			}
		});
		handlers.put("destroy", new ICommandHandler() {
			@Override
			public void process(String masterName) {
				if (Utility.isEmpty(masterName))
					return;

				if (engine.destroyRoom(masterName)) {
					System.out.println("I dismantled the room : " + masterName);
				} else {
					System.out.println("Could not dismantle the room");
				}
			}
		});
		handlers.put("goma", new ICommandHandler() {
			@Override
			public void process(String masterName) {
				if (Utility.isEmpty(masterName))
					return;

				engine.hirakeGoma(masterName);
				System.out.println("You can now enter the room : " + masterName);
			}
		});
		handlers.put("myroom", new ICommandHandler() {
			@Override
			public void process(String argument) {
				String[] tokens = argument.trim().split(" ");
				if (tokens.length == 0)
					return;

				String action = tokens[0].toLowerCase();
				if ("list".equals(action)) {
					System.out.println("[List of registered my rooms]");
					System.out.println(engine.myRoomsToString());
				} else if ("destroy".equals(action)) {
					if (tokens.length == 2) {
						String roomAddress = tokens[1];
						if (engine.destroyMyRoom(roomAddress)) {
							System.out.println("My room registration has been deleted : " + roomAddress);
						} else {
							System.out.println("Could not delete my room registration");
						}
					}
				}
			}
		});
		handlers.put("portal", new ICommandHandler() {
			@Override
			public void process(String argument) {
				String action = argument.trim();
				if ("list".equalsIgnoreCase(action)) {
					System.out.println("[List of registered portal servers]");
					for (InetSocketAddress address : engine.getPortalAddresses()) {
						System.out.println(address.getAddress().getHostAddress() + ":" + address.getPort());
					}
				} else if ("accept".equalsIgnoreCase(action)) {
					engine.setAcceptingPortal(true);
					System.out.println("We have started accepting portal connections");
				} else if ("reject".equalsIgnoreCase(action)) {
					engine.setAcceptingPortal(false);
					System.out.println("We have stopped accepting portal connections");
				}
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
		udpServer.stopListening();
	}
}
