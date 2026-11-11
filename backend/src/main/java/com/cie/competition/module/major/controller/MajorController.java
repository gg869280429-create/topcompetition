package com.cie.competition.module.major.controller;
import com.cie.competition.common.result.Result;
import com.cie.competition.module.major.dto.MajorDTO;
import com.cie.competition.module.major.service.MajorService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/major") @lombok.RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class MajorController {
 private final MajorService service;
 @GetMapping public Result<?> list(@RequestParam(required=false) String keyword){return Result.success(service.list(keyword));}
 @PostMapping public Result<Long> create(@jakarta.validation.Valid @RequestBody MajorDTO dto){return Result.success(service.save(null,dto.getName()));}
 @PutMapping("/{id}") public Result<Long> update(@PathVariable Long id,@jakarta.validation.Valid @RequestBody MajorDTO dto){return Result.success(service.save(id,dto.getName()));}
 @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id){service.delete(id);return Result.success();}
}
