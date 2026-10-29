package ac.at.tgm.submit.user;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/** Provides one hardened XML parser configuration for all package parts. */
final class TgmSecureXml {
    private TgmSecureXml() {
    }

    static Document parse(byte[] bytes, Path path)
            throws SAXException, IOException, javax.xml.parsers.ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try {
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (SAXException exception) {
            throw new SAXException("XML parsing failed in workbook " + path + ".", exception);
        }
    }
}
