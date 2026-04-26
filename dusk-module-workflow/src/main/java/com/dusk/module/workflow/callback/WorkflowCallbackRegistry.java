package com.dusk.module.workflow.callback;

import com.dusk.workflow.dto.callback.WorkflowCallbackContext;
import com.dusk.workflow.dto.callback.WorkflowCallbackResult;
import com.dusk.workflow.service.callback.IWorkflowCallbackRpcService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 工作流回调服务注册中心
 * <p>
 * 通过 Dubbo 服务发现获取所有业务服务实现的 {@link IWorkflowCallbackRpcService}，
 * 按 processKey 建立索引。工作流服务在关键节点通过此注册中心查找并调用对应的业务回调。
 * </p>
 * <p>
 * 特性：
 * <ul>
 *   <li>懒加载：check=false, lazy=true，服务不可用不影响启动</li>
 *   <li>自动刷新：定时刷新服务列表，支持服务动态上下线</li>
 *   <li>优雅降级：回调服务不可用时不影响流程执行</li>
 * </ul>
 * </p>
 *
 * @author kefuming
 */
@Slf4j
@Component
public class WorkflowCallbackRegistry {

    /**
     * 按 processKey 缓存回调服务引用
     */
    private final Map<String, IWorkflowCallbackRpcService> callbackServices = new ConcurrentHashMap<>();

    /**
     * Dubbo 泛化引用所有实现了 IWorkflowCallbackRpcService 的服务
     * check=false: 启动时不检查服务可用性
     * lazy=true: 延迟初始化
     * timeout: 5秒超时
     */
    @DubboReference(check = false, lazy = true, timeout = 5000, providedBy = "workflow-callback-provider")
    private List<IWorkflowCallbackRpcService> allCallbackServices;

    @PostConstruct
    public void init() {
        refreshRegistry();
        log.info("工作流回调注册中心初始化完成");
    }

    /**
     * 刷新注册表（支持动态服务上下线）
     * 每60秒执行一次
     */
    @Scheduled(fixedRate = 60000)
    public void refreshRegistry() {
        if (allCallbackServices == null || allCallbackServices.isEmpty()) {
            log.debug("未发现工作流回调服务");
            return;
        }

        for (IWorkflowCallbackRpcService service : allCallbackServices) {
            try {
                String processKey = service.getProcessKey();
                if (processKey != null && !processKey.isEmpty()) {
                    IWorkflowCallbackRpcService existing = callbackServices.put(processKey, service);
                    if (existing == null) {
                        log.info("注册工作流回调服务: processKey={}", processKey);
                    }
                }
            } catch (Exception e) {
                log.warn("获取回调服务 processKey 失败，服务可能不可用", e);
            }
        }
    }

    /**
     * 手动注册回调服务（用于测试或本地服务）
     *
     * @param service 回调服务实现
     */
    public void register(IWorkflowCallbackRpcService service) {
        String processKey = service.getProcessKey();
        if (processKey != null && !processKey.isEmpty()) {
            callbackServices.put(processKey, service);
            log.info("手动注册工作流回调服务: processKey={}", processKey);
        }
    }

    /**
     * 取消注册
     *
     * @param processKey 流程定义Key
     */
    public void unregister(String processKey) {
        IWorkflowCallbackRpcService removed = callbackServices.remove(processKey);
        if (removed != null) {
            log.info("取消注册工作流回调服务: processKey={}", processKey);
        }
    }

    /**
     * 获取指定流程的回调服务
     *
     * @param processKey 流程定义Key
     * @return 回调服务（可能为空）
     */
    public Optional<IWorkflowCallbackRpcService> getCallbackService(String processKey) {
        return Optional.ofNullable(callbackServices.get(processKey));
    }

    /**
     * 检查是否存在指定流程的回调服务
     *
     * @param processKey 流程定义Key
     * @return 是否存在
     */
    public boolean hasCallbackService(String processKey) {
        return callbackServices.containsKey(processKey);
    }

    /**
     * 获取所有已注册的流程Key
     *
     * @return 流程Key列表
     */
    public List<String> getRegisteredProcessKeys() {
        return List.copyOf(callbackServices.keySet());
    }

    /**
     * 执行前置回调（带异常处理）
     * <p>
     * 前置回调失败时根据 failFast 配置决定是否中断流程。
     * </p>
     *
     * @param processKey 流程定义Key
     * @param context    回调上下文
     * @param callback   回调函数
     * @return 回调结果
     */
    public WorkflowCallbackResult invokeBeforeCallback(String processKey,
                                                        WorkflowCallbackContext context,
                                                        Function<IWorkflowCallbackRpcService, WorkflowCallbackResult> callback) {
        return getCallbackService(processKey)
                .map(service -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        WorkflowCallbackResult result = callback.apply(service);
                        result.setDuration(System.currentTimeMillis() - startTime);
                        log.debug("前置回调执行成功: processKey={}, duration={}ms, proceed={}",
                                processKey, result.getDuration(), result.isProceed());
                        return result;
                    } catch (Exception e) {
                        log.error("前置回调执行异常: processKey={}", processKey, e);
                        // 根据 failFast 配置决定是否中断
                        if (service.isFailFast()) {
                            throw new RuntimeException("前置回调执行失败: " + e.getMessage(), e);
                        }
                        WorkflowCallbackResult result = WorkflowCallbackResult.proceed();
                        result.setDuration(System.currentTimeMillis() - startTime);
                        return result;
                    }
                })
                .orElseGet(() -> {
                    log.debug("未找到回调服务，跳过前置回调: processKey={}", processKey);
                    return WorkflowCallbackResult.proceed();
                });
    }

    /**
     * 执行后置回调（异常不中断流程）
     *
     * @param processKey 流程定义Key
     * @param context    回调上下文
     * @param callback   回调函数
     */
    public void invokeAfterCallback(String processKey,
                                    WorkflowCallbackContext context,
                                    java.util.function.Consumer<IWorkflowCallbackRpcService> callback) {
        getCallbackService(processKey).ifPresent(service -> {
            long startTime = System.currentTimeMillis();
            try {
                callback.accept(service);
                log.debug("后置回调执行成功: processKey={}, duration={}ms",
                        processKey, System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                log.error("后置回调执行异常（不影响流程）: processKey={}", processKey, e);
            }
        });
    }
}
