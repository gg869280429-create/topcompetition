package com.cie.competition.module.message.controller;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.result.Result;
import com.cie.competition.module.message.dto.MessageDTO;
import com.cie.competition.module.message.dto.MessageQueryDTO;
import com.cie.competition.module.message.service.MessageService;
import com.cie.competition.module.message.vo.MessageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/mine")
    public Result<PageResult<MessageVO>> pageMine(MessageQueryDTO query) {
        return Result.success(messageService.pageMine(query));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(messageService.unreadCount());
    }

    @GetMapping("/{id}")
    public Result<MessageVO> getById(@PathVariable Long id) {
        return Result.success(messageService.getById(id));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        messageService.markAllRead();
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        messageService.delete(id);
        return Result.success();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MAJOR_ADMIN')")
    public Result<Long> send(@Valid @RequestBody MessageDTO dto) {
        return Result.success(messageService.send(dto));
    }
}
