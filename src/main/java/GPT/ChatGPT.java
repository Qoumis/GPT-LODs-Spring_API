package GPT;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.codehaus.jettison.json.JSONObject;

public class ChatGPT {

    private String url = "https://api.openai.com/v1/chat/completions";
    private String key;

    /**Calls gpt without caring  about conversation history*/
    public String chooseModel(String text, String model) throws Exception{
        if(model.equals("davinci")){
            return chatGPT_Davinci(text);
        }
        else if(model.equals("turbo")){
            return chatGPT_TURBO(text);
        }

        return "Invalid Model Name!";
    }

    /**Calls gpt turbo version without caring  about conversation history*/
    public  String chatGPT_TURBO(String text) throws Exception {

        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Authorization", "Bearer "+key);

        JSONObject data = new JSONObject();
        data.put("model", "gpt-3.5-turbo-16k");
        data.put("messages", "[{'role': 'user', 'content': 'MyText'}]");
        data.put("temperature", 0.1);   //set it to 0.1 for max accuracy
        data.put("max_tokens", 2000);   //completion length

        String body=data.toString().replace("\"[", "[").replace("]\"", "]").replace("'","\"").replace("MyText", text);
        con.setDoOutput(true);
        con.getOutputStream().write(body.toString().getBytes());

        int responseCode = con.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {

            String output = new BufferedReader(new InputStreamReader(con.getInputStream())).lines()
                    .reduce((a, b) -> a + b).get();

            return new JSONObject(output).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
        }
        else {   //possible token exceedance

            System.out.println("Error: " + responseCode);
            InputStream errorStream = con.getErrorStream();
            if (errorStream != null) {
                String errorOutput = new BufferedReader(new InputStreamReader(errorStream)).lines()
                        .reduce((a, b) -> a + b).orElse("");
                System.out.println("Error details: " + errorOutput);
                return "Failed: " + new JSONObject(errorOutput).getJSONObject("error").getString("message");
            }
        }
        return "Failed to fetch an answer from Chat-GPT";
    }

    /**Calls gpt davinci version without caring  about conversation history*/
    public  String chatGPT_Davinci(String text) throws Exception {

        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Authorization", "Bearer "+key);

        JSONObject data = new JSONObject();
        data.put("model", "text-davinci-003");
        data.put("prompt", text);
        data.put("max_tokens", 1000);
        System.out.println(text);

        data.put("temperature", 0.1);

        con.setDoOutput(true);
        con.getOutputStream().write(data.toString().getBytes());

        int responseCode = con.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {

            String output = new BufferedReader(new InputStreamReader(con.getInputStream())).lines()
                    .reduce((a, b) -> a + b).get();

            return new JSONObject(output).getJSONArray("choices").getJSONObject(0).getString("text");
        }
        else { //possible token exceedance

            System.out.println("Error: " + responseCode);
            InputStream errorStream = con.getErrorStream();
            if (errorStream != null) {
                String errorOutput = new BufferedReader(new InputStreamReader(errorStream)).lines()
                        .reduce((a, b) -> a + b).orElse("");
                System.out.println("Error details: " + errorOutput);

                return "Failed: " + new JSONObject(errorOutput).getJSONObject("error").getString("message");
            }
        }
        return "Failed to fetch an answer from Chat-GPT";
    }

    public void setKey(String key){
        this.key = key;
    }

    public String getKey(){
        return this.key;
    }

}
