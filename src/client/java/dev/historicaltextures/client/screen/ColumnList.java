package dev.historicaltextures.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ObjectSelectionList;

abstract class ColumnList<E extends ObjectSelectionList.Entry<E>> extends ObjectSelectionList<E> {
	ColumnList(Minecraft minecraft, int width, int height, int top, int entryHeight) {
		super(minecraft, width, height, top, entryHeight);
	}

	@Override
	public int getRowWidth() {
		return width - 14;
	}

	@Override
	protected int scrollBarX() {
		return getX() + width - scrollbarWidth();
	}

	@Override
	public int getRowLeft() {
		return getX() + 2;
	}

	static String fit(Font font, String text, int maxWidth) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		int dotsWidth = font.width("...");
		if (maxWidth <= dotsWidth) {
			return "...";
		}
		return font.plainSubstrByWidth(text, maxWidth - dotsWidth) + "...";
	}
}
