package dk.kb.elivagar;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.kb.elivagar.config.Configuration;
import dk.kb.elivagar.pubhub.PubhubMetadataRetriever;
import dk.kb.elivagar.pubhub.PubhubPacker;
import dk.kb.elivagar.pubhub.PubhubStatistics;
import dk.kb.elivagar.pubhub.validator.AudioSuffixValidator;
import dk.kb.elivagar.pubhub.validator.EbookSuffixValidator;
import dk.kb.elivagar.pubhub.validator.FileSuffixValidator;
import dk.kb.elivagar.script.CharacterizationScriptWrapper;
import dk.pubhub.service.Book;

/**
 * Workflow for the pubhub.
 */
public class PubhubWorkflow {
    /** The logger.*/
    private static final Logger log = LoggerFactory.getLogger(PubhubWorkflow.class);

    /** The sub directory path from audio book dir to the folder with the actual file.*/
    protected static final String AUDIO_SUB_DIR_PATH = "Full/Mp3/";

    /** The configuration for pubhub.*/
    protected final Configuration conf;

    /** The retriever for Pubhub.*/
    protected final PubhubMetadataRetriever retriever;
    /** The packer of the Pubhub data.*/
    protected final PubhubPacker packer;
    /** The HTTP client.*/
    protected final HttpClient httpClient;
    
    /**
     * Constructor. 
     * @param conf The elivagar configuration. 
     */
    public PubhubWorkflow(Configuration conf) {
        this.conf = conf;
        this.retriever = new PubhubMetadataRetriever(conf.getLicenseKey());
        CharacterizationScriptWrapper script = null;
        if(conf.getCharacterizationScriptFile() != null) {
            script = new CharacterizationScriptWrapper(conf.getCharacterizationScriptFile()); 
        }
        this.httpClient = new HttpClient();
        this.packer = new PubhubPacker(conf, retriever.getServiceNamespace(), script, httpClient);
    }

    /**
     * Retrieves all the books.
     * @param max The maximum number of books to retrieve.
     * @throws JAXBException If XML marshaling fail
     * @throws IOException If files cannot be created or downloaded.
     */
    public void retrieveAllBooks(long max) throws JAXBException, IOException {
        List<Book> books = retriever.downloadAllBookMetadata().getBook();
        for(int i = 0; i < books.size() && i < max; i++) {
            Book book = books.get(i);
            packer.packBook(book);
        }
    }

    /**
     * Retrieves the books which have been modified after a given date.
     * Though with a given maximum number of books to retrieve.
     * @param earliestDate The earliest modify date for the book.
     * @param max The maximum number of books to retrieve.
     * @throws JAXBException If XML marshaling fail
     * @throws IOException If files cannot be created or downloaded.
     */
    public void retrieveModifiedBooks(Date earliestDate, long max) throws JAXBException, IOException {
        List<Book> books = retriever.downloadBookMetadataAfterModifyDate(
                earliestDate).getNewAndModifiedBooks().getBook();
        for(int i = 0; i < books.size() && i < max; i++) {
            Book book = books.get(i);
            packer.packBook(book);
        }
    }

    /**
     * Instantiates the packaging of both ebooks and audio books.
     */
    public void packFilesForBooks() {
        packFilesForEbooks();
        packFilesForAudioBooks();
    }

    /**
     * Packs the files for the ebooks into their right folder.
     * It is asserted, that the book files is named with the id as the prefix.
     */
    protected void packFilesForEbooks() {
        File[] eBooks = conf.getEbookFileDir().listFiles();
        if(eBooks == null) {
            log.info("No book files to package.");
            return;
        } else {
            for(File fileForBook : eBooks) {
                try {
                    if(fileForBook.isFile()) {
                        packer.packFileForEbook(fileForBook);
                    } else {
                        log.trace("Cannot package directory: " + fileForBook.getAbsolutePath());
                    }
                } catch (IOException e) {
                    log.error("Failed to package the file '" + fileForBook.getAbsolutePath() + "' for a book. "
                            + "Trying to continue with next book file.", e);
                }
            }
        }
    }

    /**
     * Packs the files for the audio books into their right folder.
     * The audio books are placed in a sub-directory with the following structure:
     * $AUDIO_BOOK_BASE_DIR / ${ID} / Full / Mp3 / ${ID} . mp3
     * 
     * It is asserted, that the book files is named with the id as the prefix.
     */
    protected void packFilesForAudioBooks() {
        File[] audioBooks = conf.getAudioFileDir().listFiles();
        if(audioBooks == null) {
            log.info("No audio files to package.");
            return;
        } else {
            for(File audioBookBaseDir : audioBooks) {
                String id = audioBookBaseDir.getName();
                File audioBookFileDir = new File(audioBookBaseDir, AUDIO_SUB_DIR_PATH);
                File[] audioBookFiles = audioBookFileDir.listFiles();
                if(audioBookFiles == null) {
                    log.trace("Cannot handle non-existing Audio-book file: " 
                            + audioBookFileDir.getAbsolutePath());
                } else {
                    for(File audioBookFile : audioBookFiles) {
                        try {
                            if(!audioBookFile.getName().startsWith(id)) {
                                log.info("Ignores the file '" + audioBookFile.getAbsolutePath() + " since it does not "
                                        + "comply with the format '{ID}/" + AUDIO_SUB_DIR_PATH + "{ID}.{suffix}");
                            } else {
                                if(audioBookFile.isFile()) {
                                    packer.packFileForAudio(audioBookFile);
                                } else {
                                    log.trace("Cannot handle directory: " 
                                            + audioBookFile.getAbsolutePath());
                                }
                            }
                        } catch (IOException e) {
                            log.error("Failed to package the file '" + audioBookBaseDir.getAbsolutePath() 
                                    + "' for a audio book. Trying to continue with next audio book file.", e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Makes and prints the statistics for the both the ebook directory and the audio directory.
     * @param printer The print stream where the output is written.
     */
    public void makeStatistics(PrintStream printer) {
        if(conf.getEbookOutputDir().list() != null) {
            makeStatisticsForDirectory(printer, conf.getEbookOutputDir(), new EbookSuffixValidator(conf));
        } else {
            printer.println("No ebooks to make statistics upon.");
        }
        if(conf.getAudioOutputDir().list() != null) {
            makeStatisticsForDirectory(printer, conf.getAudioOutputDir(), new AudioSuffixValidator(conf));
        } else {
            printer.println("No ebooks to make statistics upon.");
        }
    }

    /**
     * Calculates the statistics on the books in the given directory.
     * @param printer The print stream where the output is written.
     * @param dir The directory to calculate the statistics upon.
     */
    protected void makeStatisticsForDirectory(PrintStream printer, File dir, FileSuffixValidator validator) {
        PubhubStatistics statistics = new PubhubStatistics(dir, validator);
        printer.println("Calculating the statistics for directory: " + dir.getAbsolutePath());
        statistics.calculateStatistics();
        printer.println("Number of book directories traversed: " + statistics.getTotalCount());
        printer.println("Number of book directories with both book file and metadata file: '" 
                + statistics.getBothDataCount() + "'");
        printer.println("Number of book directories with only book file: '" 
                + statistics.getOnlyBookFileCount() + "'");
        printer.println("Number of book directories with only metadata file: '" 
                + statistics.getOnlyMetadataCount() + "'");
        printer.println("Number of book directories with neither book file nor metadata file: '" 
                + statistics.getNeitherDataCount() + "'");
    }    
}
