package dk.kb.elivagar.pubhub.validator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import dk.kb.elivagar.utils.StringUtils;

/**
 * Validates the suffix of files.
 */
public abstract class FileSuffixValidator {
    /** The list of valid suffixes. */ 
    protected final List<String> validSuffices;

    /**
     * Constructor.
     * @param validSuffices The list of valid suffices.
     */
    protected FileSuffixValidator(List<String> validSuffices) {
        this.validSuffices = new ArrayList<String>(validSuffices);
    }

    /**
     * Validates whether the file has a suffix from
     * @param fileToValidate The file to have the suffix validated.
     * @return Whether it was valid or not.
     */
    public boolean hasValidSuffix(File fileToValidate) {
        String suffix = StringUtils.getSuffix(fileToValidate.getAbsolutePath());
        for(String validSuffix : validSuffices) {
            if(suffix.endsWith(validSuffix)) {
                return true;
            }
        }
        return false;
    }
}
