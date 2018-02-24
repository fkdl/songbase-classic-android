package com.teapotrecords.sbandroid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.text.Html;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends AppCompatActivity {

  // These are more or less constants.

  private Toast previous_toast = null;

  private static final String rootWeb = "http://www.teapotrecords.co.uk/bfree";
  private static final String versionFile = rootWeb+"/XML/version.xml";

  private String serverName = "http://192.168.1.20:8080";
  //private String serverName = "http://129.31.182.70:8080";
  private final static String version = "1.5";
  private static String newVersion = "1.5";  // Will replace this with downloaded XML
  private final static int internal_version = 7;
  private final static String sbv_wanted = "3.6";
  private final static String app_code = "AC";
  private final static int sbv_wanted_internal = 36;
  private final Handler mHandler = new Handler();
  private Typeface fontCalibri = null;
  private boolean error_flag = true;
  private boolean got_complete_list = false;
  private String searchText = "";
  private int current_op_mode=0;
  private final static CharSequence[] mode_options = {"Fast and loose","Preview Mode"};
  private final static int FAST_AND_LOOSE = 0;
  private final static int PREVIEW_MODE = 1;
  private String last_preview_shortcut="";

  private final static int FORMAT_RTF = 1;
  private final static int FORMAT_TEXT = 2;

  private boolean handshake_complete = false;
  private boolean showing_nonlist_song = false;
  private int last_page_code = -1;
  private int last_list_code = -1;
  private int last_shortcut_ascii = -1;
  private static final String PREFS_NAME = "com.teapotrecords.sbandroid.configv1";
  private static final String DefaultInfo = "<big>Songbase Viewer<br/>for Android.<br/><br/>(C) Teapot Records 2016</big>";

  private String shortcut_list = "";
  private final ArrayList<String> list_info = new ArrayList<>();
  private final ArrayList<Integer> search_ids = new ArrayList<>();
  private final ArrayList<TextView> song_shortcuts = new ArrayList<>();
  private String list_item_selected="";
  private EventHandler eh;
  private String handshake_err="";
  private String handshake_msg="";
  private String pullpage="";
  private String pagertf="";
  private int useFormat=-1;

  private PopupMenu allSongPopup;
  private PopupMenu searchSongPopup;
  private Menu asp_menu;
  private Menu ss_menu;

  // Better thread support

  int corePoolSize = 60;
  int maximumPoolSize = 80;
  int keepAliveTime = 10;

  BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(maximumPoolSize);
  Executor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);

  ///////////////////
  // TOAST CONTROL //

  public void makeToast(String flavour) {
    if (previous_toast != null) {
      previous_toast.cancel();
      previous_toast = null;
    }
    previous_toast = Toast.makeText(MainActivity.this, flavour, Toast.LENGTH_SHORT);
    previous_toast.show();
  }

  protected void onDestroy() {
    super.onDestroy();
    if (previous_toast!=null) {
      previous_toast.cancel();
      previous_toast=null;
    }
  }

  private void savePrefs() {
    SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
    SharedPreferences.Editor editor = settings.edit();
    editor.putString("server", serverName);
    editor.putInt("op_mode",current_op_mode);
    editor.apply();
  }

  public void doServerMenu() {
    AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
    alert.setTitle("Set Server URL"); //Set Alert dialog title here
    alert.setMessage("Server"); //Message here
    final EditText input = new EditText(MainActivity.this);
    input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
    input.setText(serverName);
    alert.setView(input);
    // End of onClick(DialogInterface dialog, int whichButton)
    alert.setPositiveButton("OK", (dialog, whichButton) -> {
      serverName = input.getEditableText().toString();
      while (serverName.endsWith("/")) serverName=serverName.substring(0,serverName.length()-1);
      savePrefs();
      handshake_err="";
      handshake_msg="";
      mHandler.postDelayed(mUpdateTimeTask, 200);


    }); //End of alert.setPositiveButton

    alert.setNegativeButton("CANCEL", (dialog, whichButton) -> {
      handshake_err="";
      handshake_msg="";
      mHandler.postDelayed(mUpdateTimeTask, 200);
      dialog.cancel();
    }); //End of alert.setNegativeButton
    AlertDialog alertDialog = alert.create();
    alertDialog.show();

  }


  private class EventHandler implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    public void onClick(View v) {
      if (v instanceof TextView) {
        for (int i = 0; i < song_shortcuts.size(); i++) {
          if (song_shortcuts.get(i) == v) {
            final int index = i;
            MainActivity.this.runOnUiThread(() -> {
              if (current_op_mode==FAST_AND_LOOSE) {
                song_shortcuts.get(index).setTextColor(Color.BLUE);
                new NetTask(NetTask.REQUEST_PAGE).executeOnExecutor(threadPoolExecutor,serverName + "/_Request_Part_" + song_shortcuts.get(index).getText());

              } else if (current_op_mode==PREVIEW_MODE) {
                for (int i1 = 0; i1 <song_shortcuts.size(); i1++) {
                  if (song_shortcuts.get(i1).getCurrentTextColor() == Color.GREEN)
                    song_shortcuts.get(i1).setTextColor(Color.WHITE);
                }
                if (song_shortcuts.get(index).getCurrentTextColor()!=Color.RED)
                  song_shortcuts.get(index).setTextColor(Color.GREEN);
                new NetTask(NetTask.PREVIEW_PAGE).executeOnExecutor(threadPoolExecutor,serverName + "/"+pullpage+ song_shortcuts.get(index).getText());
                last_preview_shortcut=song_shortcuts.get(index).getText().toString();
              }
            });
          }
        }
      }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      final int id = item.getItemId();
      final String item_text = item.getTitle().toString();
      if (id==R.id.choose_mode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Select Interface Mode");
        builder.setSingleChoiceItems(mode_options, current_op_mode, (dialog, item1) -> {
          current_op_mode = item1;
          savePrefs();
          dialog.dismiss();
        });
        final AlertDialog modeDialog=builder.create();
        modeDialog.show();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        WindowManager.LayoutParams lp2;
        Window w = modeDialog.getWindow();
        if (w!=null) {
          lp2 = w.getAttributes();
          if (lp2 != null) lp.copyFrom(lp2);
        }
          lp.width=600;
        modeDialog.getWindow().setAttributes(lp);

      } else if (id == R.id.server_url_menu) {
        doServerMenu();
      } else if (id == R.id.server_req_focus) {
        new NetTask(MainActivity.NetTask.REQUEST_PAGE).executeOnExecutor(threadPoolExecutor,serverName + "/_Request_Focus_");
      } else if (id == R.id.server_req_unfocus) {
        new NetTask(MainActivity.NetTask.REQUEST_PAGE).executeOnExecutor(threadPoolExecutor,serverName + "/_Request_LoseFocus_");

      } else if (id == R.id.about_menu) {
        AlertDialog.Builder ver = new AlertDialog.Builder(MainActivity.this);
        ver.setTitle("Songbase Viewer for Android");
        ver.setMessage("Version: " + version);
        ver.setCancelable(false);
        ver.setPositiveButton("OK", (dialog, whichButton) -> dialog.cancel());
        AlertDialog ad = ver.create();
        ad.show();
      } else {
        int group = item.getGroupId();
        if (group==1) {
          if (id>=2000) { // List all songs menu
            new NetTask(NetTask.REQUEST_SONG_INDEX).executeOnExecutor(threadPoolExecutor,serverName + "/_Select_Index_" + String.valueOf(id - 2000));
          }
        } else if (group==2) { // Search song menu
          if (id>=2000) {
            new NetTask(NetTask.REQUEST_SONG_INDEX).executeOnExecutor(threadPoolExecutor,serverName+"/_Select_Index_"+String.valueOf(search_ids.get(id-2000)));
          }

        } else if (group==0) {
          if ((id>=1000) && (id<=1999)) {
            MainActivity.this.runOnUiThread(() -> {
              last_shortcut_ascii = -1;
              new NetTask(NetTask.REQUEST_SONG).executeOnExecutor(threadPoolExecutor,serverName + "/_Request_Song_" + item_text.substring(0,1));
            });
          }
        }
      }
      return false;
    }
  }

  private int parseIntOrNull(String s) {
    return (s!=null)?Integer.parseInt(s):-1;
  }

  private String parseStringOrNull(String s) {
    return (s!=null)?s:"";
  }

  /*
     Initialise app
  /****************************************/

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
    serverName = settings.getString("server", "http://192.168.2.2:8080");
    current_op_mode = settings.getInt("op_mode", 0);

    setContentView(R.layout.activity_main);
    mHandler.removeCallbacks(mUpdateTimeTask);
    mHandler.postDelayed(mUpdateTimeTask, 1000);
    TextView tv = (TextView) findViewById(R.id.main_text);
    tv.setText(Html.fromHtml(DefaultInfo));
    tv.setClickable(true);
    tv.setOnClickListener(v -> {
      if (current_op_mode==PREVIEW_MODE) {
        TextView _tv = (TextView) v;
        if (_tv.getCurrentTextColor()==Color.GRAY) {
          if (last_preview_shortcut.length()>0) {
            new NetTask(NetTask.REQUEST_PAGE).executeOnExecutor(threadPoolExecutor,serverName + "/_Request_Part_" + last_preview_shortcut);
          }
        }
      }
    });
    if (fontCalibri==null) fontCalibri = Typeface.createFromAsset(getAssets(), "calibri.ttf");
    tv.setTypeface(fontCalibri);
    tv.setTextSize(32);
    eh = new EventHandler();

    allSongPopup = new PopupMenu(this,findViewById(R.id.all_songs));
    asp_menu=allSongPopup.getMenu();
    allSongPopup.setOnMenuItemClickListener(eh);

    searchSongPopup = new PopupMenu(this,findViewById(R.id.search_songs));
    ss_menu=searchSongPopup.getMenu();
    searchSongPopup.setOnMenuItemClickListener(eh);

    /*
       Retrieve state after a rotate
                                      */

    if (savedInstanceState != null) {
      handshake_complete = savedInstanceState.getBoolean("handshake_complete");
      searchText = savedInstanceState.getString("search_text");
      last_page_code = savedInstanceState.getInt("last_page_code");
      last_list_code = savedInstanceState.getInt("last_list_code");
      last_list_code = savedInstanceState.getInt("last_list_code");
      last_shortcut_ascii = savedInstanceState.getInt("last_shortcut_ascii");
      showing_nonlist_song = savedInstanceState.getBoolean("showing_nonlist_song");
      useFormat = savedInstanceState.getInt("use_format");
      pullpage = savedInstanceState.getString("pullpage");
      pagertf = savedInstanceState.getString("pagertf");
      shortcut_list = parseStringOrNull(savedInstanceState.getString("shortcut_list"));
      list_info.clear();
      int list_size = savedInstanceState.getInt("list_size");
      for (int i=0; i<list_size; i++) {
        list_info.add(parseStringOrNull(savedInstanceState.getString("list_item_"+i)));
      }
      list_item_selected=savedInstanceState.getString("list_item_selected");
      int sc_size = savedInstanceState.getInt("sc_size");
      song_shortcuts.clear();
      if (fontCalibri == null) fontCalibri = Typeface.createFromAsset(getAssets(), "calibri.ttf");
      for (int i=0; i<sc_size; i++) {
        tv = new TextView(MainActivity.this);
        tv.setPadding(0, 0, 0, 0);
        tv.setGravity(Gravity.CENTER);
        //tv.setMinWidth(80);
        tv.setTextColor(Color.WHITE);
        tv.setClickable(true);
        tv.setOnClickListener(eh);
        tv.setTypeface(fontCalibri);
        tv.setTextSize(52);
        if (getResources().getConfiguration().orientation== Configuration.ORIENTATION_LANDSCAPE) tv.setPadding(0,0,0,0);
        else tv.setPadding(40,0,40,0);
        song_shortcuts.add(tv);
      }
      int ll_size = savedInstanceState.getInt("ll_size");
      LinearLayout ll = (LinearLayout) findViewById(R.id.song_shortcuts);
      for (int i=0; i<ll_size; i++) ll.addView(song_shortcuts.get(i));
      error_flag=savedInstanceState.getBoolean("net_status");
      runOnUiThread(updateShortCutButtons);
      runOnUiThread(updateSelectedShortcutColour);

    } else {
      MainActivity.this.runOnUiThread(versionChecker);
    }

  }

  /*
     Save state before a rotate
                                   */

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean("handshake_complete", handshake_complete);
    outState.putInt("last_page_code", last_page_code);
    outState.putInt("last_list_code", last_list_code);
    outState.putInt("last_shortcut_ascii", last_shortcut_ascii);
    outState.putBoolean("showing_nonlist_song", showing_nonlist_song);
    outState.putString("shortcut_list", String.valueOf(shortcut_list));
    outState.putString("search_text", searchText);
    outState.putInt("list_size", list_info.size());
    outState.putInt("use_format", useFormat);
    outState.putString("pullpage",pullpage);
    outState.putString("pagertf",pagertf);

    for (int i = 0; i < list_info.size(); i++) {
      outState.putString("list_item_"+i,list_info.get(i));
    }
    outState.putString("list_item_selected", list_item_selected);

    // Save the shortcut objects

    outState.putInt("sc_size", song_shortcuts.size());
    LinearLayout ll = (LinearLayout) findViewById(R.id.song_shortcuts);
    outState.putInt("ll_size", ll.getChildCount());

    outState.putBoolean("net_status",error_flag);
    runOnUiThread(error_flag ? netError : netOk);
  }


  public void showPopup(View v) {
    PopupMenu popup = new PopupMenu(this, v);
    MenuInflater inflater = popup.getMenuInflater();
    inflater.inflate(R.menu.menu_main, popup.getMenu());
    popup.setOnMenuItemClickListener(eh);
    popup.show();
  }

  public void pickSongs(View v) {
    PopupMenu popup = new PopupMenu(this, v);
    Menu menu = popup.getMenu();
    int count = list_info.size();
    if (showing_nonlist_song) count--;
    for (int i=0; i<count; i++) {
      menu.add(0,1000+i,i,list_info.get(i));
    }
    popup.setOnMenuItemClickListener(eh);
    popup.show();
  }

  public void resetShortcuts() {
    last_preview_shortcut="";
    for (int i=0; i<song_shortcuts.size(); i++) {
      song_shortcuts.get(i).setTextColor(Color.WHITE);
    }
  }

  public void blankScreen(View v) {
    new NetTask(MainActivity.NetTask.REQUEST_PAGE).executeOnExecutor(threadPoolExecutor,serverName + "/_Request_Part_-1");
    resetShortcuts();

  }

  public void updateLyrics(String[] results,boolean preview) {
    TextView tv = (TextView) findViewById(R.id.main_text);
    if (preview) tv.setTextColor(Color.GRAY);
    else tv.setTextColor(Color.WHITE);
    StringBuilder sb = new StringBuilder();
    sb.append("<html><body><center>");
    for (String result1 : results) {
      String bit;
      if (useFormat==FORMAT_RTF) {
        result1 = result1.replace("\\viewkind4","\\par ");
        result1 = result1.replace("\\deflang2057","\\par ");
        if (result1.startsWith("\\par")) {
          bit = result1.substring(5);
          bit = bit.replace("\\pard", "");
          bit = bit.replace("\\qc", "");
          bit = bit.replace("\\plain", "");
          bit = bit.replace("\\cf1", "");
          bit = bit.replace("\\b", "");
          bit = bit.replace("\\uc1", "");
          bit = bit.replace("\\i0", "</i>");
          bit = bit.replace("\\i", "<i>");
          bit = bit.replace("'", "&#39;");
          if (bit.contains("\\fs")) {
            int x = bit.indexOf("\\fs") + 3;
            while ((x < bit.length()) && (bit.charAt(x) != ' ') && (bit.charAt(x) != '\\'))
              x++;
            bit = bit.substring(0, bit.indexOf("\\fs")) + bit.substring(x);
          }
          bit = bit.replace("\\f0 ", "");
          bit = bit.replace("\\f1 ", "");
          bit = bit.replace("\\f2 ", "");
          bit = bit.replace("\\f3 ", "");
          bit = bit.replace("\\lang2057", "");
          bit = bit.replace("\\lang1033", "");

          if (bit.trim().equals("}")) bit = "";
          if (bit.equals("")) bit = "&nbsp;";
          sb.append(bit).append("<br/>");
        }
      } else {
        sb.append(result1).append("<br/>");
      }
    }
    sb.append("</center></body></html>");
    String ss = sb.toString();
    while (ss.contains("<br/><br/><br/>")) ss=ss.replace("<br/><br/><br/>","<br/><br/>");
    while (ss.startsWith("<html><body><center><br/>")) ss="<html><body><center>"+ss.substring(25);
    tv.setText(Html.fromHtml(ss));
  }

  class NetTask extends AsyncTask<String, Void, String> {
    final byte task_type;
    static final byte CHECK_UPDATE = 1;
    static final byte FETCH_PAGE = 2;
    static final byte FETCH_LIST = 3;
    static final byte REQUEST_PAGE = 4;
    static final byte REQUEST_SONG = 5;
    static final byte GET_ALL_SONGS = 6;
    static final byte REQUEST_SONG_INDEX = 7;
    static final byte SEARCH_SONGS = 8;
    static final byte HANDSHAKE = 9;
    static final byte PREVIEW_PAGE = 10;
    static final byte CHECK_SOFTWARE_VERSION = 11;

    NetTask(byte type) {
      super();
      task_type=type;
    }

    private String readStream(InputStream in) {
      BufferedReader reader = null;
      StringBuilder sb = new StringBuilder();
      try {
        reader = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line).append("\n");
        }
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        if (reader != null) {
          try {
            reader.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
      return sb.toString();
    }

    @Override
    protected String doInBackground(String... params) {
      try {
        final String result;
        //System.out.println(params[0]);
        URL url = new URL(params[0]);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setUseCaches(false);
        con.setConnectTimeout(1000);
        result = readStream(con.getInputStream());
        final String[] results = result.split("\n");
        if (task_type == CHECK_UPDATE) {
          int code = Integer.parseInt(results[0]);
          if (code != last_page_code) {
            last_page_code = code;
            MainActivity.this.runOnUiThread(pageFetcher);
          }

          code = Integer.parseInt(results[1]);
          if (code != last_list_code) {
            last_list_code = code;
            MainActivity.this.runOnUiThread(listFetcher);
          }

          code = Integer.parseInt(results[2]);
          if (code != last_shortcut_ascii) {
            last_shortcut_ascii = code;
            MainActivity.this.runOnUiThread(updateSelectedShortcutColour);
          }

        } else if (task_type == FETCH_PAGE) {

          MainActivity.this.runOnUiThread(() -> updateLyrics(results,false));
        } else if (task_type == FETCH_LIST) {
          int no_songs = parseIntOrNull(results[0]);
          shortcut_list = results[2].split("\t")[0];
          list_info.clear();
          for (int i = 0; i < no_songs; i++) {
            list_info.add(results[3+i].replace("*",". "));
          }
          showing_nonlist_song=false;
          if (results.length>3+no_songs) {
            list_info.add(results[3+no_songs].replace("*",". "));
            showing_nonlist_song=true;
          }
          final int last_item_selected2 = parseIntOrNull(results[1]);
          final String last_string;
          if (last_item_selected2==-1) {
            if (showing_nonlist_song) last_string=list_info.get(no_songs);
            else last_string="";
          } else last_string=list_info.get(last_item_selected2);

          if (!list_item_selected.equals(last_string)) {
            MainActivity.this.runOnUiThread(() -> {
              makeToast("Song selected: " + last_string);
              last_preview_shortcut="";
            });
          }
          list_item_selected = last_string;
          MainActivity.this.runOnUiThread(updateShortCutButtons);
        } else if (task_type == REQUEST_SONG) {
          MainActivity.this.runOnUiThread(listFetcher);


        } else if (task_type == GET_ALL_SONGS) {
          int no_songs = parseIntOrNull(results[0]);
          String[] s;
          asp_menu.clear();
          for (int i = 0; i < no_songs; i++) {
            s = results[i + 1].split("\t");
            if ((s.length > 2) && (s[2].length() > 0)) {
              asp_menu.add(1, 2000 + i, Menu.NONE, s[1] + " (" + s[2] + ")");
            } else {
              asp_menu.add(1, 2000 + i, Menu.NONE, s[1]);
            }
          }
          if (no_songs > 0) got_complete_list = true;
        } else if (task_type == SEARCH_SONGS) {
          String[] s;
          search_ids.clear();
          ss_menu.clear();
          for (int i = 0; i < results.length; i++) {
            s = results[i].split("\t");
            if ((s.length > 2) && (s[2].length() > 0)) {
              search_ids.add(Integer.parseInt(s[0]));
              ss_menu.add(2, 2000 + i, Menu.NONE, s[2]);
            }

          }
          MainActivity.this.runOnUiThread(showSearchResults);

        } else if (task_type == HANDSHAKE) {
          String[] s = results[0].split("\t");
          if (s[0].equals("OK")) {
            MainActivity.this.handshake_complete=true;
            useFormat = FORMAT_RTF;
            pullpage = "_Pull_Part_";
            pagertf="page.rtf";
            if (s.length>1) { // Version 41 or later returns version number
              int sbv = Integer.parseInt(s[1]);
              if (sbv>=41) {
                useFormat = FORMAT_TEXT;
                pullpage = "_Pull_PartTXT_";
                pagertf = "pagertf.txt";
              }
            }
            mHandler.postDelayed(mUpdateTimeTask, 200);
          }
          else {
            if (s[0].equals("SB_VER")) {
              handshake_err="Songbase Server Version Problem";
              handshake_msg="This app needs server version "+sbv_wanted+", but the server is running version "+s[1];
              MainActivity.this.runOnUiThread(handshakeError);
            } else if (s[0].equals("ANDROID_VER")) {
              handshake_err="SB-Android Version Problem";
              handshake_msg="The Songbase server wants version " + s[1] + " of this app - we are running version " + version;
              MainActivity.this.runOnUiThread(handshakeError);
            }
          }


        } //else if (task_type == REQUEST_SONG_INDEX) {
          // Fine - nothing to do!
        //}

        else if (task_type == PREVIEW_PAGE) {
          MainActivity.this.runOnUiThread(() -> updateLyrics(results, true));


        } else if (task_type == CHECK_SOFTWARE_VERSION) {
          checkSoftwareVersion(new URL(params[0]));
        }

        if (error_flag) {
          MainActivity.this.runOnUiThread(netOk);
          error_flag=false;
        }
      }
      catch (Exception e) {
        //e.printStackTrace();
        MainActivity.this.runOnUiThread(netError);
        error_flag=true;
        return null;
      }
      return null;
    }
  }

  // The task to update the visible buttons for bits of a song


  private final Runnable updateShortCutButtons = new Runnable() {
    public void run() {
      LinearLayout ll = (LinearLayout) findViewById(R.id.song_shortcuts);
      ll.removeAllViews();
      while (song_shortcuts.size()<shortcut_list.length()) {
        TextView tv = new TextView(MainActivity.this);
        if (getResources().getConfiguration().orientation== Configuration.ORIENTATION_LANDSCAPE) tv.setPadding(0,0,0,0);
        else tv.setPadding(40,0,40,0);
        tv.setGravity(Gravity.CENTER);
        //tv.setMinWidth(80);
        tv.setTextColor(Color.WHITE);
        tv.setClickable(true);
        tv.setOnClickListener(eh);
        if (fontCalibri==null) fontCalibri = Typeface.createFromAsset(getAssets(), "calibri.ttf");
        tv.setTypeface(fontCalibri);
        tv.setTextSize(52);

        song_shortcuts.add(tv);
      }
      while (ll.getChildCount()<shortcut_list.length()) {
        ll.addView(song_shortcuts.get(ll.getChildCount()));
      }
      while (ll.getChildCount()>shortcut_list.length()) {
        ll.removeView(song_shortcuts.get(ll.getChildCount()-1));
      }
      for (int i=0; i<shortcut_list.length(); i++) {
        song_shortcuts.get(i).setText(String.valueOf(shortcut_list.charAt(i)));
      }
    }
  };

  // Update the colour of the selected shortcut...

  private final Runnable updateSelectedShortcutColour = () -> {
    LinearLayout ll = (LinearLayout) findViewById(R.id.song_shortcuts);
    for (int i=0; i<ll.getChildCount(); i++) {
      boolean selected = (shortcut_list.charAt(i)==Character.toString((char)last_shortcut_ascii).charAt(0));
      song_shortcuts.get(i).setTextColor(selected ? Color.RED : Color.WHITE);

    }
  };

  public void listAll(View v) {
    allSongPopup.show();
  }

  public void searchSongs(View v) {
    AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
    alert.setTitle("Search Song Text"); //Set Alert dialog title here
    alert.setMessage("Search for: "); //Message here
    final EditText input = new EditText(MainActivity.this);
    input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
    input.setText(searchText);
    alert.setView(input);
    // End of onClick(DialogInterface dialog, int whichButton)
    alert.setPositiveButton("OK", (dialog, whichButton) -> {
      searchText = input.getEditableText().toString();
      new NetTask(NetTask.SEARCH_SONGS).executeOnExecutor(threadPoolExecutor,serverName + "/_Search_Text_" + searchText);
    }); //End of alert.setPositiveButton

    alert.setNegativeButton("CANCEL", (dialog, whichButton) -> dialog.cancel()); //End of alert.setNegativeButton
    AlertDialog alertDialog = alert.create();

    alertDialog.show();
  }
  // Tasks...

  private final Runnable mUpdateTimeTask = new Runnable() {
    public void run() {
      if (!handshake_complete) {
        if (handshake_err.equals("")) handShaker.run();
      } else {
        new NetTask(NetTask.CHECK_UPDATE).executeOnExecutor(threadPoolExecutor,serverName + "/page.txt");
        if (!got_complete_list) {
          new NetTask(NetTask.GET_ALL_SONGS).executeOnExecutor(threadPoolExecutor,serverName + "/complete_list.txt");
        }
        mHandler.postDelayed(this, 1000);
      }
    }
  };


  private final Runnable listFetcher = () -> new NetTask(NetTask.FETCH_LIST).executeOnExecutor(threadPoolExecutor,serverName + "/current_list.txt");

  private final Runnable pageFetcher = () -> new NetTask(NetTask.FETCH_PAGE).executeOnExecutor(threadPoolExecutor,serverName + "/"+pagertf);

  private final Runnable handShaker = () -> new NetTask(NetTask.HANDSHAKE).executeOnExecutor(threadPoolExecutor,serverName + "/_Handshake_"+app_code+"_"+internal_version+"_"+sbv_wanted_internal+"_"+sbv_wanted);


  private final Runnable netError = () -> {
    ImageButton net = (ImageButton) findViewById(R.id.net_indicator);
    net.setImageResource(R.drawable.netnotok);
  };

  private final Runnable handshakeError = () -> {
    AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
    alert.setTitle(handshake_err); //Set Alert dialog title here
    alert.setMessage(handshake_msg);
    alert.setPositiveButton("Change Server", (dialog, whichbutton) -> {
      dialog.dismiss();
      MainActivity.this.doServerMenu();
    });
    alert.setNegativeButton("Accept it and move on", (dialog, whichButton) -> {
      dialog.dismiss();
      System.exit(1);
    });
    AlertDialog dialog = alert.create();
    dialog.setCancelable(false);
    dialog.show();
  };

  private final Runnable netOk = () -> {
    ImageButton net = (ImageButton) findViewById(R.id.net_indicator);
    net.setImageResource(R.drawable.netok);
  };

  private final Runnable showSearchResults = new Runnable() {
    public void run() {
      if (ss_menu.size()==0) {
        makeToast("No matching songs I'm afraid");
      } else {
        searchSongPopup.show();
      }
    }
  };


  private Document XMLFromStream(URL url) {
    Document doc;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      URLConnection urlconn = url.openConnection();
      urlconn.setConnectTimeout(1000);
      doc = builder.parse(urlconn.getInputStream());
    } catch (Exception e) {
      //e.printStackTrace();
      doc=null;
    }
    return doc;
  }


  private boolean pingTest() {
    Runtime runtime = Runtime.getRuntime();
    boolean ok;
    try {
      Process mIpAddrProcess = runtime.exec("/system/bin/ping -w 2 -c 1 8.8.8.8");
      int mExitValue = mIpAddrProcess.waitFor();
      ok=(mExitValue == 0);
    } catch (Exception e) { ok=false; e.printStackTrace(); }
    return ok;
  }

  void checkSoftwareVersion(URL url) {
    if (pingTest()) {
      Document doc = XMLFromStream(url);
      if (doc != null) {
        Element root = doc.getDocumentElement();
        newVersion = XMLHelper.getTag(root, "sbandroid").getTextContent();
        if (!newVersion.equals(version)) {
          MainActivity.this.runOnUiThread(offerSoftwareUpdate);
        }
      }
    }
  }

  private final Runnable offerSoftwareUpdate = () -> {
    AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
    alert.setTitle("Software Update!"); //Set Alert dialog title here
    alert.setMessage("I need to update myself from version  " + version + " to " + newVersion); //Message here
    alert.setPositiveButton("Alright then", (dialog, whichButton) -> {
      String ff = "http://www.teapotrecords.co.uk/bfree/sbandroid.apk";
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ff)));
    });
    alert.setNegativeButton("Too Busy Now", (dialog, whichButton) -> dialog.cancel()); //End of alert.setNegativeButton
    alert.setCancelable(false);
    AlertDialog alertDialog = alert.create();
    alertDialog.show();
  };


  private final Runnable versionChecker = () -> new NetTask(NetTask.CHECK_SOFTWARE_VERSION).executeOnExecutor(threadPoolExecutor,versionFile);
}