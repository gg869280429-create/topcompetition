package com.cie.competition.module.major.service;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.major.dto.MajorDTO;
import com.cie.competition.module.file.service.FileService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @lombok.RequiredArgsConstructor
public class MajorBrandingService {
 private final MajorService majors; private final FileService files; private final JdbcTemplate jdbc;
 @Transactional public Long save(Long id,MajorDTO dto){
  if(UserContext.get()==null || !"ADMIN".equals(UserContext.get().getRole()))throw new BusinessException(403,"仅系统管理员可管理专业");
  String icon=dto.getIconCode();
  if(icon!=null && !java.util.Set.of("Monitor","Cpu","Tools","SetUp","Coin","Brush","House","FirstAidKit","Reading","ChatDotRound","Bowl","Sunny","Collection").contains(icon))throw new BusinessException("请选择有效的专业图标");
  if(dto.getLogoFileId()!=null)files.get(dto.getLogoFileId(),"MAJOR_LOGO");
  Long saved=majors.save(id,dto.getName());
  jdbc.update("UPDATE sys_major SET icon_code=?,logo_file_id=? WHERE id=?",icon,dto.getLogoFileId(),saved);return saved;
 }
 public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> logo(Long id){
  var rows=jdbc.queryForList("SELECT logo_file_id FROM sys_major WHERE id=? AND status=1",id);
  if(rows.isEmpty() || rows.get(0).get("logo_file_id")==null)throw new BusinessException(404,"专业未设置自定义Logo");
  return files.majorLogo(((Number)rows.get(0).get("logo_file_id")).longValue());
 }
}
