/*
 * Copyright (C) 2013-2020 Federico Iosue (federico@iosue.it)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.absolute.android.notescentral.widget;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spanned;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService.RemoteViewsFactory;

import com.absolute.android.notescentral.OmniNotes;
import com.absolute.android.notescentral.R;
import com.absolute.android.notescentral.db.DbHelper;
import com.absolute.android.notescentral.utils.BitmapHelper;
import com.absolute.android.notescentral.utils.Constants;
import com.absolute.android.notescentral.utils.ConstantsBase;
import com.absolute.android.notescentral.utils.Navigation;

import com.absolute.android.notescentral.helpers.LogDelegate;
import com.absolute.android.notescentral.models.Attachment;
import com.absolute.android.notescentral.models.Note;
import com.absolute.android.notescentral.utils.TextHelper;
import java.util.List;


public class ListRemoteViewsFactory implements RemoteViewsFactory {

  private static final String SET_BACKGROUND_COLOR = "setBackgroundColor";
  private static boolean showThumbnails = true;
  private static boolean showTimestamps = true;
  private final int WIDTH = 80;
  private final int HEIGHT = 80;
  private OmniNotes app;
  private int appWidgetId;
  private List<Note> notes;
  private int navigation;

  public ListRemoteViewsFactory (Application app, Intent intent) {
    this.app = (OmniNotes) app;
    appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
  }

  static void updateConfiguration (Context mContext, int mAppWidgetId, String sqlCondition,
      boolean thumbnails, boolean timestamps) {
    LogDelegate.d("Widget configuration updated");
    mContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_MULTI_PROCESS).edit()
            .putString(ConstantsBase.PREF_WIDGET_PREFIX + mAppWidgetId, sqlCondition).apply();
    showThumbnails = thumbnails;
    showTimestamps = timestamps;
  }

  @Override
  public void onCreate () {
    LogDelegate.d("Created widget " + appWidgetId);
    String condition = app.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_MULTI_PROCESS)
                          .getString(
                              ConstantsBase.PREF_WIDGET_PREFIX
                                  + appWidgetId, "");
    notes = DbHelper.getInstance().getNotes(condition, true);
  }

  @Override
  public void onDataSetChanged () {
    LogDelegate.d("onDataSetChanged widget " + appWidgetId);
    navigation = Navigation.getNavigation();

    String condition = app.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_MULTI_PROCESS)
                          .getString(
                              ConstantsBase.PREF_WIDGET_PREFIX
                                  + appWidgetId, "");
    notes = DbHelper.getInstance().getNotes(condition, true);
  }

  @Override
  public void onDestroy () {
    app.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_MULTI_PROCESS)
       .edit()
       .remove(ConstantsBase.PREF_WIDGET_PREFIX + appWidgetId)
       .apply();
  }

  @Override
  public int getCount () {
    return notes.size();
  }

  @Override
  public RemoteViews getViewAt (int position) {
    RemoteViews row = new RemoteViews(app.getPackageName(), R.layout.note_layout_widget);

    Note note = notes.get(position);

    Spanned[] titleAndContent = TextHelper.parseTitleAndContent(app, note);

    row.setTextViewText(R.id.note_title, titleAndContent[0]);
    row.setTextViewText(R.id.note_content, titleAndContent[1]);

    color(note, row);

    if (!note.isLocked() && showThumbnails && !note.getAttachmentsList().isEmpty()) {
      Attachment mAttachment = note.getAttachmentsList().get(0);
      Bitmap bmp = BitmapHelper.getBitmapFromAttachment(app, mAttachment, WIDTH, HEIGHT);
      row.setBitmap(R.id.attachmentThumbnail, "setImageBitmap", bmp);
      row.setInt(R.id.attachmentThumbnail, "setVisibility", View.VISIBLE);
    } else {
      row.setInt(R.id.attachmentThumbnail, "setVisibility", View.GONE);
    }
    if (showTimestamps) {
      row.setTextViewText(R.id.note_date, TextHelper.getDateText(app, note, navigation));
    } else {
      row.setTextViewText(R.id.note_date, "");
    }

    // Next, set a fill-intent, which will be used to fill in the pending intent template
    // that is set on the collection view in StackWidgetProvider.
    Bundle extras = new Bundle();
    extras.putParcelable(ConstantsBase.INTENT_NOTE, note);
    Intent fillInIntent = new Intent();
    fillInIntent.putExtras(extras);
    // Make it possible to distinguish the individual on-click
    // action of a given item
    row.setOnClickFillInIntent(R.id.root, fillInIntent);

    return row;
  }

  @Override
  public RemoteViews getLoadingView () {
    return null;
  }

  @Override
  public int getViewTypeCount () {
    return 1;
  }

  @Override
  public long getItemId (int position) {
    return position;
  }

  @Override
  public boolean hasStableIds () {
    return false;
  }

  private void color (Note note, RemoteViews row) {

    String colorsPref = app.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_MULTI_PROCESS)
                           .getString("settings_colors_widget",
                               ConstantsBase.PREF_COLORS_APP_DEFAULT);

    // Checking preference
    if (!colorsPref.equals("disabled")) {

      // Resetting transparent color to the view
      row.setInt(R.id.tag_marker, SET_BACKGROUND_COLOR, Color.parseColor("#00000000"));

      // If tag is set the color will be applied on the appropriate target
      if (note.getCategory() != null && note.getCategory().getColor() != null) {
        if (colorsPref.equals("list")) {
          row.setInt(R.id.card_layout, SET_BACKGROUND_COLOR, Integer.parseInt(note.getCategory().getColor()));
        } else {
          row.setInt(R.id.tag_marker, SET_BACKGROUND_COLOR, Integer.parseInt(note.getCategory().getColor()));
        }
      } else {
        row.setInt(R.id.tag_marker, SET_BACKGROUND_COLOR, 0);
      }
    }
  }

}
