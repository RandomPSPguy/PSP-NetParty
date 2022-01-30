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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;

import pspnetparty.lib.ICommandHandler;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniPublicServerRegistry;
import pspnetparty.lib.engine.PortalEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.IProtocol;

public class PortalServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s Portal server  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("protocol: " + IProtocol.NUMBER);

		String iniFileName = "PortalServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("Setting INI file name: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 50000);
		if (port < 1 || port > 65535) {
			System.out.println("The port number is invalid: " + port);
			return;
		}
		System.out.println("port: " + port);

		ini.saveToIni();

		ILogger logger = ServerUtils.createLogger();
		AsyncTcpServer tcpServer = new AsyncTcpServer(40000);

		final PortalEngine engine = new PortalEngine(tcpServer, logger);

		connect(engine);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tShut down the server");
				System.out.println("status\n\tShow current server status");
				System.out.println("rooms\n\tList of room information held");
				System.out.println("server active\n\tList of connected servers");
				System.out.println("server dead\n\tList of unconnected servers");
				System.out.println("server reload\n\tReload the server list to refresh the connection");
				System.out.println("reconnect\n\tAttempt to reconnect with a server that is not connected");
			}
		});
		handlers.put("status", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("ポート: " + port);
				System.out.println(engine.statusToString());
			}
		});
		handlers.put("rooms", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println(engine.allRoomsToString());
			}
		});
		handlers.put("server", new ICommandHandler() {
			@Override
			public void process(String argument) {
				if ("active".equalsIgnoreCase(argument)) {
					System.out.println("[List of connected room servers]");
					printList(engine.listActiveRoomServers());
					System.out.println("[List of connected search servers]");
					printList(engine.listActiveSearchServers());
				} else if ("dead".equalsIgnoreCase(argument)) {
					System.out.println("[List of disconnected room servers]");
					printList(engine.listDeadRoomServers());
					System.out.println("[List of disconnected search servers]");
					printList(engine.listDeadSearchServers());
				} else if ("reload".equalsIgnoreCase(argument)) {
					try {
						connect(engine);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					return;
				}
			}
		});
		handlers.put("reconnect", new ICommandHandler() {
			@Override
			public void process(String argument) {
				engine.reconnectNow();
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
	}

	private static void printList(String[] list) {
		for (String s : list)
			System.out.println(s);
	}

	private static void connect(PortalEngine engine) throws IOException {
		IniPublicServerRegistry publicServer = new IniPublicServerRegistry();
		HashSet<String> addresses = new HashSet<String>();

		for (String address : publicServer.getRoomServers()) {
			addresses.add(address);
		}
		engine.connectRoomServers(addresses);

		addresses.clear();
		for (String address : publicServer.getSearchServers()) {
			addresses.add(address);
		}
		engine.connectSearchServers(addresses);

		addresses.clear();
		for (String address : publicServer.getLobbyServers()) {
			addresses.add(address);
		}
		engine.connectLobbyServers(addresses);
	}
}
