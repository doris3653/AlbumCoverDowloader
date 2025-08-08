package hu.doris.albumartdownloader;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.ID3v24Tag;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MediaFileProcessor {
    private static String searchArtworkUrl(String artist, String album) {
        try {
            String term = java.net.URLEncoder.encode(artist + " " + album, "UTF-8");
            return "https://itunes.apple.com/search?term=" + term + "&entity=album&limit=1";
        } catch (Exception e) {
            return null;
        }
    }

    public static void processAndEmbed(Context ctx, DocumentFile df, OkHttpClient client) throws Exception {
        Uri uri = df.getUri();
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        File tmp = new File(ctx.getCacheDir(), df.getName());
        FileOutputStream fos = new FileOutputStream(tmp);
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
        fos.close();
        in.close();

        Mp3File mp3 = new Mp3File(tmp.getAbsolutePath());
        String artist = null, album = null;
        if (mp3.hasId3v2Tag()) {
            ID3v2 id3v2 = mp3.getId3v2Tag();
            artist = id3v2.getArtist();
            album = id3v2.getAlbum();
        }
        if ((artist == null || artist.isEmpty()) || (album == null || album.isEmpty())) {
            String name = df.getName();
            if (name != null && name.contains(" - ")) {
                String[] parts = name.split(" - ");
                if (artist == null || artist.isEmpty()) artist = parts[0];
                if (album == null || album.isEmpty())
                    album = parts.length > 1 ? parts[1].replaceAll("\\.mp3$", "") : null;
            }
        }
        if (artist == null) artist = "";
        if (album == null) album = "";

        String searchUrl = searchArtworkUrl(artist, album);
        String artworkUrl = null;
        if (searchUrl != null) {
            Request req = new Request.Builder().url(searchUrl).build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful()) {
                    String body = resp.body().string();
                    int idx = body.indexOf("\"artworkUrl100\"");
                    if (idx != -1) {
                        int start = body.indexOf('"', idx + 17) + 1;
                        int end = body.indexOf('"', start);
                        if (start > 0 && end > start)
                            artworkUrl = body.substring(start, end).replaceAll("200x200bb.jpg", "600x600bb.jpg");
                    }
                }
            }
        }

        if (artworkUrl != null) {
            Request req2 = new Request.Builder().url(artworkUrl).build();
            try (Response r2 = client.newCall(req2).execute()) {
                if (r2.isSuccessful()) {
                    ResponseBody rb = r2.body();
                    byte[] img = rb.bytes();
                    ID3v2 newTag = new ID3v24Tag();
                    newTag.setArtist(artist);
                    newTag.setAlbum(album);
                    newTag.setAlbumImage(img, "image/jpeg");
                    mp3.setId3v2Tag(newTag);
                    File out = new File(ctx.getCacheDir(), "out_" + df.getName());
                    mp3.save(out.getAbsolutePath());
                    try (InputStream outIn = new java.io.FileInputStream(out)) {
                        DocumentFile parent = df.getParentFile();
                        df.delete();
                        DocumentFile newFile = parent.createFile("audio/mpeg", df.getName());
                        try (java.io.OutputStream os = ctx.getContentResolver().openOutputStream(newFile.getUri())) {
                            byte[] b = new byte[8192];
                            int len;
                            while ((len = outIn.read(b)) > 0) os.write(b, 0, len);
                        }
                        out.delete();
                        tmp.delete();
                    }
                }
            }
        }
    }
}
