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
import pspnetparty.lib.engine.SearchEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.IProtocol;

public class SearchServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s Search server  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("protocol: " + IProtocol.NUMBER);

		String iniFileName = "SearchServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("Setting INI file name: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 40000);
		if (port < 1 || port > 65535) {
			System.out.println("The port number is invalid: " + port);
			return;
		}
		System.out.println("port: " + port);

		int maxUsers = settings.get(IniConstants.MAX_USERS, 30);
		if (maxUsers < 1) {
			System.out.println("Maximum number of users is invalid: " + maxUsers);
			return;
		}
		System.out.println("Maximum number of users: " + maxUsers);

		final String loginMessageFile = settings.get(IniConstants.LOGIN_MESSAGE_FILE, "");
		System.out.println("Login message file: " + loginMessageFile);

		int maxSearchResults = settings.get(IniConstants.MAX_SEARCH_RESULTS, 50);
		if (maxSearchResults < 1) {
			System.out.println("Maximum number of searches is invalid: " + maxSearchResults);
			return;
		}
		System.out.println("Maximum number of searches: " + maxSearchResults);

		int descriptionMaxLength = settings.get(IniConstants.DESCRIPTION_MAX_LENGTH, 100);
		if (descriptionMaxLength < 1) {
			System.out.println("Room details / remarks maximum size is incorrect: " + descriptionMaxLength);
			return;
		}
		System.out.println("Maximum number of characters in room details / remarks: " + descriptionMaxLength);

		ini.saveToIni();

		ILogger logger = ServerUtils.createLogger();
		AsyncTcpServer tcpServer = new AsyncTcpServer(1000000);

		final SearchEngine engine = new SearchEngine(tcpServer, logger, new IniPublicServerRegistry());
		engine.setMaxUsers(maxUsers);
		engine.setDescriptionMaxLength(descriptionMaxLength);
		engine.setMaxSearchResults(maxSearchResults);
		engine.setLoginMessageFile(loginMessageFile);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tShut down the server");
				System.out.println("status\n\tShow current server status");
				System.out.println("set MaxUsers Number of users\n\tSet maximum number of users");
				System.out.println("set MaxSearchResults number\n\tSet the maximum number of searches");
				System.out.println("set DescriptionMaxLength word count\n\tSet the maximum number of characters for room introduction and remarks");
				System.out.println("notify message\n\tAnnounce a message to everyone ");
				System.out.println("rooms\n\tList of room information held");
				System.out.println("portal list\n\tList of connected portal servers");
				System.out.println("portal accept\n\tStart accepting portal registration");
				System.out.println("portal reject\n\tStop accepting portal registration");
			}
		});
		handlers.put("status", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("port: " + port);
				System.out.println("Number of users: " + engine.getCurrentUsers() + " / " + engine.getMaxUsers());
				System.out.println("Number of registered rooms: " + engine.getRoomEntryCount());
				System.out.println("Maximum number of searches: " + engine.getMaxSearchResults());
				System.out.println("Maximum number of characters for room introduction / remarks: " + engine.getDescriptionMaxLength());
				System.out.println("Login message file : " + loginMessageFile);
				System.out.println("Portal registration: " + (engine.isAcceptingPortal() ? "Accepting" : "Stopping"));
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
				if (IniConstants.MAX_USERS.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						if (max < 0)
							return;
						engine.setMaxUsers(max);
						System.out.println("Maximum number of users " + max + " Set to");
					} catch (NumberFormatException e) {
					}
				} else if (IniConstants.MAX_SEARCH_RESULTS.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						if (max < 1)
							return;
						engine.setMaxSearchResults(max);
						System.out.println("Maximum number of searches " + max + " set to");
					} catch (NumberFormatException e) {
					}
				} else if (IniConstants.DESCRIPTION_MAX_LENGTH.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						if (max < 1)
							return;
						engine.setDescriptionMaxLength(max);
						System.out.println("Maximum number of characters for room introduction / remarks " + max + " set to");
					} catch (NumberFormatException e) {
					}
				}
			}
		});
		handlers.put("rooms", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("[List of all room registrations]");
				System.out.println(engine.allRoomsToString());
			}
		});
		handlers.put("portal", new ICommandHandler() {
			@Override
			public void process(String argument) {
				if ("list".equalsIgnoreCase(argument)) {
					System.out.println("[List of connected portal servers]");
					System.out.println(engine.allPortalsToString());
				} else if ("accept".equalsIgnoreCase(argument)) {
					engine.setAcceptingPortal(true);
					System.out.println("We have started accepting portal connections");
				} else if ("reject".equalsIgnoreCase(argument)) {
					engine.setAcceptingPortal(false);
					System.out.println("We have stopped accepting portal connections");
				}
			}
		});
		handlers.put("notify", new ICommandHandler() {
			@Override
			public void process(String message) {
				if (Utility.isEmpty(message))
					return;

				engine.notifyAllClients(message);
				System.out.println("Announced the message : " + message);
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
	}
}
