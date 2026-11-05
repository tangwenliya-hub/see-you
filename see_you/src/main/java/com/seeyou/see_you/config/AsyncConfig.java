package com.seeyou.see_you.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置。
 *
 * <p>用于把「结果不直接影响本次响应」的耗时操作移出请求线程，典型场景：
 * 找回密码的 SMTP 邮件发送（外部网络调用，可能阻塞数秒）。
 *
 * <p>显式声明线程池而不是用默认执行器：默认的 SimpleAsyncTaskExecutor 每次
 * 新建线程不复用，高并发下会大量建线程。这里核心 2 线程、队列 50，足够
 * 承接邮件等低频任务且不会堆积。
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mail-async-");
        // 队列满时由调用线程执行，保证邮件最终会发出而不是被丢弃
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
