import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.ASTVisitor;


import com.google.common.collect.HashMultimap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.json.JSONObject;
import org.neo4j.graphdb.Node;


class MyNewASTVisitor extends ASTVisitor{
	
	private GraphDatabase model;
	private CompilationUnit cu;
	private int cutype;
	private HashMultimap<String, Node> globalmethods=HashMultimap.create();//holds method return types for chains
	private HashMultimap<String, Node> globaltypes=HashMultimap.create();//holds variables, fields and method param types
	private HashMultimap<Integer, Node> printtypes=HashMultimap.create();//holds node start loc and possible types
	private HashMultimap<Integer, Node> printmethods=HashMultimap.create();//holds node start posns and possible methods they can be
	private HashMap<String, Integer> printTypesMap=new HashMap<String, Integer>();//maps node start loc to variable names
	private HashMap<String, Integer> printMethodsMap=new HashMap<String, Integer>();//holds node start locs with method names
	private HashMultimap<Integer, Integer> affectedTypes = HashMultimap.create();//holds node start locs with list of start locs they influence
	private HashMultimap<Integer, Integer> affectedMethods = HashMultimap.create();//holds node start locs with list of start locs they influence

	private String classname = null;
	private String superclassname=null;
	private ArrayList<Object> interfaces=new ArrayList<Object>();
	

	
	public MyNewASTVisitor(GraphDatabase db, CompilationUnit cu, int cutype) 
	{
		this.model=db;
		this.cu=cu;
		this.cutype=cutype;
		//db.test();
	}
	
	public void printFields()
	{
		System.out.println("globalmethods"+globalmethods);
		System.out.println("globaltypes"+globaltypes);
		System.out.println("printtypes"+printtypes);
		System.out.println("printmethods"+printmethods);
		System.out.println("printTypesMap"+printTypesMap);
		System.out.println("printMethodsMap"+printMethodsMap);
		System.out.println("affectedTypes"+affectedTypes);
		System.out.println("affectedMethods"+affectedMethods);
	}
	
