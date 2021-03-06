package dk.kb.elivagar.transfer;

import dk.kb.elivagar.Constants;
import dk.kb.elivagar.config.Configuration;
import dk.kb.elivagar.config.TransferConfiguration;
import dk.kb.elivagar.testutils.TestFileUtils;
import dk.kb.elivagar.utils.CalendarUtils;
import dk.kb.elivagar.utils.ChecksumUtils;
import dk.kb.elivagar.utils.FileUtils;
import dk.pubhub.service.BookTypeEnum;
import org.jaccept.structure.ExtendedTestCase;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.mockito.Mockito.*;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class PreIngestTransferTest extends ExtendedTestCase {
    
    @BeforeClass
    public void setup() {
        TestFileUtils.setup();
    }
    
//    @AfterClass
    public void tearDown() {
        TestFileUtils.tearDown();
    }
    
    @Test
    public void testInstantiation() {
        Configuration conf = mock(Configuration.class);
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        Assert.assertNotNull(pit);
    }
    
    @Test
    public void testTransferReadyBooks() throws Exception {
        addDescription("Test the transferReadyBooks method");

        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        
        File ebookBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File audioBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());

        when(conf.getEbookOutputDir()).thenReturn(ebookBaseDir);
        when(conf.getAudioOutputDir()).thenReturn(audioBaseDir);
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        pit.transferReadyBooks();
        
        verify(conf).getEbookOutputDir();
        verify(conf).getAudioOutputDir();
        verifyNoMoreInteractions(conf);
        verifyZeroInteractions(transferConf);
    }

    @Test
    public void testTransferReadyBooksSameBaseDir() throws Exception {
        addDescription("Test the transferReadyBooks method when the audio and ebook would have the same ");

        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        
        File bothBookBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());

        when(conf.getEbookOutputDir()).thenReturn(bothBookBaseDir);
        when(conf.getAudioOutputDir()).thenReturn(bothBookBaseDir);
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        pit.transferReadyBooks();
        
        verify(conf).getEbookOutputDir();
        verify(conf).getAudioOutputDir();
        verifyNoMoreInteractions(conf);
        verifyZeroInteractions(transferConf);
    }
    
    @Test
    public void testTransferBookNotBookDir() throws Exception {
        addDescription("Test the transferBook method when the 'book dir' is not a directory, but a file.");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        
        File bookBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File mockBookDir = new File(bookBaseDir, UUID.randomUUID().toString() + ".pdf");
        TestFileUtils.createFile(mockBookDir, UUID.randomUUID().toString());
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        pit.transferBook(bookBaseDir, BookTypeEnum.EBOG);
        
        verifyNoMoreInteractions(conf);
        verifyNoMoreInteractions(transferConf);
    }
    
    @Test
    public void testTransferBookIngest() throws Exception {
        addDescription("Test the transferBook method when the book has no register, and thus has to be tested for ingestation. "
                + "It will fail on the ingestion test, since it requires more formats than it has.");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        
        File bookBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookDir = FileUtils.createDirectory(bookBaseDir.getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());

        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(transferConf.getRequiredFormats()).thenReturn(Arrays.asList("pdf", "suffix"));
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        pit.transferBook(bookBaseDir, BookTypeEnum.EBOG);
        
        verify(conf).getTransferConfiguration();
        verifyNoMoreInteractions(conf);
        verify(transferConf).getRequiredFormats();
        verifyNoMoreInteractions(transferConf);
    }
    
    @Test
    public void testTransferBookUpdate() throws Exception {
        addDescription("Test the transferBook method when the book has a register with a date in the future. "
                + "It should go into update mode, but find no files to update.");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        
        File bookBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookDir = FileUtils.createDirectory(bookBaseDir.getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        
        addStep("Setting the register to a date in the future (by one minute)", "Should not find anything to update.");
        TransferRegistry register = new TransferRegistry(bookDir);
        register.setIngestDate(new Date(System.currentTimeMillis() + 60000L));
        register.setChecksumAndDate(bookFile);
        
        File registryFileBeforeTransfer = register.registryFile.getCanonicalFile();
        String registryFileChecksumBeforeTransfer = ChecksumUtils.generateMD5Checksum(new FileInputStream(registryFileBeforeTransfer));

        when(conf.getAudioFormats()).thenReturn(new ArrayList<String>());
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("pdf"));
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);

        pit.transferBook(bookBaseDir, BookTypeEnum.EBOG);
        
        verify(conf, times(2)).getAudioFormats();
        verify(conf, times(2)).getEbookFormats();
        verifyNoMoreInteractions(conf);

        verifyZeroInteractions(transferConf);

        File registryFileAfterTransfer = register.registryFile.getCanonicalFile();
        String registryFileChecksumAfterTransfer = ChecksumUtils.generateMD5Checksum(new FileInputStream(registryFileAfterTransfer));

        Assert.assertEquals(registryFileChecksumBeforeTransfer, registryFileChecksumAfterTransfer);
    }
    
    @Test
    public void testTransferBookFailure() throws Exception {
        addDescription("Test the transferBook method when if receives an IOException.");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        
        File bookBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookDir = FileUtils.createDirectory(bookBaseDir.getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        
        File destinationDir = FileUtils.createDirectory(bookBaseDir.getAbsolutePath() + "/" + UUID.randomUUID().toString());
        
        TransferRegistry register = new TransferRegistry(bookDir);
        register.setIngestDate(new Date(System.currentTimeMillis() - 60000L));

        when(conf.getAudioFormats()).thenReturn(new ArrayList<String>());
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("pdf"));
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(transferConf.getUpdateEbookContentDir()).thenReturn(destinationDir);
        when(transferConf.getUpdateAudioContentDir()).thenReturn(destinationDir);
        
        addStep("Set the destination directory to un-writable", "Provokes an IOException");
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        try {
            destinationDir.setWritable(false);
        
            pit.transferBook(bookBaseDir, BookTypeEnum.EBOG);
        } finally {
            destinationDir.setWritable(true);
        }
        
        verify(conf, times(3)).getAudioFormats();
        verify(conf, times(3)).getEbookFormats();
        verify(conf, times(2)).getTransferConfiguration();
        verifyNoMoreInteractions(conf);

//        verify(transferConf).getUpdateEbookContentDir();
        verify(transferConf).getRequiredFormats();
        verify(transferConf).getRetainPublicationDate();
        verifyNoMoreInteractions(transferConf);
    }

    @Test
    public void testUpdateBookAllUpdates() throws Exception {
        addDescription("Test the updateBook method, when all types of files is being updated - though no files already in update folder.");
        Configuration conf = mock(Configuration.class);
        TransferRegistry register = mock(TransferRegistry.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        
        String bookId = UUID.randomUUID().toString();
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + bookId);
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        File fitsFile = new File(bookDir, bookDir.getName() + Constants.FITS_METADATA_SUFFIX);
        TestFileUtils.createFile(fitsFile, UUID.randomUUID().toString());
        File modsFile = new File(bookDir, bookDir.getName() + Constants.MODS_METADATA_SUFFIX);
        TestFileUtils.createFile(modsFile, UUID.randomUUID().toString());
        File updateContentBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File updateMetadataBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File updateContentBookDir = FileUtils.createDirectory(updateContentBaseDir.getAbsolutePath() + "/" + bookId);
        File updateMetadataBookDir = FileUtils.createDirectory(updateMetadataBaseDir.getAbsolutePath() + "/" + bookId);
        
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(conf.getAudioFormats()).thenReturn(Arrays.asList("mp3"));
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("pdf"));
        when(transferConf.getUpdateEbookContentDir()).thenReturn(updateContentBaseDir);
        when(transferConf.getUpdateEbookMetadataDir()).thenReturn(updateMetadataBaseDir);
        when(register.getLatestUpdateDate()).thenReturn(new Date(0L));
        when(register.hasFileEntry(eq(bookFile))).thenReturn(true);
        when(register.verifyFile(eq(bookFile))).thenReturn(false);
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        Assert.assertEquals(updateContentBookDir.list().length, 0);
        Assert.assertEquals(updateMetadataBookDir.list().length, 0);

        Assert.assertFalse(new File(updateContentBookDir, bookFile.getName()).exists());
        Assert.assertFalse(new File(updateContentBookDir, fitsFile.getName()).exists());
        Assert.assertFalse(new File(updateMetadataBookDir, modsFile.getName()).exists());

        pit.updateBook(bookDir, register, BookTypeEnum.EBOG);

        Assert.assertEquals(updateContentBookDir.list().length, 2);
        Assert.assertEquals(updateMetadataBookDir.list().length, 1);
        Assert.assertTrue(new File(updateContentBookDir, bookFile.getName()).exists());
        Assert.assertTrue(new File(updateContentBookDir, fitsFile.getName()).exists());
        Assert.assertTrue(new File(updateMetadataBookDir, modsFile.getName()).exists());

        verify(conf, times(3)).getTransferConfiguration();
        verify(conf).getAudioFormats();
        verify(conf).getEbookFormats();
        verifyNoMoreInteractions(conf);
        verify(transferConf, times(2)).getUpdateEbookContentDir();
        verify(transferConf).getUpdateEbookMetadataDir();
        verifyNoMoreInteractions(transferConf);
        
        verify(register).getLatestUpdateDate();
        verify(register).setUpdateDate(any(Date.class));
        verify(register).hasFileEntry(eq(bookFile));
        verify(register).verifyFile(eq(bookFile));
        verify(register).updateFileEntries(eq(Arrays.asList(bookFile)));
        verifyNoMoreInteractions(register);
    }

    @Test
    public void testUpdateBookAllReUpdates() throws Exception {
        addDescription("Test the updateBook method, when all types of files is being updated - even though all the update folders have files.");
        Configuration conf = mock(Configuration.class);
        TransferRegistry register = mock(TransferRegistry.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);

        long oneMinuteAgo = System.currentTimeMillis()-60000;
        long oneHourAgo = System.currentTimeMillis()-3600000;

        String bookId = UUID.randomUUID().toString();
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + bookId);
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        File fitsFile = new File(bookDir, bookDir.getName() + Constants.FITS_METADATA_SUFFIX);
        TestFileUtils.createFile(fitsFile, UUID.randomUUID().toString());
        File modsFile = new File(bookDir, bookDir.getName() + Constants.MODS_METADATA_SUFFIX);
        TestFileUtils.createFile(modsFile, UUID.randomUUID().toString());
        File updateContentBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File updateMetadataBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File updateContentBookDir = FileUtils.createDirectory(updateContentBaseDir.getAbsolutePath() + "/" + bookId);
        File updateMetadataBookDir = FileUtils.createDirectory(updateMetadataBaseDir.getAbsolutePath() + "/" + bookId);

        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(conf.getAudioFormats()).thenReturn(Arrays.asList("mp3"));
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("pdf"));
        when(transferConf.getUpdateEbookContentDir()).thenReturn(updateContentBaseDir);
        when(transferConf.getUpdateEbookMetadataDir()).thenReturn(updateMetadataBaseDir);
        when(register.getLatestUpdateDate()).thenReturn(new Date(oneHourAgo));
        when(register.hasFileEntry(eq(bookFile))).thenReturn(true);
        when(register.verifyFile(eq(bookFile))).thenReturn(false);

        File updateBookFile = new File(updateContentBookDir, bookFile.getName());
        File updateFitsFile = new File(updateContentBookDir, fitsFile.getName());
        File updateModsFile = new File(updateMetadataBookDir, modsFile.getName());
        TestFileUtils.createFile(updateBookFile, UUID.randomUUID().toString());
        TestFileUtils.createFile(updateFitsFile, UUID.randomUUID().toString());
        TestFileUtils.createFile(updateModsFile, UUID.randomUUID().toString());

        updateBookFile.setLastModified(oneHourAgo);
        updateFitsFile.setLastModified(oneHourAgo);
        updateModsFile.setLastModified(oneHourAgo);

        PreIngestTransfer pit = new PreIngestTransfer(conf);

        Assert.assertEquals(updateContentBookDir.list().length, 2);
        Assert.assertEquals(updateMetadataBookDir.list().length, 1);

        Assert.assertTrue(new File(updateContentBookDir, bookFile.getName()).exists());
        Assert.assertTrue(new File(updateContentBookDir, fitsFile.getName()).exists());
        Assert.assertTrue(new File(updateMetadataBookDir, modsFile.getName()).exists());
        Assert.assertTrue(new File(updateContentBookDir, bookFile.getName()).lastModified() < oneMinuteAgo);
        Assert.assertTrue(new File(updateContentBookDir, fitsFile.getName()).lastModified() < oneMinuteAgo);
        Assert.assertTrue(new File(updateMetadataBookDir, modsFile.getName()).lastModified() < oneMinuteAgo);

        pit.updateBook(bookDir, register, BookTypeEnum.EBOG);

        Assert.assertEquals(updateContentBookDir.list().length, 2);
        Assert.assertEquals(updateMetadataBookDir.list().length, 1);
        Assert.assertTrue(new File(updateContentBookDir, bookFile.getName()).exists());
        Assert.assertTrue(new File(updateContentBookDir, fitsFile.getName()).exists());
        Assert.assertTrue(new File(updateMetadataBookDir, modsFile.getName()).exists());
        Assert.assertTrue(new File(updateContentBookDir, bookFile.getName()).lastModified() >= oneMinuteAgo);
        Assert.assertTrue(new File(updateContentBookDir, fitsFile.getName()).lastModified() >= oneMinuteAgo);
        Assert.assertTrue(new File(updateMetadataBookDir, modsFile.getName()).lastModified() >= oneMinuteAgo);

        verify(conf, times(3)).getTransferConfiguration();
        verify(conf).getAudioFormats();
        verify(conf).getEbookFormats();
        verifyNoMoreInteractions(conf);
        verify(transferConf, times(2)).getUpdateEbookContentDir();
        verify(transferConf).getUpdateEbookMetadataDir();
        verifyNoMoreInteractions(transferConf);

        verify(register).getLatestUpdateDate();
        verify(register).setUpdateDate(any(Date.class));
        verify(register).hasFileEntry(eq(bookFile));
        verify(register).verifyFile(eq(bookFile));
        verify(register).updateFileEntries(eq(Arrays.asList(bookFile)));
        verifyNoMoreInteractions(register);
    }

    @Test
    public void testUpdateBookNoUpdates() throws Exception {
        addDescription("Test the updateBook method, when it does not update any files.");
        Configuration conf = mock(Configuration.class);
        TransferRegistry register = mock(TransferRegistry.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        File updateContentDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File updateMetadataDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(conf.getAudioFormats()).thenReturn(new ArrayList<String>());
        when(conf.getEbookFormats()).thenReturn(new ArrayList<String>());
        when(register.getLatestUpdateDate()).thenReturn(new Date(System.currentTimeMillis() + 60000L));
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        Assert.assertEquals(updateContentDir.list().length, 0);
        Assert.assertEquals(updateMetadataDir.list().length, 0);
        
        pit.updateBook(bookDir, register, BookTypeEnum.EBOG);
        
        Assert.assertEquals(updateContentDir.list().length, 0);
        Assert.assertEquals(updateMetadataDir.list().length, 0);

        verify(conf).getAudioFormats();
        verify(conf).getEbookFormats();
        verifyNoMoreInteractions(conf);
        verifyZeroInteractions(transferConf);
        
        verify(register).getLatestUpdateDate();
        verifyNoMoreInteractions(register);
    }

    @Test
    public void testUpdateBookNoDate() throws Exception {
        addDescription("Test the updateBook method, when it has no date for the update.");
        Configuration conf = mock(Configuration.class);
        TransferRegistry register = mock(TransferRegistry.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(register.getLatestUpdateDate()).thenReturn(null);
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        pit.updateBook(bookDir, register, BookTypeEnum.EBOG);

        verifyZeroInteractions(conf);
        verifyZeroInteractions(transferConf);
        
        verify(register).getLatestUpdateDate();
        verifyNoMoreInteractions(register);
    }
    @Test
    public void testIngestBookFailure() throws Exception {
        addDescription("Test the ingestBook method when does not pass the criteria for being ingested.");
        Configuration conf = mock(Configuration.class);
        TransferRegistry register = mock(TransferRegistry.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        File destinationDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(transferConf.getRequiredFormats()).thenReturn(Arrays.asList("pdf", "fits.xml"));
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        Assert.assertEquals(destinationDir.list().length, 0);
        pit.ingestBook(bookDir, register, BookTypeEnum.EBOG);
        Assert.assertEquals(destinationDir.list().length, 0);

        verify(conf).getTransferConfiguration();
        verifyNoMoreInteractions(conf);
        verify(transferConf).getRequiredFormats();
        verifyNoMoreInteractions(transferConf);
        
        verifyZeroInteractions(register);
    }
    
    @Test
    public void testIngestBookSuccess() throws Exception {
        addDescription("Test the ingestBook method when it successfully is ingested");
        Configuration conf = mock(Configuration.class);
        TransferRegistry register = mock(TransferRegistry.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        File destinationDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("pdf"));
        when(conf.getAudioFormats()).thenReturn(Arrays.asList("mp3"));
        when(transferConf.getRequiredFormats()).thenReturn(Arrays.asList("pdf"));
        when(transferConf.getRetainCreateDate()).thenReturn(-1L);
        when(transferConf.getRetainModifyDate()).thenReturn(-1L);
        when(transferConf.getRetainPublicationDate()).thenReturn(-1L);
        when(transferConf.getEbookIngestDir()).thenReturn(destinationDir);
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        Assert.assertEquals(destinationDir.list().length, 0);
        pit.ingestBook(bookDir, register, BookTypeEnum.EBOG);
        Assert.assertEquals(destinationDir.list().length, 1);
        Assert.assertEquals(destinationDir.listFiles()[0].getName(), bookDir.getName());
        Assert.assertEquals(destinationDir.listFiles()[0].list().length, bookDir.list().length);

        verify(conf, times(5)).getTransferConfiguration();
        verify(conf, times(2)).getEbookFormats();
        verify(conf, times(2)).getAudioFormats();
        verifyNoMoreInteractions(conf);
        verify(transferConf).getRequiredFormats();
        verify(transferConf).getRetainCreateDate();
        verify(transferConf).getRetainModifyDate();
        verify(transferConf).getRetainPublicationDate();
        verify(transferConf).getEbookIngestDir();
        verifyNoMoreInteractions(transferConf);
        
        verify(register).setIngestDate(any(Date.class));
        verify(register).setChecksumAndDate(any(File.class));
        verifyNoMoreInteractions(register);
    }

    @Test
    public void testIngestBookWithSeveralFilesSuccess() throws Exception {
        addDescription("Test the ingestBook method when it successfully is ingested a book with two different e-book files (both epub and pdf)");
        Configuration conf = mock(Configuration.class);
        TransferRegistry register = mock(TransferRegistry.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);

        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile1 = new File(bookDir, bookDir.getName() + ".pdf");
        File bookFile2 = new File(bookDir, bookDir.getName() + ".epub");
        TestFileUtils.createFile(bookFile1, UUID.randomUUID().toString());
        TestFileUtils.createFile(bookFile2, UUID.randomUUID().toString());
        File destinationDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());

        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("pdf", "epub"));
        when(conf.getAudioFormats()).thenReturn(Arrays.asList("mp3"));
        when(transferConf.getRequiredFormats()).thenReturn(Arrays.asList("pdf", "epub"));
        when(transferConf.getRetainCreateDate()).thenReturn(-1L);
        when(transferConf.getRetainModifyDate()).thenReturn(-1L);
        when(transferConf.getRetainPublicationDate()).thenReturn(-1L);
        when(transferConf.getEbookIngestDir()).thenReturn(destinationDir);

        PreIngestTransfer pit = new PreIngestTransfer(conf);

        Assert.assertEquals(destinationDir.list().length, 0);
        pit.ingestBook(bookDir, register, BookTypeEnum.EBOG);
        Assert.assertEquals(destinationDir.list().length, 1);
        Assert.assertEquals(destinationDir.listFiles()[0].getName(), bookDir.getName());
        Assert.assertEquals(destinationDir.listFiles()[0].list().length, bookDir.list().length);

        verify(conf, times(7)).getTransferConfiguration();
        verify(conf, times(2)).getEbookFormats();
        verify(conf, times(2)).getAudioFormats();
        verifyNoMoreInteractions(conf);
        verify(transferConf).getRequiredFormats();
        verify(transferConf, times(2)).getRetainCreateDate();
        verify(transferConf, times(2)).getRetainModifyDate();
        verify(transferConf).getRetainPublicationDate();
        verify(transferConf).getEbookIngestDir();
        verifyNoMoreInteractions(transferConf);

        verify(register).setIngestDate(any(Date.class));
        verify(register).setChecksumAndDate(eq(bookFile1));
        verify(register).setChecksumAndDate(eq(bookFile2));
        verifyNoMoreInteractions(register);
    }
    
    @Test
    public void testReadyForIngestSuccess() throws Exception {
        addDescription("Test the readyForIngest method when all the requirements have been met");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("pdf"));
        when(conf.getAudioFormats()).thenReturn(Arrays.asList("mp3"));
        when(transferConf.getRequiredFormats()).thenReturn(Arrays.asList("pdf"));
        when(transferConf.getRetainCreateDate()).thenReturn(0L);
        when(transferConf.getRetainModifyDate()).thenReturn(0L);
        when(transferConf.getRetainPublicationDate()).thenReturn(System.currentTimeMillis());
        
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        TestFileUtils.copyFile(new File("src/test/resources/metadata/pubhub_metadata.xml"), new File(bookDir, bookDir.getName() + Constants.PUBHUB_METADATA_SUFFIX));
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        Assert.assertTrue(pit.readyForIngest(bookDir));
        
        verify(conf, times(7)).getTransferConfiguration();
        verify(conf).getEbookFormats();
        verify(conf).getAudioFormats();
        verifyNoMoreInteractions(conf);
        verify(transferConf).getRequiredFormats();
        verify(transferConf, times(2)).getRetainCreateDate();
        verify(transferConf, times(2)).getRetainModifyDate();
        verify(transferConf, times(2)).getRetainPublicationDate();
        verifyNoMoreInteractions(transferConf);
    }

    @Test
    public void testReadyForIngestSuccessWithNoChecks() throws Exception {
        addDescription("Test the readyForIngest method when all the requirements have been met");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(conf.getEbookFormats()).thenReturn(new ArrayList<String>());
        when(conf.getAudioFormats()).thenReturn(new ArrayList<String>());
        when(transferConf.getRequiredFormats()).thenReturn(new ArrayList<String>());
        when(transferConf.getRetainCreateDate()).thenReturn(-1L);
        when(transferConf.getRetainModifyDate()).thenReturn(-1L);
        when(transferConf.getRetainPublicationDate()).thenReturn(-1L);
        
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        TestFileUtils.copyFile(new File("src/test/resources/metadata/pubhub_metadata.xml"), new File(bookDir, bookDir.getName() + Constants.PUBHUB_METADATA_SUFFIX));
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        Assert.assertTrue(pit.readyForIngest(bookDir));

        verify(conf, times(2)).getTransferConfiguration();
        verify(conf).getEbookFormats();
        verify(conf).getAudioFormats();
        verifyNoMoreInteractions(conf);
        verify(transferConf).getRequiredFormats();
        verify(transferConf).getRetainPublicationDate();
        verifyNoMoreInteractions(transferConf);
    }

    @Test
    public void testReadyForIngestFailureNotRequiredFormat() throws Exception {
        addDescription("Test the readyForIngest method when all the requirements have been met");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(transferConf.getRequiredFormats()).thenReturn(Arrays.asList("pdf", "fits.xml"));
        
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        TestFileUtils.copyFile(new File("src/test/resources/metadata/pubhub_metadata.xml"), new File(bookDir, bookDir.getName() + Constants.PUBHUB_METADATA_SUFFIX));
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        Assert.assertFalse(pit.readyForIngest(bookDir));
        

        verify(conf, times(1)).getTransferConfiguration();
        verifyNoMoreInteractions(conf);
        verify(transferConf).getRequiredFormats();
        verifyNoMoreInteractions(transferConf);
    }

    @Test
    public void testReadyForIngestFailureContentFileDate() throws Exception {
        addDescription("Test the readyForIngest method when all the requirements have been met");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("pdf"));
        when(conf.getAudioFormats()).thenReturn(new ArrayList<String>());
        when(transferConf.getRequiredFormats()).thenReturn(Arrays.asList("pdf"));
        when(transferConf.getRetainCreateDate()).thenReturn(1000000000L);
        when(transferConf.getRetainModifyDate()).thenReturn(-1L);
        when(transferConf.getRetainPublicationDate()).thenReturn(-1L);
        
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        TestFileUtils.copyFile(new File("src/test/resources/metadata/pubhub_metadata.xml"), new File(bookDir, bookDir.getName() + Constants.PUBHUB_METADATA_SUFFIX));
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        Assert.assertFalse(pit.readyForIngest(bookDir));
        

        verify(conf, times(3)).getTransferConfiguration();
        verify(conf).getEbookFormats();
        verify(conf).getAudioFormats();
        verifyNoMoreInteractions(conf);
        verify(transferConf).getRequiredFormats();
        verify(transferConf, times(2)).getRetainCreateDate();
        verify(transferConf, times(0)).getRetainModifyDate();
        verify(transferConf, times(0)).getRetainPublicationDate();
        verifyNoMoreInteractions(transferConf);
    }

    @Test
    public void testReadyForIngestFailureNoPubhubFile() throws Exception {
        addDescription("Test the readyForIngest method when all the requirements have been met");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("pdf"));
        when(conf.getAudioFormats()).thenReturn(new ArrayList<String>());
        when(transferConf.getRequiredFormats()).thenReturn(Arrays.asList("pdf"));
        when(transferConf.getRetainCreateDate()).thenReturn(-1L);
        when(transferConf.getRetainModifyDate()).thenReturn(-1L);
        when(transferConf.getRetainPublicationDate()).thenReturn(0L);
        
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        Assert.assertFalse(pit.readyForIngest(bookDir));
        

        verify(conf, times(4)).getTransferConfiguration();
        verify(conf).getEbookFormats();
        verify(conf).getAudioFormats();
        verifyNoMoreInteractions(conf);
        verify(transferConf).getRequiredFormats();
        verify(transferConf, times(1)).getRetainCreateDate();
        verify(transferConf, times(1)).getRetainModifyDate();
        verify(transferConf, times(1)).getRetainPublicationDate();
        verifyNoMoreInteractions(transferConf);
    }

    @Test
    public void testReadyForIngestFailureTooNewPublicationDate() throws Exception {
        addDescription("Test the readyForIngest method when all the requirements have been met");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("pdf"));
        when(conf.getAudioFormats()).thenReturn(new ArrayList<String>());
        when(transferConf.getRequiredFormats()).thenReturn(Arrays.asList("pdf"));
        when(transferConf.getRetainCreateDate()).thenReturn(-1L);
        when(transferConf.getRetainModifyDate()).thenReturn(-1L);
        when(transferConf.getRetainPublicationDate()).thenReturn(10000000L);
        
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        TestFileUtils.copyFile(new File("src/test/resources/metadata/pubhub_metadata.xml"), new File(bookDir, bookDir.getName() + Constants.PUBHUB_METADATA_SUFFIX));
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        Assert.assertFalse(pit.readyForIngest(bookDir));

        verify(conf, times(5)).getTransferConfiguration();
        verify(conf).getEbookFormats();
        verify(conf).getAudioFormats();
        verifyNoMoreInteractions(conf);
        verify(transferConf).getRequiredFormats();
        verify(transferConf, times(1)).getRetainCreateDate();
        verify(transferConf, times(1)).getRetainModifyDate();
        verify(transferConf, times(2)).getRetainPublicationDate();
        verifyNoMoreInteractions(transferConf);
    }
    
    @Test
    public void testHasRequiredFile() throws Exception {
        addDescription("Test the hasRequiredFile method");
        Configuration conf = mock(Configuration.class);
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        String suffix = ".suffix";
        String badSuffix = ".notSuffix";
        
        File bookDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File testFile = new File(bookDir, UUID.randomUUID().toString() + suffix);
        TestFileUtils.createFile(testFile, UUID.randomUUID().toString());
        
        Assert.assertTrue(pit.hasRequiredFile(bookDir, suffix));
        Assert.assertFalse(pit.hasRequiredFile(bookDir, badSuffix));
    }
    
    @Test
    public void testGetUpdateMetadataDir() throws Exception {
        addDescription("Test the getUpdateMetadataDir method");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        
        File contentDir = new File(TestFileUtils.getTempDir().getAbsolutePath(), UUID.randomUUID().toString());
        File bookDir = new File(TestFileUtils.getTempDir().getAbsolutePath(), UUID.randomUUID().toString());
        
        when(transferConf.getUpdateEbookMetadataDir()).thenReturn(contentDir);
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        File updateDir = new File(pit.getUpdateMetadataDir(bookDir, BookTypeEnum.EBOG));
        Assert.assertEquals(updateDir.getParentFile(), contentDir);
        Assert.assertEquals(updateDir.getName(), bookDir.getName());
        
        verify(conf).getTransferConfiguration();
        verifyNoMoreInteractions(conf);
        
        verify(transferConf).getUpdateEbookMetadataDir();
        verifyNoMoreInteractions(transferConf);
    }
    
    @Test
    public void testGetUpdateContentDir() throws Exception {
        addDescription("Test the getUpdateContentDir method");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        
        File contentDir = new File(TestFileUtils.getTempDir().getAbsolutePath(), UUID.randomUUID().toString());
        File bookDir = new File(TestFileUtils.getTempDir().getAbsolutePath(), UUID.randomUUID().toString());
        
        when(transferConf.getUpdateEbookContentDir()).thenReturn(contentDir);
        
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        File updateDir = new File(pit.getUpdateContentDir(bookDir, BookTypeEnum.EBOG));
        Assert.assertEquals(updateDir.getParentFile(), contentDir);
        Assert.assertEquals(updateDir.getName(), bookDir.getName());
        
        verify(conf).getTransferConfiguration();
        verifyNoMoreInteractions(conf);
        
        verify(transferConf).getUpdateEbookContentDir();
        verifyNoMoreInteractions(transferConf);
    }
    
    @Test
    public void testCopyToUpdateDir() throws Exception {
        addDescription("Test the copyToUpdateDir method.");
        Configuration conf = mock(Configuration.class);

        PreIngestTransfer pit = new PreIngestTransfer(conf);

        File origDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        File destinationDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        
        File origFile = new File(origDir, UUID.randomUUID().toString());
        TestFileUtils.createFile(origFile, UUID.randomUUID().toString());
        
        Assert.assertEquals(destinationDir.list().length, 0);
        Assert.assertEquals(origDir.list().length, 1);
        pit.copyUpdatedFiles(Arrays.asList(origFile), destinationDir.getAbsolutePath());
        Assert.assertEquals(destinationDir.list().length, 1);
        Assert.assertEquals(origDir.list().length, 1);
    }
    
    @Test
    public void testHasContentFileDateSuccess() throws Exception {
        addDescription("Test the hasContentFileDate method for the success case, when both dates are checked.");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(transferConf.getRetainCreateDate()).thenReturn(0L);
        when(transferConf.getRetainModifyDate()).thenReturn(0L);

        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        File f = TestFileUtils.createTempFile(UUID.randomUUID().toString());
        
        Assert.assertTrue(pit.hasContentFileDate(f.toPath()));
        
        verify(conf, times(4)).getTransferConfiguration();
        verifyNoMoreInteractions(conf);
        
        verify(transferConf, times(2)).getRetainCreateDate();
        verify(transferConf, times(2)).getRetainModifyDate();
        verifyNoMoreInteractions(transferConf);
    }

    @Test
    public void testHasContentFileDateSuccessWhenNoDateChecks() throws Exception {
        addDescription("Test the hasContentFileDate method for the success case, when neither dates are checked.");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(transferConf.getRetainCreateDate()).thenReturn(-1L);
        when(transferConf.getRetainModifyDate()).thenReturn(-1L);

        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        File f = TestFileUtils.createTempFile(UUID.randomUUID().toString());
        
        Assert.assertTrue(pit.hasContentFileDate(f.toPath()));

        verify(conf, times(2)).getTransferConfiguration();
        verifyNoMoreInteractions(conf);
        
        verify(transferConf, times(1)).getRetainCreateDate();
        verify(transferConf, times(1)).getRetainModifyDate();
        verifyNoMoreInteractions(transferConf);
    }

    @Test
    public void testHasContentFileDateFailureTooNewlyCreated() throws Exception {
        addDescription("Test the hasContentFileDate method for the failure case, when the file has been created later than required.");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(transferConf.getRetainCreateDate()).thenReturn(1000000L);
        when(transferConf.getRetainModifyDate()).thenReturn(-1L);

        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        File f = TestFileUtils.createTempFile(UUID.randomUUID().toString());
        
        Assert.assertFalse(pit.hasContentFileDate(f.toPath()));

        verify(conf, times(2)).getTransferConfiguration();
        verifyNoMoreInteractions(conf);
        
        verify(transferConf, times(2)).getRetainCreateDate();
        verify(transferConf, times(0)).getRetainModifyDate();
        verifyNoMoreInteractions(transferConf);
    }

    @Test
    public void testHasContentFileDateFailureTooNewlyModified() throws Exception {
        addDescription("Test the hasContentFileDate method for the failure case, when the file has been modified later than required.");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);
        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(transferConf.getRetainCreateDate()).thenReturn(-1L);
        when(transferConf.getRetainModifyDate()).thenReturn(1000000L);

        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        File f = TestFileUtils.createTempFile(UUID.randomUUID().toString());
        
        Assert.assertFalse(pit.hasContentFileDate(f.toPath()));

        verify(conf, times(3)).getTransferConfiguration();
        verifyNoMoreInteractions(conf);
        
        verify(transferConf, times(1)).getRetainCreateDate();
        verify(transferConf, times(2)).getRetainModifyDate();
        verifyNoMoreInteractions(transferConf);
    }
    
    @Test
    public void testFindPublicationDate() throws Exception {
        addDescription("Test the findPublicationDate method for a file with the publication date.");
        Configuration conf = mock(Configuration.class);
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        File metadataFile = TestFileUtils.copyFileToTemp(new File("src/test/resources/metadata/pubhub_metadata.xml"));
        Date d = pit.findPublicationDate(metadataFile);
        
        Date expectedDate = CalendarUtils.getDateFromString("31-07-2017", PreIngestTransfer.DATE_FORMAT_PUBLICATION_DATE);
        Assert.assertEquals(d, expectedDate);
    }
    
    @Test
    public void testFindPublicationDateFailureNoDate() throws Exception {
        addDescription("Test the findPublicationDate method for a file without the publication date.");
        Configuration conf = mock(Configuration.class);
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        File metadataFile = TestFileUtils.copyFileToTemp(new File("src/test/resources/metadata/pubhub_metadata_gtin.xml"));
        Assert.assertNull(pit.findPublicationDate(metadataFile));
    }
    
    @Test(expectedExceptions = IllegalStateException.class)
    public void testFindPublicationDateFailureBadFileFormat() throws Exception {
        addDescription("Test the findPublicationDate method from a file in a non-xml format.");
        Configuration conf = mock(Configuration.class);
        PreIngestTransfer pit = new PreIngestTransfer(conf);
        
        File metadataFile = TestFileUtils.createTempFile(UUID.randomUUID().toString());
        pit.findPublicationDate(metadataFile);
    }
    
    @Test
    public void testGetContentFiles() throws Exception {
        addDescription("Test the getNewContentFiles method, where one file is newer and another file is older than the time-frame.");
        Configuration conf = mock(Configuration.class);
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("tif", "pdf"));
        when(conf.getAudioFormats()).thenReturn(Arrays.asList("mp3", "wav"));
        PreIngestTransfer pit = new PreIngestTransfer(conf);

        File dir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        
        File tifFile = new File(dir, dir.getName() + ".tif");
        TestFileUtils.createFile(tifFile, UUID.randomUUID().toString());
        File mp3File = new File(dir, dir.getName() + ".mp3");
        TestFileUtils.createFile(mp3File, UUID.randomUUID().toString());
        File unknownFile = new File(dir, dir.getName() + ".suffix");
        TestFileUtils.createFile(unknownFile, UUID.randomUUID().toString());
        
        List<Path> files = pit.getContentFiles(dir);
        Assert.assertFalse(files.isEmpty());
        Assert.assertEquals(files.size(), 2);
        Assert.assertTrue(files.contains(tifFile.toPath()));
        Assert.assertTrue(files.contains(mp3File.toPath()));
        Assert.assertFalse(files.contains(unknownFile.toPath()));
    }
    
    @Test
    public void testGetNewFilesWithSuffixSuccess() throws Exception {
        addDescription("Test the getNewFilesWithSuffix method with one new file with correct suffix.");
        Configuration conf = mock(Configuration.class);
        PreIngestTransfer pit = new PreIngestTransfer(conf);

        File dir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        
        File testFile = new File(dir, dir.getName() + ".pdf");
        TestFileUtils.createFile(testFile, UUID.randomUUID().toString());
        testFile.setLastModified(200000000000L);
        
        List<File> files = pit.getNewFilesWithSuffix(dir, Arrays.asList("pdf"), new Date(100000000000L));
        Assert.assertFalse(files.isEmpty());
        Assert.assertTrue(files.contains(testFile));
    }
    
    @Test
    public void testGetNewFilesWithSuffixTooNew() throws Exception {
        addDescription("Test the getNewFilesWithSuffix method with a file which is too old");
        Configuration conf = mock(Configuration.class);
        PreIngestTransfer pit = new PreIngestTransfer(conf);

        File dir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        
        File testFile = new File(dir, dir.getName() + ".pdf");
        TestFileUtils.createFile(testFile, UUID.randomUUID().toString());
        testFile.setLastModified(100000000000L);
        
        List<File> files = pit.getNewFilesWithSuffix(dir, Arrays.asList("pdf"), new Date(200000000000L));
        Assert.assertTrue(files.isEmpty());
    }
    
    @Test
    public void testGetNewFilesWithSuffixWrongSuffix() throws Exception {
        addDescription("Test the getNewFilesWithSuffix method with a file with the wrong suffix");
        Configuration conf = mock(Configuration.class);
        PreIngestTransfer pit = new PreIngestTransfer(conf);

        File dir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        
        File testFile = new File(dir, dir.getName() + ".tif");
        TestFileUtils.createFile(testFile, UUID.randomUUID().toString());
        
        List<File> files = pit.getNewFilesWithSuffix(dir, Arrays.asList("pdf"), new Date(0));
        Assert.assertTrue(files.isEmpty());
    }
    
    @Test
    public void testGetNewContentFiles() throws Exception {
        addDescription("Test the getNewContentFiles method, where one file is newer and another file is older than the time-frame.");
        Configuration conf = mock(Configuration.class);
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("tif"));
        when(conf.getAudioFormats()).thenReturn(Arrays.asList("mp3"));
        PreIngestTransfer pit = new PreIngestTransfer(conf);

        File dir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
        
        File newFile = new File(dir, dir.getName() + ".tif");
        TestFileUtils.createFile(newFile, UUID.randomUUID().toString());
        File oldFile = new File(dir, dir.getName() + ".mp3");
        TestFileUtils.createFile(oldFile, UUID.randomUUID().toString());

        TransferRegistry registry = mock(TransferRegistry.class);
        when(registry.hasFileEntry(any(File.class))).thenReturn(true);
        when(registry.verifyFile(eq(newFile))).thenReturn(false);
        when(registry.verifyFile(eq(oldFile))).thenReturn(true);


        Date d = new Date(1234567890);
        Assert.assertTrue(newFile.setLastModified(9999999999L));
        Assert.assertTrue(oldFile.setLastModified(0L));

        List<File> newFiles = pit.getNewContentFiles(dir, registry);
        
        Assert.assertFalse(newFiles.isEmpty());
        Assert.assertEquals(newFiles.size(), 1);
        Assert.assertTrue(newFiles.contains(newFile));
        
        verify(conf).getEbookFormats();
        verify(conf).getAudioFormats();
        verifyNoMoreInteractions(conf);
    }

    @Test
    public void testOldRegistryFile() throws IOException {
        addDescription("Test when the registry file has the old template with only the ingest date and not the checksum and last-modified date");
        Configuration conf = mock(Configuration.class);
        TransferConfiguration transferConf = mock(TransferConfiguration.class);

        String bookId = UUID.randomUUID().toString();
        File booksBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + "baseBookDir");
        File bookDir = FileUtils.createDirectory(booksBaseDir.getAbsolutePath() + "/" + bookId);
        File bookFile = new File(bookDir, bookDir.getName() + ".pdf");
        TestFileUtils.createFile(bookFile, UUID.randomUUID().toString());
        File fitsFile = new File(bookDir, bookDir.getName() + Constants.FITS_METADATA_SUFFIX);
        TestFileUtils.createFile(fitsFile, UUID.randomUUID().toString());
        File modsFile = new File(bookDir, bookDir.getName() + Constants.MODS_METADATA_SUFFIX);
        TestFileUtils.createFile(modsFile, UUID.randomUUID().toString());
        File ingestBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + "ingest");
        File ingestBookDir = FileUtils.createDirectory(ingestBaseDir.getAbsolutePath() + "/" + bookId);
        File updateContentBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + "updateContent");
        File updateMetadataBaseDir = FileUtils.createDirectory(TestFileUtils.getTempDir().getAbsolutePath() + "/" + "updateMetadata");
        File updateContentBookDir = FileUtils.createDirectory(updateContentBaseDir.getAbsolutePath() + "/" + bookId);
        File updateMetadataBookDir = FileUtils.createDirectory(updateMetadataBaseDir.getAbsolutePath() + "/" + bookId);

        when(conf.getTransferConfiguration()).thenReturn(transferConf);
        when(conf.getAudioFormats()).thenReturn(Arrays.asList("mp3"));
        when(conf.getEbookFormats()).thenReturn(Arrays.asList("pdf"));
        when(transferConf.getUpdateEbookContentDir()).thenReturn(updateContentBaseDir);
        when(transferConf.getUpdateEbookMetadataDir()).thenReturn(updateMetadataBaseDir);
        when(transferConf.getEbookIngestDir()).thenReturn(ingestBaseDir);

        PreIngestTransfer pit = new PreIngestTransfer(conf);

        addStep("Set only the ingest date in the registry to 1 hour ago - before the metadata files",
                "No record of the file. ");
        TransferRegistry registry = new TransferRegistry(bookDir);
        registry.setIngestDate(new Date(System.currentTimeMillis() - 3600000L));
        Assert.assertFalse(registry.hasFileEntry(bookFile));

        addStep("Check transfer folders", "Should all be empty.");
        Assert.assertEquals(updateContentBookDir.list().length, 0);
        Assert.assertEquals(updateMetadataBookDir.list().length, 0);
        Assert.assertEquals(ingestBookDir.list().length, 0);

        pit.transferBook(booksBaseDir, BookTypeEnum.EBOG);

        addStep("Check transfer folders",
                "Should have transferred fits file to update-content dir, and mods file to update-metadata dir.");
        Assert.assertEquals(ingestBookDir.list().length, 0);
        Assert.assertEquals(updateMetadataBookDir.list().length, 1);
        Assert.assertEquals(updateMetadataBookDir.listFiles()[0].getName(), modsFile.getName());
        Assert.assertEquals(updateContentBookDir.list().length, 1);
        Assert.assertEquals(updateContentBookDir.listFiles()[0].getName(), fitsFile.getName());

        addStep("Check Registry",
                "Should have entry for file and newer date for update than ingest.");
        registry = new TransferRegistry(bookDir);
        Assert.assertTrue(registry.hasFileEntry(bookFile));
        Date ingestDate = registry.getIngestDate();
        Date updateDate = registry.getLatestUpdateDate();
        Assert.assertTrue(ingestDate.getTime() < updateDate.getTime());


        verify(conf, times(2)).getTransferConfiguration();
        verify(conf, times(2)).getAudioFormats();
        verify(conf, times(2)).getEbookFormats();
        verifyNoMoreInteractions(conf);

        verify(transferConf).getUpdateEbookContentDir();
        verify(transferConf).getUpdateEbookMetadataDir();
        verifyNoMoreInteractions(transferConf);
    }
}

