package com.pomelo.tv;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Recommandations que les applis publient pour l'écran d'accueil d'Android TV. À appeler hors du fil principal. */
final class TvPrograms {

    private static final int MAX_IMAGE_BYTES = 3_000_000;

    static final class Program {
        String title, description, image, intentUri;
    }

    private TvPrograms() {
    }

    /** Recommandations par appli qui les publie, ou liste vide si elles ne sont pas lisibles. */
    static Map<String, List<Program>> loadAll(Context context) {
        Map<String, List<Program>> out = new HashMap<>();
        String[] columns = {"package_name", "title", "short_description", "poster_art_uri", "thumbnail_uri", "intent_uri"};
        try (Cursor c = context.getContentResolver().query(
                Uri.parse("content://android.media.tv/preview_program"), columns, null, null, null)) {
            while (c != null && c.moveToNext()) {
                Program p = new Program();
                p.title = c.getString(1);
                p.description = c.getString(2);
                p.image = c.getString(3) != null ? c.getString(3) : c.getString(4);
                p.intentUri = c.getString(5);
                if (p.title == null || p.image == null || p.intentUri == null) continue;
                List<Program> list = out.get(c.getString(0));
                if (list == null) {
                    list = new ArrayList<>();
                    out.put(c.getString(0), list);
                }
                list.add(p);
            }
        } catch (RuntimeException ignored) {
            // Sans permission ou selon le modèle de TV : pas de recommandations, simplement.
        }
        return out;
    }

    /** Image réduite au plus près de la taille d'affichage, pour ménager la mémoire de la TV. */
    static Bitmap loadImage(Context context, String uri, int width, int height) {
        try {
            byte[] data = read(context, uri);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            o.inJustDecodeBounds = false;
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= width && o.outHeight / (o.inSampleSize * 2) >= height) {
                o.inSampleSize *= 2;
            }
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private static byte[] read(Context context, String uri) throws Exception {
        InputStream in;
        HttpURLConnection http = null;
        if (uri.startsWith("http")) {
            http = (HttpURLConnection) new URL(uri).openConnection();
            http.setConnectTimeout(5_000);
            http.setReadTimeout(8_000);
            in = http.getInputStream();
        } else {
            in = context.getContentResolver().openInputStream(Uri.parse(uri));
        }
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = stream.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                if (out.size() > MAX_IMAGE_BYTES) throw new IllegalStateException("image trop lourde");
            }
            return out.toByteArray();
        } finally {
            if (http != null) http.disconnect();
        }
    }
}
