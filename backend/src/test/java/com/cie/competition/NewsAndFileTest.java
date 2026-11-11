package com.cie.competition;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.*;
import com.cie.competition.module.file.mapper.StoredFileMapper;
import com.cie.competition.module.file.service.FileService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewsAndFileTest {
 @TempDir Path folder;
 @AfterEach void clear(){UserContext.clear();}
 @Test void teacherUploadsAwardImagesButCannotUploadNewsCover() throws Exception {
  UserContext.set(new LoginUser(2L,"teacher","TEACHER"));
  var files=new FileService(mock(StoredFileMapper.class),folder.toString());
  var image=new MockMultipartFile("file","proof.png","image/png",new byte[]{(byte)137,80,78,71,13,10,26,10});
  assertEquals("AWARD_PROCESS_PHOTO",files.uploadAwardMaterial(image,"AWARD_PROCESS_PHOTO").getBizType());
  assertThrows(BusinessException.class,()->files.uploadContentCover(image));
  assertThrows(BusinessException.class,()->files.uploadAwardMaterial(image,"CONTENT_COVER"));
 }
 @Test void spoofedImageAndDocumentScreenshotAreRejected(){
  UserContext.set(new LoginUser(2L,"teacher","TEACHER"));
  var files=new FileService(mock(StoredFileMapper.class),folder.toString());
  assertThrows(BusinessException.class,()->files.uploadAwardMaterial(new MockMultipartFile("file","proof.png","image/png","not an image".getBytes()),"AWARD_PROCESS_PHOTO"));
  assertThrows(BusinessException.class,()->files.uploadAwardMaterial(new MockMultipartFile("file","proof.pdf","application/pdf","%PDF-1.4".getBytes()),"AWARD_REGISTRATION_SCREENSHOT"));
 }
}
