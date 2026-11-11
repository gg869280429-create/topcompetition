package com.cie.competition;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.*;
import com.cie.competition.module.major.service.MajorService;
import com.cie.competition.module.user.service.StudentService;
import com.cie.competition.module.user.dto.StudentDTO;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class FoundationManagementTest {
 @Mock JdbcTemplate jdbc; @Mock UserMapper users; @Mock PasswordEncoder encoder;
 @AfterEach void clear(){UserContext.clear();}
 void as(String role){UserContext.set(new LoginUser(9L,"test",role));}
 @Test void onlySystemAdminMayReadManageMajors(){for(String role:List.of("TEACHER","STUDENT","MAJOR_ADMIN")){as(role);assertThrows(BusinessException.class,()->new MajorService(jdbc).list(null));}verifyNoInteractions(jdbc);}
 @Test void renamePreservesIdAndOnlyUpdatesName(){as("ADMIN");when(jdbc.queryForList(contains("FOR UPDATE"),eq(1L))).thenReturn(List.of(Map.of("id",1L)));assertEquals(1L,new MajorService(jdbc).save(1L," Renamed "));verify(jdbc).update("UPDATE sys_major SET name=? WHERE id=?","Renamed",1L);}
 @Test void duplicateMajorIsBusinessError(){as("ADMIN");when(jdbc.queryForList(contains("FOR UPDATE"),eq(1L))).thenReturn(List.of(Map.of("id",1L)));when(jdbc.update(anyString(),eq("Existing"),eq(1L))).thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));assertThrows(BusinessException.class,()->new MajorService(jdbc).save(1L,"Existing"));}
 @Test void linkedMajorCannotBeDeleted(){as("ADMIN");when(jdbc.queryForList(contains("FOR UPDATE"),eq(1L))).thenReturn(List.of(Map.of("id",1L)));when(jdbc.queryForObject(anyString(),eq(Long.class),eq(1L),eq(1L),eq(1L),eq(1L),eq(1L))).thenReturn(1L);assertThrows(BusinessException.class,()->new MajorService(jdbc).delete(1L));verify(jdbc,never()).update("DELETE FROM sys_major WHERE id=?",1L);}
 StudentDTO dto(){var d=new StudentDTO();d.setUsername("student");d.setRealName("Student");d.setMajorId(1L);d.setStatus(1);d.setPassword("test-password-only");return d;}
 StudentService service(){return new StudentService(users,jdbc,encoder);}
 @Test void teachersCannotManageStudents(){as("TEACHER");assertThrows(BusinessException.class,()->service().page(null,null,null,1,10));verifyNoInteractions(users,jdbc);}
 @Test void majorAdminCannotMoveStudentToForeignMajor(){as("MAJOR_ADMIN");var admin=new User();admin.setMajorId(1L);when(users.selectById(9L)).thenReturn(admin);var d=dto();d.setMajorId(2L);assertThrows(BusinessException.class,()->service().save(null,d));verify(users,never()).insert(any(User.class));}
 @Test void majorAdminCannotEditExistingForeignStudent(){as("MAJOR_ADMIN");var admin=new User();admin.setMajorId(1L);when(users.selectById(9L)).thenReturn(admin);when(jdbc.queryForList(contains("FOR UPDATE"),eq(1L))).thenReturn(List.of(Map.of("id",1L)));var foreign=new User();foreign.setRole("STUDENT");foreign.setMajorId(2L);when(users.lockById(2L)).thenReturn(foreign);assertThrows(BusinessException.class,()->service().save(2L,dto()));verify(users,never()).updateById(any(User.class));}
 @Test void studentsWithHistoricalEnrollmentCannotBePhysicallyDeleted(){as("ADMIN");var student=new User();student.setRole("STUDENT");when(users.lockById(2L)).thenReturn(student);when(jdbc.queryForObject(anyString(),eq(Long.class),eq(2L),eq(2L),eq(2L),eq(2L))).thenReturn(1L);assertThrows(BusinessException.class,()->service().delete(2L));verify(users,never()).deleteById(2L);}
 @Test void newStudentPasswordIsEncodedAndRoleIsFixed(){as("ADMIN");when(jdbc.queryForList(contains("FOR UPDATE"),eq(1L))).thenReturn(List.of(Map.of("id",1L)));when(encoder.encode("test-password-only")).thenReturn("encoded-test-value");when(users.insert(any(User.class))).thenAnswer(inv->{((User)inv.getArgument(0)).setId(5L);return 1;});assertEquals(5L,service().save(null,dto()));var saved=ArgumentCaptor.forClass(User.class);verify(users).insert(saved.capture());assertEquals("STUDENT",saved.getValue().getRole());assertEquals("encoded-test-value",saved.getValue().getPassword());}
}
