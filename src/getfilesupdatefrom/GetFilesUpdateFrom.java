/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package getfilesupdatefrom;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.json.simple.parser.JSONParser;

/**
 *
 * @author phil
 */
public class GetFilesUpdateFrom {

    /**
     * @param args the command line arguments
     */
    static String sJiraDomain = "";
    static String sJiraPass = "";
    static String sJiraUser = "";
    static String sFromRevision = "0";
    static String sToRevision ="HEAD";
    static String sRepoUrl = "";
    

    public static void main(String[] args) {
         java.io.BufferedReader br = null;
        try
        {
            br = new java.io.BufferedReader( new java.io.InputStreamReader(new java.io.FileInputStream(
                    args.length!=0?args[0]:"config.txt"
                    )));
            String s = "";
            
            if(br!=null)
            while(s!=null)
            {
                String[] infoConnect = {"",""};
                s = br.readLine();
                if(s==null)
                {
                    continue;
                }
                infoConnect = s.split(":",2);

                switch(infoConnect[0].trim())
                {
                    case "sJiraDomain": 
                            sJiraDomain = infoConnect[1].trim();
                        break;
                    case "user":  
                            sJiraUser = infoConnect[1].trim();
                        break;
                    case "pass":  
                            sJiraPass = infoConnect[1].trim();
                        break;
                    case "from-revision":
                            sFromRevision = infoConnect[1].trim();
                        break;
                    case "to-revision":
                        sToRevision = infoConnect[1].trim();
                        break;
                    case "sRepoUrl":
                        sRepoUrl = infoConnect[1].trim();
                        break;
                }
            }

            br.close();
        }
        catch(Exception ex){ex.printStackTrace();}

        GetFilesUpdateFrom t = new GetFilesUpdateFrom();
    }
    
