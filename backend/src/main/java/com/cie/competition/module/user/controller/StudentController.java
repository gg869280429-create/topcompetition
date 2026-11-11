package com.cie.competition.module.user.controller;
import com.cie.competition.common.result.Result;
import com.cie.competition.module.user.dto.StudentDTO;
import com.cie.competition.module.user.service.StudentService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/student") @lombok.RequiredArgsConstructor @org.springframework.validation.annotation.Validated
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
public class StudentController {
 private final StudentService service;
 @GetMapping("/page") public Result<?> page(@RequestParam(required=false) String keyword,@RequestParam(required=false) Long majorId,@RequestParam(required=false) Integer status,@RequestParam(defaultValue="1") @jakarta.validation.constraints.Min(1) long current,@RequestParam(defaultValue="10") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) long size){return Result.success(service.page(keyword,majorId,status,current,size));}
 @PostMapping public Result<Long> create(@jakarta.validation.Valid @RequestBody StudentDTO dto){return Result.success(service.save(null,dto));}
 @PutMapping("/{id}") public Result<Long> update(@PathVariable Long id,@jakarta.validation.Valid @RequestBody StudentDTO dto){return Result.success(service.save(id,dto));}
 @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id){service.delete(id);return Result.success();}
}
