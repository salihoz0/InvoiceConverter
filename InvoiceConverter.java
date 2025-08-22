import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

public class InvoiceConverter {

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

        // Diagnostic: print the first part (visible and codepoints) to stderr to help
        // debug malformed prefixes
        try {
            String preview = xmlContent.length() > 200 ? xmlContent.substring(0, 200) : xmlContent;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(preview.length(), 80); i++) {
                int cp = preview.charAt(i);
                if (cp < 32 || cp > 126)
                    sb.append(String.format("\\u%04X", cp));
                else
                    sb.append((char) cp);
            }
            System.err.println("[DEBUG] xmlContent preview: '" + sb.toString() + "'");
        } catch (Exception ignore) {
        }

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
            byte[] decoded = Base64.getDecoder().decode(base64);
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
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    public String htmlToPdfBase64(String html) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("wkhtmltopdf", "--encoding", "UTF-8", "-", "-");
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        // Writer thread
        try (OutputStream os = proc.getOutputStream()) {
            os.write(html.getBytes(StandardCharsets.UTF_8));
        }

        // Reader thread
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = proc.getInputStream()) {
            byte[] buffer = new byte[64 * 1024]; // 64KB buffer
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
        }

        int exitCode = proc.waitFor();
        if (exitCode != 0)
            throw new RuntimeException("wkhtmltopdf failed: " + exitCode);

        return Base64.getEncoder().encodeToString(baos.toByteArray());
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
