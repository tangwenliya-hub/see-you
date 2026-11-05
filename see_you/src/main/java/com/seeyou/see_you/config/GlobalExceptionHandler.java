package com.seeyou.see_you.config;

import com.seeyou.see_you.util.BizException;
import com.seeyou.see_you.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 *
 * <p>安全约定：<b>只有 BizException 的 message 允许回传给前端</b>。
 * 其它任何异常（尤其是 SQLException、NullPointerException）的 message 都可能包含
 * 表名、字段名、SQL 片段等内部实现细节，一律只写日志，前端返回通用文案。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：规则不满足，message 可安全展示 */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBiz(BizException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 参数绑定/校验失败：提示缺少或格式错误即可，不回传框架原始描述 */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            IllegalArgumentException.class
    })
    public Result<?> handleBadRequest(Exception e) {
        log.warn("请求参数异常：{}", e.getMessage());
        return Result.error(400, "请求参数不正确，请检查后重试");
    }

    /** 数据库异常：message 常带表名/字段名，绝不外传 */
    @ExceptionHandler(DataAccessException.class)
    public Result<?> handleDataAccess(DataAccessException e) {
        log.error("数据库操作失败：", e);
        return Result.error(500, "数据操作失败，请稍后重试");
    }

    /** 上传文件超出限制 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleMaxUpload(MaxUploadSizeExceededException e) {
        log.warn("上传文件过大：{}", e.getMessage());
        return Result.error(413, "上传文件超出大小限制");
    }

    /** 空指针：属于代码缺陷，记日志排查，不暴露细节 */
    @ExceptionHandler(NullPointerException.class)
    public Result<?> handleNullPointer(NullPointerException e) {
        log.error("空指针异常：", e);
        return Result.error(500, "服务器内部错误");
    }

    /** 兜底：所有未知异常 */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常：", e);
        return Result.error(500, "服务器内部错误");
    }
}
