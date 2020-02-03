from pyopencga.rest_clients._parent_rest_clients import _ParentRestClient


class Clinical(_ParentRestClient):
    """
    This class contains methods for the 'Analysis - Clinical Interpretation' webservices
    Client version: 2.0.0
    PATH: /{apiVersion}/analysis/clinical
    """

    def __init__(self, configuration, token=None, login_handler=None, *args, **kwargs):
        _category = 'analysis/clinical'
        super(Clinical, self).__init__(configuration, _category, token, login_handler, *args, **kwargs)

    def stats_interpretation(self, **options):
        """
        Clinical interpretation analysis.
        PATH: /{apiVersion}/analysis/clinical/interpretation/stats

        :param str include: Fields included in the response, whole JSON path must be provided.
        :param str exclude: Fields excluded in the response, whole JSON path must be provided.
        :param int limit: Number of results to be returned.
        :param int skip: Number of results to skip.
        :param bool count: Get the total number of results matching the query. Deactivated by default.
        :param bool skip_count: Do not count total number of results.
        :param bool sort: Sort the results.
        :param bool summary: Fast fetch of main variant parameters.
        :param bool approximate_count: Get an approximate count, instead of an exact total count. Reduces execution time.
        :param int approximate_count_sampling_size: Sampling size to get the approximate count. Larger values increase accuracy but also increase execution time.
        :param str id: List of IDs, these can be rs IDs (dbSNP) or variants in the format chrom:start:ref:alt, e.g. rs116600158,19:7177679:C:T.
        :param str region: List of regions, these can be just a single chromosome name or regions in the format chr:start-end, e.g.: 2,3:100000-200000.
        :param str type: Clinical analysis type, e.g. DUO, TRIO, ...
        :param str reference: Reference allele.
        :param str alternate: Main alternate allele.
        :param str project: Project [user@]project where project can be either the ID or the alias.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str file: Filter variants from the files specified. This will set includeFile parameter when not provided.
        :param str filter: Specify the FILTER for any of the files. If 'file' filter is provided, will match the file and the filter. e.g.: PASS,LowGQX.
        :param str qual: Specify the QUAL for any of the files. If 'file' filter is provided, will match the file and the qual. e.g.: >123.4.
        :param str info: Filter by INFO attributes from file. [{file}:]{key}{op}{value}[,;]* . If no file is specified, will use all files from "file" filter. e.g. AN>200 or file_1.vcf:AN>200;file_2.vcf:AN<10 . Many INFO fields can be combined. e.g. file_1.vcf:AN>200;DB=true;file_2.vcf:AN<10.
        :param str sample: Filter variants where the samples contain the variant (HET or HOM_ALT). Accepts AND (;) and OR (,) operators. This will automatically set 'includeSample' parameter when not provided.
        :param str genotype: Samples with a specific genotype: {samp_1}:{gt_1}(,{gt_n})*(;{samp_n}:{gt_1}(,{gt_n})*)* e.g. HG0097:0/0;HG0098:0/1,1/1. Unphased genotypes (e.g. 0/1, 1/1) will also include phased genotypes (e.g. 0|1, 1|0, 1|1), but not vice versa. When filtering by multi-allelic genotypes, any secondary allele will match, regardless of its position e.g. 1/2 will match with genotypes 1/2, 1/3, 1/4, .... Genotype aliases accepted: HOM_REF, HOM_ALT, HET, HET_REF, HET_ALT and MISS  e.g. HG0097:HOM_REF;HG0098:HET_REF,HOM_ALT. This will automatically set 'includeSample' parameter when not provided.
        :param str format: Filter by any FORMAT field from samples. [{sample}:]{key}{op}{value}[,;]* . If no sample is specified, will use all samples from "sample" or "genotype" filter. e.g. DP>200 or HG0097:DP>200,HG0098:DP<10 . Many FORMAT fields can be combined. e.g. HG0097:DP>200;GT=1/1,0/1,HG0098:DP<10.
        :param str sample_annotation: Selects some samples using metadata information from Catalog. e.g. age>20;phenotype=hpo:123,hpo:456;name=smith.
        :param bool sample_metadata: Return the samples metadata group by study. Sample names will appear in the same order as their corresponding genotypes.
        :param str unknown_genotype: Returned genotype for unknown genotypes. Common values: [0/0, 0|0, ./.].
        :param int sample_limit: Limit the number of samples to be included in the result.
        :param int sample_skip: Skip some samples from the result. Useful for sample pagination.
        :param str cohort: Select variants with calculated stats for the selected cohorts.
        :param str cohort_stats_ref: Reference Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_alt: Alternate Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_maf: Minor Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_mgf: Minor Genotype Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str missing_alleles: Number of missing alleles: [{study:}]{cohort}[<|>|<=|>=]{number}.
        :param str missing_genotypes: Number of missing genotypes: [{study:}]{cohort}[<|>|<=|>=]{number}.
        :param str score: Filter by variant score: [{study:}]{score}[<|>|<=|>=]{number}.
        :param str include_study: List of studies to include in the result. Accepts 'all' and 'none'.
        :param str include_file: List of files to be returned. Accepts 'all' and 'none'.
        :param str include_sample: List of samples to be included in the result. Accepts 'all' and 'none'.
        :param str include_format: List of FORMAT names from Samples Data to include in the output. e.g: DP,AD. Accepts 'all' and 'none'.
        :param str include_genotype: Include genotypes, apart of other formats defined with includeFormat.
        :param bool annotation_exists: Return only annotated variants.
        :param str gene: List of genes, most gene IDs are accepted (HGNC, Ensembl gene, ...). This is an alias to 'xref' parameter.
        :param str ct: List of SO consequence types, e.g. missense_variant,stop_lost or SO:0001583,SO:0001578.
        :param str xref: List of any external reference, these can be genes, proteins or variants. Accepted IDs include HGNC, Ensembl genes, dbSNP, ClinVar, HPO, Cosmic, ...
        :param str biotype: List of biotypes, e.g. protein_coding.
        :param str protein_substitution: Protein substitution scores include SIFT and PolyPhen. You can query using the score {protein_score}[<|>|<=|>=]{number} or the description {protein_score}[~=|=]{description} e.g. polyphen>0.1,sift=tolerant.
        :param str conservation: Filter by conservation score: {conservation_score}[<|>|<=|>=]{number} e.g. phastCons>0.5,phylop<0.1,gerp>0.1.
        :param str population_frequency_alt: Alternate Population Frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str population_frequency_ref: Reference Population Frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str population_frequency_maf: Population minor allele frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str transcript_flag: List of transcript annotation flags. e.g. CCDS, basic, cds_end_NF, mRNA_end_NF, cds_start_NF, mRNA_start_NF, seleno.
        :param str gene_trait_id: List of gene trait association id. e.g. "umls:C0007222" , "OMIM:269600".
        :param str go: List of GO (Gene Ontology) terms. e.g. "GO:0002020".
        :param str expression: List of tissues of interest. e.g. "lung".
        :param str protein_keyword: List of Uniprot protein variant annotation keywords.
        :param str drug: List of drug names.
        :param str functional_score: Functional score: {functional_score}[<|>|<=|>=]{number} e.g. cadd_scaled>5.2 , cadd_raw<=0.3.
        :param str clinical_significance: Clinical significance: benign, likely_benign, likely_pathogenic, pathogenic.
        :param str custom_annotation: Custom annotation: {key}[<|>|<=|>=]{number} or {key}[~=|=]{text}.
        :param str trait: List of traits, based on ClinVar, HPO, COSMIC, i.e.: IDs, histologies, descriptions,...
        :param str field: Facet field for categorical fields.
        :param str field_range: Facet field range for continuous fields.
        :param str clinical_analysis_id: Clinical analysis ID.
        :param str disease: Disease (HPO term).
        :param str family_id: Family ID.
        :param list subject_ids: Comma separated list of subject IDs.
        :param str panel_id: Panel ID.
        :param str panel_version: Panel version.
        :param bool save: Save interpretation in Catalog.
        :param str interpretation_id: ID of the stored interpretation.
        :param str interpretation_name: Name of the stored interpretation.
        """

        return self._get('stats', subcategory='interpretation', **options)

    def update_interpretations(self, clinical_analysis, data=None, **options):
        """
        Add or remove Interpretations to/from a Clinical Analysis.
        PATH: /{apiVersion}/analysis/clinical/{clinicalAnalysis}/interpretations/update

        :param str clinical_analysis: Clinical analysis ID.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str action: Action to be performed if the array of interpretations is being updated.
        :param dict data: JSON containing clinical analysis information.
        """

        return self._post('update', query_id=clinical_analysis, subcategory='interpretations', data=data, **options)

    def update_comments(self, clinical_analysis, interpretation, data=None, **options):
        """
        Update comments of an Interpretation.
        PATH: /{apiVersion}/analysis/clinical/{clinicalAnalysis}/interpretations/{interpretation}/comments/update

        :param str study: [[user@]project:]study id.
        :param str clinical_analysis: Clinical analysis id.
        :param str interpretation: Interpretation id.
        :param str action: Action to be performed.
        :param dict data: JSON containing a list of comments.
        """

        return self._post('comments/update', query_id=clinical_analysis, subcategory='interpretations', second_query_id=interpretation, data=data, **options)

    def update_primary_findings(self, clinical_analysis, interpretation, data=None, **options):
        """
        Update reported variants of an interpretation.
        PATH: /{apiVersion}/analysis/clinical/{clinicalAnalysis}/interpretations/{interpretation}/primaryFindings/update

        :param str study: [[user@]project:]study id.
        :param str clinical_analysis: Clinical analysis id.
        :param str interpretation: Interpretation id.
        :param str action: Action to be performed.
        :param dict data: JSON containing a list of reported variants.
        """

        return self._post('primaryFindings/update', query_id=clinical_analysis, subcategory='interpretations', second_query_id=interpretation, data=data, **options)

    def run_interpretation_team(self, **options):
        """
        TEAM interpretation analysis.
        PATH: /{apiVersion}/analysis/clinical/interpretation/team/run

        :param bool include_low_coverage: Include low coverage regions.
        :param int max_low_coverage: Max. low coverage.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str clinical_analysis_id: Clinical analysis ID.
        :param str panel_ids: Comma separated list of disease panel IDs.
        :param str family_segregation: Filter by mode of inheritance from a given family. Accepted values: [ monoallelic, monoallelicIncompletePenetrance, biallelic, biallelicIncompletePenetrance, XlinkedBiallelic, XlinkedMonoallelic, Ylinked, MendelianError, DeNovo, CompoundHeterozygous ].
        """

        return self._post('run', subcategory='interpretation/team', **options)

    def run_interpretation_tiering(self, **options):
        """
        GEL Tiering interpretation analysis.
        PATH: /{apiVersion}/analysis/clinical/interpretation/tiering/run

        :param bool include_low_coverage: Include low coverage regions.
        :param int max_low_coverage: Max. low coverage.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str clinical_analysis_id: Clinical analysis ID.
        :param str panel_ids: Comma separated list of disease panel IDs.
        :param str penetrance: Penetrance.
        """

        return self._post('run', subcategory='interpretation/tiering', **options)

    def run_interpretation_custom(self, **options):
        """
        Interpretation custom analysis.
        PATH: /{apiVersion}/analysis/clinical/interpretation/custom/run

        :param str include: Fields included in the response, whole JSON path must be provided.
        :param str exclude: Fields excluded in the response, whole JSON path must be provided.
        :param int limit: Number of results to be returned.
        :param int skip: Number of results to skip.
        :param bool sort: Sort the results.
        :param bool summary: Fast fetch of main variant parameters.
        :param bool include_low_coverage: Include low coverage regions.
        :param int max_low_coverage: Max. low coverage.
        :param bool skip_untiered_variants: Skip variants without tier assigned.
        :param str id: List of IDs, these can be rs IDs (dbSNP) or variants in the format chrom:start:ref:alt, e.g. rs116600158,19:7177679:C:T.
        :param str region: List of regions, these can be just a single chromosome name or regions in the format chr:start-end, e.g.: 2,3:100000-200000.
        :param str type: List of types, accepted values are SNV, MNV, INDEL, SV, CNV, INSERTION, DELETION, e.g. SNV,INDEL.
        :param str reference: Reference allele.
        :param str alternate: Main alternate allele.
        :param str project: Project [user@]project where project can be either the ID or the alias.
        :param str file: Filter variants from the files specified. This will set includeFile parameter when not provided.
        :param str filter: Specify the FILTER for any of the files. If 'file' filter is provided, will match the file and the filter. e.g.: PASS,LowGQX.
        :param str qual: Specify the QUAL for any of the files. If 'file' filter is provided, will match the file and the qual. e.g.: >123.4.
        :param str info: Filter by INFO attributes from file. [{file}:]{key}{op}{value}[,;]* . If no file is specified, will use all files from "file" filter. e.g. AN>200 or file_1.vcf:AN>200;file_2.vcf:AN<10 . Many INFO fields can be combined. e.g. file_1.vcf:AN>200;DB=true;file_2.vcf:AN<10.
        :param str sample: Filter variants where the samples contain the variant (HET or HOM_ALT). Accepts AND (;) and OR (,) operators. This will automatically set 'includeSample' parameter when not provided.
        :param str genotype: Samples with a specific genotype: {samp_1}:{gt_1}(,{gt_n})*(;{samp_n}:{gt_1}(,{gt_n})*)* e.g. HG0097:0/0;HG0098:0/1,1/1. Unphased genotypes (e.g. 0/1, 1/1) will also include phased genotypes (e.g. 0|1, 1|0, 1|1), but not vice versa. When filtering by multi-allelic genotypes, any secondary allele will match, regardless of its position e.g. 1/2 will match with genotypes 1/2, 1/3, 1/4, .... Genotype aliases accepted: HOM_REF, HOM_ALT, HET, HET_REF, HET_ALT and MISS  e.g. HG0097:HOM_REF;HG0098:HET_REF,HOM_ALT. This will automatically set 'includeSample' parameter when not provided.
        :param str format: Filter by any FORMAT field from samples. [{sample}:]{key}{op}{value}[,;]* . If no sample is specified, will use all samples from "sample" or "genotype" filter. e.g. DP>200 or HG0097:DP>200,HG0098:DP<10 . Many FORMAT fields can be combined. e.g. HG0097:DP>200;GT=1/1,0/1,HG0098:DP<10.
        :param str sample_annotation: Selects some samples using metadata information from Catalog. e.g. age>20;phenotype=hpo:123,hpo:456;name=smith.
        :param bool sample_metadata: Return the samples metadata group by study. Sample names will appear in the same order as their corresponding genotypes.
        :param str unknown_genotype: Returned genotype for unknown genotypes. Common values: [0/0, 0|0, ./.].
        :param int sample_limit: Limit the number of samples to be included in the result.
        :param int sample_skip: Skip some samples from the result. Useful for sample pagination.
        :param str cohort: Select variants with calculated stats for the selected cohorts.
        :param str cohort_stats_ref: Reference Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_alt: Alternate Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_maf: Minor Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_mgf: Minor Genotype Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str missing_alleles: Number of missing alleles: [{study:}]{cohort}[<|>|<=|>=]{number}.
        :param str missing_genotypes: Number of missing genotypes: [{study:}]{cohort}[<|>|<=|>=]{number}.
        :param str score: Filter by variant score: [{study:}]{score}[<|>|<=|>=]{number}.
        :param str family: Filter variants where any of the samples from the given family contains the variant (HET or HOM_ALT).
        :param str family_disorder: Specify the disorder to use for the family segregation.
        :param str family_segregation: Filter by mode of inheritance from a given family. Accepted values: [ monoallelic, monoallelicIncompletePenetrance, biallelic, biallelicIncompletePenetrance, XlinkedBiallelic, XlinkedMonoallelic, Ylinked, MendelianError, DeNovo, CompoundHeterozygous ].
        :param str family_members: Sub set of the members of a given family.
        :param str family_proband: Specify the proband child to use for the family segregation.
        :param str penetrance: Penetrance.
        :param str include_study: List of studies to include in the result. Accepts 'all' and 'none'.
        :param str include_file: List of files to be returned. Accepts 'all' and 'none'.
        :param str include_sample: List of samples to be included in the result. Accepts 'all' and 'none'.
        :param str include_format: List of FORMAT names from Samples Data to include in the output. e.g: DP,AD. Accepts 'all' and 'none'.
        :param str include_genotype: Include genotypes, apart of other formats defined with includeFormat.
        :param bool annotation_exists: Return only annotated variants.
        :param str gene: List of genes, most gene IDs are accepted (HGNC, Ensembl gene, ...). This is an alias to 'xref' parameter.
        :param str ct: List of SO consequence types, e.g. missense_variant,stop_lost or SO:0001583,SO:0001578.
        :param str xref: List of any external reference, these can be genes, proteins or variants. Accepted IDs include HGNC, Ensembl genes, dbSNP, ClinVar, HPO, Cosmic, ...
        :param str biotype: List of biotypes, e.g. protein_coding.
        :param str protein_substitution: Protein substitution scores include SIFT and PolyPhen. You can query using the score {protein_score}[<|>|<=|>=]{number} or the description {protein_score}[~=|=]{description} e.g. polyphen>0.1,sift=tolerant.
        :param str conservation: Filter by conservation score: {conservation_score}[<|>|<=|>=]{number} e.g. phastCons>0.5,phylop<0.1,gerp>0.1.
        :param str population_frequency_alt: Alternate Population Frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str population_frequency_ref: Reference Population Frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str population_frequency_maf: Population minor allele frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str transcript_flag: List of transcript annotation flags. e.g. CCDS, basic, cds_end_NF, mRNA_end_NF, cds_start_NF, mRNA_start_NF, seleno.
        :param str gene_trait_id: List of gene trait association id. e.g. "umls:C0007222" , "OMIM:269600".
        :param str go: List of GO (Gene Ontology) terms. e.g. "GO:0002020".
        :param str expression: List of tissues of interest. e.g. "lung".
        :param str protein_keyword: List of Uniprot protein variant annotation keywords.
        :param str drug: List of drug names.
        :param str functional_score: Functional score: {functional_score}[<|>|<=|>=]{number} e.g. cadd_scaled>5.2 , cadd_raw<=0.3.
        :param str clinical_significance: Clinical significance: benign, likely_benign, likely_pathogenic, pathogenic.
        :param str custom_annotation: Custom annotation: {key}[<|>|<=|>=]{number} or {key}[~=|=]{text}.
        :param str panel: Filter by genes from the given disease panel.
        :param str trait: List of traits, based on ClinVar, HPO, COSMIC, i.e.: IDs, histologies, descriptions,...
        :param str clinical_analysis_id: Clinical analysis ID.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        """

        return self._post('run', subcategory='interpretation/custom', **options)

    def run_interpretation_cancer_tiering(self, **options):
        """
        Cancer Tiering interpretation analysis.
        PATH: /{apiVersion}/analysis/clinical/interpretation/cancerTiering/run

        :param bool include_low_coverage: Include low coverage regions.
        :param int max_low_coverage: Max. low coverage.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str clinical_analysis_id: Clinical analysis ID.
        :param str panel_ids: Comma separated list of variant IDs to discard.
        """

        return self._post('run', subcategory='interpretation/cancerTiering', **options)

    def primary_findings_interpretation(self, **options):
        """
        Search for secondary findings for a given query.
        PATH: /{apiVersion}/analysis/clinical/interpretation/primaryFindings

        :param str include: Fields included in the response, whole JSON path must be provided.
        :param str exclude: Fields excluded in the response, whole JSON path must be provided.
        :param int limit: Number of results to be returned.
        :param int skip: Number of results to skip.
        :param bool sort: Sort the results.
        :param bool summary: Fast fetch of main variant parameters.
        :param bool skip_untiered_variants: Skip variants without tier assigned.
        :param str id: List of IDs, these can be rs IDs (dbSNP) or variants in the format chrom:start:ref:alt, e.g. rs116600158,19:7177679:C:T.
        :param str region: List of regions, these can be just a single chromosome name or regions in the format chr:start-end, e.g.: 2,3:100000-200000.
        :param str type: List of types, accepted values are SNV, MNV, INDEL, SV, CNV, INSERTION, DELETION, e.g. SNV,INDEL.
        :param str reference: Reference allele.
        :param str alternate: Main alternate allele.
        :param str project: Project [user@]project where project can be either the ID or the alias.
        :param str file: Filter variants from the files specified. This will set includeFile parameter when not provided.
        :param str filter: Specify the FILTER for any of the files. If 'file' filter is provided, will match the file and the filter. e.g.: PASS,LowGQX.
        :param str qual: Specify the QUAL for any of the files. If 'file' filter is provided, will match the file and the qual. e.g.: >123.4.
        :param str info: Filter by INFO attributes from file. [{file}:]{key}{op}{value}[,;]* . If no file is specified, will use all files from "file" filter. e.g. AN>200 or file_1.vcf:AN>200;file_2.vcf:AN<10 . Many INFO fields can be combined. e.g. file_1.vcf:AN>200;DB=true;file_2.vcf:AN<10.
        :param str sample: Filter variants where the samples contain the variant (HET or HOM_ALT). Accepts AND (;) and OR (,) operators. This will automatically set 'includeSample' parameter when not provided.
        :param str genotype: Samples with a specific genotype: {samp_1}:{gt_1}(,{gt_n})*(;{samp_n}:{gt_1}(,{gt_n})*)* e.g. HG0097:0/0;HG0098:0/1,1/1. Unphased genotypes (e.g. 0/1, 1/1) will also include phased genotypes (e.g. 0|1, 1|0, 1|1), but not vice versa. When filtering by multi-allelic genotypes, any secondary allele will match, regardless of its position e.g. 1/2 will match with genotypes 1/2, 1/3, 1/4, .... Genotype aliases accepted: HOM_REF, HOM_ALT, HET, HET_REF, HET_ALT and MISS  e.g. HG0097:HOM_REF;HG0098:HET_REF,HOM_ALT. This will automatically set 'includeSample' parameter when not provided.
        :param str format: Filter by any FORMAT field from samples. [{sample}:]{key}{op}{value}[,;]* . If no sample is specified, will use all samples from "sample" or "genotype" filter. e.g. DP>200 or HG0097:DP>200,HG0098:DP<10 . Many FORMAT fields can be combined. e.g. HG0097:DP>200;GT=1/1,0/1,HG0098:DP<10.
        :param str sample_annotation: Selects some samples using metadata information from Catalog. e.g. age>20;phenotype=hpo:123,hpo:456;name=smith.
        :param bool sample_metadata: Return the samples metadata group by study. Sample names will appear in the same order as their corresponding genotypes.
        :param str unknown_genotype: Returned genotype for unknown genotypes. Common values: [0/0, 0|0, ./.].
        :param int sample_limit: Limit the number of samples to be included in the result.
        :param int sample_skip: Skip some samples from the result. Useful for sample pagination.
        :param str cohort: Select variants with calculated stats for the selected cohorts.
        :param str cohort_stats_ref: Reference Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_alt: Alternate Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_maf: Minor Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_mgf: Minor Genotype Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str missing_alleles: Number of missing alleles: [{study:}]{cohort}[<|>|<=|>=]{number}.
        :param str missing_genotypes: Number of missing genotypes: [{study:}]{cohort}[<|>|<=|>=]{number}.
        :param str score: Filter by variant score: [{study:}]{score}[<|>|<=|>=]{number}.
        :param str family: Filter variants where any of the samples from the given family contains the variant (HET or HOM_ALT).
        :param str family_disorder: Specify the disorder to use for the family segregation.
        :param str family_segregation: Filter by mode of inheritance from a given family. Accepted values: [ monoallelic, monoallelicIncompletePenetrance, biallelic, biallelicIncompletePenetrance, XlinkedBiallelic, XlinkedMonoallelic, Ylinked, MendelianError, DeNovo, CompoundHeterozygous ].
        :param str family_members: Sub set of the members of a given family.
        :param str family_proband: Specify the proband child to use for the family segregation.
        :param str penetrance: Penetrance.
        :param str include_study: List of studies to include in the result. Accepts 'all' and 'none'.
        :param str include_file: List of files to be returned. Accepts 'all' and 'none'.
        :param str include_sample: List of samples to be included in the result. Accepts 'all' and 'none'.
        :param str include_format: List of FORMAT names from Samples Data to include in the output. e.g: DP,AD. Accepts 'all' and 'none'.
        :param str include_genotype: Include genotypes, apart of other formats defined with includeFormat.
        :param bool annotation_exists: Return only annotated variants.
        :param str gene: List of genes, most gene IDs are accepted (HGNC, Ensembl gene, ...). This is an alias to 'xref' parameter.
        :param str ct: List of SO consequence types, e.g. missense_variant,stop_lost or SO:0001583,SO:0001578.
        :param str xref: List of any external reference, these can be genes, proteins or variants. Accepted IDs include HGNC, Ensembl genes, dbSNP, ClinVar, HPO, Cosmic, ...
        :param str biotype: List of biotypes, e.g. protein_coding.
        :param str protein_substitution: Protein substitution scores include SIFT and PolyPhen. You can query using the score {protein_score}[<|>|<=|>=]{number} or the description {protein_score}[~=|=]{description} e.g. polyphen>0.1,sift=tolerant.
        :param str conservation: Filter by conservation score: {conservation_score}[<|>|<=|>=]{number} e.g. phastCons>0.5,phylop<0.1,gerp>0.1.
        :param str population_frequency_alt: Alternate Population Frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str population_frequency_ref: Reference Population Frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str population_frequency_maf: Population minor allele frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str transcript_flag: List of transcript annotation flags. e.g. CCDS, basic, cds_end_NF, mRNA_end_NF, cds_start_NF, mRNA_start_NF, seleno.
        :param str gene_trait_id: List of gene trait association id. e.g. "umls:C0007222" , "OMIM:269600".
        :param str go: List of GO (Gene Ontology) terms. e.g. "GO:0002020".
        :param str expression: List of tissues of interest. e.g. "lung".
        :param str protein_keyword: List of Uniprot protein variant annotation keywords.
        :param str drug: List of drug names.
        :param str functional_score: Functional score: {functional_score}[<|>|<=|>=]{number} e.g. cadd_scaled>5.2 , cadd_raw<=0.3.
        :param str clinical_significance: Clinical significance: benign, likely_benign, likely_pathogenic, pathogenic.
        :param str custom_annotation: Custom annotation: {key}[<|>|<=|>=]{number} or {key}[~=|=]{text}.
        :param str panel: Filter by genes from the given disease panel.
        :param str trait: List of traits, based on ClinVar, HPO, COSMIC, i.e.: IDs, histologies, descriptions,...
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        """

        return self._get('primaryFindings', subcategory='interpretation', **options)

    def secondary_findings_interpretation(self, **options):
        """
        Search for secondary findings for a given sample.
        PATH: /{apiVersion}/analysis/clinical/interpretation/secondaryFindings

        :param str sample: Sample id.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        """

        return self._get('secondaryFindings', subcategory='interpretation', **options)

    def acl(self, clinical_analyses, **options):
        """
        Returns the acl of the clinical analyses. If member is provided, it will only return the acl for the member.
        PATH: /{apiVersion}/analysis/clinical/{clinicalAnalyses}/acl

        :param str clinical_analyses: Comma separated list of clinical analysis IDs or names up to a maximum of 100.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str member: User or group id.
        :param bool silent: Boolean to retrieve all possible entries that are queried for, false to raise an exception whenever one of the entries looked for cannot be shown for whichever reason.
        """

        return self._get('acl', query_id=clinical_analyses, **options)

    def update_acl(self, members, data=None, **options):
        """
        Update the set of permissions granted for the member.
        PATH: /{apiVersion}/analysis/clinical/acl/{members}/update

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str members: Comma separated list of user or group ids.
        :param dict data: JSON containing the parameters to add ACLs.
        """

        return self._post('update', query_id=members, data=data, **options)

    def index_interpretation(self, **options):
        """
        Index clinical analysis interpretations in the clinical variant database.
        PATH: /{apiVersion}/analysis/clinical/interpretation/index

        :param str interpretation_id: Comma separated list of interpretation IDs to be indexed in the clinical variant database.
        :param str clinical_analysis_id: Comma separated list of clinical analysis IDs to be indexed in the clinical variant database.
        :param bool false: Reset the clinical variant database and import the specified interpretations.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        """

        return self._get('index', subcategory='interpretation', **options)

    def update(self, clinical_analyses, data=None, **options):
        """
        Update clinical analysis attributes.
        PATH: /{apiVersion}/analysis/clinical/{clinicalAnalyses}/update

        :param str clinical_analyses: Comma separated list of clinical analysis ids.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param dict data: JSON containing clinical analysis information.
        """

        return self._post('update', query_id=clinical_analyses, data=data, **options)

    def update_interpretation(self, clinical_analysis, interpretation, data=None, **options):
        """
        Update Interpretation fields.
        PATH: /{apiVersion}/analysis/clinical/{clinicalAnalysis}/interpretations/{interpretation}/update

        :param str study: [[user@]project:]study id.
        :param str clinical_analysis: Clinical analysis id.
        :param str interpretation: Interpretation id.
        :param dict data: JSON containing clinical interpretation information.
        """

        return self._post('update', query_id=clinical_analysis, subcategory='interpretations', second_query_id=interpretation, data=data, **options)

    def info(self, clinical_analyses, **options):
        """
        Clinical analysis info.
        PATH: /{apiVersion}/analysis/clinical/{clinicalAnalyses}/info

        :param str include: Fields included in the response, whole JSON path must be provided.
        :param str exclude: Fields excluded in the response, whole JSON path must be provided.
        :param str clinical_analyses: Comma separated list of clinical analysis IDs or names up to a maximum of 100.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        """

        return self._get('info', query_id=clinical_analyses, **options)

    def create(self, data=None, **options):
        """
        Create a new clinical analysis.
        PATH: /{apiVersion}/analysis/clinical/create

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param dict data: JSON containing clinical analysis information.
        """

        return self._post('create', data=data, **options)

    def query_interpretation(self, **options):
        """
        Query for reported variants.
        PATH: /{apiVersion}/analysis/clinical/interpretation/query

        :param str include: Fields included in the response, whole JSON path must be provided.
        :param str exclude: Fields excluded in the response, whole JSON path must be provided.
        :param int limit: Number of results to be returned.
        :param int skip: Number of results to skip.
        :param bool count: Get the total number of results matching the query. Deactivated by default.
        :param bool skip_count: Do not count total number of results.
        :param bool sort: Sort the results.
        :param bool summary: Fast fetch of main variant parameters.
        :param bool approximate_count: Get an approximate count, instead of an exact total count. Reduces execution time.
        :param int approximate_count_sampling_size: Sampling size to get the approximate count. Larger values increase accuracy but also increase execution time.
        :param str id: List of IDs, these can be rs IDs (dbSNP) or variants in the format chrom:start:ref:alt, e.g. rs116600158,19:7177679:C:T.
        :param str region: List of regions, these can be just a single chromosome name or regions in the format chr:start-end, e.g.: 2,3:100000-200000.
        :param str type: List of types, accepted values are SNV, MNV, INDEL, SV, CNV, INSERTION, DELETION, e.g. SNV,INDEL.
        :param str reference: Reference allele.
        :param str alternate: Main alternate allele.
        :param str project: Project [user@]project where project can be either the ID or the alias.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str file: Filter variants from the files specified. This will set includeFile parameter when not provided.
        :param str filter: Specify the FILTER for any of the files. If 'file' filter is provided, will match the file and the filter. e.g.: PASS,LowGQX.
        :param str qual: Specify the QUAL for any of the files. If 'file' filter is provided, will match the file and the qual. e.g.: >123.4.
        :param str info: Filter by INFO attributes from file. [{file}:]{key}{op}{value}[,;]* . If no file is specified, will use all files from "file" filter. e.g. AN>200 or file_1.vcf:AN>200;file_2.vcf:AN<10 . Many INFO fields can be combined. e.g. file_1.vcf:AN>200;DB=true;file_2.vcf:AN<10.
        :param str sample: Filter variants where the samples contain the variant (HET or HOM_ALT). Accepts AND (;) and OR (,) operators. This will automatically set 'includeSample' parameter when not provided.
        :param str genotype: Samples with a specific genotype: {samp_1}:{gt_1}(,{gt_n})*(;{samp_n}:{gt_1}(,{gt_n})*)* e.g. HG0097:0/0;HG0098:0/1,1/1. Unphased genotypes (e.g. 0/1, 1/1) will also include phased genotypes (e.g. 0|1, 1|0, 1|1), but not vice versa. When filtering by multi-allelic genotypes, any secondary allele will match, regardless of its position e.g. 1/2 will match with genotypes 1/2, 1/3, 1/4, .... Genotype aliases accepted: HOM_REF, HOM_ALT, HET, HET_REF, HET_ALT and MISS  e.g. HG0097:HOM_REF;HG0098:HET_REF,HOM_ALT. This will automatically set 'includeSample' parameter when not provided.
        :param str format: Filter by any FORMAT field from samples. [{sample}:]{key}{op}{value}[,;]* . If no sample is specified, will use all samples from "sample" or "genotype" filter. e.g. DP>200 or HG0097:DP>200,HG0098:DP<10 . Many FORMAT fields can be combined. e.g. HG0097:DP>200;GT=1/1,0/1,HG0098:DP<10.
        :param str sample_annotation: Selects some samples using metadata information from Catalog. e.g. age>20;phenotype=hpo:123,hpo:456;name=smith.
        :param bool sample_metadata: Return the samples metadata group by study. Sample names will appear in the same order as their corresponding genotypes.
        :param str unknown_genotype: Returned genotype for unknown genotypes. Common values: [0/0, 0|0, ./.].
        :param int sample_limit: Limit the number of samples to be included in the result.
        :param int sample_skip: Skip some samples from the result. Useful for sample pagination.
        :param str cohort: Select variants with calculated stats for the selected cohorts.
        :param str cohort_stats_ref: Reference Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_alt: Alternate Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_maf: Minor Allele Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str cohort_stats_mgf: Minor Genotype Frequency: [{study:}]{cohort}[<|>|<=|>=]{number}. e.g. ALL<=0.4.
        :param str missing_alleles: Number of missing alleles: [{study:}]{cohort}[<|>|<=|>=]{number}.
        :param str missing_genotypes: Number of missing genotypes: [{study:}]{cohort}[<|>|<=|>=]{number}.
        :param str score: Filter by variant score: [{study:}]{score}[<|>|<=|>=]{number}.
        :param str include_study: List of studies to include in the result. Accepts 'all' and 'none'.
        :param str include_file: List of files to be returned. Accepts 'all' and 'none'.
        :param str include_sample: List of samples to be included in the result. Accepts 'all' and 'none'.
        :param str include_format: List of FORMAT names from Samples Data to include in the output. e.g: DP,AD. Accepts 'all' and 'none'.
        :param str include_genotype: Include genotypes, apart of other formats defined with includeFormat.
        :param bool annotation_exists: Return only annotated variants.
        :param str gene: List of genes, most gene IDs are accepted (HGNC, Ensembl gene, ...). This is an alias to 'xref' parameter.
        :param str ct: List of SO consequence types, e.g. missense_variant,stop_lost or SO:0001583,SO:0001578.
        :param str xref: List of any external reference, these can be genes, proteins or variants. Accepted IDs include HGNC, Ensembl genes, dbSNP, ClinVar, HPO, Cosmic, ...
        :param str biotype: List of biotypes, e.g. protein_coding.
        :param str protein_substitution: Protein substitution scores include SIFT and PolyPhen. You can query using the score {protein_score}[<|>|<=|>=]{number} or the description {protein_score}[~=|=]{description} e.g. polyphen>0.1,sift=tolerant.
        :param str conservation: Filter by conservation score: {conservation_score}[<|>|<=|>=]{number} e.g. phastCons>0.5,phylop<0.1,gerp>0.1.
        :param str population_frequency_alt: Alternate Population Frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str population_frequency_ref: Reference Population Frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str population_frequency_maf: Population minor allele frequency: {study}:{population}[<|>|<=|>=]{number}. e.g. 1kG_phase3:ALL<0.01.
        :param str transcript_flag: List of transcript annotation flags. e.g. CCDS, basic, cds_end_NF, mRNA_end_NF, cds_start_NF, mRNA_start_NF, seleno.
        :param str gene_trait_id: List of gene trait association id. e.g. "umls:C0007222" , "OMIM:269600".
        :param str go: List of GO (Gene Ontology) terms. e.g. "GO:0002020".
        :param str expression: List of tissues of interest. e.g. "lung".
        :param str protein_keyword: List of Uniprot protein variant annotation keywords.
        :param str drug: List of drug names.
        :param str functional_score: Functional score: {functional_score}[<|>|<=|>=]{number} e.g. cadd_scaled>5.2 , cadd_raw<=0.3.
        :param str clinical_significance: Clinical significance: benign, likely_benign, likely_pathogenic, pathogenic.
        :param str custom_annotation: Custom annotation: {key}[<|>|<=|>=]{number} or {key}[~=|=]{text}.
        :param str trait: List of traits, based on ClinVar, HPO, COSMIC, i.e.: IDs, histologies, descriptions,...
        :param str clinical_analysis_id: List of clinical analysis IDs.
        :param str clinical_analysis_name: List of clinical analysis names.
        :param str clinical_analysis_descr: Clinical analysis description.
        :param str clinical_analysis_files: List of clinical analysis files.
        :param str clinical_analysis_proband_id: List of proband IDs.
        :param str clinical_analysis_proband_disorders: List of proband disorders.
        :param str clinical_analysis_proband_phenotypes: List of proband phenotypes.
        :param str clinical_analysis_family_id: List of family IDs.
        :param str clinical_analysis_fam_member_ids: List of clinical analysis family member IDs.
        :param str interpretation_id: List of interpretation IDs.
        :param str interpretation_software_name: List of interpretation software names.
        :param str interpretation_software_version: List of interpretation software versions.
        :param str interpretation_analyst_name: List of interpretation analysist names.
        :param str interpretation_panels: List of interpretation panels.
        :param str interpretation_description: Interpretation description.
        :param str interpretation_dependencies: List of interpretation dependency, format: name:version, e.g. cellbase:4.0.
        :param str interpretation_filters: List of interpretation filters.
        :param str interpretation_comments: List of interpretation comments.
        :param str interpretation_creation_date: Iinterpretation creation date (including date ranges).
        :param str reported_variant_de_novo_quality_score: List of reported variant de novo quality scores.
        :param str reported_variant_comments: List of reported variant comments.
        :param str reported_event_phenotype_names: List of reported event phenotype names.
        :param str reported_event_consequence_type_ids: List of reported event consequence type IDs.
        :param str reported_event_xrefs: List of reported event phenotype xRefs.
        :param str reported_event_panel_ids: List of reported event panel IDs.
        :param str reported_event_acmg: List of reported event ACMG.
        :param str reported_event_clinical_significance: List of reported event clinical significance.
        :param str reported_event_drug_response: List of reported event drug response.
        :param str reported_event_trait_association: List of reported event trait association.
        :param str reported_event_functional_effect: List of reported event functional effect.
        :param str reported_event_tumorigenesis: List of reported event tumorigenesis.
        :param str reported_event_other_classification: List of reported event other classification.
        :param str reported_event_roles_in_cancer: List of reported event roles in cancer.
        """

        return self._get('query', subcategory='interpretation', **options)

    def search(self, **options):
        """
        Clinical analysis search.
        PATH: /{apiVersion}/analysis/clinical/search

        :param str include: Fields included in the response, whole JSON path must be provided.
        :param str exclude: Fields excluded in the response, whole JSON path must be provided.
        :param int limit: Number of results to be returned.
        :param int skip: Number of results to skip.
        :param bool count: Get the total number of results matching the query. Deactivated by default.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str type: Clinical analysis type.
        :param str priority: Priority.
        :param str status: Clinical analysis status.
        :param str creation_date: Creation date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805.
        :param str modification_date: Modification date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805.
        :param str due_date: Due date (Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805...).
        :param str description: Description.
        :param str family: Family id.
        :param str proband: Proband id.
        :param str sample: Proband sample.
        :param str analyst_assignee: Clinical analyst assignee.
        :param str disorder: Disorder id or name.
        :param str flags: Flags.
        :param str release: Release value.
        :param str attributes: Text attributes (Format: sex=male,age>20 ...).
        """

        return self._get('search', **options)

