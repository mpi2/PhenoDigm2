/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.sanger.phenodigm2.model;

import java.util.List;

/**
 * Defines a mouse model for a genetic disease.
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
public class MouseModel {
    
    private String mgiGeneId;
    
    private String mgiModelId;
    //The commented out variables are all contained in the Allele, 
    //but I've go them here for a lighter-weight PhenoDigm-only representation. 
    
    private String allelicComposition;    
    private String geneticBackground;
    //link out to MGI - this is fiddly, so it's stored here for ease of reference
    private String allelicCompositionLink;
    
    private List<PhenotypeTerm> phenotypeTerms;

    public MouseModel() {
    }

    public String getMgiGeneId() {
        return mgiGeneId;
    }

    public void setMgiGeneId(String mgiGeneId) {
        this.mgiGeneId = mgiGeneId;
    }

    public String getMgiModelId() {
        return mgiModelId;
    }

    public void setMgiModelId(String mgiModelId) {
        this.mgiModelId = mgiModelId;
    }

    public String getAllelicComposition() {
        return allelicComposition;
    }

    public void setAllelicComposition(String allelicComposition) {
        this.allelicComposition = allelicComposition;
    }

    public String getGeneticBackground() {
        return geneticBackground;
    }

    public void setGeneticBackground(String geneticBackground) {
        this.geneticBackground = geneticBackground;
    }

    public String getAllelicCompositionLink() {
        return allelicCompositionLink;
    }

    public void setAllelicCompositionLink(String allelicCompositionLink) {
        this.allelicCompositionLink = allelicCompositionLink;
    }
    
    
    public List<PhenotypeTerm> getPhenotypeTerms() {
        return phenotypeTerms;
    }

    public void setPhenotypeTerms(List<PhenotypeTerm> phenotypeTerms) {
        this.phenotypeTerms = phenotypeTerms;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + (this.mgiGeneId != null ? this.mgiGeneId.hashCode() : 0);
        hash = 29 * hash + (this.mgiModelId != null ? this.mgiModelId.hashCode() : 0);
        hash = 29 * hash + (this.allelicComposition != null ? this.allelicComposition.hashCode() : 0);
        hash = 29 * hash + (this.geneticBackground != null ? this.geneticBackground.hashCode() : 0);
        hash = 29 * hash + (this.allelicCompositionLink != null ? this.allelicCompositionLink.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MouseModel other = (MouseModel) obj;
        if ((this.mgiGeneId == null) ? (other.mgiGeneId != null) : !this.mgiGeneId.equals(other.mgiGeneId)) {
            return false;
        }
        if ((this.mgiModelId == null) ? (other.mgiModelId != null) : !this.mgiModelId.equals(other.mgiModelId)) {
            return false;
        }
        if ((this.allelicComposition == null) ? (other.allelicComposition != null) : !this.allelicComposition.equals(other.allelicComposition)) {
            return false;
        }
        if ((this.geneticBackground == null) ? (other.geneticBackground != null) : !this.geneticBackground.equals(other.geneticBackground)) {
            return false;
        }
        if ((this.allelicCompositionLink == null) ? (other.allelicCompositionLink != null) : !this.allelicCompositionLink.equals(other.allelicCompositionLink)) {
            return false;
        }
        return true;
    }

    
    
    @Override
    public String toString() {
        return "MouseModel{" + "mgiGeneId=" + mgiGeneId + ", mgiModelId=" + mgiModelId + ", allelicComposition=" + allelicComposition + ", geneticBackground=" + geneticBackground + ", phenotypeTerms=" + phenotypeTerms + '}';
    }

}
