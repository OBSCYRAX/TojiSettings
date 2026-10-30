package com.toji.settings;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  final int BG=0xFF0E0B16, PANEL=0xFF1C1729, PURPLE=0xFF9B42F5, MUTED=0xFFAAA5B7, WHITE=Color.WHITE;
  LinearLayout screen, body, tabs;
  android.content.SharedPreferences prefs;
  String current="Início";
  String[] labels={"Geral","Mira Red Dot","Mira 2x","Mira 4x","Sniper","Câmera"};
  int[] defaults={100,90,85,75,55,78};
  @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("toji_v2",MODE_PRIVATE);render("Início");}
  int dp(int v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
  GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
  TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
  LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  void add(LinearLayout parent,View view,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(view,p);}
  LinearLayout card(String title,String subtitle){LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(bg(PANEL,18));add(c,text(title,18,WHITE,true),0);if(subtitle!=null)add(c,text(subtitle,13,MUTED,false),8);add(body,c,14);return c;}
  void button(LinearLayout parent,String name,Runnable action){TextView b=text(name,15,WHITE,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(16),dp(12),dp(16));b.setBackground(bg(PURPLE,13));add(parent,b,12);b.setOnClickListener(v->action.run());}
  void message(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show();}
  void render(String tab){current=tab;screen=column();screen.setBackgroundColor(BG);setContentView(screen);TextView head=text("TOJI SETTINGS",25,PURPLE,true);head.setGravity(Gravity.CENTER);head.setPadding(0,dp(26),0,dp(6));screen.addView(head);TextView sub=text("BLOOD STRIKE  |  GAME BOOSTER",11,MUTED,false);sub.setGravity(Gravity.CENTER);screen.addView(sub);
    ScrollView scroll=new ScrollView(this);body=column();body.setPadding(dp(18),dp(12),dp(18),dp(28));scroll.setFillViewport(true);scroll.addView(body);screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    tabs=new LinearLayout(this);tabs.setPadding(dp(6),dp(10),dp(6),dp(12));tabs.setBackgroundColor(PANEL);screen.addView(tabs);
    for(String name:new String[]{"🏠 Início","⚡ Booster","🎯 Sensibilidade","👤 Perfil"}){TextView item=text(name,12,name.equals(tab)?PURPLE:MUTED,name.equals(tab));item.setGravity(Gravity.CENTER);tabs.addView(item,new LinearLayout.LayoutParams(0,dp(42),1));item.setOnClickListener(v->render(name));}
    if(tab.equals("Início"))home();else if(tab.equals("Booster"))booster();else if(tab.equals("Sensibilidade"))sensitivity();else profile();
  }
  void home(){add(body,text("Bem-vindo ao Toji Settings",21,WHITE,true),18);LinearLayout c=card("⚡ Game Booster","Ferramentas de verificação e preparação do dispositivo.");button(c,"ABRIR GAME BOOSTER",()->render("Booster"));LinearLayout games=card("🎮 Jogos","Abra um jogo instalado no seu dispositivo.");button(games,"ABRIR BLOOD STRIKE",()->openGame(new String[]{"com.netease.newspike"}));button(games,"ABRIR FREE FIRE",()->openGame(new String[]{"com.dts.freefireth","com.dts.freefiremax"}));LinearLayout quick=card("🎯 Sensibilidade","Configure e salve seus perfis de mira.");button(quick,"AJUSTAR SENSIBILIDADE",()->render("Sensibilidade"));}
  void openGame(String[] pkgs){PackageManager pm=getPackageManager();for(String pkg:pkgs){Intent i=pm.getLaunchIntentForPackage(pkg);if(i!=null){startActivity(i);return;}}message("Jogo não encontrado. Confira se está instalado. Alguns pacotes podem variar conforme a versão.");}
  boolean installed(String pkg){try{getPackageManager().getPackageInfo(pkg,0);return true;}catch(Exception e){return false;}}
  void booster(){add(body,text("Game Booster",22,WHITE,true),18);LinearLayout c=card("🔌 Shizuku",installed("moe.shizuku.privileged.api")?"Aplicativo Shizuku detectado. Autorização ainda não integrada.":"Shizuku não detectado. Instale e inicie o serviço para uma futura integração.");button(c,"VERIFICAR NOVAMENTE",()->render("Booster"));button(c,"ABRIR SHIZUKU",()->openGame(new String[]{"moe.shizuku.privileged.api"}));LinearLayout info=card("📱 Estado do dispositivo","Android "+Build.VERSION.RELEASE+"  •  "+Build.MANUFACTURER+" "+Build.MODEL);button(info,"ATUALIZAR INFORMAÇÕES",()->render("Booster"));LinearLayout proc=card("🛡️ Gerenciamento de processos","Encerrar outros aplicativos exige autorização específica. Esta versão não executa comandos privilegiados nem promete ganho de FPS.");button(proc,"ABRIR CONFIGURAÇÕES DE APPS",()->{Intent i=new Intent("android.settings.APPLICATION_SETTINGS");try{startActivity(i);}catch(Exception e){message("Não foi possível abrir as configurações.");}});}
  void sensitivity(){add(body,text("Sensibilidade",22,WHITE,true),18);add(body,text("Perfil: "+prefs.getString("profile","Personalizado"),14,MUTED,false),6);LinearLayout preset=card("🎯 Perfis sugeridos","Valores de referência; aplique manualmente no jogo.");button(preset,"RUSH",()->setPreset("Rush",new int[]{120,110,100,90,65,100}));button(preset,"EQUILIBRADO",()->setPreset("Equilibrado",defaults));button(preset,"PRECISÃO",()->setPreset("Precisão",new int[]{80,75,70,60,40,65}));for(int i=0;i<labels.length;i++){final int index=i;LinearLayout c=card(labels[i],null);TextView val=text("Valor: "+prefs.getInt("v"+i,defaults[i]),15,WHITE,false);add(c,val,4);SeekBar seek=new SeekBar(this);seek.setMax(200);seek.setProgress(prefs.getInt("v"+i,defaults[i]));add(c,seek,8);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){val.setText("Valor: "+p);if(from)prefs.edit().putInt("v"+index,p).putString("profile","Personalizado").apply();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});}button(body,"SALVAR CONFIGURAÇÕES",()->message("Configurações salvas neste celular. Para usar no jogo, ajuste os valores manualmente no menu de sensibilidade do Blood Strike."));}
  void setPreset(String name,int[] values){android.content.SharedPreferences.Editor e=prefs.edit().putString("profile",name);for(int i=0;i<values.length;i++)e.putInt("v"+i,values[i]);e.apply();render("Sensibilidade");}
  void profile(){add(body,text("Meu Perfil",22,WHITE,true),18);LinearLayout c=card("👤 Jogador", "Seu perfil fica salvo somente neste dispositivo.");EditText input=new EditText(this);input.setSingleLine(true);input.setText(prefs.getString("nickname","Jogador"));input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setHint("Apelido");add(c,input,12);button(c,"SALVAR APELIDO",()->{prefs.edit().putString("nickname",input.getText().toString()).apply();message("Apelido salvo!");});LinearLayout settings=card("⚙️ Configurações","Versão 2.0 • Projeto em reconstrução");button(settings,"RESTAURAR SENSIBILIDADE",()->setPreset("Equilibrado",defaults));LinearLayout about=card("ℹ️ Sobre","Toji Settings V2. Os ajustes são recomendações locais; não alteram diretamente arquivos ou configurações internas dos jogos.");}
}
