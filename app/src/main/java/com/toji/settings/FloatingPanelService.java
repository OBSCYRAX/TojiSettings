package com.toji.settings;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingPanelService extends Service {
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private LinearLayout root, panel;
    private TextView bubble, status;
    private EditText widthInput, heightInput;
    private boolean expanded, busy;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView button(String label){TextView v=new TextView(this);v.setText(label);v.setTextColor(Color.WHITE);v.setTextSize(13);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),dp(12),dp(10),dp(12));v.setBackground(shape(0xff7435ad,12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);panel.addView(v,p);return v;}
    private void toast(String s){handler.post(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    private void setStatus(String s){handler.post(()->{if(status!=null)status.setText(s);});}
    private void hideKeyboard(){try{InputMethodManager im=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(im!=null&&root!=null)im.hideSoftInputFromWindow(root.getWindowToken(),0);if(root!=null)root.clearFocus();}catch(Exception ignored){}}
    private void updateWindow(){if(root!=null&&wm!=null&&params!=null)try{wm.updateViewLayout(root,params);}catch(Exception ignored){}}
    private void setExpanded(boolean open){expanded=open;if(open){bubble.setVisibility(View.GONE);panel.setVisibility(View.VISIBLE);params.width=dp(270);params.height=WindowManager.LayoutParams.WRAP_CONTENT;params.flags&=~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;}else{hideKeyboard();panel.setVisibility(View.GONE);bubble.setVisibility(View.VISIBLE);params.width=dp(62);params.height=dp(62);params.flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;}updateWindow();}
    private void runCommand(int mode,int w,int h){if(busy){toast("Aguarde o comando anterior.");return;}busy=true;setStatus(mode==0?"Testando Shizuku...":"Executando pelo Shizuku...");new Thread(()->{ResolutionController.Result result;try{result=mode==0?ResolutionController.testConnection():mode==2?ResolutionController.restore(this):ResolutionController.apply(this,w,h);}catch(Throwable t){result=new ResolutionController.Result(false,"Erro: "+t.getMessage());}ResolutionController.Result r=result;handler.post(()->{busy=false;String msg=(r.ok?"✓ ":"✕ ")+r.message;setStatus(msg);toast(msg);});}).start();}
    private void drag(View v){v.setOnTouchListener(new View.OnTouchListener(){float downX,downY;int startX,startY;boolean moved;@Override public boolean onTouch(View view,MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=params.x;startY=params.y;moved=false;return true;case MotionEvent.ACTION_MOVE:float dx=e.getRawX()-downX,dy=e.getRawY()-downY;if(Math.abs(dx)>dp(8)||Math.abs(dy)>dp(8))moved=true;if(moved){params.x=startX+(int)dx;params.y=startY+(int)dy;updateWindow();}return true;case MotionEvent.ACTION_UP:if(!moved)setExpanded(!expanded);return true;case MotionEvent.ACTION_CANCEL:return true;default:return false;}}});}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!Settings.canDrawOverlays(this)){toast("Autorize a sobreposição de tela.");stopSelf();return START_NOT_STICKY;}
        if(root!=null)return START_NOT_STICKY;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        bubble=new TextView(this);bubble.setText("TOJI");bubble.setTextColor(Color.WHITE);bubble.setTextSize(13);bubble.setTypeface(null,Typeface.BOLD);bubble.setGravity(Gravity.CENTER);GradientDrawable bg=shape(0xff261a38,100);bg.setStroke(dp(2),0xffb678ff);bubble.setBackground(bg);root.addView(bubble,new LinearLayout.LayoutParams(dp(62),dp(62)));
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(14),dp(12),dp(14),dp(14));panel.setBackground(shape(0xff211b2c,16));panel.setVisibility(View.GONE);root.addView(panel);
        TextView title=new TextView(this);title.setText("☷ TOJI • BLOOD STRIKE");title.setTextColor(0xffe5b7ff);title.setTextSize(14);title.setGravity(Gravity.CENTER);title.setPadding(dp(8),dp(10),dp(8),dp(12));panel.addView(title);
        TextView note=new TextView(this);note.setText("A resolução altera a tela inteira do Android.");note.setTextColor(Color.LTGRAY);note.setTextSize(11);panel.addView(note);
        widthInput=new EditText(this);widthInput.setText("1080");widthInput.setHint("Largura");widthInput.setSingleLine(true);widthInput.setTextColor(Color.WHITE);widthInput.setHintTextColor(Color.LTGRAY);widthInput.setTextSize(16);widthInput.setInputType(InputType.TYPE_CLASS_NUMBER);widthInput.setSelectAllOnFocus(true);panel.addView(widthInput);
        heightInput=new EditText(this);heightInput.setText("1920");heightInput.setHint("Altura");heightInput.setSingleLine(true);heightInput.setTextColor(Color.WHITE);heightInput.setHintTextColor(Color.LTGRAY);heightInput.setTextSize(16);heightInput.setInputType(InputType.TYPE_CLASS_NUMBER);heightInput.setSelectAllOnFocus(true);panel.addView(heightInput);
        TextView test=button("TESTAR SHIZUKU"),apply=button("APLICAR RESOLUÇÃO"),restore=button("RESTAURAR TELA"),game=button("ABRIR BLOOD STRIKE"),minimize=button("MINIMIZAR"),close=button("FECHAR PAINEL");
        status=new TextView(this);status.setText("Teste o Shizuku antes de aplicar.");status.setTextColor(0xffd7c5e9);status.setTextSize(12);status.setPadding(0,dp(12),0,dp(4));panel.addView(status);
        test.setOnClickListener(v->runCommand(0,0,0));
        apply.setOnClickListener(v->{try{int w=Integer.parseInt(widthInput.getText().toString().trim()),h=Integer.parseInt(heightInput.getText().toString().trim());if(w<600||w>3000||h<600||h>4000){toast("Resolução fora dos limites.");return;}hideKeyboard();runCommand(1,w,h);}catch(NumberFormatException e){toast("Digite números válidos.");}});
        restore.setOnClickListener(v->{hideKeyboard();runCommand(2,0,0);});
        game.setOnClickListener(v->{Intent launch=getPackageManager().getLaunchIntentForPackage("com.netease.newspike");if(launch==null){toast("Blood Strike não encontrado.");return;}launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{startActivity(launch);setExpanded(false);}catch(Exception e){toast("Abra o Blood Strike manualmente.");}});
        minimize.setOnClickListener(v->setExpanded(false));close.setOnClickListener(v->stopSelf());drag(bubble);drag(title);
        params=new WindowManager.LayoutParams(dp(62),dp(62),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.START;params.x=dp(20);params.y=dp(110);
        try{wm.addView(root,params);}catch(Exception e){root=null;stopSelf();}
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){hideKeyboard();if(root!=null&&wm!=null)try{wm.removeView(root);}catch(Exception ignored){}root=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
