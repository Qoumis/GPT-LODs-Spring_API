package thesis.gtp_lods_plus.gptlodsspringboot;

import Facts.validateFacts;
import LODs.LODsyndesis;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Info;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import GPT.ChatGPT;

@SpringBootApplication
@RestController
@OpenAPIDefinition(info = @Info(title = "GPT-LODs+ API", version = "1.0",
                                description = "This API provides : <br> " +
                                              "<pre> 1. Entity recognition of Chat GPT's responses. <br>" +
                                              "<pre> 2. Fact checking with and without entity recognition of Chat GPT's responses." +
                                              "<pre> 3. Fact checking with and without entity recognition of plain text." +
                                              "<pre> 4. Fact validation for 2 & 3 in a single step." +
                                              "<pre> 5. Fact validation separately (by providing the generated triples as input)" +
                                              "<pre> <pre>Detailed information about each service, including instructions on how to make calls (required body parameters etc.), is available in this <a href=\"https://docs.google.com/document/d/1yX4NzmvTXj3ggCYVsoXMEVnbruUuC-pYbOqb_0QE0dM\" target=\"_blank\">spreadsheet</a>"))
@RequestMapping("/GPT-LODsPlus/rest-API")
public class GptLodsSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(GptLodsSpringbootApplication.class, args);
    }

    @Operation(summary = "Calls Chat-GPT with the given text and recognizes all the entities of the provided gpt response by using any combination of three recognition tools and LODsyndesis. \n " +
                         "It produces the keyword of each recognized entity and its corresponding DBpedia and LODsyndesis URI.")
    @PostMapping(value = "/entityRecognition/entities", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String getEntities(@RequestBody String Json, HttpServletResponse response) {

        return call_gpt_lods(Json, response, "entities");
    }

    @Operation(summary = "Calls Chat-GPT with the given text and recognizes all the entities of the provided gpt response by using any combination of three recognition tools and LODsyndesis." +
                         "It produces the initial text in HTML format enriched with RDFa.")
    @PostMapping(value = "/entityRecognition/RDFa",consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String exportAsRDFa(@RequestBody String Json, HttpServletResponse response ) {

        return call_gpt_lods(Json, response, "rdf");
    }

    @Operation(summary = "It produces the initial text in parts in JSON format. For each recognized entity, the JSON file contains its DBpedia URI, " +
                         "its LODsyndesis URI, its type,an image and optionally its equivalent URIs. On the contrary, it also returns text parts that are not entities")
    @PostMapping(value = "/entityRecognition/JSON", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String exportAsJSON(@RequestBody String Json, HttpServletResponse response) {

        return call_gpt_lods(Json, response, "json");
    }

    //Calls each LODsyndesis API based on the caller
    public String call_gpt_lods(String Json, HttpServletResponse response, String caller){
        System.out.println("Received : " + Json);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // Convert JSON string to a Map
            Map<String, String> dataMap = objectMapper.readValue(Json, Map.class);

            String gpt_resp = call_gpt(dataMap, response); //First we call chat-gpt
            if(response.getStatus() != 200)
                return gpt_resp;        //something  went wrong when calling chat-gpt. Stop and return error.

            //Now it's time to call LODsyndesis
            if(dataMap.get("ERtools") == null){
                response.setStatus(400);
                return "Make sure you provided all the required parameters in your json file (ERtools cannot be null)";
            }

            LODsyndesis lod = new LODsyndesis();

            if(caller.equals("json"))
                return lod.exportAsJson(gpt_resp, dataMap.get("ERtools"), dataMap.get("equivalentURIs"), response);
            else if(caller.equals("rdf"))
                return lod.exportAsRDFa(gpt_resp, dataMap.get("ERtools"), response);
            else
                return lod.getEntities(gpt_resp, dataMap.get("ERtools"), dataMap.get("equivalentURIs"), dataMap.get("provenance"), response);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(415);
            return "Please make sure you are providing a valid json format in the request's body!";
        }

    }

    public String call_gpt(Map<String, String> dataMap, HttpServletResponse response) throws Exception {

        ChatGPT gpt = new ChatGPT();

        if(dataMap.get("key") == null){
            response.setStatus(403);
            return "Please provide your open AI key to use the service";
        }
        gpt.setKey(dataMap.get("key"));

        if(dataMap.get("text") == null){
            response.setStatus(400);
            return "Make sure you provided all the required parameters in your json file (text cannot be null)";
        }

        String gpt_resp = gpt.chatGPT_TURBO(dataMap.get("text"));  //call chat gpt
        if(gpt_resp.startsWith("Failed")){  //something went wrong when calling chat gpt
            response.setStatus(400);
            return gpt_resp;
        }

        response.setStatus(200);
        return gpt_resp;
    }


    @Operation(summary = "First it fetches an answer from chat gpt for the given question.\n" +
            "Returns all the facts of the chat-gpt response in RDF N-triples format using DBpedia model. \n" +
            "It also performs entity recognition, by using any combination of three recognition tools and LODsyndesis,\n" +
            "before generating facts if 'annotate' parameter is set to true.\n")
    @PostMapping(value = "/facts/GPT/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String GetFacts_GPT(@RequestBody String Json, HttpServletResponse response) {
        System.out.println("Received : " + Json);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // Convert JSON string to a Map
            Map<String, String> dataMap = objectMapper.readValue(Json, Map.class);

            if(dataMap.get("text") == null){
                response.setStatus(400);
                return "Make sure you provided all the required parameters in your json file (text cannot be null)";
            }

            //Call chat-gpt first to get the response that will be fact checked
            String gpt_resp = call_gpt(dataMap, response);
            if(response.getStatus() != 200)
                return gpt_resp;        //something  went wrong when calling chat-gpt. Stop and return error.

            System.out.println("GPT response to the question : " + gpt_resp);
            dataMap.replace("text", gpt_resp);

            //The rest of the work happens here
            return getFacts(dataMap, response);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(415);
            return "Please make sure you are providing a valid json format in the request's body!";
        }
    }
    @Operation(summary = "Returns all the facts of the given text in RDF N-triples format using DBpedia model. \n" +
            "It also performs entity recognition, by using any combination of three recognition tools and LODsyndesis,\n" +
            "before generating facts if ‘annotate’ parameter is set to true.")
    @PostMapping(value = "/facts/text/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String GetFacts_text(@RequestBody String Json, HttpServletResponse response) {
        System.out.println("Received : " + Json);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // Convert JSON string to a Map
            Map<String, String> dataMap = objectMapper.readValue(Json, Map.class);

            if(dataMap.get("text") == null){
                response.setStatus(400);
                return "Make sure you provided all the required parameters in your json file (text cannot be null)";
            }

            //All the work happens here
            return getFacts(dataMap, response);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(415);
            return "Please make sure you are providing a valid json format in the request's body!";
        }
    }

     @Operation(summary = "First it fetches an answer from chat gpt for the given question.\n" +
             "Then it gets all the facts of the chat-gpt response in RDF N-triples format using DBpedia model. It also performs entity recognition, by using any combination of three recognition tools and LODsyndesis,\n" +
             "before generating facts if ‘annotate’ parameter is set to true.\n" +
             "After that it validates and returns the facts for each triple received by using  DBpedia or LODsyndesis knowledge graphs.")
     @PostMapping(value = "/facts/GPT/validation", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
     public String ValidateFacts_GPT(@RequestBody String Json, HttpServletResponse response) {
         System.out.println("Received : " + Json);

         ObjectMapper objectMapper = new ObjectMapper();
         try {
             // Convert JSON string to a Map
             Map<String, String> dataMap = objectMapper.readValue(Json, Map.class);

             if(dataMap.get("text") == null){
                 response.setStatus(400);
                 return "Make sure you provided all the required parameters in your json file (text cannot be null)";
             }

             //Call chat-gpt first to get the response that will be fact checked
             String gpt_resp = call_gpt(dataMap, response);
             if(response.getStatus() != 200)
                 return gpt_resp;        //something  went wrong when calling chat-gpt. Stop and return error.

             System.out.println("GPT response to the question : " + gpt_resp);
             dataMap.replace("text", gpt_resp);

             //get triples
             String triples = getFacts(dataMap, response);
             if(response.getStatus() != 200)
                 return triples;    //something went wrong when generating triples, stop and return the error

             //validate facts
             validateFacts vf = new validateFacts();
             return vf.validateTriples(triples, dataMap.get("kg"), response);

         } catch (Exception e) {
             e.printStackTrace();
             response.setStatus(415);
             return "Please make sure you are providing a valid json format in the request's body!";
         }
     }

     @Operation(summary = "Gets all the facts of the given text in RDF N-triples format using DBpedia model. It also performs entity recognition, by using any combination of three recognition tools and LODsyndesis,\n" +
             "before generating facts if ‘annotate’ parameter is set to true.\n" +
             "After that it validates and returns the facts for each triple received by using  DBpedia or LODsyndesis knowledge graphs.\n")
     @PostMapping(value = "/facts/text/validation", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
     public String ValidateFacts_text(@RequestBody String Json, HttpServletResponse response) {
         System.out.println("Received : " + Json);

         ObjectMapper objectMapper = new ObjectMapper();
         try {
             // Convert JSON string to a Map
             Map<String, String> dataMap = objectMapper.readValue(Json, Map.class);

             if(dataMap.get("text") == null){
                 response.setStatus(400);
                 return "Make sure you provided all the required parameters in your json file (text cannot be null)";
             }

             //get triples
             String triples = getFacts(dataMap, response);
             if(response.getStatus() != 200)
                 return triples;    //something went wrong when generating triples, stop and return the error

             //validate facts
             validateFacts vf = new validateFacts();
             return vf.validateTriples(triples, dataMap.get("kg"), response);

         } catch (Exception e) {
             e.printStackTrace();
             response.setStatus(415);
             return "Please make sure you are providing a valid json format in the request's body!";
         }
     }

     @Operation(summary = "Validates the facts for each triple received by using  DBpedia or LODsyndesis knowledge graphs.")
     @PostMapping(value = "/facts/triples/validation", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
     public String ValidateFacts_triples(@RequestBody String Json, HttpServletResponse response) {
         System.out.println("Received : " + Json);

         ObjectMapper objectMapper = new ObjectMapper();
         try {
             // Convert JSON string to a Map
             Map<String, String> dataMap = objectMapper.readValue(Json, Map.class);

             validateFacts vf = new validateFacts();
             return vf.validateTriples(dataMap.get("triples"), dataMap.get("kg"), response);

         } catch (Exception e) {
             e.printStackTrace();
             response.setStatus(415);
             return "Please make sure you are providing a valid json format in the request's body!";
         }
     }

     //used to retrieve facts (generate triples) from chat-gpt
     String getFacts(Map<String, String> dataMap, HttpServletResponse response) throws Exception {
         //Annotate text (if selected) before generating triples
         if(dataMap.get("annotate") != null && dataMap.get("annotate").equals("true")) {
             if(dataMap.get("ERtools") == null){
                 response.setStatus(400);
                 return "To perform Annotation you need to specify ERtools  to be used!";
             }

             LODsyndesis lod = new LODsyndesis();
             String annotated_string = lod.annotate_text(dataMap.get("text"), dataMap.get("ERtools"), response);
             if(response.getStatus() != 200) //something went wrong when calling LODs. Stop & return error
                 return annotated_string;

             System.out.println("Annotated String : " + annotated_string);
         }

         //Call chat-gpt to generate triples
         dataMap.replace("text", "Give me just the RDF N-triples using DBpedia format for the text : " + dataMap.get("text"));

         String gpt_resp = call_gpt(dataMap, response);
         if(response.getStatus() != 200)
             return gpt_resp;        //something  went wrong when calling chat-gpt. Stop and return error.

         //format triples if needed
         System.out.println("Response: "  + gpt_resp);
         if(!gpt_resp.startsWith("<"))
             return format_triples(gpt_resp, response);
         else
             return gpt_resp;
     }

     //Used to get rid of blank lines / lines that don't contain triples
     String format_triples(String text, HttpServletResponse response){

         String[] linesArr = text.split("\n");
         List<String> lines = Arrays.asList(linesArr);

         // Get rid of blank lines / lines that don't contain triples (Sometimes chat-gpt gives some text explanation before each triple)
         List<String> filteredLines = lines.stream()
                 .filter(line -> !line.trim().isEmpty() && line.trim().startsWith("<"))
                 .collect(Collectors.toList());

         // Convert the filtered lines back to a single string
         String filteredText = String.join("\n", filteredLines);

         if(filteredText.length() == 0) {
             response.setStatus(400);
             return "Chat-GPT failed to generate triples, as the provided text might be incomplete. " +
                     "Please provide the full text or specify what information you would like to extract from it.";
         }
         return filteredText;
     }
}
