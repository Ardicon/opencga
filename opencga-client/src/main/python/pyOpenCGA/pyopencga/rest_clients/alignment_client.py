from pyopencga.rest_clients._parent_rest_clients import _ParentRestClient


class Alignment(_ParentRestClient):
    """
    This class contains methods for the 'Analysis - Alignment' webservices
    Client version: 2.0.0
    PATH: /{apiVersion}/analysis/alignment
    """

    def __init__(self, configuration, token=None, login_handler=None, *args, **kwargs):
        _category = 'analysis/alignment'
        super(Alignment, self).__init__(configuration, _category, token, login_handler, *args, **kwargs)

    def run_stats(self, file, **options):
        """
        Compute stats for a given alignment file
        PATH: /{apiVersion}/analysis/alignment/stats/run

        :param str file: File ID
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        """
        options['file'] = file
        return self._post('stats/run', **options)

    def run_coverage(self, file, **options):
        """
        Compute coverage for a list of alignment files
        PATH: /{apiVersion}/analysis/alignment/coverage/run

        :param str file: File ID
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param int window_size: Window size for the region coverage (if a coverage range is provided, window size must be 1)
        """
        options['file'] = file
        return self._post('coverage/run', **options)

    def query_coverage(self, file, **options):
        """
        Query the coverage of an alignment file for regions or genes
        PATH: /{apiVersion}/analysis/alignment/coverage/query

        :param str file: File ID
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param str region: Comma separated list of regions 'chr:start-end, e.g.: 2,3:63500-65000
        :param str gene: Comma separated list of genes, e.g.: BCRA2,TP53
        :param int offset: Offset to extend the region, gene or exon at up and downstream
        :param bool only_exons: Only exons are taking into account when genes are specified
        :param str range: Range of coverage values to be reported. Minimum and maximum values are separated by '-', e.g.: 20-40 (for coverage values greater or equal to 20 and less or equal to 40). A single value means to report coverage values less or equal to that value
        :param int window_size: Window size for the region coverage (if a coverage range is provided, window size must be 1)
        :param bool split_results: Split results into regions (or gene/exon regions)
        """
        options['file'] = file
        return self._get('coverage/query', **options)

    def ratio_coverage(self, file1, file2, **options):
        """
        Compute coverage ratio from file #1 vs file #2, (e.g. somatic vs germline)
        PATH: /{apiVersion}/analysis/alignment/coverage/ratio

        :param str file1: Input file #1 (e.g. somatic file)
        :param str file2: Input file #2 (e.g. germline file)
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param bool skip_log2: Do not apply Log2 to normalise the coverage ratio
        :param str region: Comma separated list of regions 'chr:start-end, e.g.: 2,3:63500-65000
        :param str gene: Comma separated list of genes, e.g.: BCRA2,TP53
        :param int offset: Offset to extend the region, gene or exon at up and downstream
        :param bool only_exons: Only exons are taking into account when genes are specified
        :param int window_size: Window size for the region coverage (if a coverage range is provided, window size must be 1)
        :param bool split_results: Split results into regions (or gene/exon regions)
        """
        options['file1'] = file1
        options['file2'] = file2
        return self._get('coverage/ratio', **options)

    def info_stats(self, file, **options):
        """
        Show the stats for a given alignment file
        PATH: /{apiVersion}/analysis/alignment/stats/info

        :param str file: File ID
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        """
        options['file'] = file
        return self._get('stats/info', **options)

    def query_stats(self, **options):
        """
        Fetch alignment files according to their stats
        PATH: /{apiVersion}/analysis/alignment/stats/query

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param str raw_total_sequences: Raw total sequences: [<|>|<=|>=]{number}, e.g. >=1000
        :param str filtered_sequences: Filtered sequences: [<|>|<=|>=]{number}, e.g. <=500
        :param str reads_mapped: Reads mapped: [<|>|<=|>=]{number}, e.g. >3000
        :param str reads_mapped_and_paired: Reads mapped and paired: paired-end technology bit set + both mates mapped: [<|>|<=|>=]{number}, e.g. >=1000
        :param str reads_unmapped: Reads unmapped: [<|>|<=|>=]{number}, e.g. >=1000
        :param str reads_properly_paired: Reads properly paired (proper-pair bit set: [<|>|<=|>=]{number}, e.g. >=1000
        :param str reads_paired: Reads paired: paired-end technology bit set: [<|>|<=|>=]{number}, e.g. >=1000
        :param str reads_duplicated: Reads duplicated: PCR or optical duplicate bit set: [<|>|<=|>=]{number}, e.g. >=1000
        :param str reads_m_q0: Reads mapped and MQ = 0: [<|>|<=|>=]{number}, e.g. >=1000
        :param str reads_q_c_failed: Reads QC failed: [<|>|<=|>=]{number}, e.g. >=1000
        :param str non_primary_alignments: Non-primary alignments: [<|>|<=|>=]{number}, e.g. <=100
        :param str mismatches: Mismatches from NM fields: [<|>|<=|>=]{number}, e.g. <=100
        :param str error_rate: Error rate: mismatches / bases mapped (cigar): [<|>|<=|>=]{number}, e.g. <=0.002
        :param str average_length: Average_length: [<|>|<=|>=]{number}, e.g. >=90.0
        :param str average_first_fragment_length: Average first fragment length: [<|>|<=|>=]{number}, e.g. >=90.0
        :param str average_last_fragment_length: Average_last_fragment_length: [<|>|<=|>=]{number}, e.g. >=90.0
        :param str average_quality: Average quality: [<|>|<=|>=]{number}, e.g. >=35.5
        :param str insert_size_average: Insert size average: [<|>|<=|>=]{number}, e.g. >=100.0
        :param str insert_size_standard_deviation: Insert size standard deviation: [<|>|<=|>=]{number}, e.g. <=1.5
        :param str pairs_with_other_orientation: Pairs with other orientation: [<|>|<=|>=]{number}, e.g. >=1000
        :param str pairs_on_different_chromosomes: Pairs on different chromosomes: [<|>|<=|>=]{number}, e.g. >=1000
        :param str percentage_of_properly_paired_reads: Percentage of properly paired reads: [<|>|<=|>=]{number}, e.g. >=96.5
        """

        return self._get('stats/query', **options)

    def run_bwa(self, **options):
        """
        BWA is a software package for mapping low-divergent sequences against a large reference genome.
        PATH: /{apiVersion}/analysis/alignment/bwa/run

        :param str study: study
        :param str job_name: Job name
        :param str job _i_d or _u_u_i_d: Job Description
        :param str job_tags: Job tags
        """

        return self._post('bwa/run', **options)

    def run_samtools(self, **options):
        """
        Samtools is a program for interacting with high-throughput sequencing data in SAM, BAM and CRAM formats.
        PATH: /{apiVersion}/analysis/alignment/samtools/run

        :param str study: study
        :param str job_name: Job name
        :param str job _i_d or _u_u_i_d: Job Description
        :param str job_tags: Job tags
        """

        return self._post('samtools/run', **options)

    def run_deeptools(self, **options):
        """
        Deeptools is a suite of python tools particularly developed for the efficient analysis of high-throughput sequencing data, such as ChIP-seq, RNA-seq or MNase-seq.
        PATH: /{apiVersion}/analysis/alignment/deeptools/run

        :param str study: study
        :param str job_name: Job name
        :param str job _i_d or _u_u_i_d: Job Description
        :param str job_tags: Job tags
        """

        return self._post('deeptools/run', **options)

    def run_fastqc(self, **options):
        """
        A quality control tool for high throughput sequence data.
        PATH: /{apiVersion}/analysis/alignment/fastqc/run

        :param str study: study
        :param str job_name: Job name
        :param str job _i_d or _u_u_i_d: Job Description
        :param str job_tags: Job tags
        """

        return self._post('fastqc/run', **options)

    def index(self, file, **options):
        """
        Index alignment file
        PATH: /{apiVersion}/analysis/alignment/index

        :param str file: File ID
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        """
        options['file'] = file
        return self._post('index', **options)

    def query(self, file, **options):
        """
        Search over indexed alignments
        PATH: /{apiVersion}/analysis/alignment/query

        :param int limit: Number of results to be returned
        :param int skip: Number of results to skip
        :param bool count: Get the total number of results matching the query. Deactivated by default.
        :param str file: File ID
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param str region: Comma separated list of regions 'chr:start-end, e.g.: 2,3:63500-65000
        :param str gene: Comma separated list of genes, e.g.: BCRA2,TP53
        :param int offset: Offset to extend the region, gene or exon at up and downstream
        :param bool only_exons: Only exons are taking into account when genes are specified
        :param int min_mapping_quality: Minimum mapping quality
        :param int max_num_mismatches: Maximum number of mismatches
        :param int max_num_hits: Maximum number of hits
        :param bool properly_paired: Return only properly paired alignments
        :param int max_insert_size: Maximum insert size
        :param bool skip_unmapped: Skip unmapped alignments
        :param bool skip_duplicated: Skip duplicated alignments
        :param bool region_contained: Return alignments contained within boundaries of region
        :param bool force_m_d_field: Force SAM MD optional field to be set with the alignments
        :param bool bin_qualities: Compress the nucleotide qualities by using 8 quality levels
        :param bool split_results: Split results into regions (or gene/exon regions)
        """
        options['file'] = file
        return self._get('query', **options)
