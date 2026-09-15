package jp.sakai.fanza.image;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final int OUTPUT_SIZE = 1200;
    EditText data, url;
    ImageView preview;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(32, 28, 32, 28);
        x.addView(t("④ 大きな公式画像を準備", 24));
        x.addView(t("画像全体が切れない1200×1200画像を作り、写真へ保存します。", 15));
        data = e("作品情報"); data.setMinLines(6); x.addView(data);
        Button r = btn("クリップボード読込"); r.setOnClickListener(v -> load()); x.addView(r);
        url = e("画像URL"); x.addView(url);
        Button d = btn("大きな全体画像を作って保存"); d.setOnClickListener(v -> download()); x.addView(d);
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.rgb(24, 24, 24));
        preview.setVisibility(ImageView.GONE);
        x.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 900));
        Button keep = btn("作品情報を再コピーして②へ"); keep.setOnClickListener(v -> keep()); x.addView(keep);
        ScrollView scroll = new ScrollView(this); scroll.addView(x); setContentView(scroll);
        getWindow().setStatusBarColor(Color.rgb(216, 27, 96));
    }

    TextView t(String s, int z) { TextView v = new TextView(this); v.setText(s); v.setTextSize(z); v.setPadding(0, 10, 0, 10); return v; }
    EditText e(String h) { EditText v = new EditText(this); v.setHint(h); v.setTextSize(16); return v; }
    Button btn(String s) { Button b = new Button(this); b.setText(s); b.setTextSize(16); return b; }

    void load() {
        ClipboardManager m = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (!m.hasPrimaryClip()) { toast("作品情報がありません"); return; }
        String s = m.getPrimaryClip().getItemAt(0).coerceToText(this).toString();
        data.setText(s); url.setText(field(s, "IMAGE:"));
    }

    String field(String s, String k) {
        for (String l : s.split("\n")) if (l.startsWith(k)) return l.substring(k.length()).trim();
        return "";
    }

    void download() {
        String u = url.getText().toString().trim();
        if (!u.startsWith("https://")) { toast("有効な公式画像URLがありません"); return; }
        toast("大きな画像を作成しています");
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0"); c.connect();
                if (c.getResponseCode() / 100 != 2) throw new Exception("画像取得エラー " + c.getResponseCode());
                Bitmap source;
                try (InputStream in = c.getInputStream()) { source = BitmapFactory.decodeStream(in); }
                if (source == null) throw new Exception("画像を読み込めませんでした");
                Bitmap output = fitWholeImage(source);
                saveToPictures(output);
                runOnUiThread(() -> {
                    preview.setImageBitmap(output); preview.setVisibility(ImageView.VISIBLE);
                    toast("保存しました。⑤で最新の画像を選んでください");
                });
                source.recycle();
            } catch (Exception ex) {
                runOnUiThread(() -> toast("保存できません: " + ex.getMessage()));
            } finally { if (c != null) c.disconnect(); }
        }).start();
    }

    Bitmap fitWholeImage(Bitmap source) {
        Bitmap out = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.rgb(24, 24, 24));
        float scale = Math.min((float) OUTPUT_SIZE / source.getWidth(), (float) OUTPUT_SIZE / source.getHeight());
        float w = source.getWidth() * scale, h = source.getHeight() * scale;
        float left = (OUTPUT_SIZE - w) / 2f, top = (OUTPUT_SIZE - h) / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, new android.graphics.RectF(left, top, left + w, top + h), p);
        return out;
    }

    Uri saveToPictures(Bitmap bitmap) throws Exception {
        String name = "fanza_full_" + System.currentTimeMillis() + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FANZA");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("保存先を作れませんでした");
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) throw new Exception("画像保存に失敗しました");
        } catch (Exception e) { getContentResolver().delete(uri, null, null); throw e; }
        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0);
        getContentResolver().update(uri, values, null, null);
        return uri;
    }

    void keep() {
        String s = data.getText().toString();
        if (s.isEmpty()) { toast("作品情報がありません"); return; }
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("FANZA作品情報", s));
        toast("再コピーしました。②を開いてください");
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
