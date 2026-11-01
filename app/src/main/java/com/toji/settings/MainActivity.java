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
import android.text.InputType;
import java.util.*;
import rikka.shizuku.Shizuku;
import android.provider.Settings;
import android.net.Uri;
public class MainActivity extends Activity {
 final int BG=0xff100d18, PANEL=0xff211b2c, PANEL2=0xff292132, PURPLE=0xffa444f4, LIGHT=0xffc782ff, MUTED=0xffaaa4b4, WHITE=0xfff7f3fa;
 final String[] tabs={"Início","Booster","Sensi","Perfil"}, symbols={"⌂","ϟ","☷","♙"};
 final String[] labels={"Geral","Mira Red Dot","Mira 2x","Mira 4x","Sniper","Câmera"};
 final int[] defaults={100,90,85,75,55,78};
 LinearLayout page,content,bar; android.content.SharedPreferences prefs; String current="Início";
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(Color.BLACK);prefs=getSharedPreferences("toji_v2",MODE_PRIVATE);show("Início");}
 int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 GradientDrawable shape(int color,int r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
 GradientDrawable outline(int color,int r,int line){GradientDrawable d=shape(color,r);d.setStroke(dp(1),line);return d;}
 TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
 LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 void add(LinearLayout parent,View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(v,p);}
 void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
 LinearLayout card(String title,String subtitle){LinearLayout c=col();pad(c,18,19);c.setBackground(shape(PANEL,20));add(content,c,15);if(title!=null)add(c,text(title,18,WHITE,true),0);if(subtitle!=null)add(c,text(subtitle,13,MUTED,false),9);return c;}
 void btn(LinearLayout p,String name,Runnable action){TextView t=text(name,14,WHITE,true);t.setGravity(Gravity.CENTER);pad(t,12,16);t.setBackground(shape(PURPLE,15));add(p,t,14);t.setOnClickListener(v->action.run());}
 void info(String message){new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",null).show();}
 void heading(String title,String subtitle){add(content,text(title,27,WHITE,true),24);if(subtitle!=null)add(content,text(subtitle,14,MUTED,false),12);}
 void show(String tab){current=tab;page=col();page.setBackgroundColor(BG);setContentView(page);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=col();pad(content,19,12);scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  if(tab.equals("Início"))home();else if(tab.equals("Booster"))booster();else if(tab.equals("Sensi"))sensi();else profile();
  bar=new LinearLayout(this);bar.setBackgroundColor(PANEL);bar.setPadding(dp(8),dp(9),dp(8),dp(8));page.addView(bar);
  for(int i=0;i<tabs.length;i++){final String target=tabs[i];LinearLayout item=col();item.setGravity(Gravity.CENTER);boolean selected=tab.equals(target);TextView icon=text(symbols[i],27,selected?WHITE:MUTED,true);icon.setGravity(Gravity.CENTER);if(selected){icon.setBackground(shape(0xff8743c3,20));icon.setPadding(dp(18),0,dp(18),0);}item.addView(icon,new LinearLayout.LayoutParams(-2,dp(36)));TextView name=text(tabs[i],12,selected?WHITE:MUTED,selected);name.setGravity(Gravity.CENTER);add(item,name,5);bar.addView(item,new LinearLayout.LayoutParams(0,dp(64),1));item.setOnClickListener(v->show(target));}
 }
 void brand(){TextView logo=text("TOJI SETTINGS",27,PURPLE,true);logo.setGravity(Gravity.CENTER);add(content,logo,24);TextView sub=text("BLOOD STRIKE  |  GAME BOOSTER",11,MUTED,false);sub.setGravity(Gravity.CENTER);add(content,sub,6);}
 void home(){brand();add(content,text("Bem-vindo, "+prefs.getString("nickname","Jogador"),23,WHITE,true),36);LinearLayout a=card("⚡ Game Booster","Prepare seu dispositivo e verifique o Shizuku.");btn(a,"ABRIR GAME BOOSTER",()->show("Booster"));LinearLayout g=card("🎮 Jogos","Abra um jogo instalado no seu celular.");btn(g,"ABRIR BLOOD STRIKE",()->openGame("com.netease.newspike"));btn(g,"ABRIR FREE FIRE",()->openGame("com.dts.freefireth","com.dts.freefiremax"));LinearLayout s=card("🎯 Sensibilidade","Configure perfis de mira e salve seus valores.");btn(s,"AJUSTAR SENSIBILIDADE",()->show("Sensi"));LinearLayout about=card("✦ Toji Settings V3","Tema escuro • Ferramentas locais • Perfis salvos no dispositivo");}
 void openGame(String... pkgs){PackageManager pm=getPackageManager();for(String p:pkgs){Intent launch=pm.getLaunchIntentForPackage(p);if(launch!=null){startActivity(launch);return;}}info("Jogo não encontrado. Confira se está instalado no celular.");}
 boolean shizukuRunning(){try{return Shizuku.pingBinder();}catch(Throwable t){return false;}}
 boolean shizukuAllowed(){try{return shizukuRunning()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(Throwable t){return false;}}
 void requestShizuku(){if(!shizukuRunning()){try{Intent i=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");if(i!=null){startActivity(i);return;}}catch(Exception ignored){}info("Instale e inicie o aplicativo Shizuku. Depois volte ao Toji Settings.");return;}if(shizukuAllowed()){info("Shizuku já está autorizado para o Toji Settings.");return;}try{Shizuku.requestPermission(1001);info("Responda à solicitação de autorização do Shizuku e depois toque em atualizar.");}catch(Throwable t){info("Não foi possível solicitar a permissão: "+t.getMessage());}}
 void booster(){heading("Game Booster","Veja o status do Shizuku e prepare seu dispositivo.");boolean active=shizukuRunning(), allowed=shizukuAllowed();LinearLayout c=card("♢  "+(allowed?"SHIZUKU CONECTADO":active?"SHIZUKU SEM PERMISSÃO":"SHIZUKU DESLIGADO"),allowed?"Autorização concedida ao Toji Settings.":active?"Serviço ativo. Autorize este aplicativo.":"Inicie o Shizuku para usar recursos autorizados.");btn(c,allowed?"VERIFICAR CONEXÃO":active?"AUTORIZAR":"CONECTAR",()->{if(allowed)show("Booster");else requestShizuku();});LinearLayout how=card("ⓘ  Como conectar","1. Abra o Shizuku.\n2. Inicie via depuração sem fio ou outro método suportado.\n3. Autorize o Toji Settings.\n4. Volte e toque em atualizar.");LinearLayout stat=card("▣  APPS EM EXECUÇÃO","O Android limita a visualização de processos de outros apps. O Toji não apresenta contagens inventadas.");btn(stat,"⟳  ATUALIZAR STATUS",()->show("Booster"));LinearLayout dev=card("◈  SEU DISPOSITIVO",Build.MANUFACTURER+" "+Build.MODEL+"  •  Android "+Build.VERSION.RELEASE);LinearLayout manage=card("♧  GERENCIAR SEGUNDO PLANO","O Shizuku autorizado não garante, sozinho, permissão para encerrar qualquer aplicativo. Use as configurações do Android para controlar apps.");btn(manage,"ABRIR GERENCIAMENTO DE APPS",()->{try{startActivity(new Intent("android.settings.APPLICATION_SETTINGS"));}catch(Exception e){info("Não foi possível abrir as configurações.");}});add(content,text("O Toji não monitora processos continuamente nem promete aumento de FPS.",12,MUTED,false),19);stretch();}

void stretch() {
    LinearLayout c = card(
        "▣ TELA ESTICADA • BLOOD STRIKE",
        "Configure a resolução do Android usando Shizuku."
    );

    btn(c, "ABRIR PAINEL FLUTUANTE", () -> {
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                ));
            } catch (Exception e) {
                info("Autorize a sobreposição nas configurações.");
            }
            return;
        }

        try {
            startService(new Intent(this, FloatingPanelService.class));
            info("Painel flutuante iniciado!");
        } catch (Exception e) {
            info("Erro ao iniciar painel: " + e.getMessage());
        }
    });

    btn(c, "RESTAURAR RESOLUÇÃO", () -> {
        new Thread(() -> {
            ResolutionController.Result r =
                ResolutionController.restore(this);

            runOnUiThread(() -> info(r.message));
        }).start();
    });
}
 
 void sensi(){heading("Sensibilidade","Crie seu perfil e ajuste os valores de referência.");LinearLayout p=card("🎯  PERFIS DE MIRA","Perfil atual: "+prefs.getString("profile","Equilibrado"));btn(p,"⚡ RUSH",()->preset("Rush",new int[]{120,110,100,90,65,100}));btn(p,"◎ EQUILIBRADO",()->preset("Equilibrado",defaults));btn(p,"⊕ PRECISÃO",()->preset("Precisão",new int[]{80,75,70,60,40,65}));for(int i=0;i<labels.length;i++){final int index=i;LinearLayout c=card(labels[i],null);int val=prefs.getInt("v"+i,defaults[i]);TextView number=text("Valor: "+val,14,LIGHT,false);add(c,number,10);SeekBar seek=new SeekBar(this);seek.setMax(200);seek.setProgress(val);add(c,seek,5);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int v,boolean from){number.setText("Valor: "+v);if(from)prefs.edit().putInt("v"+index,v).putString("profile","Personalizado").apply();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});}btn(content,"SALVAR CONFIGURAÇÕES",()->info("Valores salvos neste celular. Aplique manualmente nas configurações do jogo."));add(content,text("Os ajustes são referências locais e não modificam os arquivos dos jogos.",12,MUTED,false),15);}
 void preset(String name,int[] values){android.content.SharedPreferences.Editor edit=prefs.edit().putString("profile",name);for(int i=0;i<values.length;i++)edit.putInt("v"+i,values[i]);edit.apply();show("Sensi");}
 void profile(){heading("Meu Perfil",null);LinearLayout c=card("👤  Jogador","Seu perfil fica salvo somente neste dispositivo.");EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setText(prefs.getString("nickname","Jogador"));input.setHint("Apelido");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);add(c,input,15);btn(c,"SALVAR APELIDO",()->{String n=input.getText().toString().trim();if(n.isEmpty()){info("Digite um apelido.");return;}prefs.edit().putString("nickname",n).apply();info("Apelido salvo!");});LinearLayout settings=card("⚙  Configurações","Versão 3.0 • Reconstrução do Toji Settings");btn(settings,"RESTAURAR SENSIBILIDADE",()->new AlertDialog.Builder(this).setTitle("Restaurar sensibilidade?").setMessage("Isso substituirá os valores salvos pelos padrões.").setNegativeButton("CANCELAR",null).setPositiveButton("RESTAURAR",(d,w)->{android.content.SharedPreferences.Editor e=prefs.edit().putString("profile","Equilibrado");for(int i=0;i<defaults.length;i++)e.putInt("v"+i,defaults[i]);e.apply();info("Sensibilidade restaurada.");}).show());LinearLayout about=card("ⓘ  Sobre","Toji Settings V3. As sensibilidades são recomendações locais. O Booster exibe informações disponíveis e orienta o uso do Shizuku sem simular ações privilegiadas.");}
}
