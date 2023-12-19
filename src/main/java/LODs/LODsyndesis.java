package LODs;

import jakarta.servlet.http.HttpServletResponse;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class LODsyndesis {

    private static final String URL ="https://demos.isl.ics.forth.gr/LODsyndesisIE/rest-api";


    public String getEntities(String text, String ERtools, String equivalentURIs, String provenance, HttpServletResponse response) throws IOException, JSONException {
        text = text.replace(" ","%20");

        HttpURLConnection con = (HttpURLConnection) new URL(URL + "/getEntities" + "?text=" + text + "&ERtools=" + ERtools + "&equivalentURIs=" + equivalentURIs + "&provenance=" + provenance).openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Content-Type", "application/n-triples");

        return receiveResponse(con,response);
    }


    public String exportAsRDFa(String text, String ERtools, HttpServletResponse response) throws IOException, JSONException {

        text = text.replace(" ","%20");

        HttpURLConnection con = (HttpURLConnection) new URL(URL + "/exportAsRDFa" + "?text=" + text + "&ERtools=" + ERtools).openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Content-Type", "text/html");

        return receiveResponse(con,response);

    }

    public String exportAsJson(String text, String ERtools, String equivalentURIs, HttpServletResponse response) throws IOException, JSONException {
        text = text.replace(" ","%20");

        HttpURLConnection con = (HttpURLConnection) new URL(URL + "/exportAsJSON" + "?text=" + text + "&ERtools=" + ERtools + "&equivalentURIs=" + equivalentURIs).openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Content-Type", "application/json");

        return receiveResponse(con,response);
    }


    public String receiveResponse(HttpURLConnection con, HttpServletResponse response) throws IOException, JSONException {

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
                return "Entity Recognition failed: " + new JSONObject(errorOutput).getJSONObject("error").getString("message");
            }
        }
        return "dummy";
    }

    public String annotate_text(String text, String ERtools, HttpServletResponse response ) throws JSONException, IOException {

        //Use lods to recognize entities
        String json = exportAsJson(text, ERtools, "false", response);
        if(response.getStatus() != 200)
            return json;

        //For each recognized entity provide the dbpedia link
        JSONArray jsonArray = new JSONArray(json);
        for(int i = 0; i < jsonArray.length(); i++){
            JSONObject jsonObject = jsonArray.getJSONObject(i);

            if(jsonObject.getBoolean("isEntity"))
                text = text.replace(jsonObject.getString("textpart"), jsonObject.getString("textpart") + " (" + jsonObject.getString("dbpediaURI") + ")");
        }

        return text;
    }


}
