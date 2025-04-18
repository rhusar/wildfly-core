/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.parsing.XmlConstants.XML_SCHEMA_NAMESPACE;
import static org.jboss.dmr.ModelType.PROPERTY;
import static org.jboss.dmr.ModelType.STRING;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.dmr.ValueExpression;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.projectodd.vdx.core.ErrorType;
import org.projectodd.vdx.core.ValidationError;
import org.projectodd.vdx.core.XMLStreamValidationException;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ParseUtils {

    private ParseUtils() {
    }

    public static Element nextElement(XMLExtendedStreamReader reader) throws XMLStreamException {
        if (reader.nextTag() == END_ELEMENT) {
            return null;
        }

        return Element.forName(reader.getLocalName());
    }

    /**
     * A variation of nextElement that verifies the nextElement is not in a different namespace.
     *
     * @param reader the XmlExtendedReader to read from.
     * @param expectedNamespace the namespace expected.
     * @return the element or null if the end is reached
     * @throws XMLStreamException if the namespace is wrong or there is a problem accessing the reader
     */
    public static Element nextElement(XMLExtendedStreamReader reader, String expectedNamespaceUri) throws XMLStreamException {
        Element element = nextElement(reader);

        if (element == null) {
            return null;
        } else if (element != Element.UNKNOWN
                && expectedNamespaceUri.equals(reader.getNamespaceURI())) {
            return element;
        }

        throw unexpectedElement(reader);
    }

    /**
     * Get an exception reporting an unexpected XML element.
     * @param reader the stream reader
     * @return the exception
     */
    public static XMLStreamException unexpectedElement(final XMLExtendedStreamReader reader) {
        final XMLStreamException ex = ControllerLogger.ROOT_LOGGER.unexpectedElement(reader.getName(), reader.getLocation());

        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.UNEXPECTED_ELEMENT)
                                                        .element(reader.getName()),
                                                ex);
    }

    /**
     * Get an exception reporting an unexpected XML element.
     * @param reader the stream reader
     * @return the exception
     */
    public static XMLStreamException unexpectedElement(final XMLExtendedStreamReader reader, Set<?> possible) {
        final XMLStreamException ex = ControllerLogger.ROOT_LOGGER.unexpectedElement(reader.getName(), asStringList(possible), reader.getLocation());

        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.UNEXPECTED_ELEMENT)
                                                        .element(reader.getName())
                                                        .alternatives(possible.stream().map(Object::toString).collect(Collectors.toSet())),
                                                ex);
    }

    /**
     * Get an exception reporting an element using an unsupported namespace.
     * @param reader the stream reader
     * @return the exception
     */
    public static XMLStreamException unsupportedNamespace(final XMLExtendedStreamReader reader) {
        final XMLStreamException ex = ControllerLogger.ROOT_LOGGER.unsupportedNamespace(reader.getName(), reader.getLocation());

        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.UNSUPPORTED_ELEMENT)
                                                        .element(reader.getName()),
                                                ex);
    }

    /**
     * Get an exception reporting an unexpected end tag for an XML element.
     * @param reader the stream reader
     * @return the exception
     */
    public static XMLStreamException unexpectedEndElement(final XMLExtendedStreamReader reader) {
        return ControllerLogger.ROOT_LOGGER.unexpectedEndElement(reader.getName(), reader.getLocation());
    }

    /**
     * Get an exception reporting an unexpected XML attribute.
     * @param reader the stream reader
     * @param index the attribute index
     * @return the exception
     */
    public static XMLStreamException unexpectedAttribute(final XMLExtendedStreamReader reader, final int index) {
        final XMLStreamException ex = ControllerLogger.ROOT_LOGGER.unexpectedAttribute(reader.getAttributeName(index), reader.getLocation());

        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.UNEXPECTED_ATTRIBUTE)
                                                        .element(reader.getName())
                                                        .attribute(reader.getAttributeName(index)),
                                                ex);
    }

    /**
     * Get an exception reporting an unexpected XML attribute.
     * @param reader the stream reader
     * @param index the attribute index
     * @param possibleAttributes attributes that are expected on this element
     * @return the exception
     */
    public static XMLStreamException unexpectedAttribute(final XMLExtendedStreamReader reader, final int index, Set<?> possibleAttributes) {
        final XMLStreamException ex = ControllerLogger.ROOT_LOGGER.unexpectedAttribute(reader.getAttributeName(index), asStringList(possibleAttributes), reader.getLocation());

        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.UNEXPECTED_ATTRIBUTE)
                                                        .element(reader.getName())
                                                        .attribute(reader.getAttributeName(index))
                                                        .alternatives(possibleAttributes.stream().map(Object::toString).collect(Collectors.toSet())),
                                                ex);
    }

    private static StringBuilder asStringList(Set<?> attributes) {
        final StringBuilder b = new StringBuilder();
        Iterator<?> iterator = attributes.iterator();
        while (iterator.hasNext()) {
            final Object o = iterator.next();
            b.append(o.toString());
            if (iterator.hasNext()) {
                b.append(", ");
            }
        }
        return b;
    }
    /**
     * Get an exception reporting an invalid XML attribute value.
     * @param reader the stream reader
     * @param index the attribute index
     * @return the exception
     */
    public static XMLStreamException invalidAttributeValue(final XMLExtendedStreamReader reader, final int index) {
        final XMLStreamException ex = ControllerLogger.ROOT_LOGGER.invalidAttributeValue(reader.getAttributeValue(index), reader.getAttributeName(index), reader.getLocation());

        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.INVALID_ATTRIBUTE_VALUE)
                                                        .element(reader.getName())
                                                        .attribute(reader.getAttributeName(index))
                                                        .attributeValue(reader.getAttributeValue(index)),
                                                ex);
    }

    private static XMLStreamException wrapMissingRequiredAttribute(final XMLStreamException ex, final XMLStreamReader reader, final Set<String> required) {
        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.REQUIRED_ATTRIBUTE_MISSING)
                                                        .element(reader.getName())
                                                        .alternatives(required),
                                                ex);
    }

    /**
     * Get an exception reporting a missing, required XML attribute.
     * @param reader the stream reader
     * @param required a set of enums whose toString method returns the
     *        attribute name
     * @return the exception
     */
    public static XMLStreamException missingRequired(final XMLExtendedStreamReader reader, final Set<?> required) {
        Set<String> set = new HashSet<>();
        for (Object o : required) {
            String toString = o.toString();
            set.add(toString);
        }
        return wrapMissingRequiredAttribute(ControllerLogger.ROOT_LOGGER.missingRequiredAttributes(asStringList(required),
                                                                                                   reader.getLocation()),
                                            reader,
            set);
    }

    /**
     * Get an exception reporting a missing, required XML attribute.
     * @param reader the stream reader
     * @param required a set of enums whose toString method returns the
     *        attribute name
     * @return the exception
     */
    public static XMLStreamException missingRequired(final XMLExtendedStreamReader reader, final String... required) {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < required.length; i++) {
            final String o = required[i];
            b.append(o);
            if (required.length > i + 1) {
                b.append(", ");
            }
        }

        return wrapMissingRequiredAttribute(ControllerLogger.ROOT_LOGGER.missingRequiredAttributes(b, reader.getLocation()),
                                            reader, new HashSet<>(Arrays.asList(required)));
    }

    /**
     * Get an exception reporting a missing, required XML child element.
     * @param reader the stream reader
     * @param required a set of enums whose toString method returns the
     *        attribute name
     * @return the exception
     */
    public static XMLStreamException missingRequiredElement(final XMLExtendedStreamReader reader, final Set<?> required) {
        final StringBuilder b = new StringBuilder();
        Iterator<?> iterator = required.iterator();
        while (iterator.hasNext()) {
            final Object o = iterator.next();
            b.append(o.toString());
            if (iterator.hasNext()) {
                b.append(", ");
            }
        }
        final XMLStreamException ex = ControllerLogger.ROOT_LOGGER.missingRequiredElements(b, reader.getLocation());

        Set<String> set = new HashSet<>();
        for (Object o : required) {
            String toString = o.toString();
            set.add(toString);
        }
        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.REQUIRED_ELEMENTS_MISSING)
                                                        .element(reader.getName())
                                                        .alternatives(set),
                                                ex);
    }

    /**
     * Get an exception reporting a missing, required XML child element.
     * @param reader the stream reader
     * @param required a set of enums whose toString method returns the
     *        attribute name
     * @return the exception
     */
    public static XMLStreamException missingOneOf(final XMLExtendedStreamReader reader, final Set<?> required) {
        final StringBuilder b = new StringBuilder();
        Iterator<?> iterator = required.iterator();
        while (iterator.hasNext()) {
            final Object o = iterator.next();
            b.append(o.toString());
            if (iterator.hasNext()) {
                b.append(", ");
            }
        }
        final XMLStreamException ex = ControllerLogger.ROOT_LOGGER.missingOneOf(b, reader.getLocation());

        Set<String> set = new HashSet<>();
        for (Object o : required) {
            String toString = o.toString();
            set.add(toString);
        }
        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.REQUIRED_ELEMENT_MISSING)
                                                                    .element(reader.getName())
                                                                    .alternatives(set),
                                                ex);
    }

    /**
     * Checks that the current element has no attributes, throwing an
     * {@link javax.xml.stream.XMLStreamException} if one is found.
     * @param reader the reader
     * @throws javax.xml.stream.XMLStreamException if an error occurs
     */
    public static void requireNoAttributes(final XMLExtendedStreamReader reader) throws XMLStreamException {
        if (reader.getAttributeCount() > 0) {
            throw unexpectedAttribute(reader, 0);
        }
    }

    /**
     * Consumes the remainder of the current element, throwing an
     * {@link javax.xml.stream.XMLStreamException} if it contains any child
     * elements.
     * @param reader the reader
     * @throws javax.xml.stream.XMLStreamException if an error occurs
     */
    public static void requireNoContent(final XMLExtendedStreamReader reader) throws XMLStreamException {
        if (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            throw unexpectedElement(reader);
        }
    }

    /**
     * Require that the namespace of the current element matches the required namespace.
     *
     * @param reader the reader
     * @param requiredNs the namespace required
     * @throws XMLStreamException if the current namespace does not match the required namespace
     */
    public static void requireNamespace(final XMLExtendedStreamReader reader, final String requiredNs) throws XMLStreamException {
        String actualNs = reader.getNamespaceURI();
        if (!actualNs.equals(requiredNs)) {
            throw unexpectedElement(reader);
        }
    }

    /**
     * Get an exception reporting that an attribute of a given name has already
     * been declared in this scope.
     * @param reader the stream reader
     * @param name the name that was redeclared
     * @return the exception
     */
    public static XMLStreamException duplicateAttribute(final XMLExtendedStreamReader reader, final String name) {
        final XMLStreamException ex = ControllerLogger.ROOT_LOGGER.duplicateAttribute(name, reader.getLocation());

        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.DUPLICATE_ATTRIBUTE)
                                                        .element(reader.getName())
                                                        .attribute(new QName(name)),
                                                ex);
    }

    /**
     * Get an exception reporting that an element of a given type and name has
     * already been declared in this scope.
     * @param reader the stream reader
     * @param name the name that was redeclared
     * @return the exception
     */
    public static XMLStreamException duplicateNamedElement(final XMLExtendedStreamReader reader, final String name) {
        final XMLStreamException ex = ControllerLogger.ROOT_LOGGER.duplicateNamedElement(name, reader.getLocation());

        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.DUPLICATE_ELEMENT)
                                                        .element(reader.getName())
                                                        .attribute(QName.valueOf("name"))
                                                        .attributeValue(name),
                                                ex);
    }

    /**
     * Read an element which contains only a single boolean attribute.
     * @param reader the reader
     * @param attributeName the attribute name, usually "value"
     * @return the boolean value
     * @throws javax.xml.stream.XMLStreamException if an error occurs or if the
     *         element does not contain the specified attribute, contains other
     *         attributes, or contains child elements.
     */
    public static boolean readBooleanAttributeElement(final XMLExtendedStreamReader reader, final String attributeName)
            throws XMLStreamException {
        requireSingleAttribute(reader, attributeName);
        final boolean value = Boolean.parseBoolean(reader.getAttributeValue(0));
        requireNoContent(reader);
        return value;
    }

    /**
     * Read an element which contains only a single string attribute.
     * @param reader the reader
     * @param attributeName the attribute name, usually "value" or "name"
     * @return the string value
     * @throws javax.xml.stream.XMLStreamException if an error occurs or if the
     *         element does not contain the specified attribute, contains other
     *         attributes, or contains child elements.
     */
    public static String readStringAttributeElement(final XMLExtendedStreamReader reader, final String attributeName)
            throws XMLStreamException {
        requireSingleAttribute(reader, attributeName);
        final String value = reader.getAttributeValue(0);
        requireNoContent(reader);
        return value;
    }

    /**
     * Read an element which contains only a single list attribute of a given
     * type.
     * @param reader the reader
     * @param attributeName the attribute name, usually "value"
     * @param type the value type class
     * @param <T> the value type
     * @return the value list
     * @throws javax.xml.stream.XMLStreamException if an error occurs or if the
     *         element does not contain the specified attribute, contains other
     *         attributes, or contains child elements.
     */
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    public static <T> List<T> readListAttributeElement(final XMLExtendedStreamReader reader, final String attributeName,
            final Class<T> type) throws XMLStreamException {
        requireSingleAttribute(reader, attributeName);
        // todo: fix this when this method signature is corrected
        final List<T> value = (List<T>) reader.getListAttributeValue(0, type);
        requireNoContent(reader);
        return value;
    }

    public static Property readProperty(final XMLExtendedStreamReader reader) throws XMLStreamException {
        return readProperty(reader, false);
    }

    public static Property readProperty(final XMLExtendedStreamReader reader, boolean supportsExpressions) throws XMLStreamException {
        final int cnt = reader.getAttributeCount();
        String name = null;
        ModelNode value = null;
        for (int i = 0; i < cnt; i++) {
            String uri = reader.getAttributeNamespace(i);
            if (uri != null && !uri.equals(XMLConstants.NULL_NS_URI)) {
                throw unexpectedAttribute(reader, i);
            }
            final String localName = reader.getAttributeLocalName(i);
            if (localName.equals("name")) {
                name = reader.getAttributeValue(i);
            } else if (localName.equals("value")) {
                if (supportsExpressions) {
                    value = parsePossibleExpression(reader.getAttributeValue(i));
                } else {
                    value = new ModelNode(reader.getAttributeValue(i));
                }
            } else {
                throw unexpectedAttribute(reader, i);
            }
        }
        if (name == null) {
            throw missingRequired(reader, Collections.singleton("name"));
        }
        if (reader.next() != END_ELEMENT) {
            throw unexpectedElement(reader);
        }
        return new Property(name, new ModelNode().set(value == null ? new ModelNode() : value));
    }

    /**
     * Read an element which contains only a single list attribute of a given
     * type, returning it as an array.
     * @param reader the reader
     * @param attributeName the attribute name, usually "value"
     * @param type the value type class
     * @param <T> the value type
     * @return the value list as an array
     * @throws javax.xml.stream.XMLStreamException if an error occurs or if the
     *         element does not contain the specified attribute, contains other
     *         attributes, or contains child elements.
     */
    @SuppressWarnings({ "unchecked" })
    public static <T> T[] readArrayAttributeElement(final XMLExtendedStreamReader reader, final String attributeName,
            final Class<T> type) throws XMLStreamException {
        final List<T> list = readListAttributeElement(reader, attributeName, type);
        return list.toArray((T[]) Array.newInstance(type, list.size()));
    }

    /**
     * Require that the current element have only a single attribute with the
     * given name.
     * @param reader the reader
     * @param attributeName the attribute name
     * @throws javax.xml.stream.XMLStreamException if an error occurs
     */
    public static void requireSingleAttribute(final XMLExtendedStreamReader reader, final String attributeName)
            throws XMLStreamException {
        final int count = reader.getAttributeCount();
        if (count == 0) {
            throw missingRequired(reader, Collections.singleton(attributeName));
        }
        requireNoNamespaceAttribute(reader, 0);
        if (!attributeName.equals(reader.getAttributeLocalName(0))) {
            throw unexpectedAttribute(reader, 0);
        }
        if (count > 1) {
            throw unexpectedAttribute(reader, 1);
        }
    }

    /**
     * Require all the named attributes, returning their values in order.
     * @param reader the reader
     * @param attributeNames the attribute names
     * @return the attribute values in order
     * @throws javax.xml.stream.XMLStreamException if an error occurs
     */
    public static String[] requireAttributes(final XMLExtendedStreamReader reader, final String... attributeNames)
            throws XMLStreamException {
        final int length = attributeNames.length;
        final String[] result = new String[length];
        for (int i = 0; i < length; i++) {
            final String name = attributeNames[i];
            final String value = reader.getAttributeValue(null, name);
            if (value == null) {
                throw missingRequired(reader, Collections.singleton(name));
            }
            result[i] = value;
        }
        return result;
    }

    public static boolean isXmlNamespaceAttribute(final XMLExtendedStreamReader reader, final int index) {
        String namespace = reader.getAttributeNamespace(index);

        return XML_SCHEMA_NAMESPACE.equals(namespace);
    }

    public static boolean isNoNamespaceAttribute(final XMLExtendedStreamReader reader, final int index) {
        String namespace = reader.getAttributeNamespace(index);
        // FIXME when STXM-8 is done, remove the null check
        return namespace == null || XMLConstants.NULL_NS_URI.equals(namespace);
    }

    public static void requireNoNamespaceAttribute(final XMLExtendedStreamReader reader, final int index)
            throws XMLStreamException {
        if (!isNoNamespaceAttribute(reader, index)) {
            throw unexpectedAttribute(reader, index);
        }
    }

    public static ModelNode parseAttributeValue(final String value, final boolean isExpressionAllowed, final ModelType attributeType) {
        final String trimmed = value == null ? null : value.trim();
        ModelNode node;
        if (trimmed != null) {
            if (isExpressionAllowed && isExpression(value)) {
                if(attributeType == STRING || attributeType == PROPERTY) {
                    node = new ModelNode(new ValueExpression(value));
                } else {
                    node = new ModelNode(new ValueExpression(trimmed));
                }
            } else {
                if(attributeType == STRING || attributeType == PROPERTY) {
                    node = new ModelNode().set(value);
                } else {
                    node = new ModelNode().set(trimmed);
                }
            }
            if (node.getType() != ModelType.EXPRESSION) {
                // Convert the string to the expected type
                // This is a convenience only and is not a requirement
                // of this method
                try {
                    switch (attributeType) {
                        case BIG_DECIMAL:
                            node.set(node.asBigDecimal());
                            break;
                        case BIG_INTEGER:
                            node.set(node.asBigInteger());
                            break;
                        case BOOLEAN:
                            node.set(node.asBoolean());
                            break;
                        case BYTES:
                            node.set(node.asBytes());
                            break;
                        case DOUBLE:
                            node.set(node.asDouble());
                            break;
                        case INT:
                            node.set(node.asInt());
                            break;
                        case LONG:
                            node.set(node.asLong());
                            break;
                    }
                } catch (IllegalArgumentException iae) {
                    // ignore and return the unconverted node
                }
            }
        } else {
            node = new ModelNode();
        }
        return node;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isExpression(String value) {
        int openIdx = value.indexOf("${");
        return openIdx > -1 && value.lastIndexOf('}') > openIdx;
    }

    public static ModelNode parsePossibleExpression(String value) {
        ModelNode result = new ModelNode();
        if (isExpression(value)) {
            result.set(new ValueExpression(value));
        }
        else {
            result.set(value);
        }
        return result;
    }

    /**
     * Get an exception reporting a missing, required XML attribute.
     * @param reader the stream reader
     * @param supportedElement the element that is to be used in place of the unsupported one.
     * @return the exception
     */
    public static XMLStreamException unsupportedElement(final XMLExtendedStreamReader reader, String supportedElement) {
        XMLStreamException ex = ControllerLogger.ROOT_LOGGER.unsupportedElement(
                new QName(reader.getNamespaceURI(), reader.getLocalName(),reader.getPrefix()), reader.getLocation(), supportedElement);

        return new XMLStreamValidationException(ex.getMessage(),
                                                ValidationError.from(ex, ErrorType.UNSUPPORTED_ELEMENT)
                                                        .element(reader.getName())
                                                        .alternatives(Set.of(supportedElement)),
                                                ex);
    }

    /**
     * Creates an exception reporting that a given element(s) did not appear a sufficient number of times.
     * @param reader the stream reader
     * @param elementName the element name
     * @return a validation exception
     */
    public static XMLStreamException minOccursNotReached(XMLExtendedStreamReader reader, Collection<QName> names, XMLCardinality cardinality) {
        XMLStreamException e = new XMLStreamException(ControllerLogger.ROOT_LOGGER.minOccursNotReached(names, cardinality.getMinOccurs()), reader.getLocation());
        return createValidationException(e, ErrorType.REQUIRED_ELEMENT_MISSING, names);
    }

    /**
     * Creates an exception reporting that a given element appeared too many times.
     * @param reader the stream reader
     * @param elementName the element name
     * @return a validation exception
     */
    public static XMLStreamException maxOccursExceeded(XMLExtendedStreamReader reader, Collection<QName> names, XMLCardinality cardinality) {
        XMLStreamException e = new XMLStreamException(ControllerLogger.ROOT_LOGGER.maxOccursExceeded(names, cardinality.getMaxOccurs().orElse(Integer.MAX_VALUE)), reader.getLocation());
        return createValidationException(e, ErrorType.DUPLICATE_ELEMENT, names);
    }

    private static XMLStreamValidationException createValidationException(XMLStreamException e, ErrorType type, Collection<QName> elements) {
        ValidationError error = ValidationError.from(e, type);
        Iterator<QName> names = elements.iterator();
        if (names.hasNext()) {
            error.element(names.next());
        }
        if (names.hasNext()) {
            Set<String> alternatives = new TreeSet<>();
            do {
                alternatives.add(names.next().getLocalPart());
            } while (names.hasNext());
            error.alternatives(alternatives);
        }
        return new XMLStreamValidationException(e.getMessage(), error, e);
    }
}