	public void endVisit(VariableDeclarationStatement node)
	{
		//System.out.println("visiting");
		for(int j=0;j<node.fragments().size();j++)
		{
			//System.out.println(node.getType().toString());
			Collection<Node> celist=model.getCandidateClassNodes(node.getType().toString());
			for(Node ce : celist)
			{
				//System.out.println(ce.getProperty("id"));
				if(ce!=null)
				{
					globaltypes.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), ce);
					printtypes.put(node.getType().getStartPosition(), ce);
					printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
				}
			}
		}
	}
	
	public void endVisit(FieldDeclaration node) 
	{
		for(int j=0;j<node.fragments().size();j++)
		{	
			Collection<Node> types=new HashSet<Node>();	
			if(node.getType().getNodeType()==74)
			{	Collection<Node> celist=model.getCandidateClassNodes(((ParameterizedType)node.getType()).getType().toString());
			for(Node ce : celist)
			{
				if(ce!=null)
				{
					types.add(ce);
					globaltypes.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(),ce);
					printtypes.put(node.getType().getStartPosition(), ce);
					printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
				}
			}
			}
			else
			{	
			Collection<Node> celist=model.getCandidateClassNodes(node.getType().toString());
			for(Node ce : celist)
			{
				if(ce!=null){
					types.add(ce);
					globaltypes.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), ce);
					printtypes.put(node.getType().getStartPosition(),ce);
					printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
				}
			}
			}
		}
	}

	public void endVisit(MethodInvocation node)
	{
		Expression e=node.getExpression();
		if(e==null)
		{
			if(superclassname!=null)
			{	System.out.println("###"+node.getName().toString()+"  1.1");
				/*
				 * Handles inheritance, where methods from Superclasses can be directly called
				 */
				Collection<Node> celist=model.getCandidateClassNodes(superclassname);
				for(Node ce : celist)
				{
					Collection<Node> melist=model.getMethodNodes(ce);
					for(Node me : melist)
					{
						if(((String)me.getProperty("exactName")).equals(node.getName().toString()))
						{
							if(model.getMethodParams(me).size()==node.arguments().size())
							{
								if(matchParams(me, node.arguments())==true)
								{
									printtypes.put(node.getStartPosition(),ce);
									printmethods.put(node.getStartPosition(), me);
									if(model.getMethodReturn(me)!=null)
										globalmethods.put(node.getName().toString(),model.getMethodReturn(me));
								}
							}
						}
					}
				}
			}
			else
			{
				System.out.println("###"+node.getName().toString()+"  1.2");
				/*
				 * Might be user declared helper functions or maybe object reference is assumed to be obvious in the snippet
				 */
				Collection<Node> melist=model.getCandidateMethodNodes(node.getName().toString());
				for(Node me : melist)
				{
					if(model.getMethodParams(me).size()==node.arguments().size())
					{
						if(matchParams(me, node.arguments())==true)
						{
						printtypes.put(node.getName().getStartPosition(), model.getMethodContainer(me));
						printmethods.put(node.getName().getStartPosition(), me);
						if(model.getMethodReturn(me)!=null)
							globalmethods.put(node.toString(),model.getMethodReturn(me));
						}
					}
				}
			}
		}
		else if(e.toString().contains("System."))
		{
			
		}
		else if(globaltypes.containsKey(e.toString()))
		{
			System.out.println("###"+node.getName().toString()+"  2.1");
			String exactname=null;
			Set<Node> methods=new HashSet<Node>();
			Set <Node> clist= new HashSet<Node>();
			Set <Node> celist=globaltypes.get(e.toString());
			printMethodsMap.put(node.toString(),node.getStartPosition());
			affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
			affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());
			for(Node ce:celist)
			{
					exactname=(String) ce.getProperty("exactName");
					Collection<Node> melist = model.getMethodNodes(ce);
					for(Node me : melist)
					{
						if(((String)me.getProperty("exactName")).equals(node.getName().toString()) && node.arguments().size()==model.getMethodParams(me).size())
						{
							if(matchParams(me, node.arguments())==true)
									methods.add(me);
						}
					}
					for(Node m : methods)
					{
							printmethods.put(node.getName().getStartPosition(), m);
							Node fcname=model.getMethodContainer(m);
							if(fcname!=null && ((String)fcname.getProperty("exactName")).equals(exactname)==true)
								clist.add(fcname);
							if(model.getMethodReturn(m)!=null)
								globalmethods.put(node.toString(), model.getMethodReturn(m));
					}
			}
			//clist has the list of class names extracted from the methodname that match the exactname
			//use that to replace previously assigned values
			if(clist.isEmpty()==false)
			{
					globaltypes.replaceValues(e.toString(),clist);
					System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
					printtypes.replaceValues(printTypesMap.get(e.toString()), clist);
					//change affected types of e.toString() too
					//System.out.println("1&&&"+clist);
					for(Integer affectedNode:affectedTypes.get(printTypesMap.get(e.toString())))
					{
						printtypes.replaceValues(affectedNode, clist);
						//System.out.println("0");
					}
					
					for(Integer affectedNode:affectedMethods.get(printTypesMap.get(e.toString())))
					{
						Collection<Node>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
						//System.out.println("1:"+affectedNode);
							printmethods.replaceValues(affectedNode, temp);
					}
					if(printtypes.containsKey(node.getExpression().getStartPosition()))
						printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
					else
						printtypes.putAll(node.getExpression().getStartPosition(), clist);
					//System.out.println(affectedTypes);
					
			}
		}
		else if(e.toString().matches("[A-Z][a-zA-Z]*"))
		{
			System.out.println("###"+node.getName().toString()+"  3.1");
			String exactname="";
			Set <Node> clist= new HashSet<Node>();
			printMethodsMap.put(node.toString(),node.getStartPosition());
			affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
			affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());
			Collection<Node> celist=model.getCandidateClassNodes(e.toString());
			for(Node ce : celist)
			{
				exactname=(String) ce.getProperty("exactName");
				Collection<Node> melist = model.getMethodNodes(ce);
				for(Node me : melist)
				{
					if(((String)me.getProperty("exactName")).equals(node.getName().toString()) && me!=null && model.getMethodParams(me).size()==node.arguments().size())
					{
						if(matchParams(me, node.arguments())==true)
						{
							printmethods.put(node.getName().getStartPosition(), me);
							clist.add(ce);
							if(model.getMethodReturn(me)!=null)
								globalmethods.put(node.toString(),model.getMethodReturn(me));
						}
					}
				}	
			}
			
			
			
			if(clist.isEmpty()==false)
			{
				/*
					//globaltypes.replaceValues(e.toString(),clist);
					//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
					//printtypes.replaceValues(printTypesMap.get(e.toString()), clist);
					//change affected types of e.toString() too
					System.out.println("1&&&"+clist);
					for(Integer affectedNode:affectedTypes.get(printTypesMap.get(e.toString())))
					{
						printtypes.replaceValues(affectedNode, clist);
						System.out.println("0");
					}
					
					for(Integer affectedNode:affectedMethods.get(printTypesMap.get(e.toString())))
					{
						Collection<MethodElement>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
						System.out.println("1:"+affectedNode);
							printmethods.replaceValues(affectedNode, temp);
					}
				*/
				if(printtypes.containsKey(node.getExpression().getStartPosition()))
					printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
				else
					printtypes.putAll(node.getExpression().getStartPosition(), clist);
					//System.out.println(affectedTypes);
					
			}
		}
		else if(globalmethods.containsKey(e.toString()))
		{
			System.out.println("###"+node.getName().toString()+"  4.1");
			String exactname="";
			Set<Node> celist=globalmethods.get(e.toString());
			Set<Node> methods=new HashSet<Node>();
			Set <Node> clist= new HashSet<Node>();
			printMethodsMap.put(node.toString(),node.getStartPosition());
			//affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
			//affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());
			for(Node ce : celist)
			{
				exactname=(String) ce.getProperty("exactName");
				Collection<Node> melist = model.getMethodNodes(ce);
				for(Node me : melist)
				{
					if(((String)me.getProperty("exactName")).equals(node.getName().toString()) && me!=null && model.getMethodParams(me).size()==node.arguments().size())
					{	
						if(matchParams(me, node.arguments())==true)
								methods.add(me);
					}
				}
				for(Node m : methods)
				{
					printmethods.put(node.getName().getStartPosition(), m);
					Node fcname=model.getMethodContainer(m);
					if(fcname!=null && ((String)fcname.getProperty("exactName")).equals(exactname)==true)
						clist.add(fcname);
					if(model.getMethodReturn(m)!=null)
						globalmethods.put(node.toString(),model.getMethodReturn(m));
				}
			}
			
			if(clist.isEmpty()==false)
			{
					globaltypes.replaceValues(e.toString(),clist);
					//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
					//printtypes.replaceValues(printTypesMap.get(e.toString()), clist);
					//change affected types of e.toString() too
					System.out.println("1&&&"+clist);
					for(Integer affectedNode:affectedTypes.get(printTypesMap.get(e.toString())))
					{
						printtypes.replaceValues(affectedNode, clist);
						//System.out.println("0");
					}
					
					for(Integer affectedNode:affectedMethods.get(printTypesMap.get(e.toString())))
					{
						Collection<Node>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
						//System.out.println("1:"+affectedNode);
							printmethods.replaceValues(affectedNode, temp);
					}
					printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
					//System.out.println(affectedTypes);
			}
		}
		else
		{
			System.out.println("###"+node.getName().toString()+"--"+e.toString()+"  5.1");
			Collection<Node> melist=model.getCandidateMethodNodes(node.getName().toString());
			Set<Node> methods=new HashSet<Node>();
			Set <Node> clist= new HashSet<Node>();
			printMethodsMap.put(node.toString(),node.getStartPosition());
			
			for(Node me : melist)
			{
				if(model.getMethodParams(me).size()==node.arguments().size())
				{	
					if(matchParams(me, node.arguments())==true)
						methods.add(me);
				}
			}
			
			for(Node m : methods)
			{
				//System.out.println(m.getProperty("id"));
				Node fcname=model.getMethodContainer(m);
				if(fcname!=null)
					clist.add(fcname);
				printmethods.put(node.getName().getStartPosition(), m);
				if(model.getMethodReturn(m)!=null)
					globalmethods.put(node.toString(),model.getMethodReturn(m));
			}

			if(clist.isEmpty()==false)
			{
				if(globaltypes.containsKey(e.toString()))
					globaltypes.replaceValues(e.toString(),clist);
				else
					globaltypes.putAll(e.toString(),clist);
				if(printTypesMap.containsKey(e.toString())==false)
				{
					printTypesMap.put(e.toString(), e.getStartPosition());
				}
					//System.out.println("!!!:"+node.getExpression().getStartPosition());
					affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
					affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());
					//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
					//printtypes.putAll(printTypesMap.get(e.toString()), clist);
					//change affected types of e.toString() too
					//System.out.println("1&&&"+clist);
					for(Integer affectedNode:affectedTypes.get(printTypesMap.get(e.toString())))
					{
						printtypes.replaceValues(affectedNode, clist);
						//System.out.println("0");
					}
					
					for(Integer affectedNode:affectedMethods.get(printTypesMap.get(e.toString())))
					{
						Collection<Node>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
						//System.out.println("1:"+affectedNode);
							printmethods.replaceValues(affectedNode, temp);
					}
					printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
					//System.out.println(affectedTypes);
			}
		}
	}

	private boolean matchParams(Node me, List<ASTNode> params) 
	{
		ArrayList<HashSet<String>> nodeArgs = new ArrayList<HashSet<String>>();
		if(params.size()==0)
		{
			return true;
		}
		TreeSet<Node>graphNodes = new TreeSet<Node>(new Comparator<Node>(){
					public int compare(Node a, Node b)
					{
						return (Integer)a.getProperty("paramIndex")-(Integer)b.getProperty("paramIndex");
					}
				});
		graphNodes = model.getMethodParams(me);
		int i=0;
		for(ASTNode param : params)
		{
			
			HashSet<String> possibleTypes = new HashSet<String>();
			if(param.getNodeType()==34)
			{
				possibleTypes.add("int");
				possibleTypes.add("byte");
				possibleTypes.add("float");
				possibleTypes.add("double");
				possibleTypes.add("long");
				possibleTypes.add("short");
			}
			else if(param.getNodeType()==9)
			{
				possibleTypes.add("boolean");
			}
			else if(param.getNodeType()==13)
			{
				possibleTypes.add("char");
			}
			else if(param.getNodeType()==27)
			{
				InfixExpression tempNode = (InfixExpression) param;
				if(tempNode.getLeftOperand().getNodeType() == 45 || tempNode.getRightOperand().getNodeType() == 45)
					possibleTypes.add("String");
				else if(tempNode.getLeftOperand().getNodeType() == 34 || tempNode.getRightOperand().getNodeType() == 34)
				{
					possibleTypes.add("int");
					possibleTypes.add("byte");
					possibleTypes.add("float");
					possibleTypes.add("double");
					possibleTypes.add("long");
					possibleTypes.add("short");
				}
				else
					possibleTypes.add("UNKNOWN");
			}
			else if(param.getNodeType()==45)
			{
				possibleTypes.add("String");
			}
			else if (param.getNodeType()==42)
			{
				if(globaltypes.containsKey(param.toString()))
				{
					Set<Node> localTypes = globaltypes.get(param.toString());
					for(Node localType : localTypes)
					{
						possibleTypes.add((String) localType.getProperty("id"));
					}
				}
				else
				{
					possibleTypes.add("UNKNOWN");
					System.out.println("UNKNOWN");
				}
			}
			else if(param.getNodeType()==32)
			{
				if(globalmethods.containsKey(param.toString()))
				{
					Set<Node> localTypes = globalmethods.get(param.toString());
					for(Node localType : localTypes)
					{
						possibleTypes.add((String) localType.getProperty("id"));
					}
				}
				else
				{
					possibleTypes.add("UNKNOWN");
					System.out.println("UNKNOWN");
				}
			}
			else if(param.getNodeType()==14)
			{
				ClassInstanceCreation tempNode = (ClassInstanceCreation) param;
				possibleTypes.add(tempNode.getType().toString());
				System.out.println("14:  "+tempNode.getType().toString());
			}
			else
			{
				possibleTypes.add("UNKNOWN");
			}
			nodeArgs.add(possibleTypes);
		}
		Iterator<Node> iter1 = graphNodes.iterator();
		Iterator<HashSet<String>> iter2 = nodeArgs.iterator();
		while(iter1.hasNext())
		{
			Node graphParam = iter1.next();
			HashSet<String> args = iter2.next();
			int flag=0;
			for(String arg : args)
			{
				if(graphParam.getProperty("exactName").equals(arg)== true || graphParam.getProperty("id").equals(arg)==true)
				{
					flag=0;
					break;
				}
				else if(arg.equals("UNKNOWN"))
				{
					flag=0;
					break;
				}
				else if(model.checkIfParentNode(graphParam, arg))
				{
					flag=0;
					break;
				}
				else
					flag=1;
			}
			if(flag==1)
				return false;
		}
		System.out.println("MATCH : "+me.getProperty("id"));
		return true;
	}

	private Collection<Node> getReplacementClassList(Set<Node> set,	Set<Node> clist) {
		Collection<Node> returnSet=new HashSet<Node>();
		for(Node me: set)
		{
			String cname=(String) model.getMethodContainer(me).getProperty("id");
			int flag=0;
			for(Node ce:clist)
			{
				if(((String)ce.getProperty("id")).equals(cname))
				{
					flag=1;
					//System.out.println("777777777777777"+me.getId());
				}
				else
				{
					//System.out.println("00000000000"+me.getId());
				}
			}
			if(flag==1)
				returnSet.add(me);
		}
		return returnSet;
	}

	public boolean visit(TypeDeclaration node)
	{
		classname=node.getName().toString();
		if(node.getSuperclassType()!=null)
		{
			if(node.getSuperclassType().getNodeType()==74)
			{
				superclassname=((ParameterizedType)node.getSuperclassType()).getType().toString();
			}
			else
			{
				superclassname=node.getSuperclassType().toString();
			}
		}
		for(Object ob:node.superInterfaceTypes())
		{	
			interfaces.add(ob);
		}
		return true;
	}

	public boolean visit(MethodDeclaration node)
	{
		@SuppressWarnings("unchecked")
		List<SingleVariableDeclaration> param=node.parameters();
		for(int i=0;i<param.size();i++)
		{
			//System.out.println("+++"+param.get(i).getType().toString());
			Collection<Node> ce=model.getCandidateClassNodes(param.get(i).getType().toString());
			for(Node c : ce)
			{
				if(c!=null)
				{
					globaltypes.put(param.get(i).getName().toString(),c);
					printtypes.put(param.get(i).getType().getStartPosition(),c);
					printTypesMap.put(param.get(i).getName().toString(), param.get(i).getType().getStartPosition());
				}
				if(ce.size()>1){
				}
			}
		}
		if(superclassname!=null)
		{
			Collection<Node> ce=model.getCandidateClassNodes(superclassname);
			for(Node c : ce)
			{
				Collection<Node> methods=model.getMethodNodes(c);
				for(Node me : methods)
				{
					if(((String)me.getProperty("exactName")).equals(node.getName()))
					{
						if(c!=null && me!=null && model.getMethodParams(me).size()==node.parameters().size())
						{
							printtypes.put(node.getStartPosition(),model.getMethodContainer(me));
							printmethods.put(node.getStartPosition(), me);
						}	
					}
				}
			}
		}
		if(!interfaces.isEmpty())
		{
			for(int i=0;i<interfaces.size();i++)
			{
				Collection<Node> ce=model.getCandidateClassNodes(interfaces.get(i).toString());
				for(Node c : ce)
				{
					Collection<Node> methods=model.getMethodNodes(c);
					for(Node me : methods)
					{
						if(((String)me.getProperty("exactName")).equals(node.getName()))
						{
							if(c!=null && me!=null && model.getMethodParams(me).size()==node.parameters().size())
							{
								printtypes.put(node.getStartPosition(),model.getMethodContainer(me));
								printmethods.put(node.getStartPosition(), me);
							}	
						}
					}
				}
			}
		}
		return true;
	}

	public void endVisit(ConstructorInvocation node)
	{	System.out.println("constructor:"+classname+"<init>");
		Collection<Node> celist=model.getCandidateClassNodes(classname);
		for(Node ce : celist)
		{
			Collection<Node>melist=model.getMethodNodes(ce);
			for(Node me:melist)
			{
			if(((String)me.getProperty("exactName")).equals("<init>") && model.getMethodParams(me).size()==node.arguments().size())
			{
				if(matchParams(me, node.arguments())==true)
				{
					printmethods.put(node.getStartPosition(), me);
					printtypes.put(node.getStartPosition(),model.getMethodContainer(me));
					if(model.getMethodReturn(me)!=null)
						globalmethods.put(node.toString(), model.getMethodReturn(me));
				}
			}
			}
		}
	}

	public void endVisit(SuperConstructorInvocation node)
	{	
		Collection<Node> celist=model.getCandidateClassNodes(superclassname);
		for(Node ce : celist)
		{
			Collection<Node>melist=model.getMethodNodes(ce);
			for(Node me:melist)
			{
				if(((String)me.getProperty("exactName")).equals("<init>") && model.getMethodParams(me).size()==node.arguments().size())
				{
					if(matchParams(me, node.arguments())==true)
					{
						printmethods.put(node.getStartPosition(),me);
						printtypes.put(node.getStartPosition(), model.getMethodContainer(me));
						if(model.getMethodReturn(me)!=null)
							globalmethods.put(node.toString(),model.getMethodReturn(me));
					}
				}	
			}
		}
	}

	public void endVisit(SuperMethodInvocation node)
	{
		Collection<Node> celist=model.getCandidateClassNodes(superclassname);
		Set<Node> methods=new TreeSet<Node>();
		Set<Node> tempmethods1=new TreeSet<Node>();
		Set<Node> tempmethods2=new TreeSet<Node>();
		Set <Node> clist= new HashSet<Node>();
		printMethodsMap.put(node.toString(),node.getStartPosition());
		for(Node ce : celist)
		{
			Collection<Node> melist=model.getMethodNodes(ce);
			for(Node me : melist)
			{
				if(node.getName().toString().equals((String)me.getProperty("exactName")) && model.getMethodParams(me).size()==node.arguments().size())
				{
					if(matchParams(me, node.arguments())==true)
					{
						methods.add(me);
					}
							//System.out.println("4:"+me.getId()+":"+c.getId());
				}
			}
			for(Node me : methods)
			{
				Node fcname=model.getMethodContainer(me);
				if(fcname!=null)
					clist.add(fcname);
				printmethods.put(node.getName().getStartPosition(), me);
				if(model.getMethodReturn(me)!=null)
					globalmethods.put(node.toString(),model.getMethodReturn(me));
			}
			if(clist.isEmpty()==false)
			{
					//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
					//printtypes.replaceValues(printTypesMap.get(e.toString()), clist);
					//change affected types of e.toString() too
					//System.out.println("1&&&"+clist);
					printtypes.replaceValues(node.getStartPosition(), clist);
					//System.out.println(affectedTypes);
			}
		}
		if(tempmethods1.isEmpty() && tempmethods2.isEmpty())
		{
			//System.out.println("yoyo");
			Collection<Node> melist=model.getCandidateMethodNodes(node.getName().toString());
			for(Node me : melist)
			{
				if(model.getMethodParams(me).size()==node.arguments().size())
				{
					if(matchParams(me, node.arguments())==true)
					{
						printmethods.put(node.getName().getStartPosition(), me);
						printtypes.put(node.getName().getStartPosition(),model.getMethodContainer(me));
						if(model.getMethodReturn(me)!=null)
							globalmethods.put(node.toString(), model.getMethodReturn(me));
					}
				}
			}
		}
	}

	public boolean visit(final ClassInstanceCreation node)
	{
		ASTNode anon=node.getAnonymousClassDeclaration();
		if(anon!=null)
		{
			anon.accept(new ASTVisitor(){
				public boolean visit(MethodDeclaration md)
				{
					//System.out.println("anon:");
					Collection<Node> celist=model.getCandidateClassNodes(node.getType().toString());
					for(Node ce : celist)
					{
						Collection<Node>melist=model.getMethodNodes(ce);
						for(Node me:melist)
						{
							
							if(((String)me.getProperty("exactName")).equals(md.getName().toString()) && md.parameters().size()==model.getMethodParams(me).size())
							{
								if(matchParams(me, md.parameters())==true)
								{
									printmethods.put(md.getStartPosition(),me);
									printtypes.put(md.getStartPosition(), model.getMethodContainer(me));
								}
							}
						}
					}
					return true;
				}
			});
		}
		else
		{
			Collection<Node> celist=model.getCandidateClassNodes(node.getType().toString());
			for(Node ce:celist)
			{
				Collection<Node> melist=model.getMethodNodes(ce);
				for(Node me:melist)
				{
					if(((String)me.getProperty("exactName")).equals("<init>") && model.getMethodParams(me).size()==node.arguments().size())
					{
						if(matchParams(me, node.arguments())==true)
						{
							//System.out.println("##########"+node.getParent().getParent().getStartPosition()+node.getType().getStartPosition());
							printmethods.put(node.getType().getStartPosition(),me);
							affectedMethods.put(node.getParent().getParent().getStartPosition(), node.getType().getStartPosition());
							//printtypes.put(node.getType().getStartPosition(), model.getClassElementForMethod(me.getId()));
							//System.out.println("class inst:"+node.getType()+":"+ImpreciseModel.getClassForMethod(me));
						}
					}
				}
			}
		}
		return true;
	}

	public void endVisit(ClassInstanceCreation node)
	{	
		//System.out.println(node.getType().toString()+"0000"+node.toString()+"0000"+node.getParent().getParent());
		Collection<Node> ce=model.getCandidateClassNodes(node.getType().toString());
		for(Node c : ce)
		{
			if(c!=null){
				//globaltypes.put(node.toString(), c);
				//affectedTypes.put(node.getExpression().getStartPosition(), node.get.getStartPosition());
				//printtypes.put(node.getType().getStartPosition(), c);
				printTypesMap.put(node.toString(), node.getType().getStartPosition());
			}
		}
	}
	
	public boolean visit(CastExpression node)
	{
		Collection<Node> ce=model.getCandidateClassNodes(node.getType().toString());
		for(Node c : ce)
		{
			if(c!=null){
				globaltypes.put(node.toString(), c);
				printtypes.put(node.getType().getStartPosition(), c);
				globaltypes.put("("+node.toString()+")", c);
			}
		}
		return true;
	}

	public boolean visit(Assignment node)
	{
		String lhs,rhs;
		lhs=node.getLeftHandSide().toString();
		rhs=node.getRightHandSide().toString();
		if(globalmethods.containsKey(rhs))
		{
			if(!globaltypes.containsKey(lhs))
			{	
				globalmethods.putAll(lhs, globalmethods.get(rhs));
				globalmethods.putAll(lhs, globalmethods.get(rhs));
			}
			else
			{	
				int flag=0;
				Set<Node> temp=new HashSet<Node>();
				for(Node ce:globaltypes.get(lhs))
				{
					if(((String)ce.getProperty("id")).equals(globalmethods.get(rhs)))
					{
						flag=1;
						temp.add(ce);
					}
				}
				if(flag==1)
				{
					System.out.println("^^^^^^^^^:"+temp+node.getStartPosition());
					globaltypes.replaceValues(lhs,temp);
				}
				
			}
		}
		
		return true;
	}

	public void printJson()
	{
		checkForNull();

		//Add to primitive and uncomment to remove unwanted elements
		//String[] primitive = {"int","float","char","long","boolean","String","byte[]","String[]","int[]","float[]","char[]","long[]","byte"};
		String[] primitive={};
		JSONObject main_json=new JSONObject();

		for(Integer key:printtypes.keySet())
		{
			int flag=0;
			String cname=null;
			List<String> namelist = new ArrayList<String>();
			for(Node type_name:printtypes.get(key))
			{
				int isprimitive=0;
				for(String primitive_type : primitive)
				{
					if(((String)type_name.getProperty("id")).equals(primitive_type)==true)
					{
						isprimitive=1;
						break;
					}
				}
				if(isprimitive==0)
				{
					namelist.add("\""+type_name.getProperty("id")+"\"");
					if(flag==0)
					{
						cname=(String) type_name.getProperty("exactName");
						flag=1;
					}
				}

			}
			if(namelist.isEmpty()==false)
			{
				JSONObject json = new JSONObject();
				json.accumulate("line_number",Integer.toString(cu.getLineNumber(key)-cutype));
				json.accumulate("precision", Integer.toString(printtypes.get(key).size()));
				json.accumulate("name",cname);
				json.accumulate("elements",namelist);
				json.accumulate("type","api_type");
				json.accumulate("character", Integer.toString(key));
				main_json.accumulate("api_elements", json);
			}

		}
		for(Integer key:printmethods.keySet())
		{
			List<String> namelist = new ArrayList<String>();
			String mname=null;
			for(Node method_name:printmethods.get(key))
			{
				namelist.add("\""+method_name.getProperty("id")+"\"");
				mname=(String) method_name.getProperty("exactName");
			}
			if(namelist.isEmpty()==false)
			{
				JSONObject json = new JSONObject();
				json.accumulate("line_number",Integer.toString(cu.getLineNumber(key)-cutype));
				json.accumulate("precision", Integer.toString(printmethods.get(key).size()));
				json.accumulate("name",mname);
				json.accumulate("elements",namelist);
				json.accumulate("type","api_method");
				json.accumulate("character", Integer.toString(key));
				main_json.accumulate("api_elements", json);
			}
			//System.out.println(main_json.toString());
		}
		if(main_json.isNull("api_elements"))
		{
			System.out.println("{\"api_elements\": [{ \"precision\": \"\",\"name\": \"\",\"line_number\": \"\",\"type\": \"\",\"elements\": \"\"}]}" ); 
		}
		else
		{
			System.out.println(main_json.toString(3));
		}
		printFields();
	}
	
	public void checkForNull()
	{
		for(Integer key : printtypes.keySet())
			for(Node type_name:printtypes.get(key))
			{
				if(type_name==null)
					printtypes.remove(key, type_name);
			}
		for(Integer key : printmethods.keySet())
			for(Node method_name:printmethods.get(key))
			{
				if(method_name==null)
					printmethods.remove(key, method_name);
			}
	}
	
	
}