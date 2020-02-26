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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Scanner;

public class IR_model
{
  //Count Matrix: rows are documents, cols are words in collection, entries are
  //term frequency.
  public static Map<Integer,Map<String, Integer>> count_matrix = new HashMap<Integer,Map<String, Integer>>();

  //Hashmap of (term : # of docs term is in)
  public static Map<String, Integer> df_matrix = new HashMap<String, Integer>();

  //Hashmap of (document # : (term:weight))
  public static Map<Integer, Map<String, Double>> doc_vectors = new HashMap<Integer,Map<String, Double>>();

  //Query vector
  public static Map<String,Double> query_vector = new HashMap<String, Double>();

  //all vocab words in the search results
  public static List<String> vocabulary = new LinkedList<String>();

  public static Set<String> stopwords = new HashSet<String>();

  public static void main( String[] args )throws Exception
  {
    //Asking for user input
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    System.out.println("Enter a query: ");
    String query = br.readLine();
    Scanner scan = new Scanner(System.in);
    System.out.println("Enter a precision: ");
    Double precision = scan.nextDouble();

    String engine_ID = System.getenv("G_ENGINE_ID");
    String api_Key = System.getenv("G_API_KEY");

    stopwords = getStopWords("proj1-stop.txt");

    if(args.length > 0){ engine_ID = args[0]; }
    if(args.length > 1){ api_Key = args[1]; }
    if(args.length > 2){ precision = Double.parseDouble(args[2]); }
    if(args.length > 3){ query = args[3]; }

    System.out.println("\nParameters:\nClient key  = " + api_Key+"\nEngine key  = " + engine_ID + "\nQuery       = "
                        + query + "\nPrecision   = "+ precision + "\nGoogle Search Results:\n======================\n");

    //querying loop
    while(true){
      System.out.println(query);

      JSONObject results = getSearchResults(api_Key,engine_ID,query);
      generateFrequencyMatrices(query,results);
      //System.out.println(df_matrix);

      vectorizeDocuments(results);
      vectorizeQuery(query);


      Map<ArrayList,Double> precision_indices = getPrecision(results);
      ArrayList rel_doc_indices =  new ArrayList<Integer>();
      Double curr_precision = 0.0;
      for (ArrayList x : precision_indices.keySet()){
        rel_doc_indices =  x;
        curr_precision = precision_indices.get(x);
      }

      //stop program if reached desired precision
      if (curr_precision >= precision){
        System.out.println("\nGoogle Search Results:\n======================\n"+"\nFEEDBACK SUMMARY\nQuery: " + query+"\nPrecision: " + curr_precision + "\nDesired precision reached, done\n");
        System.exit(1);
      }

      //reweight query vector given set of relevant docs
      Map<String,Double> new_query_vector = calculateNewQuery(rel_doc_indices);

      //retrieve new words based on reweighted query vector, reorder query terms
      String new_query = deriveNewWords(query,new_query_vector);
      //System.out.println(new_query);
      query = new_query;
  }

  }


  //Get search results from API and return as a JSONObject
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


  //Returns a list of stopwords
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


  public static void generateFrequencyMatrices(String query, JSONObject results){
    try{
      JSONArray items = results.getJSONArray("items");
      Set<String> query_w = new HashSet<String>(Arrays.asList(query.replaceAll("[^a-zA-Z ]", "").toLowerCase().split("\\s+")));
      //String[] query_w = query.replaceAll("[^a-zA-Z ]", "").toLowerCase().split("\\s+");
      //terminate program if less than 10 results
      if (items.length()<10){
        System.out.println("Less than 10 results returned. End");
        System.exit(1);
      }

      //creating vocabulary list and filling in count matrix
      for (int i=0; i<items.length(); i++){
        Map<String,Integer> term_frequencies = new HashMap<String, Integer>();
        JSONObject item = (JSONObject) items.get(i);
        String summary = null;
        String[] words = {};
        List<String> filtered_words = new LinkedList<String>();
        try{
          summary = (String) item.get("snippet");
          words = summary.replaceAll("[^a-zA-Z ]", "").toLowerCase().split("\\s+");
          for(String word : words){
            if(!stopwords.contains(word) || query_w.contains(word)){
              filtered_words.add(word);
            }
          }
        }
        catch(Exception e){
                    //e.printStackTrace();
        }
        for (String word: filtered_words){
          if(!vocabulary.contains(word)){
            vocabulary.add(word);
          }
          if(term_frequencies.containsKey(word)){
            Integer count = term_frequencies.get(word);
            term_frequencies.put(word, count+1);
          }
          else{
            term_frequencies.put(word, 1);
          }
        }
        count_matrix.put(i, term_frequencies);
      }
      //filling in df_matrix
      for (String vocab : vocabulary){
        Integer num_docs = 0;
        for (Integer doc : count_matrix.keySet()){
          if (count_matrix.get(doc).containsKey(vocab)){
            num_docs += 1;
          }
        }
        df_matrix.put(vocab,num_docs);
      }
    }
    catch(Exception e){
            e.printStackTrace();
    }
  }


  public static void vectorizeDocuments(JSONObject results){
    JSONArray items = results.getJSONArray("items");
    //printResult(items, 0);
    for (int i=0;i<items.length(); i++){ //iterating through docs
      Map<String,Double> term_tf_idf = new HashMap<String, Double>();
      Double denom = norm_term(i);
      for (String vocab : vocabulary){
        if (count_matrix.get(i).containsKey(vocab)){
          Double tf_idf = calculate_weight(i, vocab);
          term_tf_idf.put(vocab,tf_idf/denom);
        }
        else{
          term_tf_idf.put(vocab,0.0);
        }
      }
      doc_vectors.put(i,term_tf_idf);
    }
  }


