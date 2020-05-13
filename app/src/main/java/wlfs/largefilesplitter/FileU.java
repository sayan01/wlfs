package wlfs.largefilesplitter;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

public class FileU {
	private static final String EXTERNAL = Environment.getExternalStorageDirectory().getAbsolutePath();

	static String getTree(@Nullable Uri uri){
		try {
			if (uri == null) return "";
			String path = uri.getPath();
			if (path == null) return "";
			String[] token = path.split(":");
			String device = token[0].substring(token[0].lastIndexOf("/") + 1);

			if (!path.contains(":")) {
				return path.contains("download") ? Environment.
						getExternalStoragePublicDirectory(
								Environment.DIRECTORY_DOWNLOADS)
						.getAbsolutePath()+"/" : "";
			}

			path = (token.length > 1)?token[1]:"";
			for (int i = 2; i < token.length; i++) {
				path += ":" + token[i];
			}

			if (device.contains("primary")) {
				return EXTERNAL + "/" + path + "/" ;
			} else {
				return "/storage/" + device + "/" + path + "/";
			}
		}
		catch (Exception e){
			return "";
		}
	}

	static String getPath(Context context, Uri uri){

		try {
			String path = uri.getPath();
			if (path == null) return "";
			String[] token = path.split(":");
			String device = token[0].substring(token[0].lastIndexOf("/") + 1);

			if (token.length == 1 || device.contains("msf")) {
				return "";
			}

			if (FileU.isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				} else {
					return "";
				}
				final String selection = "_id=?";
				final String[] selectionArgs = new String[]{
						split[1]
				};
				return FileU.getDataColumn(context, contentUri, selection, selectionArgs);
			}

			path = token[1];
			for (int i = 2; i < token.length; i++) {
				path += ":" + token[i];
			}
			if (device.contains("primary")) {
				return EXTERNAL + "/" + path;
			} else {
				return "/storage/" + device + "/" + path;
			}
		}
		catch (Exception e){
			return "";
		}
	}

	static String getDataColumn(Context context, Uri uri, String selection,
								String[] selectionArgs) {

		final String column = "_data";
		final String[] projection = {
				column
		};
		try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
				null)) {
			if (cursor != null && cursor.moveToFirst()) {
				final int index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(index);
			}
		} catch (Exception e) {
			return "";
		}
		return uri.getPath();
	}

	private static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

}