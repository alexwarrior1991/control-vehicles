package com.alejandro.controlvehicles.service.imports;

import com.alejandro.controlvehicles.service.ValidationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The first sheet of an .xlsx with the JDK alone (an .xlsx is a zip of XML files), so the application does not
 * carry Apache POI for one screen. Text cells come from the shared strings or inline; numbers are written plain
 * ("120500", "45.3") and dates stay Excel day numbers, which {@code Inputs.dateTime} understands.
 */
public final class XlsxReader {

    private XlsxReader() {
    }

    public static TabularFile read(String name, byte[] bytes) {
        Map<String, byte[]> parts = unzip(bytes);
        byte[] sheet = parts.get("xl/worksheets/sheet1.xml");
        if (sheet == null) {
            sheet = parts.entrySet().stream().filter(e -> e.getKey().startsWith("xl/worksheets/sheet"))
                    .map(Map.Entry::getValue).findFirst()
                    .orElseThrow(() -> new ValidationException(name + " has no sheet"));
        }
        List<String> shared = sharedStrings(parts.get("xl/sharedStrings.xml"));
        Document document = parse(sheet, name);
        List<List<String>> lines = new ArrayList<>();
        NodeList rows = document.getElementsByTagNameNS("*", "row");
        for (int r = 0; r < rows.getLength(); r++) {
            Element row = (Element) rows.item(r);
            List<String> cells = new ArrayList<>();
            NodeList cs = row.getElementsByTagNameNS("*", "c");
            for (int c = 0; c < cs.getLength(); c++) {
                Element cell = (Element) cs.item(c);
                int column = columnIndex(cell.getAttribute("r"), cells.size());
                while (cells.size() < column) {
                    cells.add("");
                }
                cells.add(value(cell, shared));
            }
            lines.add(cells);
        }
        return TabularFile.of(name, lines);
    }

    private static String value(Element cell, List<String> shared) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            return text(cell, "t");
        }
        String raw = text(cell, "v");
        if (raw.isEmpty()) {
            return "";
        }
        if ("s".equals(type)) {
            int index = Integer.parseInt(raw.trim());
            return index < shared.size() ? shared.get(index) : "";
        }
        if (type.isEmpty() || "n".equals(type)) {
            try {
                // Excel guarda 40.12 como 40.120000000000005: se lee con sus 15 cifras, como lo enseña.
                return new BigDecimal(raw.trim()).round(new MathContext(15)).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                return raw;
            }
        }
        return raw;
    }

    private static List<String> sharedStrings(byte[] xml) {
        List<String> strings = new ArrayList<>();
        if (xml == null) {
            return strings;
        }
        NodeList items = parse(xml, "sharedStrings").getElementsByTagNameNS("*", "si");
        for (int i = 0; i < items.getLength(); i++) {
            strings.add(text((Element) items.item(i), "t"));
        }
        return strings;
    }

    /** The text of every {@code tag} inside the element, joined (rich text is split in runs). */
    private static String text(Element element, String tag) {
        NodeList nodes = element.getElementsByTagNameNS("*", tag);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < nodes.getLength(); i++) {
            text.append(nodes.item(i).getTextContent());
        }
        return text.toString();
    }

    /** "C7" → 2; without a reference, the next column. */
    static int columnIndex(String reference, int next) {
        if (reference == null || reference.isEmpty()) {
            return next;
        }
        int index = 0;
        for (char c : reference.toCharArray()) {
            if (!Character.isLetter(c)) {
                break;
            }
            index = index * 26 + (Character.toUpperCase(c) - 'A' + 1);
        }
        return index - 1;
    }

    private static Map<String, byte[]> unzip(byte[] bytes) {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                parts.put(entry.getName(), zip.readAllBytes());
            }
        } catch (IOException e) {
            throw new ValidationException("Not a readable .xlsx file: " + e.getMessage());
        }
        if (parts.isEmpty()) {
            throw new ValidationException("Not a readable .xlsx file");
        }
        return parts;
    }

    private static Document parse(byte[] xml, String what) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream in = new ByteArrayInputStream(xml)) {
                return builder.parse(in);
            }
        } catch (Exception e) {
            throw new ValidationException("Cannot read " + what + ": " + e.getMessage());
        }
    }
}
