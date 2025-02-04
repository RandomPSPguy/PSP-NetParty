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
package pspnetparty.client.swt.message;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.hyperlink.HyperlinkManager;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlinkPresenter;
import org.eclipse.jface.text.hyperlink.URLHyperlinkDetector;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import pspnetparty.client.swt.PlayClient;
import pspnetparty.client.swt.SwtUtils;
import pspnetparty.client.swt.config.IniAppearance;
import pspnetparty.lib.FixedSizeList;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;

public class LogViewer {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

	private FixedSizeList<IMessage> messageList;

	private PlayClient application;

	private Composite container;
	private SourceViewer sourceViewer;
	private StyledText logWidget;

	private CompositeRuler ruler;
	private TimestampRulerColumn timestampRulerColumn;
	private NameRulerColumn nameRulerColumn;

	private String lineDelimiter;
	private ArrayList<StyleRange> styleRanges = new ArrayList<StyleRange>();

	public LogViewer(Composite parent, int size, PlayClient application) {
		this.application = application;

		messageList = new FixedSizeList<IMessage>(size);

		ruler = new CompositeRuler();

		container = new Composite(parent, SWT.BORDER);
		container.setLayout(new FillLayout());

		sourceViewer = new SourceViewer(container, ruler, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION | SWT.WRAP);
		Document document = new Document();
		sourceViewer.setDocument(document);

		logWidget = sourceViewer.getTextWidget();

		timestampRulerColumn = new TimestampRulerColumn();
		nameRulerColumn = new NameRulerColumn();

		ruler.addDecorator(0, timestampRulerColumn);
		ruler.addDecorator(1, nameRulerColumn);

		logWidget.setEditable(false);
		logWidget.setLineSpacing(1);
		logWidget.setMargins(3, 2, 3, 2);
		application.initLogControl(logWidget);
		logWidget.addControlListener(new ControlListener() {
			@Override
			public void controlResized(ControlEvent e) {
				ruler.update();
			}

			@Override
			public void controlMoved(ControlEvent e) {
			}
		});

		lineDelimiter = logWidget.getLineDelimiter();

		HyperlinkManager manager = new HyperlinkManager(HyperlinkManager.LONGEST_REGION_FIRST);
		IHyperlinkPresenter presenter = new IHyperlinkPresenter() {
			@Override
			public void uninstall() {
			}

			@Override
			public void showHyperlinks(IHyperlink[] hyperlinks) throws IllegalArgumentException {
			}

			@Override
			public void install(ITextViewer textViewer) {
			}

			@Override
			public void hideHyperlinks() {
			}

			@Override
			public boolean canShowMultipleHyperlinks() {
				return false;
			}
		};

		manager.install(sourceViewer, presenter, new IHyperlinkDetector[] { new URLHyperlinkDetector() }, SWT.NONE);
		applyAppearance();

		Menu menu = new Menu(parent.getShell(), SWT.POP_UP);
		logWidget.setMenu(menu);

		MenuItem menuCopyLog = new MenuItem(menu, SWT.PUSH);
		menuCopyLog.setText("Copy log");
		menuCopyLog.addListener(SWT.Selection, new Listener() {
			private Date date = new Date();

			@Override
			public void handleEvent(Event event) {
				StringBuilder sb = new StringBuilder();

				for (int i = 0; i < messageList.size(); i++) {
					IMessage log = messageList.get(i);
					date.setTime(log.getTimestamp());
					sb.append(DATE_FORMAT.format(date));
					if (Utility.isEmpty(log.getName())) {
						sb.append(' ');
					} else {
						sb.append(" <").append(log.getName()).append("> ");
					}
					sb.append(log.getMessage());
					sb.append(AppConstants.NEW_LINE);
				}

				LogViewer.this.application.putClipboard(sb.toString());
			}
		});

		new MenuItem(menu, SWT.SEPARATOR);

		MenuItem menuClearLog = new MenuItem(menu, SWT.PUSH);
		menuClearLog.setText("Clear the log");
		menuClearLog.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				messageList.clear();

				if (!styleRanges.isEmpty()) {
					styleRanges.clear();
					StyleRange[] ranges = new StyleRange[0];
					logWidget.setStyleRanges(ranges);
				}

				logWidget.setText("");
				ruler.update();
			}
		});
	}

	public void applyAppearance() {
		IniAppearance appearance = application.getAppearance();

		timestampRulerColumn.setColor(appearance.getColorLogTimestampRulerBG(), appearance.getColorLogTimestampRulerFG());
		timestampRulerColumn.resizeWidth(appearance.getLogTimestampRulerWidth());

		nameRulerColumn.setColor(appearance.getColorLogNameRulerBG(), appearance.getColorLogNameRulerFG());
		nameRulerColumn.resizeWidth(appearance.getLogNameRulerWidth());

		int offset = 0;
		ArrayList<StyleRange> newStyles = new ArrayList<StyleRange>(messageList.size());
		for (int i = 0; i < messageList.size(); i++) {
			styleRanges.clear();
			IMessage message = messageList.get(i);
			message.configureStyle(styleRanges, appearance);
			for (StyleRange range : styleRanges) {
				range.start += offset;
				newStyles.add(range);
			}
			offset += message.length() + lineDelimiter.length();
		}

		if (!newStyles.isEmpty()) {
			StyleRange[] ranges = newStyles.toArray(new StyleRange[newStyles.size()]);
			logWidget.setStyleRanges(ranges);
		}
	}

	public Control getControl() {
		return container;
	}

	public void appendMessage(final IMessage message) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						appendMessage(message);
					}
				});
				return;
			}

			int lineCount = logWidget.getLineCount();
			int topLineIndex = logWidget.getTopIndex();
			int bottomLineIndex = logWidget.getLineIndex(logWidget.getSize().y);
			boolean scrollLastLine = lineCount - bottomLineIndex < 2;

			IMessage removed = messageList.add(message);
			if (removed != null) {
				int length = removed.length() + lineDelimiter.length();
				logWidget.replaceTextRange(0, length, "");
			}

			int offset = logWidget.getCharCount();
			if (offset > 0)
				logWidget.append(lineDelimiter);
			logWidget.append(message.getMessage());

			styleRanges.clear();
			message.configureStyle(styleRanges, application.getAppearance());
			for (StyleRange range : styleRanges) {
				if (offset > 0)
					range.start += offset + lineDelimiter.length();
				// System.out.println(range);
				logWidget.setStyleRange(range);
			}

			if (scrollLastLine) {
				logWidget.setTopIndex(lineCount);
				ruler.update();
			} else if (removed != null && topLineIndex > 1) {
				logWidget.setTopIndex(topLineIndex - 2);
				ruler.update();
			}
		} catch (SWTException e) {
		} catch (RuntimeException e) {
			e.fillInStackTrace();
		}
	}

	private class TimestampRulerColumn extends BaseRulerColumn {
		private Date date = new Date();

		private TimestampRulerColumn() {
			super(logWidget, 3);
		}

		@Override
		protected String getLabel(int line) {
			if (messageList.size() == 0)
				return null;
			IMessage message = messageList.get(line);
			date.setTime(message.getTimestamp());
			return DATE_FORMAT.format(date);
		}

		void resizeWidth(int width) {
			setWidth(width);
		}
	}

	private class NameRulerColumn extends BaseRulerColumn {

		private NameRulerColumn() {
			super(logWidget, 3);
		}

		@Override
		protected String getLabel(int line) {
			if (messageList.size() == 0)
				return null;
			IMessage message = messageList.get(line);
			return message.getName();
		}

		void resizeWidth(int width) {
			setWidth(width);
		}
	}
}
