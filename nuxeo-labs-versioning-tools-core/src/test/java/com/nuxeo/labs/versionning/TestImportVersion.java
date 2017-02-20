package com.nuxeo.labs.versionning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.nuxeo.ecm.core.api.VersionModel;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
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
        document = session.createDocument(document);
        session.save();

    }
    
    @Test
    public void testVersionningOptions() {
        assertNotNull(folder);
        assertNotNull(document);
        String documentLiveId = document.getId();
        String documentVersionSeriesId = document.getVersionSeriesId();
        assertEquals(documentLiveId,documentVersionSeriesId);
        
        document.checkIn(VersioningOption.MAJOR, "major version");
        document.checkOut();
        session.save();
        
        String vid = "12345678-1234-1234-1234-fedcba987654";
        
        DocumentRef parentRef = null;
        String type = document.getType();
        String name = "foobar";
        Path path = new Path(name);

        
        DocumentModel ver = new DocumentModelImpl((String) null, type, vid, new Path(name), null, null, parentRef,
                null, null, null, null);
        
        Calendar vcr = new GregorianCalendar(2009, Calendar.JANUARY, 1, 2, 3, 4);
        ver.putContextData(CoreSession.IMPORT_VERSION_VERSIONABLE_ID, documentLiveId);
        ver.putContextData(CoreSession.IMPORT_VERSION_CREATED, vcr);
        ver.putContextData(CoreSession.IMPORT_VERSION_LABEL, "V1");
        ver.putContextData(CoreSession.IMPORT_VERSION_DESCRIPTION, "v descr");
        ver.putContextData(CoreSession.IMPORT_IS_VERSION, Boolean.TRUE);
        ver.putContextData(CoreSession.IMPORT_VERSION_IS_LATEST, Boolean.FALSE);
        ver.putContextData(CoreSession.IMPORT_VERSION_IS_LATEST_MAJOR, Boolean.FALSE);
        ver.putContextData(CoreSession.IMPORT_VERSION_MAJOR, Long.valueOf(0));
        ver.putContextData(CoreSession.IMPORT_VERSION_MINOR, Long.valueOf(14));
        ver.putContextData(CoreSession.IMPORT_LIFECYCLE_POLICY, "v lcp");
        ver.putContextData(CoreSession.IMPORT_LIFECYCLE_STATE, "v lcst");
        ver.setProperty("dublincore", "title", "Ver title");
        Calendar mod = new GregorianCalendar(2008, Calendar.JULY, 14, 12, 34, 56);
        ver.setProperty("dublincore", "modified", mod);
        session.importDocuments(Collections.singletonList(ver));
        session.save();
        ver = session.getDocument(new IdRef(vid));
        // assertEquals(name, doc.getName()); // no path -> no name...
        assertEquals("Ver title", ver.getProperty("dublincore", "title"));
        assertEquals(mod, ver.getProperty("dublincore", "modified"));
        assertEquals(documentLiveId,ver.getVersionSeriesId());
        DocumentModelListImpl documents = new DocumentModelListImpl(session.getVersions(document.getRef()));
        assertEquals(2,documents.size());
    }

    @Test
    public void shouldCallTheOperation() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        DocumentModel doc = (DocumentModel) automationService.run(ctx, ImportVersion.ID);
        assertEquals("/", doc.getPathAsString());
    }

    @Test
    public void shouldCallWithParameters() throws OperationException {
        final String path = "/default-domain";
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        params.put("path", path);
        DocumentModel doc = (DocumentModel) automationService.run(ctx, ImportVersion.ID, params);
        assertEquals(path, doc.getPathAsString());
        
    }
}
