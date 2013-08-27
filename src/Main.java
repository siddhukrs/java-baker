import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.lang.StringEscapeUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.neo4j.graphdb.Transaction;



import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


class Main
{
	public static void main(String args[]) throws IOException, NullPointerException, ClassNotFoundException, DocumentException, ParseException, SQLException
	{
		String input_oracle = "/home/s23subra/workspace/model-generator/maven-graph-database/";

		/*String input_snippet="sample_3.txt";
		Parser parser=new Parser(input_oracle);
		parser.setInputFile(input_snippet);
		CompilationUnit cu=parser.getCompilationUnitFromFile();
		int cutype=parser.getCuType();
		GraphDatabase db = parser.getGraph();
		MyNewASTVisitor visitor=new MyNewASTVisitor(db,cu,cutype);
		cu.accept(visitor);
		System.out.println(visitor.printJson());*/

		Parser parser=new Parser(input_oracle);
		Connection connection = getDatabase("/home/s23subra/workspace/Java Snippet Parser/javadb.db");
		Element root = getCodeXML("/home/s23subra/workspace/stackoverflow/java_codes.xml");
		iterate(root, connection, parser);
	}

	private static void iterate(Element root, Connection connection, Parser parser) throws NullPointerException, IOException, ClassNotFoundException, ParseException, SQLException 
	{
		int cnt=0;
		GraphDatabase db = parser.getGraph();
		//Transaction tx0 = db.graphDb.beginTx();
		//try
		//{
			for ( Iterator i = root.elementIterator(); i.hasNext(); ) 
			{
				cnt++;
				Element post = (Element) i.next();
				//211, 
				Statement statement = connection.createStatement();
				if(cnt>606)
				{	
					String qid=post.attributeValue("qid");
					String aid=post.attributeValue("aid");
					String code = post.element("code").getText();
					String codeid = post.element("code").attributeValue("id");
					System.out.println(cnt+":"+qid+":"+aid+":"+codeid);
					String initcode = code;
					code = code.replace("&lt;", "<");
					code = code.replace("&gt;", ">");
					code = code.replace("&amp;", "&");
					code = code.replace("&quot;", "\"");

					final CompilationUnit cu=parser.getCompilationUnitFromString(code);
					int cutype = parser.getCuType();
					if(aid!=null && qid!=null && codeid!=null && initcode!=null)
					{
						initcode = StringEscapeUtils.escapeSql(initcode);
						String other_query = "insert into map values('"+aid+"','"+qid+"','"+codeid+"','"+initcode+"','"+cutype+"')";
						System.out.println(other_query);
						statement.executeUpdate(other_query);
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
							JSONObject result = future.get(120, TimeUnit.SECONDS); 
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
		String precision = obj.getString("precision");
		String line_no = obj.getString("line_number");
		String type = obj.getString("type");
		String character = obj.getString("character");
		ArrayList<String> elements = (ArrayList<String>) obj.get("elements");
		if(elements.size() < 30)
			for(int k =0; k< elements.size(); k++)
			{
				String element = (String) elements.get(k);
				//System.out.println(element);
				String query = null;
				if(type.equals("api_type"))
					query="insert into types values('"+aid+"','"+codeid+"','"+element+"','"+character+"','"+line_no+"','"+precision+"')";
				else if(type.equals("api_method"))
					query="insert into methods values('"+aid+"','"+codeid+"','"+element+"','"+character+"','"+line_no+"','"+precision+"')";
				//System.out.println(query);
				statement.executeUpdate(query);
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