    public GetFilesUpdateFrom()
    {
        String s = executeCommand("svn log "+sRepoUrl+" -v -r  "+sFromRevision+":"+sToRevision+" --xml");
        HashMap arrXmlInfo = parseXml(s);
        printToFile(arrXmlInfo);
    }
    
    
    private String executeCommand(String command) {

		StringBuffer output = new StringBuffer();

		Process p;
		try {
			p = Runtime.getRuntime().exec(command);
			//p.waitFor();
			BufferedReader reader = 
                           new BufferedReader(new InputStreamReader(p.getInputStream()));

			String line = "";			
			while ((line = reader.readLine())!= null) {
				output.append(line);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return output.toString();

	}

    private HashMap parseXml(String s) {
        HashMap<String,HashMap> arrAuthor= new HashMap<String,HashMap>();
        HashMap<String,String> ListFiles = new HashMap<String,String>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();  
        DocumentBuilder builder; 
        Document document;
        try {  
            builder = factory.newDocumentBuilder();  
            document = builder.parse(new InputSource(new StringReader(s)));
            Node node = document.getFirstChild();
            if(node.getNodeName()=="log")
            {
                 node = node.getFirstChild();
                 //iterer parmi les noeuds logentry
                 while(node!=null)
                 {
                     if(node.getNodeName()=="logentry")
                     {
                         String sRev = node.getAttributes().getNamedItem("revision").getTextContent();
                         Node logEntryNode = node;
                         Node oSubNode = logEntryNode.getFirstChild();
                         HashMap oAuthor=null;
                         String[] arrMsg = null;
                         
                         while(oSubNode!=null)
                         {
                             switch(oSubNode.getNodeName())
                             {
                                 case "msg":
                                        //Vérifier s'il n'a un ticket dans le commentaire
                                        String sMsg = oSubNode.getTextContent().replaceAll("([a-zA-Z0-9]+\\-[0-9]+)", "/-/$1");
                                        arrMsg = sMsg.split("/-/");                                    
                                        break;
                                 case "author":
                                        String sRealName = parseJiraJsonToRetrieveAuthor(oSubNode.getTextContent());
                                        oAuthor = arrAuthor.get(sRealName);    
                                        if(oAuthor==null)
                                        {
                                            arrAuthor.put(sRealName, new HashMap());
                                            oAuthor = arrAuthor.get(sRealName);
                                        }
                                        break;
                                 case "paths":
                                        //iterer parmi les paths
                                        Node oPathNode = oSubNode.getFirstChild();
                                        while(oPathNode!=null)
                                        {
                                            if(oPathNode.getNodeName()=="path")
                                            {
                                                String sValue = ListFiles.get(oPathNode.getTextContent());
                                                if(sValue==null)
                                                {
                                                    ListFiles.put(oPathNode.getTextContent(),oPathNode.getTextContent());
                                                }
                                            }
                                            oPathNode = oPathNode.getNextSibling();
                                        }
                                        break;
                             }
                             oSubNode = oSubNode.getNextSibling();
                         }
                         
                         //Remplir la doc du noeud
                         if(arrMsg!=null)
                         {  
                             String pattern = "([a-zA-Z0-9]+\\-[0-9]+)";
                             Pattern r = Pattern.compile(pattern);
                             
                             //récupérer le numéro du billet
                             for (int i = 0; i < arrMsg.length; i++) {
                                 Matcher m = r.matcher(arrMsg[i]);
                                 if(m.find())
                                 {
                                     System.out.println(m.group(0));
                                     //appeler l'API JIRA pour récupérer information du billet
                                     HashMap arrResult = parseJiraJson(m.group(0));
                                     if(!((String)arrResult.get("sAuthor")).isEmpty())
                                     {
                                         arrMsg[i] = arrMsg[i].replace(m.group(0), "<a target=\"_blank\" href=\""+sJiraDomain+"/browse/"+m.group(0).replace("#", "")+"\">"+m.group(0)+"</a>") + " | Demandeur:"+arrResult.get("sAuthor");
                                     }
                                     
                                     
                                 }
                                 if(!arrMsg[i].isEmpty())
                                 {
                                     oAuthor.put(sRev, arrMsg[i]);
                                 }
                                
                            }
                         }
                        
                         //iterer parmi les sous-noeuds pour sortir les infos importantes
                         // auteur et path des fichiers commités
                         
                     }
                     node = node.getNextSibling();
                 }
            }
            else //log n'est pas le noeud root
            {
                
            }
        } catch (Exception e) {  
            e.printStackTrace();  
        } 
        HashMap arrReturn = new HashMap();
        arrReturn.put("0arrListFileInfo",ListFiles);
        arrReturn.put("1arrAuthorInfo",arrAuthor);
        return arrReturn;
    }

    private void printToFile(HashMap hashm) {
        String sFinalText="";
       for (Object sLevel1 : hashm.keySet()) 
       {
           
           switch(sLevel1.toString())
           {
               case "1arrAuthorInfo":
                                for (Object sLevel2 : ((HashMap)hashm.get(sLevel1)).keySet()) 
                                {
                                    sFinalText+= "<p><b>"+sLevel2+"</b></p><ul>";
                                    for (Object sLevel3 : ((HashMap)((HashMap)hashm.get(sLevel1)).get(sLevel2)).keySet()) 
                                    {
                                       sFinalText += "<li>"+((HashMap)((HashMap)hashm.get(sLevel1)).get(sLevel2)).get(sLevel3)+"</li>";
                                    }
                                    sFinalText+="</ul>";
                                }
                                break;
               case "0arrListFileInfo":
                                    sFinalText+= "<p><b>Liste des fichiers mis à jour</b></p><ul>";
                                    Map sortedMap = new TreeMap((HashMap)hashm.get(sLevel1));
                                    Set set2 = sortedMap.entrySet();
                                    Iterator iterator2 = set2.iterator();
                                    while(iterator2.hasNext()) {
                                        Map.Entry me2 = (Map.Entry)iterator2.next();
                                        sFinalText += "<li>"+me2.getValue()+"</li>";
                                     }
                                        
                                    sFinalText+="</ul>";
                                break;
           }
       }
       
       try{
            java.io.BufferedWriter b = new java.io.BufferedWriter(new java.io.FileWriter("report.html",false));
            b.write(sFinalText.toString());
            b.flush();
            b.close();
            System.out.println("fichier créé");
        }
        catch(Exception ex){
            ex.printStackTrace();
        }
    }

    private HashMap parseJiraJson(String sNoTicket){
        String sAssignee = "";
       String sAuthor = "";
       String sJson = executeCommand("curl -D- -u "+sJiraUser+":"+sJiraPass+" -X GET -H \"Content-Type: application/json\" "+sJiraDomain+"/rest/api/latest/issue/"+sNoTicket.replace("#", ""));
       try{
       sJson = sJson.substring(sJson.indexOf("{\"expand\""));
       
       JSONParser jsonp = new JSONParser();
       
       
           JSONObject json = (JSONObject) jsonp.parse(sJson);
          sAssignee = ((JSONObject)((JSONObject)json.get("fields")).get("assignee")).get("displayName").toString();
          sAuthor = ((JSONObject)((JSONObject)json.get("fields")).get("creator")).get("displayName").toString();
       }
       catch(Exception e)
       {
           System.out.println("Billet "+sNoTicket+" n'existe pas!");
       }
       HashMap<String, String> arrReturn = new HashMap<String, String>();
       arrReturn.put("sAssignee", sAssignee);
       arrReturn.put("sAuthor", sAuthor);
       return arrReturn;
    }
    
    private String parseJiraJsonToRetrieveAuthor(String sName){
       String sJson = executeCommand("curl -D- -u "+sJiraUser+":"+sJiraPass+" -X GET -H \"Content-Type: application/json\" "+sJiraDomain+"/rest/api/latest/user/search/?username="+sName);
       sJson = sJson.substring(sJson.indexOf("[{\"self\""));
       
       JSONParser jsonp = new JSONParser();
       String sDisplayName = "";
       try{
           JSONArray json = (JSONArray) jsonp.parse(sJson);
           JSONObject json1 = (JSONObject)json.get(0);
           sDisplayName = json1.get("displayName").toString();
           
       }
       catch(Exception e)
       {
           e.printStackTrace();
       }

       return sDisplayName;
    }
    
}
