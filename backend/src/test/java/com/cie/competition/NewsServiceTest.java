package com.cie.competition;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.*;
import com.cie.competition.module.content.dto.ContentDTO;
import com.cie.competition.module.content.entity.Content;
import com.cie.competition.module.content.mapper.ContentMapper;
import com.cie.competition.module.content.service.impl.ContentServiceImpl;
import com.cie.competition.module.file.service.FileService;
import com.cie.competition.module.user.service.UserService;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NewsServiceTest {
 ContentMapper mapper; ContentServiceImpl service;
 @BeforeEach void setup(){mapper=mock(ContentMapper.class);service=new ContentServiceImpl(mapper,mock(UserService.class),mock(FileService.class));UserContext.set(new LoginUser(1L,"admin","ADMIN"));}
 @AfterEach void clear(){UserContext.clear();}
 ContentDTO draft(){var dto=new ContentDTO();dto.setTitle("News");dto.setCategory("NEWS");return dto;}
 @Test void draftsMayLackUrlButNewsCannotPublishWithoutOfficialUrl(){
  var dto=draft();assertDoesNotThrow(()->service.create(dto));
  var news=new Content();news.setId(1L);news.setCategory("NEWS");news.setStatus("DRAFT");when(mapper.selectOne(any())).thenReturn(news);
  assertThrows(BusinessException.class,()->service.updateStatus(1L,"PUBLISHED"));verify(mapper,never()).updateById(news);
 }
 @Test void unsafeAndMalformedOfficialLinksRejected(){for(String url:java.util.List.of("javascript:alert(1)","file:///tmp/a","https:///no-host","https://user:password@example.com")){var dto=draft();dto.setOfficialUrl(url);assertThrows(BusinessException.class,()->service.create(dto));}}
 @Test void validHttpsLinkMayPublish(){var news=new Content();news.setId(1L);news.setCategory("NEWS");news.setOfficialUrl("https://example.org/competition");when(mapper.selectOne(any())).thenReturn(news);assertDoesNotThrow(()->service.updateStatus(1L,"PUBLISHED"));assertEquals("PUBLISHED",news.getStatus());}
 @Test void carouselRequiresCoverAndOfficialLink(){var dto=draft();dto.setCarouselEnabled(true);assertThrows(BusinessException.class,()->service.create(dto));dto.setOfficialUrl("https://example.org");assertThrows(BusinessException.class,()->service.create(dto));}
 @Test void studentCannotMaintainNewsAtServiceBoundary(){UserContext.set(new LoginUser(2L,"student","STUDENT"));assertThrows(BusinessException.class,()->service.create(draft()));assertThrows(BusinessException.class,()->service.delete(1L));}
}
