/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.sanger.phenodigm2.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;
import uk.ac.sanger.phenodigm2.model.Disease;
import uk.ac.sanger.phenodigm2.model.DiseaseAssociation;
import uk.ac.sanger.phenodigm2.model.GeneIdentifier;
import uk.ac.sanger.phenodigm2.model.MouseModel;
import uk.ac.sanger.phenodigm2.model.PhenotypeMatch;
import uk.ac.sanger.phenodigm2.model.PhenotypeTerm;

/**
 *
 * Disease data access manager implementation.
 *
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
@Repository
public class PhenoDigmDaoJdbcImpl implements PhenoDigmDao, InitializingBean {

    private Logger logger = Logger.getLogger(PhenoDigmDaoJdbcImpl.class);
    @Autowired
    private JdbcTemplate jdbcTemplate;
    //caches these are constantly cross-referenced when building objects
    private static OrthologCache orthologCache;
    private static DiseaseCache diseaseCache;
    private static MouseModelCache mouseModelCache;

    @Override
    public void afterPropertiesSet() throws Exception {
        setUpCaches();
    }

    private void setUpCaches() {
        //don't change the order - the orthologs are needed first off
        setUpOrthologCache();
        setUpDiseaseCache();
        setUpMouseModelsCache();
    }

    private void setUpOrthologCache() {

        if (orthologCache == null) {
            System.out.println("Setting up ortholog cache...");

            String sql = "select mgi_gene_id, mgi_gene_symbol, human_gene_symbol, ifnull(omim_gene_id, \"\") as omim_gene_id from human2mouse_orthologs;";

            Map<GeneIdentifier, GeneIdentifier> orthologMap = this.jdbcTemplate.query(sql, new OrthologResultSetExtractor());
            orthologCache = new OrthologCache(orthologMap);
        }
    }

    private void setUpDiseaseCache() {
        if (diseaseCache == null) {
            System.out.println("Setting up disease cache...");

            String sql = "select distinct h2mo.*, d.*  from disease d left join omim_genes og on og.omim_disease_id = d.disease_id left join human2mouse_orthologs h2mo on og.omim_gene_id = h2mo.omim_gene_id where d.type = 'disease';";

            Map<String, Disease> diseaseMap = this.jdbcTemplate.query(sql, new DiseaseResultSetExtractor());
            diseaseCache = new DiseaseCache(diseaseMap);
        }
    }

    private void setUpMouseModelsCache() {
        if (mouseModelCache == null) {
            System.out.println("Setting up mouse model cache...");

            String sql = "select mgm.mgi_gene_id, mm.mouse_model_id, mm.allelic_composition, mm.genetic_background, mm.allcomp_link, mm.source from mouse_models mm join mgi_gene_models mgm on mgm.mouse_model_id = mm.mouse_model_id order by mgm.mgi_gene_id;";
            Map<String, MouseModel> mouseModels = this.jdbcTemplate.query(sql, new MouseModelResultSetExtractor());
            mouseModelCache = new MouseModelCache(mouseModels);
        }
    }

    
    @Override
    public GeneIdentifier getGeneIdentifierForMgiGeneId(String mgiGeneId) {
        return orthologCache.getMouseGeneIdentifier(mgiGeneId);
    }

    @Override
    public Disease getDiseaseByDiseaseId(String omimDiseaseId) {
        return diseaseCache.getDiseaseForOmimDiseaseId(omimDiseaseId);
    }

    @Override
    public Set<Disease> getDiseasesByOmimGeneId(String omimGeneId) {
        return diseaseCache.getDiseasesByOmimGeneId(omimGeneId);
    }

    @Override
    public Set<Disease> getDiseasesByMgiGeneId(String mgiGeneId) {
        GeneIdentifier humanOrtholog = orthologCache.getHumanOrthologOfMouseGene(mgiGeneId);
        logger.info(String.format("%s maps to human ortholog %s", mgiGeneId, humanOrtholog));
        return diseaseCache.getDiseasesByOmimGeneId(humanOrtholog.getCompoundIdentifier());

    }

    @Override
    public Map<Disease, Set<DiseaseAssociation>> getKnownDiseaseAssociationsForMgiGeneId(String mgiGeneId) {

        Map<Disease, Set<DiseaseAssociation>> diseaseAssociationsMap;
        String sql = "select od.omim_disease_id, ifnull(mouse_to_disease_perc_score, 0.0) as mouse2disease_score, ifnull(disease_to_mouse_perc_score, 0.0) as disease2mouse_score, mgm.mgi_gene_id, od.mouse_model_id from omim_disease_2_mgi_mouse_models od join mgi_gene_models mgm on mgm.mouse_model_id = od.mouse_model_id left join disease_mouse_genotype_associations dmga on dmga.disease_id = od.omim_disease_id and dmga.mouse_model_id = od.mouse_model_id where mgm.mgi_gene_id = ? order by od.omim_disease_id;";
        
        PreparedStatementCreator prepStatmentCreator = new SingleValuePreparedStatementCreator(mgiGeneId, sql);
//TODO:
        diseaseAssociationsMap = this.jdbcTemplate.query(prepStatmentCreator, new DiseaseAssociationResultSetExtractor());
        //also add in the diseases with no known disease associations.
        for (Disease disease : getDiseasesByMgiGeneId(mgiGeneId)) {
            if (!diseaseAssociationsMap.keySet().contains(disease)) {
                diseaseAssociationsMap.put(disease, new TreeSet<DiseaseAssociation>());
            }
        }
        
        return diseaseAssociationsMap;
    }

    @Override
    public Map<Disease, Set<DiseaseAssociation>> getPredictedDiseaseAssociationsForMgiGeneId(String mgiGeneId) {

        Map<Disease, Set<DiseaseAssociation>> diseaseAssociationsMap;
        
        String sql = "select disease_id as omim_disease_id, ifnull(mouse_to_disease_perc_score, 0.0) as mouse2disease_score, ifnull(disease_to_mouse_perc_score, 0.0) as disease2mouse_score, mgi_gene_id, mouse_model_id from disease_mouse_genotype_associations d where mgi_gene_id = ?;";
        
        PreparedStatementCreator prepStatmentCreator = new SingleValuePreparedStatementCreator(mgiGeneId, sql);

        diseaseAssociationsMap = this.jdbcTemplate.query(prepStatmentCreator, new DiseaseAssociationResultSetExtractor());

        return diseaseAssociationsMap;

    }

    @Override
    public GeneIdentifier getHumanOrthologIdentifierForMgiGeneId(String mgiGeneId) {
        return orthologCache.getHumanOrthologOfMouseGene(mgiGeneId);
    }
    
    @Override
    public List<PhenotypeTerm> getDiseasePhenotypeTerms(String diseaseId) {
        List<PhenotypeTerm> phenotypeList;
        String sql = "select d.evidence, hp.term_id as term_id, hp.name as name, hp.definition as definition, hp.comment as comment from hp_term_infos hp join disease_hp d on d.hp_id = hp.term_id where d.disease_id = ?;";
        
        PreparedStatementCreator prepStatmentCreator = new SingleValuePreparedStatementCreator(diseaseId, sql);

        phenotypeList = this.jdbcTemplate.query(prepStatmentCreator, new DiseasePhenotypesResultSetExtractor());
        
        return phenotypeList;    
    }
    
    @Override
    public List<PhenotypeTerm> getMouseModelPhenotypeTerms(String mouseModelId) {
        List<PhenotypeTerm> phenotypeList;
        String sql = "select mp.term_id as term_id, mp.name as name, mp.definition as definition, mp.comment as comment from mp_mouse_models mm join mp_term_infos mp on mp.term_id = mm.mp_id where mm.mouse_model_id = ?;";
        
        PreparedStatementCreator prepStatmentCreator = new SingleValuePreparedStatementCreator(mouseModelId, sql);

        phenotypeList = this.jdbcTemplate.query(prepStatmentCreator, new DiseasePhenotypesResultSetExtractor());
        
        return phenotypeList;    
    }

    @Override
    public List<PhenotypeMatch> getPhenotypeMatches(String diseaseId, String mouseModelId) {
        List<PhenotypeMatch> phenotypeMatchList;
        String sql = "select phenomap.disease_id as disease, phenomap.mouse_model_id as mouse_model_id, phenomap.ic as ic, phenomap.simJ as simJ, phenomap.mp_id as mp_term_id, phenomap.mp_term as mp_term, phenomap.hp_id as hp_term_id, phenomap.hp_term as hp_term from disease_mouse_genotype_mappings phenomap where disease_id = ? and mouse_model_id = ?;";
        
        PreparedStatementCreator prepStatmentCreator = new TwoValuePreparedStatementCreator(diseaseId, mouseModelId, sql);
        
        phenotypeMatchList = this.jdbcTemplate.query(prepStatmentCreator, new PhenotypeMatchesResultSetExtractor());
        
        return phenotypeMatchList;
    }
    
    private static class OrthologResultSetExtractor implements ResultSetExtractor<Map<GeneIdentifier, GeneIdentifier>> {

        private Map<GeneIdentifier, GeneIdentifier> resultsMap;

        public OrthologResultSetExtractor() {
            resultsMap = new HashMap<GeneIdentifier, GeneIdentifier>();
        }

        @Override
        public Map<GeneIdentifier, GeneIdentifier> extractData(ResultSet rs) throws SQLException, DataAccessException {
            while (rs.next()) {
                GeneIdentifier mouseGeneIdentifier = new GeneIdentifier(rs.getString("mgi_gene_symbol"), rs.getString("mgi_gene_id"));
                GeneIdentifier humanGeneIdentifier;
                if (rs.getString("omim_gene_id").equals("")) {
                    humanGeneIdentifier = new GeneIdentifier(rs.getString("human_gene_symbol"), "OMIM", "");
                } else {
                    humanGeneIdentifier = new GeneIdentifier(rs.getString("human_gene_symbol"), rs.getString("omim_gene_id"));
                }

                resultsMap.put(mouseGeneIdentifier, humanGeneIdentifier);
            }
//            System.out.println("Made new ortholog map with " + resultsMap.keySet().size() + " orthologs");
            return resultsMap;
        }
    }

    private static class DiseaseResultSetExtractor implements ResultSetExtractor<Map<String, Disease>> {

        private Map<String, Disease> diseaseMap;

        public DiseaseResultSetExtractor() {
            diseaseMap = new TreeMap<String, Disease>();
        }

        @Override
        public Map<String, Disease> extractData(ResultSet rs) throws SQLException, DataAccessException {

            while (rs.next()) {
                String omimDiseaseId = rs.getString("disease_id");
                Disease disease = diseaseMap.get(omimDiseaseId);
                if (disease == null) {
                    String type = rs.getString("type");
                    String fullOmimId = rs.getString("disease_full_id");
                    String term = rs.getString("disease_term");
                    String altTerms = rs.getString("disease_alts");
                    disease = makeDisease(omimDiseaseId, type, fullOmimId, term, altTerms);
                    diseaseMap.put(disease.getOmimId(), disease);
                }
                String mgiGeneId = rs.getString("mgi_gene_id");

                if (mgiGeneId != null) {
                    GeneIdentifier humanGene = orthologCache.getHumanOrthologOfMouseGene(mgiGeneId); //new GeneIdentifier(fields[2], fields[3]);
                    GeneIdentifier mouseGene = orthologCache.getMouseGeneIdentifier(mgiGeneId); //new GeneIdentifier(fields[1], fields[0]);
//                    mgiToOmimOrthologMap.put(mouseGene.getCompoundIdentifier(), humanGene.getCompoundIdentifier());
                    disease.getAssociatedHumanGenes().add(humanGene);
                    disease.getAssociatedMouseGenes().add(mouseGene);
                }

//                addDiseaseToOmimGeneIdToDiseaseMap(disease);
            }

            return diseaseMap;
        }
    }

    private static Disease makeDisease(String omimId, String type, String fullOmimId, String term, String altTerms) {
        Disease disease = new Disease();

        disease.setOmimId(omimId);
        disease.setType(type);
        disease.setFullOmimId(fullOmimId);
        disease.setTerm(term);

        //add alternative disease terms - if there are any
        if (!altTerms.isEmpty()) {
            disease.setAlternativeTerms(makeAlternativeTerms(altTerms));
        }
        //add human gene identifiers
        disease.setAssociatedHumanGenes(new ArrayList<GeneIdentifier>());
        //add mouse gene identifiers
        disease.setAssociatedMouseGenes(new ArrayList<GeneIdentifier>());

        return disease;
    }

    private static List<String> makeAlternativeTerms(String otherTerms) {
        List<String> alternativeTerms = new ArrayList<String>();
        String[] altTerms = otherTerms.split("\\|");
        for (int i = 0; i < altTerms.length; i++) {
            alternativeTerms.add(altTerms[i]);
        }
        return alternativeTerms;
    }

    private class SingleValuePreparedStatementCreator implements PreparedStatementCreator {

        private String mgiId;
        private String sql;
        
        public SingleValuePreparedStatementCreator(String mgiId, String sql) {
            this.mgiId = mgiId;
            this.sql = sql;
        }

        @Override
        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException {
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, mgiId);
            return preparedStatement;
        }
        
    }

    private static class TwoValuePreparedStatementCreator implements PreparedStatementCreator {

        private String sql;
        private String diseaseId;
        private String mouseModelId;
        
        public TwoValuePreparedStatementCreator(String diseaseId, String mouseModelId, String sql) {
            this.sql = sql;
            this.diseaseId = diseaseId;
            this.mouseModelId = mouseModelId;
        }
        
        @Override
        public PreparedStatement createPreparedStatement(Connection conn) throws SQLException {
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, diseaseId);
            preparedStatement.setString(2, mouseModelId);
            return preparedStatement;
        }
    }

    private static class MouseModelResultSetExtractor implements ResultSetExtractor<Map<String, MouseModel>> {

        private Map<String, MouseModel> mouseModelMap;

        public MouseModelResultSetExtractor() {
            mouseModelMap = new HashMap<String, MouseModel>();
        }

        @Override
        public Map<String, MouseModel> extractData(ResultSet rs) throws SQLException, DataAccessException {
            while (rs.next()) {
                MouseModel mouseModel = new MouseModel();
                mouseModel.setMgiGeneId(rs.getString("mgi_gene_id"));
                mouseModel.setMgiModelId(rs.getString("mouse_model_id"));
                mouseModel.setAllelicComposition(rs.getString("allelic_composition"));
                mouseModel.setGeneticBackground(rs.getString("genetic_background"));
                mouseModel.setAllelicCompositionLink(rs.getString("allcomp_link"));
                mouseModel.setPhenotypeTerms(new ArrayList<PhenotypeTerm>());

                mouseModelMap.put(mouseModel.getMgiModelId(), mouseModel);
            }
            return mouseModelMap;
        }
    }

    private static class DiseaseAssociationResultSetExtractor implements ResultSetExtractor<Map<Disease, Set<DiseaseAssociation>>> {

        public DiseaseAssociationResultSetExtractor() {
        }

        @Override
        public Map<Disease, Set<DiseaseAssociation>> extractData(ResultSet rs) throws SQLException, DataAccessException {
            Map<Disease, Set<DiseaseAssociation>> results = new TreeMap<Disease, Set<DiseaseAssociation>>();

            while (rs.next()) {
                DiseaseAssociation diseaseAssociation = new DiseaseAssociation();
                String omimDiseaseId = rs.getString("omim_disease_id");
                Disease disease = diseaseCache.getDiseaseForOmimDiseaseId(omimDiseaseId);
                if (disease == null) {
                    System.out.println("unable to find disease " + omimDiseaseId + " in disease cache. Possibly the OMIM disease id has been moved?");
                } else {
                    diseaseAssociation.setOmimDiseaseId(omimDiseaseId);
                    diseaseAssociation.setMouseModel(mouseModelCache.getModel(rs.getString("mouse_model_id")));
                    diseaseAssociation.setPubMedId("TODO");
                    diseaseAssociation.setDiseaseToModelScore(Double.parseDouble(rs.getString("disease2mouse_score")));
                    diseaseAssociation.setModelToDiseaseScore(Double.parseDouble(rs.getString("mouse2disease_score")));
                    diseaseAssociation.setPhenotypeMatches(new ArrayList<PhenotypeMatch>());

                    if (results.containsKey(disease)) {
                        results.get(disease).add(diseaseAssociation);
                    } else {
                        Set<DiseaseAssociation> diseaseAssociations = new TreeSet<DiseaseAssociation>();
                        diseaseAssociations.add(diseaseAssociation);
                        results.put(disease, diseaseAssociations);
                    }
                }
            }
//            System.out.println("\nMade disease associations:");
//            for (Disease disease : results.keySet()) {
//                System.out.println("Disease associations for " + disease.getOmimId());
//                for (DiseaseAssociation diseaseAssoc : results.get(disease)) {
//                    System.out.println("\t" + diseaseAssoc);
//                }               
//                System.out.println("");
//            }
//                
//            
            return results;
        }
    }
    
        private static class DiseasePhenotypesResultSetExtractor implements ResultSetExtractor<List<PhenotypeTerm>> {

        public DiseasePhenotypesResultSetExtractor() {
        }

        @Override
        public List<PhenotypeTerm> extractData(ResultSet rs) throws SQLException, DataAccessException {
            List<PhenotypeTerm> phenotypes = new ArrayList<PhenotypeTerm>();
        
            while(rs.next()) {
                PhenotypeTerm term = new PhenotypeTerm();
                term.setTermId(rs.getString("term_id"));
                term.setName(rs.getString("name"));
                term.setDefinition(rs.getString("definition"));
                term.setComment(rs.getString("comment"));
                phenotypes.add(term);
            }
            
            return phenotypes;
        }
    }
        
    private static class PhenotypeMatchesResultSetExtractor implements ResultSetExtractor<List<PhenotypeMatch>> {
                
        public PhenotypeMatchesResultSetExtractor() {
        }

        @Override
        public List<PhenotypeMatch> extractData(ResultSet rs) throws SQLException, DataAccessException {
            List<PhenotypeMatch> results = new ArrayList<PhenotypeMatch>();
            
            while (rs.next()) {
                PhenotypeMatch phenoMatch = new PhenotypeMatch();
                phenoMatch.setSimJ(rs.getDouble("simJ"));
                phenoMatch.setIc(rs.getDouble("ic"));
                
                //TODO: There are only around 20K PhenotypeTerms so these might
                //be best pulled from a cache as they are static objects from 
                //the HPO (human phenotype ontology) and MP (mammalian phenotype ontology)
                
                PhenotypeTerm mouseTerm = new PhenotypeTerm();
                mouseTerm.setTermId(rs.getString("mp_term_id"));
                mouseTerm.setName(rs.getString("mp_term"));
                phenoMatch.setMousePhenotype(mouseTerm);
                
                PhenotypeTerm humanTerm = new PhenotypeTerm();
                humanTerm.setTermId(rs.getString("hp_term_id"));
                humanTerm.setName(rs.getString("hp_term"));
                phenoMatch.setHumanPhenotype(humanTerm);
                
                
                results.add(phenoMatch);
            }
            return results;
        }
    }


}