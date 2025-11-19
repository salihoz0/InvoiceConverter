import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

public class InvoiceConverter {

    /**
     * Harici bir URL'den resim veya font indirir ve base64'e çevirir
     * Timeout: 5 saniye
     */
    private String downloadAndConvertToBase64(String urlString) {
        try {
            // HTTP redirect takibi için max 3 redirect izin ver
            String currentUrl = urlString;
            int redirectCount = 0;
            final int MAX_REDIRECTS = 3;

            while (redirectCount < MAX_REDIRECTS) {
                URL url = new URL(currentUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(1500); // 1.5 saniye timeout
                connection.setReadTimeout(1500);
                connection.setRequestMethod("GET");

                // Tam browser headers ekle
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                connection.setRequestProperty("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7");
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setRequestProperty("Referer", "https://mutabakat.aydemperakende.com.tr/");
                connection.setInstanceFollowRedirects(false); // Manuel redirect takibi

                int responseCode = connection.getResponseCode();

                // Redirect kontrolü
                if (responseCode == 301 || responseCode == 302 || responseCode == 303 || responseCode == 307
                        || responseCode == 308) {
                    String newUrl = connection.getHeaderField("Location");
                    if (newUrl == null) {
                        System.err.println("Redirect URL bulunamadı: " + currentUrl);
                        connection.disconnect();
                        return "";
                    }
                    // Relative URL ise absolute yap
                    if (!newUrl.startsWith("http")) {
                        URL base = new URL(currentUrl);
                        newUrl = new URL(base, newUrl).toString();
                    }
                    System.err.println("Redirect takip ediliyor: " + newUrl);
                    currentUrl = newUrl;
                    redirectCount++;
                    connection.disconnect();
                    continue;
                }

                if (responseCode == 200) {
                    try (InputStream is = connection.getInputStream();
                            ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }

                        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

                        // MIME type'ı belirle
                        String contentType = connection.getContentType();
                        if (contentType == null || contentType.contains(";")) {
                            if (urlString.toLowerCase().endsWith(".jpg") || urlString.toLowerCase().endsWith(".jpeg")) {
                                contentType = "image/jpeg";
                            } else if (urlString.toLowerCase().endsWith(".png")) {
                                contentType = "image/png";
                            } else if (urlString.toLowerCase().endsWith(".ttf")) {
                                contentType = "font/ttf";
                            } else if (contentType != null && contentType.contains(";")) {
                                contentType = contentType.split(";")[0].trim();
                            }
                        }

                        connection.disconnect();
                        return "data:" + contentType + ";base64," + base64;
                    }
                } else {
                    System.err.println("URL indirilemedi (HTTP " + responseCode + "): " + urlString);
                    connection.disconnect();
                    return "";
                }
            }

            System.err.println("Çok fazla redirect: " + urlString);
            return "";

        } catch (Exception e) {
            System.err.println("URL indirilirken hata oluştu: " + urlString + " - " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * HTML içindeki harici URL'leri bulup base64 data URI'lere çevirir
     */
    private String convertExternalUrlsToBase64(String html) {
        // Resim src="http..." pattern'ini bul
        Pattern imgPattern = Pattern.compile("src=\"(https?://[^\"]+)\"");
        Matcher imgMatcher = imgPattern.matcher(html);
        StringBuffer result = new StringBuffer();

        while (imgMatcher.find()) {
            String url = imgMatcher.group(1);
            System.err.println("Harici resim indiriliyor: " + url);
            String base64DataUri = downloadAndConvertToBase64(url);
            if (!base64DataUri.isEmpty()) {
                imgMatcher.appendReplacement(result, "src=\"" + Matcher.quoteReplacement(base64DataUri) + "\"");
                System.err.println("✓ Resim base64'e çevrildi");
            } else {
                imgMatcher.appendReplacement(result, "src=\"\"");
                System.err.println("✗ Resim indirilemedi, boş bırakıldı");
            }
        }
        imgMatcher.appendTail(result);
        html = result.toString();

        // CSS içindeki url('http...') pattern'ini bul (fontlar için)
        Pattern cssUrlPattern = Pattern.compile("url\\(['\"]?(https?://[^'\")]+)['\"]?\\)");
        Matcher cssUrlMatcher = cssUrlPattern.matcher(html);
        result = new StringBuffer();

        while (cssUrlMatcher.find()) {
            String url = cssUrlMatcher.group(1);
            System.err.println("Harici font indiriliyor: " + url);
            String base64DataUri = downloadAndConvertToBase64(url);
            if (!base64DataUri.isEmpty()) {
                cssUrlMatcher.appendReplacement(result, "url('" + Matcher.quoteReplacement(base64DataUri) + "')");
                System.err.println("✓ Font base64'e çevrildi");
            } else {
                cssUrlMatcher.appendReplacement(result, "/* font yüklenemedi */");
                System.err.println("✗ Font indirilemedi");
            }
        }
        cssUrlMatcher.appendTail(result);

        return result.toString();
    }

    private Document parseXmlString(String xmlContent) throws Exception {
        if (xmlContent == null) {
            throw new IllegalArgumentException("xmlContent is null");
        }

        // Remove UTF-8 BOM if present
        xmlContent = xmlContent.replaceAll("^\\uFEFF", "");

        // If there are any stray characters before the first '<', strip them.
        // This prevents errors like: "Content is not allowed in prolog." when
        // the input contains logging, invisible chars or a malformed prefix.
        int firstLt = xmlContent.indexOf('<');
        if (firstLt > 0) {
            xmlContent = xmlContent.substring(firstLt);
        }

        // Baştaki boşluk/newline karakterlerini sil
        xmlContent = xmlContent.trim();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        try {
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignore) {
        }
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        try (StringReader sr = new StringReader(xmlContent)) {
            return dBuilder.parse(new InputSource(sr));
        }
    }

    public String transformXmlToHtml(String xmlContent) throws Exception {
        // 1️⃣ XSLTC yerine klasik Transformer kullanmak için sistem property
        System.setProperty("javax.xml.transform.TransformerFactory",
                "org.apache.xalan.processor.TransformerFactoryImpl");

        // 2️⃣ XML parse işlemi
        Document doc = parseXmlString(xmlContent);

        NodeList xslList = doc.getElementsByTagNameNS("http://www.w3.org/1999/XSL/Transform", "stylesheet");
        Transformer transformer;

        if (xslList.getLength() > 0) {
            Node xslNode = xslList.item(0);
            DOMSource xsltSource = new DOMSource(xslNode);
            xslNode.getParentNode().removeChild(xslNode);
            TransformerFactory factory = TransformerFactory.newInstance();
            transformer = factory.newTransformer(xsltSource);
        } else {
            // Try to find an EmbeddedDocumentBinaryObject that actually contains XSLT.
            // Prefer AdditionalDocumentReference elements with DocumentType == 'XSLT'.
            NodeList addRefs = doc.getElementsByTagNameNS("*", "AdditionalDocumentReference");
            Element embedded = null;
            for (int i = 0; i < addRefs.getLength(); i++) {
                Element ar = (Element) addRefs.item(i);
                NodeList dtList = ar.getElementsByTagNameNS("*", "DocumentType");
                if (dtList.getLength() > 0) {
                    String dt = dtList.item(0).getTextContent();
                    if (dt != null && dt.trim().equalsIgnoreCase("XSLT")) {
                        NodeList embs = ar.getElementsByTagNameNS("*", "EmbeddedDocumentBinaryObject");
                        if (embs.getLength() > 0) {
                            embedded = (Element) embs.item(0);
                            break;
                        }
                    }
                }
            }

            // Fallback heuristics: look for mimeCode, filename, or content that looks like
            // XML/XSL
            if (embedded == null) {
                NodeList embList = doc.getElementsByTagNameNS("*", "EmbeddedDocumentBinaryObject");
                for (int i = 0; i < embList.getLength(); i++) {
                    Element e = (Element) embList.item(i);
                    String mime = e.getAttribute("mimeCode");
                    String filename = e.getAttribute("filename");
                    String text = e.getTextContent().trim();
                    if ((mime != null && mime.toLowerCase().contains("xsl")) ||
                            (filename != null && (filename.toLowerCase().endsWith(".xsl")
                                    || filename.toLowerCase().endsWith(".xslt")))
                            ||
                            (text.startsWith("<?xml") && text.contains("<xsl:stylesheet"))) {
                        embedded = e;
                        break;
                    }
                }
            }

            if (embedded == null) {
                throw new Exception("XML içinde <xsl:stylesheet> veya gömülü XSLT bulunamadı!");
            }

            String base64 = embedded.getTextContent().trim();

            // Safe Base64 decoding - remove invalid characters
            base64 = base64.replaceAll("[^A-Za-z0-9+/=]", "");

            // Add padding if necessary
            int padding = 4 - (base64.length() % 4);
            if (padding != 4) {
                base64 += "=".repeat(padding);
            }

            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                throw new Exception("Base64 decode hatası: " + e.getMessage() + " - Content: " +
                        base64.substring(0, Math.min(50, base64.length())));
            }
            String xsltContent = new String(decoded, StandardCharsets.UTF_8);
            Document xsltDoc;
            try {
                xsltDoc = parseXmlString(xsltContent);
            } catch (Exception ex) {
                throw new Exception("Gömülü XSLT çözümleme hatası: " + ex.getMessage(), ex);
            }
            TransformerFactory factory = TransformerFactory.newInstance();
            transformer = factory.newTransformer(new DOMSource(xsltDoc));
        }

        DOMSource xmlSource = new DOMSource(doc);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            StreamResult result = new StreamResult(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
            transformer.transform(xmlSource, result);
            String htmlOutput = baos.toString(StandardCharsets.UTF_8);

            // Harici URL'leri base64'e çevir (wkhtmltopdf'in takılmaması için)
            System.err.println("HTML içindeki harici kaynaklar base64'e çevriliyor...");
            htmlOutput = convertExternalUrlsToBase64(htmlOutput);
            System.err.println("Dönüşüm tamamlandı.");

            return htmlOutput;
        }
    }

    public String htmlToPdfBase64(String html) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "wkhtmltopdf",
                "--encoding", "UTF-8",
                "--enable-local-file-access", // Yerel dosya erişimine izin ver
                "--quiet", // Daha az verbose çıktı
                "--page-size", "A4", // A4 sayfa boyutu
                "--margin-top", "5mm", // Minimal üst margin
                "--margin-bottom", "5mm", // Minimal alt margin
                "--margin-left", "5mm", // Minimal sol margin
                "--margin-right", "5mm", // Minimal sağ margin
                "--zoom", "1.0", // Zoom seviyesi
                "--enable-smart-shrinking", // İçeriği sayfaya sığdırmaya çalış
                "--no-stop-slow-scripts", // JS timeout'larını kapat
                "-", "-");
        pb.redirectErrorStream(false); // stderr'i ayrı tut
        Process proc = pb.start();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        // Async olarak stdout oku (PDF içeriği)
        Thread outputReader = new Thread(() -> {
            try (InputStream is = proc.getInputStream()) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
            } catch (IOException e) {
                // Ignore
            }
        });

