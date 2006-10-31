/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.processor;

import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext;
import org.orbeon.oxf.xforms.processor.handlers.XHTMLBodyHandler;
import org.orbeon.oxf.xforms.processor.handlers.XHTMLHeadHandler;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This processor handles XForms initialization and produces an XHTML document which is a
 * translation from the source XForms + XHTML.
 */
public class XFormsToXHTML extends ProcessorImpl {

    private static final boolean IS_MIGRATE_TO_SESSION = false;// TODO: for tests

    public static Logger logger = XFormsServer.logger;

    private static final String INPUT_ANNOTATED_DOCUMENT = "annotated-document";
    private static final String OUTPUT_DOCUMENT = "document";

    private static final String OUTPUT_CACHE_KEY = "dynamicState";

    private static final String NAMESPACE_CACHE_KEY = "containerNamespace";
    private static final Long CONSTANT_VALIDITY = new Long(0);

//    private static final KeyValidity CONSTANT_KEY_VALIDITY
//            = new KeyValidity(new InternalCacheKey("sessionId", "NO_SESSION_DEPENDENCY"), new Long(0));

    public XFormsToXHTML() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ANNOTATED_DOCUMENT));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DOCUMENT));
    }

    /**
     * Case where an XML response must be generated.
     */
    public ProcessorOutput createOutput(final String outputName) {
        final ProcessorOutput output = new URIProcessorOutputImpl(XFormsToXHTML.this, outputName, INPUT_ANNOTATED_DOCUMENT) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                doIt(pipelineContext, contentHandler, this);
            }

            protected OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
                final OutputCacheKey outputCacheKey = super.getKeyImpl(pipelineContext);

                if (IS_MIGRATE_TO_SESSION && outputCacheKey != null) {
//                    final InputDependencies inputDependencies = (InputDependencies) getCachedInputAsObject(pipelineContext, getInputByName(INPUT_ANNOTATED_DOCUMENT));
//                    if (inputDependencies != null && inputDependencies.isDependsOnSession()) {
//                        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
//                        final ExternalContext.Session session = externalContext.getSession(true);
//
//                        // Find cached info
//                        final XFormsEngineStaticState staticState = inputDependencies.getXFormsEngineStaticState();
//                        final String staticStateUUID = staticState.getUUID();
//                        final String encodedStaticState = staticState.getEncodedStaticState();
//
//                        final String dynamicStateUUID = (String) getOutputObject(pipelineContext, this, OUTPUT_CACHE_KEY,
//                                new KeyValidity(outputCacheKey, getValidityImpl(pipelineContext)));
//
//                        // Migrate data to current session
//                    }
                }

                return outputCacheKey;
            }

            protected boolean supportsLocalKeyValidity() {
                return true;
            }

            public KeyValidity getLocalKeyValidity(PipelineContext pipelineContext, URIReferences uriReferences) {

                // Use the container namespace as a dependency
                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                final String containerNamespace = externalContext.getRequest().getContainerNamespace();

//                System.out.println("  XFormsToXHTML namespace: " + containerNamespace);

                return new KeyValidity(new InternalCacheKey(XFormsToXHTML.this, NAMESPACE_CACHE_KEY, containerNamespace), CONSTANT_VALIDITY);
            }
        };
        addOutput(outputName, output);
        return output;
    }

    public void reset(PipelineContext context) {
        setState(context, new URIProcessorOutputImpl.URIReferencesState());
    }

    private void doIt(final PipelineContext pipelineContext, ContentHandler contentHandler, final URIProcessorOutputImpl processorOutput) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        // ContainingDocument and XFormsState created below
        final XFormsContainingDocument[] containingDocument = new XFormsContainingDocument[1];
        final XFormsServer.XFormsState[] xformsState = new XFormsServer.XFormsState[1];
        final boolean[] cachedInput = new boolean[1];

        // Read and try to cache the complete XForms+XHTML document with annotations
        final InputDependencies inputDependencies = (InputDependencies) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_ANNOTATED_DOCUMENT), new CacheableInputReader() {
            public Object read(PipelineContext pipelineContext, ProcessorInput processorInput) {

                // Compute annotated XForms document + static state document
                final SAXStore annotatedSAXStore;
                final XFormsEngineStaticState xformsEngineStaticState;
                {
                    final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
                    final LocationDocumentResult documentResult = new LocationDocumentResult();
                    identity.setResult(documentResult);

                    final XMLUtils.DigestContentHandler digestContentHandler = new XMLUtils.DigestContentHandler("MD5");

                    annotatedSAXStore = new SAXStore(new TeeContentHandler(new ContentHandler[] {
                            new XFormsExtractorContentHandler(pipelineContext, identity),
                            digestContentHandler
                    }));

                    // Read the input
                    readInputAsSAX(pipelineContext, processorInput, annotatedSAXStore);

                    // Get the results
                    final Document staticStateDocument = documentResult.getDocument();
                    final String digest = Base64.encode(digestContentHandler.getResult());
                    if (XFormsServer.logger.isDebugEnabled())
                        XFormsServer.logger.debug("XForms - created digest for static state: " + digest);

//                    xformsEngineStaticState = new XFormsEngineStaticState(pipelineContext, staticStateDocument, digest);
                    xformsEngineStaticState = new XFormsEngineStaticState(pipelineContext, staticStateDocument);
                }

                // Create document here so we can do appropriate analysis of caching dependencies
                createCacheContainingDocument(pipelineContext, processorOutput, xformsEngineStaticState, containingDocument, xformsState);

                // Set caching dependencies
                final InputDependencies inputDependencies = new InputDependencies(annotatedSAXStore, xformsEngineStaticState);
                setCachingDependencies(containingDocument[0], inputDependencies);

                return inputDependencies;
            }

            public void foundInCache() {
                cachedInput[0] = true;
            }

            public void storedInCache() {
                cachedInput[0] = true;
            }
        });

        try {
            // Create containing document if not done yet
            final String staticStateUUID;
            if (containingDocument[0] == null) {
                logger.debug("XForms - annotated document and static state obtained from cache; creating containing document.");
                createCacheContainingDocument(pipelineContext, processorOutput, inputDependencies.getXFormsEngineStaticState(), containingDocument, xformsState);
            } else {
                logger.debug("XForms - annotated document and static state not obtained from cache.");
            }

            if (cachedInput[0]) {
                staticStateUUID = inputDependencies.getXFormsEngineStaticState().getUUID();
            } else {
                staticStateUUID = null;
            }

            // Try to cache dynamic state UUID associated with the output
            final String dynamicStateUUID = (String) getCacheOutputObject(pipelineContext, processorOutput, OUTPUT_CACHE_KEY, new OutputObjectCreator() {
                public Object create(PipelineContext pipelineContext, ProcessorOutput processorOutput) {
                    logger.debug("XForms - caching UUID for resulting document.");
                    return UUIDUtils.createPseudoUUID();
                }

                public void foundInCache() {
                    logger.debug("XForms - found cached UUID for resulting document.");
                }

                public void unableToCache() {
                    logger.debug("XForms - cannot cache UUID for resulting document.");
                }
            });

            // Output resulting document
            outputResponse(pipelineContext, externalContext, inputDependencies.getAnnotatedSAXStore(), containingDocument[0], contentHandler, xformsState[0], staticStateUUID, dynamicStateUUID);
        } catch (Throwable e) {
            if (containingDocument[0] != null) {
                // If an exception is caught, we need to discard the object as its state may be inconsistent
                final ObjectPool sourceObjectPool = containingDocument[0].getSourceObjectPool();
                if (sourceObjectPool != null) {
                    logger.debug("XForms - containing document cache: throwable caught, discarding document from pool.");
                    try {
                        sourceObjectPool.invalidateObject(containingDocument);
                        containingDocument[0].setSourceObjectPool(null);
                    } catch (Exception e1) {
                        throw new OXFException(e1);
                    }
                }
            }
            throw new OXFException(e);
        }
    }

    // What can be cached: URI dependencies + the annotated XForms document
    private static class InputDependencies extends URIProcessorOutputImpl.URIReferences {

        private SAXStore annotatedSAXStore;
        private XFormsEngineStaticState xformsEngineStaticState;
        private boolean dependsOnSession;

        public InputDependencies(SAXStore annotatedSAXStore, XFormsEngineStaticState xformsEngineStaticState) {
            this.annotatedSAXStore = annotatedSAXStore;
            this.xformsEngineStaticState = xformsEngineStaticState;
        }

        public SAXStore getAnnotatedSAXStore() {
            return annotatedSAXStore;
        }

        public XFormsEngineStaticState getXFormsEngineStaticState() {
            return xformsEngineStaticState;
        }

        public boolean isDependsOnSession() {
            return dependsOnSession;
        }

        public void setDependsOnSession(boolean dependsOnSession) {
            this.dependsOnSession = dependsOnSession;
        }
    }

    private void setCachingDependencies(XFormsContainingDocument containingDocument, InputDependencies inputDependencies) {

        // If a submission took place during XForms initialization, we currently don't cache
        // TODO: Some cases could be easily handled, like GET
        if (containingDocument.isGotSubmission()) {
            if (logger.isDebugEnabled())
                logger.debug("XForms - submission occurred during XForms initialization, disabling caching of output.");
            inputDependencies.setNoCache();
            return;
        }

        // Set caching dependencies if the input was actually read
        for (Iterator i = containingDocument.getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();

            // Add schema dependencies
            final String schemaURI = currentModel.getSchemaURI();
            if (schemaURI != null) {
                if (logger.isDebugEnabled())
                    logger.debug("XForms - adding document cache dependency for schema: " + schemaURI);
                inputDependencies.addReference(null, schemaURI, null, null);// TODO: support username / password on schema refs
            }

            // Add instance source dependencies
            for (Iterator j = currentModel.getInstances().iterator(); j.hasNext();) {
                final XFormsInstance currentInstance = (XFormsInstance) j.next();
                final String instanceSourceURI = currentInstance.getInstanceSourceURI();

                // Add dependency
                if (instanceSourceURI != null) {
                    if (logger.isDebugEnabled())
                        logger.debug("XForms - adding document cache dependency for instance: " + instanceSourceURI);
                    inputDependencies.addReference(null, instanceSourceURI, currentInstance.getUsername(), currentInstance.getPassword());
                }
            }

            // TODO: Add @src attributes from controls
        }

        // Handle dependency on session id
        if (containingDocument.isSessionStateHandling()) {
            inputDependencies.setDependsOnSession(true);
        }
    }

    private void createCacheContainingDocument(final PipelineContext pipelineContext, URIProcessorOutputImpl processorOutput, XFormsEngineStaticState xformsEngineStaticState,
                                               XFormsContainingDocument[] containingDocument, XFormsServer.XFormsState[] xformsState) {

        boolean[] requireClientSubmission = new boolean[1];
        {
            // Create initial state, before XForms initialization
            final XFormsServer.XFormsState initialXFormsState = new XFormsServer.XFormsState(xformsEngineStaticState.getEncodedStaticState(), "");

            // Create URIResolver
            final XFormsURIResolver uriResolver = new XFormsURIResolver(XFormsToXHTML.this, processorOutput, pipelineContext, INPUT_ANNOTATED_DOCUMENT, URLGenerator.DEFAULT_HANDLE_XINCLUDE);

            // Create containing document and initialize XForms engine
            containingDocument[0] = XFormsServer.createXFormsContainingDocument(pipelineContext, initialXFormsState, null, xformsEngineStaticState, uriResolver);

            // The URIResolver above doesn't make any sense anymore past initialization
            containingDocument[0].setURIResolver(null);

            // This is the state after XForms initialization
            final Document dynamicStateDocument = XFormsServer.createDynamicStateDocument(containingDocument[0], requireClientSubmission);
            xformsState[0] = new XFormsServer.XFormsState(initialXFormsState.getStaticState(),
                    XFormsUtils.encodeXML(pipelineContext, dynamicStateDocument, containingDocument[0].isSessionStateHandling() ? null : XFormsUtils.getEncryptionKey()));
        }

        // Cache ContainingDocument if requested and possible
        {
            if (XFormsUtils.isCacheDocument()) {
                if (!requireClientSubmission[0]) {
                    // NOTE: We check on requireClientSubmission because the event is encoded
                    // in the dynamic state. But if we stored the event separately, then we
                    // could still cache the containing document.
                    XFormsServerDocumentCache.instance().add(pipelineContext, xformsState[0], containingDocument[0]);
                } else {
                    // Since we cannot cache the result, we have to get the object out of its current pool
                    final ObjectPool objectPool = containingDocument[0].getSourceObjectPool();
                    if (objectPool != null) {
                        logger.debug("XForms - containing document cache: discarding non-cacheable document from pool.");
                        try {
                            objectPool.invalidateObject(containingDocument);
                            containingDocument[0].setSourceObjectPool(null);
                        } catch (Exception e1) {
                            throw new OXFException(e1);
                        }
                    }
                }
            }
        }
    }

    private void outputResponse(final PipelineContext pipelineContext, final ExternalContext externalContext,
                                final SAXStore annotatedDocument, final XFormsContainingDocument containingDocument,
                                final ContentHandler contentHandler, final XFormsServer.XFormsState xformsState,
                                final String staticStateUUID, String dynamicStateUUID) throws SAXException {

        final ElementHandlerController controller = new ElementHandlerController();

        // Make sure we have up to date controls
        final XFormsControls xformsControls = containingDocument.getXFormsControls();
        xformsControls.rebuildCurrentControlsStateIfNeeded(pipelineContext);

        // Register handlers on controller (the other handlers are registered by the body handler)
        controller.registerHandler(XHTMLHeadHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "head");
        controller.registerHandler(XHTMLBodyHandler.class.getName(), XMLConstants.XHTML_NAMESPACE_URI, "body");

        // Set final output with output to filter remaining xforms:* elements if any
        // TODO: Remove this filter once the "exception elements" below are filtered at the source.
        controller.setOutput(new DeferredContentHandlerImpl(new XFormsElementFilterContentHandler(contentHandler)));

        controller.setElementHandlerContext(new HandlerContext(controller, pipelineContext, containingDocument, xformsState, staticStateUUID, dynamicStateUUID, externalContext));

        // Process everything
        annotatedDocument.replay(new ElementFilterContentHandler(controller) {
            protected boolean isFilterElement(String uri, String localname, String qName, Attributes attributes) {
                // We filter everything that is not a control
                // TODO: There are some temporary exceptions, but those should actually be handled by the ControlInfo in the first place
                return (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri) && !(localname.equals("img") || localname.equals("dialog")))
                        || (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)
                            && !(XFormsControls.isActualControl(localname) || exceptionXFormsElements.get(localname) != null));
            }
        });
    }
    private static final Map exceptionXFormsElements = new HashMap();

    static {
        exceptionXFormsElements.put("item", "");
        exceptionXFormsElements.put("itemset", "");
        exceptionXFormsElements.put("choices", "");
        exceptionXFormsElements.put("value", "");
        exceptionXFormsElements.put("label", "");
        exceptionXFormsElements.put("hint", "");
        exceptionXFormsElements.put("help", "");
        exceptionXFormsElements.put("alert", "");
    }
}
