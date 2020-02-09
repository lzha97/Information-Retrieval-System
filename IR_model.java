import java.net.URL;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.*;

public class IR_model 
{
    public static void main ( String[] args )throws Exception
    {
        String engine_ID = System.getenv("G_ENGINE_ID");
        String api_Key = System.getenv("G_API_KEY");
        Double precision =  0.8; 
        String query = "milky way";

        if(args.length > 0){
            engine_ID = args[0];
        }
        if(args.length > 1){
            api_Key = args[1];
        }
        if(args.length > 2){
            precision = Double.parseDouble(args[2]);
        }
        if(args.length > 3){
            query = args[3];
            System.out.println(args[3]);
        }
        String str_results = getSearchResults(api_Key,engine_ID,query);
        try{
            JSONObject json_results = new JSONObject(str_results);
            JSONArray items = json_results.getJSONArray("items");

            for(int i=0; i<items.length(); i++){
                printResult(items, i);

            }  
        }catch(Exception e){
            e.printStackTrace();
        }
       
    }

    public static void printResult(JSONArray items, int i){
        JSONObject item = (JSONObject) items.get(i); 
                Object title = null; 
                Object item_url = null; 
                Object summary = null; 

                try{
                    title = item.get("title");
                    item_url = item.get("link");
                    summary = item.get("snippet");
                }
                catch(Exception e){}

                System.out.println("Result " + (i+1));
                System.out.println("["); 
                System.out.println(" URL: " + item_url);
                System.out.println(" Title: " + title); 
                System.out.println(" Summary: "+summary);
                System.out.println("]\n");
    }

    public static String getSearchResults(String apiKey, String engineID, String query){
        String results = " ";
        try{
            URL url = new URL("https://www.googleapis.com/customsearch/v1?key=" 
                    + apiKey + "&cx="+engineID+"&q=" +query.replace(" ", "%20")+"&alt=json");

            HttpURLConnection cnxn = (HttpURLConnection) url.openConnection();
            cnxn.setRequestMethod("GET");
            cnxn.setRequestProperty("Accept", "application/json");
            BufferedReader buffread = new BufferedReader(new InputStreamReader(cnxn.getInputStream()));
            StringBuilder sb = new StringBuilder();

            String output; 
            while((output = buffread.readLine()) != null){
                sb.append(output+"\n");
            }
            cnxn.disconnect();
            results = sb.toString();
            

        }
        catch(Exception e){
            e.printStackTrace();
        }
        return results;
    }
}
