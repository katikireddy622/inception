/*
 * Copyright 2018
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.kb;

import static de.tudarmstadt.ukp.inception.kb.querybuilder.SPARQLQueryBuilderTest.isReachable;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;

import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestWatcher;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.transaction.annotation.Transactional;

import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryProperties;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.inception.kb.graph.KBHandle;
import de.tudarmstadt.ukp.inception.kb.graph.KBProperty;
import de.tudarmstadt.ukp.inception.kb.model.KnowledgeBase;
import de.tudarmstadt.ukp.inception.kb.util.TestFixtures;
import de.tudarmstadt.ukp.inception.kb.yaml.KnowledgeBaseProfile;

@RunWith(Parameterized.class)
@ContextConfiguration(classes = SpringConfig.class)
@Transactional
@DataJpaTest
public class KnowledgeBaseServiceRemoteTest
{
    private final String PROJECT_NAME = "Test project";

    private static Map<String, KnowledgeBaseProfile> PROFILES;
    
    private final TestConfiguration sutConfig;

    private KnowledgeBaseServiceImpl sut;

    private Project project;
    private TestFixtures testFixtures;

    private static final Logger LOGGER = Logger.getLogger(KnowledgeBaseServiceRemoteTest.class.getName());
    @Rule
    public TestWatcher watcher = new TestWatcher()
    {
        @Override
        protected void starting(org.junit.runner.Description aDescription)
        {
            String methodName = aDescription.getMethodName();
            
            LOGGER.log(Level.INFO,"\n=== " + methodName + " =====================\n");
        };
    };

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Autowired
    private TestEntityManager testEntityManager;

    @ClassRule
    public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();

    @Rule
    public SpringMethodRule springMethodRule = new SpringMethodRule();

    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        System.setProperty("org.eclipse.rdf4j.repository.debug", "true");
    }

    @Before
    public void setUp() throws Exception
    {
        KnowledgeBase kb = sutConfig.getKnowledgeBase();

        RepositoryProperties repoProps = new RepositoryProperties();
        repoProps.setPath(temporaryFolder.getRoot());
        EntityManager entityManager = testEntityManager.getEntityManager();
        testFixtures = new TestFixtures(testEntityManager);
        sut = new KnowledgeBaseServiceImpl(repoProps, entityManager);
        project = testFixtures.createProject(PROJECT_NAME);
        kb.setProject(project);
        if (kb.getType() == RepositoryType.LOCAL) {
            sut.registerKnowledgeBase(kb, sut.getNativeConfig());
            sut.updateKnowledgeBase(kb, sut.getKnowledgeBaseConfig(kb));
            importKnowledgeBase(sutConfig.getDataUrl());
        }
        else if (kb.getType() == RepositoryType.REMOTE) {
            testFixtures.assumeEndpointIsAvailable(sutConfig.getDataUrl());
            sut.registerKnowledgeBase(kb, sut.getRemoteConfig(sutConfig.getDataUrl()));
            sut.updateKnowledgeBase(kb, sut.getKnowledgeBaseConfig(kb));
        }
        else {
            throw new IllegalStateException(
                    "Unknown type: " + sutConfig.getKnowledgeBase().getType());
        }
    }

    @After
    public void tearDown() throws Exception
    {
        testEntityManager.clear();
        sut.destroy();
    }

    public KnowledgeBaseServiceRemoteTest(TestConfiguration aConfig) throws Exception
    {
        sutConfig = aConfig;
        
        Assume.assumeTrue(
                "Remote repository at [" + aConfig.getDataUrl() + "] is not reachable",
                aConfig.getKnowledgeBase().getType() != RepositoryType.REMOTE || 
                        isReachable(aConfig.getDataUrl()));
    }

    @Parameterized.Parameters(name = "KB = {0}")
    public static List<Object[]> data() throws Exception
    {
        PROFILES = KnowledgeBaseProfile.readKnowledgeBaseProfiles();
        int maxResults = 1000;

        Set<String> rootConcepts;
        Map<String, String> parentChildConcepts;
        List<TestConfiguration> kbList = new ArrayList<>();

        {
            KnowledgeBaseProfile profile = PROFILES.get("wine_ontology");
            KnowledgeBase kb_wine = new KnowledgeBase();
            kb_wine.setName("Wine ontology (OWL)");
            kb_wine.setType(profile.getType());
            kb_wine.setReification(profile.getReification());
            kb_wine.setFullTextSearchIri(profile.getAccess().getFullTextSearchIri());
            kb_wine.applyMapping(profile.getMapping());
            kb_wine.setDefaultLanguage(profile.getDefaultLanguage());
            kb_wine.setMaxResults(maxResults);
            rootConcepts = new HashSet<String>();
            rootConcepts.add("http://www.w3.org/TR/2003/PR-owl-guide-20031209/food#Grape");
            parentChildConcepts = new HashMap<String, String>();
            parentChildConcepts.put("http://www.w3.org/TR/2003/PR-owl-guide-20031209/food#Grape",
                    "http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#WineGrape");
            kbList.add(new TestConfiguration("data/wine-ontology.rdf", kb_wine,
                    "http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#ChateauMargaux",
                    rootConcepts, parentChildConcepts));
        }


//        {
//            ValueFactory vf = SimpleValueFactory.getInstance();
//            KnowledgeBase kb_hucit = new KnowledgeBase();
//            kb_hucit.setName("Hucit");
//            kb_hucit.setType(profile.getType());
//            kb_hucit.setReification(Reification.NONE);
//            kb_hucit.setBasePrefix("http://www.ukp.informatik.tu-darmstadt.de/inception/1.0#");
//            kb_hucit.setClassIri(vf.createIRI("http://www.w3.org/2002/07/owl#Class"));
//            kb_hucit.setSubclassIri(
//                    vf.createIRI("http://www.w3.org/2000/01/rdf-schema#subClassOf"));
//            kb_hucit.setTypeIri(vf.createIRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"));
//            kb_hucit.setDescriptionIri(
//                    vf.createIRI("http://www.w3.org/2000/01/rdf-schema#comment"));
//            kb_hucit.setLabelIri(vf.createIRI("http://www.w3.org/2000/01/rdf-schema#label"));
//            kb_hucit.setPropertyTypeIri(
//                    vf.createIRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"));
//            kb_hucit.setPropertyLabelIri(RDFS.LABEL);
//            kb_hucit.setPropertyDescriptionIri(RDFS.COMMENT);
//            kb_hucit.setDefaultLanguage("en");
//            kb_hucit.setMaxResults(maxResults);
//            rootConcepts = new HashSet<String>();
//            rootConcepts.add("http://www.w3.org/2000/01/rdf-schema#Class");
//            parentChildConcepts = new HashMap<String, String>();
//            parentChildConcepts.put("http://www.w3.org/2000/01/rdf-schema#Class",
//                    "http://www.w3.org/2002/07/owl#Class");
//            kbList.add(new TestConfiguration("http://nlp.dainst.org:8888/sparql", kb_hucit,
//                    // person -> Achilles :: urn:cts:cwkb:1137
//                    "http://purl.org/hucit/kb/authors/1137", rootConcepts, parentChildConcepts));
//        }

        {
            KnowledgeBaseProfile profile = PROFILES.get("wikidata");
            KnowledgeBase kb_wikidata_direct = new KnowledgeBase();
            kb_wikidata_direct.setName("Wikidata (official/direct mapping)");
            kb_wikidata_direct.setType(profile.getType());
            kb_wikidata_direct.setReification(profile.getReification());
            kb_wikidata_direct.setFullTextSearchIri(profile.getAccess().getFullTextSearchIri());
            kb_wikidata_direct.applyMapping(profile.getMapping());
            kb_wikidata_direct.applyRootConcepts(profile);
            kb_wikidata_direct.setDefaultLanguage(profile.getDefaultLanguage());
            kb_wikidata_direct.setMaxResults(maxResults);
            rootConcepts = new HashSet<String>();
            rootConcepts.add("http://www.wikidata.org/entity/Q35120");
            parentChildConcepts = new HashMap<String, String>();
            parentChildConcepts.put("http://www.wikidata.org/entity/Q35120",
                    "http://www.wikidata.org/entity/Q24229398");
            kbList.add(new TestConfiguration(profile.getAccess().getAccessUrl(), kb_wikidata_direct,
                    "http://www.wikidata.org/entity/Q5", rootConcepts, parentChildConcepts));
        }

        // {
        // KnowledgeBaseProfile profile = PROFILES.get("virtuoso");
        // KnowledgeBase kb_wikidata_direct = new KnowledgeBase();
        // kb_wikidata_direct.setName("UKP_Wikidata (Virtuoso)");
        // kb_wikidata_direct.setType(profile.getType());
        // kb_wikidata_direct.setReification(profiles.getReification());
        // kb_wikidata_direct.applyMapping(profile.getMapping());
        // kb_wikidata_direct.setDefaultLanguage(profiles.getDefaultLanguage);
        // rootConcepts = new HashSet<String>();
        // rootConcepts.add("http://www.wikidata.org/entity/Q2419");
        // kbList.add(new TestConfiguration(profile.getAccess().getAccessUrl(), kb_wikidata_direct,
        // "http://www.wikidata.org/entity/Q19576436", rootConcepts));
        // }

        {
            KnowledgeBaseProfile profile = PROFILES.get("db_pedia");
            KnowledgeBase kb_dbpedia = new KnowledgeBase();
            kb_dbpedia.setName(profile.getName());
            kb_dbpedia.setType(profile.getType());
            kb_dbpedia.setReification(profile.getReification());
            kb_dbpedia.setFullTextSearchIri(profile.getAccess().getFullTextSearchIri());
            kb_dbpedia.applyMapping(profile.getMapping());
            kb_dbpedia.applyRootConcepts(profile);
            kb_dbpedia.setDefaultLanguage(profile.getDefaultLanguage());
            kb_dbpedia.setMaxResults(maxResults);
            kb_dbpedia.setDefaultDatasetIri(profile.getDefaultDataset());
            rootConcepts = new HashSet<String>();
            rootConcepts.add("http://www.w3.org/2002/07/owl#Thing");
            parentChildConcepts = new HashMap<String, String>();
            parentChildConcepts.put("http://www.w3.org/2002/07/owl#Thing",
                    "http://dbpedia.org/ontology/Biomolecule");
            kbList.add(new TestConfiguration(profile.getAccess().getAccessUrl(), kb_dbpedia,
                    "http://dbpedia.org/ontology/Organisation", rootConcepts, parentChildConcepts));
        }

        {
            KnowledgeBaseProfile profile = PROFILES.get("yago");
            KnowledgeBase kb_yago = new KnowledgeBase();
            kb_yago.setName(profile.getName());
            kb_yago.setType(profile.getType());
            kb_yago.setReification(profile.getReification());
            kb_yago.setFullTextSearchIri(profile.getAccess().getFullTextSearchIri());
            kb_yago.applyMapping(profile.getMapping());
            kb_yago.applyRootConcepts(profile);
            kb_yago.setDefaultLanguage(profile.getDefaultLanguage());
            kb_yago.setMaxResults(maxResults);
            rootConcepts = new HashSet<String>();
            rootConcepts.add("http://www.w3.org/2002/07/owl#Thing");
            parentChildConcepts = new HashMap<String, String>();
            parentChildConcepts.put("http://www.w3.org/2002/07/owl#Thing",
                    "http://yago-knowledge.org/resource/wikicat_Alleged_UFO-related_entities");
            kbList.add(new TestConfiguration(profile.getAccess().getAccessUrl(), kb_yago,
                    "http://yago-knowledge.org/resource/wikicat_Alkaloids",
                    rootConcepts, parentChildConcepts));
        }

        {
            KnowledgeBaseProfile profile = PROFILES.get("zbw-stw-economics");
            KnowledgeBase kb_zbw_stw_economics = new KnowledgeBase();
            kb_zbw_stw_economics.setName(profile.getName());
            kb_zbw_stw_economics.setType(profile.getType());
            kb_zbw_stw_economics.setReification(profile.getReification());
            kb_zbw_stw_economics.setFullTextSearchIri(profile.getAccess().getFullTextSearchIri());
            kb_zbw_stw_economics.applyMapping(profile.getMapping());
            kb_zbw_stw_economics.applyRootConcepts(profile);
            kb_zbw_stw_economics.setDefaultLanguage(profile.getDefaultLanguage());
            kb_zbw_stw_economics.setMaxResults(maxResults);
            rootConcepts = new HashSet<String>();
            rootConcepts.add("http://zbw.eu/stw/thsys/a");
            parentChildConcepts = new HashMap<String, String>();
            parentChildConcepts.put("http://zbw.eu/stw/thsys/a",
                    "http://zbw.eu/stw/thsys/70582");
            kbList.add(new TestConfiguration(profile.getAccess().getAccessUrl(),
                    kb_zbw_stw_economics, "http://zbw.eu/stw/thsys/71020", rootConcepts, parentChildConcepts));
        }

        // Commenting this out for the moment becuase we expect that every ontology contains
        // property definitions. However, this one does not include any property definitions!
        // {
        // KnowledgeBaseProfile profile = PROFILES.get("zbw-gnd");
        // KnowledgeBase kb_zbw_gnd = new KnowledgeBase();
        // kb_zbw_gnd.setName(profile.getName());
        // kb_zbw_gnd.setType(profile.getType());
        // kb_zbw_gnd.setReification(profile.getReification());
        // kb_zbw_gnd.applyMapping(profile.getMapping());
        // kb_zbw_gnd.setDefaultLanguage(profile.getDefaultLanguage());
        // kbList.add(new TestConfiguration(profile.getSparqlUrl(), kb_zbw_gnd));
        // }

        List<Object[]> dataList = new ArrayList<>();
        for (TestConfiguration kb : kbList) {
            dataList.add(new Object[] { kb });
        }
        return dataList;
    }

    @Test
    public void thatRootConceptsCanBeRetrieved()
    {
        KnowledgeBase kb = sutConfig.getKnowledgeBase();

        long duration = System.currentTimeMillis();
        List<KBHandle> rootConceptKBHandle = sut.listRootConcepts(kb, true);
        duration = System.currentTimeMillis() - duration;

        
        LOGGER.log(Level.INFO,"Root concepts retrieved : %d%n", rootConceptKBHandle.size());
        LOGGER.log(Level.INFO,"Time required           : %d ms%n", duration);
       
        rootConceptKBHandle.stream().limit(10).forEach(h -> LOGGER.log(Level.INFO,"   %s%n", h));

        assertThat(rootConceptKBHandle).as("Check that root concept list is not empty")
                .isNotEmpty();
        for (String expectedRoot : sutConfig.getRootIdentifier()) {
            assertThat(rootConceptKBHandle.stream().map(KBHandle::getIdentifier)).as("Check that root concept is retreived")
            .contains(expectedRoot);
        }
    }
    
    @Test
    public void thatPropertyListCanBeRetrieved()
    {
        KnowledgeBase kb = sutConfig.getKnowledgeBase();

        long duration = System.currentTimeMillis();
        List<KBProperty> propertiesKBHandle = sut.listProperties(kb, true);
        duration = System.currentTimeMillis() - duration;

        LOGGER.log(Level.INFO,"Properties retrieved : %d%n", propertiesKBHandle.size());
        LOGGER.log(Level.INFO,"Time required        : %d ms%n", duration);
        propertiesKBHandle.stream().limit(10).forEach(h ->LOGGER.log(Level.INFO,"   %s%n", h));

        assertThat(propertiesKBHandle).as("Check that property list is not empty").isNotEmpty();
    }

    @Test
    public void thatParentListCanBeRetireved()
    {
        KnowledgeBase kb = sutConfig.getKnowledgeBase();

        long duration = System.currentTimeMillis();
        List<KBHandle> parentList = sut.getParentConceptList(kb, sutConfig.getTestIdentifier(),
                true);
        duration = System.currentTimeMillis() - duration;

        LOGGER.log(Level.INFO,"Parents for          : %s%n", sutConfig.getTestIdentifier());
        LOGGER.log(Level.INFO,"Parents retrieved    : %d%n", parentList.size());
        LOGGER.log(Level.INFO,"Time required        : %d ms%n", duration);
        
        parentList.stream().limit(10).forEach(h -> System.out.printf("   %s%n", h));

        assertThat(parentList).as("Check that parent list is not empty").isNotEmpty();
    }
    
    // Helper

    private void importKnowledgeBase(String resourceName) throws Exception
    {
        ClassLoader classLoader = KnowledgeBaseServiceRemoteTest.class.getClassLoader();
        String fileName = classLoader.getResource(resourceName).getFile();
        try (InputStream is = classLoader.getResourceAsStream(resourceName)) {
            sut.importData(sutConfig.getKnowledgeBase(), fileName, is);
        }
    }

    public static KnowledgeBase setOWLSchemaMapping(KnowledgeBase kb)
    {
        kb.setClassIri(OWL.CLASS);
        kb.setSubclassIri(RDFS.SUBCLASSOF);
        kb.setTypeIri(RDF.TYPE);
        kb.setDescriptionIri(RDFS.COMMENT);
        kb.setLabelIri(RDFS.LABEL);
        kb.setPropertyTypeIri(RDF.PROPERTY);
        return kb;
    }

    private static class TestConfiguration
    {
        private final String url;
        private final KnowledgeBase kb;
        private final String testIdentifier;
        private final Set<String> rootIdentifier;
        private final Map<String,String> parentChildIdentifier;

        public TestConfiguration(String aUrl, KnowledgeBase aKb, String atestIdentifier,
                Set<String> aRootIdentifier, Map<String,String> aParentChildIdentifier)
        {
            super();
            url = aUrl;
            kb = aKb;
            testIdentifier = atestIdentifier;
            rootIdentifier = aRootIdentifier;
            parentChildIdentifier = aParentChildIdentifier;
        }

        public KnowledgeBase getKnowledgeBase()
        {
            return kb;
        }

        public String getDataUrl()
        {
            return url;
        }

        public String getTestIdentifier()
        {
            return testIdentifier;
        }

        public Set<String> getRootIdentifier()
        {
            return rootIdentifier;
        }

        public Map<String,String> getParentChildIdentifier()
        {
            return parentChildIdentifier;
        }

        @Override
        public String toString()
        {
            return kb.getName();
        }
    }
}
