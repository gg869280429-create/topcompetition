package com.cie.competition.module.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.result.PageResult;
import com.cie.competition.common.result.ResultCode;
import com.cie.competition.common.security.LoginUser;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.content.dto.ContentDTO;
import com.cie.competition.module.content.dto.ContentQueryDTO;
import com.cie.competition.module.content.entity.Content;
import com.cie.competition.module.content.mapper.ContentMapper;
import com.cie.competition.module.content.service.ContentService;
import com.cie.competition.module.content.vo.ContentVO;
import com.cie.competition.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentServiceImpl implements ContentService {

    private static final Set<String> CATEGORIES = Set.of("NOTICE", "NEWS", "ANNOUNCEMENT");

    private final ContentMapper contentMapper;
    private final UserService userService;

    @Override
    public PageResult<ContentVO> page(ContentQueryDTO query) {
        LambdaQueryWrapper<Content> wrapper = new LambdaQueryWrapper<Content>()
                .eq(StringUtils.hasText(query.getCategory()), Content::getCategory, query.getCategory())
                .eq(StringUtils.hasText(query.getStatus()), Content::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getKeyword()), Content::getTitle, query.getKeyword());
        if (!isManager()) {
            wrapper.eq(Content::getStatus, "PUBLISHED");
        }
        wrapper.orderByDesc(Content::getCreateTime);
        Page<Content> page = contentMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return new PageResult<>(assembleVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ContentVO getById(Long id) {
        Content content = getContent(id);
        if (!isManager() && !"PUBLISHED".equals(content.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权限查看该内容");
        }
        if ("PUBLISHED".equals(content.getStatus())) {
            contentMapper.increaseViewCount(id);
            content.setViewCount((content.getViewCount() == null ? 0 : content.getViewCount()) + 1);
        }
        return assembleVOs(List.of(content)).get(0);
    }

    @Override
    public Long create(ContentDTO dto) {
        validateCategory(dto.getCategory());
        Content content = new Content();
        BeanUtils.copyProperties(dto, content);
        content.setStatus("DRAFT");
        content.setAuthorId(UserContext.getUserId());
        content.setViewCount(0);
        contentMapper.insert(content);
        return content.getId();
    }

    @Override
    public void update(Long id, ContentDTO dto) {
        validateCategory(dto.getCategory());
        Content content = getContent(id);
        if (!"DRAFT".equals(content.getStatus())) {
            throw new BusinessException("仅草稿状态的内容可编辑");
        }
        content.setTitle(dto.getTitle());
        content.setContent(dto.getContent());
        content.setCoverFileId(dto.getCoverFileId());
        content.setCategory(dto.getCategory());
        contentMapper.updateById(content);
    }

    @Override
    public void delete(Long id) {
        getContent(id);
        contentMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, String status) {
        if (!"DRAFT".equals(status) && !"PUBLISHED".equals(status)) {
            throw new BusinessException("非法的内容状态");
        }
        Content content = getContent(id);
        content.setStatus(status);
        if ("PUBLISHED".equals(status)) {
            content.setPublishTime(LocalDateTime.now());
        }
        contentMapper.updateById(content);
    }

    private void validateCategory(String category) {
        if (!CATEGORIES.contains(category)) {
            throw new BusinessException("非法的内容分类");
        }
    }

    private boolean isManager() {
        LoginUser loginUser = UserContext.get();
        return "ADMIN".equals(loginUser.getRole()) || "MAJOR_ADMIN".equals(loginUser.getRole());
    }

    private Content getContent(Long id) {
        Content content = contentMapper.selectById(id);
        if (content == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "内容不存在");
        }
        return content;
    }

    private List<ContentVO> assembleVOs(List<Content> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> authorIds = list.stream().map(Content::getAuthorId).filter(id -> id != null).distinct().toList();
        Map<Long, String> authorNameMap = userService.listRealNameMap(authorIds);
        return list.stream().map(c -> {
            ContentVO vo = new ContentVO();
            BeanUtils.copyProperties(c, vo);
            vo.setAuthorName(authorNameMap.get(c.getAuthorId()));
            return vo;
        }).toList();
    }
}
