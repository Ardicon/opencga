package org.opencb.opencga.storage.core.search;

import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by wasim on 14/11/16.
 */
public class VariantSearchFactory {


    public VariantSearch create(Variant variant) {
        return variantToSolrConverter(variant);
    }

    public List<VariantSearch> create(List<Variant> variants) {
        List<VariantSearch> variantSearches = new ArrayList<VariantSearch>();
        for (Variant variant : variants) {
            VariantSearch variantSearch = variantToSolrConverter(variant);
            if (variantSearch.getId() != null) {
                variantSearches.add(variantSearch);
            }
        }
        return variantSearches;
    }

    private VariantSearch variantToSolrConverter(Variant variant) {

        VariantSearch variantSearch = new VariantSearch();

        variantSearch.setDbSNP(variant.getId());
        variantSearch.setType(variant.getType().toString());
        variantSearch.setChromosome(variant.getChromosome());
        variantSearch.setStart(variant.getStart());
        variantSearch.setEnd(variant.getEnd());

        //TODO get clear with Nacho what to put in studies
//        variantSearch.setStudies(variant.getStudies());

        VariantAnnotation variantAnnotation = variant.getAnnotation();

        if (variantAnnotation != null) {

            variantSearch.setId(variantAnnotation.getChromosome() + "_" + variantAnnotation.getStart() + "_"
                    + variantAnnotation.getReference() + "_" + variantAnnotation.getAlternate());

            List<ConsequenceType> consequenceTypes = variantAnnotation.getConsequenceTypes();

            if (consequenceTypes != null) {
                for (ConsequenceType consequenceType : consequenceTypes) {

                    variantSearch.setGenes(consequenceType.getGeneName());
                    //substitutionScores
                    List<Double> proteinScores = getsubstitutionScores(consequenceType);
                    variantSearch.setSift(proteinScores.get(0));
                    variantSearch.setPolyphen(proteinScores.get(1));
                    // Accession
                    for (SequenceOntologyTerm sequenceOntologyTerm : consequenceType.getSequenceOntologyTerms()) {
                        variantSearch.setAccessions(sequenceOntologyTerm.getAccession());
                    }
                }
            }
            if (variantAnnotation.getPopulationFrequencies() != null) {
                for (PopulationFrequency populationFrequency : variantAnnotation.getPopulationFrequencies()) {
                    Map<String, Float> population = new HashMap<String, Float>();
                    population.put("study_" + populationFrequency.getStudy() + "_" + populationFrequency.getPopulation(),
                            populationFrequency.getAltAlleleFreq());
                    variantSearch.setPopulations(population);

                }
            }

            // conservations
            if (variantAnnotation.getConservation() != null) {
                for (Score score : variantAnnotation.getConservation()) {
                    if ("gerp".equals(score.getSource())) {
                        variantSearch.setGerp(score.getScore());
                    } else if ("phastCons".equals(score.getSource())) {
                        variantSearch.setPhastCons(score.getScore());
                    } else if ("phylop".equals(score.getSource())) {
                        variantSearch.setPhylop(score.getScore());
                    }
                }
            }

            //cadd
            if (variantAnnotation.getFunctionalScore() != null) {
                for (Score score : variantAnnotation.getFunctionalScore()) {
                    if ("cadd_raw".equals(score.getSource())) {
                        variantSearch.setCaddRaw(score.getScore());
                    } else if ("cadd_scaled".equals(score.getSource())) {
                        variantSearch.setCaddScaled(score.getScore());
                    }
                }
            }
        }
        return variantSearch;
    }

    private List<Double> getsubstitutionScores(ConsequenceType consequenceType) {

        double min = 10;
        double max = 0;

        if (consequenceType.getProteinVariantAnnotation() != null
                && consequenceType.getProteinVariantAnnotation().getSubstitutionScores() != null) {

            for (Score score : consequenceType.getProteinVariantAnnotation().getSubstitutionScores()) {
                String s = score.getSource();
                if (s.equals("sift")) {
                    if (score.getScore() < min) {
                        min = score.getScore();
                    }
                } else if (s.equals("polyphen")) {
                    if (score.getScore() > max) {
                        max = score.getScore();
                    }

                }
            }
        }

        // Always Two values : First value min and second max
        List<Double> result = new ArrayList<Double>(2);
        result.add(min);
        result.add(max);

        return result;
    }
}

