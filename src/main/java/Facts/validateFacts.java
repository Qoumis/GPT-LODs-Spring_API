package Facts;

import jakarta.servlet.http.HttpServletResponse;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

public class validateFacts {
    private String adaptive_url = "http://93.115.20.167:5000/";

    public String validateTriples(String triples, String kg, HttpServletResponse response) throws IOException, JSONException {

        if(kg == null)
            kg  = "DBpedia";

        HttpURLConnection con = (HttpURLConnection) new URL(adaptive_url + "factChecking/" + kg).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        con.getOutputStream().write(triples.getBytes());


        int responseCode = con.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {

            String output = new BufferedReader(new InputStreamReader(con.getInputStream())).lines()
                    .reduce((a, b) -> a + b).get();

            return output;
        }
        else {

            System.out.println("Error: " + responseCode);

            response.setStatus(responseCode);
            InputStream errorStream = con.getErrorStream();
            if (errorStream != null) {
                String errorOutput = new BufferedReader(new InputStreamReader(errorStream)).lines()
                        .reduce((a, b) -> a + b).orElse("");
                System.out.println("Error details: " + errorOutput);
                return "Fact Validation failed: The server encountered an internal error and was unable to complete your request. " +
                        "Please make sure that you provided a valid RDF triple format! ";
            }
        }
        return "dummy";

    }

}
