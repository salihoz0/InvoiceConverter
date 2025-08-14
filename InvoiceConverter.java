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
            NodeList embeddedList = doc.getElementsByTagNameNS("*", "EmbeddedDocumentBinaryObject");
            if (embeddedList.getLength() == 0) {
                throw new Exception("XML içinde <xsl:stylesheet> veya gömülü XSLT bulunamadı!");
            }
            Element embedded = (Element) embeddedList.item(0);
            String base64 = embedded.getTextContent().trim();
            byte[] decoded = Base64.getDecoder().decode(base64);
            String xsltContent = new String(decoded, StandardCharsets.UTF_8);
            Document xsltDoc = parseXmlString(xsltContent);
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
        String wkhtmltopdfPath = System.getenv("WKHTMLTOPDF_PATH");
        if (wkhtmltopdfPath == null || wkhtmltopdfPath.isEmpty()) {
            wkhtmltopdfPath = "wkhtmltopdf";
        }

        ProcessBuilder pb = new ProcessBuilder(wkhtmltopdfPath, "--encoding", "UTF-8", "-", "-");
        pb.redirectErrorStream(true);

        Process proc = pb.start();

        try (OutputStream os = proc.getOutputStream()) {
            os.write(html.getBytes(StandardCharsets.UTF_8));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = proc.getInputStream()) {
            is.transferTo(baos);
        }

        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("wkhtmltopdf failed with exit code " + exitCode);
        }

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
