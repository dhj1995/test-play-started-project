package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.TikaPdfExtractor;

import java.util.List;

public class PdfExtractionController extends Controller {
    public Result getExtractedPdfContent(Http.Request request){
        String extractedContent;
        List<String> embeddedImages ;
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        String tileId;
        String topicBoardId;
        String url;
        Boolean extractImages;

        try{
            JsonNode requestJson = request.body().asJson();
            tileId = requestJson.get("tile_id").asText();
            topicBoardId = requestJson.get("topic_board_id").asText();
            url = requestJson.get("pdf_url").asText();
            extractImages = requestJson.has("extract_images") ? requestJson.get("extract_images").asBoolean() : false;
            TikaPdfExtractor tikaPdfExtractor = new TikaPdfExtractor(url, topicBoardId, tileId, extractImages);
            extractedContent = tikaPdfExtractor.extractPdfContent();
            embeddedImages = tikaPdfExtractor.getEmbeddedImages();
            result.put("status_code", 200);
            ObjectNode data = mapper.createObjectNode();
            data.put("content", extractedContent);
            //System.out.println(extractedContent);
            data.putPOJO("embeddedImages", embeddedImages);
            //data.put("embeddedImages", Json.toJson(embeddedImages));
            //result.put("data",data);
            result.putPOJO("data",data);
        }catch (Exception e){
            result.put("error", e.toString());
        }
        return ok(result);
    }
}

