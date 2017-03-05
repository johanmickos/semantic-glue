package edu.kth.wsglue.parsing.util;

import edu.kth.wsglue.models.wsdl.MessageField;
import edu.kth.wsglue.parsing.comparators.FieldGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;

/**
 * Helper class for managing nodes in a document and caching results.
 */
public class WSDLHelper {
    private static final Logger log = LoggerFactory.getLogger(WSDLHelper.class.getName());

    private Document document;

    private Map<String, Element> elementsCache = new HashMap<>();
    private boolean staleCache = true;

    public WSDLHelper() {}

    /**
     * Clears any built-up caches and invalidates the document.
     */
    public void clear() {
        document = null;
        elementsCache.clear();
        staleCache = false;
    }

    public void updateDocument(Document doc) {
        setDocument(doc);
        buildElementsCache();
    }
    private void setDocument(Document doc) {
        document = doc;
        staleCache = true;
    }

    /**
     * Finds all elements (simple, complex, element, message, part) and stores them in in-memory map.
     */
    private void buildElementsCache() {
        NodeList complexTypes = document.getElementsByTagNameNS("*","complexType");
        NodeList simpleTypes = document.getElementsByTagNameNS("*","simpleType");
        NodeList elements = document.getElementsByTagNameNS("*","element");
        NodeList messages = document.getElementsByTagNameNS("*","message");
        NodeList parts = document.getElementsByTagNameNS("*","part");
        elementsCache.putAll(nodeListToNameMap(complexTypes));
        elementsCache.putAll(nodeListToNameMap(simpleTypes));
        elementsCache.putAll(nodeListToNameMap(elements));
        elementsCache.putAll(nodeListToNameMap(messages));
        elementsCache.putAll(nodeListToNameMap(parts));
        staleCache = false;
    }

    private Map<String, Element> nodeListToNameMap(NodeList elements) {
        Map<String, Element> rv = new HashMap<>();
        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            String tag = new TagName(el.getTagName()).getName();
            String name = el.getAttribute("name");
            if (name != null) {
                rv.put(generateCacheKey(tag, name), el);
                rv.put(name, el);
            }
        }
        return rv;
    }


    /**
     * Retrieves the element from the cache.
     * If the cache is stale for this document, it rebuilds it before retrieving.
     * @param name the name to look up
     * @return the element (or null if it doesn't exist)
     */
    public Element findElementByName(String name) {
        if (staleCache) {
            log.warn("Forcing a rebuild of the elements cache");
            buildElementsCache();
        }
        return elementsCache.get(name);
    }

    /**
     * Retrieves the element from the cache, prioritizing on tag type.
     * If the cache is stale for this document, it rebuilds it before retrieving.
     * @param expectedTag the tag type one expects for this element
     * @param name the name to look up
     * @return the element (or null if it doesn't exist)
     */
    public Element findElementByTagAndName(String expectedTag, String name) {
        if (staleCache) {
            log.warn("Forcing a rebuild of the elements cache");
            buildElementsCache();
        }
        // TODO Refactor key-gen into function
        return elementsCache.get(generateCacheKey(expectedTag, name));
    }

    /**
     * Recursively looks up and flattens an element into its most basic fields and primitive types.
     * @param el the element to flatten
     * @return a set of the names of the underlying fields
     */
    public Set<MessageField> flatten(FieldGenerator fg, Element el) {
        Set<MessageField> rv = new HashSet<>();
        if (el == null) {
            return rv;
        }
        log.debug("Flattening " + el.getAttribute("name"));
        String typeCheck = el.getAttribute("type");
        if (typeCheck != null && !Objects.equals(typeCheck, "")) {
            TagName typeTag = new TagName(typeCheck);
            if (WSDLUtil.isPrimitiveType(typeTag.getName())) {
                // XXX TODO
                MessageField field = fg.generate(el.getAttribute("name"), el);
                rv.add(field);
            } else {
                log.debug("Looking up type: " + typeTag.getName());
                Element check = findElementByTagAndName("complexType", typeTag.getName());
                if (check == null) {
                    check = findElementByTagAndName("simpleType", typeTag.getName());
                }
                if (check == null) {
                    check = findElementByTagAndName("element", typeTag.getName());
                }
                if (check == null) {
                    // Now we're in trouble!
                    check = findElementByName(typeTag.getName());
                }
                rv.addAll(flatten(fg, check));
            }
        } else {
            // Handle complex case
            NodeList children = el.getElementsByTagNameNS("*", "element");
            for (int i = 0; i < children.getLength(); i++) {
                rv.addAll(flatten(fg, (Element) children.item(i)));
            }
        }
        return rv;
    }

    private String generateCacheKey(String expectedTag, String name) {
        return expectedTag + ":" + name;
    }


}
