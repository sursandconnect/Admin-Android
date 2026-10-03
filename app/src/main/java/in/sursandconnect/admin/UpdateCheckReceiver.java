package in.sursandconnect.admin;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;

public class UpdateCheckReceiver extends BroadcastReceiver {
 private static final String API="https://script.google.com/macros/s/AKfycbyjTV3BcPjYhXEDFlRhw0P-ZNDR7_k47N3emxak9ccgB2iHjUadognpyWUTBaZe07bu/exec";
 @Override public void onReceive(Context context,Intent intent){
  final PendingResult p=goAsync();new Thread(()->{try{check(context);}catch(Exception ignored){}finally{p.finish();}}).start();
 }
 private void check(Context context)throws Exception{
  SharedPreferences sp=context.getSharedPreferences("sursand_native",Context.MODE_PRIVATE);
  String token=sp.getString("admin_token","");if(token.isEmpty())return;
  JSONObject req=new JSONObject();req.put("action","adminDataAll");req.put("token",token);
  HttpURLConnection c=(HttpURLConnection)new URL(API).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
  try(OutputStream os=c.getOutputStream()){os.write(req.toString().getBytes("UTF-8"));}
  BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);br.close();c.disconnect();
  JSONObject root=new JSONObject(sb.toString());if(!root.optBoolean("success",false))return;JSONObject data=root.optJSONObject("data");if(data==null)return;
  JSONObject latest=latestPending(data.optJSONArray("businesses"),data.optJSONArray("services"),data.optJSONArray("changeMakers"));
  if(latest==null)return;
  String id=first(latest,"Request ID","ID","Business ID","Service ID","Name","Business Name","Service Person Name")+"|"+first(latest,"Created At","Timestamp","Date");
  if(id.equals("|")||id.equals(sp.getString("latest_admin_request","")))return;sp.edit().putString("latest_admin_request",id).apply();
  String kind=first(latest,"_kind");String name=first(latest,"Business Name","Service Person Name","Name","Full Name");
  String title="New "+(kind.isEmpty()?"request":kind+" request");String body=name.isEmpty()?"A new request is waiting for approval.":name+" is waiting for approval.";
  Intent open=new Intent(context,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent pi=PendingIntent.getActivity(context,4300,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,"sursand_updates"):new Notification.Builder(context);
  b.setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(pi);
  ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(4301,b.build());
 }
 private JSONObject latestPending(JSONArray... arrays){
  JSONObject last=null;
  String[] kinds={"Business","Service","Change Maker"};
  for(int a=0;a<arrays.length;a++){JSONArray arr=arrays[a];if(arr==null)continue;for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o==null)continue;String source=o.optString("_source","");String status=first(o,"Status","Approval Status").toLowerCase();if(!"registration".equalsIgnoreCase(source)&&!(status.isEmpty()||status.equals("pending")))continue;try{o.put("_kind",kinds[Math.min(a,kinds.length-1)]);}catch(Exception ignored){}last=o;}}
  return last;
 }
 private String first(JSONObject o,String...keys){for(String k:keys){String v=o.optString(k,"").trim();if(!v.isEmpty())return v;}return "";}
}