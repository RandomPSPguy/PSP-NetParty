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
import pspnetparty.lib.engine.LobbyEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.IProtocol;

public class LobbyServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s lobby server  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("protocol: " + IProtocol.NUMBER);

		String iniFileName = "LobbyServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("Settings INI file name: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 60000);
		if (port < 1 || port > 65535) {
			System.out.println("Incorrect port number: " + port);
			return;
		}
		System.out.println("port: " + port);

		final String loginMessageFile = settings.get(IniConstants.LOGIN_MESSAGE_FILE, "");
		System.out.println("Login message file: " + loginMessageFile);

		ini.saveToIni();

		ILogger logger = ServerUtils.createLogger();
		AsyncTcpServer tcpServer = new AsyncTcpServer(40000);

		final LobbyEngine engine = new LobbyEngine(tcpServer, logger, new IniPublicServerRegistry());
		engine.setLoginMessageFile(loginMessageFile);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tShut down the server");
				System.out.println("status\n\tShow current server status ");
				System.out.println("notify message\n\tAnnounce a message to everyone");
				System.out.println("portal list\n\tList of registered portals");
				System.out.println("portal accept\n\tStart accepting portal registration");
				System.out.println("portal reject\n\tStop accepting portal registration");
			}
		});
		handlers.put("status", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("port: " + port);
				System.out.println("# of users: " + engine.getCurrentPlayers());
				System.out.println("Login message file : " + loginMessageFile);
				System.out.println("Portal registration: " + (engine.isAcceptingPortal() ? "Accepting" : "stopping"));
			}
		});
		handlers.put("notify", new ICommandHandler() {
			@Override
			public void process(String message) {
				if (Utility.isEmpty(message))
					return;

				engine.notifyAllUsers(message);
				System.out.println("Announced the message : " + message);
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
	}
}
