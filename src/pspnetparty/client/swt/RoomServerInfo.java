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

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

public class RoomServerInfo {
	private String address;
	private int currentRooms;
	private int maxRooms;

	public RoomServerInfo(String address, int currentRooms, int maxRooms) {
		this.address = address;
		this.currentRooms = currentRooms;
		this.maxRooms = maxRooms;
	}

	public String getAddress() {
		return address;
	}

	public int getCurrentRooms() {
		return currentRooms;
	}

	public int getMaxRooms() {
		return maxRooms;
	}

	public static final ITableLabelProvider LABEL_PROVIDER = new ITableLabelProvider() {
		@Override
		public void removeListener(ILabelProviderListener arg0) {
		}

		@Override
		public boolean isLabelProperty(Object arg0, String arg1) {
			return false;
		}

		@Override
		public void dispose() {
		}

		@Override
		public void addListener(ILabelProviderListener arg0) {
		}

		@Override
		public String getColumnText(Object element, int index) {
			RoomServerInfo info = (RoomServerInfo) element;

			switch (index) {
			case 0:
				return info.address;
			case 1:
				double useRate = ((double) info.currentRooms) / ((double) info.maxRooms);
				return String.format("%.1f%%", useRate * 100);
			}

			return "";
		}

		@Override
		public Image getColumnImage(Object arg0, int arg1) {
			return null;
		}
	};
}
