package jp.sakai.fanza.search;

import android.app.*; import android.os.*; import android.content.*; import android.graphics.Color;
import android.widget.*; import org.json.*; import java.io.*; import java.net.*;
import java.nio.charset.StandardCharsets; import java.util.*;

public class MainActivity extends Activity {
  LinearLayout box, results; EditText api, affiliate, keyword;

  public void onCreate(Bundle b) {
    super.onCreate(b); box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(32,28,32,28);
    box.addView(t("① FANZAランキング自動選択",24));
    box.addView(t("API情報を入力後、ボタンを押すだけでランキング上位の未選択作品を自動で選びます。",15));
    api=e("API ID"); affiliate=e("アフィリエイトID（例：xxxxx-990）"); keyword=e("選ばれた女優名・作品名が自動で入ります");
    keyword.setFocusable(false); keyword.setClickable(false); box.addView(api); box.addView(affiliate); box.addView(keyword);
    Button auto=btn("ランキングから自動選択する"); auto.setOnClickListener(v->loadRanking()); box.addView(auto);
    results=new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); box.addView(results);
    ScrollView s=new ScrollView(this); s.addView(box); setContentView(s); getWindow().setStatusBarColor(Color.rgb(216,27,96));
    SharedPreferences p=getSharedPreferences("keys",0); api.setText(p.getString("api","")); affiliate.setText(p.getString("aff",""));
  }

  TextView t(String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.DKGRAY);v.setPadding(0,10,0,10);return v;}
  EditText e(String h){EditText v=new EditText(this);v.setHint(h);v.setSingleLine();v.setTextSize(17);v.setPadding(18,18,18,18);return v;}
  Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextSize(17);b.setAllCaps(false);return b;}

  void loadRanking(){
    String a=api.getText().toString().trim(), f=affiliate.getText().toString().trim();
    if(a.isEmpty()||f.isEmpty()){toast("API IDとアフィリエイトIDを入力してください");return;}
    getSharedPreferences("keys",0).edit().putString("api",a).putString("aff",f).apply();
    results.removeAllViews(); results.addView(t("FANZAランキングを取得中…",17));
    new Thread(()->{try{
      String url="https://api.dmm.com/affiliate/v3/ItemList?api_id="+enc(a)+"&affiliate_id="+enc(f)+"&site=FANZA&service=digital&floor=videoa&hits=100&sort=rank&output=json";
      HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(20000);
      int code=c.getResponseCode(); InputStream in=code<400?c.getInputStream():c.getErrorStream(); String body=read(in);
      if(code>=400)throw new Exception("APIエラー "+code+"\n"+body);
      JSONArray ar=new JSONObject(body).getJSONObject("result").optJSONArray("items");
      if(ar==null||ar.length()==0)throw new Exception("ランキング作品がありません");
      JSONObject chosen=chooseUnseen(ar); runOnUiThread(()->showChosen(chosen));
    }catch(Exception x){runOnUiThread(()->{results.removeAllViews();results.addView(t("取得できませんでした。API ID、アフィリエイトID、審査状態を確認してください。\n\n"+x.getMessage(),15));});}}).start();
  }

  JSONObject chooseUnseen(JSONArray ar)throws Exception{
    SharedPreferences p=getSharedPreferences("history",0); Set<String> seen=new HashSet<>(p.getStringSet("ids",Collections.emptySet())); JSONObject chosen=null;
    for(int i=0;i<ar.length();i++){JSONObject c=ar.getJSONObject(i);String id=c.optString("content_id",c.optString("product_id",""));if(!seen.contains(id)){chosen=c;break;}}
    if(chosen==null){seen.clear();chosen=ar.getJSONObject(0);} String id=chosen.optString("content_id",chosen.optString("product_id",""));
    if(!id.isEmpty())seen.add(id); p.edit().putStringSet("ids",seen).apply(); return chosen;
  }

  void showChosen(JSONObject o){
    results.removeAllViews(); String title=o.optString("title","作品"), actress=actressOf(o); String query=actress.isEmpty()?title:actress+" / "+title;
    keyword.setText(query); results.addView(t("ランキングから自動選択しました",18)); results.addView(t(query,16)); copyAndContinue(o);
  }

  String actressOf(JSONObject o){JSONArray ac=o.optJSONArray("actress");if(ac!=null&&ac.length()>0){JSONObject first=ac.optJSONObject(0);if(first!=null)return first.optString("name","");}return "";}

  void copyAndContinue(JSONObject o){
    String title=o.optString("title",""),aff=o.optString("affiliateURL",o.optString("URL","")),product=o.optString("URL",""),actress=actressOf(o);
    JSONObject image=o.optJSONObject("imageURL");String img=image==null?"":image.optString("large",image.optString("list",""));
    String data="TITLE: "+title+"\nACTRESS: "+actress+"\nAFFILIATE: "+aff+"\nIMAGE: "+img+"\nPRODUCT: "+product;
    ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("FANZA作品情報",data));
    new AlertDialog.Builder(this).setTitle("自動選択・コピー完了").setMessage((actress.isEmpty()?"":actress+"\n")+title+"\n\n次は『03 X投稿文作成』を開いてください。").setPositiveButton("OK",null).show();
  }

  static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
  static String read(InputStream i)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(i,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);return b.toString();}
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
