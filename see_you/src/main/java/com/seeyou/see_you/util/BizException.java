package com.seeyou.see_you.util;

/**
 * 业务异常：预期内的、可以原样提示给用户的错误（如「只能修改自己的资料」「反馈不存在」）。
 *
 * <p>与系统异常的区别：
 * <ul>
 *   <li>抛 BizException —— 说明是业务规则不满足，message 可以直接展示给用户；</li>
 *   <li>抛其它异常（SQL 异常、空指针等）—— 属于系统内部错误，message 可能包含表名、
 *       字段名、堆栈等敏感信息，只能记日志，前端统一提示「服务器内部错误」。</li>
 * </ul>
 */
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
