/**
 * Copyright 2005-2010 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.ext.odata.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.restlet.Context;
import org.restlet.ext.atom.Content;
import org.restlet.ext.atom.Entry;
import org.restlet.ext.atom.Feed;
import org.restlet.ext.atom.Link;
import org.restlet.ext.atom.Person;
import org.restlet.ext.odata.internal.edm.AssociationEnd;
import org.restlet.ext.odata.internal.edm.EntityType;
import org.restlet.ext.odata.internal.edm.Mapping;
import org.restlet.ext.odata.internal.edm.Metadata;
import org.restlet.ext.odata.internal.edm.Property;
import org.restlet.ext.odata.internal.reflect.ReflectUtils;
import org.restlet.ext.xml.DomRepresentation;
import org.restlet.ext.xml.NodeSet;
import org.w3c.dom.Node;

/**
 * Parses a Feed representation and extract from its entries a list of embedded
 * entities.
 * 
 * @param <T>
 *            The type of the target entities.
 * 
 * @author Thierry Boileau
 */
public class FeedParser<T> {

    /** Class of the entity targeted by this feed. */
    private Class<?> entityClass;

    /** The underlying feed. */
    private Feed feed;

    /** The internal logger. */
    private Logger logger;

    /** The metadata of the WCF service. */
    private Metadata metadata;

    /**
     * Constructor.
     * 
     * @param feed
     *            The feed to parse.
     * @param entityClass
     *            The class of the target entities.
     * @param metadata
     *            The metadata of the WCF service.
     */
    public FeedParser(Feed feed, Class<?> entityClass, Metadata metadata) {
        super();
        this.feed = feed;
        this.entityClass = entityClass;
        this.metadata = metadata;
    }

    /**
     * Creates a new feed parser for a specific entity class.
     * 
     * @param <E>
     *            The class of the target entity.
     * @param subpath
     *            The path to this entity relatively to the service URI.
     * @param entityClass
     *            The target class of the entity.
     * @param metadata
     *            The metadata of the WCF service.
     * @return A feed parser instance.
     */
    public <E> FeedParser<E> createFeedParser(Feed feed, Class<E> entityClass,
            Metadata metadata) {
        return new FeedParser<E>(feed, entityClass, metadata);
    }

    /**
     * Returns the current logger.
     * 
     * @return The current logger.
     */
    private Logger getLogger() {
        if (logger == null) {
            logger = Context.getCurrentLogger();
        }
        return logger;
    }

    /**
     * Parses the current feed and returns a list of objects or null if the feed
     * is null.
     * 
     * @return A list of objects or null if the feed is null.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public Iterator<T> parse() {
        Iterator<T> result = null;

        if (this.entityClass == null) {
            this.entityClass = ReflectUtils.getEntryClass(feed);
        }

        if (feed == null || metadata == null || entityClass == null) {
            return result;
        }

        List<T> list = new ArrayList<T>();
        EntityType entityType = metadata.getEntityType(entityClass);

        for (Entry entry : feed.getEntries()) {
            try {
                // instanciate the entity
                Object entity = entityClass.newInstance();

                // Update it with the entry content.
                updateWithContent(entity, entry.getContent());

                // Examines the links
                for (Link link : entry.getLinks()) {
                    updateWithLink(entity, link, entityType);
                }

                // Examines the mappings
                for (Mapping mapping : metadata.getMappings()) {
                    updateWithMapping(entity, mapping, entry, entityType);
                }
                // Add the entity to the list of discovered entities.
                list.add((T) entity);
            } catch (InstantiationException e) {
                getLogger().log(
                        Level.WARNING,
                        "Can't instantiate the constructor without arguments of the entity class: "
                                + entityClass, e);
            } catch (IllegalAccessException e) {
                getLogger().log(
                        Level.WARNING,
                        "Can't instantiate the constructor without arguments of the entity class: "
                                + entityClass, e);
            }
        }
        result = list.iterator();

        return result;
    }

    /**
     * Updates the given object according to the given {@link Content} instance
     * taken from the Atom feed representation.
     * 
     * @param entity
     *            The entity to update.
     * @param content
     *            The instance of {@link Content} that contains the values of
     *            the entity attributes.
     */
    private void updateWithContent(Object entity, Content content) {
        if (content != null && content.getInlineContent() != null) {
            // Recreate the bean
            DomRepresentation dr = new DomRepresentation(content
                    .getInlineContent());

            // [ifndef android] instruction
            NodeSet propertyNodes = dr.getNodes("/properties/*");

            // [ifdef android] uncomment
            // List<Node> propertyNodes = new ArrayList<Node>();
            // try {
            // org.w3c.dom.NodeList nl = dr.getDocument().getChildNodes();
            // if (nl != null && nl.getLength() > 0) {
            // Node properties = nl.item(0);
            // boolean found = false;
            // int index = properties.getNodeName().indexOf(":");
            // if (index != -1) {
            // found = properties.getNodeName()
            // .endsWith(":properties");
            // } else {
            // found = properties.getNodeName().equals("properties");
            // }
            // if (found) {
            // for (int i = 0; i < properties.getChildNodes()
            // .getLength(); i++) {
            // Node n = properties.getChildNodes().item(i);
            // if (n.getNodeType() == Node.ELEMENT_NODE) {
            // propertyNodes.add(n);
            // }
            // }
            // }
            // }
            // } catch (java.io.IOException e1) {
            // }
            // [enddef]

            for (Node node : propertyNodes) {
                String nodeName = node.getNodeName();
                int index = nodeName.indexOf(":");
                if (index != -1) {
                    nodeName = nodeName.substring(index + 1);
                }

                Property property = metadata.getProperty(entity, nodeName);
                try {
                    // [ifndef android] instruction
                    ReflectUtils.setProperty(entity, property, node
                            .getTextContent());
                    // [ifdef android] instruction uncomment
                    // ReflectUtils.setProperty(entity, property,
                    // org.restlet.ext.xml.XmlRepresentation
                    // .getTextContent(node));
                } catch (Exception e) {
                    getLogger().log(
                            Level.WARNING,
                            "Can't set the property " + nodeName + " of "
                                    + entity.getClass(), e);
                }
            }
        }
    }

