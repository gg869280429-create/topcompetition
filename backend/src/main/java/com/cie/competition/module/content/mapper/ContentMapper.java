package com.cie.competition.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cie.competition.module.content.entity.Content;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ContentMapper extends BaseMapper<Content> {

    /**
     * 浏览次数原子递增，避免并发下计数丢失。
     */
    @Update("UPDATE content SET view_count = view_count + 1 WHERE id = #{id}")
    int increaseViewCount(@Param("id") Long id);
}
