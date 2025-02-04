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
package pspnetparty.client.swt.config;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import pspnetparty.lib.socket.TransportLayer;
import pspnetparty.wlan.JnetPcapWlanDevice;
import pspnetparty.wlan.NativeWlanDevice;
import pspnetparty.wlan.WlanLibrary;

public class MiscSettingPage extends PreferencePage {

	public static final String PAGE_ID = "settings";

	private IniSettings settings;

	private Button startupWindowArena;
	private Button startupWindowRoom;
	private Button arenaAutoLoginRoomList;
	private Button arenaAutoLoginLobby;
	private Button appCloseConfirmCheck;
	private Button balloonNotifyRoomCheck;
	private Button balloonNotifyLobbyCheck;
	private Button balloonLobbyEnterExitCheck;
	private Button privatePortalServerUseCheck;
	private Text privatePortalServerAddress;
	private Button myRoomAllowEmptyMasterNameCheck;

	private Button libraryPnpWlan;
	private Button libraryJnetPcap;
	private Button libraryProxy;

	private Button ssidAutoScan;
	private Button tunnelTransportTcp;
	private Button tunnelTransportUdp;

	public MiscSettingPage(IniSettings settings) {
		super("various settings");
		this.settings = settings;

		noDefaultAndApplyButton();
	}

	@Override
	protected Control createContents(Composite parent) {
		GridLayout gridLayout;
		GridData gridData;

		Composite configContainer = new Composite(parent, SWT.NONE);
		gridLayout = new GridLayout(1, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 6;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = 2;
		configContainer.setLayout(gridLayout);

		Group startupWindowGroup = new Group(configContainer, SWT.SHADOW_IN);
		startupWindowGroup.setText("Startup window");
		startupWindowGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		startupWindowGroup.setLayout(gridLayout);

		startupWindowArena = new Button(startupWindowGroup, SWT.RADIO | SWT.FLAT);
		startupWindowArena.setText("Arena");

		startupWindowRoom = new Button(startupWindowGroup, SWT.RADIO | SWT.FLAT);
		startupWindowRoom.setText("Room");

		Group arenaAutoLoginGroup = new Group(configContainer, SWT.SHADOW_IN);
		arenaAutoLoginGroup.setText("Connect automatically when opening the arena window");
		arenaAutoLoginGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		arenaAutoLoginGroup.setLayout(gridLayout);

		arenaAutoLoginRoomList = new Button(arenaAutoLoginGroup, SWT.CHECK | SWT.FLAT);
		arenaAutoLoginRoomList.setText("Room list");

		arenaAutoLoginLobby = new Button(arenaAutoLoginGroup, SWT.CHECK | SWT.FLAT);
		arenaAutoLoginLobby.setText("lobby");

		appCloseConfirmCheck = new Button(configContainer, SWT.CHECK | SWT.FLAT);
		appCloseConfirmCheck.setText("Check when closing the application");

		Group configTaskTrayBalloonGroup = new Group(configContainer, SWT.SHADOW_IN);
		configTaskTrayBalloonGroup.setText("Notify with a balloon from the task tray");
		configTaskTrayBalloonGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		configTaskTrayBalloonGroup.setLayout(new GridLayout(1, false));

		balloonNotifyRoomCheck = new Button(configTaskTrayBalloonGroup, SWT.CHECK | SWT.FLAT);
		balloonNotifyRoomCheck.setText("Playroom log message");

		balloonNotifyLobbyCheck = new Button(configTaskTrayBalloonGroup, SWT.CHECK | SWT.FLAT);
		balloonNotifyLobbyCheck.setText("Lobby log message");

		balloonLobbyEnterExitCheck = new Button(configTaskTrayBalloonGroup, SWT.CHECK | SWT.FLAT);
		balloonLobbyEnterExitCheck.setText("Notification of entry / exit logs for the entire lobby");
		gridData = new GridData(SWT.LEFT, SWT.CENTER, true, false);
		gridData.horizontalIndent = 15;
		balloonLobbyEnterExitCheck.setLayoutData(gridData);

		Group ssidGroup = new Group(configContainer, SWT.SHADOW_IN);
		ssidGroup.setText("SSID function");
		ssidGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(3, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		ssidGroup.setLayout(gridLayout);

		libraryPnpWlan = new Button(ssidGroup, SWT.RADIO);
		libraryPnpWlan.setText("ON (PnpWlan)");
		libraryPnpWlan.setEnabled(NativeWlanDevice.LIBRARY.isReady());

		libraryJnetPcap = new Button(ssidGroup, SWT.RADIO);
		libraryJnetPcap.setText("OFF (jNetPcap)");
		libraryJnetPcap.setEnabled(JnetPcapWlanDevice.LIBRARY.isReady());

		libraryProxy = new Button(ssidGroup, SWT.RADIO);
		libraryProxy.setText("Proxy (special mode)");

		ssidAutoScan = new Button(ssidGroup, SWT.CHECK | SWT.FLAT);
		ssidAutoScan.setText("Automatically start SSID scan");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		// gridData.verticalIndent = 6;
		ssidAutoScan.setLayoutData(gridData);

		Group configTunnelGroup = new Group(configContainer, SWT.SHADOW_IN);
		configTunnelGroup.setText("Tunnel communication");
		configTunnelGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(3, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		configTunnelGroup.setLayout(gridLayout);

		Label tunnelTransportLayer = new Label(configTunnelGroup, SWT.NONE);
		tunnelTransportLayer.setText("Transport layer: ");

		tunnelTransportTcp = new Button(configTunnelGroup, SWT.RADIO | SWT.FLAT);
		tunnelTransportTcp.setText("TCP");

		tunnelTransportUdp = new Button(configTunnelGroup, SWT.RADIO | SWT.FLAT);
		tunnelTransportUdp.setText("UDP");

		Group configPortalServerGroup = new Group(configContainer, SWT.SHADOW_IN);
		configPortalServerGroup.setText("Portal Server");
		configPortalServerGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		configPortalServerGroup.setLayout(gridLayout);

		privatePortalServerUseCheck = new Button(configPortalServerGroup, SWT.CHECK | SWT.FLAT);
		privatePortalServerUseCheck.setText("Specify a portal server (otherwise use a public portal server)");
		privatePortalServerUseCheck.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));

		Label configPortalServerAddressLabel = new Label(configPortalServerGroup, SWT.NONE);
		configPortalServerAddressLabel.setText("Server address");
		configPortalServerAddressLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

		privatePortalServerAddress = new Text(configPortalServerGroup, SWT.BORDER | SWT.SINGLE);
		privatePortalServerAddress.setTextLimit(100);
		privatePortalServerAddress.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Group configMyRoomGroup = new Group(configContainer, SWT.SHADOW_IN);
		configMyRoomGroup.setText("my room");
		configMyRoomGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(1, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 5;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		configMyRoomGroup.setLayout(gridLayout);

		myRoomAllowEmptyMasterNameCheck = new Button(configMyRoomGroup, SWT.CHECK | SWT.FLAT);
		myRoomAllowEmptyMasterNameCheck.setText("Allows you to log in even if you omit the room owner name of the address");
		myRoomAllowEmptyMasterNameCheck.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		if (settings.isStartupWindowArena())
			startupWindowArena.setSelection(true);
		else
			startupWindowRoom.setSelection(true);
		appCloseConfirmCheck.setSelection(settings.isNeedAppCloseConfirm());
		balloonNotifyRoomCheck.setSelection(settings.isBallonNotifyRoom());
		balloonNotifyLobbyCheck.setSelection(settings.isBallonNotifyLobby());
		balloonLobbyEnterExitCheck.setSelection(settings.isBalloonLobbyEnterExit());
		arenaAutoLoginRoomList.setSelection(settings.isArenaAutoLoginRoomList());
		arenaAutoLoginLobby.setSelection(settings.isArenaAutoLoginLobby());

		privatePortalServerUseCheck.setSelection(settings.isPrivatePortalServerUse());
		privatePortalServerAddress.setText(settings.getPrivatePortalServerAddress());
		privatePortalServerAddress.setEnabled(settings.isPrivatePortalServerUse());

		myRoomAllowEmptyMasterNameCheck.setSelection(settings.isMyRoomAllowNoMasterName());

		WlanLibrary library = settings.getWlanLibrary();
		if (library == NativeWlanDevice.LIBRARY) {
			libraryPnpWlan.setSelection(true);
		} else if (library == JnetPcapWlanDevice.LIBRARY) {
			libraryJnetPcap.setSelection(true);
		} else {
			libraryProxy.setSelection(true);
		}

		ssidAutoScan.setSelection(settings.isSsidAutoScan());
		switch (settings.getTunnelTransportLayer()) {
		case TCP:
			tunnelTransportTcp.setSelection(true);
			break;
		case UDP:
			tunnelTransportUdp.setSelection(true);
			break;
		}

		privatePortalServerUseCheck.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				privatePortalServerAddress.setEnabled(privatePortalServerUseCheck.getSelection());
			}
		});