    /**
     * Updates one association property of the given object according to the
     * given {@link Link} instance taken from the Atom feed representation.
     * 
     * @param entity
     *            The entity to update.
     * @param link
     *            The instance of {@link Link} that contains the values of the
     *            entity attribute.
     * @param entityType
     *            The descriptor of the given entity's type.
     */
    private void updateWithLink(Object entity, Link link, EntityType entityType) {
        // Try to get inline content denoting the full content of a property of
        // the current entity
        if (link.getContent() != null && link.getTitle() != null) {
            String propertyName = ReflectUtils.normalize(link.getTitle());
            // Get the associated entity
            AssociationEnd association = metadata.getAssociation(entityType,
                    propertyName);
            if (association != null) {
                try {
                    Feed linkFeed = null;
                    if (association.isToMany()) {
                        linkFeed = new Feed(link.getContent()
                                .getInlineContent());
                    } else {
                        linkFeed = new Feed();
                        linkFeed.getEntries()
                                .add(
                                        new Entry(link.getContent()
                                                .getInlineContent()));
                    }

                    Class<?> linkClass = ReflectUtils.getEntryClass(linkFeed);
                    Iterator<?> iterator = createFeedParser(linkFeed,
                            linkClass, metadata).parse();
                    ReflectUtils.setProperty(entity, propertyName, association
                            .isToMany(), iterator, linkClass);
                } catch (Exception e) {
                    getLogger().log(
                            Level.WARNING,
                            "Can't retrieve associated property "
                                    + propertyName, e);
                }
            }
        }
    }

    /**
     * Updates the given object according to the given {@link Content} instance
     * taken from the Atom feed representation.
     * 
     * @param entity
     *            The entity to update.
     * @param mapping
     *            The instance of {@link Mapping} that describes the entity
     *            attribute to update and the location of its value.
     * @param entry
     *            The current Atom entry, where could be located the value.
     * @param entityType
     *            The descriptor of the given entity's type.
     */
    private void updateWithMapping(Object entity, Mapping mapping, Entry entry,
            EntityType entityType) {
        DomRepresentation inlineContent = null;
        if (entityType != null && entityType.equals(mapping.getType())) {
            Object value = null;
            if (mapping.getNsPrefix() == null && mapping.getNsUri() == null) {
                // mapping atom
                Person author = (entry.getAuthors().isEmpty()) ? null : entry
                        .getAuthors().get(0);
                Person contributor = (entry.getContributors().isEmpty()) ? null
                        : entry.getContributors().get(0);
                if ("SyndicationAuthorEmail".equals(mapping.getValuePath())) {
                    value = (author != null) ? author.getEmail() : null;
                } else if ("SyndicationAuthorName".equals(mapping
                        .getValuePath())) {
                    value = (author != null) ? author.getName() : null;
                } else if ("SyndicationAuthorUri"
                        .equals(mapping.getValuePath())) {
                    value = (author != null) ? author.getUri().toString()
                            : null;
                } else if ("SyndicationContributorEmail".equals(mapping
                        .getValuePath())) {
                    value = (contributor != null) ? contributor.getEmail()
                            : null;
                } else if ("SyndicationContributorName".equals(mapping
                        .getValuePath())) {
                    value = (contributor != null) ? contributor.getName()
                            : null;
                } else if ("SyndicationContributorUri".equals(mapping
                        .getValuePath())) {
                    value = (contributor != null) ? contributor.getUri()
                            .toString() : null;
                } else if ("SyndicationPublished"
                        .equals(mapping.getValuePath())) {
                    value = entry.getPublished();
                } else if ("SyndicationRights".equals(mapping.getValuePath())) {
                    value = (entry.getRights() != null) ? entry.getRights()
                            .getContent() : null;
                } else if ("SyndicationSummary".equals(mapping.getValuePath())) {
                    value = entry.getSummary();
                } else if ("SyndicationTitle".equals(mapping.getValuePath())) {
                    value = (entry.getTitle() != null) ? entry.getTitle()
                            .getContent() : null;
                } else if ("SyndicationUpdated".equals(mapping.getValuePath())) {
                    value = entry.getUpdated();
                }
            } else if (entry.getInlineContent() != null) {
                if (inlineContent == null) {
                    inlineContent = new DomRepresentation(entry
                            .getInlineContent());
                }
                Node node = inlineContent.getNode(mapping.getValuePath());
                if (node != null) {
                    value = node.getTextContent();
                }
            }

            try {
                if (value != null) {
                    ReflectUtils.invokeSetter(entity,
                            mapping.getPropertyPath(), value);
                }
            } catch (Exception e) {
            }
        }
    }

}