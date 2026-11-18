package com.cie.competition.module.major.controller;
@org.springframework.web.bind.annotation.RestController @lombok.RequiredArgsConstructor
public class MajorLogoController {
 private final com.cie.competition.module.major.service.MajorBrandingService branding;
 @org.springframework.web.bind.annotation.GetMapping("/api/major/{id}/logo")
 public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> logo(@org.springframework.web.bind.annotation.PathVariable Long id){return branding.logo(id);}
}
