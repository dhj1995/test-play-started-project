package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.ToXMLContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class TikaPdfExtractor {

    static int count = 0;
    public static String xhtmlContent = "";
    public static HashMap<String, String> hashMap = new HashMap<>();
    public static String url;
    public static String tileId;
    public static String topicBoardId;
    static List<String> embeddedImages = new ArrayList<>();
    public static Boolean extractImages = false;

    public TikaPdfExtractor(String url, String topicBoardId, String tileId, Boolean extractImages){
        this.url = url;
        this.topicBoardId = topicBoardId;
        this.tileId= tileId;
        this.extractImages = extractImages;
    }

    public List<String> getEmbeddedImages(){
        return embeddedImages;
    }

    public String extractPdfContent() throws Exception {
        xhtmlContent = parseDocument();
        for (String key : hashMap.keySet()) {
            String embeddedkey = "embedded:" + key;
            xhtmlContent = xhtmlContent.replaceAll(embeddedkey, hashMap.get(key));
        }
        return xhtmlContent;
    }

    private static void setPdfConfig(ParseContext context) {
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);
        pdfConfig.setExtractUniqueInlineImagesOnly(false);
        pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        context.set(PDFParserConfig.class, pdfConfig);
    }

    private static String parseDocument() {
        String xhtmlContents = "";
        Random rand = new Random();
        AutoDetectParser parser = new AutoDetectParser();
        ToXMLContentHandler handler = new ToXMLContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        EmbeddedDocumentExtractor embeddedDocumentExtractor =
                new EmbeddedDocumentExtractor() {
                    @Override
                    public boolean shouldParseEmbedded(Metadata metadata) {
                        return extractImages;
                    }

                    @Override
                    public void parseEmbedded(InputStream stream, org.xml.sax.ContentHandler handler, Metadata metadata, boolean outputHtml)
                            throws SAXException, IOException {
                        String mimeType = metadata.get("Content-Type");
                        String imageType = mimeType.split("/")[1];
                        Path tempFile = Files.createTempFile("embeddedImageFile", "."+imageType);
                        Files.deleteIfExists(tempFile);
                        Files.copy(stream, tempFile);
                        String resourceName = metadata.get("resourceName");
                        String uploadedImageUrl = getUploadedImageUrl(tempFile.toString());
                        hashMap.put(resourceName, uploadedImageUrl);
                        embeddedImages.add(uploadedImageUrl);
                        count += 1;
                        Files.deleteIfExists(tempFile);
                    }
                };

        context.set(EmbeddedDocumentExtractor.class, embeddedDocumentExtractor);
        context.set(AutoDetectParser.class, parser);

        setPdfConfig(context);
        //InputStream stream = new FileInputStream(path)
        try (InputStream stream = new URL(url).openStream();) {
            parser.parse(stream, handler, metadata, context);
            xhtmlContents = handler.toString();
        } catch (IOException | SAXException | TikaException e) {
            e.printStackTrace();
        }

        return xhtmlContents;
    }

    public static JsonNode uploadImage(String Url, String image_path) throws Exception {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httppost = new HttpPost(Url);
            StringBuilder respline = new StringBuilder();
            String line = "";
            FileBody bin = new FileBody(new File(image_path));

            HttpEntity reqEntity = MultipartEntityBuilder.create()
                    .addPart("file", bin)
                    .build();

            httppost.setEntity(reqEntity);
            try (CloseableHttpResponse response = httpclient.execute(httppost)) {
                HttpEntity resEntity = response.getEntity();
                BufferedReader rd = new BufferedReader(new InputStreamReader(
                        response.getEntity().getContent()));
                while ((line = rd.readLine()) != null) {
                    respline.append(line);
                }

                EntityUtils.consume(resEntity);
            }
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(respline.toString());
        }
    }

    public static String getUploadedImageUrl(String path) {
        try {
            JsonNode jsonNode = uploadImage("http://api.cronycle.com/v10/topic_boards/"+topicBoardId+"/tiles/"+tileId+"/uploads?auth_token=06ec612788d5646a"
                    , path);
            return jsonNode.get("url").asText();
        } catch (Exception e) {
            return null;
        }
    }
}


//http://api.cronycle.com/v10/topic_boards/40977/tiles/6145496/uploads?auth_token=06ec612788d5646a