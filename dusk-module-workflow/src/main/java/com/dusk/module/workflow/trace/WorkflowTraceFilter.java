package com.dusk.module.workflow.trace;

import com.dusk.workflow.trace.WorkflowTraceContext;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

/**
 * Dubbo 追踪过滤器
 * <p>
 * 在 Dubbo RPC 调用间传递 TraceId，实现分布式追踪。
 * </p>
 * <ul>
 *   <li>消费者端：将当前 TraceId 放入 RpcContext 传递给服务端</li>
 *   <li>提供者端：从 RpcContext 获取 TraceId 并设置到本地上下文</li>
 * </ul>
 *
 * @author kefuming
 */
@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER}, order = -10000)
public class WorkflowTraceFilter implements Filter {

    private static final String TRACE_ID_KEY = "workflow-trace-id";
    private static final String SPAN_ID_KEY = "workflow-span-id";
    private static final String PARENT_SPAN_ID_KEY = "workflow-parent-span-id";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcContext context = RpcContext.getContext();

        if (context.isConsumerSide()) {
            // 消费者端：传递追踪信息
            String traceId = WorkflowTraceContext.getTraceId();
            context.setAttachment(TRACE_ID_KEY, traceId);

            // 创建新的 SpanId
            String spanId = WorkflowTraceContext.newSpanId();
            context.setAttachment(SPAN_ID_KEY, spanId);

            String parentSpanId = WorkflowTraceContext.getParentSpanId();
            if (parentSpanId != null) {
                context.setAttachment(PARENT_SPAN_ID_KEY, parentSpanId);
            }
        } else {
            // 提供者端：接收追踪信息
            String traceId = context.getAttachment(TRACE_ID_KEY);
            if (traceId != null && !traceId.isEmpty()) {
                WorkflowTraceContext.setTraceId(traceId);
            }

            String spanId = context.getAttachment(SPAN_ID_KEY);
            if (spanId != null && !spanId.isEmpty()) {
                WorkflowTraceContext.setParentSpanId(spanId);
                WorkflowTraceContext.newSpanId(); // 创建本次调用的 SpanId
            }
        }

        try {
            return invoker.invoke(invocation);
        } finally {
            // 提供者端：清理上下文
            if (context.isProviderSide()) {
                WorkflowTraceContext.clear();
            }
        }
    }
}
