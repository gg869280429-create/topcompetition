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
    private final com.cie.competition.module.file.service.FileService files;

    @Override
    public PageResult<ContentVO> page(ContentQueryDTO query) {
        LambdaQueryWrapper<Content> wrapper = new LambdaQueryWrapper<Content>()
                .eq(StringUtils.hasText(query.getCategory()), Content::getCategory, query.getCategory())
                .eq(StringUtils.hasText(query.getStatus()), Content::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getKeyword()), Content::getTitle, query.getKeyword());
        if (!isManager()) {
            publishedWindow(wrapper,LocalDateTime.now());
        }
        wrapper.orderByDesc(Content::getCreateTime);
        Page<Content> page = contentMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return new PageResult<>(assembleVOs(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ContentVO getById(Long id) {
        Content content = getContent(id);
        if (!isManager() && !visible(content)) {
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
        requireManager();
        validateCategory(dto.getCategory());
        validateDraft(dto);
        Content content = new Content();
        BeanUtils.copyProperties(dto, content);
        content.setStatus("DRAFT");
        content.setAuthorId(UserContext.getUserId());
        content.setViewCount(0);
        contentMapper.insert(content);
        return content.getId();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void update(Long id, ContentDTO dto) {
        requireManager();
        validateCategory(dto.getCategory());
        validateDraft(dto);
        Content content = lockContent(id);
        if (!"DRAFT".equals(content.getStatus())) {
            throw new BusinessException("仅草稿状态的内容可编辑");
        }
        content.setTitle(dto.getTitle());
        content.setContent(dto.getContent());
        content.setCoverFileId(dto.getCoverFileId());
        content.setCategory(dto.getCategory());
        content.setOfficialUrl(dto.getOfficialUrl()); content.setCarouselEnabled(Boolean.TRUE.equals(dto.getCarouselEnabled())); content.setSortOrder(dto.getSortOrder()==null?0:dto.getSortOrder());
        contentMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Content>()
            .eq(Content::getId,id).set(Content::getTitle,content.getTitle()).set(Content::getContent,content.getContent())
            .set(Content::getCategory,content.getCategory()).set(Content::getCoverFileId,content.getCoverFileId())
            .set(Content::getOfficialUrl,content.getOfficialUrl()).set(Content::getCarouselEnabled,content.getCarouselEnabled()).set(Content::getSortOrder,content.getSortOrder()).set(Content::getPublishStartTime,dto.getPublishStartTime()).set(Content::getPublishEndTime,dto.getPublishEndTime()).set(Content::getCarouselStartTime,dto.getCarouselStartTime()).set(Content::getCarouselEndTime,dto.getCarouselEndTime()));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void delete(Long id) {
        requireManager(); lockContent(id);
        contentMapper.deleteById(id);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void updateStatus(Long id, String status) {
        requireManager();
        if (!"DRAFT".equals(status) && !"PUBLISHED".equals(status)) {
            throw new BusinessException("非法的内容状态");
        }
        Content content = lockContent(id);
        content.setStatus(status);
        if ("PUBLISHED".equals(status)) {
            if ("NEWS".equals(content.getCategory())) validateUrl(content.getOfficialUrl(),true);
            validateCarousel(content.getCarouselEnabled(),content.getCoverFileId(),content.getOfficialUrl(),content.getCategory());
            var now=LocalDateTime.now();
            if(content.getPublishEndTime()!=null && !content.getPublishEndTime().isAfter(now))throw new BusinessException("新闻结束时间已到期，请先修改时间");
            if(content.getPublishStartTime()!=null && content.getPublishStartTime().isAfter(now))content.setStatus("SCHEDULED");
            else content.setPublishTime(now);
        }
        contentMapper.updateById(content);
    }

    public List<ContentVO> carousel() {
        var now=LocalDateTime.now();
        var wrapper=new LambdaQueryWrapper<Content>().eq(Content::getCategory,"NEWS").eq(Content::getCarouselEnabled,true)
            .isNotNull(Content::getCoverFileId).isNotNull(Content::getOfficialUrl);
        publishedWindow(wrapper,now);
        wrapper.and(w->w.isNull(Content::getCarouselStartTime).or().le(Content::getCarouselStartTime,now))
            .and(w->w.isNull(Content::getCarouselEndTime).or().gt(Content::getCarouselEndTime,now))
            .orderByAsc(Content::getSortOrder).orderByDesc(Content::getPublishTime).last("LIMIT 20");
        var list=contentMapper.selectList(wrapper);
        return assembleVOs(list);
    }
    public Long coverFileId(Long id) {
        var content=getContent(id);
        if(!isManager() && !visible(content))throw new BusinessException(403,"无权限查看该内容");
        return content.getCoverFileId();
    }
    private void publishedWindow(LambdaQueryWrapper<Content> wrapper,LocalDateTime now) {
        wrapper.eq(Content::getStatus,"PUBLISHED")
            .and(w->w.isNull(Content::getPublishStartTime).or().le(Content::getPublishStartTime,now))
            .and(w->w.isNull(Content::getPublishEndTime).or().gt(Content::getPublishEndTime,now));
    }
    private boolean visible(Content content) {
        var now=LocalDateTime.now();return "PUBLISHED".equals(content.getStatus())
            && (content.getPublishStartTime()==null || !content.getPublishStartTime().isAfter(now))
            && (content.getPublishEndTime()==null || content.getPublishEndTime().isAfter(now));
    }
    private void validateWindow(LocalDateTime start,LocalDateTime end,String label) {
        if(start!=null && end!=null && !start.isBefore(end))throw new BusinessException(label+"开始时间必须早于结束时间");
    }
    @Override @org.springframework.transaction.annotation.Transactional
    public void updateCarousel(Long id,com.cie.competition.module.content.dto.CarouselDTO dto) {
        requireManager();var content=lockContent(id);
        validateWindow(dto.getCarouselStartTime(),dto.getCarouselEndTime(),"轮播");
        validateCarousel(dto.getCarouselEnabled(),content.getCoverFileId(),content.getOfficialUrl(),content.getCategory());
        if(Boolean.TRUE.equals(dto.getCarouselEnabled()) && dto.getCarouselEndTime()!=null && !dto.getCarouselEndTime().isAfter(LocalDateTime.now()))throw new BusinessException("轮播结束时间已到期");
        contentMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Content>().eq(Content::getId,id)
            .set(Content::getCarouselEnabled,dto.getCarouselEnabled()).set(Content::getSortOrder,dto.getSortOrder())
            .set(Content::getCarouselStartTime,dto.getCarouselStartTime()).set(Content::getCarouselEndTime,dto.getCarouselEndTime()));
    }
    private void requireManager() {
        if(!isManager()) throw new BusinessException(403,"仅管理员可以维护新闻");
    }
    private void validateDraft(ContentDTO dto) {
        validateWindow(dto.getPublishStartTime(),dto.getPublishEndTime(),"新闻");
        validateWindow(dto.getCarouselStartTime(),dto.getCarouselEndTime(),"轮播");
        validateUrl(dto.getOfficialUrl(),false);
        if(dto.getCoverFileId()!=null) files.validateContentCover(dto.getCoverFileId());
        if(dto.getSortOrder()!=null && dto.getSortOrder()<0) throw new BusinessException("排序不能小于0");
        validateCarousel(dto.getCarouselEnabled(),dto.getCoverFileId(),dto.getOfficialUrl(),dto.getCategory());
    }
    private void validateCarousel(Boolean enabled,Long cover,String url,String category) {
        if(!Boolean.TRUE.equals(enabled))return;
        if(!"NEWS".equals(category))throw new BusinessException("仅新闻可加入首页轮播");
        validateUrl(url,true);
        if(cover==null)throw new BusinessException("开启轮播必须上传封面图片");
        files.get(cover,"CONTENT_COVER");
    }
    private void validateUrl(String url,boolean required) {
        if(!StringUtils.hasText(url)){if(required)throw new BusinessException("发布新闻或开启轮播必须填写比赛官网地址");return;}
        try{
            var uri=new java.net.URI(url);
            if(!Set.of("http","https").contains(java.util.Objects.toString(uri.getScheme(),"").toLowerCase(java.util.Locale.ROOT)) || uri.getHost()==null || uri.getUserInfo()!=null || url.length()>2048)
                throw new BusinessException("官网地址仅支持有效的HTTP/HTTPS地址");
        }catch(java.net.URISyntaxException e){throw new BusinessException("官网地址格式不正确");}
    }
    private void validateCategory(String category) {
        if (!CATEGORIES.contains(category)) {
            throw new BusinessException("非法的内容分类");
        }
    }

    private boolean isManager() {
        LoginUser loginUser = UserContext.get();
        return loginUser != null && ("ADMIN".equals(loginUser.getRole()) || "MAJOR_ADMIN".equals(loginUser.getRole()));
    }

    private Content lockContent(Long id) {
        var content = contentMapper.selectOne(new LambdaQueryWrapper<Content>().eq(Content::getId,id).last("FOR UPDATE"));
        if(content==null) throw new BusinessException(404,"内容不存在");
        return content;
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
