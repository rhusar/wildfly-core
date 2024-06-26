/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.readArrayAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.readProperty;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.remoting.CommonAttributes.AUTHENTICATION_PROVIDER;
import static org.jboss.as.remoting.CommonAttributes.CONNECTOR;
import static org.jboss.as.remoting.CommonAttributes.INCLUDE_MECHANISMS;
import static org.jboss.as.remoting.CommonAttributes.PROPERTY;
import static org.jboss.as.remoting.CommonAttributes.SOCKET_BINDING;
import static org.jboss.as.remoting.CommonAttributes.VALUE;

import java.util.EnumSet;
import java.util.List;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * The root element parser for the Remoting subsystem.
 */
class RemotingSubsystem10Parser implements XMLStreamConstants, XMLElementReader<List<ModelNode>> {

    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {

        final PathAddress address = PathAddress.pathAddress(SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);
        final ModelNode subsystem = Util.createAddOperation(address);
        list.add(subsystem);

        requireNoAttributes(reader);

        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case WORKER_THREAD_POOL:
                    parseWorkerThreadPool(reader, subsystem);
                    break;
                case CONNECTOR: {
                    // Add connector updates
                    parseConnector(reader, address.toModelNode(), list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
            break;
        }

        // Apply magic default worker specified by legacy schema versions
        if (!subsystem.hasDefined(RemotingSubsystemRootResource.WORKER.getName())) {
            subsystem.get(RemotingSubsystemRootResource.WORKER.getName()).set(RemotingSubsystemRootResource.LEGACY_DEFAULT_WORKER);
        }
    }

    /**
     * Adds the worker thread pool attributes to the subysystem add method
     */
    void parseWorkerThreadPool(final XMLExtendedStreamReader reader, final ModelNode subsystemAdd) throws XMLStreamException {
        // Ignore attributes
        requireNoContent(reader);
    }

    void parseConnector(XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        String name = null;
        String socketBinding = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.SOCKET_BINDING);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case SOCKET_BINDING: {
                    socketBinding = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        assert name != null;
        assert socketBinding != null;

        final ModelNode connector = new ModelNode();
        connector.get(OP).set(ADD);
        connector.get(OP_ADDR).set(address).add(CONNECTOR, name);
        // requestProperties.get(NAME).set(name); // Name is part of the address
        connector.get(SOCKET_BINDING).set(socketBinding);
        list.add(connector);

        // Handle nested elements.
        final EnumSet<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (visited.contains(element)) {
                throw unexpectedElement(reader);
            }
            visited.add(element);
            switch (element) {
                case SASL: {
                    parseSaslElement(reader, connector.get(OP_ADDR), list);
                    break;
                }
                case PROPERTIES: {
                    parseProperties(reader, connector.get(OP_ADDR), list);
                    break;
                }
                case AUTHENTICATION_PROVIDER: {
                    connector.get(AUTHENTICATION_PROVIDER).set(readStringAttributeElement(reader, "name"));
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
            break;
        }
    }

    void parseSaslElement(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        final ModelNode saslElement = new ModelNode();
        saslElement.get(OP).set(ADD);
        saslElement.get(OP_ADDR).set(address).add(SaslResource.SASL_CONFIG_PATH.getKey(), SaslResource.SASL_CONFIG_PATH.getValue());
        list.add(saslElement);

        // No attributes
        final int count = reader.getAttributeCount();
        if (count > 0) {
            throw unexpectedAttribute(reader, 0);
        }
        // Nested elements
        final EnumSet<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (visited.contains(element)) {
                throw unexpectedElement(reader);
            }
            visited.add(element);
            switch (element) {
                case INCLUDE_MECHANISMS: {
                    final ModelNode includes = saslElement.get(INCLUDE_MECHANISMS);
                    for (final String s : readArrayAttributeElement(reader, "value", String.class)) {
                        includes.add().set(s);
                    }
                    break;
                }
                case POLICY: {
                    parsePolicyElement(reader, saslElement.get(OP_ADDR), list);
                    break;
                }
                case PROPERTIES: {
                    parseProperties(reader, saslElement.get(OP_ADDR), list);
                    break;
                }
                case QOP: {
                    String qop = readStringAttributeElement(reader, "value");
                    SaslResource.QOP_ATTRIBUTE.getParser().parseAndSetParameter(SaslResource.QOP_ATTRIBUTE, qop, saslElement, reader);
                    break;
                }
                case REUSE_SESSION: {
                    String value = readStringAttributeElement(reader, "value");
                    SaslResource.REUSE_SESSION_ATTRIBUTE.parseAndSetParameter(value, saslElement, reader);
                    break;
                }
                case SERVER_AUTH: {
                    String value = readStringAttributeElement(reader, "value");
                    SaslResource.SERVER_AUTH_ATTRIBUTE.parseAndSetParameter(value, saslElement, reader);
                    break;
                }
                case STRENGTH: {
                    String strength = readStringAttributeElement(reader, "value");
                    SaslResource.STRENGTH_ATTRIBUTE.getParser().parseAndSetParameter(SaslResource.STRENGTH_ATTRIBUTE, strength, saslElement, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    ModelNode parsePolicyElement(XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        final ModelNode policy = new ModelNode();
        policy.get(OP).set(ADD);
        policy.get(OP_ADDR).set(address).add(SaslPolicyResource.SASL_POLICY_CONFIG_PATH.getKey(), SaslPolicyResource.SASL_POLICY_CONFIG_PATH.getValue());
        list.add(policy);

        if (reader.getAttributeCount() > 0) {
            throw ParseUtils.unexpectedAttribute(reader, 0);
        }
        // Handle nested elements.
        final EnumSet<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (visited.contains(element)) {
                throw ParseUtils.unexpectedElement(reader);
            }
            visited.add(element);
            switch (element) {
                case FORWARD_SECRECY: {
                    SaslPolicyResource.FORWARD_SECRECY.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                case NO_ACTIVE: {
                    SaslPolicyResource.NO_ACTIVE.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                case NO_ANONYMOUS: {
                    SaslPolicyResource.NO_ANONYMOUS.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                case NO_DICTIONARY: {
                    SaslPolicyResource.NO_DICTIONARY.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                case NO_PLAIN_TEXT: {
                    SaslPolicyResource.NO_PLAIN_TEXT.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                case PASS_CREDENTIALS: {
                    SaslPolicyResource.PASS_CREDENTIALS.parseAndSetParameter(readStringAttributeElement(reader, "value"), policy, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        return policy;
    }

    void parseProperties(XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        while (reader.nextTag() != END_ELEMENT) {
            //reader.require(START_ELEMENT, Namespace.REMOTING_1_1.getUriString(), Element.PROPERTY.getLocalName());
            final Property property = readProperty(reader, true);
            ModelNode propertyOp = new ModelNode();
            propertyOp.get(OP).set(ADD);
            propertyOp.get(OP_ADDR).set(address).add(PROPERTY, property.getName());
            propertyOp.get(VALUE).set(property.getValue());
            list.add(propertyOp);
        }
    }
}
