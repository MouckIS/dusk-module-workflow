package com.dusk.module.workflow.authorization;


import com.dusk.common.core.auth.permission.AuthorizationProvider;
import com.dusk.common.core.auth.permission.IPermissionDefinitionContext;
import com.dusk.common.core.auth.permission.MultiTenancySides;
import com.dusk.common.core.auth.permission.Permission;
import org.springframework.stereotype.Component;

/**
 * @author kefuming
 * @date 2020-07-22 10:58
 */
@Component
public class ActivitiAuthProvider extends AuthorizationProvider {
    public static final String PAGES_ACTIVITI = "Pages.Activiti";
    public static final String PAGES_ACTIVITI_MODEL = "Pages.Activiti.Model";
    public static final String PAGES_ACTIVITI_MODEL_SAVE = "Pages.Activiti.Model.Save";
    public static final String PAGES_ACTIVITI_MODEL_DEPLOY = "Pages.Activiti.Model.Deploy";
    public static final String PAGES_ACTIVITI_MODEL_DELETE = "Pages.Activiti.Model.Delete";

    public static final String PAGES_ACTIVITI_PROCESS = "Pages.Activiti.Process";
    public static final String PAGES_ACTIVITI_PROCESS_DELETE = "Pages.Activiti.Process.Delete";

    public static final String PAGES_ACTIVITI_TASK = "Pages.Activiti.Task";

    @Override
    public void setPermissions(IPermissionDefinitionContext context) {
        Permission main = context.createPermission(PAGES_ACTIVITI, "工作流", MultiTenancySides.Tenant);

        Permission modelPermission = main.createChildPermission(PAGES_ACTIVITI_MODEL, "模型管理", MultiTenancySides.Tenant);
        modelPermission.createChildPermission(PAGES_ACTIVITI_MODEL_SAVE, "新增或者编辑", MultiTenancySides.Tenant);
        modelPermission.createChildPermission(PAGES_ACTIVITI_MODEL_DEPLOY, "发布流程", MultiTenancySides.Tenant);
        modelPermission.createChildPermission(PAGES_ACTIVITI_MODEL_DELETE, "删除", MultiTenancySides.Tenant);

        Permission processPermission = main.createChildPermission(PAGES_ACTIVITI_PROCESS, "流程管理", MultiTenancySides.Tenant);
        processPermission.createChildPermission(PAGES_ACTIVITI_PROCESS_DELETE, "删除", MultiTenancySides.Tenant);

        main.createChildPermission(PAGES_ACTIVITI_TASK, "任务管理", MultiTenancySides.Tenant);
    }
}
