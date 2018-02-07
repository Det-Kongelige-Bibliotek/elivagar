package dk.kb.elivagar;

/**
 * The constants, which are used across different classes.
 */
public class Constants {

    /** The suffix of XML files.*/
    private static final String XML_SUFFIX = ".xml";
    /** The suffix of Publizon metadata XML files from pubhub.*/
    public static final String PUBHUB_METADATA_SUFFIX = ".pubhub" + XML_SUFFIX;
    /** The suffix for the fits characterization metadata output files.*/
    public static final String FITS_METADATA_SUFFIX = ".fits" + XML_SUFFIX;
    /** The suffix for the epubcheck characterization metadata output files.*/
    public static final String EPUBCHECK_METADATA_SUFFIX = ".epubcheck" + XML_SUFFIX;

    /** The suffix for the temporary MARC21 files.*/
    public static final String MARC_METADATA_SUFFIX = ".marc" + XML_SUFFIX;
    /** The suffix for the temporary Aleph DanMarc2 files.*/
    public static final String ALEPH_METADATA_SUFFIX = ".aleph" + XML_SUFFIX;
    /** The suffix for the MODS files.*/
    public static final String MODS_METADATA_SUFFIX = ".mods" + XML_SUFFIX;

    /** The suffix for the epub files.*/
    public static final String EPUB_FILE_SUFFIX = ".epub";
}