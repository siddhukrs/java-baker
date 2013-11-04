import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.lang.StringEscapeUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.json.JSONArray;
import org.json.JSONObject;



import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class Main
{
	
	
	public static void main(String args[]) throws IOException, NullPointerException, ClassNotFoundException, DocumentException, ParseException, SQLException
	{
		long start = System.nanoTime();
		
		String input_oracle = "/home/s23subra/workspace/model-generator/maven-graph-database/";
		String input_snippet = "sample.txt";
		Parser parser = new Parser(input_oracle);
		parser.setInputFile(input_snippet);
		CompilationUnit cu = parser.getCompilationUnitFromFile();
		int cutype=parser.getCuType();
		GraphDatabase db = parser.getGraph();
		
		FirstASTVisitor visitor1 = new FirstASTVisitor(db,cu,cutype);
		cu.accept(visitor1);
		System.out.println(visitor1.printJson().toString(3));
		visitor1.printFields();
		
		SubsequentASTVisitor visitor2 = new SubsequentASTVisitor(visitor1);
		cu.accept(visitor2);
		System.out.println(visitor2.printJson().toString(3));
		visitor2.printFields();
		
		SubsequentASTVisitor visitor3 = new SubsequentASTVisitor(visitor2);
		cu.accept(visitor3);
		System.out.println(visitor3.printJson().toString(3));
		visitor3.printFields();
		
		SubsequentASTVisitor prev = visitor2;
		SubsequentASTVisitor curr = visitor3;
		
		while(compareMaps(curr, prev) == false)
		{
			SubsequentASTVisitor visitor4 = new SubsequentASTVisitor(curr);
			cu.accept(visitor4);
			System.out.println(visitor4.printJson().toString(3));
			visitor4.printFields();
			prev = curr;
			curr = visitor4;
			
		}
		
		
		
		
		
		
		
		long end = System.nanoTime();
		System.out.println("Total Time" + " - " + String.valueOf((double)(end-start)/(1000000000)));
		/*Parser parser=new Parser(input_oracle);
		Connection connection = getDatabase("/home/s23subra/workspace/Java Snippet Parser/javadb.db");
		Element root = getCodeXML("/home/s23subra/workspace/stackoverflow/java_codes_tags.xml");
		iterate(root, connection, parser);*/
	}
	
	
	

	private static boolean compareMaps(SubsequentASTVisitor curr, SubsequentASTVisitor prev) 
	{
		if(curr.variableTypeMap.equals(prev.variableTypeMap) && 
				curr.methodReturnTypesMap.equals(prev.methodReturnTypesMap) &&
				curr.printtypes.equals(prev.printtypes) &&
				curr.printmethods.equals(prev.printmethods) &&
				curr.printTypesMap.equals(prev.printTypesMap) &&
				curr.printMethodsMap.equals(prev.printMethodsMap))
			return true;
		else
			return false;
	}




	public static void iterate(Element root, Connection connection, Parser parser) throws NullPointerException, IOException, ClassNotFoundException, ParseException, SQLException  
	{
		TreeSet<String> lru = new TreeSet<String>();
		//lru.add("gwt");
		lru.add("apache");
		//lru.add("jodatime");
		//lru.add("xstream");
		//lru.add("httpclient");
		
		
		int cnt=0;
		GraphDatabase db = parser.getGraph();
		int lruCounter = 0;
		//Transaction tx0 = db.graphDb.beginTx();
		//try
		//{
		//int finished = 559;
		int finished = 0;
		TreeSet<String> alreadyParsed = new TreeSet<String>();
		BufferedReader br = new BufferedReader(new FileReader("/home/s23subra/workspace/Java Snippet Parser/alreadyInDb.txt"));
		String line = null;
		while((line = br.readLine())!=null)
		{
			alreadyParsed.add(line.trim());
		}
		
			for ( Iterator i = root.elementIterator(); i.hasNext(); ) 
			{
				
				cnt++;
				Element post = (Element) i.next();
				//211, 606, 1354, 2074, 2812, 3222,3934, 5138, 6245, 6266, 6320, 6326, 6412, 6441, 6838, 7566, 7622, 8320, 9240, 10208, 11134
				//Exception in thread "main" java.lang.OutOfMemoryError: GC overhead limit exceeded
				//274-102
				Statement statement = connection.createStatement();
				//if(cnt>8000)
				if(cnt>38644)
				{
					String qid=post.attributeValue("qid");
					String aid=post.attributeValue("aid");
					if(alreadyParsed.contains(aid)==true)
					{
						//System.out.println("In DB");
					}
					else
					{
						String tagString=post.attributeValue("tags");
						//System.out.println(tagString);
						String[] tags = tagString.split("\\|");
						String code = post.element("code").getText();
						String codeid = post.element("code").attributeValue("id");
						
						String initcode = code;
						code = code.replace("&lt;", "<");
						code = code.replace("&gt;", ">");
						code = code.replace("&amp;", "&");
						code = code.replace("&quot;", "\"");
						String code1= code;
						int breakFlag = 0;
						int tagCount = 0;
						int matchCount =0;
						for(String tag : tags)
						{
							tagCount++;
							if(lru.contains(tag))
							{
								matchCount++;
							}
						}
						//if(matchCount<1 || code1.toLowerCase().contains("eclipse")==false &&  code1.toLowerCase().contains("gwt") == false))
						if(matchCount<1 )
						{
							//System.out.println("matchcount not exceeded");
						}
						else
						{
							final CompilationUnit cu=parser.getCompilationUnitFromString(code);
							int cutype = parser.getCuType();
							if(aid!=null && qid!=null && codeid!=null && initcode!=null)
							{
								initcode = StringEscapeUtils.escapeSql(initcode);
								String other_query1 = "delete from map where aid = '"+aid+"'";
								
								String other_query2 = "insert into map values('"+aid+"','"+qid+"','"+codeid+"','"+initcode+"','"+cutype+"')";
								System.out.println(other_query2);
								try{
								statement.executeUpdate(other_query1);
								statement.executeUpdate(other_query2);
									/*
									 * 10490951 //try for joda
									10721084
									8032357
									8045171
									8109868
									8345323
									6458141
									 */
								}
								catch(Exception e)
								{
									
								}
							}
							JSONObject op = null;
							if(cu != null)
							{
								final MyNewASTVisitor visitor=new MyNewASTVisitor(db,cu,cutype);
								/*cu.accept(visitor);
							visitor.printJson();*/
								ExecutorService executor = Executors.newCachedThreadPool();
								Callable<JSONObject> task = new Callable<JSONObject>() {
									public JSONObject call() 
									{
										cu.accept(visitor);
										return visitor.printJson();
									}
								};
								Future<JSONObject> future = executor.submit(task);
								try 
								{
									JSONObject result = future.get(90, TimeUnit.SECONDS); 
									op = result;
									
								} 
								catch (TimeoutException ex)
								{
									//op = "{\"api_elements\": [{ \"precision\": \"\",\"name\": \"\",\"line_number\": \"\",\"type\": \"\",\"elements\": \"\"}]}";
								} 
								catch (InterruptedException e) 
								{
								} 
								catch (ExecutionException e) 
								{
								} 
							}
							if(op!=null)
							{
								//System.out.println(op.toString(3));;
								finished++;
								String q1 = "delete from types where aid = '"+aid+"'";
								String q2 = "delete from methods where aid = '"+aid+"'";
								statement.executeUpdate(q1);
								statement.executeUpdate(q2);
								if (op.get("api_elements") instanceof JSONObject)
								{
									JSONObject apielements = op.getJSONObject("api_elements");
									insert(apielements, statement, aid, qid, codeid, code, Integer.toString(cutype));
								}
								else if (op.get("api_elements") instanceof JSONArray)
								{
									JSONArray apielements = op.getJSONArray("api_elements");
									for(int j=0; j < apielements.length(); j++)
									{
										JSONObject obj = (JSONObject) apielements.get(j);
										insert(obj, statement, aid, qid, codeid, code, Integer.toString(cutype));
									}
								}
							}
							
							for(int p=0; p<tags.length;p++)
							{
								System.out.println(tags[p]);
								if(tags[p].equals("java")==false)
								{
									//lru.add(tags[p]);
								}
							}
							if(lruCounter<10)
								lruCounter=0;
							else
							{
								lruCounter=0;
								//lru = new TreeSet<String>();
							}
							System.out.println(cnt+ ":"+ finished + ":"+qid+":"+aid+":"+codeid);
						}
						
					}
				}
			}
			//tx0.success();
		//}
		//finally
		//{
		//	tx0.finish();
		//}
	}

	private static void insert(JSONObject obj, Statement statement, String aid, String qid, String codeid, String code, String cutype) throws SQLException 
	{
		
		String line_no = obj.getString("line_number");
		String type = obj.getString("type");
		String character = obj.getString("character");
		ArrayList<String> elements = (ArrayList<String>) obj.get("elements");
		
		if(elements.size() < 30)
		{
			TreeSet<String> elements2 = new TreeSet<String>();
			for(int k =0; k< elements.size(); k++)
			{
				String element = elements.get(k);
				/*if(element.indexOf("com.google.appengine.repackaged.")!=-1 || 
						element.indexOf("com.ning.metrics.serialization")!=-1 || 
						element.indexOf("com.ning.metrics.eventtracker")!=-1 || 
						element.indexOf("jruby.joda.")!=-1 || 
						element.indexOf("org.elasticsearch.common")!=-1 || 
						element.indexOf("com.proofpoint.hive")!=-1 || 
						element.indexOf("clover.cenqua_com_licensing")!=-1 ||
						element.indexOf("com.ning.metrics.eventtracker")!=-1)*/
				/*if(element.indexOf("br.com.caelum.vraptor")!=-1 || 
						element.indexOf("com.cedarsoft")!=-1 || 
						element.indexOf("com.cloudbees")!=-1 || 
						element.indexOf("com.ovea.jetty")!=-1 || 
						element.indexOf("com.wwm.attrs.internal")!=-1 || 
						element.indexOf("cucumber.runtime")!=-1 || 
						element.indexOf("edu.internet2")!=-1 ||
						element.indexOf("hudson.plugins")!=-1 ||
						element.indexOf("net.incongru.taskman")!=-1 ||
						element.indexOf("no.antares")!=-1 ||
						element.indexOf("org.activemq")!=-1 ||
						element.indexOf("org.apache")!=-1 ||
						element.indexOf("org.codehaus")!=-1 ||
						element.indexOf("org.fabric3")!=-1 ||
						element.indexOf("org.jasig")!=-1 ||
						element.indexOf("org.kohsuke")!=-1 ||
						element.indexOf("org.kuali")!=-1 ||
						element.indexOf("org.mattressframework")!=-1 ||
						element.indexOf("org.nakedobjects")!=-1 ||
						element.indexOf("org.pitest")!=-1 ||
						element.indexOf("org.sca4j")!=-1 ||
						element.indexOf("org.sonatype")!=-1 ||
						element.indexOf("org.springframework")!=-1 ||
						element.indexOf("org.jboss")!=-1 ||
						element.indexOf("org.compass")!=-1 ||
						element.indexOf("org.jibx")!=-1 ||
						element.indexOf("org.dom4j")!=-1 ||
						element.indexOf("com.quigley")!=-1 ||
						element.indexOf("org.compass")!=-1 ||
						element.indexOf("de.javakaffee")!=-1)*/
				/*if(element.indexOf("ar.com")!=-1 || 
						element.indexOf("br.com")!=-1 || 
						element.indexOf("br.net")!=-1 || 
						element.indexOf("ca.odell")!=-1 || 
						element.indexOf("cirrus.hibernate")!=-1 || 
						element.indexOf("com.atomikos")!=-1 || 
						element.indexOf("com.britesnow")!=-1 ||
						element.indexOf("com.caucho")!=-1 ||
						element.indexOf("com.discursive")!=-1 ||
						element.indexOf("com.emsoft")!=-1 ||
						element.indexOf("com.fasterxml")!=-1 ||
						element.indexOf("com.googlecode")!=-1 ||
						element.indexOf("com.fortuityframework")!=-1 ||
						element.indexOf("com.hazelcast")!=-1 ||
						element.indexOf("com.ibatis")!=-1 ||
						element.indexOf("com.javaforge")!=-1 ||
						element.indexOf("com.katesoft")!=-1 ||
						element.indexOf("com.liferay")!=-1 ||
						element.indexOf("com.mysema")!=-1 ||
						element.indexOf("com.opensymphony")!=-1 ||
						element.indexOf("com.portlandwebworks")!=-1 ||
						element.indexOf("xdoclet.modules")!=-1 ||
						element.indexOf("woko.hibernate")!=-1 ||
						element.indexOf("wicket.contrib")!=-1 ||
						element.indexOf("pl.net")!=-1 ||
						element.indexOf("org.xdoclet")!=-1 ||
						element.indexOf("org.jvnet")!=-1 ||
						element.indexOf("org.jboss")!=-1 ||
						element.indexOf("org.hibernatespatial")!=-1 ||
						element.indexOf("org.granite")!=-1 ||
						element.indexOf("org.geomajas")!=-1 ||
						element.indexOf("org.exoplatform")!=-1 ||
					    element.indexOf("org.duineframework")!=-1 ||
						element.indexOf("org.domdrides")!=-1 ||
						element.indexOf("org.controlhaus")!=-1 ||
						element.indexOf("org.codehaus")!=-1 ||
						element.indexOf("org.apache")!=-1 ||
						element.indexOf("net.sourceforge")!=-1 ||
						element.indexOf("net.sf")!=-1 ||
						element.indexOf("net.incongru")!=-1 ||
						element.indexOf("it.")!=-1 ||
						element.indexOf("org.grails")!=-1 ||
						element.indexOf("com.jpattern")!=-1 ||
						element.indexOf("org.javaclub")!=-1 ||
						element.indexOf("ru.")!=-1 ||	
						element.indexOf("org.compass")!=-1)*/
				/*if(element.indexOf("bibliothek.extension")!=-1 || 
						element.indexOf("com.eclipsesource")!=-1 || 
						element.indexOf("com.google")!=-1 || 
						element.indexOf("com.jidesoft")!=-1 || 
						element.indexOf("com.sonatype")!=-1 || 
						element.indexOf("copied.org")!=-1 || 
						element.indexOf("de.abstrakt")!=-1 ||
						element.indexOf("fr.opensagres")!=-1 ||
						element.indexOf("gate.util")!=-1 ||
						element.indexOf("lombok.eclipse")!=-1 ||
						element.indexOf("net.bpelunit")!=-1 ||
						element.indexOf("ma.glasnost")!=-1 ||
						element.indexOf("net.officefloor")!=-1 ||
						element.indexOf("net.sf")!=-1 ||
						element.indexOf("org.apache")!=-1 ||
						element.indexOf("org.aspectj")!=-1 ||
						element.indexOf("org.eclipselab")!=-1 ||
						element.indexOf("org.maven")!=-1 ||
						element.indexOf("org.openl")!=-1 ||
						element.indexOf("org.sonatype")!=-1 ||
						element.indexOf("org.wamblee")!=-1 ||
						element.indexOf("org.compass")!=-1)*/
				if(false)
					{System.out.println("came hetre");}
				/*if(element.indexOf("ar.com")!=-1 || 
						element.indexOf("br.com")!=-1 || 
						element.indexOf("br.net")!=-1 || 
						element.indexOf("ca.odell")!=-1 || 
						element.indexOf("cirrus.hibernate")!=-1 || 
						element.indexOf("com.atomikos")!=-1 || 
						element.indexOf("com.britesnow")!=-1 ||
						element.indexOf("com.caucho")!=-1 ||
						element.indexOf("com.discursive")!=-1 ||
						element.indexOf("com.emsoft")!=-1 ||
						element.indexOf("com.fasterxml")!=-1 ||
						element.indexOf("com.googlecode")!=-1 ||
						element.indexOf("com.fortuityframework")!=-1 ||
						element.indexOf("com.hazelcast")!=-1 ||
						element.indexOf("com.ibatis")!=-1 ||
						element.indexOf("com.javaforge")!=-1 ||
						element.indexOf("com.katesoft")!=-1 ||
						element.indexOf("com.liferay")!=-1 ||
						element.indexOf("com.mysema")!=-1 ||
						element.indexOf("com.opensymphony")!=-1 ||
						element.indexOf("com.portlandwebworks")!=-1 ||
						element.indexOf("xdoclet.modules")!=-1 ||
						element.indexOf("woko.hibernate")!=-1 ||
						element.indexOf("org.sonatype")!=-1 ||
						element.indexOf("org.robotframework")!=-1 ||
						element.indexOf("org.restlet")!=-1 ||
						element.indexOf("org.jets3t")!=-1 ||
						element.indexOf("org.carrot2")!=-1 ||
						element.indexOf("org.apache.maven")!=-1 ||
						element.indexOf("net.sourceforge")!=-1 ||
						element.indexOf("net.sf")!=-1 ||
						element.indexOf("jec.")!=-1 ||
					    element.indexOf("hidden.org")!=-1 ||
						element.indexOf("flex.messaging")!=-1 ||
						element.indexOf("edu.internet2")!=-1 ||
						element.indexOf("com.yammer")!=-1 ||
						element.indexOf("com.torunski")!=-1 ||
						element.indexOf("com.sirika")!=-1 ||
						element.indexOf("com.reevoo")!=-1 ||
						element.indexOf("com.payneteasy")!=-1 ||
						element.indexOf("com.openshift")!=-1 ||
						element.indexOf("com.google")!=-1 ||
						element.indexOf("com.github")!=-1 ||
						element.indexOf("com.discursive")!=-1 ||
						element.indexOf("au.net.")!=-1 ||	
						element.indexOf("ar.com")!=-1)	*/
				/*else if(element.indexOf("com.allen")!=-1 || 
					element.indexOf("com.dasberg")!=-1 || 
					element.indexOf("com.denormans")!=-1 || 
					element.indexOf("com.flipthebird")!=-1 || 
					element.indexOf("com.google.code")!=-1 || 
					element.indexOf("com.googlecode")!=-1 || 
					element.indexOf("com.gwtext")!=-1 ||
					element.indexOf("com.gwtplatform")!=-1 ||
					element.indexOf("com.johnsoncs")!=-1 ||
					element.indexOf("com.jpattern")!=-1 ||
					element.indexOf("com.sencha")!=-1 ||
					element.indexOf("com.smartgwt")!=-1 ||
					element.indexOf("com.threerings")!=-1 ||
					element.indexOf("com.vaadin")!=-1 ||
					element.indexOf("gwtupload.")!=-1 ||
					element.indexOf("net.customware")!=-1 ||
					element.indexOf("org.atmosphere")!=-1 ||
					element.indexOf("org.codehaus")!=-1 ||
					element.indexOf("org.eclipse")!=-1 ||
					element.indexOf("org.fusesource.")!=-1 ||
					element.indexOf("org.geomajas")!=-1 ||
					element.indexOf("org.gwtmpv")!=-1 ||
					element.indexOf("org.gwtopenmaps.")!=-1 ||
					element.indexOf("org.gwtwidgets")!=-1 ||
					element.indexOf("org.hudsonci")!=-1 ||
					element.indexOf("org.jboss.")!=-1 ||
					element.indexOf("org.kuali.")!=-1 ||
					element.indexOf("org.ocpsoft")!=-1 ||
					element.indexOf("org.opencms.")!=-1 ||
					element.indexOf("org.ow2")!=-1 ||
					element.indexOf("org.sonar")!=-1 ||
					element.indexOf("org.urish")!=-1 ||
				    element.indexOf("org.xcmis")!=-1 ||
					element.indexOf("redora.configuration")!=-1 ||
					element.indexOf("se.spagettikod")!=-1)
				{ System.out.println("came hetre");}*/
				else
					elements2.add(element);
			}
			//elements2 =  new ArrayList<String>(elements);
			int precision = elements2.size();
			for(String element : elements2)
			{
				//String element;
				//System.out.println(element);
				String query = null;
				if(type.equals("api_type"))
				{
					query="insert into types values('"+aid+"','"+codeid+"','"+element+"','"+character+"','"+line_no+"','"+Integer.toString(precision)+"')";
					//if(precision.equals("1"))
						System.out.println(query);
				}
				else if(type.equals("api_method"))
				{
					query="insert into methods values('"+aid+"','"+codeid+"','"+element+"','"+character+"','"+line_no+"','"+Integer.toString(precision)+"')";
					//if(precision.equals("1"))
						System.out.println(query);
				}
				//System.out.println(query);
				statement.executeUpdate(query);
			}
		}
	}

	private static Element getCodeXML(String fname) throws FileNotFoundException, DocumentException
	{
		FileInputStream fis = new FileInputStream(fname);
		DataInputStream in = new DataInputStream(fis);

		SAXReader reader = new SAXReader();
		Document document = reader.read(in);
		Element root = document.getRootElement();
		return root;
	}

	private static Connection getDatabase(String fname) throws ClassNotFoundException
	{
		try
		{
			Class.forName("org.sqlite.JDBC");
			Connection connection = null;
			connection = DriverManager.getConnection("jdbc:sqlite:"+fname);
			Statement statement = connection.createStatement();
			statement.setQueryTimeout(30);
			/*statement.executeUpdate("drop table if exists types");
			statement.executeUpdate("drop table if exists methods");
			statement.executeUpdate("drop table if exists map");
			statement.executeUpdate("create table types (aid string, codeid int, tname string, charat int, line int, prob int)");
			statement.executeUpdate("create table methods (aid string, codeid int, mname string, charat int, line int, prob int)");
			statement.executeUpdate("create table map (aid string, qid string, codeid int, code string, cutype int, PRIMARY KEY (aid, qid, codeid))");
			 */			return connection;
		}
		catch(SQLException e)
		{
			System.err.println(e.getMessage());
			return null;
		}
	}
}

//awk '{printf "%d\t%s\n", NR, $0}' < sample.txt >> print.txt
//shade.org.apache.http.params.CoreConnectionPNames