
package jp.sakai.fanza.search;

import android.app.Activity;
import android.os.Bundle;

import android.net.Uri;

import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    LinearLayout root, results;
    EditText apiId, affiliateId, keyword;
    Spinner searchType;
    String[] types = {"女優名", "作品名・キーワード"};

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(24, 24, 24, 24);
        scroll.addView(root);
        setContentView(scroll);

        title("FANZA 作品検索 改良版");

        apiId = field("DMM API ID");
        affiliateId = field("アフィリエイトID");
        keyword = field("女優名・作品名を入力");

        android.content.SharedPreferences pref =
            getSharedPreferences("settings", 0);

        apiId.setText(pref.getString("api", ""));
        affiliateId.setText(pref.getString("aff", ""));

        searchType = new Spinner(this);
        searchType.setAdapter(new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            types
        ));
        root.addView(searchType);

        Button search = button("FANZAで検索");
        search.setOnClickListener(v -> search());

        results = new LinearLayout(this);
        results.setOrientation(1);
        root.addView(results);
    }

    EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        root.addView(e);
        return e;
    }

    void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(22);
        t.setTextColor(Color.BLACK);
        t.setPadding(0, 8, 0, 20);
        root.addView(t);
    }

    Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        root.addView(b);
        return b;
    }

    void search() {
        String api = apiId.getText().toString().trim();
        String aff = affiliateId.getText().toString().trim();
        String word = keyword.getText().toString().trim();

        if (api.isEmpty() || aff.isEmpty() || word.isEmpty()) {
            toast("API ID・アフィリエイトID・検索語を入力");
            return;
        }

        getSharedPreferences("settings", 0).edit()
            .putString("api", api)
            .putString("aff", aff)
            .apply();

        results.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("検索中...");
        results.addView(loading);

        new Thread(() -> {
            try {
                String url =
                    "https://api.dmm.com/affiliate/v3/ItemList" +
                    "?api_id=" + enc(api) +
                    "&affiliate_id=" + enc(aff) +
                    "&site=FANZA" +
                    "&service=digital" +
                    "&floor=videoa" +
                    "&hits=20" +
                    "&output=json" +
                    "&keyword=" + enc(word);

                HttpURLConnection c =
                    (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);

                int status = c.getResponseCode();
                InputStream stream = status >= 400
                    ? c.getErrorStream() : c.getInputStream();

                if (stream == null) {
                    throw new IOException("HTTP " + status);
                }

                ByteArrayOutputStream out =
                    new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = stream.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                stream.close();
                c.disconnect();

                String response = new String(
                    out.toByteArray(), StandardCharsets.UTF_8);

                if (status >= 400) {
                    throw new IOException(
                        "HTTP " + status + ": " +
                        response.substring(0,
                            Math.min(200, response.length())));
                }

                JSONObject json = new JSONObject(response);
                JSONObject result = json.getJSONObject("result");

                if (result.has("status") &&
                    result.optInt("status", 200) != 200) {
                    throw new Exception(
                        result.optString("message", "APIエラー"));
                }

                JSONArray items = result.optJSONArray("items");

                runOnUiThread(() -> {
                    results.removeAllViews();
                    if (items == null || items.length() == 0) {
                        showMessage("作品が見つかりませんでした");
                        return;
                    }

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item != null) showItem(item);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    results.removeAllViews();
                    showMessage("検索エラー: " + e.getMessage());
                });
            }
        }).start();
    }

    void showItem(JSONObject item) {
        String name = item.optString("title", "作品名なし");
        String url = item.optString("affiliateURL",
                     item.optString("URL", ""));

        JSONObject image = item.optJSONObject("imageURL");
        String imageUrl = "";
        if (image != null) {
            imageUrl = image.optString("large",
                       image.optString("list", ""));
        }

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(17);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 22, 0, 10);
        results.addView(title);

        if (!imageUrl.isEmpty()) {
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(500);
            results.addView(iv);
            loadImage(imageUrl, iv);
        }

        Button copy = new Button(this);
        copy.setText("X投稿文をコピー");
        results.addView(copy);

        final String postUrl = url;
        copy.setOnClickListener(v -> {
            String post = "【おすすめ作品】\n" +
                name + "\n\n" +
                "作品の詳細はこちら👇\n" +
                postUrl + "\n\n" +
                "#PR #FANZA";

            ClipboardManager clipboard =
                (ClipboardManager) getSystemService(
                    CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(
                ClipData.newPlainText("X投稿文", post));
            toast("投稿文をコピーしました");
        });

        Button open = new Button(this);
        open.setText("Xで投稿する");
        results.addView(open);
        open.setOnClickListener(v -> {
            String post = "【おすすめ作品】\n" +
                name + "\n\n" +
                "作品の詳細はこちら👇\n" +
                postUrl + "\n\n" +
                "#PR #FANZA";
            try {
                String intentUrl =
                    "https://twitter.com/intent/tweet?text=" +
                    enc(post);
                startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(intentUrl)));
            } catch (Exception e) {
                toast("Xを開けませんでした");
            }
        });
    }

    void loadImage(String url, ImageView view) {
        new Thread(() -> {
            try {
                HttpURLConnection c =
                    (HttpURLConnection)
                    new URL(url).openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                InputStream in = c.getInputStream();
                Bitmap bitmap =
                    BitmapFactory.decodeStream(in);
                in.close();
                c.disconnect();

                if (bitmap != null) {
                    runOnUiThread(() ->
                        view.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    void showMessage(String message) {
        TextView t = new TextView(this);
        t.setText(message);
        t.setTextSize(16);
        results.addView(t);
    }

    String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
