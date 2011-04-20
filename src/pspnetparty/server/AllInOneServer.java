package pspnetparty.server;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;

import pspnetparty.lib.ICommandHandler;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.engine.LobbyEngine;
import pspnetparty.lib.engine.PortalEngine;
import pspnetparty.lib.engine.RoomEngine;
import pspnetparty.lib.engine.SearchEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.AsyncUdpServer;
import pspnetparty.lib.socket.IProtocol;

public class AllInOneServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s オールインワンサーバー  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("プロトコル: " + IProtocol.NUMBER);

		String iniFileName = "AllInOneServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("設定INIファイル名: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 20000);
		if (port < 1 || port > 65535) {
			System.out.println("ポート番号が不正です: " + port);
			return;
		}
		System.out.println("ポート: " + port);

		String hostname = settings.get("HostName", "localhost");
		if (Utility.isEmpty(hostname)) {
			System.out.println("ホスト名が設定されていません: " + hostname);
			return;
		}
		System.out.println("ホスト名: " + hostname);

		final String loginMessageFile = settings.get(IniConstants.LOGIN_MESSAGE_FILE, "");
		System.out.println("ルームログインメッセージファイル : " + loginMessageFile);

		ILogger logger = ServerUtils.createLogger();

		AsyncTcpServer tcpServer = new AsyncTcpServer(100000);
		AsyncUdpServer udpServer = new AsyncUdpServer();

		{
			int maxRooms = settings.get(IniConstants.MAX_ROOMS, 10);
			if (maxRooms < 1) {
				System.out.println("部屋数が不正です: " + maxRooms);
				return;
			}
			System.out.println("最大部屋数: " + maxRooms);

			RoomEngine roomEngine = new RoomEngine(tcpServer, udpServer, logger);
			roomEngine.setMaxRooms(maxRooms);
			roomEngine.setLoginMessageFile(loginMessageFile);
		}
		{
			int maxUsers = settings.get(IniConstants.MAX_USERS, 30);
			if (maxUsers < 1) {
				System.out.println("最大検索ユーザー数が不正です: " + maxUsers);
				return;
			}
			System.out.println("最大検索ユーザー数: " + maxUsers);

			int maxSearchResults = settings.get(IniConstants.MAX_SEARCH_RESULTS, 50);
			if (maxSearchResults < 1) {
				System.out.println("最大検索件数が不正です: " + maxSearchResults);
				return;
			}
			System.out.println("最大検索件数: " + maxSearchResults);

			int descriptionMaxLength = settings.get(IniConstants.DESCRIPTION_MAX_LENGTH, 100);
			if (descriptionMaxLength < 1) {
				System.out.println("部屋の詳細・備考の最大サイズが不正です: " + descriptionMaxLength);
				return;
			}
			System.out.println("部屋の詳細・備考の最大文字数: " + descriptionMaxLength);

			SearchEngine searchEngine = new SearchEngine(tcpServer, logger);
			searchEngine.setMaxUsers(maxUsers);
			searchEngine.setMaxSearchResults(maxSearchResults);
			searchEngine.setDescriptionMaxLength(descriptionMaxLength);
		}
		{
			LobbyEngine lobbyEngine = new LobbyEngine(tcpServer, logger);
			lobbyEngine.setTitle("ロビー");
			lobbyEngine.setLoginMessageFile(loginMessageFile);
		}

		ini.saveToIni();

		PortalEngine portalEngine = new PortalEngine(tcpServer, logger);
		HashSet<String> addresses = new HashSet<String>();
		addresses.add(hostname + ":" + port);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);
		udpServer.startListening(bindAddress);

		portalEngine.connectRoomServers(addresses);
		portalEngine.connectSearchServers(addresses);
		portalEngine.connectLobbyServers(addresses);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tサーバーを終了させる");
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
		udpServer.stopListening();
	}
}
