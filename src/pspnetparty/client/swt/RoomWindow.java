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
package pspnetparty.client.swt;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import pspnetparty.client.swt.PlayClient.PortalQuery;
import pspnetparty.client.swt.config.MiscSettingPage;
import pspnetparty.client.swt.config.ChatTextPresetsPage;
import pspnetparty.client.swt.config.IPreferenceNodeProvider;
import pspnetparty.client.swt.config.IniAppData;
import pspnetparty.client.swt.config.IniChatTextPresets;
import pspnetparty.client.swt.config.IniSettings;
import pspnetparty.client.swt.config.IniUserProfile;
import pspnetparty.client.swt.message.AdminNotify;
import pspnetparty.client.swt.message.Chat;
import pspnetparty.client.swt.message.ErrorLog;
import pspnetparty.client.swt.message.InfoLog;
import pspnetparty.client.swt.message.LogViewer;
import pspnetparty.client.swt.message.RoomLog;
import pspnetparty.client.swt.message.ServerLog;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.LobbyUserState;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.engine.IMyRoomMasterHandler;
import pspnetparty.lib.engine.MyRoomEngine;
import pspnetparty.lib.engine.PlayRoom;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.IProtocolMessageHandler;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.PacketData;
import pspnetparty.lib.socket.TextProtocolDriver;
import pspnetparty.lib.socket.TransportLayer;
import pspnetparty.wlan.WlanDevice;
import pspnetparty.wlan.WlanLibrary;
import pspnetparty.wlan.WlanNetwork;

public class RoomWindow implements IAppWindow {
	private static final String SECTION_LAN_ADAPTERS = "LanAdapters";
	private static final int MAX_SERVER_HISTORY = 10;
	private static final int DEFAULT_MAX_PLAYERS = 4;

	private static final String INI_ROOM_TITLE = "Title";
	private static final String INI_ROOM_PASSWORD = "Password";
	private static final String INI_ROOM_CAPACITY = "Capacity";
	private static final String INI_ROOM_DESCRIPTION = "Description";
	private static final String INI_ROOM_REMARKS = "Remarks";

	enum SessionState {
		OFFLINE, MY_ROOM_MASTER, CONNECTING_ROOM_PARTICIPANT, ROOM_PARTICIPANT, CONNECTING_ROOM_MASTER, ROOM_MASTER, NEGOTIATING,
	};

	private PlayClient application;
	private IniChatTextPresets chatTextPresets;

	private Shell shell;
	private boolean isActiveWindow;

	private SessionState sessionState;
	private String roomLoginName;
	private String roomMasterName;
	private String roomServerAddressPort;

	private MyRoomEngine myRoomEngine;

	private RoomProtocol roomProtocol = new RoomProtocol();
	private ISocketConnection roomConnection = ISocketConnection.NULL;

	private TunnelProtocol tunnelProtocol = new TunnelProtocol();
	private ISocketConnection tunnelConnection = ISocketConnection.NULL;

	private MyRoomEntryProtocol myRoomEntryProtocol = new MyRoomEntryProtocol();
	private ISocketConnection myRoomEntryConnection = ISocketConnection.NULL;

	private int scanIntervalMillis = 2000;
	private long nextSsidCheckTime = 0L;

	private boolean tunnelIsLinked = false;
	private boolean isPacketCapturing = false;
	private boolean isSSIDScaning = false;
	private boolean isRoomInfoUpdating = false;

	private WlanLibrary currentWlanLibrary;
	private WlanDevice currentWlanDevice = WlanDevice.NULL;
	private ByteBuffer bufferForCapturing = ByteBuffer.allocateDirect(WlanDevice.CAPTURE_BUFFER_SIZE);
	private ArrayList<WlanDevice> wlanAdapterList = new ArrayList<WlanDevice>();
	private HashMap<WlanDevice, String> wlanAdapterMacAddressMap = new HashMap<WlanDevice, String>();

	private HashMap<String, Player> roomPlayerMap = new LinkedHashMap<String, Player>();
	private HashMap<String, TraficStatistics> traficStatsMap = new HashMap<String, TraficStatistics>();

	public int actualSentBytes;
	public int actualRecievedBytes;

	private Thread packetMonitorThread;
	private Thread packetCaptureThread;
	private Thread wlanScannerThread;

	private Widgets widgets;

	private ComboHistoryManager roomServerHistoryManager;
	private ComboHistoryManager roomAddressHistoryManager;

	public RoomWindow(PlayClient application) {
		this.application = application;

		shell = new Shell(SwtUtils.DISPLAY);
		shell.setText("Room - " + AppConstants.APP_NAME);

		chatTextPresets = new IniChatTextPresets(application.getIniSection(IniChatTextPresets.SECTION_NAME));

		myRoomEngine = new MyRoomEngine(new MyRoomServerHandler());

		widgets = new Widgets();
		widgets.initWidgets();

		IniSettings iniSettings = application.getSettings();
		IniAppData iniAppData = application.getAppData();

		widgets.formMyRoomModePortSpinner.setSelection(iniSettings.getMyRoomPort());
		widgets.formMyRoomModeHostText.setText(iniSettings.getMyRoomHostName());

		widgets.initWidgetListeners();
		widgets.initMenus();

		changeStateTo(SessionState.OFFLINE);

		shell.setMinimumSize(new Point(650, 400));
		iniAppData.restoreMainWindow(shell);

		String[] stringList;

		stringList = iniSettings.getRoomServerList();
		ComboHistoryManager.addList(widgets.formManualModeRoomServerCombo, stringList);
		stringList = iniAppData.getRoomServerHistory();
		roomServerHistoryManager = new ComboHistoryManager(widgets.formManualModeRoomServerCombo, stringList, MAX_SERVER_HISTORY, true);

		stringList = iniSettings.getRoomAddressList();
		ComboHistoryManager.addList(widgets.formManualModeRoomAddressCombo, stringList);
		stringList = iniAppData.getRoomAddressHistory();
		roomAddressHistoryManager = new ComboHistoryManager(widgets.formManualModeRoomAddressCombo, stringList, MAX_SERVER_HISTORY, true);

		application.addConfigPageProvider(new IPreferenceNodeProvider() {
			@Override
			public PreferenceNode createPreferenceNode() {
				return new PreferenceNode("chatpresets",
						new ChatTextPresetsPage(RoomWindow.this.application.getSettings(), chatTextPresets));
			}
		});

		currentWlanLibrary = iniSettings.getWlanLibrary();
		refreshLanAdapterList();

		initBackgroundThreads();
	}

	public Shell getShell() {
		return shell;
	}

	private class Widgets {
		private Composite toolBarContainer;
		private SashForm mainSashForm;

		private Composite formModeSwitchContainer;
		private StackLayout roomModeStackLayout;
		private Combo formModeSelectionCombo;
		private Composite formAutoModeContainer;
		private Text formAutoModeServerAddress;
		private Button formAutoModeRoomButton;
		private Composite formManualModeContainer;
		private Combo formManualModeRoomServerCombo;
		private Button formManualModeRoomServerButton;
		private Combo formManualModeRoomAddressCombo;
		private Button formManualModeRoomAddressButton;
		private Composite formMyRoomModeContainer;
		private Text formMyRoomModeHostText;
		private Spinner formMyRoomModePortSpinner;
		private Button formMyRoomModeStartButton;
		private Text formMyRoomModeRoomServer;
		private Button formMyRoomModeEntryButton;
		private Text formMasterNameText;
		private Text formTitleText;
		private Text formPasswordText;
		private Spinner formMaxPlayersSpiner;
		private Text formTimestampText;
		private Text formDescriptionText;
		private Text formRemarksText;
		private Button formEditSaveButton;
		private Button formEditLoadButton;
		private Button formEditSubmitButton;

		private SashForm centerSashForm;
		private Combo wlanAdapterListCombo;
		private Button wlanPspCommunicationButton;
		private TableViewer packetMonitorTable;
		private LogViewer logViewer;
		private Text chatText;
		private Button multilineChatButton;

		private SashForm rightSashForm;
		private Button ssidStartScan;
		private Text ssidCurrentSsidText;
		private Label ssidMatchLabel;
		private Text ssidMatchText;
		private Spinner ssidScanIntervalSpinner;
		private Button ssidAutoDetectCheck;
		private TableViewer ssidListTableViewer;
		private TableViewer roomPlayerListTable;
		private Button macFilteringWhiteListCheck;
		private List macFilteringWhiteList;
		private Button macFilteringBlackListCheck;
		private List macFilteringBlackList;

		private Composite statusBarContainer;
		private Label statusUserNameLabel;
		private Label statusServerAddressLabel;
		private Label statusTunnelConnectionLabel;
		private Label statusTraficStatusLabel;

		private MenuItem playerMenuChaseSsid;
		private MenuItem playerMenuSetSsid;
		private MenuItem playerMenuCopySsid;
		private MenuItem playerMenuKick;
		private MenuItem playerMenuMasterTransfer;
		private MenuItem statusServerAddressMenuCopy;
		private MenuItem packetMonitorMenuCopy;
		private MenuItem packetMonitorMenuWhiteList;
		private MenuItem packetMonitorMenuBlackList;
		private MenuItem packetMonitorMenuClear;

		private MenuItem macFilteringWhiteListMenuRemove;
		private MenuItem macFilteringBlackListMenuRemove;

		private Menu statusTunnelConnectionMenu;
		private MenuItem statusTunnelConnectionMenuChangeTransport;

		private Composite chatPresetContainer;
		private Button chatTextPresetF1;
		private Button chatTextPresetF2;
		private Button chatTextPresetF3;
		private Button chatTextPresetF4;
		private Button chatTextPresetF5;
		private Button chatTextPresetF6;
		private Button chatTextPresetF7;
		private Button chatTextPresetF8;
		private Button chatTextPresetF9;
		private Button chatTextPresetF10;
		private Button chatTextPresetF11;
		private Button chatTextPresetF12;

		private void initWidgets() {
			IniAppData iniAppData = application.getAppData();

			try {
				shell.setImages(application.getShellImages());
			} catch (RuntimeException e) {
				e.printStackTrace();
			}

			GridLayout gridLayout;
			GridData gridData;

			// ImageRegistry imageRegistry = application.getImageRegistry();
			ColorRegistry colorRegistry = application.getColorRegistry();

			gridLayout = new GridLayout(1, false);
			gridLayout.horizontalSpacing = 3;
			gridLayout.verticalSpacing = 0;
			gridLayout.marginWidth = 2;
			gridLayout.marginHeight = 1;
			shell.setLayout(gridLayout);

			toolBarContainer = new Composite(shell, SWT.NONE);
			toolBarContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			toolBarContainer.setLayout(new FillLayout());
			application.createToolBar(toolBarContainer, RoomWindow.this);

			mainSashForm = new SashForm(shell, SWT.HORIZONTAL);
			gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			gridData.verticalIndent = 3;
			mainSashForm.setLayoutData(gridData);

			Composite leftContainer = new Composite(mainSashForm, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.horizontalSpacing = 0;
			gridLayout.verticalSpacing = 1;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			leftContainer.setLayout(gridLayout);

			Composite formModeContainer = new Composite(leftContainer, SWT.NONE);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 8;
			gridLayout.verticalSpacing = 3;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginLeft = 3;
			formModeContainer.setLayout(gridLayout);
			formModeContainer.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false));

			Label formModeSelectionLabel = new Label(formModeContainer, SWT.NONE);
			formModeSelectionLabel.setText("モード");
			formModeSelectionLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			application.initControl(formModeSelectionLabel);

			formModeSelectionCombo = new Combo(formModeContainer, SWT.READ_ONLY);
			formModeSelectionCombo.setItems(new String[] { "Auto", "Manual", "My Room" });
			formModeSelectionCombo.select(0);
			formModeSelectionCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			application.initControl(formModeSelectionCombo);

			formModeSwitchContainer = new Composite(formModeContainer, SWT.NONE);
			roomModeStackLayout = new StackLayout();
			formModeSwitchContainer.setLayout(roomModeStackLayout);
			formModeSwitchContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

			formAutoModeContainer = new Composite(formModeSwitchContainer, SWT.NONE);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 8;
			gridLayout.verticalSpacing = 6;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginTop = 2;
			formAutoModeContainer.setLayout(gridLayout);

			Label formAutoModeServerAddressLabel = new Label(formAutoModeContainer, SWT.NONE);
			formAutoModeServerAddressLabel.setText("server");
			application.initControl(formAutoModeServerAddressLabel);

			formAutoModeServerAddress = new Text(formAutoModeContainer, SWT.BORDER | SWT.SINGLE | SWT.READ_ONLY);
			formAutoModeServerAddress.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			application.initControl(formAutoModeServerAddress);

