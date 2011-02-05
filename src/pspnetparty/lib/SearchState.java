package pspnetparty.lib;

import java.util.HashMap;

public class SearchState implements IClientState {

	private ISocketConnection connection;
	public HashMap<String, IServerMessageHandler<SearchState>> messageHandlers;
	// public String address;
	public PlayRoom entryRoom;

	public SearchState(ISocketConnection connection) {
		this.connection = connection;
	}

	@Override
	public ISocketConnection getConnection() {
		return connection;
	}

}
