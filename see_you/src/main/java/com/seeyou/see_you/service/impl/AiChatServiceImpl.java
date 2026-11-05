package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.AiChat;
import com.seeyou.see_you.mapper.AiChatMapper;
import com.seeyou.see_you.service.AiChatService;
import org.springframework.stereotype.Service;
@Service
public class AiChatServiceImpl extends ServiceImpl<AiChatMapper, AiChat> implements AiChatService {
}