		return configContainer;
	}

	private void reflectValues() {
		if (!isControlCreated())
			return;

		settings.setStartupWindowArena(startupWindowArena.getSelection());
		settings.setNeedAppCloseConfirm(appCloseConfirmCheck.getSelection());
		settings.setBallonNotifyRoom(balloonNotifyRoomCheck.getSelection());
		settings.setBallonNotifyLobby(balloonNotifyLobbyCheck.getSelection());
		settings.setBalloonLobbyEnterExit(balloonLobbyEnterExitCheck.getSelection());
		settings.setArenaAutoLoginSearch(arenaAutoLoginRoomList.getSelection());
		settings.setArenaAutoLoginLobby(arenaAutoLoginLobby.getSelection());
		settings.setPrivatePortalServerUse(privatePortalServerUseCheck.getSelection());
		settings.setPrivatePortalServerAddress(privatePortalServerAddress.getText());

		if (libraryPnpWlan.getSelection()) {
			settings.setWlanLibrary(NativeWlanDevice.LIBRARY);
		} else if (libraryJnetPcap.getSelection()) {
			settings.setWlanLibrary(JnetPcapWlanDevice.LIBRARY);
		} else {
			settings.setWlanLibrary(null);
		}
		settings.setSsidAutoScan(ssidAutoScan.getSelection());
		if (tunnelTransportTcp.getSelection()) {
			settings.setTunnelTransportLayer(TransportLayer.TCP);
		} else {
			settings.setTunnelTransportLayer(TransportLayer.UDP);
		}
	}

	@Override
	protected void performApply() {
		super.performApply();
		reflectValues();
	}

	@Override
	public boolean performOk() {
		reflectValues();
		return super.performOk();
	}
}
