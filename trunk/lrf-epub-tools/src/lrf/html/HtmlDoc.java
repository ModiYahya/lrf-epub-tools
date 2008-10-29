package lrf.html;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import lrf.conv.BaseRenderer;
import lrf.epub.EPUBEntity;
import lrf.epub.EPUBMetaData;
import lrf.objects.tags.Tag;

import com.lowagie.text.Image;
import com.sun.jmx.snmp.Enumerated;

public class HtmlDoc implements EPUBEntity{
	public String title,fNam,auth,id,producer;
	public File tmp;
	public int numIm=0;
	public Hashtable<Integer,String> imagenes=new Hashtable<Integer,String>();
	ByteArrayOutputStream bos;
	PrintWriter pw;
	Vector<String> emits=new Vector<String>();
	
	boolean isDivOpen=false;
	
	HtmlStyle currentStyle=new HtmlStyle(new Vector<Tag>());
	HtmlStyle bookStyle,pageStyle,blockStyle;
	HtmlStyle initStyle=new HtmlStyle(new Vector<Tag>());
	
	public static final int es_book=0;
	public static final int es_page=1;
	public static final int es_block=2;
	public static final int es_text=3;
	public static final int es_curr=4;
	
	public void setEstilo(int hob, HtmlStyle estilo){
		switch(hob){
		case es_book:
			currentStyle=initStyle;
			changeStyle(estilo);
			bookStyle=estilo;
			break;
		case es_page:
			currentStyle=bookStyle;
			changeStyle(estilo);
			pageStyle=estilo;
			break;
		case es_block:
			currentStyle=pageStyle;
			changeStyle(estilo);
			blockStyle=estilo;
			break;
		case es_text:
			currentStyle=blockStyle;
			changeStyle(estilo);
			break;
		}
	}

	public void emitText(String txt){
		if(!isDivOpen)
			openDiv();
		emits.add(Emitter.spanOpen(currentStyle));
		if(anchorAntes!=null){
			emits.add(anchorAntes);
			anchorAntes=null;
		}
		emits.add(txt);
		if(anchorDespues!=null){
			emits.add(anchorDespues);
			anchorDespues=null;
		}
		emits.add(Emitter.spanClose());
	}
	
	public String anchorAntes=null;
	public String anchorDespues=null;
	public void setTemporaryStyle(StyleItem si){
		if(si.getLevel()==StyleItem.st_proc){
			if(si.getPropName().equals("BeginButton")){
				anchorAntes=Emitter.anchorOrig(si.value,true);
			}
			if(si.getPropName().equals("EndButton")){
				anchorDespues=Emitter.anchorOrig(null,false);
			}
		}else{
			changeStyle(new HtmlStyle(si));
		}
	}
	
	private void changeStyle(HtmlStyle estilo) {
		HtmlStyle diff=currentStyle.newStyles(estilo, StyleItem.st_body);
		if(diff.getNumProps()>0){
			//Generar Body
			closeDiv();
			currentStyle.overrideWith(diff);
			emits.add(Emitter.body(currentStyle));
		}
		diff=currentStyle.newStyles(estilo, StyleItem.st_div);
		if(diff.getNumProps()>0){
			closeDiv();
			currentStyle.overrideWith(diff);
			openDiv();
		}
		diff=currentStyle.newStyles(estilo, StyleItem.st_span);
		if(diff.getNumProps()>0){
			currentStyle.overrideWith(diff);
		}
	}
	
	public void openDiv(){
		emits.add(Emitter.divOpen(currentStyle));
		isDivOpen=true;
	}
	
	public void closeDiv(){
		if(!isDivOpen)
			return;
		emits.add(Emitter.divClose());
		isDivOpen=false;
	}
	
	public void newParagraph(){
		closeDiv();
		openDiv();
	}
	
	public void emitAnchorDest(String anchorName){
		emits.add(Emitter.anchorDest(anchorName));
	}
	
	public HtmlDoc(String filename, String title, String auth, String producer, String id, File tmp){
		this.fNam=filename;
		this.title=title;
		this.auth=auth;
		this.producer=producer;
		this.id=id;
		this.tmp=tmp;
		bos=new ByteArrayOutputStream();
		pw=new PrintWriter(bos);
		emits.add(Emitter.head(auth, id, title, fNam+".css")+"\n");
	}

	public void addImage(int id, Image im, String ext, byte[] b)
			throws Exception {
		String imgfn=imagenes.get(id);
		if(imgfn==null){
			numIm++;
			imgfn=fNam+numIm+ext;
			FileOutputStream fosi=new FileOutputStream(new File(tmp,""+numIm));
			fosi.write(b);
			fosi.close();
			imagenes.put(id,imgfn);
		}
		closeDiv();
		emits.add(
				Emitter.img(
						imgfn,
						imgfn.substring(0,imgfn.length()-ext.length()),
						""+im.getWidth(),""+im.getHeight())+"\n");
	}
	
	public Vector<String> getImagenes(){
		Vector<String>ret=new Vector<String>();
		for(Enumeration<String>enu=imagenes.elements();enu.hasMoreElements();){
			ret.add(enu.nextElement());
		}
		return ret;
	}
	
	public void createEPUB(EPUBMetaData e, String catpar) {
		int sz=emits.size();
		String divAnterior="1",divActual="2";
		String spanAnterior="",spanActual="";
		boolean spanAnteriorEOP=false;
		int spanAnteriorNdx=-1;
		for(int i=0;i<sz;i++){
			String base=emits.elementAt(i);
			boolean isText=!base.startsWith("<");
			//Concatenar parrafos
			if(catpar!=null && isText){
				if(divAnterior.equals(divActual) && 
				   spanAnterior.equals(spanActual) &&
				   !spanActual.contains("center") &&
				   !spanAnteriorEOP &&
				   !BaseRenderer.isBeginOfParagraph(base)){
					//No hay cambio de formato y no parece que se terminase el p�rrafo
					String oldText=emits.elementAt(spanAnteriorNdx);
					emits.set(spanAnteriorNdx, oldText+catpar+base);
					emits.set(i, "");
					spanAnteriorEOP=BaseRenderer.isEndOfParagraph(base);
					continue; //No comprobar div, span o text
				}
			}
			if(base.startsWith("<div")){
				divAnterior=divActual;
				divActual=base;
			}
			if(base.startsWith("<span")){
				spanAnterior=spanActual;
				spanActual=base;
			}
			if(!base.startsWith("<")){
				spanAnteriorEOP=BaseRenderer.isEndOfParagraph(base);
				spanAnteriorNdx=i;
			}
		}
		for(int i=0;i<sz;i++){
			pw.print(emits.elementAt(i));
		}
		pw.flush();
		try {
			//Volcamos primero el xhtml
			File fnh=new File(tmp,fNam+".html");
			FileOutputStream fos=new FileOutputStream(fnh);
			bos.writeTo(fos);
			fos.close();
			//Esto es innecesario
			//e.addFile(fileName+".html", fnh, 5);
			//Las imagenes
			int i=0;
			for(Enumeration<String> enu=imagenes.elements();enu.hasMoreElements();){
				String imgfn=enu.nextElement();
				e.processFile(
						new FileInputStream(new File(tmp,""+(++i))), 
						"images/"+imgfn);
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	public File getHTMLFile(){
		return new File(tmp,fNam+".html");
	}
}