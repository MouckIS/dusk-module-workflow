package com.dusk.workflow.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 工作流分布式追踪上下文
 * <p>
 * 提供 TraceId 的生成、存储和传递能力，支持跨服务追踪。
 * 与 SLF4J MDC 集成，日志自动携带 traceId。
 * </p>
 * <p>
 * <b>使用方式：</b>
 * <pre>
 * // 设置追踪ID（通常在请求入口或 Dubbo Filter 中设置）
 * WorkflowTraceContext.setTraceId(traceId);
 *
 * // 获取追踪ID（会自动生成如果不存在）
 * String traceId = WorkflowTraceContext.getTraceId();
 *
 * // 请求结束时清理
 * WorkflowTraceContext.clear();
 * </pre>
 * </p>
 *
 * @author kefuming
 */
public class WorkflowTraceContext {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";
    private static final String PARENT_SPAN_ID_KEY = "parentSpanId";

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SPAN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> PARENT_SPAN_ID = new ThreadLocal<>();

    /**
     * 设置追踪ID
     *
     * @param traceId 追踪ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            TRACE_ID.set(traceId);
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 获取追踪ID，如果不存在则自动生成
     *
     * @return 追踪ID
     */
    public static String getTraceId() {
        String traceId = TRACE_ID.get();
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
            setTraceId(traceId);
        }
        return traceId;
    }

    /**
     * 获取追踪ID，如果不存在返回 null（不自动生成）
     *
     * @return 追踪ID 或 null
     */
    public static String getTraceIdOrNull() {
        return TRACE_ID.get();
    }

    /**
     * 设置 SpanId
     *
     * @param spanId SpanId
     */
    public static void setSpanId(String spanId) {
        if (spanId != null && !spanId.isEmpty()) {
            SPAN_ID.set(spanId);
            MDC.put(SPAN_ID_KEY, spanId);
        }
    }

    /**
     * 获取 SpanId
     *
     * @return SpanId
     */
    public static String getSpanId() {
        return SPAN_ID.get();
    }

    /**
     * 设置父 SpanId
     *
     * @param parentSpanId 父 SpanId
     */
    public static void setParentSpanId(String parentSpanId) {
        if (parentSpanId != null && !parentSpanId.isEmpty()) {
            PARENT_SPAN_ID.set(parentSpanId);
            MDC.put(PARENT_SPAN_ID_KEY, parentSpanId);
        }
    }

    /**
     * 获取父 SpanId
     *
     * @return 父 SpanId
     */
    public static String getParentSpanId() {
        return PARENT_SPAN_ID.get();
    }

    /**
     * 生成新的 SpanId
     *
     * @return 新的 SpanId
     */
    public static String newSpanId() {
        String spanId = generateSpanId();
        // 将当前 spanId 设为 parentSpanId
        String currentSpanId = SPAN_ID.get();
        if (currentSpanId != null) {
            setParentSpanId(currentSpanId);
        }
        setSpanId(spanId);
        return spanId;
    }

    /**
     * 清理上下文
     * <p>
     * 在请求结束时调用，避免线程复用导致的数据污染。
     * </p>
     */
    public static void clear() {
        TRACE_ID.remove();
        SPAN_ID.remove();
        PARENT_SPAN_ID.remove();
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
        MDC.remove(PARENT_SPAN_ID_KEY);
    }

    /**
     * 生成追踪ID
     *
     * @return 32位的追踪ID
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 SpanId
     *
     * @return 16位的 SpanId
     */
    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 创建追踪上下文快照（用于异步线程传递）
     *
     * @return 上下文快照
     */
    public static TraceSnapshot snapshot() {
        return new TraceSnapshot(
                TRACE_ID.get(),
                SPAN_ID.get(),
                PARENT_SPAN_ID.get()
        );
    }

    /**
     * 从快照恢复上下文
     *
     * @param snapshot 上下文快照
     */
    public static void restore(TraceSnapshot snapshot) {
        if (snapshot != null) {
            if (snapshot.getTraceId() != null) {
                setTraceId(snapshot.getTraceId());
            }
            if (snapshot.getSpanId() != null) {
                setSpanId(snapshot.getSpanId());
            }
            if (snapshot.getParentSpanId() != null) {
                setParentSpanId(snapshot.getParentSpanId());
            }
        }
    }

    /**
     * 追踪上下文快照
     */
    public static class TraceSnapshot {
        private final String traceId;
        private final String spanId;
        private final String parentSpanId;

        public TraceSnapshot(String traceId, String spanId, String parentSpanId) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public String getParentSpanId() {
            return parentSpanId;
        }
    }
}