        // Async olarak stderr oku (hata/uyarı mesajları)
        Thread errorReader = new Thread(() -> {
            try (InputStream is = proc.getErrorStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    errorStream.write(buffer, 0, read);
                }
            } catch (IOException e) {
                // Ignore
            }
        });

        outputReader.start();
        errorReader.start();

        // HTML'i stdin'e yaz
        try (OutputStream os = proc.getOutputStream()) {
            os.write(html.getBytes(StandardCharsets.UTF_8));
        }

        // 10 saniye timeout - eğer işlem bu sürede bitmezse öldür
        boolean finished = proc.waitFor(10, TimeUnit.SECONDS);

        // Thread'lerin bitmesini bekle
        outputReader.join(1000);
        errorReader.join(1000);

        if (!finished) {
            proc.destroyForcibly();
            String errorOutput = errorStream.toString(StandardCharsets.UTF_8);
            throw new RuntimeException(
                    "wkhtmltopdf timeout (10 saniye): İşlem çok uzun sürdü ve durduruldu. Stderr: " + errorOutput);
        }

        int exitCode = proc.exitValue();
        if (exitCode != 0) {
            String errorOutput = errorStream.toString(StandardCharsets.UTF_8);
            throw new RuntimeException("wkhtmltopdf failed with exit code " + exitCode + ". Stderr: " + errorOutput);
        }

        byte[] pdfBytes = baos.toByteArray();
        if (pdfBytes.length == 0) {
            String errorOutput = errorStream.toString(StandardCharsets.UTF_8);
            throw new RuntimeException("wkhtmltopdf produced empty PDF. Stderr: " + errorOutput);
        }

        return Base64.getEncoder().encodeToString(pdfBytes);
    }

    public String convert(String xmlInput, String mode, boolean isBase64Input) throws Exception {
        String xmlContent = isBase64Input
                ? new String(Base64.getDecoder().decode(xmlInput), StandardCharsets.UTF_8)
                : xmlInput;

        String html = transformXmlToHtml(xmlContent);

        if ("html".equalsIgnoreCase(mode)) {
            return html;
        } else if ("pdf".equalsIgnoreCase(mode)) {
            return htmlToPdfBase64(html);
        } else {
            throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp InvoiceConverter.jar InvoiceConverter <mode> [isBase64Input]");
            System.exit(1);
        }

        String mode = args[0];
        boolean isBase64Input = args.length >= 2 && Boolean.parseBoolean(args[1]);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            StringBuilder xmlBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                xmlBuilder.append(line).append("\n");
            }
            String xmlInput = xmlBuilder.toString();

            InvoiceConverter converter = new InvoiceConverter();
            String result = converter.convert(xmlInput, mode, isBase64Input);
            System.out.print(result);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
