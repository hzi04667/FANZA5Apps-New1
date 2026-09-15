package jp.sakai.fanza.search;

import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;

public class MainActivity extends Activity {
  LinearLayout box, results; EditText api, affiliate, keyword; final ArrayList<JSONObject> items=new ArrayList<>();
  public void onCreate(Bundle b){super.onCreate(b); box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,28,32,28);
    TextView h=t("① FANZA作品検索",24);box.addView(h);box.addView(t("DMM Webサービスの情報を入力してください。API IDは端末内だけに保存します。",15));
    api=e("API ID");affiliate=e("アフィリエイトID（例：xxxxx-990）");keyword=e("検索語（女優名・作品名）");box.addView(api);box.addView(affiliate);box.addView(keyword);
    Button go=btn("検索する");go.setOnClickListener(v->search());box.addView(go);results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);box.addView(results);
    ScrollView s=new ScrollView(this);s.addView(box);setContentView(s);getWindow().setStatusBarColor(Color.rgb(216,27,96));
    android.content.SharedPreferences p=getSharedPreferences("keys",0);api.setText(p.getString("api",""));affiliate.setText(p.getString("aff","")); }
  TextView t(String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.DKGRAY);v.setPadding(0,10,0,10);return v;}
  EditText e(String h){EditText v=new EditText(this);v.setHint(h);v.setSingleLine();v.setTextSize(17);v.setPadding(18,18,18,18);return v;}
  Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextSize(17);return b;}
  void search(){String a=api.getText().toString().trim(), f=affiliate.getText().toString().trim(), q=keyword.getText().toString().trim();if(a.isEmpty()||f.isEmpty()||q.isEmpty()){toast("3項目すべて入力してください");return;}
    getSharedPreferences("keys",0).edit().putString("api",a).putString("aff",f).apply();results.removeAllViews();results.addView(t("検索中…",17));
    new Thread(()->{try{String url="https://api.dmm.com/affiliate/v3/ItemList?api_id="+enc(a)+"&affiliate_id="+enc(f)+"&site=FANZA&service=digital&floor=videoa&hits=20&sort=rank&keyword="+enc(q)+"&output=json";
      HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();String body=read(in);if(code>=400)throw new Exception("APIエラー "+code+"\n"+body);
      JSONObject root=new JSONObject(body);JSONArray ar=root.getJSONObject("result").optJSONArray("items");items.clear();if(ar!=null)for(int i=0;i<ar.length();i++)items.add(ar.getJSONObject(i));runOnUiThread(this::showItems);
    }catch(Exception x){runOnUiThread(()->{results.removeAllViews();results.addView(t("検索できませんでした。API ID、アフィリエイトID、審査状態を確認してください。\n\n"+x.getMessage(),15));});}}).start(); }
  void showItems(){results.removeAllViews();if(items.isEmpty()){results.addView(t("該当作品がありません。",17));return;}for(int i=0;i<items.size();i++){JSONObject o=items.get(i);String title=o.optString("title","作品");Button b=btn((i+1)+". "+title);b.setAllCaps(false);final int n=i;b.setOnClickListener(v->select(items.get(n)));results.addView(b);}}
  void select(JSONObject o){String title=o.optString("title",""), aff=o.optString("affiliateURL",o.optString("URL","")), product=o.optString("URL","");JSONObject image=o.optJSONObject("imageURL");String img=image==null?"":image.optString("large",image.optString("list",""));String actress="";JSONArray ac=o.optJSONArray("actress");if(ac!=null&&ac.length()>0)actress=ac.optJSONObject(0).optString("name","");
    String bundle="TITLE: "+title+"\nACTRESS: "+actress+"\nAFFILIATE: "+aff+"\nIMAGE: "+img+"\nPRODUCT: "+product;
    ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("FANZA作品情報",bundle));new AlertDialog.Builder(this).setTitle("作品情報をコピーしました").setMessage(title+"\n\n次は『04 画像準備』を開いてください。").setPositiveButton("OK",null).show();}
  static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}static String read(InputStream i)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(i,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);return b.toString();}void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
