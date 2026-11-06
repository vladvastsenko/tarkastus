package fi.tarkastus.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.text.*;
import android.util.TypedValue;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root, form, defectsBox;
    EditText title, object, address, customer, contractor, inspector, dateField, scope, generalNotes, conclusion;
    TextView logoStatus, savedStatus;
    LinearLayout customFieldsBox;
    ImageView logoPreview;
    Bitmap logoBitmap;
    ArrayList<Defect> defects = new ArrayList<>();
    ArrayList<CustomField> customFields = new ArrayList<>();
    SignatureView signatureView;
    static class CustomField { String name="", value=""; }
    File workDir;
    static class PhotoItem {
        Bitmap original, annotated;
        String comment="";
    }
    static class Defect {
        String place="", description="", action="";
        ArrayList<PhotoItem> photos = new ArrayList<>();
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        workDir = new File(getFilesDir(), "report_assets"); workDir.mkdirs();
        buildUi(); restoreDraft();
    }
    int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    TextView text(String s,int size, boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(35,45,58)); if(bold)t.setTypeface(null,1); t.setPadding(0,dp(5),0,dp(5)); return t; }
    GradientDrawable bg(int color){ GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(12)); return d; }
    EditText field(String hint){ EditText e=new EditText(this); e.setSingleLine(false); e.setTextSize(15); e.setHint(hint); e.setPadding(dp(12),dp(10),dp(12),dp(10)); e.setBackground(bg(Color.rgb(246,248,250))); e.setMinHeight(dp(48)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(8); form.addView(e,p); return e; }
    void section(String s){ TextView t=text(s,18,true); t.setPadding(0,dp(16),0,dp(7)); form.addView(t); }
    Button button(String label, Runnable r){ Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v->r.run()); return b; }
    void buildUi(){
        ScrollView scroll=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(18),dp(18),dp(30)); root.setBackgroundColor(Color.WHITE); scroll.addView(root); setContentView(scroll);
        TextView brand=text("TARKASTUS",24,true); brand.setTextColor(Color.rgb(25,54,82)); root.addView(brand);
        root.addView(text("Yleinen tarkastusraportti",14,false));
        form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); root.addView(form);
        section("Raportin tiedot");
        title=field("Raportin otsikko"); title.setText("Yleinen tarkastusraportti");
        object=field("Kohteen nimi"); address=field("Kohteen osoite"); customer=field("Tilaaja"); contractor=field("Urakoitsija"); inspector=field("Tarkastaja");
        dateField=field("Tarkastuspäivä (pp.kk.vvvv)"); dateField.setText(new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("fi-FI")).format(new Date()));
        section("Tarkastuksen kuvaus"); scope=field("Tarkastuksen tarkoitus ja laajuus"); generalNotes=field("Yleiset huomiot");
        section("Omat lisäkentät");
        LinearLayout customFieldsBox=new LinearLayout(this); customFieldsBox.setOrientation(LinearLayout.VERTICAL); form.addView(customFieldsBox);
        form.addView(button("+ Lisää oma kenttä",()->addCustomField()));
        this.customFieldsBox=customFieldsBox;
        section("Havaitut puutteet ja virheet");
        defectsBox=new LinearLayout(this); defectsBox.setOrientation(LinearLayout.VERTICAL); form.addView(defectsBox);
        form.addView(button("+ Lisää havainto", this::addDefect));
        section("Yhteenveto"); conclusion=field("Johtopäätökset ja tarvittavat toimenpiteet");
        section("Yrityksen / tarkastajan logo");
        LinearLayout logoRow=new LinearLayout(this); logoRow.setGravity(Gravity.CENTER_VERTICAL); form.addView(logoRow);
        logoPreview=new ImageView(this); logoPreview.setLayoutParams(new LinearLayout.LayoutParams(dp(90),dp(65))); logoPreview.setScaleType(ImageView.ScaleType.FIT_CENTER); logoRow.addView(logoPreview);
        logoStatus=text("Logo ei ole vielä valittu",13,false); logoRow.addView(logoStatus,new LinearLayout.LayoutParams(0,-2,1));
        form.addView(button("Valitse logo",()->pickGallery(-1)));
        section("Allekirjoitus"); signatureView=new SignatureView(this); signatureView.setBackground(bg(Color.rgb(246,248,250))); form.addView(signatureView,new LinearLayout.LayoutParams(-1,dp(130)));
        form.addView(button("Tyhjennä allekirjoitus",()->signatureView.clear()));
        savedStatus=text("Luonnos tallennetaan tähän puhelimeen.",12,false); form.addView(savedStatus);
        form.addView(button("Uusi raportti",this::newReport));
        form.addView(button("Tallenna luonnos",this::saveDraft));
        form.addView(button("Tallenna raportti arkistoon",this::archiveReport));
        form.addView(button("Avaa arkisto",this::showArchive));
        Button pdf=button("Luo PDF-raportti",this::createPdf); pdf.setTextColor(Color.WHITE); pdf.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(25,75,112))); form.addView(pdf);
    }
    void addCustomField(){ CustomField f=new CustomField(); customFields.add(f); renderCustomFields(); }
    void renderCustomFields(){
        if(customFieldsBox==null)return;
        customFieldsBox.removeAllViews();
        for(int i=0;i<customFields.size();i++){
            final int index=i; CustomField f=customFields.get(i);
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(10),dp(8),dp(10),dp(8)); card.setBackground(bg(Color.rgb(244,247,250)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.bottomMargin=dp(8); customFieldsBox.addView(card,cp);
            EditText name=miniField("Kentän nimi / otsikko",f.name); card.addView(name); name.addTextChangedListener(watcher(v->f.name=v));
            EditText value=miniField("Kirjoita tähän",f.value); value.setMinLines(2); value.setGravity(Gravity.TOP|Gravity.START); card.addView(value); value.addTextChangedListener(watcher(v->f.value=v));
            card.addView(button("Poista kenttä",()->{customFields.remove(index);renderCustomFields();}));
        }
    }
    void addDefect(){
        Defect d=new Defect(); defects.add(d); renderDefects();
    }
    void renderDefects(){
        defectsBox.removeAllViews(); int n=0;
        for(Defect d:defects){ final int index=n++; LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12),dp(10),dp(12),dp(10)); card.setBackground(bg(Color.rgb(244,247,250))); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.bottomMargin=dp(10); defectsBox.addView(card,cp);
            card.addView(text("Havainto "+(index+1),16,true));
            EditText place=miniField("Tila / sijainti",d.place); card.addView(place); place.addTextChangedListener(watcher(v->d.place=v));
            EditText desc=miniField("Havaitun puutteen kuvaus",d.description); card.addView(desc); desc.addTextChangedListener(watcher(v->d.description=v));
            EditText action=miniField("Suositeltu korjaustoimenpide",d.action); card.addView(action); action.addTextChangedListener(watcher(v->d.action=v));
            for(int pi=0;pi<d.photos.size();pi++){ final int photoIndex=pi; PhotoItem item=d.photos.get(pi); LinearLayout photoCard=new LinearLayout(this); photoCard.setOrientation(LinearLayout.VERTICAL); photoCard.setPadding(dp(6),dp(6),dp(6),dp(6)); photoCard.setBackground(bg(Color.WHITE)); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2); pp.topMargin=dp(8); card.addView(photoCard,pp);
                photoCard.addView(text("Valokuva "+(pi+1),13,true)); ImageView iv=new ImageView(this); iv.setImageBitmap(item.annotated!=null?item.annotated:item.original); iv.setAdjustViewBounds(true); iv.setMaxHeight(dp(220)); iv.setScaleType(ImageView.ScaleType.FIT_CENTER); photoCard.addView(iv);
                EditText comment=miniField("Valokuvan kommentti",item.comment); photoCard.addView(comment); comment.addTextChangedListener(watcher(v->item.comment=v));
                LinearLayout pr=new LinearLayout(this); photoCard.addView(pr); Button mark=button("Merkitse kuva",()->editPhoto(item)); pr.addView(mark,new LinearLayout.LayoutParams(0,-2,1)); Button removePhoto=button("Poista kuva",()->{d.photos.remove(photoIndex);renderDefects();}); pr.addView(removePhoto,new LinearLayout.LayoutParams(0,-2,1));
            }
            LinearLayout actions=new LinearLayout(this); card.addView(actions);
            Button camera=button("Ota kuva",()->takePhoto(index)); actions.addView(camera,new LinearLayout.LayoutParams(0,-2,1));
            Button gallery=button("Galleriasta",()->pickGallery(index)); actions.addView(gallery,new LinearLayout.LayoutParams(0,-2,1));
            Button remove=button("Poista",()->{defects.remove(index);renderDefects();}); actions.addView(remove,new LinearLayout.LayoutParams(0,-2,1));
        }
    }
    interface TextChanged{void changed(String s);} TextWatcher watcher(TextChanged c){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){} public void onTextChanged(CharSequence s,int st,int before,int count){c.changed(s.toString());} public void afterTextChanged(Editable e){}};}
    EditText miniField(String hint,String value){EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextSize(14);e.setPadding(dp(8),dp(6),dp(8),dp(6));return e;}
    int pickTarget=-1;
    File pendingCameraFile;
    int pendingCameraTarget=-1;

    void pickGallery(int target){
        pickTarget=target;
        Intent i;
        if(Build.VERSION.SDK_INT>=33){
            i=new Intent(MediaStore.ACTION_PICK_IMAGES);
            i.setType("image/*");
            i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,20);
        }else{
            i=new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        }
        startActivityForResult(i,target==-1?10:11);
    }

    void takePhoto(int target){
        if(target<0||target>=defects.size())return;
        try{
            File dir=getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if(dir==null)dir=getCacheDir();
            if(!dir.exists())dir.mkdirs();
            pendingCameraFile=File.createTempFile("tarkastus_photo_",".jpg",dir);
            pendingCameraTarget=target;
            Uri output=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".provider",pendingCameraFile);
            Intent camera=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(MediaStore.EXTRA_OUTPUT,output);
            camera.setClipData(ClipData.newRawUri("Tarkastus photo",output));
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(camera,12);
        }catch(Exception e){
            if(pendingCameraFile!=null)pendingCameraFile.delete();
            pendingCameraFile=null;pendingCameraTarget=-1;
            toast("Kameran avaaminen epäonnistui: "+e.getMessage());
        }
    }

    void addPhoto(Defect d,Bitmap bitmap){
        if(bitmap==null)return;
        PhotoItem item=new PhotoItem();item.original=bitmap;d.photos.add(item);
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==12){
            File captured=pendingCameraFile;int target=pendingCameraTarget;
            pendingCameraFile=null;pendingCameraTarget=-1;
            if(res==RESULT_OK&&captured!=null&&captured.exists()&&captured.length()>0&&target>=0&&target<defects.size()){
                try{Bitmap b=decode(Uri.fromFile(captured));addPhoto(defects.get(target),b);renderDefects();}
                catch(Exception e){toast("Kuvan avaaminen epäonnistui");}
            }else if(res==RESULT_OK){toast("Kuvaa ei saatu tallennettua");}
            if(captured!=null)captured.delete();
            return;
        }
        if(res!=RESULT_OK||data==null)return;
        try{
            if(req==10){
                Uri selected=data.getData();
                if(selected==null&&data.getClipData()!=null)selected=data.getClipData().getItemAt(0).getUri();
                if(selected==null)return;
                logoBitmap=decode(selected);logoPreview.setImageBitmap(logoBitmap);logoStatus.setText("Logo valittu");
            }else if(req==11&&pickTarget>=0&&pickTarget<defects.size()){
                Defect d=defects.get(pickTarget);
                if(data.getClipData()!=null){
                    for(int i=0;i<data.getClipData().getItemCount();i++)addPhoto(d,decode(data.getClipData().getItemAt(i).getUri()));
                }else if(data.getData()!=null)addPhoto(d,decode(data.getData()));
                renderDefects();
            }
        }catch(Exception e){toast("Kuvan avaaminen epäonnistui: "+e.getMessage());}
    }
    Bitmap decode(Uri u)throws Exception{InputStream in=getContentResolver().openInputStream(u);Bitmap b=BitmapFactory.decodeStream(in);if(in!=null)in.close();if(b!=null&&b.getWidth()>1800){float f=1800f/b.getWidth();b=Bitmap.createScaledBitmap(b,1800,(int)(b.getHeight()*f),true);}return b;}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    void editPhoto(PhotoItem photo){
        AnnotationView editor=new AnnotationView(this,photo.annotated!=null?photo.annotated:photo.original);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(8),dp(12),dp(8));
        TextView hint=text("Valitse merkintätyökalu ja osoita puutetta kuvassa. Alkuperäinen kuva säilyy erillään.",13,false);box.addView(hint);
        box.addView(editor,new LinearLayout.LayoutParams(-1,dp(330)));
        LinearLayout tools1=new LinearLayout(this);box.addView(tools1);
        Button free=button("Piirrä",()->editor.setMode(AnnotationView.FREE));tools1.addView(free,new LinearLayout.LayoutParams(0,-2,1));
        Button arrow=button("Nuoli",()->editor.setMode(AnnotationView.ARROW));tools1.addView(arrow,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout tools2=new LinearLayout(this);box.addView(tools2);
        Button circle=button("Ympyrä",()->editor.setMode(AnnotationView.CIRCLE));tools2.addView(circle,new LinearLayout.LayoutParams(0,-2,1));
        Button rect=button("Suorakulmio",()->editor.setMode(AnnotationView.RECT));tools2.addView(rect,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout tools3=new LinearLayout(this);box.addView(tools3); Button label=button("Tekstimerkintä",()->editor.setMode(AnnotationView.TEXT));tools3.addView(label,new LinearLayout.LayoutParams(-1,-2,1));
        LinearLayout row=new LinearLayout(this);box.addView(row);
        Button clear=button("Tyhjennä",editor::clearMarks);row.addView(clear,new LinearLayout.LayoutParams(0,-2,1));
        Button save=button("Tallenna",()->{photo.annotated=editor.result();renderDefects();});row.addView(save,new LinearLayout.LayoutParams(0,-2,1));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Kuvan merkinnät").setView(box).setNegativeButton("Sulje",(a,b)->{}).create();save.setOnClickListener(v->{photo.annotated=editor.result();dialog.dismiss();renderDefects();});dialog.show();
    }
    String val(EditText e){return e.getText().toString().trim();}
    org.json.JSONObject toJson(){org.json.JSONObject j=new org.json.JSONObject();try{j.put("title",val(title));j.put("object",val(object));j.put("address",val(address));j.put("customer",val(customer));j.put("contractor",val(contractor));j.put("inspector",val(inspector));j.put("date",val(dateField));j.put("scope",val(scope));j.put("notes",val(generalNotes));j.put("conclusion",val(conclusion));org.json.JSONArray custom=new org.json.JSONArray();for(CustomField cf:customFields){org.json.JSONObject x=new org.json.JSONObject();x.put("name",cf.name);x.put("value",cf.value);custom.put(x);}j.put("customFields",custom);org.json.JSONArray a=new org.json.JSONArray();for(int i=0;i<defects.size();i++){Defect d=defects.get(i);org.json.JSONObject x=new org.json.JSONObject();x.put("place",d.place);x.put("description",d.description);x.put("action",d.action);org.json.JSONArray photos=new org.json.JSONArray();for(int pi=0;pi<d.photos.size();pi++){PhotoItem item=d.photos.get(pi);org.json.JSONObject ph=new org.json.JSONObject();ph.put("comment",item.comment);String fn="photo_"+i+"_"+pi+".jpg";String originalFn="photo_original_"+i+"_"+pi+".jpg";if(item.annotated!=null){saveBitmap(item.annotated,new File(workDir,fn));ph.put("annotated",fn);}if(item.original!=null){saveBitmap(item.original,new File(workDir,originalFn));ph.put("original",originalFn);}photos.put(ph);}x.put("photos",photos);a.put(x);}j.put("defects",a);if(!signatureView.isEmpty()){savePng(signatureView.bitmap(),new File(workDir,"signature.png"));j.put("signature","signature.png");}if(logoBitmap!=null){saveBitmap(logoBitmap,new File(workDir,"logo.png"));j.put("logo","logo.png");}return j;}catch(Exception e){return j;}}
    void saveBitmap(Bitmap b,File f)throws Exception{FileOutputStream o=new FileOutputStream(f);b.compress(Bitmap.CompressFormat.JPEG,88,o);o.close();}
    void savePng(Bitmap b,File f)throws Exception{FileOutputStream o=new FileOutputStream(f);b.compress(Bitmap.CompressFormat.PNG,100,o);o.close();}
    void saveDraft(){try{getSharedPreferences("draft",0).edit().putString("json",toJson().toString()).apply();savedStatus.setText("Luonnos tallennettu tähän puhelimeen.");toast("Luonnos tallennettu");}catch(Exception e){toast("Tallennus epäonnistui");}}
    void newReport(){new AlertDialog.Builder(this).setTitle("Uusi raportti?").setMessage("Nykyinen raportti jää talteen vain, jos tallennat sen ensin arkistoon. Aloitetaanko tyhjä raportti?").setNegativeButton("Peruuta",null).setPositiveButton("Aloita",(d,w)->{title.setText("Yleinen tarkastusraportti");object.setText("");address.setText("");customer.setText("");contractor.setText("");inspector.setText("");dateField.setText(new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("fi-FI")).format(new Date()));scope.setText("");generalNotes.setText("");conclusion.setText("");customFields.clear();renderCustomFields();defects.clear();renderDefects();signatureView.clear();getSharedPreferences("draft",0).edit().remove("json").apply();savedStatus.setText("Uusi raportti");}).show();}
    File archiveDir(){File d=new File(getFilesDir(),"reports");if(!d.exists())d.mkdirs();return d;}
    void archiveReport(){try{org.json.JSONObject j=toJson();String id=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());j.put("archiveId",id);String prefix="arch_"+id+"_";org.json.JSONArray arr=j.optJSONArray("defects");if(arr!=null)for(int i=0;i<arr.length();i++){org.json.JSONObject d=arr.getJSONObject(i);org.json.JSONArray phs=d.optJSONArray("photos");if(phs!=null)for(int k=0;k<phs.length();k++){org.json.JSONObject ph=phs.getJSONObject(k);copyArchiveAsset(ph,"original",prefix);copyArchiveAsset(ph,"annotated",prefix);}}copyArchiveAsset(j,"logo",prefix);copyArchiveAsset(j,"signature",prefix);File f=new File(archiveDir(),id+".json");FileOutputStream o=new FileOutputStream(f);o.write(j.toString(2).getBytes("UTF-8"));o.close();toast("Raportti tallennettu arkistoon");}catch(Exception e){toast("Arkistointi epäonnistui: "+e.getMessage());}}
    void copyArchiveAsset(org.json.JSONObject holder,String key,String prefix)throws Exception{String old=holder.optString(key,"");if(old.isEmpty())return;File src=new File(workDir,old);if(!src.exists())return;String next=prefix+old;File dst=new File(workDir,next);java.nio.file.Files.copy(src.toPath(),dst.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);holder.put(key,next);}
    void showArchive(){File[] files=archiveDir().listFiles((dir,name)->name.endsWith(".json"));if(files==null||files.length==0){toast("Arkisto on tyhjä");return;}Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));String[] labels=new String[files.length];for(int i=0;i<files.length;i++){try{org.json.JSONObject j=new org.json.JSONObject(readFile(files[i]));labels[i]=j.optString("object","Kohde")+" — "+j.optString("date",files[i].getName());}catch(Exception e){labels[i]=files[i].getName();}}new AlertDialog.Builder(this).setTitle("Tallennetut raportit").setItems(labels,(dialog,which)->{try{loadJson(new org.json.JSONObject(readFile(files[which])));toast("Raportti avattu");}catch(Exception e){toast("Raportin avaaminen epäonnistui");}}).setNegativeButton("Sulje",null).show();}
    String readFile(File f)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();FileInputStream in=new FileInputStream(f);byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)b.write(buf,0,n);in.close();return b.toString("UTF-8");}
    void loadJson(org.json.JSONObject j){defects.clear();customFields.clear();org.json.JSONArray cf=j.optJSONArray("customFields");if(cf!=null)for(int ci=0;ci<cf.length();ci++){org.json.JSONObject x=cf.optJSONObject(ci);if(x!=null){CustomField f=new CustomField();f.name=x.optString("name");f.value=x.optString("value");customFields.add(f);}}renderCustomFields();set(title,j,"title");set(object,j,"object");set(address,j,"address");set(customer,j,"customer");set(contractor,j,"contractor");set(inspector,j,"inspector");set(dateField,j,"date");set(scope,j,"scope");set(generalNotes,j,"notes");set(conclusion,j,"conclusion");org.json.JSONArray a=j.optJSONArray("defects");if(a!=null)for(int i=0;i<a.length();i++){try{org.json.JSONObject x=a.getJSONObject(i);Defect d=new Defect();d.place=x.optString("place");d.description=x.optString("description");d.action=x.optString("action");org.json.JSONArray phs=x.optJSONArray("photos");if(phs!=null)for(int k=0;k<phs.length();k++){org.json.JSONObject ph=phs.getJSONObject(k);PhotoItem item=new PhotoItem();item.comment=ph.optString("comment");String op=ph.optString("original","");String an=ph.optString("annotated","");if(!op.isEmpty()&&new File(workDir,op).exists())item.original=BitmapFactory.decodeFile(new File(workDir,op).getAbsolutePath());if(!an.isEmpty()&&new File(workDir,an).exists())item.annotated=BitmapFactory.decodeFile(new File(workDir,an).getAbsolutePath());d.photos.add(item);}defects.add(d);}catch(Exception ignored){}}String lp=j.optString("logo","");if(!lp.isEmpty()&&new File(workDir,lp).exists()){logoBitmap=BitmapFactory.decodeFile(new File(workDir,lp).getAbsolutePath());logoPreview.setImageBitmap(logoBitmap);logoStatus.setText("Logo valittu");}String sp=j.optString("signature","");if(!sp.isEmpty()&&new File(workDir,sp).exists())signatureView.setBitmap(BitmapFactory.decodeFile(new File(workDir,sp).getAbsolutePath()));renderDefects();}
    void restoreDraft(){try{String s=getSharedPreferences("draft",0).getString("json",null);if(s==null)return;org.json.JSONObject j=new org.json.JSONObject(s);customFields.clear();org.json.JSONArray cf=j.optJSONArray("customFields");if(cf!=null)for(int ci=0;ci<cf.length();ci++){org.json.JSONObject x=cf.optJSONObject(ci);if(x!=null){CustomField f=new CustomField();f.name=x.optString("name");f.value=x.optString("value");customFields.add(f);}}renderCustomFields();set(title,j,"title");set(object,j,"object");set(address,j,"address");set(customer,j,"customer");set(contractor,j,"contractor");set(inspector,j,"inspector");set(dateField,j,"date");set(scope,j,"scope");set(generalNotes,j,"notes");set(conclusion,j,"conclusion");org.json.JSONArray a=j.optJSONArray("defects");if(a!=null)for(int i=0;i<a.length();i++){org.json.JSONObject x=a.getJSONObject(i);Defect d=new Defect();d.place=x.optString("place");d.description=x.optString("description");d.action=x.optString("action");org.json.JSONArray photos=x.optJSONArray("photos");if(photos!=null){for(int pi=0;pi<photos.length();pi++){org.json.JSONObject ph=photos.getJSONObject(pi);PhotoItem item=new PhotoItem();item.comment=ph.optString("comment");String op=ph.optString("original","");String an=ph.optString("annotated","");if(!op.isEmpty()&&new File(workDir,op).exists())item.original=BitmapFactory.decodeFile(new File(workDir,op).getAbsolutePath());if(!an.isEmpty()&&new File(workDir,an).exists())item.annotated=BitmapFactory.decodeFile(new File(workDir,an).getAbsolutePath());d.photos.add(item);}}else{String legacy=x.optString("photo","");if(!legacy.isEmpty()&&new File(workDir,legacy).exists()){PhotoItem item=new PhotoItem();item.annotated=BitmapFactory.decodeFile(new File(workDir,legacy).getAbsolutePath());String original=x.optString("originalPhoto","");item.original=!original.isEmpty()&&new File(workDir,original).exists()?BitmapFactory.decodeFile(new File(workDir,original).getAbsolutePath()):item.annotated;item.comment=x.optString("photoComment");d.photos.add(item);}}defects.add(d);}String lp=j.optString("logo","");if(!lp.isEmpty()&&new File(workDir,lp).exists()){logoBitmap=BitmapFactory.decodeFile(new File(workDir,lp).getAbsolutePath());logoPreview.setImageBitmap(logoBitmap);logoStatus.setText("Logo valittu");}renderDefects();String sp=j.optString("signature","");if(!sp.isEmpty()&&new File(workDir,sp).exists())signatureView.setBitmap(BitmapFactory.decodeFile(new File(workDir,sp).getAbsolutePath()));}catch(Exception ignored){}}
    void set(EditText e,org.json.JSONObject j,String k){e.setText(j.optString(k,""));}
    void createPdf(){try{saveDraft();File out=new File(getExternalFilesDir(null),"Tarkastus_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date())+".pdf");PdfDocument pdf=new PdfDocument();int pageNo=1;PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,pageNo).create());Canvas c=page.getCanvas();Paint p=new Paint(3);int y=48;
        p.setColor(Color.rgb(26,59,86));p.setTextSize(20);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));c.drawText("TARKASTUSRAPORTTI",40,y,p);y+=30;
        if(logoBitmap!=null){Bitmap l=scaled(logoBitmap,110,55);c.drawBitmap(l,430,20,p);}
        p.setColor(Color.DKGRAY);p.setTextSize(10);p.setTypeface(Typeface.DEFAULT);String[] lines={"Raportti: "+val(title),"Kohde: "+val(object),"Osoite: "+val(address),"Tilaaja: "+val(customer),"Urakoitsija: "+val(contractor),"Tarkastaja: "+val(inspector),"Päiväys: "+val(dateField),"","TARKASTUKSEN KUVAUS",val(scope),"","YLEISET HUOMIOT",val(generalNotes)};
        for(String line:lines){if(y>790){pdf.finishPage(page);page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,++pageNo).create());c=page.getCanvas();y=48;}if(line.equals("TARKASTUKSEN KUVAUS")||line.equals("YLEISET HUOMIOT")){p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(Color.rgb(26,59,86));}else{p.setTypeface(Typeface.DEFAULT);p.setColor(Color.DKGRAY);}y=drawWrapped(c,line,40,y,510,p,15);}
        for(CustomField cf:customFields){if(!cf.name.trim().isEmpty()||!cf.value.trim().isEmpty()){p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(Color.rgb(26,59,86));p.setTextSize(11);y=drawWrapped(c,cf.name.trim().isEmpty()?"LISÄTIEDOT":cf.name,40,y,510,p,14);p.setTypeface(Typeface.DEFAULT);p.setColor(Color.DKGRAY);p.setTextSize(10);y=drawWrapped(c,cf.value,40,y,510,p,13);}}
        for(int i=0;i<defects.size();i++){Defect d=defects.get(i);if(y>700){pdf.finishPage(page);page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,++pageNo).create());c=page.getCanvas();y=48;}p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(Color.rgb(26,59,86));p.setTextSize(13);y+=12;c.drawText("HAVAINTO "+(i+1),40,y,p);y+=18;p.setTypeface(Typeface.DEFAULT);p.setTextSize(10);p.setColor(Color.DKGRAY);y=drawWrapped(c,"Sijainti: "+d.place,40,y,510,p,13);y=drawWrapped(c,"Kuvaus: "+d.description,40,y,510,p,13);y=drawWrapped(c,"Toimenpide: "+d.action,40,y,510,p,13);for(int pi=0;pi<d.photos.size();pi++){PhotoItem item=d.photos.get(pi);Bitmap source=item.annotated!=null?item.annotated:item.original;if(source!=null){Bitmap b=scaled(source,500,280);if(y+b.getHeight()+45>820){pdf.finishPage(page);page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,++pageNo).create());c=page.getCanvas();y=45;}c.drawBitmap(b,40,y,p);y+=b.getHeight()+5;y=drawWrapped(c,"Valokuva "+(pi+1)+": "+item.comment,40,y,510,p,13);}}}
        if(y>730){pdf.finishPage(page);page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,++pageNo).create());c=page.getCanvas();y=45;}p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(Color.rgb(26,59,86));p.setTextSize(13);y+=15;c.drawText("YHTEENVETO",40,y,p);y+=18;p.setTypeface(Typeface.DEFAULT);p.setTextSize(10);p.setColor(Color.DKGRAY);y=drawWrapped(c,val(conclusion),40,y,510,p,13);y+=25;c.drawText("Tarkastajan allekirjoitus:",40,y,p);y+=10;if(!signatureView.isEmpty()){Bitmap sig=signatureView.bitmap();Bitmap s=scaled(sig,230,90);c.drawBitmap(s,40,y,p);}else{c.drawLine(40,y+60,260,y+60,p);}pdf.finishPage(page);FileOutputStream os=new FileOutputStream(out);pdf.writeTo(os);os.close();pdf.close();sharePdf(out); }catch(Exception e){toast("PDF:n luominen epäonnistui: "+e.getMessage());}}
    int drawWrapped(Canvas c,String text,float x,int y,int width,Paint p,int lineH){if(text==null||text.isEmpty())return y+lineH;String[] words=text.split(" ");String line="";for(String w:words){String test=line.isEmpty()?w:line+" "+w;if(p.measureText(test)>width&&!line.isEmpty()){c.drawText(line,x,y,p);y+=lineH;line=w;}else line=test;}if(!line.isEmpty()){c.drawText(line,x,y,p);y+=lineH;}return y;}
    Bitmap scaled(Bitmap b,int maxW,int maxH){float f=Math.min((float)maxW/b.getWidth(),(float)maxH/b.getHeight());return Bitmap.createScaledBitmap(b,Math.max(1,(int)(b.getWidth()*f)),Math.max(1,(int)(b.getHeight()*f)),true);}
    void sharePdf(File f){Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");Uri u=androidxUri(f);i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Jaa PDF-raportti"));}
    Uri androidxUri(File f){return androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".provider",f);}

    class SignatureView extends View {
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); Path path=new Path(); Bitmap saved;
        public SignatureView(Context c){super(c);paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2.2f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        protected void onDraw(Canvas c){super.onDraw(c);if(saved!=null)c.drawBitmap(saved,null,new Rect(0,0,getWidth(),getHeight()),null);c.drawPath(path,paint);}
        public boolean onTouchEvent(MotionEvent e){float x=e.getX(),y=e.getY();switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN: if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);path.moveTo(x,y);invalidate();return true;
            case MotionEvent.ACTION_MOVE: for(int i=0;i<e.getHistorySize();i++)path.lineTo(e.getHistoricalX(i),e.getHistoricalY(i));path.lineTo(x,y);invalidate();return true;
            case MotionEvent.ACTION_UP: path.lineTo(x,y);invalidate();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;
            case MotionEvent.ACTION_CANCEL: if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
        }return true;}
        @Override public boolean performClick(){super.performClick();return true;}
        void clear(){path.reset();saved=null;invalidate();}
        void setBitmap(Bitmap b){saved=transparentSignature(b);invalidate();}
        boolean isEmpty(){return path.isEmpty()&&saved==null;}
        Bitmap bitmap(){Bitmap b=Bitmap.createBitmap(Math.max(1,getWidth()),Math.max(1,getHeight()),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);if(saved!=null)c.drawBitmap(saved,null,new Rect(0,0,b.getWidth(),b.getHeight()),null);c.drawPath(path,paint);return b;}
    }
    Bitmap transparentSignature(Bitmap source){if(source==null)return null;Bitmap out=source.copy(Bitmap.Config.ARGB_8888,true);int w=out.getWidth(),h=out.getHeight();int[] px=new int[w*h];out.getPixels(px,0,w,0,0,w,h);for(int i=0;i<px.length;i++){int color=px[i],r=Color.red(color),g=Color.green(color),b=Color.blue(color);int lum=(r*299+g*587+b*114)/1000;int alpha=Math.max(0,255-lum);if(alpha<10)alpha=0;px[i]=Color.argb(alpha,0,0,0);}out.setPixels(px,0,w,0,0,w,h);return out;}
    class AnnotationView extends View {
        static final int FREE=0, ARROW=1, CIRCLE=2, RECT=3, TEXT=4;
        class Mark { int type; float x1,y1,x2,y2; String label=""; Path path=new Path(); }
        Bitmap base; Paint imagePaint=new Paint(3), markPaint=new Paint(3); ArrayList<Mark> marks=new ArrayList<>(); Mark active;
        int mode=FREE; float sx,sy,scaleFactor;
        AnnotationView(Context c,Bitmap b){super(c);base=b;markPaint.setColor(Color.RED);markPaint.setStyle(Paint.Style.STROKE);markPaint.setStrokeWidth(Math.max(7f,Math.min(base.getWidth(),base.getHeight())*0.006f));markPaint.setStrokeCap(Paint.Cap.ROUND);markPaint.setStrokeJoin(Paint.Join.ROUND);setBackgroundColor(Color.rgb(235,238,241));}
        void setMode(int m){mode=m;toast(m==FREE?"Piirtotila":m==ARROW?"Nuolityökalu":m==CIRCLE?"Ympyrätyökalu":m==RECT?"Suorakulmiotyökalu":"Tekstimerkintä");}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);if(base==null)return;float sc=Math.min((float)getWidth()/base.getWidth(),(float)getHeight()/base.getHeight());float w=base.getWidth()*sc,h=base.getHeight()*sc;float left=(getWidth()-w)/2f,top=(getHeight()-h)/2f;canvas.save();canvas.translate(left,top);canvas.scale(sc,sc);canvas.drawBitmap(base,0,0,imagePaint);for(Mark m:marks)drawMark(canvas,m);if(active!=null)drawMark(canvas,active);canvas.restore();sx=left;sy=top;scaleFactor=sc;}
        void drawMark(Canvas c,Mark m){
            if(m.type==FREE){c.drawPath(m.path,markPaint);return;}
            if(m.type==TEXT){Paint tp=new Paint(3);tp.setColor(Color.RED);tp.setTextSize(Math.max(24f,Math.min(base.getWidth(),base.getHeight())*0.035f));tp.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(m.label,m.x1,m.y1,tp);return;}
            float l=Math.min(m.x1,m.x2),r=Math.max(m.x1,m.x2),t=Math.min(m.y1,m.y2),b=Math.max(m.y1,m.y2);
            if(m.type==CIRCLE)c.drawOval(l,t,r,b,markPaint);
            else if(m.type==RECT)c.drawRect(l,t,r,b,markPaint);
            else if(m.type==ARROW){c.drawLine(m.x1,m.y1,m.x2,m.y2,markPaint);float angle=(float)Math.atan2(m.y2-m.y1,m.x2-m.x1);float len=Math.min(base.getWidth(),base.getHeight())*0.035f;float a1=angle-(float)Math.PI/6,a2=angle+(float)Math.PI/6;c.drawLine(m.x2,m.y2,m.x2-len*(float)Math.cos(a1),m.y2-len*(float)Math.sin(a1),markPaint);c.drawLine(m.x2,m.y2,m.x2-len*(float)Math.cos(a2),m.y2-len*(float)Math.sin(a2),markPaint);}
        }
        public boolean onTouchEvent(android.view.MotionEvent e){float x=(e.getX()-sx)/Math.max(0.001f,scaleFactor),y=(e.getY()-sy)/Math.max(0.001f,scaleFactor);x=Math.max(0,Math.min(base.getWidth(),x));y=Math.max(0,Math.min(base.getHeight(),y));if(e.getAction()==MotionEvent.ACTION_DOWN){active=new Mark();active.type=mode;active.x1=x;active.y1=y;active.x2=x;active.y2=y;if(mode==FREE)active.path.moveTo(x,y);invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE&&active!=null){active.x2=x;active.y2=y;if(mode==FREE)active.path.lineTo(x,y);invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_UP&&active!=null){active.x2=x;active.y2=y;if(mode==FREE)active.path.lineTo(x,y);if(mode==TEXT){Mark target=active;EditText input=new EditText(MainActivity.this);input.setHint("Teksti kuvaan");new AlertDialog.Builder(MainActivity.this).setTitle("Tekstimerkintä").setView(input).setNegativeButton("Peruuta",(d,w)->{}).setPositiveButton("Lisää",(d,w)->{target.label=input.getText().toString().trim();if(!target.label.isEmpty())marks.add(target);invalidate();}).show();}else marks.add(active);active=null;invalidate();return true;}return true;}
        void clearMarks(){marks.clear();active=null;invalidate();}
        Bitmap result(){Bitmap out=base.copy(Bitmap.Config.ARGB_8888,true);Canvas c=new Canvas(out);for(Mark m:marks)drawMark(c,m);return out;}
    }

}
