package com.cie.competition;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.*;
import com.cie.competition.module.file.entity.StoredFile;
import com.cie.competition.module.file.mapper.StoredFileMapper;
import com.cie.competition.module.file.service.FileService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AwardFileTest {
    @TempDir Path directory;
    StoredFileMapper mapper; FileService service;
    @BeforeEach void setup(){mapper=mock(StoredFileMapper.class);service=new FileService(mapper,directory.toString());UserContext.set(new LoginUser(17L,"test","MAJOR_ADMIN"));}
    @AfterEach void clear(){UserContext.clear();}
    StoredFile file(String type,long uploader){StoredFile f=new StoredFile();f.setBizType(type);f.setUploaderId(uploader);return f;}
    @Test void competitionFileCannotServeAsCertificate(){when(mapper.selectById(1L)).thenReturn(file("COMPETITION_DOCUMENT",17));assertThrows(BusinessException.class,()->service.validateAwardCertificate(1L));}
    @Test void certificateCannotServeAsCompetitionFile(){when(mapper.selectById(1L)).thenReturn(file("AWARD_CERTIFICATE",17));assertThrows(BusinessException.class,()->service.get(1L));}
    @Test void majorCannotAttachOthersCertificate(){when(mapper.selectById(1L)).thenReturn(file("AWARD_CERTIFICATE",99));assertThrows(BusinessException.class,()->service.validateAwardCertificate(1L));}
    @Test void adminCanAttachOthersCertificate(){UserContext.set(new LoginUser(17L,"test","ADMIN"));when(mapper.selectById(1L)).thenReturn(file("AWARD_CERTIFICATE",99));assertDoesNotThrow(()->service.validateAwardCertificate(1L));}
    @Test void certificateUploadHasDistinctType() throws Exception {var f=service.uploadAwardCertificate(new MockMultipartFile("file","proof.pdf","application/pdf","%PDF-test".getBytes()));assertEquals("AWARD_CERTIFICATE",f.getBizType());assertEquals(17L,f.getUploaderId());}
    @Test void originalUploadTypeUnchanged() throws Exception {var f=service.upload(new MockMultipartFile("file","rules.pdf","application/pdf","%PDF-test".getBytes()));assertEquals("COMPETITION_DOCUMENT",f.getBizType());}
    @Test void studentCannotUploadCertificate(){UserContext.set(new LoginUser(17L,"test","STUDENT"));assertThrows(BusinessException.class,()->service.uploadAwardCertificate(new MockMultipartFile("file","proof.pdf","application/pdf","%PDF-test".getBytes())));}
    @Test void falseExtensionRejected(){assertThrows(BusinessException.class,()->service.uploadAwardCertificate(new MockMultipartFile("file","proof.pdf","application/pdf","invalid".getBytes())));}
}
