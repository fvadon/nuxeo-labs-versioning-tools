package com.nuxeo.labs.versionning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.client.model.Documents;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.VersionModel;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.versioning.VersioningService;
import org.nuxeo.ecm.platform.audit.service.NXAuditEventsService;
import org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener;
import org.nuxeo.ecm.platform.ec.notification.NotificationConstants;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("com.nuxeo.labs.versionning.nuxeo-labs-versioning-tools-core")
public class TestImportVersion {

    
    protected DocumentModel folder;
    protected DocumentModel document;
    
    @Inject
    protected CoreSession session;
    
    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected AutomationService automationService;
    
    @Before
    public void initRepo() {
        folder = session.createDocumentModel("/", "Folder", "Folder");
        folder.setPropertyValue("dc:title", "Folder");
        folder = session.createDocument(folder);
        session.save();
        folder = session.getDocument(folder.getRef());
        
        document=session.createDocumentModel("/Folder/", "file1", "File");
        document.setPropertyValue("dc:title", "File1");
        document.setPropertyValue("dc:source", "Source1");

        document = session.createDocument(document);
        session.save();

    }
    
    @Test
    public void testAllowVersionWrite() {
        DocumentModel doc = session.createDocumentModel("/", "doc", "File");
        doc.setPropertyValue("icon", "icon1");
        doc = session.createDocument(doc);
        DocumentRef verRef = session.checkIn(doc.getRef(), null, null);

        // regular version cannot be written
        DocumentModel ver = session.getDocument(verRef);
        ver.setPropertyValue("icon", "icon2");
        try {
            session.saveDocument(ver);
            fail("Should not allow version write");
        } catch (PropertyException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Cannot set property on a version"));
        }

        // with proper option, it's allowed
        ver.setPropertyValue("icon", "icon3");
        ver.putContextData(CoreSession.ALLOW_VERSION_WRITE, Boolean.TRUE);
        session.saveDocument(ver);
        // refetch to check
        ver = session.getDocument(verRef);
        assertEquals("icon3", ver.getPropertyValue("icon"));
    }
    
   
    
    @Test
    public void testImportANewVersion() {
        String documentLiveId = document.getId();
        String documentVersionSeriesId = document.getVersionSeriesId();
        assertEquals(documentLiveId,documentVersionSeriesId);
        
        // Create a first major version
        document.checkIn(VersioningOption.MAJOR, "major version");
        
        // Generate a new unique UUUID for the minor version to import;
        String vid = UUID.randomUUID().toString(); 
        
        String type = document.getType();
        Path path = new Path("empty"); // Needed for import but will be overridden automatically by source path.

        
        Calendar vcr = new GregorianCalendar(2009, Calendar.JANUARY, 1, 2, 3, 4);
        //Create minimal document model, copy the info from source, and put specific data
        DocumentModel copy = new DocumentModelImpl((String) null, type, vid, path, null, null, null,
                null, null, null, null);
        copy.copyContent(document);
        copy.putContextData(CoreSession.IMPORT_VERSION_VERSIONABLE_ID, documentLiveId);
        copy.putContextData(CoreSession.IMPORT_VERSION_CREATED, vcr);
        copy.putContextData(CoreSession.IMPORT_VERSION_LABEL, "V02");
        copy.putContextData(CoreSession.IMPORT_VERSION_DESCRIPTION, "v descr");
        copy.putContextData(CoreSession.IMPORT_IS_VERSION, Boolean.TRUE);
        copy.putContextData(CoreSession.IMPORT_VERSION_IS_LATEST, Boolean.FALSE);
        copy.putContextData(CoreSession.IMPORT_VERSION_IS_LATEST_MAJOR, Boolean.FALSE);
        copy.putContextData(CoreSession.IMPORT_VERSION_MAJOR, Long.valueOf(0));
        copy.putContextData(CoreSession.IMPORT_VERSION_MINOR, Long.valueOf(2));
        copy.setProperty("dublincore", "title", "version from copy");
        
        session.importDocuments(Collections.singletonList(copy));
        session.save();
        session = coreFeature.reopenCoreSession();
        
        copy = session.getDocument(new IdRef(vid));
        
        assertEquals("version from copy", copy.getProperty("dublincore", "title"));
        //Check that we have the copied the information from the source.
        assertEquals("Source1", copy.getProperty("dublincore", "source"));
        assertEquals(documentLiveId,copy.getVersionSeriesId());
        DocumentModelListImpl documents = new DocumentModelListImpl(session.getVersions(document.getRef()));
        assertEquals(2,documents.size());
        
        
    }
    
}
