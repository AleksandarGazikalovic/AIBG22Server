package aibg.serverv2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Test {
    public static void main(String[] args) {
        try {
            String x = "{\"phoneNumbers\":[1,2,3,4]}";
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readValue(x, JsonNode.class);

            System.out.println(node.get("phoneNumbers").toString().replaceAll("^\\[|]$", ""));
        }catch (Exception ex){
            System.out.println("test");
            System.out.println(ex.getMessage());
        }
    }
}