  public static void vectorizeQuery(String query){
    String lw_query = query.replaceAll("[^a-zA-Z0-9 ]", "").toLowerCase();
    //System.out.println(lw_query);
    String[] query_words = lw_query.split(" ");
    Map<String,Integer> word_count = new HashMap<String, Integer>();
    Double sum = 0.0;
    for (String w1 : query_words){
      int num = 0;
      for (String w2 : query_words){
        if (w1 == w2){
          num += 1;
        }
      }
      if (!word_count.containsKey(w1)){
        word_count.put(w1,num);
        sum += Math.pow((Math.log(num)+1.0)*(Math.log(10.0/df_matrix.get(w1))),2);
      }
    }
    for (String word : word_count.keySet()){
      Double tf_idf = (Math.log(word_count.get(word))+1.0)*(Math.log(10.0/df_matrix.get(word)));
      query_vector.put(word,tf_idf/Math.sqrt(sum));
    }
  }


  public static Double norm_term(Integer doc_id){
    Double sum = 0.0;
    Map<String, Integer> doc_term_count = count_matrix.get(doc_id);
    for (String word : doc_term_count.keySet()){
      int f_ik = doc_term_count.get(word);
      int n_k = df_matrix.get(word);
      //Double tf_idf = Math.pow((Math.log(f_ik)+1.0)*(Math.log(10.0/n_k)),2);
      Double tf_idf = Math.pow(Math.log(f_ik)+1.0,2);
      sum += tf_idf;
    }
    return Math.sqrt(sum);
  }


  public static Double calculate_weight(Integer doc_id, String term){
    //Compute term frequency
    Map<String, Integer> doc_term_count = count_matrix.get(doc_id);
    int f_ik = 0;
    if (doc_term_count.containsKey(term)){
      f_ik = doc_term_count.get(term);
    }
    int n_k = df_matrix.get(term);
    //Double tf_idf = (Math.log(f_ik)+1.0)*(Math.log(10.0/n_k));
    Double tf_idf = (Math.log(f_ik)+1.0);
    return tf_idf;
  }


  //Relevance feedback loop, returns list of indices of relevant docs
  public static Map<ArrayList,Double> getPrecision(JSONObject results){
      Double precision = 0.0;
      Double relevant_count = 0.0;
      ArrayList relevant_indices = new ArrayList<Integer>();
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
                  relevant_indices.add(i);
              }
              System.out.println();

          }
          precision = relevant_count/10.0;
          //stop program if precision is 0
          if (precision == 0.0){
            System.out.println("Precision is 0. End");
            System.exit(1);
          }

      }catch(Exception e){
          e.printStackTrace();
      }
      System.out.println(precision);
      Map<ArrayList,Double> precision_indices = new HashMap<ArrayList,Double>();
      precision_indices.put(relevant_indices,precision);
      return precision_indices;
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


  //calculates new query vector using Rocchio's.
  public static Map<String,Double> calculateNewQuery(ArrayList rel_indices){
    //System.out.println(rel_indices);
    ArrayList irrel_indices = new ArrayList<Integer>();
    for (int i = 0; i<10; i++){
      if (!rel_indices.contains(i)){
        irrel_indices.add(i);
      }
    }
    //System.out.println(irrel_indices);

    Double alpha = 1.0;
    Double beta = 1.0;
    Double gamma = 0.5;

    Map<String,Double> rel_vector = doc_vectors.get(rel_indices.get(0));
    for (int i=1; i<rel_indices.size(); i++){
      doc_vectors.get(rel_indices.get(i)).forEach((k,v) -> rel_vector.merge(k,v,Double::sum));
    }
    Map<String,Double> irrel_vector = doc_vectors.get(irrel_indices.get(0));
    for (int j=1; j<irrel_indices.size(); j++){
      doc_vectors.get(irrel_indices.get(j)).forEach((k,v) -> irrel_vector.merge(k,v,Double::sum));
    }

    Map<String,Double> new_query_vector = new HashMap<String, Double>();
    for (String key : irrel_vector.keySet()) {
      Double val1 = irrel_vector.get(key);
      val1 *= (gamma/(double) irrel_indices.size());
      Double val2 = rel_vector.get(key);
      val2 *= (beta/(double) rel_indices.size());
      Double val3;
      if (new_query_vector.containsKey(key)){
        val3 = new_query_vector.get(key);
      }
      else { val3 = 0.0; }
      val3 *= alpha;
      new_query_vector.put(key,val3+val2-val1);
    }
    return new_query_vector;
  }


  //Get the weights with topmost frequency add them to query.
  public static String deriveNewWords(String query, Map<String,Double> query_vector){
    String[] query_words_list = query.split(" ");
    List<String> query_words = new ArrayList<String>();
    int num = query_words_list.length;
        for (int i=0; i<num; i++)
        {
            String w = query_words_list[i];
            query_words.add(w);
        }
    System.out.println(query_words);
    Double highest = Double.MIN_VALUE;
    String highest_word = null;
    Double highest2 = Double.MIN_VALUE;
    String highest_word2 = null;
    for (String word: query_vector.keySet()){
      Double value = query_vector.get(word);
      if (highest <= value && !query_words.contains(word)){
        highest2 = highest;
        highest_word2 = highest_word;
        highest = value;
        highest_word = word;
      }
      else if (highest2 <= value && !query_words.contains(word)){
        highest2 = value;
        highest_word2 = word;
      }
    }
    query_words.add(highest_word);
    query_words.add(highest_word2);
    StringBuffer sb = new StringBuffer();

      for (String s : query_words) {
         sb.append(s);
         sb.append(" ");
      }
      String query_string = sb.toString();
    return query_string;
  }

}
