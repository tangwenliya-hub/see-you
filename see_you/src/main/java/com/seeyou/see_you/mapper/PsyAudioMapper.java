package com.seeyou.see_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seeyou.see_you.entity.PsyAudio;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PsyAudioMapper extends BaseMapper<PsyAudio> {

    /**
     * 原子增加播放量，避免并发下计数丢失
     */
    @Update("UPDATE psy_audio SET play_count = play_count + 1 WHERE audio_id = #{id}")
    int incrementPlayCount(Long id);
}