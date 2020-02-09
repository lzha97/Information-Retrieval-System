import java.net.URL;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.*;
import java.util.*;
import java.lang.String;
import java.lang.Math;
import java.text.DecimalFormat;
import java.io.File;  
import java.io.FileNotFoundException;


public class IR_model 
{
    //Count Matrix: for each document, frequency of each word in the collection
    public static Map<Integer,Map<String, Integer>> count_matrix = new HashMap<Integer,Map<String, Integer>>();

    //for each term in collection, number of documents it appears in
    public static Map<String, Integer> df_matrix = new HashMap<String, Integer>();

    public static Set<String> stopwords = new HashSet<String>();
        
    public static void main ( String[] args )throws Exception
    {
        String engine_ID = System.getenv("G_ENGINE_ID");
        String api_Key = System.getenv("G_API_KEY");
        Double precision =  0.8; 
        String query = "milky way";

        stopwords = getStopWords("proj1-stop.txt");

        if(args.length > 0){ engine_ID = args[0]; }
        if(args.length > 1){ api_Key = args[1]; }
        if(args.length > 2){ precision = Double.parseDouble(args[2]); }
        if(args.length > 3){ query = args[3]; }

        System.out.println("\nParameters:\nClient key  = " + api_Key+"\nEngine key  = " + engine_ID + "\nQuery       = "
                            + query + "\nPrecision   = "+ precision + "\nGoogle Search Results:\n======================\n");
        
        JSONObject results = getSearchResults(api_Key,engine_ID,query);
        generateFrequencyMatrices(results);

        Map<String, Double> term_relevance_scores = new HashMap<String,Double>();
        for(Map.Entry<String, Integer> entry : df_matrix.entrySet()){
            Double score = 0.0;
            for(int i=0; i<10; i++){
               Double tf_idf = tf_idf(entry.getKey(), i);
               score = score + tf_idf;
            }
            term_relevance_scores.put(entry.getKey(), score);
        }
        System.out.println("\n" + term_relevance_scores);

        //Double precis = getPrecision(results);
        //System.out.println("Precision: "+precis);
       
    }

    public static Set<String> getStopWords(String filename){
        Set<String> stopwords = new HashSet<String>();
        try {
            File file = new File(filename);
            Scanner scn = new Scanner(file);
            while (scn.hasNextLine()) {
                stopwords.add(scn.nextLine());
            }
            scn.close();
        } catch (FileNotFoundException e) {    
            e.printStackTrace();
        }
        return stopwords;

    }

    public static String deriveNewWords(JSONObject results){
        return "";
    }

    public static void generateFrequencyMatrices(JSONObject results){

        try{
            JSONArray items = results.getJSONArray("items");

            for(int i=0; i<items.length(); i++){

                Map<String,Integer> term_frequencies = new HashMap<String, Integer>();
                JSONObject item = (JSONObject) items.get(i);
                String summary = null;
                String[] words = {};
                List<String> filtered_words = new LinkedList<String>();
                try{
                    summary = (String) item.get("snippet"); 
                    words = summary.replaceAll("[^a-zA-Z ]", "").toLowerCase().split("\\s+");
                    for(String word : words){
                        if(!stopwords.contains(word)){
                            filtered_words.add(word);
                        }
                    }
                }catch(Exception e){
                    //e.printStackTrace();
                }

                for (String word: filtered_words){
                    //build document frequency for each term
                    if(df_matrix.containsKey(word)){
                        Integer count = df_matrix.get(word); 
                        df_matrix.put(word, count+1);
                    }else{
                        df_matrix.put(word, 1);
                    }
                    //build term frequency map for the document 
                    if(term_frequencies.containsKey(word)){
                        Integer count = term_frequencies.get(word);
                        term_frequencies.put(word, count+1);
                    }else{
                        term_frequencies.put(word, 1); 
                    }
                }
                count_matrix.put(i, term_frequencies);
            } 

            System.out.println(count_matrix);
            System.out.println("\n"+df_matrix);
           
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static Double tf_idf(String term, Integer doc_id){
        //Compute term frequency
        
        Double tf = 0.0;

        Map<String, Integer> doc_tf = count_matrix.get(doc_id); 
        if(doc_tf.containsKey(term)){
            tf = (double) count_matrix.get(doc_id).get(term);
        }
        Double doc_length = (double)count_matrix.get(doc_id).size();
        if(doc_length == 0.0){ return 0.0; }
        tf = tf/doc_length;

        Double doc_count = (double)df_matrix.get(term);
        Double idf = Math.log(10/doc_count);

        return tf*idf;
    }

    public static Double getPrecision(JSONObject results){
        Double precision = 0.0;
        Double relevant_count = 0.0; 

        try{
            JSONArray items = results.getJSONArray("items");

            for(int i=0; i<items.length(); i++){
                printResult(items, i);
                Scanner scn = new Scanner(System.in);
                System.out.println("Relevant? [Y/N]");
                String relevance = scn.nextLine().trim();
                while(relevance.equals("Y") && relevance.equals("N")){
                    System.out.println(relevance);
                    System.out.println("Incorrect input. Relevant? [Y/N]");
                    relevance = scn.nextLine();
                }
                if(relevance.equals("Y")){
                    relevant_count++;
                }
                System.out.println();

            } 
            precision = relevant_count/10; 

        }catch(Exception e){
            e.printStackTrace();
        }
        return precision;
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
        }catch(Exception e){}

        System.out.println("Result " + (i+1));
        System.out.println("["); 
        System.out.println(" URL: " + item_url);
        System.out.println(" Title: " + title); 
        System.out.println(" Summary: "+summary);
        System.out.println("]");
    }

    public static JSONObject getSearchResults(String apiKey, String engineID, String query){
        JSONObject json_results = null;
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
            String results = " ";
            results = sb.toString();
            json_results = new JSONObject(results);

        }catch(Exception e){
            e.printStackTrace();
        }
        return json_results;
    }
}
