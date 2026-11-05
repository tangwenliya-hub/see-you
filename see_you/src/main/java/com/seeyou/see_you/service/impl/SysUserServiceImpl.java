package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seeyou.see_you.entity.SysUser;
import com.seeyou.see_you.mapper.SysUserMapper;
import com.seeyou.see_you.service.SysUserService;
import org.springframework.stereotype.Service;
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {}