			formAutoModeRoomButton = new Button(formAutoModeContainer, SWT.PUSH);
			formAutoModeRoomButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, true, 2, 1));
			application.initControl(formAutoModeRoomButton);

			formManualModeContainer = new Composite(formModeSwitchContainer, SWT.NONE);
			gridLayout = new GridLayout(3, false);
			gridLayout.horizontalSpacing = 5;
			gridLayout.verticalSpacing = 4;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			formManualModeContainer.setLayout(gridLayout);

			Label formManualModeServerLabel = new Label(formManualModeContainer, SWT.NONE);
			formManualModeServerLabel.setText("Server");
			application.initControl(formManualModeServerLabel);

			formManualModeRoomServerCombo = new Combo(formManualModeContainer, SWT.NONE);
			formManualModeRoomServerCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			application.initControl(formManualModeRoomServerCombo);

			formManualModeRoomServerButton = new Button(formManualModeContainer, SWT.PUSH);
			formManualModeRoomServerButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
			application.initControl(formManualModeRoomServerButton);

			Label formManualModeAddressLabel = new Label(formManualModeContainer, SWT.NONE);
			formManualModeAddressLabel.setText("address");
			application.initControl(formManualModeAddressLabel);

			formManualModeRoomAddressCombo = new Combo(formManualModeContainer, SWT.NONE);
			formManualModeRoomAddressCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			application.initControl(formManualModeRoomAddressCombo);

			formManualModeRoomAddressButton = new Button(formManualModeContainer, SWT.PUSH);
			formManualModeRoomAddressButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
			application.initControl(formManualModeRoomAddressButton);

			formMyRoomModeContainer = new Composite(formModeSwitchContainer, SWT.NONE);
			gridLayout = new GridLayout(4, false);
			gridLayout.horizontalSpacing = 3;
			gridLayout.verticalSpacing = 4;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginRight = 1;
			formMyRoomModeContainer.setLayout(gridLayout);

			Label formServerModeEntryLabel = new Label(formMyRoomModeContainer, SWT.NONE);
			formServerModeEntryLabel.setText("Host: Port");
			formServerModeEntryLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			application.initControl(formServerModeEntryLabel);

			formMyRoomModeHostText = new Text(formMyRoomModeContainer, SWT.SINGLE | SWT.BORDER);
			formMyRoomModeHostText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			application.initControl(formMyRoomModeHostText);

			formMyRoomModePortSpinner = new Spinner(formMyRoomModeContainer, SWT.BORDER);
			formMyRoomModePortSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
			formMyRoomModePortSpinner.setMinimum(1);
			formMyRoomModePortSpinner.setMaximum(65535);
			formMyRoomModePortSpinner.setSelection(30000);
			application.initControl(formMyRoomModePortSpinner);

			formMyRoomModeStartButton = new Button(formMyRoomModeContainer, SWT.PUSH);
			formMyRoomModeStartButton.setText("to start");
			formMyRoomModeStartButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
			application.initControl(formMyRoomModeStartButton);

			Label formMyRoomModeAddressLabel = new Label(formMyRoomModeContainer, SWT.NONE);
			formMyRoomModeAddressLabel.setText("server");
			formMyRoomModeAddressLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			application.initControl(formMyRoomModeAddressLabel);

			formMyRoomModeRoomServer = new Text(formMyRoomModeContainer, SWT.BORDER | SWT.SINGLE | SWT.READ_ONLY);
			formMyRoomModeRoomServer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
			application.initControl(formMyRoomModeRoomServer);

			formMyRoomModeEntryButton = new Button(formMyRoomModeContainer, SWT.PUSH);
			formMyRoomModeEntryButton.setText("Search registration");
			formMyRoomModeEntryButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
			application.initControl(formMyRoomModeEntryButton);

			roomModeStackLayout.topControl = formAutoModeContainer;

			Group formEditGroup = new Group(leftContainer, SWT.NONE);
			formEditGroup.setText("Room information");
			formEditGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			gridLayout = new GridLayout(2, false);
			gridLayout.marginWidth = 3;
			gridLayout.marginHeight = 3;
			formEditGroup.setLayout(gridLayout);

			Label formMasterNameLabel = new Label(formEditGroup, SWT.NONE);
			formMasterNameLabel.setText("Room owner");
			formMasterNameLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			application.initControl(formMasterNameLabel);

			formMasterNameText = new Text(formEditGroup, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
			formMasterNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			application.initControl(formMasterNameText);

			Label formTitleLabel = new Label(formEditGroup, SWT.NONE);
			formTitleLabel.setText("Room name");
			formTitleLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			application.initControl(formTitleLabel);

			formTitleText = new Text(formEditGroup, SWT.SINGLE | SWT.BORDER);
			formTitleText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			formTitleText.setTextLimit(100);
			application.initControl(formTitleText);

			Label formPasswordLabel = new Label(formEditGroup, SWT.NONE);
			formPasswordLabel.setText("password");
			formPasswordLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			application.initControl(formPasswordLabel);

			formPasswordText = new Text(formEditGroup, SWT.SINGLE | SWT.BORDER);
			formPasswordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			formPasswordText.setTextLimit(30);
			application.initControl(formPasswordText);

			Label formMaxPlayersLabel = new Label(formEditGroup, SWT.NONE);
			formMaxPlayersLabel.setText("Capacity");
			formMaxPlayersLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			application.initControl(formMaxPlayersLabel);

			Composite formMaxPlayerContainer = new Composite(formEditGroup, SWT.NONE);
			formMaxPlayerContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			gridLayout = new GridLayout(3, false);
			gridLayout.horizontalSpacing = 3;
			gridLayout.verticalSpacing = 0;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			formMaxPlayerContainer.setLayout(gridLayout);

			formMaxPlayersSpiner = new Spinner(formMaxPlayerContainer, SWT.READ_ONLY | SWT.BORDER);
			formMaxPlayersSpiner.setMinimum(2);
			formMaxPlayersSpiner.setMaximum(ProtocolConstants.Room.MAX_ROOM_PLAYERS);
			formMaxPlayersSpiner.setSelection(DEFAULT_MAX_PLAYERS);
			application.initControl(formMaxPlayersSpiner);

			Label formTimestampLabel = new Label(formMaxPlayerContainer, SWT.NONE);
			formTimestampLabel.setText("Creation date and time");
			formTimestampLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			application.initControl(formTimestampLabel);

			formTimestampText = new Text(formMaxPlayerContainer, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
			formTimestampText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			application.initControl(formTimestampText);

			SashForm formDescriptionSashForm = new SashForm(formEditGroup, SWT.VERTICAL);
			gridData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
			gridData.verticalIndent = 1;
			formDescriptionSashForm.setLayoutData(gridData);

			Composite formDescriptionContainer = new Composite(formDescriptionSashForm, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginTop = 4;
			gridLayout.verticalSpacing = 2;
			formDescriptionContainer.setLayout(gridLayout);

			Label formDescriptionLabel = new Label(formDescriptionContainer, SWT.NONE);
			formDescriptionLabel.setText("Introduction of the room (displayed in the search)");
			gridData = new GridData();
			gridData.horizontalIndent = 3;
			formDescriptionLabel.setLayoutData(gridData);
			application.initControl(formDescriptionLabel);

			formDescriptionText = new Text(formDescriptionContainer, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER | SWT.WRAP);
			formDescriptionText.setTextLimit(1000);
			formDescriptionText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			application.initControl(formDescriptionText);

			Composite formRemarksContainer = new Composite(formDescriptionSashForm, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginTop = 4;
			gridLayout.verticalSpacing = 2;
			formRemarksContainer.setLayout(gridLayout);

			Label formRemarksLabel = new Label(formRemarksContainer, SWT.NONE);
			formRemarksLabel.setText("Remarks (not displayed in the search)");
			gridData = new GridData();
			gridData.horizontalIndent = 3;
			formRemarksLabel.setLayoutData(gridData);
			application.initControl(formRemarksLabel);

			formRemarksText = new Text(formRemarksContainer, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER | SWT.WRAP);
			formRemarksText.setTextLimit(1000);
			formRemarksText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			application.initControl(formRemarksText);

			Composite formEditControlContainer = new Composite(formEditGroup, SWT.NONE);
			formEditControlContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
			gridLayout = new GridLayout(3, false);
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.verticalSpacing = 0;
			formEditControlContainer.setLayout(gridLayout);

			formEditSaveButton = new Button(formEditControlContainer, SWT.PUSH);
			formEditSaveButton.setText("save");
			formEditSaveButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			application.initControl(formEditSaveButton);

			formEditLoadButton = new Button(formEditControlContainer, SWT.PUSH);
			formEditLoadButton.setText("Read");
			formEditLoadButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			application.initControl(formEditLoadButton);

			formEditSubmitButton = new Button(formEditControlContainer, SWT.PUSH);
			formEditSubmitButton.setText("Update room information");
			formEditSubmitButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
			application.initControl(formEditSubmitButton);

			centerSashForm = new SashForm(mainSashForm, SWT.VERTICAL);
			centerSashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			Composite centerUpperContainer = new Composite(centerSashForm, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.verticalSpacing = 3;
			gridLayout.horizontalSpacing = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginWidth = 0;
			centerUpperContainer.setLayout(gridLayout);

			Composite wlanAdaptorContainer = new Composite(centerUpperContainer, SWT.NONE);
			gridLayout = new GridLayout(3, false);
			gridLayout.verticalSpacing = 0;
			gridLayout.horizontalSpacing = 3;
			gridLayout.marginHeight = 0;
			gridLayout.marginWidth = 2;
			wlanAdaptorContainer.setLayout(gridLayout);
			wlanAdaptorContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label wlanAdapterListLabel = new Label(wlanAdaptorContainer, SWT.NONE);
			wlanAdapterListLabel.setText("Wireless LAN adapter");
			application.initControl(wlanAdapterListLabel);

			wlanAdapterListCombo = new Combo(wlanAdaptorContainer, SWT.READ_ONLY);
			wlanAdapterListCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			application.initControl(wlanAdapterListCombo);

			wlanPspCommunicationButton = new Button(wlanAdaptorContainer, SWT.TOGGLE);
			wlanPspCommunicationButton.setText("Start communication with PSP");
			application.initControl(wlanPspCommunicationButton);

			packetMonitorTable = new TableViewer(centerUpperContainer, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
			packetMonitorTable.getTable().setHeaderVisible(true);
			packetMonitorTable.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			application.initControl(packetMonitorTable.getTable());

			TableColumn packetMonitorIsMineColumn = new TableColumn(packetMonitorTable.getTable(), SWT.CENTER);
			packetMonitorIsMineColumn.setText("");
			SwtUtils.installSorter(packetMonitorTable, packetMonitorIsMineColumn, TraficStatistics.MINE_SORTER);

			TableColumn packetMonitorMacAddressColumn = new TableColumn(packetMonitorTable.getTable(), SWT.LEFT);
			packetMonitorMacAddressColumn.setText("MAC address");
			SwtUtils.installSorter(packetMonitorTable, packetMonitorMacAddressColumn, TraficStatistics.MAC_ADDRESS_SORTER);

			TableColumn packetMonitorPlayerNameColumn = new TableColumn(packetMonitorTable.getTable(), SWT.LEFT);
			packetMonitorPlayerNameColumn.setText("username");
			SwtUtils.installSorter(packetMonitorTable, packetMonitorPlayerNameColumn, TraficStatistics.PLAYER_NAME_SORTER);

			TableColumn packetMonitorInSpeedColumn = new TableColumn(packetMonitorTable.getTable(), SWT.RIGHT);
			packetMonitorInSpeedColumn.setText("In (Kbps)");
			SwtUtils.installSorter(packetMonitorTable, packetMonitorInSpeedColumn, TraficStatistics.IN_SPEED_SORTER);

			TableColumn packetMonitorOutSpeedColumn = new TableColumn(packetMonitorTable.getTable(), SWT.RIGHT);
			packetMonitorOutSpeedColumn.setText("Out (Kbps)");
			SwtUtils.installSorter(packetMonitorTable, packetMonitorOutSpeedColumn, TraficStatistics.OUT_SPEED_SORTER);

			TableColumn packetMonitorTotalInBytesColumn = new TableColumn(packetMonitorTable.getTable(), SWT.RIGHT);
			packetMonitorTotalInBytesColumn.setText("In cumulative bytes");
			SwtUtils.installSorter(packetMonitorTable, packetMonitorTotalInBytesColumn, TraficStatistics.TOTAL_IN_SORTER);

			TableColumn packetMonitorTotalOutBytesColumn = new TableColumn(packetMonitorTable.getTable(), SWT.RIGHT);
			packetMonitorTotalOutBytesColumn.setText("Out cumulative bytes");
			SwtUtils.installSorter(packetMonitorTable, packetMonitorTotalOutBytesColumn, TraficStatistics.TOTAL_OUT_SORTER);

			packetMonitorTable.setContentProvider(new TraficStatistics.ContentProvider());
			packetMonitorTable.setLabelProvider(new TraficStatistics.LabelProvider());
			SwtUtils.enableColumnDrag(packetMonitorTable.getTable());

			Composite chatContainer = new Composite(centerSashForm, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.verticalSpacing = 0;
			gridLayout.horizontalSpacing = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginWidth = 0;
			chatContainer.setLayout(gridLayout);

			logViewer = new LogViewer(chatContainer, application.getSettings().getMaxLogCount(), application);
			logViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			chatPresetContainer = new Composite(chatContainer, SWT.NONE);
			gridLayout = new GridLayout(12, false);
			gridLayout.verticalSpacing = 0;
			gridLayout.horizontalSpacing = 3;
			gridLayout.marginHeight = 0;
			gridLayout.marginWidth = 0;
			gridLayout.marginTop = 3;
			gridLayout.marginBottom = 1;
			gridLayout.marginRight = 1;
			chatPresetContainer.setLayout(gridLayout);
			gridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
			chatPresetContainer.setLayoutData(gridData);

			chatTextPresetF1 = new Button(chatPresetContainer, SWT.PUSH);
			chatTextPresetF2 = new Button(chatPresetContainer, SWT.PUSH);
			chatTextPresetF3 = new Button(chatPresetContainer, SWT.PUSH);
			chatTextPresetF4 = new Button(chatPresetContainer, SWT.PUSH);
			chatTextPresetF5 = new Button(chatPresetContainer, SWT.PUSH);
			chatTextPresetF6 = new Button(chatPresetContainer, SWT.PUSH);
			chatTextPresetF7 = new Button(chatPresetContainer, SWT.PUSH);
			chatTextPresetF8 = new Button(chatPresetContainer, SWT.PUSH);
			chatTextPresetF9 = new Button(chatPresetContainer, SWT.PUSH);
			chatTextPresetF10 = new Button(chatPresetContainer, SWT.PUSH);
			chatTextPresetF11 = new Button(chatPresetContainer, SWT.PUSH);
			chatTextPresetF12 = new Button(chatPresetContainer, SWT.PUSH);

			updateChatPresetButtons();

			Composite chatCommandContainer = new Composite(chatContainer, SWT.NONE);
			gridLayout = new GridLayout(2, false);
			gridLayout.verticalSpacing = 0;
			gridLayout.horizontalSpacing = 3;
			gridLayout.marginHeight = 0;
			gridLayout.marginWidth = 0;
			gridLayout.marginTop = 3;
			gridLayout.marginBottom = 1;
			gridLayout.marginRight = 1;
			chatCommandContainer.setLayout(gridLayout);
			chatCommandContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			chatText = new Text(chatCommandContainer, SWT.BORDER | SWT.SINGLE);
			chatText.setTextLimit(300);
			chatText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			application.initChatControl(chatText);

			multilineChatButton = new Button(chatCommandContainer, SWT.PUSH);
			multilineChatButton.setText("複数行");
			application.initControl(multilineChatButton);

			rightSashForm = new SashForm(mainSashForm, SWT.VERTICAL);

			Composite ssidContainer = new Composite(rightSashForm, SWT.NONE);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 1;
			gridLayout.verticalSpacing = 4;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			ssidContainer.setLayout(gridLayout);

			Label ssidCurrentSsidLabel = new Label(ssidContainer, SWT.NONE);
			ssidCurrentSsidLabel.setText("Current SSID");
			ssidCurrentSsidLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			application.initControl(ssidCurrentSsidLabel);

			ssidCurrentSsidText = new Text(ssidContainer, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
			ssidCurrentSsidText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			application.initControl(ssidCurrentSsidText);

			ssidMatchLabel = new Label(ssidContainer, SWT.NONE);
			ssidMatchLabel.setText("search");
			ssidMatchLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			application.initControl(ssidMatchLabel);

			ssidMatchText = new Text(ssidContainer, SWT.SINGLE | SWT.BORDER);
			ssidMatchText.setText("PSP_");
			ssidMatchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			application.initControl(ssidMatchText);

			Composite ssidControlContainer = new Composite(ssidContainer, SWT.NONE);
			ssidControlContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
			gridLayout = new GridLayout(4, false);
			gridLayout.horizontalSpacing = 3;
			gridLayout.verticalSpacing = 0;
			gridLayout.marginWidth = 1;
			gridLayout.marginHeight = 0;
			ssidControlContainer.setLayout(gridLayout);

			ssidStartScan = new Button(ssidControlContainer, SWT.TOGGLE);
			ssidStartScan.setText("Start scanning");
			ssidStartScan.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			ssidStartScan.setEnabled(false);
			application.initControl(ssidStartScan);

			ssidScanIntervalSpinner = new Spinner(ssidControlContainer, SWT.BORDER);
			ssidScanIntervalSpinner.setMinimum(500);
			ssidScanIntervalSpinner.setMaximum(9999);
			ssidScanIntervalSpinner.setSelection(scanIntervalMillis);
			application.initControl(ssidScanIntervalSpinner);

			Label ssidScanIntervalLabel = new Label(ssidControlContainer, SWT.NONE);
			ssidScanIntervalLabel.setText("millisecond");
			application.initControl(ssidScanIntervalLabel);

			ssidAutoDetectCheck = new Button(ssidControlContainer, SWT.CHECK | SWT.FLAT);
			ssidAutoDetectCheck.setText("Automatic tracking");
			ssidAutoDetectCheck.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
			application.initControl(ssidAutoDetectCheck);

			ssidListTableViewer = new TableViewer(ssidContainer, SWT.BORDER | SWT.FULL_SELECTION);
			ssidListTableViewer.setContentProvider(new ArrayContentProvider());
			ssidListTableViewer.setLabelProvider(new WlanUtils.LabelProvider());

			Table ssidListTable = ssidListTableViewer.getTable();
			ssidListTable.setHeaderVisible(true);
			ssidListTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
			application.initControl(ssidListTable);

			TableColumn ssidListTableSsidColumn = new TableColumn(ssidListTable, SWT.LEFT);
			ssidListTableSsidColumn.setText("SSID");

			TableColumn ssidListTableRssiColumn = new TableColumn(ssidListTable, SWT.RIGHT);
			ssidListTableRssiColumn.setText("strength");

			roomPlayerListTable = new TableViewer(rightSashForm, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
			roomPlayerListTable.getTable().setHeaderVisible(true);
			application.initControl(roomPlayerListTable.getTable());

			TableColumn roomPlayerSsidChaseColumn = new TableColumn(roomPlayerListTable.getTable(), SWT.CENTER);
			SwtUtils.installSorter(roomPlayerListTable, roomPlayerSsidChaseColumn, Player.SSID_CHASE_SORTER);

			TableColumn roomPlayerNameColumn = new TableColumn(roomPlayerListTable.getTable(), SWT.LEFT);
			roomPlayerNameColumn.setText("name");
			SwtUtils.installSorter(roomPlayerListTable, roomPlayerNameColumn, Player.NANE_SORTER);

			TableColumn roomPlayerSsidColumn = new TableColumn(roomPlayerListTable.getTable(), SWT.LEFT);
			roomPlayerSsidColumn.setText("SSID");
			SwtUtils.installSorter(roomPlayerListTable, roomPlayerSsidColumn, Player.SSID_SORTER);

			TableColumn roomPlayerPingColumn = new TableColumn(roomPlayerListTable.getTable(), SWT.RIGHT);
			roomPlayerPingColumn.setText("PING");
			SwtUtils.installSorter(roomPlayerListTable, roomPlayerPingColumn, Player.PING_SORTER);

			roomPlayerListTable.setContentProvider(new Player.PlayerListContentProvider());
			roomPlayerListTable.setLabelProvider(new Player.RoomPlayerLabelProvider());
			SwtUtils.enableColumnDrag(roomPlayerListTable.getTable());
			roomPlayerListTable.setInput(roomPlayerMap);

			Composite macFilteringContainer = new Composite(rightSashForm, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.horizontalSpacing = 0;
			gridLayout.verticalSpacing = 2;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			macFilteringContainer.setLayout(gridLayout);

			Label macFilteringLabel = new Label(macFilteringContainer, SWT.NONE);
			macFilteringLabel.setText("MAC address filtering");
			macFilteringLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
			application.initControl(macFilteringLabel);

			SashForm macFilteringSashForm = new SashForm(macFilteringContainer, SWT.HORIZONTAL);
			macFilteringSashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			Composite macFilteringWhiteListContainer = new Composite(macFilteringSashForm, SWT.NONE);
			macFilteringWhiteListContainer.setLayout(gridLayout);

			macFilteringWhiteListCheck = new Button(macFilteringWhiteListContainer, SWT.CHECK | SWT.FLAT);
			macFilteringWhiteListCheck.setText("Enable white list");
			application.initControl(macFilteringWhiteListCheck);

			macFilteringWhiteList = new List(macFilteringWhiteListContainer, SWT.BORDER | SWT.SINGLE);
			macFilteringWhiteList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			application.initControl(macFilteringWhiteList);

			Composite macFilteringBlackListContainer = new Composite(macFilteringSashForm, SWT.NONE);
			macFilteringBlackListContainer.setLayout(gridLayout);

			macFilteringBlackListCheck = new Button(macFilteringBlackListContainer, SWT.CHECK | SWT.FLAT);
			macFilteringBlackListCheck.setText("Enable blacklist");
			application.initControl(macFilteringBlackListCheck);

			macFilteringBlackList = new List(macFilteringBlackListContainer, SWT.BORDER | SWT.SINGLE);
			macFilteringBlackList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			application.initControl(macFilteringBlackList);

			gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
			gridData.heightHint = 15;
			statusBarContainer = new Composite(shell, SWT.NONE);
			gridData = new GridData(SWT.FILL, SWT.CENTER, false, false);
			gridData.verticalIndent = 3;
			statusBarContainer.setLayoutData(gridData);
			gridLayout = new GridLayout(8, false);
			gridLayout.marginHeight = 2;
			gridLayout.marginWidth = 3;
			statusBarContainer.setLayout(gridLayout);

			gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
			gridData.heightHint = 15;

			statusUserNameLabel = new Label(statusBarContainer, SWT.NONE);
			application.initControl(statusUserNameLabel);

			new Label(statusBarContainer, SWT.SEPARATOR | SWT.VERTICAL).setLayoutData(gridData);

			statusServerAddressLabel = new Label(statusBarContainer, SWT.NONE);
			application.initControl(statusServerAddressLabel);

			new Label(statusBarContainer, SWT.SEPARATOR | SWT.VERTICAL).setLayoutData(gridData);

			statusTunnelConnectionLabel = new Label(statusBarContainer, SWT.NONE);
			statusTunnelConnectionLabel.setForeground(colorRegistry.get(PlayClient.COLOR_NG));
			statusTunnelConnectionLabel.setText("Tunnel not connected");
			application.initControl(statusTunnelConnectionLabel);

			new Label(statusBarContainer, SWT.SEPARATOR | SWT.VERTICAL).setLayoutData(gridData);

			statusTraficStatusLabel = new Label(statusBarContainer, SWT.NONE);
			statusTraficStatusLabel.setText("No traffic");
			application.initControl(statusTraficStatusLabel);

			new Label(statusBarContainer, SWT.SEPARATOR | SWT.VERTICAL).setLayoutData(gridData);

			try {
				mainSashForm.setWeights(iniAppData.getRoomSashWeights());
			} catch (SWTException e) {
			}
			try {
				centerSashForm.setWeights(iniAppData.getRoomCenterSashWeights());
			} catch (SWTException e) {
			}
			try {
				rightSashForm.setWeights(iniAppData.getRoomRightSashWeights());
			} catch (SWTException e) {
			}

			iniAppData.restorePacketMonitorTable(packetMonitorTable.getTable());
			iniAppData.restoreSsidScanTable(ssidListTableViewer.getTable());
			iniAppData.restoreRoomPlayerTable(roomPlayerListTable.getTable());
		}

		private void initWidgetListeners() {
			shell.addShellListener(new ShellListener() {
				@Override
				public void shellActivated(ShellEvent e) {
					isActiveWindow = true;
					switch (sessionState) {
					case MY_ROOM_MASTER:
					case ROOM_MASTER:
					case ROOM_PARTICIPANT:
						changeLobbyStateTo(LobbyUserState.PLAYING);
					}
				}

				@Override
				public void shellIconified(ShellEvent e) {
					isActiveWindow = false;
				}

				@Override
				public void shellDeiconified(ShellEvent e) {
					isActiveWindow = true;
				}

				@Override
				public void shellDeactivated(ShellEvent e) {
					isActiveWindow = false;
				}

				@Override
				public void shellClosed(ShellEvent e) {
				}
			});
			shell.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					myRoomEngine.closeRoom();
					isPacketCapturing = false;

					IniAppData iniAppData = application.getAppData();

					iniAppData.storeMainWindow(shell.getBounds());

					iniAppData.setRoomSashWeights(mainSashForm.getWeights());
					iniAppData.setRoomCenterSashWeights(centerSashForm.getWeights());
					iniAppData.setRoomRightSashWeights(rightSashForm.getWeights());

					iniAppData.storePacketMonitorTable(packetMonitorTable.getTable());
					iniAppData.storeSsidScanTable(ssidListTableViewer.getTable());
					iniAppData.storeRoomPlayerTable(roomPlayerListTable.getTable());

					iniAppData.setRoomServerHistory(roomServerHistoryManager.makeCSV());
					iniAppData.setRoomAddressHistory(roomAddressHistoryManager.makeCSV());

					int index = wlanAdapterListCombo.getSelectionIndex() - 1;
					if (wlanAdapterListCombo.getItemCount() < 2 || index == -1) {
						iniAppData.setLastLanAdapter("");
					} else {
						WlanDevice device = wlanAdapterList.get(index);
						iniAppData.setLastLanAdapter(wlanAdapterMacAddressMap.get(device));
					}
				}
			});
			SwtUtils.DISPLAY.addFilter(SWT.KeyUp, new Listener() {
				private int lastKeyCode = 0;

				@Override
				public void handleEvent(Event event) {
					if (!application.getSettings().isChatPresetEnableKeyInput())
						return;
					if (!isActiveWindow)
						return;

					String msg = null;
					switch (event.keyCode) {
					case SWT.F1:
						msg = chatTextPresets.getPresetF1();
						break;
					case SWT.F2:
						msg = chatTextPresets.getPresetF2();
						break;
					case SWT.F3:
						msg = chatTextPresets.getPresetF3();
						break;
					case SWT.F4:
						msg = chatTextPresets.getPresetF4();
						break;
					case SWT.F5:
						msg = chatTextPresets.getPresetF5();
						break;
					case SWT.F6:
						msg = chatTextPresets.getPresetF6();
						break;
					case SWT.F7:
						msg = chatTextPresets.getPresetF7();
						break;
					case SWT.F8:
						msg = chatTextPresets.getPresetF8();
						break;
					case SWT.F9:
						msg = chatTextPresets.getPresetF9();
						break;
					case SWT.F10:
						msg = chatTextPresets.getPresetF10();
						break;
					case SWT.F11:
						msg = chatTextPresets.getPresetF11();
						break;
					case SWT.F12:
						msg = chatTextPresets.getPresetF12();
						break;
					case SWT.ESC:
						widgets.chatText.setText("");
						widgets.chatText.setFocus();

						lastKeyCode = event.keyCode;
						return;
					default:
						lastKeyCode = 0;
						return;
					}

					if ("".equals(msg))
						return;
					if (lastKeyCode == event.keyCode && msg.equals(widgets.chatText.getText())) {
						if (sendChat(msg)) {
							chatText.setText("");
							lastKeyCode = 0;
						}
					} else {
						widgets.chatText.setText(msg);
						widgets.chatText.setSelection(msg.length());
						widgets.chatText.setFocus();

						lastKeyCode = event.keyCode;
					}
				}
			});

			formModeSelectionCombo.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					updateRoomModeSelection();
				}
			});

			formAutoModeRoomButton.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					switch (sessionState) {
					case OFFLINE:
						autoConnectAsMaster();
						break;
					case ROOM_MASTER:
						confirmRoomDelete(false);
						break;
					case ROOM_PARTICIPANT:
						roomConnection.disconnect();
						break;
					}
				}
			});
			formAutoModeRoomButton.addMouseListener(new MouseListener() {
				@Override
				public void mouseUp(MouseEvent e) {
					if (sessionState != SessionState.OFFLINE)
						return;
					if (e.button != 3)
						return;
					if (e.x < 0 || e.y < 0)
						return;
					Point size = formAutoModeRoomButton.getSize();
					if (e.x > size.x || e.y > size.y)
						return;

					if (!checkConfigUserName() || !checkRoomFormTitle())
						return;

					formAutoModeRoomButton.setEnabled(false);
					selectRoomServer(new ServerSelectAction() {
						@Override
						public String getOkLabel() {
							return "Create a room";
						}

						@Override
						public void select(String address) {
							autoConnectAsMaster(address);
						}

						@Override
						public void cancel() {
							try {
								if (SwtUtils.isNotUIThread()) {
									SwtUtils.DISPLAY.asyncExec(new Runnable() {
										public void run() {
											cancel();
										}
									});
									return;
								}

								formAutoModeRoomButton.setEnabled(true);
							} catch (SWTException e) {
							}
						}
					});
				}

				@Override
				public void mouseDown(MouseEvent e) {
				}

				@Override
				public void mouseDoubleClick(MouseEvent e) {
				}
			});

			formManualModeRoomServerCombo.addKeyListener(new KeyListener() {
				@Override
				public void keyReleased(KeyEvent e) {
				}

				@Override
				public void keyPressed(KeyEvent e) {
					switch (e.character) {
					case SWT.CR:
					case SWT.LF:
						e.doit = false;
						manualConnectAsMaster();
						break;
					}
				}
			});
			formManualModeRoomServerButton.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (sessionState == SessionState.OFFLINE) {
						manualConnectAsMaster();
					} else {
						confirmRoomDelete(false);
					}
				}
			});

			formManualModeRoomAddressCombo.addKeyListener(new KeyListener() {
				@Override
				public void keyReleased(KeyEvent e) {
				}

				@Override
				public void keyPressed(KeyEvent e) {
					switch (e.character) {
					case SWT.CR:
					case SWT.LF:
						e.doit = false;
						manualConnectAsParticipant();
						break;
					}
				}
			});
			formManualModeRoomAddressButton.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (sessionState == SessionState.OFFLINE) {
						manualConnectAsParticipant();
					} else {
						roomConnection.disconnect();
					}
				}
			});

			formMyRoomModeStartButton.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					try {
						if (myRoomEngine.isStarted()) {
							formMyRoomModeStartButton.setEnabled(false);
							myRoomEngine.closeRoom();
						} else {
							startMyRoomServer();
						}
					} catch (IOException e) {
						application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
					}
				}
			});
			formMyRoomModeHostText.addModifyListener(new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					String host = formMyRoomModeHostText.getText();
					application.getSettings().setMyRoomHostName(host);

					switch (sessionState) {
					case MY_ROOM_MASTER:
						roomServerAddressPort = host + ":" + formMyRoomModePortSpinner.getSelection();
						updateLoginStatus();
					}
				}
			});
			formMyRoomModePortSpinner.addModifyListener(new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					application.getSettings().setMyRoomPort(formMyRoomModePortSpinner.getSelection());
				}
			});
			formMyRoomModeEntryButton.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (myRoomEntryConnection.isConnected()) {
						myRoomEntryConnection.disconnect();
					} else {
						autoConnectAsMyRoom();
					}
				}
			});
			formMyRoomModeEntryButton.addMouseListener(new MouseListener() {
				@Override
				public void mouseUp(MouseEvent e) {
					if (myRoomEntryConnection.isConnected())
						return;
					if (e.button != 3)
						return;
					if (e.x < 0 || e.y < 0)
						return;
					Point size = formMyRoomModeEntryButton.getSize();
					if (e.x > size.x || e.y > size.y)
						return;

					if (!checkConfigUserName() || !checkRoomFormTitle())
						return;

					formMyRoomModeEntryButton.setEnabled(false);
					selectRoomServer(new ServerSelectAction() {
						@Override
						public String getOkLabel() {
							return "Register for search";
						}

						@Override
						public void select(String address) {
							autoConnectAsMyRoom(address);
						}

						@Override
						public void cancel() {
							try {
								if (SwtUtils.isNotUIThread()) {
									SwtUtils.DISPLAY.asyncExec(new Runnable() {
										public void run() {
											cancel();
										}
									});
									return;
								}

								formMyRoomModeEntryButton.setEnabled(true);
							} catch (SWTException e) {
							}
						}
					});
				}

				@Override
				public void mouseDown(MouseEvent e) {
				}

				@Override
				public void mouseDoubleClick(MouseEvent e) {
				}
			});

			chatText.addKeyListener(new KeyListener() {
				@Override
				public void keyReleased(KeyEvent e) {
				}

				@Override
				public void keyPressed(KeyEvent e) {
					switch (e.character) {
					case SWT.CR:
					case SWT.LF:
						e.doit = false;
						if (sendChat(chatText.getText())) {
							chatText.setText("");
						}
					}
				}
			});
			multilineChatButton.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					switch (sessionState) {
					case MY_ROOM_MASTER:
					case ROOM_MASTER:
					case ROOM_PARTICIPANT:
						MultiLineChatDialog dialog = new MultiLineChatDialog(shell, application);
						switch (dialog.open()) {
						case IDialogConstants.OK_ID:
							String message = dialog.getMessage();
							sendChat(message);
						}
					}
				}
			});

			chatTextPresetF1.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF1());
					widgets.chatText.setFocus();
				}
			});
			chatTextPresetF2.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF2());
					widgets.chatText.setFocus();
				}
			});
			chatTextPresetF3.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF3());
					widgets.chatText.setFocus();
				}
			});
			chatTextPresetF4.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF4());
					widgets.chatText.setFocus();
				}
			});
			chatTextPresetF5.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF5());
					widgets.chatText.setFocus();
				}
			});
			chatTextPresetF6.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF6());
					widgets.chatText.setFocus();
				}
			});
			chatTextPresetF7.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF7());
					widgets.chatText.setFocus();
				}
			});
			chatTextPresetF8.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF8());
					widgets.chatText.setFocus();
				}
			});
			chatTextPresetF9.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF9());
					widgets.chatText.setFocus();
				}
			});
			chatTextPresetF10.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF10());
					widgets.chatText.setFocus();
				}
			});
			chatTextPresetF11.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF11());
					widgets.chatText.setFocus();
				}
			});
			chatTextPresetF12.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					sendChat(chatTextPresets.getPresetF12());
					widgets.chatText.setFocus();
				}
			});

			wlanAdapterListCombo.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					int index = widgets.wlanAdapterListCombo.getSelectionIndex();

					int separatorIndex = wlanAdapterList.size() + 1;
					int refreshIndex = separatorIndex + 1;
					if (index == 0) {
						wlanPspCommunicationButton.setEnabled(false);
					} else if (index < separatorIndex) {
						wlanPspCommunicationButton.setEnabled(true);
					} else if (index == separatorIndex) {
						wlanAdapterListCombo.select(0);
						wlanPspCommunicationButton.setEnabled(false);
					} else if (index == refreshIndex) {
						refreshLanAdapterList();
					}
				}
			});
			wlanPspCommunicationButton.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (wlanPspCommunicationButton.getSelection()) {
						if (startPacketCapturing()) {
							wlanPspCommunicationButton.setText("Communicating with PSP");
							wlanAdapterListCombo.setEnabled(false);

							if (currentWlanLibrary.isSSIDEnabled()) {
								ssidStartScan.setEnabled(true);
							}
						} else {
							wlanPspCommunicationButton.setSelection(false);
						}
					} else {
						wlanPspCommunicationButton.setEnabled(false);
						isPacketCapturing = false;

						if (currentWlanLibrary.isSSIDEnabled()) {
							updateSsidStartScan(false);
							widgets.ssidStartScan.setEnabled(false);

							setAndSendInformNewSSID("");
							nextSsidCheckTime = 0L;
						}
					}
				}
			});

			ssidScanIntervalSpinner.addModifyListener(new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					scanIntervalMillis = ssidScanIntervalSpinner.getSelection();
				}
			});

			ssidStartScan.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (!currentWlanLibrary.isSSIDEnabled() || currentWlanDevice == null) {
						updateSsidStartScan(false);
					} else {
						updateSsidStartScan(ssidStartScan.getSelection());
					}
				}
			});

			ssidListTableViewer.addDoubleClickListener(new IDoubleClickListener() {
				@Override
				public void doubleClick(DoubleClickEvent e) {
					if (!currentWlanLibrary.isSSIDEnabled() || currentWlanDevice == null) {
						return;
					}
					IStructuredSelection sel = (IStructuredSelection) e.getSelection();
					WlanNetwork network = (WlanNetwork) sel.getFirstElement();
					if (network == null)
						return;

					String selectedSSID = network.getSsid();
					String currentSSID = ssidCurrentSsidText.getText();
					if (currentSSID.equals(selectedSSID))
						return;

					changeSSID(selectedSSID);
				}
			});

			macFilteringWhiteListCheck.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					switch (sessionState) {
					case MY_ROOM_MASTER:
						myRoomEngine.enableMacAddressWhiteList(macFilteringWhiteListCheck.getSelection());
						break;
					case ROOM_MASTER:
						StringBuilder sb = new StringBuilder();
						sb.append(ProtocolConstants.Room.COMMAND_WHITELIST_ENABLE);
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(macFilteringWhiteListCheck.getSelection() ? "Y" : "N");

						roomConnection.send(Utility.encode(sb));
						break;
					}
				}
			});
			macFilteringBlackListCheck.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					switch (sessionState) {
					case MY_ROOM_MASTER:
						myRoomEngine.enableMacAddressBlackList(macFilteringBlackListCheck.getSelection());
						break;
					case ROOM_MASTER:
						StringBuilder sb = new StringBuilder();
						sb.append(ProtocolConstants.Room.COMMAND_BLACKLIST_ENABLE);
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(macFilteringBlackListCheck.getSelection() ? "Y" : "N");

						roomConnection.send(Utility.encode(sb));
						break;
					}
				}
			});

			formEditSaveButton.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					FileDialog dialog = new FileDialog(shell, SWT.SAVE);
					dialog.setOverwrite(true);
					dialog.setFilterExtensions(new String[] { "*.txt" });
					String filename = dialog.open();
					if (Utility.isEmpty(filename))
						return;

					try {
						new File(filename).delete();
						IniFile file = new IniFile(filename);
						IniSection section = file.getSection(null);

						section.set(INI_ROOM_TITLE, formTitleText.getText());
						section.set(INI_ROOM_PASSWORD, formPasswordText.getText());
						section.set(INI_ROOM_CAPACITY, formMaxPlayersSpiner.getSelection());

						String desc = widgets.formDescriptionText.getText();
						desc = desc.replace(widgets.formDescriptionText.getLineDelimiter(), "\\n");
						section.set(INI_ROOM_DESCRIPTION, desc);

						String remarks = widgets.formRemarksText.getText();
						remarks = remarks.replace(widgets.formRemarksText.getLineDelimiter(), "\\n");
						section.set(INI_ROOM_REMARKS, remarks);

						file.saveToIni();
					} catch (IOException e) {
						application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
					}
				}
			});

			formEditLoadButton.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					FileDialog dialog = new FileDialog(shell, SWT.OPEN);
					dialog.setFilterExtensions(new String[] { "*.txt" });
					String filename = dialog.open();
					if (Utility.isEmpty(filename))
						return;

					try {
						IniFile file = new IniFile(filename);
						IniSection section = file.getSection(null);

						formTitleText.setText(section.get(INI_ROOM_TITLE, ""));
						formPasswordText.setText(section.get(INI_ROOM_PASSWORD, ""));
						formMaxPlayersSpiner.setSelection(section.get(INI_ROOM_CAPACITY, 4));

						String desc = section.get(INI_ROOM_DESCRIPTION, "");
						desc = desc.replace("\\n", formDescriptionText.getLineDelimiter());
						formDescriptionText.setText(desc);

						String remarks = section.get(INI_ROOM_REMARKS, "");
						remarks = remarks.replace("\\n", formRemarksText.getLineDelimiter());
						formRemarksText.setText(remarks);
					} catch (IOException e) {
						application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
					}
				}
			});

			formEditSubmitButton.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					commitRoomEditForm();
				}
			});

			formTitleText.addVerifyListener(SwtUtils.NOT_ACCEPT_CONTROL_CHAR_LISTENER);
			formPasswordText.addVerifyListener(SwtUtils.NOT_ACCEPT_CONTROL_CHAR_LISTENER);
			formDescriptionText.addVerifyListener(SwtUtils.NOT_ACCEPT_CONTROL_CHAR_LISTENER);
			formRemarksText.addVerifyListener(SwtUtils.NOT_ACCEPT_CONTROL_CHAR_LISTENER);

			formManualModeRoomAddressCombo.addVerifyListener(SwtUtils.NOT_ACCEPT_CONTROL_CHAR_LISTENER);
			formMyRoomModeHostText.addVerifyListener(SwtUtils.NOT_ACCEPT_CONTROL_CHAR_LISTENER);

			ModifyListener roomEditFormModifyDetectListener = new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					if (isRoomInfoUpdating)
						return;

					switch (sessionState) {
					case MY_ROOM_MASTER:
					case ROOM_MASTER:
						formEditSubmitButton.setEnabled(true);
						break;
					}
				}
			};
			widgets.formTitleText.addModifyListener(roomEditFormModifyDetectListener);
			widgets.formPasswordText.addModifyListener(roomEditFormModifyDetectListener);
			widgets.formMaxPlayersSpiner.addModifyListener(roomEditFormModifyDetectListener);
			widgets.formDescriptionText.addModifyListener(roomEditFormModifyDetectListener);
			widgets.formRemarksText.addModifyListener(roomEditFormModifyDetectListener);
		}

		private void initMenus() {
			Menu playerMenu = new Menu(shell, SWT.POP_UP);

			playerMenuChaseSsid = new MenuItem(playerMenu, SWT.CHECK);
			playerMenuChaseSsid.setText("Track the SSID of this player");
			playerMenuChaseSsid.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					IStructuredSelection selection = (IStructuredSelection) roomPlayerListTable.getSelection();
					Player player = (Player) selection.getFirstElement();
					if (player == null)
						return;

					player.setSSIDChased(playerMenuChaseSsid.getSelection());
					roomPlayerListTable.refresh(player);
				}
			});

			playerMenuSetSsid = new MenuItem(playerMenu, SWT.PUSH);
			playerMenuSetSsid.setText("Set to this SSID");
			playerMenuSetSsid.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					IStructuredSelection selection = (IStructuredSelection) roomPlayerListTable.getSelection();
					Player player = (Player) selection.getFirstElement();
					if (player == null)
						return;

					changeSSID(player.getSsid());
				}
			});

			playerMenuCopySsid = new MenuItem(playerMenu, SWT.PUSH);
			playerMenuCopySsid.setText("Copy this SSID");
			playerMenuCopySsid.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					IStructuredSelection selection = (IStructuredSelection) roomPlayerListTable.getSelection();
					Player player = (Player) selection.getFirstElement();
					if (player == null)
						return;

					application.putClipboard(player.getSsid());
				}
			});

			new MenuItem(playerMenu, SWT.SEPARATOR);

			playerMenuKick = new MenuItem(playerMenu, SWT.PUSH);
			playerMenuKick.setText("kick");
			playerMenuKick.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					IStructuredSelection selection = (IStructuredSelection) roomPlayerListTable.getSelection();
					Player player = (Player) selection.getFirstElement();
					if (player == null || roomLoginName.equals(player.getName()))
						return;

					String kickedName = player.getName();
					switch (sessionState) {
					case MY_ROOM_MASTER:
						myRoomEngine.kickPlayer(kickedName);
						removeKickedRoomPlayer(kickedName);
						break;
					case ROOM_MASTER:
						ByteBuffer buf = Utility.encode(ProtocolConstants.Room.COMMAND_ROOM_KICK_PLAYER
								+ TextProtocolDriver.ARGUMENT_SEPARATOR + kickedName);
						roomConnection.send(buf);
						break;
					}
				}
			});

			new MenuItem(playerMenu, SWT.SEPARATOR);

			playerMenuMasterTransfer = new MenuItem(playerMenu, SWT.PUSH);
			playerMenuMasterTransfer.setText("Delegate the room owner");
			playerMenuMasterTransfer.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					IStructuredSelection selection = (IStructuredSelection) roomPlayerListTable.getSelection();
					Player player = (Player) selection.getFirstElement();
					if (player == null)
						return;

					String newMasterName = player.getName();
					switch (sessionState) {
					case ROOM_MASTER:
						ByteBuffer buf = Utility.encode(ProtocolConstants.Room.COMMAND_ROOM_MASTER_TRANSFER
								+ TextProtocolDriver.ARGUMENT_SEPARATOR + newMasterName);
						roomConnection.send(buf);
						if (myRoomEntryConnection.isConnected()) {
							myRoomEntryConnection.disconnect();
						}
						break;
					}
				}
			});

			roomPlayerListTable.getTable().setMenu(playerMenu);
			roomPlayerListTable.getTable().addMenuDetectListener(new MenuDetectListener() {
				@Override
				public void menuDetected(MenuDetectEvent e) {
					IStructuredSelection selection = (IStructuredSelection) roomPlayerListTable.getSelection();
					Player player = (Player) selection.getFirstElement();

					if (player == null) {
						playerMenuKick.setEnabled(false);
						playerMenuMasterTransfer.setEnabled(false);

						playerMenuChaseSsid.setSelection(false);
						playerMenuChaseSsid.setEnabled(false);
						playerMenuSetSsid.setEnabled(false);
						playerMenuCopySsid.setEnabled(false);
						return;
					}

					boolean isMasterAndOtherSelected = false;
					switch (sessionState) {
					case MY_ROOM_MASTER:
					case ROOM_MASTER:
						if (!roomMasterName.equals(player.getName())) {
							isMasterAndOtherSelected = true;
						}
						break;
					}
					playerMenuKick.setEnabled(isMasterAndOtherSelected);
					if (sessionState == SessionState.ROOM_MASTER) {
						playerMenuMasterTransfer.setEnabled(isMasterAndOtherSelected);
					} else {
						playerMenuMasterTransfer.setEnabled(false);
					}

					boolean isSelfSelected = Utility.equals(roomLoginName, player.getName());

					if (isSelfSelected || !isPacketCapturing) {
						playerMenuChaseSsid.setEnabled(false);
						playerMenuChaseSsid.setSelection(false);
					} else {
						playerMenuChaseSsid.setEnabled(currentWlanLibrary.isSSIDEnabled());
						playerMenuChaseSsid.setSelection(player.isSSIDChased());
					}

					if (Utility.isEmpty(player.getSsid())) {
						playerMenuSetSsid.setEnabled(false);
						playerMenuCopySsid.setEnabled(false);
					} else {
						playerMenuSetSsid.setEnabled(currentWlanLibrary.isSSIDEnabled() && !isSelfSelected && isPacketCapturing);
						playerMenuCopySsid.setEnabled(true);
					}
				}
			});

			Menu statusServerAddressMenu = new Menu(shell, SWT.POP_UP);

			statusServerAddressMenuCopy = new MenuItem(statusServerAddressMenu, SWT.PUSH);
			statusServerAddressMenuCopy.setText("Copy address");
			statusServerAddressMenuCopy.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					String roomAddress = roomServerAddressPort + ":" + roomMasterName;
					application.putClipboard(roomAddress);
				}
			});

			statusServerAddressLabel.setMenu(statusServerAddressMenu);
			statusServerAddressLabel.addMenuDetectListener(new MenuDetectListener() {
				@Override
				public void menuDetected(MenuDetectEvent e) {
					switch (sessionState) {
					case MY_ROOM_MASTER:
					case ROOM_PARTICIPANT:
					case ROOM_MASTER:
						statusServerAddressMenuCopy.setEnabled(true);
						break;
					default:
						statusServerAddressMenuCopy.setEnabled(false);
					}
				}
			});

			statusTunnelConnectionMenu = new Menu(shell, SWT.POP_UP);

			statusTunnelConnectionMenuChangeTransport = new MenuItem(statusTunnelConnectionMenu, SWT.PUSH);
			statusTunnelConnectionMenuChangeTransport.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					switch (application.getSettings().getTunnelTransportLayer()) {
					case TCP:
						application.getSettings().setTunnelTransportLayer(TransportLayer.UDP);
						break;
					case UDP:
						application.getSettings().setTunnelTransportLayer(TransportLayer.TCP);
						break;
					}
					tunnelConnection.disconnect();
				}
			});

			statusTunnelConnectionLabel.addMenuDetectListener(new MenuDetectListener() {
				@Override
				public void menuDetected(MenuDetectEvent e) {
					switch (sessionState) {
					case ROOM_MASTER:
					case ROOM_PARTICIPANT:
						statusTunnelConnectionLabel.setMenu(statusTunnelConnectionMenu);

						switch (application.getSettings().getTunnelTransportLayer()) {
						case TCP:
							statusTunnelConnectionMenuChangeTransport.setText("Reconnect with UDP");
							break;
						case UDP:
							statusTunnelConnectionMenuChangeTransport.setText("Reconnect with TCP");
							break;
						}
						break;
					default:
						statusTunnelConnectionLabel.setMenu(null);
					}
				}
			});

			Menu packetMonitorMenu = new Menu(shell, SWT.POP_UP);

			packetMonitorMenuCopy = new MenuItem(packetMonitorMenu, SWT.PUSH);
			packetMonitorMenuCopy.setText("Copy MAC address and username");
			packetMonitorMenuCopy.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					IStructuredSelection selection = (IStructuredSelection) packetMonitorTable.getSelection();
					TraficStatistics stats = (TraficStatistics) selection.getFirstElement();
					if (stats == null)
						return;

					application.putClipboard(stats.macAddress + "\t" + stats.playerName);
				}
			});

			packetMonitorMenuWhiteList = new MenuItem(packetMonitorMenu, SWT.CHECK);
			packetMonitorMenuWhiteList.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					IStructuredSelection selection = (IStructuredSelection) packetMonitorTable.getSelection();
					TraficStatistics stats = (TraficStatistics) selection.getFirstElement();
					if (stats == null || stats.isMine)
						return;
					if (macFilteringWhiteList.indexOf(stats.macAddress) == -1) {
						addMacAddressToWhiteList(stats.macAddress);
					} else {
						removeMacAddressFromWhiteList(stats.macAddress);
					}
				}
			});

			packetMonitorMenuBlackList = new MenuItem(packetMonitorMenu, SWT.CHECK);
			packetMonitorMenuBlackList.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					IStructuredSelection selection = (IStructuredSelection) packetMonitorTable.getSelection();
					TraficStatistics stats = (TraficStatistics) selection.getFirstElement();
					if (stats == null || stats.isMine)
						return;
					if (macFilteringBlackList.indexOf(stats.macAddress) == -1) {
						addMacAddressToBlackList(stats.macAddress);
					} else {
						removeMacAddressFromBlackList(stats.macAddress);
					}
				}
			});

			new MenuItem(packetMonitorMenu, SWT.SEPARATOR);

			packetMonitorMenuClear = new MenuItem(packetMonitorMenu, SWT.PUSH);
			packetMonitorMenuClear.setText("Clear cumulative bytes");
			packetMonitorMenuClear.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					synchronized (traficStatsMap) {
						Iterator<Entry<String, TraficStatistics>> iter = traficStatsMap.entrySet().iterator();
						while (iter.hasNext()) {
							Entry<String, TraficStatistics> entry = iter.next();
							TraficStatistics stats = entry.getValue();

							stats.totalInBytes = 0;
							stats.totalOutBytes = 0;
						}
					}
					packetMonitorTable.refresh();
				}
			});

			packetMonitorTable.getTable().setMenu(packetMonitorMenu);
			packetMonitorTable.getTable().addMenuDetectListener(new MenuDetectListener() {
				@Override
				public void menuDetected(MenuDetectEvent e) {
					packetMonitorMenuClear.setEnabled(traficStatsMap.size() > 0);

					IStructuredSelection selection = (IStructuredSelection) packetMonitorTable.getSelection();
					TraficStatistics stats = (TraficStatistics) selection.getFirstElement();

					boolean validStats = stats != null && !stats.isMine && !Utility.isMacBroadCastAddress(stats.macAddress);
					packetMonitorMenuCopy.setEnabled(validStats);
					packetMonitorMenuWhiteList.setEnabled(validStats);
					packetMonitorMenuBlackList.setEnabled(validStats);
					if (validStats) {
						boolean onWhiteList = macFilteringWhiteList.indexOf(stats.macAddress) != -1;
						packetMonitorMenuWhiteList.setSelection(onWhiteList);
						packetMonitorMenuWhiteList.setText(onWhiteList ? "Removed from white list" : "Add to white list");
						boolean onBlackList = macFilteringBlackList.indexOf(stats.macAddress) != -1;
						packetMonitorMenuBlackList.setSelection(onBlackList);
						packetMonitorMenuBlackList.setText(onBlackList ? "Removed from blacklist" : "Add to blacklist");
					} else {
						packetMonitorMenuWhiteList.setText("Add to white list");
						packetMonitorMenuBlackList.setText("Add to blacklist");
					}
				}
			});

			Menu macFilteringWhiteListMenu = new Menu(shell, SWT.POP_UP);

			macFilteringWhiteListMenuRemove = new MenuItem(macFilteringWhiteListMenu, SWT.PUSH);
			macFilteringWhiteListMenuRemove.setText("delete");
			macFilteringWhiteListMenuRemove.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					int index = macFilteringWhiteList.getSelectionIndex();
					if (index == -1)
						return;

					String address = macFilteringWhiteList.getItem(index);
					removeMacAddressFromWhiteList(address);
				}
			});

			macFilteringWhiteList.setMenu(macFilteringWhiteListMenu);
			macFilteringWhiteList.addMenuDetectListener(new MenuDetectListener() {
				@Override
				public void menuDetected(MenuDetectEvent e) {
					macFilteringWhiteListMenuRemove.setEnabled(macFilteringWhiteList.getSelectionIndex() != -1);
				}
			});

			Menu macFilteringBlackListMenu = new Menu(shell, SWT.POP_UP);

			macFilteringBlackListMenuRemove = new MenuItem(macFilteringBlackListMenu, SWT.PUSH);
			macFilteringBlackListMenuRemove.setText("delete");
			macFilteringBlackListMenuRemove.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					int index = macFilteringBlackList.getSelectionIndex();
					if (index == -1)
						return;

					String address = macFilteringBlackList.getItem(index);
					removeMacAddressFromBlackList(address);
				}
			});

			macFilteringBlackList.setMenu(macFilteringBlackListMenu);
			macFilteringBlackList.addMenuDetectListener(new MenuDetectListener() {
				@Override
				public void menuDetected(MenuDetectEvent e) {
					macFilteringBlackListMenuRemove.setEnabled(macFilteringBlackList.getSelectionIndex() != -1);
				}
			});
		}

		private void updateChatPresetButtons() {
			IniSettings settings = application.getSettings();
			String msg;
			int maxLength = settings.getChatPresetButtonMaxLength();
			if (maxLength < 1) {
				maxLength = 1;
				settings.setChatPresetButtonMaxLength(maxLength);
			}

			msg = chatTextPresets.getPresetF1();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF1.setText("1:" + msg);
			chatTextPresetF1.setEnabled(msg.length() > 0);

			msg = chatTextPresets.getPresetF2();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF2.setText("2:" + msg);
			chatTextPresetF2.setEnabled(msg.length() > 0);

			msg = chatTextPresets.getPresetF3();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF3.setText("3:" + msg);
			chatTextPresetF3.setEnabled(msg.length() > 0);

			msg = chatTextPresets.getPresetF4();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF4.setText("4:" + msg);
			chatTextPresetF4.setEnabled(msg.length() > 0);

			msg = chatTextPresets.getPresetF5();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF5.setText("5:" + msg);
			chatTextPresetF5.setEnabled(msg.length() > 0);

			msg = chatTextPresets.getPresetF6();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF6.setText("6:" + msg);
			chatTextPresetF6.setEnabled(msg.length() > 0);

			msg = chatTextPresets.getPresetF7();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF7.setText("7:" + msg);
			chatTextPresetF7.setEnabled(msg.length() > 0);

			msg = chatTextPresets.getPresetF8();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF8.setText("8:" + msg);
			chatTextPresetF8.setEnabled(msg.length() > 0);

			msg = chatTextPresets.getPresetF9();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF9.setText("9:" + msg);
			chatTextPresetF9.setEnabled(msg.length() > 0);

			msg = chatTextPresets.getPresetF10();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF10.setText("10:" + msg);
			chatTextPresetF10.setEnabled(msg.length() > 0);

			msg = chatTextPresets.getPresetF11();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF11.setText("11:" + msg);
			chatTextPresetF11.setEnabled(msg.length() > 0);

			msg = chatTextPresets.getPresetF12();
			msg = msg.length() > maxLength ? msg.substring(0, maxLength) : msg;
			chatTextPresetF12.setText("12:" + msg);
			chatTextPresetF12.setEnabled(msg.length() > 0);

			GridData data = (GridData) chatPresetContainer.getLayoutData();
			data.exclude = !application.getSettings().isShowChatPresetButtons();
			chatPresetContainer.getParent().layout(true, true);
		}
	}

	@Override
	public Type getType() {
		return Type.ROOM;
	}

	@Override
	public void settingChanged() {
		widgets.updateChatPresetButtons();

		WlanLibrary library = application.getSettings().getWlanLibrary();
		if (library != currentWlanLibrary && currentWlanDevice == WlanDevice.NULL) {
			currentWlanLibrary = library;
			refreshLanAdapterList();
		}
	}

	private void initBackgroundThreads() {
		packetCaptureThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Runnable prepareCaptureEndAction = new Runnable() {
					@Override
					public void run() {
						try {
							isPacketCapturing = false;
							widgets.wlanPspCommunicationButton.setEnabled(false);
						} catch (SWTException e) {
						}
					}
				};
				Runnable captureEndAction = new Runnable() {
					@Override
					public void run() {
						try {
							widgets.wlanAdapterListCombo.setEnabled(true);
							widgets.wlanPspCommunicationButton.setText("Start communication with PSP");
							widgets.wlanPspCommunicationButton.setEnabled(true);

							WlanLibrary library = application.getSettings().getWlanLibrary();
							if (library != currentWlanLibrary) {
								currentWlanLibrary = library;
								refreshLanAdapterList();
							}

							if (currentWlanLibrary.isSSIDEnabled())
								updateSsidStartScan(false);
						} catch (SWTException e) {
						}
					}
				};

				try {
					while (!shell.isDisposed()) {
						synchronized (packetCaptureThread) {
							if (!isPacketCapturing)
								packetCaptureThread.wait();
						}

						try {
							while (isPacketCapturing) {
								bufferForCapturing.clear();
								int ret = currentWlanDevice.capturePacket(bufferForCapturing);
								if (ret > 0) {
									bufferForCapturing.flip();
									processCapturedPacket();
								} else if (ret == 0) {
								} else {
									SwtUtils.DISPLAY.syncExec(prepareCaptureEndAction);
									break;
								}
							}
						} catch (Exception e) {
							application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
							isPacketCapturing = false;
						}

						currentWlanDevice.close();
						currentWlanDevice = WlanDevice.NULL;

						SwtUtils.DISPLAY.syncExec(captureEndAction);
					}
				} catch (SWTException e) {
				} catch (Exception e) {
					application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
				}
			}
		}, "PacketCaptureThread");
		packetCaptureThread.setDaemon(true);

		wlanScannerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				final ArrayList<WlanNetwork> networkList = new ArrayList<WlanNetwork>();

				Runnable refreshAction = new Runnable() {
					@Override
					public void run() {
						try {
							if (widgets.ssidAutoDetectCheck.getSelection()) {
								String currentSSID = widgets.ssidCurrentSsidText.getText();
								String match = widgets.ssidMatchText.getText();
								for (WlanNetwork bssid : networkList) {
									String ssid = bssid.getSsid();

									if (!ssid.equals(currentSSID) && ssid.startsWith(match)) {
										changeSSID(ssid);
										break;
									}
								}
							} else {
								checkSsidChange();
							}
							widgets.ssidListTableViewer.setInput(networkList);
							widgets.ssidListTableViewer.refresh();
						} catch (SWTException e) {
						}
					}
				};
				Runnable clearAction = new Runnable() {
					@Override
					public void run() {
						try {
							networkList.clear();
							widgets.ssidListTableViewer.setInput(networkList);
							widgets.ssidListTableViewer.refresh();
						} catch (SWTException e) {
						}
					}
				};

				try {
					while (!shell.isDisposed()) {
						synchronized (wlanScannerThread) {
							while (!isSSIDScaning)
								wlanScannerThread.wait();
						}

						while (isSSIDScaning) {
							long nextIteration = System.currentTimeMillis() + scanIntervalMillis;

							networkList.clear();
							currentWlanDevice.findNetworks(networkList);
							SwtUtils.DISPLAY.syncExec(refreshAction);

							currentWlanDevice.scanNetwork();

							long diff = nextIteration - System.currentTimeMillis();
							if (diff > 0)
								Thread.sleep(diff);
						}

						SwtUtils.DISPLAY.asyncExec(clearAction);
					}
				} catch (SWTException e) {
				} catch (InterruptedException e) {
				}
			}
		}, "WlanScannerThread");
		wlanScannerThread.setDaemon(true);

		packetMonitorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				int intervalMillis = 1000;

				Runnable refreshAction = new Runnable() {
					@Override
					public void run() {
						try {
							checkSsidChange();

							widgets.packetMonitorTable.setInput(traficStatsMap);
							widgets.packetMonitorTable.refresh();
						} catch (SWTException e) {
						}
					}
				};
				Runnable clearAction = new Runnable() {
					@Override
					public void run() {
						try {
							synchronized (traficStatsMap) {
								traficStatsMap.clear();
							}
							widgets.packetMonitorTable.setInput(traficStatsMap);
						} catch (SWTException e) {
						}
					}
				};

				try {
					while (!shell.isDisposed()) {
						synchronized (packetMonitorThread) {
							if (!isPacketCapturing && !tunnelIsLinked)
								packetMonitorThread.wait();
						}

						while (isPacketCapturing || tunnelIsLinked) {
							long deadlineTime = System.currentTimeMillis() - 10000;

							synchronized (traficStatsMap) {
								Iterator<Entry<String, TraficStatistics>> iter = traficStatsMap.entrySet().iterator();
								while (iter.hasNext()) {
									Entry<String, TraficStatistics> entry = iter.next();
									TraficStatistics stats = entry.getValue();

									if (stats.lastModified < deadlineTime) {
										iter.remove();
										continue;
									}

									stats.currentInKbps = ((double) stats.currentInBytes) * 8 / intervalMillis;
									stats.currentOutKbps = ((double) stats.currentOutBytes) * 8 / intervalMillis;

									stats.currentInBytes = 0;
									stats.currentOutBytes = 0;
								}

								String text;
								if (actualSentBytes == 0 && actualRecievedBytes == 0) {
									text = " No traffic ";
								} else {
									double totalInKbps = ((double) actualRecievedBytes) * 8 / intervalMillis;
									double totalOutKbps = ((double) actualSentBytes) * 8 / intervalMillis;
									text = String.format(" In: %.1f Kbps   Out: %.1f Kbps ", totalInKbps, totalOutKbps);

									actualSentBytes = 0;
									actualRecievedBytes = 0;
								}
								updateTraficStatus(text);
							}

							SwtUtils.DISPLAY.syncExec(refreshAction);

							Thread.sleep(intervalMillis);
						}

						SwtUtils.DISPLAY.syncExec(clearAction);
					}
				} catch (SWTException e) {
				} catch (InterruptedException e) {
				}
			}
		}, "PacketMonitorThread");
		packetMonitorThread.setDaemon(true);
	}

	private void wakeupThread(Thread thread) {
		synchronized (thread) {
			if (thread.isAlive()) {
				thread.notify();
				return;
			}
		}
		thread.start();
	}

	public void show() {
		shell.open();
	}

	public void hide() {
		shell.setVisible(false);
	}

	public void reflectAppearance() {
		widgets.logViewer.applyAppearance();
		shell.layout(true, true);
	}

	private long nextPingTime = 0L;

	public void cronJob() {
		switch (sessionState) {
		case ROOM_MASTER:
		case ROOM_PARTICIPANT:
			long now = System.currentTimeMillis();
			if (now < nextPingTime)
				return;

			ByteBuffer buf = Utility.encode(ProtocolConstants.Room.COMMAND_PING + TextProtocolDriver.ARGUMENT_SEPARATOR + now);
			roomConnection.send(buf);

			nextPingTime = now + 5000;
			break;
		}
	}

	private void connectToRoomServer(final InetSocketAddress socketAddress) {
		Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					application.connectTcp(socketAddress, roomProtocol);
					return;
				} catch (SocketTimeoutException e) {
					ErrorLog log = new ErrorLog("Could not connect to the server");
					widgets.logViewer.appendMessage(log);
				} catch (IOException e) {
					ErrorLog log = new ErrorLog(e);
					widgets.logViewer.appendMessage(log);
				} catch (RuntimeException e) {
					ErrorLog log = new ErrorLog(e);
					widgets.logViewer.appendMessage(log);
				}
				changeStateTo(SessionState.OFFLINE);
			}
		};
		application.execute(task);
	}

	private interface ServerSelectAction {
		public String getOkLabel();

		public void select(String address);

		public void cancel();
	}

	private void selectRoomServer(final ServerSelectAction action) {
		PortalQuery query = new PortalQuery() {
			@Override
			public String getCommand() {
				return ProtocolConstants.Portal.COMMAND_LIST_ROOM_SERVERS;
			}

			@Override
			public void failCallback(ErrorLog log) {
				action.cancel();
				widgets.logViewer.appendMessage(log);
			}

			@Override
			public void successCallback(String message) {
				ArrayList<RoomServerInfo> list = new ArrayList<RoomServerInfo>();
				for (String info : message.split("\n")) {
					try {
						String[] values = info.split("\t");
						String address = values[0];
						int currentRooms = Integer.parseInt(values[1]);
						int maxRooms = Integer.parseInt(values[2]);

						RoomServerInfo server = new RoomServerInfo(address, currentRooms, maxRooms);
						list.add(server);
					} catch (NumberFormatException e) {
					}
				}

				showRoomServerSelectDialog(list, action);
			}
		};
		application.queryPortalServer(query);
	}

	private void showRoomServerSelectDialog(final java.util.List<RoomServerInfo> list, final ServerSelectAction action) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						showRoomServerSelectDialog(list, action);
					}
				});
				return;
			}

			if (list.isEmpty()) {
				ErrorLog log = new ErrorLog("Room server not found");
				widgets.logViewer.appendMessage(log);

				action.cancel();
			} else {
				RoomServerSelectDialog dialog = new RoomServerSelectDialog(shell, list, action.getOkLabel());
				switch (dialog.open()) {
				case IDialogConstants.OK_ID:
					RoomServerInfo selected = dialog.getSelectedServer();
					action.select(selected.getAddress());
					break;
				case IDialogConstants.CANCEL_ID:
					action.cancel();
					break;
				}
			}
		} catch (SWTException e) {
		}

	}

	private void autoConnectAsMaster() {
		if (!checkConfigUserName() || !checkRoomFormTitle())
			return;

		widgets.formAutoModeRoomButton.setEnabled(false);

		PortalQuery query = new PortalQuery() {
			@Override
			public String getCommand() {
				return ProtocolConstants.Portal.COMMAND_FIND_ROOM_SERVER;
			}

			@Override
			public void failCallback(ErrorLog log) {
				revertRoomFormAutoModeButton();

				widgets.logViewer.appendMessage(log);
			}

			@Override
			public void successCallback(String address) {
				autoConnectAsMaster(address);
			}
		};

		application.queryPortalServer(query);
	}

	private void autoConnectAsMaster(final String address) {
		try {
			if (SwtUtils.isNotUIThread()) {
				if (address == null) {
					revertRoomFormAutoModeButton();

					ErrorLog log = new ErrorLog("Could not get the address");
					widgets.logViewer.appendMessage(log);
					return;
				}
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						autoConnectAsMaster(address);
					}
				});
				return;
			}

			InetSocketAddress socketAddress = Utility.parseSocketAddress(address);
			if (socketAddress == null) {
				revertRoomFormAutoModeButton();

				ErrorLog log = new ErrorLog("Incorrect server address");
				widgets.logViewer.appendMessage(log);
				return;
			}

			changeStateTo(SessionState.CONNECTING_ROOM_MASTER);
			roomMasterName = roomLoginName;
			roomServerAddressPort = address;

			connectToRoomServer(socketAddress);
		} catch (SWTException e) {
		}
	}

	private void revertRoomFormAutoModeButton() {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						revertRoomFormAutoModeButton();
					}
				});
				return;
			}

			widgets.formAutoModeRoomButton.setEnabled(true);
		} catch (SWTException e) {
		}
	}

	public void autoConnectAsParticipant(PlayRoom room) {
		if (shell.getMinimized())
			shell.setMinimized(false);
		shell.open();

		if (sessionState != SessionState.OFFLINE) {
			ErrorLog log = new ErrorLog("You are currently logged in to the room");
			widgets.logViewer.appendMessage(log);
			return;
		}
		if (!checkConfigUserName())
			return;

		InetSocketAddress socketAddress = Utility.parseSocketAddress(room.getServerAddress());
		if (socketAddress == null) {
			ErrorLog log = new ErrorLog("Incorrect server address");
			widgets.logViewer.appendMessage(log);
			return;
		}

		widgets.formModeSelectionCombo.select(0);
		updateRoomModeSelection();

		changeStateTo(SessionState.CONNECTING_ROOM_PARTICIPANT);
		roomMasterName = room.getMasterName();
		roomServerAddressPort = room.getServerAddress();

		connectToRoomServer(socketAddress);
	}

	private void manualConnectAsMaster() {
		if (!checkConfigUserName() || !checkRoomFormTitle())
			return;

		String address = widgets.formManualModeRoomServerCombo.getText();
		if (Utility.isEmpty(address)) {
			ErrorLog log = new ErrorLog("Please enter the server address");
			widgets.logViewer.appendMessage(log);
			return;
		}

		InetSocketAddress socketAddress = Utility.parseSocketAddress(address);
		if (socketAddress == null) {
			ErrorLog log = new ErrorLog("Incorrect server address");
			widgets.logViewer.appendMessage(log);
			return;
		}

		changeStateTo(SessionState.CONNECTING_ROOM_MASTER);
		if (socketAddress.getAddress().isLoopbackAddress()) {
			roomServerAddressPort = ":" + socketAddress.getPort();
		} else {
			roomServerAddressPort = address;
		}
		roomMasterName = roomLoginName;

		connectToRoomServer(socketAddress);
	}

	private void manualConnectAsParticipant() {
		if (!checkConfigUserName())
			return;

		String address = widgets.formManualModeRoomAddressCombo.getText();
		if (Utility.isEmpty(address)) {
			ErrorLog log = new ErrorLog("Please enter the server address");
			widgets.logViewer.appendMessage(log);
			return;
		}

		String[] tokens = address.split(":", 3);

		roomMasterName = null;
		switch (tokens.length) {
		case 2:
			roomMasterName = "";
			break;
		case 3:
			roomMasterName = tokens[2];
			break;
		default:
			ErrorLog log = new ErrorLog("Incorrect server address");
			widgets.logViewer.appendMessage(log);
			return;
		}

		InetSocketAddress socketAddress = Utility.parseSocketAddress(tokens[0], tokens[1]);
		if (socketAddress == null) {
			ErrorLog log = new ErrorLog("Incorrect server address");
			widgets.logViewer.appendMessage(log);
			return;
		}

		changeStateTo(SessionState.CONNECTING_ROOM_PARTICIPANT);
		if (socketAddress.getAddress().isLoopbackAddress()) {
			roomServerAddressPort = ":" + socketAddress.getPort();
			if (roomMasterName.equals("")) {
				widgets.formManualModeRoomAddressCombo.setText(roomServerAddressPort);
			} else {
				widgets.formManualModeRoomAddressCombo.setText(roomServerAddressPort + ":" + roomMasterName);
			}
		} else {
			roomServerAddressPort = Utility.socketAddressToStringByHostName(socketAddress);
		}

		connectToRoomServer(socketAddress);
	}

	private void startMyRoomServer() throws IOException {
		int port = widgets.formMyRoomModePortSpinner.getSelection();

		if (!checkConfigUserName())
			return;

		String title = widgets.formTitleText.getText();
		if (Utility.isEmpty(title)) {
			RoomLog log = new RoomLog("Please enter the room name");
			widgets.logViewer.appendMessage(log);

			widgets.formTitleText.setFocus();
			return;
		}

		myRoomEngine.setTitle(title);
		myRoomEngine.setMaxPlayers(widgets.formMaxPlayersSpiner.getSelection());
		myRoomEngine.setPassword(widgets.formPasswordText.getText());
		myRoomEngine.setDescription(widgets.formDescriptionText.getText());
		myRoomEngine.setRemarks(widgets.formRemarksText.getText());

		try {
			myRoomEngine.openRoom(port, roomLoginName);

			widgets.formMasterNameText.setText(roomLoginName);
			roomMasterName = roomLoginName;
			roomServerAddressPort = widgets.formMyRoomModeHostText.getText() + ":" + port;

			widgets.formModeSelectionCombo.setEnabled(false);
			widgets.formMyRoomModePortSpinner.setEnabled(false);
			widgets.formMyRoomModeStartButton.setEnabled(false);
		} catch (BindException e) {
			ErrorLog log = new ErrorLog("The same port is already in use");
			widgets.logViewer.appendMessage(log);
		} catch (RuntimeException e) {
			application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
		}
	}

	private void autoConnectAsMyRoom() {
		if (sessionState != SessionState.MY_ROOM_MASTER) {
			return;
		}

		if (widgets.formEditSubmitButton.getEnabled()) {
			if (!commitRoomEditForm()) {
				widgets.formMyRoomModeStartButton.setSelection(false);
				return;
			}
		}

		widgets.formMyRoomModeEntryButton.setEnabled(false);
		widgets.formMyRoomModeEntryButton.setSelection(true);

		PortalQuery query = new PortalQuery() {
			@Override
			public String getCommand() {
				return ProtocolConstants.Portal.COMMAND_FIND_ROOM_SERVER;
			}

			@Override
			public void failCallback(ErrorLog log) {
				updateMyRoomEntryForm(true);

				widgets.logViewer.appendMessage(log);
			}

			@Override
			public void successCallback(String address) {
				autoConnectAsMyRoom(address);
			}
		};

		application.queryPortalServer(query);
	}

	private void autoConnectAsMyRoom(final String address) {
		try {
			if (SwtUtils.isNotUIThread()) {
				if (address == null) {
					updateMyRoomEntryForm(false);

					ErrorLog log = new ErrorLog("Could not get the address");
					widgets.logViewer.appendMessage(log);
					return;
				}
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						autoConnectAsMyRoom(address);
					}
				});
				return;
			}

			widgets.formMyRoomModeRoomServer.setText(address);

			Runnable task = new Runnable() {
				@Override
				public void run() {
					try {
						InetSocketAddress socketAddress = Utility.parseSocketAddress(address);
						application.connectTcp(socketAddress, myRoomEntryProtocol);
						return;
					} catch (IOException e) {
						ErrorLog log = new ErrorLog(e);
						widgets.logViewer.appendMessage(log);
					} catch (RuntimeException e) {
						ErrorLog log = new ErrorLog(e);
						widgets.logViewer.appendMessage(log);
					}
					updateMyRoomEntryForm(false);
				}
			};
			application.execute(task);
		} catch (SWTException e) {
		}
	}

	private void updateMyRoomEntryForm(final boolean entryOn) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateMyRoomEntryForm(entryOn);
					}
				});
				return;
			}

			if (entryOn) {
				widgets.formMyRoomModeEntryButton.setText("Cancel search");
				widgets.formMyRoomModeEntryButton.setSelection(true);
				widgets.formMyRoomModeEntryButton.setEnabled(true);
				widgets.formMyRoomModeHostText.setEnabled(false);
			} else {
				widgets.formMyRoomModeRoomServer.setText("");
				widgets.formMyRoomModeEntryButton.setText("Search registration");
				widgets.formMyRoomModeEntryButton.setSelection(false);
				widgets.formMyRoomModeEntryButton.setEnabled(sessionState != SessionState.OFFLINE);
				widgets.formMyRoomModeHostText.setEnabled(true);
			}
		} catch (SWTException e) {
		}
	}

	private void addMacAddressToWhiteList(String macAddress) {
		switch (sessionState) {
		case MY_ROOM_MASTER: {
			myRoomEngine.addMacAddressToWhiteList(macAddress);
			break;
		}
		case ROOM_MASTER: {
			StringBuilder sb = new StringBuilder();
			sb.append(ProtocolConstants.Room.COMMAND_WHITELIST_ADD);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(macAddress);
			roomConnection.send(Utility.encode(sb));
			break;
		}
		}
		widgets.macFilteringWhiteList.add(macAddress);
	}

	private void removeMacAddressFromWhiteList(String macAddress) {
		switch (sessionState) {
		case MY_ROOM_MASTER: {
			myRoomEngine.removeMacAddressFromWhiteList(macAddress);
			break;
		}
		case ROOM_MASTER: {
			StringBuilder sb = new StringBuilder();
			sb.append(ProtocolConstants.Room.COMMAND_WHITELIST_REMOVE);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(macAddress);
			roomConnection.send(Utility.encode(sb));
			break;
		}
		}
		widgets.macFilteringWhiteList.remove(macAddress);
	}

	private void addMacAddressToBlackList(String macAddress) {
		switch (sessionState) {
		case MY_ROOM_MASTER: {
			myRoomEngine.addMacAddressToBlackList(macAddress);
			break;
		}
		case ROOM_MASTER: {
			StringBuilder sb = new StringBuilder();
			sb.append(ProtocolConstants.Room.COMMAND_BLACKLIST_ADD);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(macAddress);
			roomConnection.send(Utility.encode(sb));
			break;
		}
		}
		widgets.macFilteringBlackList.add(macAddress);
	}

	private void removeMacAddressFromBlackList(String macAddress) {
		switch (sessionState) {
		case MY_ROOM_MASTER: {
			myRoomEngine.removeMacAddressFromBlackList(macAddress);
			break;
		}
		case ROOM_MASTER: {
			StringBuilder sb = new StringBuilder();
			sb.append(ProtocolConstants.Room.COMMAND_BLACKLIST_REMOVE);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(macAddress);
			roomConnection.send(Utility.encode(sb));
			break;
		}
		}
		widgets.macFilteringBlackList.remove(macAddress);
	}

	private boolean checkConfigUserName() {
		IniUserProfile profile = application.getUserProfile();

		String name = profile.getUserName();
		if (Utility.isEmpty(name)) {
			application.openConfigDialog(shell, MiscSettingPage.PAGE_ID);
			name = profile.getUserName();
			if (Utility.isEmpty(name))
				return false;
		}

		roomLoginName = name;
		return true;
	}

	private boolean checkRoomFormTitle() {
		String title = widgets.formTitleText.getText();
		if (Utility.isEmpty(title)) {
			RoomLog log = new RoomLog("Please enter the room name");
			widgets.logViewer.appendMessage(log);

			widgets.formTitleText.setFocus();
			return false;
		}
		return true;
	}

	int confirmRoomDelete(boolean onExit) {
		switch (sessionState) {
		case ROOM_MASTER: {
			if (roomPlayerMap.size() < 2) {
				if (onExit) {
					return -1;
				} else {
					roomConnection.disconnect();
					return 1;
				}
			}

			RoomDeleteDialog dialog = new RoomDeleteDialog(shell);
			dialog.open();
			switch (dialog.getSelection()) {
			case LOGOUT:
				roomConnection.disconnect();
				return 1;
			case DESTROY:
				roomConnection.send(Utility.encode(ProtocolConstants.Room.COMMAND_ROOM_DELETE));
				return 1;
			case CANCEL:
				return 0;
			}
			break;
		}
		case MY_ROOM_MASTER: {
			ConfirmDialog dialog = new ConfirmDialog(shell, "My room is still running", "Close my room. Is it OK?");
			switch (dialog.open()) {
			case IDialogConstants.OK_ID:
				myRoomEngine.closeRoom();
				return 1;
			case IDialogConstants.CANCEL_ID:
				return 0;
			}
			break;
		}
		}
		return -1;
	}

	private void appendRoomInfo(StringBuilder sb) {
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(widgets.formMaxPlayersSpiner.getText());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(widgets.formTitleText.getText());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(widgets.formPasswordText.getText());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(widgets.formDescriptionText.getText());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(widgets.formRemarksText.getText());
	}

	private boolean commitRoomEditForm() {
		String title = widgets.formTitleText.getText();
		if (Utility.isEmpty(title)) {
			ErrorLog log = new ErrorLog("Please enter the room name");
			widgets.logViewer.appendMessage(log);

			widgets.formTitleText.setFocus();
			return false;
		}
		widgets.formEditSubmitButton.setEnabled(false);

		switch (sessionState) {
		case MY_ROOM_MASTER:
			myRoomEngine.setTitle(title);
			myRoomEngine.setMaxPlayers(widgets.formMaxPlayersSpiner.getSelection());
			myRoomEngine.setPassword(widgets.formPasswordText.getText());
			myRoomEngine.setDescription(widgets.formDescriptionText.getText());
			myRoomEngine.setRemarks(widgets.formRemarksText.getText());

			myRoomEngine.updateRoom();

			RoomLog log = new RoomLog("Updated room information");
			widgets.logViewer.appendMessage(log);

			widgets.chatText.setFocus();
			sendMyRoomUpdate();
			break;
		case ROOM_MASTER:
			StringBuilder sb = new StringBuilder();
			sb.append(ProtocolConstants.Room.COMMAND_ROOM_UPDATE);
			appendRoomInfo(sb);

			roomConnection.send(Utility.encode(sb));

			break;
		}

		return true;
	}

	private boolean sendChat(String message) {
		if (!Utility.isEmpty(message)) {
			switch (sessionState) {
			case MY_ROOM_MASTER:
				myRoomEngine.sendChat(message);
				return true;
			case ROOM_MASTER:
			case ROOM_PARTICIPANT:
				ByteBuffer buf = Utility.encode(ProtocolConstants.Room.COMMAND_CHAT + TextProtocolDriver.ARGUMENT_SEPARATOR + message);
				roomConnection.send(buf);
				return true;
			default:
				InfoLog log = new InfoLog("You are not logged in to the server");
				widgets.logViewer.appendMessage(log);
			}
		}
		return false;
	}

	private void processChat(String player, String message) {
		boolean isMine = roomLoginName.equals(player);

		String name = player;
		for (String line : message.replace("\r", "").split("\n", -1)) {
			Chat chat = new Chat(name, line, isMine);
			widgets.logViewer.appendMessage(chat);
			name = "";
		}

		Chat chat = new Chat(player, message, roomLoginName.equals(player));

		if (!isMine && !isActiveWindow && application.getSettings().isBallonNotifyRoom()) {
			application.balloonNotify(shell, "<" + player + "> " + message);
		}

		application.roomMessageReceived(chat);
	}

	private void processAdminNotify(String message) {
		for (String line : message.replace("\r", "").split("\n", -1)) {
			AdminNotify log = new AdminNotify(line);
			widgets.logViewer.appendMessage(log);
		}

		if (!isActiveWindow)
			application.balloonNotify(shell, message);
	}

	private void checkSsidChange() {
		if (System.currentTimeMillis() < nextSsidCheckTime)
			return;
		String latestSSID = currentWlanDevice.getSSID();
		if (latestSSID == null)
			latestSSID = "";

		String currentSSID = widgets.ssidCurrentSsidText.getText();
		if (!latestSSID.equals(currentSSID))
			setAndSendInformNewSSID(latestSSID);

		if (isPacketCapturing && application.getSettings().isSsidAutoScan())
			if ("".equals(latestSSID) && !widgets.ssidStartScan.getSelection()) {
				widgets.ssidStartScan.setSelection(true);
				updateSsidStartScan(true);
			}

		nextSsidCheckTime = System.currentTimeMillis() + 3000;
	}

	private void changeSSID(String newSSID) {
		if (!Utility.isEmpty(newSSID))
			currentWlanDevice.setSSID(newSSID);
		setAndSendInformNewSSID(newSSID);
		updateSsidStartScan(false);

		nextSsidCheckTime = System.currentTimeMillis() + 7000;
	}

	private void setAndSendInformNewSSID(String latestSSID) {
		widgets.ssidCurrentSsidText.setText(latestSSID);
		updateRoomPlayerSSID(roomLoginName, latestSSID);

		switch (sessionState) {
		case MY_ROOM_MASTER:
			myRoomEngine.informSSID(latestSSID);
			break;
		case ROOM_MASTER:
		case ROOM_PARTICIPANT:
			if (roomConnection.isConnected()) {
				ByteBuffer buf = Utility.encode(ProtocolConstants.Room.COMMAND_INFORM_SSID + TextProtocolDriver.ARGUMENT_SEPARATOR
						+ latestSSID);
				roomConnection.send(buf);
			}
			break;
		}
	}

	private void updateSsidStartScan(boolean startScan) {
		if (!currentWlanLibrary.isSSIDEnabled())
			return;
		isSSIDScaning = startScan;
		widgets.ssidStartScan.setSelection(isSSIDScaning);
		widgets.ssidStartScan.setText(isSSIDScaning ? "Scanning" : "Start scanning");
		if (isSSIDScaning)
			wakeupThread(wlanScannerThread);
	}

	private void updateLoginStatus() {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateLoginStatus();
					}
				});
				return;
			}

			switch (sessionState) {
			case OFFLINE:
				widgets.statusUserNameLabel.setText("You are not logged in to the room");
				widgets.statusServerAddressLabel.setText("No room address");
				break;
			default:
				String roomAddress;
				if (roomMasterName.equals("")) {
					roomAddress = roomServerAddressPort;
				} else {
					roomAddress = roomServerAddressPort + ":" + roomMasterName;
				}

				widgets.statusUserNameLabel.setText("username: " + roomLoginName);
				widgets.statusServerAddressLabel.setText("Room address  " + roomAddress);
			}
			widgets.statusBarContainer.layout();
		} catch (SWTException e) {
		}
	}

	private void updateTunnelStatus(final boolean isLinked) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateTunnelStatus(isLinked);
					}
				});
				return;
			}

			tunnelIsLinked = isLinked;
			if (tunnelIsLinked)
				wakeupThread(packetMonitorThread);

			ColorRegistry colorRegistry = application.getColorRegistry();
			if (tunnelIsLinked) {
				widgets.statusTunnelConnectionLabel.setForeground(colorRegistry.get(PlayClient.COLOR_OK));

				StringBuilder sb = new StringBuilder();

				if (sessionState != SessionState.MY_ROOM_MASTER)
					switch (application.getSettings().getTunnelTransportLayer()) {
					case TCP:
						sb.append("TCP");
						break;
					case UDP:
						sb.append("UDP");
						break;
					}
				sb.append("During tunnel connection");
				widgets.statusTunnelConnectionLabel.setText(sb.toString());
			} else {
				widgets.statusTunnelConnectionLabel.setForeground(colorRegistry.get(PlayClient.COLOR_NG));
				widgets.statusTunnelConnectionLabel.setText("Tunnel not connected");
			}
			widgets.statusBarContainer.layout();
		} catch (SWTException e) {
		}
	}

	private void updateTraficStatus(final String text) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateTraficStatus(text);
					}
				});
				return;
			}

			widgets.statusTraficStatusLabel.setText(text);
			widgets.statusBarContainer.layout();
		} catch (SWTException e) {
		}
	}

	private void replaceRoomPlayerList(final String[] playerInfoList) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						replaceRoomPlayerList(playerInfoList);
					}
				});
				return;
			}
			TableViewer viewer = widgets.roomPlayerListTable;

			viewer.getTable().clearAll();
			roomPlayerMap.clear();
			for (int i = 0; i < playerInfoList.length - 1; i++) {
				String name = playerInfoList[i];
				String ssid = playerInfoList[++i];

				if (Utility.isEmpty(name))
					continue;

				Player player = new Player(name);
				player.setSsid(name.equals(roomLoginName) ? widgets.ssidCurrentSsidText.getText() : ssid);

				roomPlayerMap.put(name, player);
				viewer.add(player);
			}
			viewer.refresh();
		} catch (SWTException e) {
		}
	}

	private void addRoomPlayer(final String name) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						addRoomPlayer(name);
					}
				});
				return;
			}
			TableViewer viewer = widgets.roomPlayerListTable;

			InfoLog log = new InfoLog(name + " Has entered the room");
			widgets.logViewer.appendMessage(log);

			if (!isActiveWindow && application.getSettings().isBallonNotifyRoom())
				application.balloonNotify(shell, log.getMessage());

			Player player = new Player(name);

			roomPlayerMap.put(name, player);
			viewer.add(player);
			viewer.refresh();

			sendMyRoomPlayerCountChange();
		} catch (SWTException e) {
		}
	}

	private void removeRoomPlayer(final String name) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						removeRoomPlayer(name);
					}
				});
				return;
			}
			TableViewer viewer = widgets.roomPlayerListTable;

			Player player = roomPlayerMap.remove(name);
			if (player == null)
				return;

			viewer.remove(player);
			viewer.refresh();

			sendMyRoomPlayerCountChange();
		} catch (SWTException e) {
		}
	}

	private void removeExitingRoomPlayer(String name) {
		InfoLog log = new InfoLog(name + " Has left the room");
		widgets.logViewer.appendMessage(log);
		if (!isActiveWindow && application.getSettings().isBallonNotifyRoom())
			application.balloonNotify(shell, log.getMessage());
		removeRoomPlayer(name);
	}

	private void removeKickedRoomPlayer(String name) {
		switch (sessionState) {
		case MY_ROOM_MASTER:
		case ROOM_MASTER: {
			RoomLog log = new RoomLog(name + " Was kicked out of the room");
			widgets.logViewer.appendMessage(log);
			if (!isActiveWindow && application.getSettings().isBallonNotifyRoom())
				application.balloonNotify(shell, log.getMessage());
			break;
		}
		case ROOM_PARTICIPANT: {
			RoomLog log = new RoomLog(name + " Was kicked out of the room");
			widgets.logViewer.appendMessage(log);
			if (!isActiveWindow && application.getSettings().isBallonNotifyRoom())
				application.balloonNotify(shell, log.getMessage());
			break;
		}
		}
		removeRoomPlayer(name);
	}

	private void updateRoomPlayerPing(final String name, final int ping) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateRoomPlayerPing(name, ping);
					}
				});
				return;
			}

			HashMap<String, Player> map = roomPlayerMap;
			Player player = map.get(name);
			if (player == null)
				return;

			player.setPing(ping);
			widgets.roomPlayerListTable.refresh(player);
		} catch (SWTException e) {
		}
	}

	private void updateRoomPlayerSSID(final String name, final String ssid) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateRoomPlayerSSID(name, ssid);
					}
				});
				return;
			}

			Player player = roomPlayerMap.get(name);
			if (player == null)
				return;

			player.setSsid(ssid);
			widgets.roomPlayerListTable.refresh(player);

			if (player.isSSIDChased() && isPacketCapturing) {
				changeSSID(ssid);
			}
		} catch (SWTException e) {
		}
	}

	private void updateRoom(final String[] tokens, final boolean isInitialUpdate) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateRoom(tokens, isInitialUpdate);
					}
				});
				return;
			}

			String masterName = tokens[0];
			int maxPlayers = Integer.parseInt(tokens[1]);
			String title = tokens[2];
			String password = tokens[3];
			long created = Long.parseLong(tokens[4]);
			String description = tokens[5];
			String remarks = tokens[6];

			isRoomInfoUpdating = true;

			widgets.formMasterNameText.setText(masterName);
			widgets.formMaxPlayersSpiner.setSelection(maxPlayers);
			widgets.formTitleText.setText(title);
			widgets.formPasswordText.setText(password);
			widgets.formTimestampText.setText(PlayRoomUtils.DATE_FORMAT.format(new Date(created)));
			widgets.formDescriptionText.setText(description);
			widgets.formRemarksText.setText(remarks);

			isRoomInfoUpdating = false;
			widgets.formEditSubmitButton.setEnabled(false);

			if (isInitialUpdate)
				return;

			RoomLog log;
			log = new RoomLog("Room information has been updated");
			widgets.logViewer.appendMessage(log);

			if (!masterName.equals(roomMasterName)) {
				roomMasterName = masterName;

				log = new RoomLog("The room owner " + roomMasterName + " Changed to");
				widgets.logViewer.appendMessage(log);

				if (!isActiveWindow && application.getSettings().isBallonNotifyRoom())
					application.balloonNotify(shell, log.getMessage());

				updateLoginStatus();

				if (masterName.equals(roomLoginName)) {
					changeStateTo(SessionState.ROOM_MASTER);
				} else if (sessionState == SessionState.ROOM_MASTER) {
					widgets.formManualModeRoomAddressCombo.setEnabled(false);
					widgets.formManualModeRoomAddressCombo.setText(roomServerAddressPort + ":" + masterName);
					changeStateTo(SessionState.ROOM_PARTICIPANT);
				} else {
					widgets.formManualModeRoomAddressCombo.setText(roomServerAddressPort + ":" + masterName);
				}
			}
		} catch (NumberFormatException e) {
		} catch (SWTException e) {
		}
	}

	private void changeLobbyStateTo(LobbyUserState userState) {
		ArenaWindow window = application.getArenaWindow();
		if (window != null)
			window.changeLobbyStateTo(userState);
	}

	private void changeStateTo(final SessionState state) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						changeStateTo(state);
					}
				});
				return;
			}

			sessionState = state;

			switch (state) {
			case OFFLINE:
				updateLoginStatus();

				roomPlayerMap.clear();
				widgets.roomPlayerListTable.refresh();

				widgets.formEditSubmitButton.setEnabled(false);
				widgets.formEditLoadButton.setEnabled(true);
				setEnableRoomFormItems(true);
				setEnableMacFilteringControls(true);

				widgets.formMasterNameText.setText("");
				widgets.formTimestampText.setText("");

				widgets.formModeSelectionCombo.setEnabled(true);

				widgets.formAutoModeServerAddress.setText("");
				widgets.formAutoModeRoomButton.setText("Create a room");
				widgets.formAutoModeRoomButton.setEnabled(true);
				widgets.formAutoModeContainer.layout();

				widgets.formManualModeRoomServerCombo.setEnabled(true);
				widgets.formManualModeRoomServerButton.setText("Room creation");
				widgets.formManualModeRoomServerButton.setEnabled(true);
				widgets.formManualModeRoomAddressCombo.setEnabled(true);
				widgets.formManualModeRoomAddressButton.setText("Enter the room");
				widgets.formManualModeRoomAddressButton.setEnabled(true);
				// window.roomFormManualModeContainer.layout();

				widgets.formMyRoomModePortSpinner.setEnabled(true);
				widgets.formMyRoomModeStartButton.setText("to start");
				widgets.formMyRoomModeStartButton.setEnabled(true);
				widgets.formMyRoomModeEntryButton.setEnabled(false);

				switch (widgets.formModeSelectionCombo.getSelectionIndex()) {
				case 0:
					break;
				case 1:
					break;
				case 2:
					updateTunnelStatus(false);
					break;
				}
				changeLobbyStateTo(LobbyUserState.LOGIN);
				break;
			case MY_ROOM_MASTER:
				widgets.formMyRoomModeStartButton.setText("Stop");
				widgets.formMyRoomModeStartButton.setEnabled(true);

				widgets.formMyRoomModeEntryButton.setEnabled(true);

				updateTunnelStatus(true);

				if (widgets.macFilteringWhiteList.getItemCount() > 0) {
					for (String mac : widgets.macFilteringWhiteList.getItems()) {
						myRoomEngine.addMacAddressToWhiteList(mac);
					}
				}
				if (widgets.macFilteringBlackList.getItemCount() > 0) {
					for (String mac : widgets.macFilteringBlackList.getItems()) {
						myRoomEngine.addMacAddressToBlackList(mac);
					}
				}

				widgets.chatText.setFocus();
				break;
			case CONNECTING_ROOM_MASTER:
				widgets.formModeSelectionCombo.setEnabled(false);

				widgets.formManualModeRoomServerButton.setEnabled(false);
				widgets.formManualModeRoomServerCombo.setEnabled(false);
				widgets.formManualModeRoomAddressButton.setEnabled(false);
				widgets.formManualModeRoomAddressCombo.setEnabled(false);

				break;
			case ROOM_MASTER:
				switch (widgets.formModeSelectionCombo.getSelectionIndex()) {
				case 0:
					widgets.formAutoModeRoomButton.setText("Logout");
					widgets.formAutoModeRoomButton.setEnabled(true);
					break;
				case 1:
					widgets.formManualModeRoomServerButton.setText("Logout");
					widgets.formManualModeRoomServerButton.setEnabled(true);
					widgets.formManualModeRoomAddressButton.setEnabled(false);
					// window.roomFormManualModeContainer.layout();
					break;
				}

				widgets.formEditLoadButton.setEnabled(true);
				setEnableRoomFormItems(true);
				setEnableMacFilteringControls(true);

				if (widgets.macFilteringWhiteList.getItemCount() > 0) {
					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.Room.COMMAND_WHITELIST_ADD);
					for (String mac : widgets.macFilteringWhiteList.getItems()) {
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(mac);
					}
					roomConnection.send(Utility.encode(sb));
				}
				if (widgets.macFilteringBlackList.getItemCount() > 0) {
					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.Room.COMMAND_BLACKLIST_ADD);
					for (String mac : widgets.macFilteringBlackList.getItems()) {
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(mac);
					}
					roomConnection.send(Utility.encode(sb));
				}

				widgets.chatText.setFocus();
				break;
			case CONNECTING_ROOM_PARTICIPANT:
				widgets.formModeSelectionCombo.setEnabled(false);

				widgets.formManualModeRoomServerButton.setEnabled(false);
				widgets.formManualModeRoomServerCombo.setEnabled(false);
				widgets.formManualModeRoomAddressButton.setEnabled(false);
				widgets.formManualModeRoomAddressCombo.setEnabled(false);

				break;
			case ROOM_PARTICIPANT:
				switch (widgets.formModeSelectionCombo.getSelectionIndex()) {
				case 0:
					widgets.formAutoModeRoomButton.setText("Leave the room");
					widgets.formAutoModeRoomButton.setEnabled(true);
					break;
				case 1:
					widgets.formManualModeRoomAddressButton.setText("Leave the room");
					widgets.formManualModeRoomAddressButton.setEnabled(true);
					widgets.formManualModeRoomServerButton.setEnabled(false);
					// window.roomFormManualModeContainer.layout();
					break;
				}

				widgets.formEditLoadButton.setEnabled(false);
				setEnableRoomFormItems(false);
				setEnableMacFilteringControls(false);

				widgets.chatText.setFocus();
				break;
			}
		} catch (SWTException e) {
		}
	}

	private void updateRoomModeSelection() {
		switch (widgets.formModeSelectionCombo.getSelectionIndex()) {
		case 0:
			widgets.roomModeStackLayout.topControl = widgets.formAutoModeContainer;
			widgets.formMaxPlayersSpiner.setMaximum(ProtocolConstants.Room.MAX_ROOM_PLAYERS);
			break;
		case 1:
			widgets.roomModeStackLayout.topControl = widgets.formManualModeContainer;
			widgets.formMaxPlayersSpiner.setMaximum(ProtocolConstants.Room.MAX_ROOM_PLAYERS);
			break;
		case 2:
			widgets.roomModeStackLayout.topControl = widgets.formMyRoomModeContainer;
			widgets.formMaxPlayersSpiner.setMaximum(Integer.MAX_VALUE);
			break;
		}
		widgets.formModeSwitchContainer.layout();
	}

	private void setEnableRoomFormItems(boolean enabled) {
		widgets.formTitleText.setEditable(enabled);
		widgets.formPasswordText.setEditable(enabled);
		widgets.formMaxPlayersSpiner.setEnabled(enabled);
		widgets.formDescriptionText.setEditable(enabled);
		widgets.formRemarksText.setEditable(enabled);
	}

	private void clearRoomForm() {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						clearRoomForm();
					}
				});
				return;
			}

			widgets.formTitleText.setText("");
			widgets.formPasswordText.setText("");
			widgets.formMaxPlayersSpiner.setSelection(DEFAULT_MAX_PLAYERS);
			widgets.formDescriptionText.setText("");
			widgets.formRemarksText.setText("");
		} catch (SWTException e) {

		}
	}

	private void setEnableMacFilteringControls(boolean enabled) {
		widgets.macFilteringWhiteListCheck.setEnabled(enabled);
		widgets.macFilteringWhiteList.setEnabled(enabled);
		widgets.macFilteringBlackListCheck.setEnabled(enabled);
		widgets.macFilteringBlackList.setEnabled(enabled);

		if (enabled) {
			widgets.macFilteringWhiteListCheck.setSelection(false);
			widgets.macFilteringBlackListCheck.setSelection(false);
		}
	}

	private void sendMyRoomUpdate() {
		if (!myRoomEntryConnection.isConnected()) {
			return;
		}
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						sendMyRoomUpdate();
					}
				});
				return;
			}
			StringBuilder sb = new StringBuilder();

			sb.append(ProtocolConstants.MyRoom.COMMAND_UPDATE);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(widgets.formTitleText.getText());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(widgets.formMaxPlayersSpiner.getSelection());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(widgets.formPasswordText.getText().length() > 0 ? "Y" : "N");
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(widgets.formDescriptionText.getText());

			myRoomEntryConnection.send(Utility.encode(sb));
		} catch (SWTException e) {
		}
	}

	private void sendMyRoomPlayerCountChange() {
		if (myRoomEntryConnection.isConnected()) {
			ByteBuffer buf = Utility.encode(ProtocolConstants.MyRoom.COMMAND_UPDATE_PLAYER_COUNT + TextProtocolDriver.ARGUMENT_SEPARATOR
					+ roomPlayerMap.size());
			myRoomEntryConnection.send(buf);
		}
	}

	private class MyRoomEntryProtocol implements IProtocol {
		@Override
		public void log(String message) {
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_MY_ROOM_ENTRY;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			myRoomEntryConnection = connection;
			init(connection);

			return new MyRoomEntryProtocolDriver(connection);
		}

		private void init(ISocketConnection connection) {
			StringBuilder sb = new StringBuilder();

			sb.append(ProtocolConstants.MyRoom.COMMAND_ENTRY);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(myRoomEngine.getAuthCode());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(roomServerAddressPort);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(roomLoginName);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(myRoomEngine.getTitle());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(roomPlayerMap.size());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(myRoomEngine.getMaxPlayers());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(Utility.isEmpty(myRoomEngine.getPassword()) ? "N" : "Y");
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(myRoomEngine.getCreatedTime());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(myRoomEngine.getDescription());

			connection.send(Utility.encode(sb));
		}
	}

	private class MyRoomEntryProtocolDriver extends TextProtocolDriver {
		private boolean isEntryCompleted = false;

		public MyRoomEntryProtocolDriver(ISocketConnection connection) {
			super(connection, myRoomEntryHandlers);
		}

		@Override
		public void connectionDisconnected() {
			try {
				if (SwtUtils.isNotUIThread()) {
					SwtUtils.DISPLAY.asyncExec(new Runnable() {
						@Override
						public void run() {
							connectionDisconnected();
						}
					});
					return;
				}

				updateMyRoomEntryForm(false);

				if (isEntryCompleted) {
					isEntryCompleted = false;
					roomServerHistoryManager.addCurrentItem();

					RoomLog log = new RoomLog("My room has been unregistered");
					widgets.logViewer.appendMessage(log);
				} else {
					ErrorLog log = new ErrorLog("My room could not be registered");
					widgets.logViewer.appendMessage(log);
				}
			} catch (SWTException e) {
			}
		}

		@Override
		public void log(String message) {
			ErrorLog log = new ErrorLog(message);
			widgets.logViewer.appendMessage(log);
		}

		@Override
		public void errorProtocolNumber(String number) {
			String message = String.format("Cannot connect because the protocol number does not match the server Server:%s Client:%s", number, IProtocol.NUMBER);
			ErrorLog log = new ErrorLog(message);
			widgets.logViewer.appendMessage(log);
		}
	}

	private HashMap<String, IProtocolMessageHandler> myRoomEntryHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		myRoomEntryHandlers.put(ProtocolConstants.MyRoom.COMMAND_ENTRY, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				updateMyRoomEntryForm(true);

				MyRoomEntryProtocolDriver myroom = (MyRoomEntryProtocolDriver) driver;
				myroom.isEntryCompleted = true;

				RoomLog log = new RoomLog("I registered my room");
				widgets.logViewer.appendMessage(log);
				return true;
			}
		});
		myRoomEntryHandlers.put(ProtocolConstants.MyRoom.ERROR_TCP_PORT_NOT_OPEN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				ErrorLog log = new ErrorLog("My room TCP port is not open");
				widgets.logViewer.appendMessage(log);
				return false;
			}
		});
		myRoomEntryHandlers.put(ProtocolConstants.MyRoom.ERROR_UDP_PORT_NOT_OPEN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				ErrorLog log = new ErrorLog("My room's UDP port is not open");
				widgets.logViewer.appendMessage(log);
				return false;
			}
		});
		myRoomEntryHandlers.put(ProtocolConstants.MyRoom.ERROR_INVALID_AUTH_CODE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				ErrorLog log = new ErrorLog("Registration other than My Room is only possible");
				widgets.logViewer.appendMessage(log);
				return false;
			}
		});
	}

	private class MyRoomServerHandler implements IMyRoomMasterHandler {
		@Override
		public void log(String message) {
			application.getArenaWindow().appendToSystemLog(message, true);
		}

		@Override
		public void chatReceived(String player, String message) {
			processChat(player, message);
		}

		@Override
		public void playerEntered(String player) {
			addRoomPlayer(player);
		}

		@Override
		public void playerExited(String player) {
			removeExitingRoomPlayer(player);
		}

		@Override
		public void pingInformed(String player, int ping) {
			updateRoomPlayerPing(player, ping);
		}

		@Override
		public void ssidInformed(String player, String ssid) {
			updateRoomPlayerSSID(player, ssid);
		}

		@Override
		public void tunnelPacketReceived(ByteBuffer packet, String playerName) {
			processRemotePspPacket(packet, playerName);
		}

		@Override
		public void roomOpened() {
			try {
				if (SwtUtils.isNotUIThread()) {
					SwtUtils.DISPLAY.asyncExec(new Runnable() {
						@Override
						public void run() {
							roomOpened();
						}
					});
					return;
				}

				changeStateTo(SessionState.MY_ROOM_MASTER);
				updateLoginStatus();
				changeLobbyStateTo(LobbyUserState.PLAYING);

				RoomLog log = new RoomLog("I started my room");
				widgets.logViewer.appendMessage(log);

				addRoomPlayer(roomLoginName);
				widgets.formTimestampText.setText(PlayRoomUtils.DATE_FORMAT.format(new Date(myRoomEngine.getCreatedTime())));

				String ssid = widgets.ssidCurrentSsidText.getText();
				updateRoomPlayerSSID(roomLoginName, ssid);
				myRoomEngine.informSSID(ssid);
			} catch (SWTException e) {
			}
		}

		@Override
		public void roomClosed() {
			try {
				if (SwtUtils.isNotUIThread()) {
					SwtUtils.DISPLAY.asyncExec(new Runnable() {
						@Override
						public void run() {
							roomClosed();
						}
					});
					return;
				}
				if (myRoomEntryConnection.isConnected())
					myRoomEntryConnection.disconnect();
				changeStateTo(SessionState.OFFLINE);

				RoomLog log = new RoomLog("My room has stopped");
				widgets.logViewer.appendMessage(log);
			} catch (SWTException e) {
			}
		}
	}

	private class RoomProtocol implements IProtocol {
		@Override
		public void log(String message) {
			application.getArenaWindow().appendToSystemLog(message, true);
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_ROOM;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			roomConnection = connection;
			init(connection);

			return new RoomProtocolDriver(connection);
		}

		private void init(final ISocketConnection connection) {
			try {
				if (SwtUtils.isNotUIThread()) {
					SwtUtils.DISPLAY.asyncExec(new Runnable() {
						@Override
						public void run() {
							init(connection);
						}
					});
					return;
				}

				widgets.formAutoModeServerAddress.setText(roomServerAddressPort);

				StringBuilder sb = new StringBuilder();
				switch (sessionState) {
				case CONNECTING_ROOM_MASTER: {
					ServerLog log = new ServerLog("Connected to the server");
					widgets.logViewer.appendMessage(log);

					sb.append(ProtocolConstants.Room.COMMAND_ROOM_CREATE);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(roomLoginName);
					appendRoomInfo(sb);

					roomConnection.send(Utility.encode(sb));

					widgets.formManualModeRoomServerCombo.setText(roomServerAddressPort);
					sessionState = SessionState.NEGOTIATING;
					break;
				}
				case CONNECTING_ROOM_PARTICIPANT: {
					ServerLog log = new ServerLog("Connected to the server");
					widgets.logViewer.appendMessage(log);

					sb.append(ProtocolConstants.Room.COMMAND_LOGIN);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(roomLoginName);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(roomMasterName);

					sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
					sb.append(ProtocolConstants.Room.COMMAND_INFORM_SSID);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(widgets.ssidCurrentSsidText.getText());

					roomConnection.send(Utility.encode(sb));

					widgets.formManualModeRoomAddressCombo.setText(roomServerAddressPort + ":" + roomMasterName);
					sessionState = SessionState.NEGOTIATING;
					break;
				}
				}
			} catch (SWTException e) {
			}
		}
	}

	private class RoomProtocolDriver extends TextProtocolDriver {

		public RoomProtocolDriver(ISocketConnection connection) {
			super(connection, roomHandlers);
		}

		@Override
		public void connectionDisconnected() {
			switch (sessionState) {
			case CONNECTING_ROOM_PARTICIPANT:
			case CONNECTING_ROOM_MASTER: {
				ErrorLog log = new ErrorLog("Unable to connect to the server");
				widgets.logViewer.appendMessage(log);
				break;
			}
			case ROOM_PARTICIPANT:
				clearRoomForm();
			default:
				ServerLog log = new ServerLog("Disconnected from the server");
				widgets.logViewer.appendMessage(log);
			}

			roomConnection = ISocketConnection.NULL;
			tunnelConnection.disconnect();
			changeStateTo(SessionState.OFFLINE);
		}

		@Override
		public void log(String message) {
			application.getArenaWindow().appendToSystemLog(message, true);
		}

		@Override
		public void errorProtocolNumber(String number) {
			String message = String.format("Cannot connect because the protocol number does not match the server. Server:%s Client:%s", number, IProtocol.NUMBER);
			ErrorLog log = new ErrorLog(message);
			widgets.logViewer.appendMessage(log);
		}
	}

	private void promptRoomPassword() {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						promptRoomPassword();
					}
				});
				return;
			}

			TextDialog dialog = new TextDialog(shell, "Please enter your password", "The password is set in the room", null, 180, SWT.NONE);
			switch (dialog.open()) {
			case IDialogConstants.OK_ID:
				String password = dialog.getUserInput();

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.COMMAND_LOGIN);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(roomLoginName);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(roomMasterName);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(password);

				roomConnection.send(Utility.encode(sb));
				break;
			case IDialogConstants.CANCEL_ID:
				RoomLog log = new RoomLog("I canceled my entry");
				widgets.logViewer.appendMessage(log);
				roomConnection.disconnect();
				break;
			}
		} catch (SWTException e) {
		}
	}

	private HashMap<String, IProtocolMessageHandler> roomHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		roomHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_CREATE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, final String argument) {
				try {
					if (SwtUtils.isNotUIThread()) {
						SwtUtils.DISPLAY.asyncExec(new Runnable() {
							@Override
							public void run() {
								process(null, argument);
							}
						});

						connectRoomTunnel();
						return true;
					}

					long created = Long.parseLong(argument);
					widgets.formTimestampText.setText(PlayRoomUtils.DATE_FORMAT.format(new Date(created)));

					changeStateTo(SessionState.ROOM_MASTER);

					RoomLog log = new RoomLog("I created a room on the room server");
					widgets.logViewer.appendMessage(log);

					widgets.formMasterNameText.setText(roomLoginName);
					addRoomPlayer(roomLoginName);
					updateLoginStatus();
					changeLobbyStateTo(LobbyUserState.PLAYING);

					roomServerHistoryManager.addCurrentItem();
				} catch (NumberFormatException e) {
					return false;
				} catch (SWTException e) {
				}
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.COMMAND_LOGIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, final String args) {
				try {
					if (SwtUtils.isNotUIThread()) {
						SwtUtils.DISPLAY.asyncExec(new Runnable() {
							@Override
							public void run() {
								process(null, args);
							}
						});

						connectRoomTunnel();
						return true;
					}

					changeStateTo(SessionState.ROOM_PARTICIPANT);

					updateRoom(args.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1), true);
					updateLoginStatus();
					changeLobbyStateTo(LobbyUserState.PLAYING);

					roomAddressHistoryManager.addCurrentItem();

					RoomLog log = new RoomLog("I entered the room");
					widgets.logViewer.appendMessage(log);
				} catch (SWTException e) {
				}
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.COMMAND_CHAT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, 2);
				if (tokens.length != 2)
					return true;

				processChat(tokens[0], tokens[1]);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.COMMAND_PINGBACK, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				try {
					long time = Long.parseLong(argument);
					int ping = (int) (System.currentTimeMillis() - time);

					updateRoomPlayerPing(roomLoginName, ping);

					ByteBuffer buf = Utility.encode(ProtocolConstants.Room.COMMAND_INFORM_PING + TextProtocolDriver.ARGUMENT_SEPARATOR
							+ ping);
					roomConnection.send(buf);
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_PING, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String args) {
				try {
					switch (sessionState) {
					case MY_ROOM_MASTER:
					case ROOM_PARTICIPANT:
					case ROOM_MASTER:
						String[] values = args.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
						if (values.length == 2) {
							int ping = Integer.parseInt(values[1]);
							updateRoomPlayerPing(values[0], ping);
						}
					}
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_PORT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				updateTunnelStatus(true);
				application.getArenaWindow().appendToSystemLog("Tunnel communication connection has started", true);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR);
				if (tokens.length != 2)
					return true;

				TraficStatistics stats = traficStatsMap.get(tokens[0]);
				if (stats == null)
					return true;

				stats.playerName = tokens[1];
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_UPDATE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				try {
					if (SwtUtils.isNotUIThread()) {
						SwtUtils.DISPLAY.asyncExec(new Runnable() {
							@Override
							public void run() {
								process(null, null);
							}
						});
						return true;
					}

					RoomLog log = new RoomLog("Corrected room information");
					widgets.logViewer.appendMessage(log);

					widgets.chatText.setFocus();
				} catch (SWTException e) {
				}
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.NOTIFY_USER_LIST, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String args) {
				// System.out.println(args);
				String[] playerInfoList = args.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				replaceRoomPlayerList(playerInfoList);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.NOTIFY_USER_ENTERED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String name) {
				addRoomPlayer(name);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.NOTIFY_USER_EXITED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String name) {
				removeExitingRoomPlayer(name);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String args) {
				updateRoom(args.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1), false);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String kickedPlayer) {
				if (roomLoginName.equals(kickedPlayer)) {
					changeStateTo(SessionState.OFFLINE);

					RoomLog log = new RoomLog("I was kicked out of the room");
					widgets.logViewer.appendMessage(log);
				} else {
					removeKickedRoomPlayer(kickedPlayer);
				}
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.NOTIFY_ROOM_PASSWORD_REQUIRED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				promptRoomPassword();
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.NOTIFY_FROM_ADMIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String message) {
				processAdminNotify(message);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.NOTIFY_ROOM_DELETED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				RoomLog log = new RoomLog("The room has been deleted");
				widgets.logViewer.appendMessage(log);
				if (!isActiveWindow && application.getSettings().isBallonNotifyRoom())
					application.balloonNotify(shell, log.getMessage());
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.NOTIFY_SSID_CHANGED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				String[] values = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (values.length == 2) {
					updateRoomPlayerSSID(values[0], values[1]);
				}
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.NOTIFY_ROOM_AGE_OLD, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				processAdminNotify("It's been a long time since the room was created. It will be automatically disbanded after 15 minutes.");
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.NOTIFY_TUNNEL_COMMUNICATION_IDLE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				processAdminNotify("There is no communication. You will automatically leave after 15 minutes.");
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				ErrorLog log = new ErrorLog("I can't log in because a user with the same name is already logged in");
				widgets.logViewer.appendMessage(log);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.ERROR_LOGIN_ROOM_NOT_EXIST, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				ErrorLog log = new ErrorLog("The room you are trying to log in to does not exist");
				widgets.logViewer.appendMessage(log);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.ERROR_LOGIN_BEYOND_CAPACITY, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				ErrorLog log = new ErrorLog("I can't enter because the room is full");
				widgets.logViewer.appendMessage(log);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				ErrorLog log = new ErrorLog("There is an invalid value in the room information");
				widgets.logViewer.appendMessage(log);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.ERROR_LOGIN_PASSWORD_FAIL, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				RoomLog log = new RoomLog("The room password is wrong");
				widgets.logViewer.appendMessage(log);
				promptRoomPassword();
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.ERROR_ROOM_CREATE_DUPLICATED_NAME, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				ErrorLog log = new ErrorLog("The room has already been created by the user with the same name, so it cannot be created.");
				widgets.logViewer.appendMessage(log);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.ERROR_ROOM_CREATE_BEYOND_LIMIT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				RoomLog log = new RoomLog("The number of rooms has reached the upper limit, so you cannot create a room.");
				widgets.logViewer.appendMessage(log);
				return true;
			}
		});
		roomHandlers.put(ProtocolConstants.Room.ERROR_ROOM_TRANSFER_DUPLICATED_NAME, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver client, String argument) {
				ErrorLog log = new ErrorLog("\r\n"
						+ "Cannot be delegated because the room has already been created by the user with the same name");
				widgets.logViewer.appendMessage(log);
				return true;
			}
		});
	}

	private void connectRoomTunnel() {
		try {
			switch (application.getSettings().getTunnelTransportLayer()) {
			case TCP:
				application.connectTcp(roomConnection.getRemoteAddress(), tunnelProtocol);
				break;
			case UDP:
				application.connectUdp(roomConnection.getRemoteAddress(), tunnelProtocol);
				break;
			}
		} catch (IOException e) {
			ErrorLog log = new ErrorLog(e);
			widgets.logViewer.appendMessage(log);
			application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
		} catch (RuntimeException e) {
			ErrorLog log = new ErrorLog(e);
			widgets.logViewer.appendMessage(log);
			application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
		}
	}

	private class TunnelProtocol implements IProtocol {
		@Override
		public void log(String message) {
			application.getArenaWindow().appendToSystemLog(message, true);
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_TUNNEL;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			tunnelConnection = connection;

			TunnelProtocolDriver driver = new TunnelProtocolDriver(connection);
			return driver;
		}
	}

	private class TunnelProtocolDriver implements IProtocolDriver {
		private ISocketConnection connection;
		private int localPort = 0;

		private TunnelProtocolDriver(ISocketConnection conn) {
			this.connection = conn;

			application.execute(new Runnable() {
				@Override
				public void run() {
					try {
						while (localPort == 0) {
							connection.send(Utility.encode(ProtocolConstants.Tunnel.DUMMY_PACKET));
							Thread.sleep(10000);
						}
					} catch (InterruptedException e) {
					}
				}
			});
		}

		@Override
		public ISocketConnection getConnection() {
			return connection;
		}

		@Override
		public boolean process(PacketData data) {
			ByteBuffer packet = data.getBuffer();
			// System.out.println(packet.toString());
			if (Utility.isPspPacket(packet)) {
				processRemotePspPacket(packet, null);
			} else {
				try {
					String port = data.getMessage();
					localPort = Integer.parseInt(port);

					ByteBuffer buf = Utility.encode(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_PORT
							+ TextProtocolDriver.ARGUMENT_SEPARATOR + localPort);
					roomConnection.send(buf);
				} catch (NumberFormatException e) {
				}
			}
			return true;
		}

		@Override
		public void connectionDisconnected() {
			updateTunnelStatus(false);
			tunnelConnection = ISocketConnection.NULL;
			application.getArenaWindow().appendToSystemLog("The tunnel communication connection has ended", true);

			if (roomConnection.isConnected())
				switch (sessionState) {
				case ROOM_MASTER:
				case ROOM_PARTICIPANT:
					connectRoomTunnel();
				}
		}

		@Override
		public void errorProtocolNumber(String number) {
			String message = String.format("\r\n"
					+ "Cannot connect because the protocol number does not match the server. Server:%s Client:%s", number, IProtocol.NUMBER);
			application.getArenaWindow().appendToSystemLog(message, true);
		}
	}

	private void refreshLanAdapterList() {
		widgets.wlanAdapterListCombo.removeAll();
		if (!currentWlanLibrary.isReady()) {
			widgets.wlanPspCommunicationButton.setEnabled(false);
			widgets.wlanAdapterListCombo.add("Error: Review SSID feature settings or installation");
			widgets.wlanAdapterListCombo.select(0);
			widgets.wlanAdapterListCombo.setEnabled(false);
			return;
		}

		widgets.wlanAdapterListCombo.setEnabled(true);
		widgets.wlanAdapterListCombo.add("Not selected");
		try {
			wlanAdapterList.clear();
			currentWlanLibrary.findDevices(wlanAdapterList);
		} catch (RuntimeException e) {
			application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
			return;
		} catch (UnsatisfiedLinkError e) {
			application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
			return;
		}

		String lastUsedMacAddress = application.getAppData().getLastLanAdapter();
		int lastUsedIndex = 0;

		IniSection nicSection = application.getIniSection(SECTION_LAN_ADAPTERS);

		int maxNameLength = 15;
		int i = 1;
		for (Iterator<WlanDevice> iter = wlanAdapterList.iterator(); iter.hasNext(); i++) {
			WlanDevice device = iter.next();
			byte[] mac = device.getHardwareAddress();

			String name;
			if (mac != null) {
				String macAddress = Utility.macAddressToString(mac, 0, true);
				if (lastUsedMacAddress.equals(macAddress)) {
					lastUsedIndex = i;
				}

				name = nicSection.get(macAddress, "");

				if (Utility.isEmpty(name)) {
					name = device.getName();
					name = name.replace("(Microsoft's Packet Scheduler)", "");
					name = name.replace(" - Packet scheduler miniport", "");
					name = name.replaceAll(" {2,}", " ").trim();

					nicSection.set(macAddress, name);
				} else if (name.equals("")) {
					iter.remove();
					continue;
				}

				name += " [" + macAddress + "]";
				widgets.wlanAdapterListCombo.add(name);

				wlanAdapterMacAddressMap.put(device, macAddress);
			} else {
				name = device.getName();
				widgets.wlanAdapterListCombo.add(name);
			}

			maxNameLength = Math.max(name.length(), maxNameLength);
		}

		StringBuilder sb = new StringBuilder(maxNameLength);
		for (i = 0; i < maxNameLength; i++)
			sb.append('-');

		widgets.wlanAdapterListCombo.add(sb.toString());
		widgets.wlanAdapterListCombo.add("Reload the adapter list");

		widgets.wlanAdapterListCombo.select(lastUsedIndex);
		widgets.wlanPspCommunicationButton.setEnabled(lastUsedIndex != 0);
	}

	private void processRemotePspPacket(ByteBuffer packet, String playerName) {
		String destMac = Utility.macAddressToString(packet, 0, false);
		String srcMac = Utility.macAddressToString(packet, 6, false);

		// System.out.print("[" + playerName + "] ");
		// System.out.print("src: " + srcMac + " dest: " + destMac);
		// System.out.println();

		TraficStatistics destStats, srcStats;
		synchronized (traficStatsMap) {
			destStats = traficStatsMap.get(destMac);
			srcStats = traficStatsMap.get(srcMac);

			if (srcStats == null) {
				srcStats = new TraficStatistics(srcMac, false);
				traficStatsMap.put(srcMac, srcStats);
			}

			if (destStats == null) {
				destStats = new TraficStatistics(destMac, !Utility.isMacBroadCastAddress(destMac));
				traficStatsMap.put(destMac, destStats);
			}
		}

		int packetLength = packet.limit();

		srcStats.lastModified = destStats.lastModified = System.currentTimeMillis();

		if (!Utility.isEmpty(playerName)) {
			srcStats.playerName = playerName;
		} else if (Utility.isEmpty(srcStats.playerName) && !Utility.isMacBroadCastAddress(srcMac)) {
			ByteBuffer buf = Utility.encode(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER + TextProtocolDriver.ARGUMENT_SEPARATOR
					+ srcMac);
			roomConnection.send(buf);
		}
		if (destStats.isMine)
			destStats.playerName = roomLoginName;

		actualRecievedBytes += packetLength;

		srcStats.currentInBytes += packetLength;
		srcStats.totalInBytes += packetLength;
		destStats.currentInBytes += packetLength;
		destStats.totalInBytes += packetLength;

		if (isPacketCapturing && currentWlanDevice != null) {
			currentWlanDevice.sendPacket(packet);
		}
	}

	private void processCapturedPacket() {
		// System.out.println(bufferForCapturing);
		if (!Utility.isPspPacket(bufferForCapturing))
			return;

		String srcMac = Utility.macAddressToString(bufferForCapturing, 6, false);
		String destMac = Utility.macAddressToString(bufferForCapturing, 0, false);

		TraficStatistics srcStats, destStats;
		synchronized (traficStatsMap) {
			srcStats = traficStatsMap.get(srcMac);
			destStats = traficStatsMap.get(destMac);

			if (srcStats == null) {
				srcStats = new TraficStatistics(srcMac, true);
				traficStatsMap.put(srcMac, srcStats);
			} else if (!srcStats.isMine) {
				// Through because it is a recapture of packets sent from other PSPs sent from the server
				return;
			}

			if (destStats == null) {
				destStats = new TraficStatistics(destMac, false);
				traficStatsMap.put(destMac, destStats);

			} else if (destStats.isMine) {
				// Through communication between PSPs at hand
				return;
			}
		}

		int packetLength = bufferForCapturing.limit();

		srcStats.lastModified = destStats.lastModified = System.currentTimeMillis();
		srcStats.playerName = roomLoginName;

		if (Utility.isEmpty(destStats.playerName) && !Utility.isMacBroadCastAddress(destMac)) {
			ByteBuffer buf = Utility.encode(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER + TextProtocolDriver.ARGUMENT_SEPARATOR
					+ destMac);
			roomConnection.send(buf);
		}
		actualSentBytes += packetLength;

		srcStats.currentOutBytes += packetLength;
		srcStats.totalOutBytes += packetLength;
		destStats.currentOutBytes += packetLength;
		destStats.totalOutBytes += packetLength;

		if (!tunnelIsLinked)
			return;
		// System.out.printf("%s => %s  [%d]", srcMac, destMac,
		// packetLength);
		// System.out.println(packet.toHexdump());

		switch (sessionState) {
		case MY_ROOM_MASTER:
			myRoomEngine.sendTunnelPacketToParticipants(bufferForCapturing, srcMac, destMac);
			break;
		case ROOM_PARTICIPANT:
		case ROOM_MASTER:
			tunnelConnection.send(bufferForCapturing);
			break;
		}
	}

	private boolean startPacketCapturing() {
		try {
			int index = widgets.wlanAdapterListCombo.getSelectionIndex() - 1;
			WlanDevice device = wlanAdapterList.get(index);

			device.open();

			currentWlanDevice = device;

			checkSsidChange();

			isPacketCapturing = true;
			wakeupThread(packetCaptureThread);
			wakeupThread(packetMonitorThread);

			return true;
		} catch (RuntimeException e) {
			application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
			return false;
		} catch (Exception e) {
			application.getArenaWindow().appendToSystemLog(Utility.stackTraceToString(e), true);
			return false;
		}
	}
}
